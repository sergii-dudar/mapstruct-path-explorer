package com.dsm.mapstruct.adapter.api.ipc;

import com.dsm.mapstruct.core.model.SourceParameter;
import com.dsm.mapstruct.core.usecase.ExplorePathUseCase;
import com.dsm.mapstruct.core.usecase.ExploreTypeSourceUseCase;
import com.dsm.mapstruct.core.util.DynamicClassLoaderUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-client request loop: reads newline-delimited JSON requests, dispatches them to the use cases
 * and writes exactly one JSON response line per request.
 * <p>
 * Every request line gets an answer. Malformed JSON, missing fields and any failure inside a use
 * case - including {@link Error}s such as {@link NoClassDefFoundError}, which reflection throws when
 * a class references a type that is missing from the classpath - become an {@code error} response
 * instead of tearing the connection down.
 * <p>
 * The server serves a single editor instance: when the client connection ends (end of stream, I/O
 * error, or no traffic for {@link #HEARTBEAT_TIMEOUT_MS} while idle) the JVM exits.
 */
@Slf4j
public class IpcClientMessageListener {

    static final long HEARTBEAT_TIMEOUT_MS = 30_000;
    private static final long HEARTBEAT_CHECK_INTERVAL_MS = 5_000;
    private static final Gson GSON = new Gson();
    private static final Type SOURCE_LIST_TYPE = new TypeToken<List<SourceParameter>>() {
    }.getType();

    private static final IpcClientMessageListener DEFAULT =
            new IpcClientMessageListener(new ExplorePathUseCase(), new ExploreTypeSourceUseCase());

    private final ExplorePathUseCase explorePathUseCase;
    private final ExploreTypeSourceUseCase exploreTypeSourceUseCase;

    public IpcClientMessageListener(ExplorePathUseCase explorePathUseCase,
                                    ExploreTypeSourceUseCase exploreTypeSourceUseCase) {
        this.explorePathUseCase = explorePathUseCase;
        this.exploreTypeSourceUseCase = exploreTypeSourceUseCase;
    }

    /**
     * Outcome of one request: the response to write and whether the server must exit afterwards.
     */
    public record Response(JsonObject json, boolean shutdown) {
    }

    /**
     * Serves the given client with the production use cases until the connection ends, then exits the JVM.
     */
    public static void handleClient(SocketChannel client) {
        DEFAULT.handle(client);
    }

    /**
     * Serves one client connection: every request line is answered, and the JVM exits once the
     * connection ends for any reason (one server process per editor instance).
     */
    public void handle(SocketChannel client) {
        log.info("New client connected: {}", client);
        AtomicLong lastActivity = new AtomicLong(System.currentTimeMillis());
        AtomicBoolean busy = new AtomicBoolean(false);
        Thread heartbeatMonitor = startHeartbeatMonitor(lastActivity, busy);

        int exitCode = 0;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(Channels.newInputStream(client)));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(Channels.newOutputStream(client)))) {
            String line;
            while ((line = in.readLine()) != null) {
                lastActivity.set(System.currentTimeMillis());
                if (line.isBlank()) {
                    continue; // keep-alive noise, not a request: nothing to answer
                }
                busy.set(true);
                Response response;
                try {
                    response = processRequest(line);
                } finally {
                    busy.set(false);
                    lastActivity.set(System.currentTimeMillis());
                }

                String responseJson = GSON.toJson(response.json());
                log.debug("Sending response: {}", responseJson);
                out.write(responseJson);
                out.write('\n');
                out.flush();

                if (response.shutdown()) {
                    log.info("Shutting down server gracefully");
                    heartbeatMonitor.interrupt();
                    System.exit(0);
                    return;
                }
            }
            log.info("Client disconnected normally (end of stream)");
        } catch (IOException e) {
            log.error("Client connection error: {}", e.getMessage(), e);
        } catch (Throwable t) {
            log.error("Unexpected failure in client handler", t);
            exitCode = 1;
        } finally {
            // Never leave a monitor behind: a stale one would exit the JVM under a later, healthy connection.
            heartbeatMonitor.interrupt();
        }

        log.info("Shutting down server - client connection ended (exit code {})", exitCode);
        System.exit(exitCode);
    }

    /**
     * Processes one raw request line. Never throws: malformed JSON, missing fields and any failure
     * inside a use case (including Errors) produce an {@code error} response.
     */
    public Response processRequest(String line) {
        log.debug("Received raw request: {}", line);
        JsonObject response = new JsonObject();

        String method;
        JsonObject params;
        try {
            JsonObject request = JsonParser.parseString(line).getAsJsonObject();
            String id = optionalString(request, "id");
            if (id != null) {
                response.addProperty("id", id);
            }
            method = optionalString(request, "method");
            params = extractParams(request);
            log.info("Processing request - method: {}, id: {}", method, id);
        } catch (JsonSyntaxException | IllegalStateException | UnsupportedOperationException e) {
            // JsonParser -> JsonSyntaxException; getAsJsonObject() on a non-object -> IllegalStateException;
            // getAsString() on an object or array -> UnsupportedOperationException.
            log.error("Invalid request: {}", line, e);
            response.addProperty("error", "Invalid request: " + e.getMessage());
            return new Response(response, false);
        }

        if (method == null) {
            log.error("Request missing 'method' field");
            response.addProperty("error", "Missing 'method' field");
            return new Response(response, false);
        }

        try {
            switch (method) {
                case "ping" -> response.add("result", singleProperty("message", "pong"));
                case "heartbeat" -> response.add("result", singleProperty("status", "alive"));
                case "shutdown" -> {
                    log.info("Shutdown requested by client");
                    response.add("result", singleProperty("message", "shutting down"));
                    return new Response(response, true);
                }
                case "explore_path" -> handleExplorePath(params, response);
                case "explore_type_source" -> handleExploreTypeSource(params, response);
                default -> {
                    log.warn("Unknown method requested: {}", method);
                    response.addProperty("error", "Unknown method: " + method);
                }
            }
        } catch (Throwable t) {
            log.error("Unexpected failure while processing request: {}", line, t);
            response.addProperty("error", "Internal error: " + describe(t));
        }
        return new Response(response, false);
    }

    private void handleExplorePath(JsonObject params, JsonObject response) {
        String pathExpression = optionalString(params, "pathExpression");
        boolean isEnum = optionalBoolean(params, "isEnum");

        List<SourceParameter> sources;
        try {
            sources = parseSources(params);
        } catch (RuntimeException e) {
            // Gson wraps the record constructor's validation failure (blank name/type) in a RuntimeException.
            String reason = rootCause(e).getMessage();
            log.error("Invalid 'sources' param: {}", reason);
            response.addProperty("error", "Invalid 'sources' param: " + reason);
            return;
        }

        if (sources.isEmpty() || pathExpression == null) {
            log.error("Missing required params - sources: {}, pathExpression: {}",
                    sources.isEmpty() ? "empty" : "present", pathExpression);
            response.addProperty("error", "Missing required params: sources (array), pathExpression");
            return;
        }

        try {
            log.debug("Exploring path '{}' from {} source(s), isEnum={}", pathExpression, sources.size(), isEnum);
            ExplorePathUseCase.ExplorePathParams exploreParams =
                    new ExplorePathUseCase.ExplorePathParams(sources, pathExpression, isEnum);
            String resultJson = explorePathUseCase.execute(exploreParams);
            response.add("result", JsonParser.parseString(resultJson).getAsJsonObject());
        } catch (Throwable t) {
            // One line at ERROR (this is mostly "class not compiled yet"); the stack trace only at DEBUG,
            // since server output is mirrored into the editor's log line by line.
            log.error("Error exploring path: {}", describe(t));
            log.debug("Error exploring path - stack trace", t);
            response.addProperty("error", "Error exploring path: " + describe(t));
        }
    }

    private void handleExploreTypeSource(JsonObject params, JsonObject response) {
        String typeName = optionalString(params, "typeName");
        if (typeName == null) {
            log.error("Missing required param - typeName: null");
            response.addProperty("error", "Missing required param: typeName");
            return;
        }

        try {
            log.debug("Exploring type source for: {}", typeName);
            ClassLoader freshClassLoader = DynamicClassLoaderUtil.createFreshClassLoader(typeName);
            try {
                Class<?> clazz = DynamicClassLoaderUtil.loadClass(typeName, freshClassLoader);
                ExploreTypeSourceUseCase.ExploreTypeSourceParams exploreParams =
                        new ExploreTypeSourceUseCase.ExploreTypeSourceParams(clazz);
                String resultJson = exploreTypeSourceUseCase.execute(exploreParams);
                response.add("result", JsonParser.parseString(resultJson).getAsJsonObject());
            } finally {
                DynamicClassLoaderUtil.closeQuietly(freshClassLoader);
            }
        } catch (Throwable t) {
            log.error("Error exploring type source: {}", describe(t));
            log.debug("Error exploring type source - stack trace", t);
            response.addProperty("error", "Error exploring type source: " + describe(t));
        }
    }

    private static List<SourceParameter> parseSources(JsonObject params) {
        JsonElement sources = params.get("sources");
        if (sources == null || !sources.isJsonArray()) {
            return List.of();
        }
        List<SourceParameter> parsed = GSON.fromJson(sources, SOURCE_LIST_TYPE);
        if (parsed == null) {
            return List.of();
        }
        return parsed.stream().filter(Objects::nonNull).toList();
    }

    /**
     * {@code params} may be an object, absent, or an array: Neovim's {@code vim.json.encode({})}
     * yields {@code []} because an empty Lua table has no object/array identity.
     */
    private static JsonObject extractParams(JsonObject request) {
        JsonElement params = request.get("params");
        return params != null && params.isJsonObject() ? params.getAsJsonObject() : new JsonObject();
    }

    private static String optionalString(JsonObject object, String member) {
        JsonElement element = object.get(member);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static boolean optionalBoolean(JsonObject object, String member) {
        JsonElement element = object.get(member);
        return element != null && !element.isJsonNull() && element.getAsBoolean();
    }

    private static JsonObject singleProperty(String name, String value) {
        JsonObject object = new JsonObject();
        object.addProperty(name, value);
        return object;
    }

    /**
     * Human-readable failure text. Exceptions usually carry a self-explanatory message; Errors such as
     * NoClassDefFoundError carry only a class name, so their type is included.
     */
    static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        boolean hasMessage = message != null && !message.isBlank();
        if (throwable instanceof Exception && hasMessage) {
            return message;
        }
        return throwable.getClass().getSimpleName() + (hasMessage ? ": " + message : "");
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * Exits the JVM when the client has been silent for longer than {@link #HEARTBEAT_TIMEOUT_MS}
     * while no request is being processed (a long-running request is activity, not silence).
     */
    private static Thread startHeartbeatMonitor(AtomicLong lastActivity, AtomicBoolean busy) {
        Thread monitor = new Thread(() -> {
            log.debug("Starting heartbeat monitor thread");
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(HEARTBEAT_CHECK_INTERVAL_MS);
                    long idleMs = System.currentTimeMillis() - lastActivity.get();
                    if (!busy.get() && idleMs > HEARTBEAT_TIMEOUT_MS) {
                        log.warn("Client heartbeat timeout ({} ms) - Neovim may have crashed or closed", idleMs);
                        log.info("Shutting down server due to heartbeat timeout");
                        System.exit(0);
                    }
                }
            } catch (InterruptedException e) {
                log.debug("Heartbeat monitor interrupted - normal shutdown");
            }
        }, "MapStruct-Heartbeat-Monitor");
        monitor.setDaemon(true);
        monitor.start();
        return monitor;
    }
}
