## Architecture Diagram

```
┌─────────────────────────────────────────┐
│ Neovim Java File                        │
│ @Mapping(source = "user.address.|")     │
└──────────────┬──────────────────────────┘
               │ Type '.'
               ▼
┌─────────────────────────────────────────┐
│ context.lua (Treesitter)                │
│ - Detects @Mapping annotation           │
│ - Extracts source/target class of method│
│ - Resolves FQN by javap                 │
│ - Determines path: "user.address."      │
└──────────────┬──────────────────────────┘
               │ Context: {class, path}
               ▼
┌─────────────────────────────────────────┐
│ server.lua (Lifecycle Manager)          │
│ - Starts Java server (if not running)   │
│ - Uses jdtls classpath automatically    │
└──────────────┬──────────────────────────┘
               │ Server ready
               ▼
┌─────────────────────────────────────────┐
│ ipc_client.lua (IPC)                    │
│ - Connects via Unix socket              │
│ - Sends JSON request:                   │
│   {className, pathExpression}           │
└──────────────┬──────────────────────────┘
               │ JSON over socket
               ▼
┌─────────────────────────────────────────┐
│ Java IPC Server                         │
│ - Loads class via reflection            │
│ - Explores fields and methods           │
│ - Returns JSON with completions         │
└──────────────┬──────────────────────────┘
               │ JSON response
               ▼
┌─────────────────────────────────────────┐
│ init.lua (blink.cmp source)             │
│ - Parses response                       │
│ - Formats for blink.cmp                 │
│ - Shows in completion menu              │
└──────────────┬──────────────────────────┘
               │ Completion items
               ▼
┌─────────────────────────────────────────┐
│ blink.cmp UI                            │
│ 󰜢 street   String   Field   [MS]        │
│ 󰜢 city     String   Method  [MS]        │
│ 󰜢 zipCode  String   Field   [MS]        │
└─────────────────────────────────────────┘
