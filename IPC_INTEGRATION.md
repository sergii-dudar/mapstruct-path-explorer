# MapStruct Path Explorer - Neovim IPC Integration

This guide explains how to integrate the MapStruct Path Explorer with Neovim using Unix domain sockets for reliable IPC communication.

## Overview

The integration consists of two parts:
1. **Java IPC Server** (`IpcServer.java`) - Runs as a daemon and processes MapStruct path exploration requests
2. **Neovim Lua Client** (`mapstruct_ipc_client.lua`) - Connects to the server and provides a Lua API for Neovim

## Features

- **Unix Domain Socket Communication** - Reliable, low-latency IPC using local sockets
- **JSON Protocol** - Simple, extensible request/response format using `vim.json`
- **Automatic Lifecycle Management** - Server automatically shuts down when Neovim exits or crashes
- **Heartbeat Monitoring** - Detects disconnections and ensures clean shutdown
- **jdtls Integration** - Automatically uses classpath from `nvim-jdtls` including all project modules and dependencies
- **Multi-project Support** - Handles classpath for multi-module Maven/Gradle projects

## Requirements

- Java 17+ (for Unix domain socket support)
- Neovim 0.9+ (with `vim.loop` and `vim.json` support)
- `mfussenegger/nvim-jdtls` plugin (for automatic classpath resolution)
- Maven or Gradle project with MapStruct

## Installation

### 1. Build the JAR

```bash
mvn clean package
# Creates: target/mapstruct-path-explorer.jar
```

### 2. Install Lua Client

Copy `mapstruct_ipc_client.lua` to your Neovim configuration:

```bash
# For lazy.nvim or packer users
mkdir -p ~/.config/nvim/lua/
cp mapstruct_ipc_client.lua ~/.config/nvim/lua/

# Or create a plugin directory
mkdir -p ~/.local/share/nvim/site/pack/plugins/start/mapstruct-ipc/lua/
cp mapstruct_ipc_client.lua ~/.local/share/nvim/site/pack/plugins/start/mapstruct-ipc/lua/
```

### 3. Configure in Neovim

Add to your `ftplugin/java.lua` or jdtls configuration:

```lua
local mapstruct = require('mapstruct_ipc_client')

mapstruct.setup({
    -- Required: path to the MapStruct jar
    jar_path = vim.fn.expand("~/path/to/mapstruct-path-explorer.jar"),

    -- Automatic: uses jdtls classpath (default: true)
    use_jdtls_classpath = true,

    -- Optional: manual classpath as fallback
    -- classpath = "/path/to/additional/classes",

    -- Optional: custom Java command
    -- java_cmd = "/usr/bin/java",

    -- Optional: heartbeat interval (default: 10000ms)
    -- heartbeat_interval_ms = 10000,

    -- Optional: request timeout (default: 5000ms)
    -- request_timeout_ms = 5000,
})
```

See `nvim_jdtls_integration_example.lua` for a complete integration example with keymaps and commands.

## How It Works

### 1. Server Lifecycle

When you open a Java file with jdtls:

1. **Neovim starts the server**:
   ```bash
   java -cp mapstruct-path-explorer.jar:<jdtls-classpath> \
        com.dsm.mapstruct.IpcServer /tmp/mapstruct-ipc-<pid>.sock
   ```

2. **Server creates Unix socket**: Listens on `/tmp/mapstruct-ipc-<pid>.sock`

3. **Client connects**: Neovim connects to the socket using `vim.loop.new_pipe()`

4. **Heartbeat starts**: Client sends heartbeat every 10 seconds

5. **Server monitors**: If no heartbeat for 30 seconds, server exits (Neovim crashed)

6. **Clean shutdown**: When Neovim exits, client sends shutdown command

### 2. Classpath Resolution

The client automatically queries jdtls for the project classpath:

```lua
-- Queries jdtls using workspace/executeCommand
command: "java.project.getClasspaths"
arguments: [<file-uri>, { scope: "runtime" }]

-- Returns array of classpath entries:
[
  "/project/module1/target/classes",
  "/project/module2/target/classes",
  "~/.m2/repository/org/mapstruct/mapstruct/1.6.3/mapstruct-1.6.3.jar",
  ...
]
```

This ensures the server can resolve:
- All project modules (multi-module Maven/Gradle)
- All Maven/Gradle dependencies
- Source and compiled classes

### 3. JSON Protocol

All communication uses newline-delimited JSON:

**Request:**
```json
{
  "id": "1",
  "method": "explore_path",
  "params": {
    "className": "com.example.UserDTO",
    "pathExpression": "user.address."
  }
}
```

**Response:**
```json
{
  "id": "1",
  "result": {
    "className": "com.example.UserDTO",
    "pathExpression": "user.address.",
    "suggestions": ["street", "city", "zipCode"]
  }
}
```

