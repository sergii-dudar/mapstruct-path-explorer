package com.dsm.mapstruct;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IpcServerTest {

    private static Process serverProcess;
    private static Path socketPath;
    private static SocketChannel client;
    private static BufferedReader in;
    private static BufferedWriter out;
    private static final Gson gson = new Gson();

    @BeforeAll
    static void startServer() throws Exception {
        // Create socket path
        socketPath = Path.of("/tmp/test-mapstruct-ipc-" + System.currentTimeMillis() + ".sock");
        Files.deleteIfExists(socketPath);

        // Get JAR path and test classes
        String jarPath = "target/mapstruct-path-explorer.jar";
        String testClassesPath = "target/test-classes";
        assertThat(new File(jarPath)).exists();

        // Include test-classes in classpath so we can test with test domain classes
        String classpath = jarPath + ":" + testClassesPath;

        // Start server process
        ProcessBuilder pb = new ProcessBuilder(
                "java",
                "-cp",
                classpath,
                "com.dsm.mapstruct.IpcServer",
                socketPath.toString()
        );
        pb.redirectErrorStream(true);
        serverProcess = pb.start();

        // Wait for socket to be created
        int attempts = 0;
        while (!Files.exists(socketPath) && attempts < 50) {
            Thread.sleep(100);
            attempts++;
        }

        assertThat(socketPath).exists();

        // Connect client
        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socketPath);
        client = SocketChannel.open(StandardProtocolFamily.UNIX);
        client.connect(address);

        in = new BufferedReader(new InputStreamReader(Channels.newInputStream(client)));
        out = new BufferedWriter(new OutputStreamWriter(Channels.newOutputStream(client)));
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (client != null) {
            client.close();
        }
        if (serverProcess != null) {
            serverProcess.destroy();
            serverProcess.waitFor();
        }
        if (socketPath != null) {
            Files.deleteIfExists(socketPath);
        }
    }

    private JsonObject sendRequest(String method, JsonObject params) throws IOException {
        JsonObject request = new JsonObject();
        request.addProperty("id", String.valueOf(System.currentTimeMillis()));
        request.addProperty("method", method);
        request.add("params", params == null ? new JsonObject() : params);

        String requestJson = gson.toJson(request) + "\n";
        out.write(requestJson);
        out.flush();

        String responseLine = in.readLine();
        assertThat(responseLine).isNotNull();

        return JsonParser.parseString(responseLine).getAsJsonObject();
    }

    @Test
    @Order(1)
    void testPing() throws IOException {
        JsonObject response = sendRequest("ping", null);

        assertThat(response.has("error")).isFalse();
        assertThat(response.has("result")).isTrue();
        assertThat(response.get("result").getAsJsonObject().get("message").getAsString())
                .isEqualTo("pong");
    }

    @Test
    @Order(2)
    void testHeartbeat() throws IOException {
        JsonObject response = sendRequest("heartbeat", null);

        assertThat(response.has("error")).isFalse();
        assertThat(response.has("result")).isTrue();
        assertThat(response.get("result").getAsJsonObject().get("status").getAsString())
                .isEqualTo("alive");
    }

    @Test
    @Order(3)
    void testExplorePathRootLevel() throws IOException {
        JsonObject params = new JsonObject();
        params.addProperty("className", "com.dsm.mapstruct.testdata.TestClasses$Person");
        params.addProperty("pathExpression", "");

        JsonObject response = sendRequest("explore_path", params);

        System.out.println("Root level response: " + gson.toJson(response));

        assertThat(response.has("error")).isFalse();
        assertThat(response.has("result")).isTrue();

        JsonObject result = response.get("result").getAsJsonObject();
        assertThat(result.has("className")).isTrue();
        assertThat(result.has("completions")).isTrue();

        // Should have fields like id, name, email, address, etc.
        var completions = result.get("completions").getAsJsonArray();
        assertThat(completions.size()).isGreaterThan(0);

        // Print completions for inspection
        System.out.println("Completions found: " + completions.size());
        completions.forEach(item -> {
            JsonObject field = item.getAsJsonObject();
            System.out.println(String.format("  - %s: %s (%s)",
                    field.get("name").getAsString(),
                    field.get("type").getAsString(),
                    field.get("kind").getAsString()
            ));
        });
    }

    @Test
    @Order(4)
    void testExplorePathNested() throws IOException {
        JsonObject params = new JsonObject();
        params.addProperty("className", "com.dsm.mapstruct.testdata.TestClasses$Person");
        params.addProperty("pathExpression", "address.");

        JsonObject response = sendRequest("explore_path", params);

        System.out.println("\nNested path response: " + gson.toJson(response));

        assertThat(response.has("error")).isFalse();
        assertThat(response.has("result")).isTrue();

        JsonObject result = response.get("result").getAsJsonObject();
        var completions = result.get("completions").getAsJsonArray();
        assertThat(completions.size()).isGreaterThan(0);

        // Should have address fields like street, city, zipCode, etc.
        System.out.println("Address completions: " + completions.size());
        completions.forEach(item -> {
            JsonObject field = item.getAsJsonObject();
            System.out.println(String.format("  - %s: %s (%s)",
                    field.get("name").getAsString(),
                    field.get("type").getAsString(),
                    field.get("kind").getAsString()
            ));
        });
    }

    @Test
    @Order(5)
    void testExplorePathDeeplyNested() throws IOException {
        JsonObject params = new JsonObject();
        params.addProperty("className", "com.dsm.mapstruct.testdata.TestClasses$Person");
        params.addProperty("pathExpression", "address.country.");

        JsonObject response = sendRequest("explore_path", params);

        System.out.println("\nDeeply nested path response: " + gson.toJson(response));

        assertThat(response.has("error")).isFalse();
        assertThat(response.has("result")).isTrue();

        JsonObject result = response.get("result").getAsJsonObject();
        var completions = result.get("completions").getAsJsonArray();
        // City might be a String, so completions would be empty or might have String methods
        System.out.println("City completions: " + completions.size());
    }

    @Test
    @Order(6)
    void testExplorePathInvalidClass() throws IOException {
        JsonObject params = new JsonObject();
        params.addProperty("className", "com.nonexistent.FakeClass");
        params.addProperty("pathExpression", "");

        JsonObject response = sendRequest("explore_path", params);

        System.out.println("\nInvalid class response: " + gson.toJson(response));

        assertThat(response.has("error")).isTrue();
        assertThat(response.get("error").getAsString())
                .contains("Class not found");
    }

    @Test
    @Order(7)
    void testExplorePathMissingParams() throws IOException {
        JsonObject params = new JsonObject();
        // Missing both className and pathExpression

        JsonObject response = sendRequest("explore_path", params);

        System.out.println("\nMissing params response: " + gson.toJson(response));

        assertThat(response.has("error")).isTrue();
        assertThat(response.get("error").getAsString())
                .contains("Missing required params");
    }

    @Test
    @Order(8)
    void testUnknownMethod() throws IOException {
        JsonObject response = sendRequest("unknown_method", null);

        System.out.println("\nUnknown method response: " + gson.toJson(response));

        assertThat(response.has("error")).isTrue();
        assertThat(response.get("error").getAsString())
                .contains("Unknown method");
    }
}
