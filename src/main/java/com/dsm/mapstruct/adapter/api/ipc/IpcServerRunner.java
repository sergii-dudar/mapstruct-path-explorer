package com.dsm.mapstruct.adapter.api.ipc;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class IpcServerRunner {

    // Single-threaded executor for handling client connection (one server per client)
    private static final ExecutorService clientExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r);
        thread.setName("MapStruct-Client-Handler");
        thread.setDaemon(false);
        return thread;
    });

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

            // Add shutdown hook to gracefully stop executor
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down client executor...");
                shutdownExecutor();
            }));

            System.out.println("IPC server started on " + socketPath);

            while (true) {
                SocketChannel client = server.accept();
                // Submit client handling to thread pool
                clientExecutor.submit(() -> {
                    try {
                        IpcClientMessageListener.handleClient(client);
                    } catch (Exception e) {
                        System.err.println("Error handling client: " + e.getMessage());
                        e.printStackTrace(System.err);
                    }
                });
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

    /**
     * Gracefully shutdown the executor service.
     */
    private static void shutdownExecutor() {
        clientExecutor.shutdown();
        try {
            if (!clientExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                clientExecutor.shutdownNow();
                if (!clientExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("Executor did not terminate");
                }
            }
        } catch (InterruptedException e) {
            clientExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
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
