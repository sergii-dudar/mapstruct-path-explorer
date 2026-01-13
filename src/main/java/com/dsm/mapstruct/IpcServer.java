package com.dsm.mapstruct;

import com.dsm.mapstruct.adapter.api.ipc.IpcServerRunner;

// testing:
// http://www.dest-unreach.org/socat/
//
// socat STDIO UNIX-LISTEN:myuds,reuseaddr,fork
// socat UNIX-CLIENT:myuds STDIO
/**
 * Main entry point for the MapStruct Path Completion IPC deamon.
 *
 * Usage example:
 * java -cp mapstruct-path-explorer.jar:[app cp] com.dsm.mapstruct.IpcServer /tmp/test-ipc.sock
 */
public class IpcServer {

    public static void main(String[] args) {
        int exitCode = IpcServerRunner.run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
