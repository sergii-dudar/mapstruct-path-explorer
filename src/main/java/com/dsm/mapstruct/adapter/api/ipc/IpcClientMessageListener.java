package com.dsm.mapstruct.adapter.api.ipc;

import com.dsm.mapstruct.core.model.CompletionResult;
import com.dsm.mapstruct.core.model.SourceParameter;
import com.dsm.mapstruct.core.usecase.ExplorePathUseCase;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class IpcClientMessageListener {
    private static final Gson gson = new Gson();
    private static final long HEARTBEAT_TIMEOUT_MS = 30000; // 30 seconds
    private static final ExplorePathUseCase explorePathUseCase = new ExplorePathUseCase();

    public static void handleClient(SocketChannel client) {
        AtomicLong lastHeartbeat = new AtomicLong(System.currentTimeMillis());

        // Start heartbeat monitor thread
        Thread heartbeatMonitor = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(5000); // Check every 5 seconds
                    long timeSinceLastHeartbeat = System.currentTimeMillis() - lastHeartbeat.get();
                    if (timeSinceLastHeartbeat > HEARTBEAT_TIMEOUT_MS) {
                        System.out.println("Client heartbeat timeout - Neovim may have crashed or closed");
                        System.exit(0); // Shutdown daemon
                    }
                }
            } catch (InterruptedException e) {
                // Normal shutdown
            }
        });
        heartbeatMonitor.setDaemon(true);
        heartbeatMonitor.start();

        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(Channels.newInputStream(client)));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(Channels.newOutputStream(client)))
            ) {
            String line;
            while ((line = in.readLine()) != null) {
                lastHeartbeat.set(System.currentTimeMillis());

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

                    System.out.println("Received request - method: " + method + ", id: " + id);

                    JsonObject response = new JsonObject();
                    if (id != null) {
                        response.addProperty("id", id);
                    }

                    if (method == null) {
                        response.addProperty("error", "Missing 'method' field");
                    } else {
                        switch (method) {
                            case "ping":
                                JsonObject pongResult = new JsonObject();
                                pongResult.addProperty("message", "pong");
                                response.add("result", pongResult);
                                break;

                            case "heartbeat":
                                JsonObject heartbeatResult = new JsonObject();
                                heartbeatResult.addProperty("status", "alive");
                                response.add("result", heartbeatResult);
                                break;

                            case "shutdown":
                                JsonObject shutdownResult = new JsonObject();
                                shutdownResult.addProperty("message", "shutting down");
                                response.add("result", shutdownResult);
                                out.write(gson.toJson(response) + "\n");
                                out.flush();
                                System.out.println("Shutdown requested by client");
                                System.exit(0);
                                return;

                            case "explore_path":
                                String pathExpression = params.has("pathExpression") ? params.get("pathExpression").getAsString() : null;
                                boolean isEnum = params.has("isEnum") && params.get("isEnum").getAsBoolean();

                                // Parse sources array (new protocol)
                                List<SourceParameter> sources = new ArrayList<>();
                                if (params.has("sources") && params.get("sources").isJsonArray()) {
                                    Type sourceListType = new TypeToken<List<SourceParameter>>(){}.getType();
                                    sources = gson.fromJson(params.get("sources"), sourceListType);
                                }

                                if (sources.isEmpty() || pathExpression == null) {
                                    response.addProperty("error", "Missing required params: sources (array), pathExpression");
                                } else {
                                    try {
                                        // Execute path exploration with multi-parameter support
                                        ExplorePathUseCase.ExplorePathParams exploreParams =
                                            new ExplorePathUseCase.ExplorePathParams(sources, pathExpression, isEnum);
                                        String resultJson = explorePathUseCase.execute(exploreParams);

                                        // Parse the result and return it
                                        JsonObject resultObj = JsonParser.parseString(resultJson).getAsJsonObject();
                                        response.add("result", resultObj);

                                    } catch (Exception e) {
                                        response.addProperty("error", "Error exploring path: " + e.getMessage());
                                        e.printStackTrace();
                                    }
                                }
                                break;

                            default:
                                response.addProperty("error", "Unknown method: " + method);
                        }
                    }

                    out.write(gson.toJson(response) + "\n");
                    out.flush();
                } catch (JsonSyntaxException e) {
                    System.err.println("Invalid JSON received: " + line);
                    JsonObject errorResponse = new JsonObject();
                    errorResponse.addProperty("error", "Invalid JSON: " + e.getMessage());
                    out.write(gson.toJson(errorResponse) + "\n");
                    out.flush();
                }
            }

            System.out.println("Client disconnected normally");
            heartbeatMonitor.interrupt();
            System.exit(0); // Shutdown daemon when client disconnects
            } catch (IOException e) {
            System.out.println("Client connection error: " + e.getMessage());
            System.exit(0); // Shutdown daemon on connection error
            }
    }
}
