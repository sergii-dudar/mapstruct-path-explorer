-- Example: Integration with nvim-jdtls for MapStruct IPC
--
-- Place this in your Neovim configuration (e.g., ~/.config/nvim/lua/config/jdtls.lua)
-- or add it to your ftplugin/java.lua

local jdtls = require('jdtls')
local mapstruct_client = require('mapstruct_ipc_client')

-- Your existing jdtls config
local config = {
    cmd = {
        'java',
        '-Declipse.application=org.eclipse.jdt.ls.core.id1',
        '-Dosgi.bundles.defaultStartLevel=4',
        '-Declipse.product=org.eclipse.jdt.ls.core.product',
        '-Dlog.protocol=true',
        '-Dlog.level=ALL',
        '-Xmx1g',
        '--add-modules=ALL-SYSTEM',
        '--add-opens', 'java.base/java.util=ALL-UNNAMED',
        '--add-opens', 'java.base/java.lang=ALL-UNNAMED',
        '-jar', '/path/to/jdtls/plugins/org.eclipse.equinox.launcher_*.jar',
        '-configuration', '/path/to/jdtls/config_linux',
        '-data', '/path/to/workspace/' .. vim.fn.fnamemodify(vim.fn.getcwd(), ':p:h:t')
    },
    root_dir = jdtls.setup.find_root({'.git', 'mvnw', 'gradlew', 'pom.xml', 'build.gradle'}),
    settings = {
        java = {
            -- Your java settings
        }
    },
    init_options = {
        bundles = {}
    },
}

-- Add on_attach callback to start MapStruct IPC server when jdtls starts
local original_on_attach = config.on_attach
config.on_attach = function(client, bufnr)
    -- Call original on_attach if it exists
    if original_on_attach then
        original_on_attach(client, bufnr)
    end

    -- Setup MapStruct IPC client
    -- This will automatically use jdtls classpath
    mapstruct_client.setup({
        jar_path = vim.fn.expand("~/path/to/mapstruct-path-explorer.jar"),
        use_jdtls_classpath = true, -- Default is true
        -- Optional: manual classpath as fallback
        -- classpath = nil,
    })

    -- Setup keymaps for MapStruct operations
    local opts = { noremap = true, silent = true, buffer = bufnr }

    -- Test connection
    vim.keymap.set('n', '<leader>mp', function()
        mapstruct_client.ping(function(result, err)
            if err then
                vim.notify("MapStruct ping failed: " .. err, vim.log.levels.ERROR)
            else
                vim.notify("MapStruct is alive: " .. vim.inspect(result), vim.log.levels.INFO)
            end
        end)
    end, vim.tbl_extend('force', opts, { desc = "MapStruct: Ping server" }))

    -- Explore path (example)
    vim.keymap.set('n', '<leader>me', function()
        -- Get word under cursor or visual selection as class name
        local class_name = vim.fn.input("Class name: ", "")
        local path_expr = vim.fn.input("Path expression: ", "")

        if class_name ~= "" and path_expr ~= "" then
            mapstruct_client.explore_path(class_name, path_expr, function(result, err)
                if err then
                    vim.notify("Explore failed: " .. err, vim.log.levels.ERROR)
                else
                    vim.notify("Result: " .. vim.inspect(result), vim.log.levels.INFO)
                end
            end)
        end
    end, vim.tbl_extend('force', opts, { desc = "MapStruct: Explore path" }))

    -- Restart server (useful when switching projects)
    vim.keymap.set('n', '<leader>mr', function()
        mapstruct_client.restart_server()
    end, vim.tbl_extend('force', opts, { desc = "MapStruct: Restart server" }))

    -- Disconnect
    vim.keymap.set('n', '<leader>md', function()
        mapstruct_client.stop_server()
    end, vim.tbl_extend('force', opts, { desc = "MapStruct: Stop server" }))
end

-- Start jdtls
jdtls.start_or_attach(config)

-- Alternative: Auto-restart MapStruct server when changing Java projects
-- This ensures the classpath is always up-to-date
vim.api.nvim_create_autocmd("LspAttach", {
    pattern = "*.java",
    callback = function(args)
        local client = vim.lsp.get_client_by_id(args.data.client_id)
        if client and client.name == "jdtls" then
            -- Wait a bit for jdtls to fully initialize
            vim.defer_fn(function()
                if mapstruct_client and mapstruct_client.restart_server then
                    mapstruct_client.restart_server()
                end
            end, 2000)
        end
    end,
})

-- Create user commands for convenience
vim.api.nvim_create_user_command("MapStructPing", function()
    mapstruct_client.ping(function(result, err)
        if err then
            vim.notify("Ping failed: " .. err, vim.log.levels.ERROR)
        else
            print("Ping successful: " .. vim.inspect(result))
        end
    end)
end, { desc = "Test MapStruct IPC connection" })

vim.api.nvim_create_user_command("MapStructRestart", function()
    mapstruct_client.restart_server()
end, { desc = "Restart MapStruct IPC server with updated classpath" })

vim.api.nvim_create_user_command("MapStructStop", function()
    mapstruct_client.stop_server()
end, { desc = "Stop MapStruct IPC server" })

vim.api.nvim_create_user_command("MapStructExplore", function(opts)
    local args = vim.split(opts.args, " ", { plain = true })
    if #args < 2 then
        vim.notify("Usage: MapStructExplore <className> <pathExpression>", vim.log.levels.ERROR)
        return
    end

    local class_name = args[1]
    local path_expr = table.concat(vim.list_slice(args, 2), " ")

    mapstruct_client.explore_path(class_name, path_expr, function(result, err)
        if err then
            vim.notify("Explore failed: " .. err, vim.log.levels.ERROR)
        else
            print("Explore result:\n" .. vim.inspect(result))
        end
    end)
end, { nargs = "+", desc = "Explore MapStruct path" })

-- Alternative minimal setup (if you prefer to keep jdtls config separate)
-- Add this to your ftplugin/java.lua:
--[[
local mapstruct = require('mapstruct_ipc_client')
mapstruct.setup({
    jar_path = vim.fn.expand("~/path/to/mapstruct-path-explorer.jar"),
    use_jdtls_classpath = true,
})
]]
