-- MapStruct IPC Client for Neovim
-- This module handles communication with the MapStruct Java IPC server via Unix domain sockets
--
-- Usage:
--   local client = require('mapstruct_ipc_client')
--   client.setup({
--       jar_path = "/path/to/mapstruct-path-explorer.jar",
--       classpath = "/path/to/additional/classes" -- optional
--   })
--
--   -- Make requests
--   client.request("ping", {}, function(response)
--       print(vim.inspect(response))
--   end)

local M = {}

-- Configuration
local config = {
    jar_path = nil,
    classpath = nil,
    socket_path = nil,
    java_cmd = "java",
    heartbeat_interval_ms = 10000, -- Send heartbeat every 10 seconds
    request_timeout_ms = 5000,
    use_jdtls_classpath = true, -- Auto-detect and use jdtls classpath
}

-- State
local state = {
    server_job_id = nil,
    socket_fd = nil,
    connected = false,
    pending_requests = {},
    request_id_counter = 0,
    heartbeat_timer = nil,
    read_buffer = "",
}

-- Generate unique request ID
local function generate_request_id()
    state.request_id_counter = state.request_id_counter + 1
    return tostring(state.request_id_counter)
end

-- Generate unique socket path for this Neovim instance
local function generate_socket_path()
    local tmpdir = vim.fn.getenv("TMPDIR") or "/tmp"
    local nvim_pid = vim.fn.getpid()
    return string.format("%s/mapstruct-ipc-%d.sock", tmpdir, nvim_pid)
end

