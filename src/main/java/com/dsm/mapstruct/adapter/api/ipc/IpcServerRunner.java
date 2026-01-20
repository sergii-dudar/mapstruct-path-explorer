package com.dsm.mapstruct.adapter.api.ipc;

import lombok.extern.slf4j.Slf4j;
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

@Slf4j
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
        log.info("=== MapStruct IPC Server Starting ===");
        log.info("Java version: {}", System.getProperty("java.version"));
        log.info("User home: {}", System.getProperty("user.home"));

        String socketPath;
        if (args.length < 1 || StringUtils.isEmpty(socketPath = args[0])) {
            log.error("No socket path provided in arguments");
            printUsage();
            return 1;
        }

        log.info("Socket path: {}", socketPath);

        try {
            Path path = Path.of(socketPath);

            // Delete existing socket file if it exists
            if (Files.exists(path)) {
                log.info("Deleting existing socket file: {}", path);
                Files.deleteIfExists(path);
            }

            UnixDomainSocketAddress address = UnixDomainSocketAddress.of(path);
            ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            server.bind(address);

            log.info("Server socket bound successfully to {}", socketPath);

            // Add shutdown hook to gracefully stop executor
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("JVM shutdown hook triggered");
                System.out.println("Shutting down client executor...");
                // Just initiate shutdown, don't wait - let JVM handle it
                clientExecutor.shutdownNow();
                log.info("Executor shutdown initiated");
            }));

            System.out.println("IPC server started on " + socketPath);
            log.info("IPC server ready - waiting for client connections");

            while (true) {
                log.debug("Waiting for client connection...");
                SocketChannel client = server.accept();
                log.info("Client connected from socket");

                // Submit client handling to thread pool
                clientExecutor.submit(() -> {
                    try {
                        log.debug("Starting client handler thread");
                        IpcClientMessageListener.handleClient(client);
                    } catch (Exception e) {
                        log.error("Error handling client", e);
                        System.err.println("Error handling client: " + e.getMessage());
                        e.printStackTrace(System.err);
                    }
                });
            }
        } catch (IOException e) {
            // happens when socket disappears or Neovim dies
            log.warn("Socket I/O error - likely client disconnected: {}", e.getMessage());
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
            log.error("Unexpected error in server", e);
            printError("Error processing path: " + e.getMessage());
            e.printStackTrace(System.err);
            return 1;
        }

        log.info("Server shutting down normally");
        return 0;
    }

    /**
     * Gracefully shutdown the executor service.
     */
    private static void shutdownExecutor() {
        log.info("Shutting down executor service");
        clientExecutor.shutdown();
        try {
            if (!clientExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Executor did not terminate in 5 seconds, forcing shutdown");
                clientExecutor.shutdownNow();
                if (!clientExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.error("Executor did not terminate even after forced shutdown");
                    System.err.println("Executor did not terminate");
                }
            } else {
                log.info("Executor shutdown completed successfully");
            }
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting for executor shutdown", e);
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