**Error Response:**
```json
{
  "id": "1",
  "error": "Class not found: com.example.UserDTO"
}
```

### 4. Available Methods

- `ping` - Test connection
- `heartbeat` - Keep connection alive
- `shutdown` - Gracefully shut down server
- `explore_path` - Get MapStruct path suggestions (placeholder, needs implementation)

## Usage Examples

### Basic Usage

```lua
local mapstruct = require('mapstruct_ipc_client')

-- Test connection
mapstruct.ping(function(result, err)
    if err then
        print("Error: " .. err)
    else
        print("Pong: " .. vim.inspect(result))
    end
end)

-- Explore path
mapstruct.explore_path("com.example.UserDTO", "address.", function(result, err)
    if result then
        print("Suggestions: " .. vim.inspect(result.suggestions))
    end
end)

-- Restart server (e.g., after project change)
mapstruct.restart_server()
```

### With User Commands

```vim
:MapStructPing                           " Test connection
:MapStructExplore com.example.UserDTO address.  " Explore path
:MapStructRestart                        " Restart server
:MapStructStop                           " Stop server
```

### With Keymaps

```lua
vim.keymap.set('n', '<leader>mp', function()
    require('mapstruct_ipc_client').ping(function(result, err)
        print(vim.inspect(result or err))
    end)
end, { desc = "MapStruct: Ping" })

vim.keymap.set('n', '<leader>mr', function()
    require('mapstruct_ipc_client').restart_server()
end, { desc = "MapStruct: Restart" })
```

## Troubleshooting

### Server not starting

Check if jar path is correct:
```lua
print(vim.fn.filereadable(vim.fn.expand("~/path/to/mapstruct-path-explorer.jar")))
-- Should print 1
```

### Classpath issues

Manually check jdtls classpath:
```lua
:lua vim.print(vim.lsp.get_clients({ name = "jdtls" }))
```

Restart with updated classpath:
```vim
:MapStructRestart
```

### Socket connection issues

Check socket file exists:
```bash
ls -la /tmp/mapstruct-ipc-*.sock
```

Check server logs:
```bash
# Server outputs to stdout/stderr, visible in Neovim
:messages
```

### Heartbeat timeout

If server exits unexpectedly, check:
- Network/file system issues with socket
- Server logs for errors
- Increase timeout in config:
  ```lua
  heartbeat_interval_ms = 5000  -- Send more frequently
  ```

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────┐
│ Neovim                                                   │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ nvim-jdtls                                          │ │
│ │ • Resolves classpath from Maven/Gradle              │ │
│ │ • Returns all modules + dependencies                │ │
│ └────────────────┬────────────────────────────────────┘ │
│                  │ Classpath                             │
│                  ▼                                       │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ mapstruct_ipc_client.lua                            │ │
│ │ • Starts server with jdtls classpath                │ │
│ │ • Connects via Unix socket                          │ │
│ │ • Sends JSON requests                               │ │
│ │ • Monitors heartbeat                                │ │
│ └────────────────┬────────────────────────────────────┘ │
└──────────────────┼──────────────────────────────────────┘
                   │ Unix Socket
                   │ /tmp/mapstruct-ipc-<pid>.sock
                   ▼
┌─────────────────────────────────────────────────────────┐
│ Java Process                                             │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ IpcServer.java                                       │ │
│ │ • Creates Unix domain socket                        │ │
│ │ • Listens for connections                           │ │
│ └────────────────┬────────────────────────────────────┘ │
│                  │                                       │
│                  ▼                                       │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ IpcClientMessageListener.java                       │ │
│ │ • Parses JSON requests                              │ │
│ │ • Monitors heartbeat (30s timeout)                  │ │
│ │ • Processes methods (ping, explore_path, etc.)      │ │
│ │ • Returns JSON responses                            │ │
│ └────────────────┬────────────────────────────────────┘ │
│                  │                                       │
│                  ▼                                       │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ MapStruct Path Explorer                             │ │
│ │ • Uses jdtls-provided classpath                     │ │
│ │ • Resolves classes from all modules                 │ │
│ │ • Provides path completion suggestions              │ │
│ └─────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

## Next Steps

1. Implement actual path exploration logic in `IpcClientMessageListener.java`
2. Connect to existing `ExplorePathUseCase` or similar
3. Add completion integration (nvim-cmp or other)
4. Add caching for performance
5. Add tests for the IPC protocol

## References

- [nvim-jdtls](https://github.com/mfussenegger/nvim-jdtls)
- [Java Unix Domain Sockets](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/UnixDomainSocketAddress.html)
- [Neovim libuv bindings](https://neovim.io/doc/user/luvref.html)
