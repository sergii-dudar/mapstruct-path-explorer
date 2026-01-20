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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
        // This test requires the assembled JAR which is created during package phase
        // Skip if running during test phase
        String jarPath = "target/mapstruct-path-explorer.jar";
        File jarFile = new File(jarPath);
        assumeTrue(jarFile.exists(), "Skipping IPC server test - JAR not built yet (run 'mvn package')");
        
        // Create socket path
        socketPath = Path.of("/tmp/test-mapstruct-ipc-" + System.currentTimeMillis() + ".sock");
        Files.deleteIfExists(socketPath);

        // Get test classes
        String testClassesPath = "target/test-classes";

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

        // Use new multi-source API
        JsonObject source = new JsonObject();
        source.addProperty("name", "person");
        source.addProperty("type", "com.dsm.mapstruct.testdata.TestClasses$Person");

        params.add("sources", gson.toJsonTree(new JsonObject[]{source}));
        params.addProperty("pathExpression", "");
        params.addProperty("isEnum", false);

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

        // Use new multi-source API
        JsonObject source = new JsonObject();
        source.addProperty("name", "person");
        source.addProperty("type", "com.dsm.mapstruct.testdata.TestClasses$Person");

        params.add("sources", gson.toJsonTree(new JsonObject[]{source}));
        params.addProperty("pathExpression", "person.address.");
        params.addProperty("isEnum", false);

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

        // Use new multi-source API
        JsonObject source = new JsonObject();
        source.addProperty("name", "person");
        source.addProperty("type", "com.dsm.mapstruct.testdata.TestClasses$Person");

        params.add("sources", gson.toJsonTree(new JsonObject[]{source}));
        params.addProperty("pathExpression", "person.address.country.");
        params.addProperty("isEnum", false);

        JsonObject response = sendRequest("explore_path", params);

        System.out.println("\nDeeply nested path response: " + gson.toJson(response));

        assertThat(response.has("error")).isFalse();
        assertThat(response.has("result")).isTrue();

        JsonObject result = response.get("result").getAsJsonObject();
        var completions = result.get("completions").getAsJsonArray();
        // Country has fields, so completions should exist
        System.out.println("Country completions: " + completions.size());
    }

    @Test
    @Order(6)
    void testExplorePathInvalidClass() throws IOException {
        JsonObject params = new JsonObject();

        // Use new multi-source API with invalid class
        JsonObject source = new JsonObject();
        source.addProperty("name", "fake");
        source.addProperty("type", "com.nonexistent.FakeClass");

        params.add("sources", gson.toJsonTree(new JsonObject[]{source}));
        params.addProperty("pathExpression", "");
        params.addProperty("isEnum", false);

        JsonObject response = sendRequest("explore_path", params);

        System.out.println("\nInvalid class response: " + gson.toJson(response));

        assertThat(response.has("error")).isTrue();
        assertThat(response.get("error").getAsString())
                .contains("com.nonexistent.FakeClass");
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

    @Test
    @Order(9)
    void testExplorePathWithJavaBeanSetters() throws IOException {
        JsonObject params = new JsonObject();

        // Test with PersonPojo class that has JavaBean-style setters
        JsonObject source = new JsonObject();
        source.addProperty("name", "$target");
        source.addProperty("type", "com.dsm.mapstruct.testdata.TestClasses$PersonPojo");

        params.add("sources", gson.toJsonTree(new JsonObject[]{source}));
        params.addProperty("pathExpression", "");
        params.addProperty("isEnum", false);

        JsonObject response = sendRequest("explore_path", params);

        System.out.println("\nJavaBean setters response: " + gson.toJson(response));

        assertThat(response.has("error")).isFalse();
        assertThat(response.has("result")).isTrue();

        JsonObject result = response.get("result").getAsJsonObject();
        var completions = result.get("completions").getAsJsonArray();

        assertThat(completions.size()).isGreaterThan(0);

        // Should have both getters and setters
        System.out.println("PersonPojo completions: " + completions.size());
        completions.forEach(item -> {
            JsonObject field = item.getAsJsonObject();
            System.out.println(String.format("  - %s: %s (%s)",
                    field.get("name").getAsString(),
                    field.get("type").getAsString(),
                    field.get("kind").getAsString()
            ));
        });

        // Verify setters are present and marked as SETTER kind
        long setterCount = 0;
        for (int i = 0; i < completions.size(); i++) {
            JsonObject completion = completions.get(i).getAsJsonObject();
            if (completion.get("kind").getAsString().equals("SETTER")) {
                setterCount++;
            }
        }

        assertThat(setterCount).as("Should have SETTER kind completions").isGreaterThan(0);

        // Should include name, age, email properties
        boolean hasName = false;
        boolean hasAge = false;
        boolean hasEmail = false;

        for (int i = 0; i < completions.size(); i++) {
            JsonObject completion = completions.get(i).getAsJsonObject();
            String name = completion.get("name").getAsString();
            if (name.equals("name")) hasName = true;
            if (name.equals("age")) hasAge = true;
            if (name.equals("email")) hasEmail = true;
        }

        assertThat(hasName).isTrue();
        assertThat(hasAge).isTrue();
        assertThat(hasEmail).isTrue();
    }

    @Test
    @Order(10)
    void testExplorePathWithFluentBuilderSetters() throws IOException {
        JsonObject params = new JsonObject();

        // Test with FluentBuilder class that has fluent-style setters
        JsonObject source = new JsonObject();
        source.addProperty("name", "$target");
        source.addProperty("type", "com.dsm.mapstruct.testdata.TestClasses$FluentBuilder");

        params.add("sources", gson.toJsonTree(new JsonObject[]{source}));
        params.addProperty("pathExpression", "");
        params.addProperty("isEnum", false);

        JsonObject response = sendRequest("explore_path", params);

        System.out.println("\nFluent builder setters response: " + gson.toJson(response));

        assertThat(response.has("error")).isFalse();
        assertThat(response.has("result")).isTrue();

        JsonObject result = response.get("result").getAsJsonObject();
        var completions = result.get("completions").getAsJsonArray();

        assertThat(completions.size()).isGreaterThan(0);

        // Should have both getters and setters
        System.out.println("FluentBuilder completions: " + completions.size());
        completions.forEach(item -> {
            JsonObject field = item.getAsJsonObject();
            System.out.println(String.format("  - %s: %s (%s)",
                    field.get("name").getAsString(),
                    field.get("type").getAsString(),
                    field.get("kind").getAsString()
            ));
        });

        // Verify setters are present and marked as SETTER kind
        long setterCount = 0;
        for (int i = 0; i < completions.size(); i++) {
            JsonObject completion = completions.get(i).getAsJsonObject();
            if (completion.get("kind").getAsString().equals("SETTER")) {
                setterCount++;
            }
        }

        assertThat(setterCount).as("Should have SETTER kind completions").isGreaterThan(0);

        // Should include title, description, priority properties
        boolean hasTitle = false;
        boolean hasDescription = false;
        boolean hasPriority = false;

        for (int i = 0; i < completions.size(); i++) {
            JsonObject completion = completions.get(i).getAsJsonObject();
            String name = completion.get("name").getAsString();
            if (name.equals("title")) hasTitle = true;
            if (name.equals("description")) hasDescription = true;
            if (name.equals("priority")) hasPriority = true;
        }

        assertThat(hasTitle).isTrue();
        assertThat(hasDescription).isTrue();
        assertThat(hasPriority).isTrue();

        // Should NOT include build() method (it takes no parameters)
        boolean hasBuild = false;
        for (int i = 0; i < completions.size(); i++) {
            JsonObject completion = completions.get(i).getAsJsonObject();
            String name = completion.get("name").getAsString();
            if (name.equals("build")) hasBuild = true;
        }
        assertThat(hasBuild).as("Should not include build() method").isFalse();
    }
}
