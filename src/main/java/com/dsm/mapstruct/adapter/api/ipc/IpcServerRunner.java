package com.dsm.mapstruct.adapter.api.ipc;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;

public class IpcServerRunner {

    /**
     * Runs the tool with the given arguments and returns an exit code.
     * This method is package-private to allow testing without calling System.exit().
     *
     * @param args command-line arguments
     * @return 0 for success, 1 for error
     */
    public static int run(String[] args) {
        String socketPath;
        if (args.length < 1 || StringUtils.isEmpty(socketPath = args[0])) {
            printUsage();
            return 1;
        }

        try {
            Path path = Path.of(socketPath);
            Files.deleteIfExists(path);

            UnixDomainSocketAddress address = UnixDomainSocketAddress.of(path);
            ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            server.bind(address);

            System.out.println("IPC server started on " + socketPath);

            while (true) {
                SocketChannel client = server.accept();
                IpcClientMessageListener.handleClient(client);
            }
        } catch (IOException e) {
            // happens when socket disappears or Neovim dies
            System.out.println("Socket closed, exiting.");


            // ExplorePathUseCase explorePathUseCase = new ExplorePathUseCase();
            // Class<?> clazz = Class.forName(className);
            // ExplorePathUseCase.ExplorePathParams params = new ExplorePathUseCase.ExplorePathParams(clazz, pathExpression);
            // System.out.println(explorePathUseCase.execute(params));

            // } catch (ClassNotFoundException e) {
            //     printError("Class not found: " + className);
            //     System.err.println("Make sure the class is on the classpath.");
            //     System.err.println("\nExample:");
            //     System.err.println("  java -cp \"mapstruct-path-explorer.jar:path/to/your/classes\" \\");
            //     System.err.println("       com.dsm.mapstruct.MapStructPathTool \\");
            //     System.err.println("       \"" + className + "\" \"" + pathExpression + "\"");
            //     return 1;
        } catch (Exception e) {
            printError("Error processing path: " + e.getMessage());
            e.printStackTrace(System.err);
            return 1;
        }
        return 0;
    }

    private static void printUsage() {
        System.err.println("MapStruct Path Completion - IPC:");
        System.err.println();
        System.err.println("Usage:");
        System.err.println("  java -cp mapstruct-path-explorer.jar com.dsm.mapstruct.IpcServer [unique process socketPath]");
        System.err.println();
        System.err.println("With custom classpath:");
        System.err.println("  java -cp \"mapstruct-path-explorer.jar:path/to/classes\" com.dsm.mapstruct.IpcServer [unique process socketPath]");
    }

    private static void printError(String message) {
        System.err.println("Error: " + message);
    }
}
