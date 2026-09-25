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
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class IpcServerRunner {

    /**
     * System property (milliseconds) bounding how long the server waits for its first client. An
     * editor that gave up connecting, or a stray manual start, must not leave a JVM running forever:
     * the idle monitor only exists once a client is connected.
     */
    public static final String ACCEPT_TIMEOUT_PROPERTY = "mapstruct.ipc.acceptTimeoutMs";
    private static final long DEFAULT_ACCEPT_TIMEOUT_MS = 60_000;

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
        Path path = Path.of(socketPath);

        ServerSocketChannel server;
        try {
            // Delete existing socket file if it exists
            if (Files.deleteIfExists(path)) {
                log.info("Deleted existing socket file: {}", path);
            }
            server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            server.bind(UnixDomainSocketAddress.of(path));
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException e) {
            // Not a client problem: an unusable path (too long for AF_UNIX, unwritable dir, ...) or a
            // platform without Unix domain sockets. Report it and exit non-zero so the editor notices.
            log.error("Cannot bind Unix domain socket {}: {}", socketPath, e.getMessage(), e);
            printError("Cannot bind Unix domain socket " + socketPath + ": " + e.getMessage());
            return 1;
        }

        log.info("Server socket bound successfully to {}", socketPath);
        Object socketFileKey = fileKey(path);

        // Add shutdown hook to stop the executor and remove our socket file
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("JVM shutdown hook triggered");
            System.out.println("Shutting down client executor...");
            // Just initiate shutdown, don't wait - let JVM handle it
            clientExecutor.shutdownNow();
            log.info("Executor shutdown initiated");
            deleteOwnSocketFile(path, socketFileKey);
        }, "MapStruct-Shutdown"));

        System.out.println("IPC server started on " + socketPath);
        log.info("IPC server ready - waiting for client connections");

        AtomicBoolean clientConnected = new AtomicBoolean(false);
        startNoClientWatchdog(clientConnected, acceptTimeoutMs());

        try {
            while (true) {
                log.debug("Waiting for client connection...");
                SocketChannel client = server.accept();
                clientConnected.set(true);
                log.info("Client connected from socket");

                // Submit client handling to thread pool
                clientExecutor.submit(() -> {
                    try {
                        log.debug("Starting client handler thread");
                        IpcClientMessageListener.handleClient(client);
                    } catch (Throwable t) {
                        // handleClient answers every request itself; anything reaching here is a bug.
                        // Catch Throwable: with catch(Exception) an Error would vanish into the Future.
                        log.error("Error handling client", t);
                        System.err.println("Error handling client: " + t);
                        t.printStackTrace(System.err);
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

    private static long acceptTimeoutMs() {
        String value = System.getProperty(ACCEPT_TIMEOUT_PROPERTY);
        if (value == null || value.isBlank()) {
            return DEFAULT_ACCEPT_TIMEOUT_MS;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Ignoring invalid {}={}", ACCEPT_TIMEOUT_PROPERTY, value);
            return DEFAULT_ACCEPT_TIMEOUT_MS;
        }
    }

    /**
     * Exits the JVM if no client has connected {@code timeoutMs} after the socket was bound.
     */
    private static void startNoClientWatchdog(AtomicBoolean clientConnected, long timeoutMs) {
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(timeoutMs);
            } catch (InterruptedException e) {
                return;
            }
            if (!clientConnected.get()) {
                log.warn("No client connected within {} ms - exiting", timeoutMs);
                System.out.println("No client connected within " + timeoutMs + " ms, exiting.");
                System.exit(0);
            }
        }, "MapStruct-Accept-Watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    /**
     * Identity (device + inode) of the socket file we bound, or null if it cannot be read.
     */
    private static Object fileKey(Path path) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class).fileKey();
        } catch (IOException e) {
            log.warn("Cannot read socket file attributes for {}: {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * Deletes the socket file on exit, but only while it is still the one this process bound.
     * The editor reuses the same path for a replacement server, so a superseded process that is
     * exiting late must not unlink the file its successor is listening on.
     */
    static void deleteOwnSocketFile(Path path, Object ownFileKey) {
        try {
            if (!Files.exists(path)) {
                return;
            }
            Object currentFileKey = Files.readAttributes(path, BasicFileAttributes.class).fileKey();
            if (ownFileKey == null || !ownFileKey.equals(currentFileKey)) {
                log.info("Socket file {} now belongs to another server - leaving it in place", path);
                return;
            }
            if (Files.deleteIfExists(path)) {
                log.info("Deleted socket file {}", path);
            }
        } catch (IOException e) {
            log.warn("Could not delete socket file {}: {}", path, e.getMessage());
        }
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
