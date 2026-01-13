package com.dsm.mapstruct;

import com.dsm.mapstruct.adapter.api.ipc.IpcServerRunner;

// testing:
// http://www.dest-unreach.org/socat/
//
// socat STDIO UNIX-LISTEN:myuds,reuseaddr,fork
// socat UNIX-CLIENT:myuds STDIO
public class IpcServer {

    public static void main(String[] args) {
        int exitCode = IpcServerRunner.run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