-- Get classpath from nvim-jdtls
local function get_jdtls_classpath()
    local ok, jdtls = pcall(require, 'jdtls')
    if not ok then
        vim.notify("nvim-jdtls not found - install mfussenegger/nvim-jdtls", vim.log.levels.WARN)
        return nil
    end

    -- Get the current buffer
    local bufnr = vim.api.nvim_get_current_buf()
    local uri = vim.uri_from_bufnr(bufnr)

    -- Find jdtls client
    local clients = vim.lsp.get_clients({ bufnr = bufnr, name = "jdtls" })
    if not clients or #clients == 0 then
        -- Try to find any jdtls client
        clients = vim.lsp.get_clients({ name = "jdtls" })
        if not clients or #clients == 0 then
            vim.notify("No jdtls LSP client found - make sure jdtls is running", vim.log.levels.WARN)
            return nil
        end
    end

    local client = clients[1]

    -- Try multiple methods to get classpath
    -- Method 1: Use java.project.getClasspaths command (most reliable)
    local result, err = client.request_sync(
        "workspace/executeCommand",
        {
            command = "java.project.getClasspaths",
            arguments = { uri, { scope = "runtime" } }
        },
        10000, -- 10 second timeout (classpath resolution can be slow)
        bufnr
    )

    if result and result.result then
        local classpaths = result.result.classpaths or result.result
        if type(classpaths) == "table" and #classpaths > 0 then
            local cp_str = table.concat(classpaths, ":")
            vim.notify("Got classpath from jdtls: " .. #classpaths .. " entries", vim.log.levels.INFO)
            return cp_str
        end
    end

    -- Method 2: Try using workspace/executeCommand with java.project.getAll
    result, err = client.request_sync(
        "workspace/executeCommand",
        {
            command = "java.project.getAll",
            arguments = {}
        },
        10000,
        bufnr
    )

    if result and result.result then
        -- Parse project info and extract classpaths
        local projects = result.result
        if type(projects) == "table" then
            local all_classpaths = {}
            for _, project in ipairs(projects) do
                if project.classpaths then
                    for _, cp in ipairs(project.classpaths) do
                        table.insert(all_classpaths, cp)
                    end
                end
            end
            if #all_classpaths > 0 then
                local cp_str = table.concat(all_classpaths, ":")
                vim.notify("Got classpath from jdtls (all projects): " .. #all_classpaths .. " entries", vim.log.levels.INFO)
                return cp_str
            end
        end
    end

    vim.notify("Could not retrieve classpath from jdtls - falling back to manual classpath", vim.log.levels.WARN)
    return nil
end

-- Start the Java IPC server
local function start_server()
    if state.server_job_id then
        vim.notify("MapStruct IPC server already running", vim.log.levels.WARN)
        return false
    end

    if not config.jar_path then
        vim.notify("MapStruct jar_path not configured", vim.log.levels.ERROR)
        return false
    end

    config.socket_path = generate_socket_path()

    -- Build classpath
    local classpath = config.jar_path

    -- Try to get classpath from jdtls if enabled
    if config.use_jdtls_classpath then
        local jdtls_cp = get_jdtls_classpath()
        if jdtls_cp then
            classpath = classpath .. ":" .. jdtls_cp
        elseif config.classpath then
            -- Fallback to manual classpath if jdtls fails
            classpath = classpath .. ":" .. config.classpath
        end
    elseif config.classpath then
        -- Use manual classpath if jdtls is disabled
        classpath = classpath .. ":" .. config.classpath
    end

    -- Build command
    local cmd = {
        config.java_cmd,
        "-cp",
        classpath,
        "com.dsm.mapstruct.IpcServer",
        config.socket_path
    }

    vim.notify("Starting MapStruct IPC server on " .. config.socket_path, vim.log.levels.INFO)

    -- Start server as background job
    state.server_job_id = vim.fn.jobstart(cmd, {
        on_stdout = function(_, data, _)
            if data and #data > 0 then
                for _, line in ipairs(data) do
                    if line ~= "" then
                        vim.notify("MapStruct server: " .. line, vim.log.levels.INFO)
                    end
                end
            end
        end,
        on_stderr = function(_, data, _)
            if data and #data > 0 then
                for _, line in ipairs(data) do
                    if line ~= "" then
                        vim.notify("MapStruct server error: " .. line, vim.log.levels.ERROR)
                    end
                end
            end
        end,
        on_exit = function(_, exit_code, _)
            vim.notify("MapStruct IPC server exited with code " .. exit_code, vim.log.levels.WARN)
            M.disconnect()
        end,
    })

    if state.server_job_id <= 0 then
        vim.notify("Failed to start MapStruct IPC server", vim.log.levels.ERROR)
        state.server_job_id = nil
        return false
    end

    -- Give server time to start
    vim.defer_fn(function()
        M.connect()
    end, 500)

    return true
end

-- Connect to the Unix domain socket
function M.connect()
    if state.connected then
        return true
    end

    if not config.socket_path then
        vim.notify("Socket path not set", vim.log.levels.ERROR)
        return false
    end

    -- Wait for socket file to exist
    local max_attempts = 10
    local attempt = 0
    while attempt < max_attempts do
        if vim.fn.filereadable(config.socket_path) == 1 then
            break
        end
        attempt = attempt + 1
        vim.cmd("sleep 100m")
    end

    if vim.fn.filereadable(config.socket_path) ~= 1 then
        vim.notify("Socket file not found: " .. config.socket_path, vim.log.levels.ERROR)
        return false
    end

    -- Connect using Lua socket (requires luv/libuv)
    local uv = vim.loop
    state.socket_fd = uv.new_pipe(false)

    state.socket_fd:connect(config.socket_path, function(err)
        if err then
            vim.schedule(function()
                vim.notify("Failed to connect to MapStruct IPC: " .. err, vim.log.levels.ERROR)
            end)
            state.connected = false
            return
        end

        vim.schedule(function()
            vim.notify("Connected to MapStruct IPC server", vim.log.levels.INFO)
        end)

        state.connected = true

        -- Start reading responses
        state.socket_fd:read_start(function(read_err, data)
            if read_err then
                vim.schedule(function()
                    vim.notify("Socket read error: " .. read_err, vim.log.levels.ERROR)
                    M.disconnect()
                end)
                return
            end

            if data then
                M._handle_data(data)
            else
                -- Connection closed
                vim.schedule(function()
                    vim.notify("MapStruct IPC server disconnected", vim.log.levels.WARN)
                    M.disconnect()
                end)
            end
        end)

        -- Start heartbeat timer
        M._start_heartbeat()
    end)

    return true
end

-- Handle incoming data from socket
function M._handle_data(data)
    state.read_buffer = state.read_buffer .. data

    -- Process complete lines (newline-delimited JSON)
    while true do
        local newline_pos = state.read_buffer:find("\n")
        if not newline_pos then
            break
        end

        local line = state.read_buffer:sub(1, newline_pos - 1)
        state.read_buffer = state.read_buffer:sub(newline_pos + 1)

        if line ~= "" then
            vim.schedule(function()
                M._handle_response(line)
            end)
        end
    end
end

-- Handle a complete JSON response
function M._handle_response(json_str)
    local ok, response = pcall(vim.json.decode, json_str)
    if not ok then
        vim.notify("Failed to decode JSON response: " .. json_str, vim.log.levels.ERROR)
        return
    end

    local request_id = response.id
    if request_id and state.pending_requests[request_id] then
        local callback = state.pending_requests[request_id]
        state.pending_requests[request_id] = nil

        if response.error then
            vim.notify("Request error: " .. response.error, vim.log.levels.ERROR)
            callback(nil, response.error)
        else
            callback(response.result, nil)
        end
    end
end

-- Send a request to the server
function M.request(method, params, callback)
    if not state.connected then
        vim.notify("Not connected to MapStruct IPC server", vim.log.levels.ERROR)
        if callback then
            callback(nil, "Not connected")
        end
        return
    end

    local request_id = generate_request_id()
    local request = {
        id = request_id,
        method = method,
        params = params or {}
    }

    local json_str = vim.json.encode(request) .. "\n"

    -- Store callback for this request
    if callback then
        state.pending_requests[request_id] = callback

        -- Set timeout for request
        vim.defer_fn(function()
            if state.pending_requests[request_id] then
                state.pending_requests[request_id] = nil
                vim.notify("Request timeout: " .. method, vim.log.levels.WARN)
                callback(nil, "timeout")
            end
        end, config.request_timeout_ms)
    end

    -- Send request
    state.socket_fd:write(json_str, function(err)
        if err then
            vim.schedule(function()
                vim.notify("Failed to send request: " .. err, vim.log.levels.ERROR)
                if callback and state.pending_requests[request_id] then
                    state.pending_requests[request_id] = nil
                    callback(nil, err)
                end
            end)
        end
    end)
end

-- Send heartbeat to keep connection alive
function M._send_heartbeat()
    if not state.connected then
        return
    end

    M.request("heartbeat", {}, function(result, err)
        if err then
            vim.notify("Heartbeat failed: " .. err, vim.log.levels.WARN)
        end
    end)
end

-- Start heartbeat timer
function M._start_heartbeat()
    if state.heartbeat_timer then
        return
    end

    state.heartbeat_timer = vim.loop.new_timer()
    state.heartbeat_timer:start(
        config.heartbeat_interval_ms,
        config.heartbeat_interval_ms,
        vim.schedule_wrap(function()
            M._send_heartbeat()
        end)
    )
end

-- Stop heartbeat timer
function M._stop_heartbeat()
    if state.heartbeat_timer then
        state.heartbeat_timer:stop()
        state.heartbeat_timer:close()
        state.heartbeat_timer = nil
    end
end

-- Disconnect from server
function M.disconnect()
    M._stop_heartbeat()

    if state.socket_fd then
        state.socket_fd:close()
        state.socket_fd = nil
    end

    state.connected = false
    state.pending_requests = {}
    state.read_buffer = ""
end

-- Stop server
function M.stop_server()
    -- Send shutdown request
    if state.connected then
        M.request("shutdown", {}, function()
            -- Server will shut down
        end)
    end

    -- Give server time to shut down gracefully
    vim.defer_fn(function()
        if state.server_job_id then
            vim.fn.jobstop(state.server_job_id)
            state.server_job_id = nil
        end

        M.disconnect()

        -- Clean up socket file
        if config.socket_path and vim.fn.filereadable(config.socket_path) == 1 then
            vim.fn.delete(config.socket_path)
        end
    end, 100)
end

-- Restart server with updated classpath (useful when switching projects)
function M.restart_server()
    vim.notify("Restarting MapStruct IPC server...", vim.log.levels.INFO)
    M.stop_server()
    vim.defer_fn(function()
        start_server()
    end, 500)
end

-- Setup function
function M.setup(opts)
    config = vim.tbl_deep_extend("force", config, opts or {})

    -- Auto-cleanup on VimLeavePre
    vim.api.nvim_create_autocmd("VimLeavePre", {
        callback = function()
            M.stop_server()
        end,
    })

    -- Auto-start server
    if config.jar_path then
        start_server()
    end
end

-- Convenience functions for common operations
function M.ping(callback)
    M.request("ping", {}, callback)
end

function M.explore_path(class_name, path_expression, callback)
    M.request("explore_path", {
        className = class_name,
        pathExpression = path_expression
    }, callback)
end

return M
