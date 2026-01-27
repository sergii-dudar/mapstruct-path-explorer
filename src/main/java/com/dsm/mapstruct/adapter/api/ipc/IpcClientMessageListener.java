package com.dsm.mapstruct.adapter.api.ipc;

import com.dsm.mapstruct.core.model.SourceParameter;
import com.dsm.mapstruct.core.usecase.ExplorePathUseCase;
import com.dsm.mapstruct.core.usecase.ExploreTypeSourceUseCase;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class IpcClientMessageListener {

    private static final Gson gson = new Gson();
    private static final long HEARTBEAT_TIMEOUT_MS = 30000; // 30 seconds
    private static final ExplorePathUseCase explorePathUseCase = new ExplorePathUseCase();
    private static final ExploreTypeSourceUseCase exploreTypeSourceUseCase = new ExploreTypeSourceUseCase();

    public static void handleClient(SocketChannel client) {
        log.info("New client connected: {}", client);
        AtomicLong lastHeartbeat = new AtomicLong(System.currentTimeMillis());

        // Start heartbeat monitor thread
        Thread heartbeatMonitor = new Thread(() -> {
            log.debug("Starting heartbeat monitor thread");
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(5000); // Check every 5 seconds
                    long timeSinceLastHeartbeat = System.currentTimeMillis() - lastHeartbeat.get();
                    if (timeSinceLastHeartbeat > HEARTBEAT_TIMEOUT_MS) {
                        log.warn("Client heartbeat timeout ({} ms) - Neovim may have crashed or closed", timeSinceLastHeartbeat);
                        System.out.println("Client heartbeat timeout - Neovim may have crashed or closed");
                        log.info("Shutting down server due to heartbeat timeout");
                        System.exit(0); // Shutdown daemon
                    }
                }
            } catch (InterruptedException e) {
                log.debug("Heartbeat monitor interrupted - normal shutdown");
                // Normal shutdown
            }
        });
        heartbeatMonitor.setDaemon(true);
        heartbeatMonitor.start();

        try (
             BufferedReader in = new BufferedReader(new InputStreamReader(Channels.newInputStream(client)));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(Channels.newOutputStream(client)))
        ) {
            log.debug("Client streams initialized");
            String line;
            while ((line = in.readLine()) != null) {
                lastHeartbeat.set(System.currentTimeMillis());
                log.debug("Received raw request: {}", line);

                try {
                    JsonObject request = JsonParser.parseString(line).getAsJsonObject();
                    String id = request.has("id") ? request.get("id").getAsString() : null;
                    String method = request.has("method") ? request.get("method").getAsString() : null;

                    // Handle params - can be object or array
                    JsonObject params = new JsonObject();
                    if (request.has("params")) {
                        var paramsElement = request.get("params");
                        if (paramsElement.isJsonObject()) {
                            params = paramsElement.getAsJsonObject();
                        } else if (paramsElement.isJsonArray()) {
                            // If it's an array, just use empty object
                            params = new JsonObject();
                        }
                    }

                    log.info("Processing request - method: {}, id: {}", method, id);
                    System.out.println("Received request - method: " + method + ", id: " + id);

                    JsonObject response = new JsonObject();
                    if (id != null) {
                        response.addProperty("id", id);
                    }

                    if (method == null) {
                        log.error("Request missing 'method' field");
                        response.addProperty("error", "Missing 'method' field");
                    } else {
                        switch (method) {
                            case "ping":
                                log.debug("Handling ping request");
                                JsonObject pongResult = new JsonObject();
                                pongResult.addProperty("message", "pong");
                                response.add("result", pongResult);
                                break;

                            case "heartbeat":
                                log.debug("Handling heartbeat request");
                                JsonObject heartbeatResult = new JsonObject();
                                heartbeatResult.addProperty("status", "alive");
                                response.add("result", heartbeatResult);
                                break;

                            case "shutdown":
                                log.info("Shutdown requested by client");
                                JsonObject shutdownResult = new JsonObject();
                                shutdownResult.addProperty("message", "shutting down");
                                response.add("result", shutdownResult);
                                out.write(gson.toJson(response) + "\n");
                                out.flush();
                                System.out.println("Shutdown requested by client");
                                log.info("Shutting down server gracefully");
                                System.exit(0);
                                return;

                            case "explore_path":
                                log.debug("Handling explore_path request");
                                String pathExpression = params.has("pathExpression") ? params.get("pathExpression").getAsString() : null;
                                boolean isEnum = params.has("isEnum") && params.get("isEnum").getAsBoolean();
                                log.debug("Path expression: {}, isEnum: {}", pathExpression, isEnum);

                                // Parse sources array (new protocol)
                                List<SourceParameter> sources = new ArrayList<>();
                                if (params.has("sources") && params.get("sources").isJsonArray()) {
                                    Type sourceListType = new TypeToken<List<SourceParameter>>() {
                                    }.getType();
                                    sources = gson.fromJson(params.get("sources"), sourceListType);
                                    log.debug("Parsed {} sources", sources.size());
                                }

                                if (sources.isEmpty() || pathExpression == null) {
                                    log.error("Missing required params - sources: {}, pathExpression: {}", sources.isEmpty() ? "empty" : "present", pathExpression);
                                    response.addProperty("error", "Missing required params: sources (array), pathExpression");
                                } else {
                                    try {
                                        log.debug("Executing path exploration for {} sources", sources.size());
                                        // Execute path exploration with multi-parameter support
                                        ExplorePathUseCase.ExplorePathParams exploreParams =
                                                new ExplorePathUseCase.ExplorePathParams(sources, pathExpression, isEnum);
                                        String resultJson = explorePathUseCase.execute(exploreParams);
                                        log.debug("Path exploration completed successfully");

                                        // Parse the result and return it
                                        JsonObject resultObj = JsonParser.parseString(resultJson).getAsJsonObject();
                                        response.add("result", resultObj);

                                    } catch (Exception e) {
                                        log.error("Error exploring path: {}", e.getMessage(), e);
                                        response.addProperty("error", "Error exploring path: " + e.getMessage());
                                        e.printStackTrace();
                                    }
                                }
                                break;

                            case "explore_type_source":
                                log.debug("Handling explore_type_source request");
                                String typeName = params.has("typeName") ? params.get("typeName").getAsString() : null;

                                if (typeName == null) {
                                    log.error("Missing required param - typeName: null");
                                    response.addProperty("error", "Missing required param: typeName");
                                } else {
                                    try {
                                        log.debug("Executing type source exploration for type: {}", typeName);
                                        // Load the class
                                        Class<?> clazz = Class.forName(typeName);

                                        // Execute type source exploration
                                        ExploreTypeSourceUseCase.ExploreTypeSourceParams exploreParams =
                                                new ExploreTypeSourceUseCase.ExploreTypeSourceParams(clazz);
                                        String resultJson = exploreTypeSourceUseCase.execute(exploreParams);
                                        log.debug("Type source exploration completed successfully");

                                        // Parse the result and return it
                                        JsonObject resultObj = JsonParser.parseString(resultJson).getAsJsonObject();
                                        response.add("result", resultObj);

                                    } catch (ClassNotFoundException e) {
                                        log.error("Class not found: {}", typeName, e);
                                        response.addProperty("error", "Class not found: " + typeName);
                                    } catch (Exception e) {
                                        log.error("Error exploring type source: {}", e.getMessage(), e);
                                        response.addProperty("error", "Error exploring type source: " + e.getMessage());
                                        e.printStackTrace();
                                    }
                                }
                                break;

                            default:
                                log.warn("Unknown method requested: {}", method);
                                response.addProperty("error", "Unknown method: " + method);
                        }
                    }

                    String responseJson = gson.toJson(response);
                    log.debug("Sending response: {}", responseJson);
                    out.write(responseJson + "\n");
                    out.flush();
                } catch (JsonSyntaxException e) {
                    log.error("Invalid JSON received: {}", line, e);
                    System.err.println("Invalid JSON received: " + line);
                    JsonObject errorResponse = new JsonObject();
                    errorResponse.addProperty("error", "Invalid JSON: " + e.getMessage());
                    out.write(gson.toJson(errorResponse) + "\n");
                    out.flush();
                }
            }

            log.info("Client disconnected normally (end of stream)");
            System.out.println("Client disconnected normally");
            heartbeatMonitor.interrupt();
            log.info("Shutting down server - client disconnected");
            System.exit(0); // Shutdown daemon when client disconnects
        } catch (IOException e) {
            log.error("Client connection error: {}", e.getMessage(), e);
            System.out.println("Client connection error: " + e.getMessage());
            log.info("Shutting down server due to connection error");
            System.exit(0); // Shutdown daemon on connection error
        }
    }
}
