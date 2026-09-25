package com.dsm.mapstruct;

import com.dsm.mapstruct.adapter.api.ipc.IpcClientMessageListener;
import com.dsm.mapstruct.adapter.api.ipc.IpcClientMessageListener.Response;
import com.dsm.mapstruct.core.usecase.ExplorePathUseCase;
import com.dsm.mapstruct.core.usecase.ExploreTypeSourceUseCase;
import com.google.gson.JsonObject;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * In-process tests of the request/response contract: every request line must produce a response,
 * whatever goes wrong while handling it.
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class IpcClientMessageListenerTest {

    static final String PERSON = "com.dsm.mapstruct.testdata.TestClasses$Person";

    IpcClientMessageListener listener =
            new IpcClientMessageListener(new ExplorePathUseCase(), new ExploreTypeSourceUseCase());

    private static String explorePath(String sourcesJson, String path) {
        return "{\"id\":\"1\",\"method\":\"explore_path\",\"params\":{\"sources\":" + sourcesJson
                + ",\"pathExpression\":\"" + path + "\",\"isEnum\":false}}";
    }

    private static String personSource() {
        return "[{\"name\":\"person\",\"type\":\"" + PERSON + "\"}]";
    }

    @Test
    void explorePathReturnsCompletions() {
        Response response = listener.processRequest(explorePath(personSource(), "person.address."));

        assertThat(response.shutdown()).isFalse();
        JsonObject json = response.json();
        assertThat(json.get("id").getAsString()).isEqualTo("1");
        assertThat(json.has("error")).isFalse();
        assertThat(json.getAsJsonObject("result").getAsJsonArray("completions").size()).isGreaterThan(0);
    }

    @Test
    void errorThrownByUseCaseBecomesErrorResponse() {
        // NoClassDefFoundError is what reflection throws when a class references a type missing from
        // the classpath. It is an Error, not an Exception, and used to escape the handler entirely.
        IpcClientMessageListener failing = new IpcClientMessageListener(new ExplorePathUseCase() {
            @Override
            public String execute(ExplorePathParams input) {
                throw new NoClassDefFoundError("com/example/Missing");
            }
        }, new ExploreTypeSourceUseCase());

        Response response = failing.processRequest(explorePath(personSource(), ""));

        assertThat(response.shutdown()).isFalse();
        assertThat(response.json().get("id").getAsString()).isEqualTo("1");
        assertThat(response.json().get("error").getAsString())
                .startsWith("Error exploring path: ")
                .contains("NoClassDefFoundError")
                .contains("com/example/Missing");
    }

    @Test
    void unknownClassBecomesErrorResponse() {
        Response response = listener.processRequest(
                explorePath("[{\"name\":\"fake\",\"type\":\"com.nonexistent.FakeClass\"}]", ""));

        assertThat(response.json().get("error").getAsString())
                .startsWith("Error exploring path: ")
                .contains("com.nonexistent.FakeClass");
    }

    @Test
    void malformedSourcesEntryBecomesErrorResponse() {
        Response response = listener.processRequest(explorePath("[{\"type\":\"" + PERSON + "\"}]", ""));

        assertThat(response.shutdown()).isFalse();
        assertThat(response.json().get("id").getAsString()).isEqualTo("1");
        assertThat(response.json().get("error").getAsString())
                .startsWith("Invalid 'sources' param: ")
                .contains("name");
    }

    @Test
    void sourcesOfWrongShapeBecomeErrorResponse() {
        Response response = listener.processRequest(explorePath("[1, 2]", ""));

        assertThat(response.json().get("error").getAsString()).startsWith("Invalid 'sources' param: ");
    }

    @Test
    void missingSourcesBecomesErrorResponse() {
        Response response = listener.processRequest(
                "{\"id\":\"2\",\"method\":\"explore_path\",\"params\":{\"pathExpression\":\"\"}}");

        assertThat(response.json().get("error").getAsString()).contains("Missing required params");
    }

    @Test
    void arrayParamsAreTreatedAsNoParams() {
        // vim.json.encode({}) produces [] for an empty Lua table.
        Response response = listener.processRequest("{\"id\":\"3\",\"method\":\"explore_path\",\"params\":[]}");

        assertThat(response.json().get("error").getAsString()).contains("Missing required params");
    }

    @Test
    void invalidJsonBecomesErrorResponse() {
        Response response = listener.processRequest("this is not json");

        assertThat(response.shutdown()).isFalse();
        assertThat(response.json().get("error").getAsString()).startsWith("Invalid request: ");
    }

    @Test
    void nonObjectJsonBecomesErrorResponse() {
        Response response = listener.processRequest("[1, 2, 3]");

        assertThat(response.json().get("error").getAsString()).startsWith("Invalid request: ");
    }

    @Test
    void objectValuedIdBecomesErrorResponse() {
        Response response = listener.processRequest("{\"id\":{\"nested\":true},\"method\":\"ping\"}");

        assertThat(response.json().get("error").getAsString()).startsWith("Invalid request: ");
    }

    @Test
    void numericIdIsEchoedAsString() {
        Response response = listener.processRequest("{\"id\":7,\"method\":\"ping\"}");

        assertThat(response.json().get("id").getAsString()).isEqualTo("7");
        assertThat(response.json().getAsJsonObject("result").get("message").getAsString()).isEqualTo("pong");
    }

    @Test
    void missingMethodBecomesErrorResponse() {
        Response response = listener.processRequest("{\"id\":\"4\"}");

        assertThat(response.json().get("id").getAsString()).isEqualTo("4");
        assertThat(response.json().get("error").getAsString()).isEqualTo("Missing 'method' field");
    }

    @Test
    void unknownMethodBecomesErrorResponse() {
        Response response = listener.processRequest("{\"id\":\"5\",\"method\":\"nope\",\"params\":{}}");

        assertThat(response.json().get("error").getAsString()).isEqualTo("Unknown method: nope");
    }

    @Test
    void heartbeatIsAnswered() {
        Response response = listener.processRequest("{\"id\":\"6\",\"method\":\"heartbeat\",\"params\":[]}");

        assertThat(response.json().getAsJsonObject("result").get("status").getAsString()).isEqualTo("alive");
    }

    @Test
    void shutdownRequestsExitAfterResponse() {
        Response response = listener.processRequest("{\"id\":\"8\",\"method\":\"shutdown\",\"params\":{}}");

        assertThat(response.shutdown()).isTrue();
        assertThat(response.json().getAsJsonObject("result").get("message").getAsString())
                .isEqualTo("shutting down");
    }

    @Test
    void exploreTypeSourceReturnsClassLocation() {
        Response response = listener.processRequest(
                "{\"id\":\"9\",\"method\":\"explore_type_source\",\"params\":{\"typeName\":\"" + PERSON + "\"}}");

        assertThat(response.json().has("error")).isFalse();
        assertThat(response.json().getAsJsonObject("result").get("sourcePath").getAsString())
                .endsWith("test-classes");
    }

    @Test
    void exploreTypeSourceOfUnknownTypeBecomesErrorResponse() {
        Response response = listener.processRequest(
                "{\"id\":\"10\",\"method\":\"explore_type_source\",\"params\":{\"typeName\":\"com.nonexistent.Nope\"}}");

        assertThat(response.json().get("error").getAsString())
                .startsWith("Error exploring type source: ")
                .contains("com.nonexistent.Nope");
    }

    @Test
    void exploreTypeSourceWithoutTypeNameBecomesErrorResponse() {
        Response response = listener.processRequest(
                "{\"id\":\"11\",\"method\":\"explore_type_source\",\"params\":{}}");

        assertThat(response.json().get("error").getAsString()).isEqualTo("Missing required param: typeName");
    }
}
