# Architecture Diagram

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
│ Java Process                                            │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ IpcServer.java                                      │ │
│ │ • Creates Unix domain socket                        │ │
│ │ • Listens for connections                           │ │
│ └────────────────┬────────────────────────────────────┘ │
│                  │                                      │
│                  ▼                                      │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ IpcClientMessageListener.java                       │ │
│ │ • Parses JSON requests                              │ │
│ │ • Monitors heartbeat (30s timeout)                  │ │
│ │ • Processes methods (ping, explore_path, etc.)      │ │
│ │ • Returns JSON responses                            │ │
│ └────────────────┬────────────────────────────────────┘ │
│                  │                                      │
│                  ▼                                      │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ MapStruct Path Explorer                             │ │
│ │ • Uses jdtls-provided classpath                     │ │
│ │ • Resolves classes from all modules                 │ │
│ │ • Provides path completion suggestions              │ │
│ └─────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

## References

- [nvim-jdtls](https://github.com/mfussenegger/nvim-jdtls)
- [Java Unix Domain Sockets](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/UnixDomainSocketAddress.html)
- [Neovim libuv bindings](https://neovim.io/doc/user/luvref.html)
