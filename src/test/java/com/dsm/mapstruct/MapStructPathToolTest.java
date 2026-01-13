package com.dsm.mapstruct;

import com.dsm.mapstruct.testdata.TestClasses.Person;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the MapStructPathTool main method.
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class MapStructPathToolTest {

    ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    PrintStream originalErr = System.err;
    Gson gson = new Gson();

    @BeforeEach
    void setUpStreams() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void testRunWithValidArguments() {
        String className = Person.class.getName();
        String path = "";

        int exitCode = MapStructPathTool.run(new String[]{className, path});

        assertThat(exitCode).isEqualTo(0);
        String output = outContent.toString();
        assertThat(output).isNotEmpty();

        // Verify JSON output is valid
        JsonObject json = gson.fromJson(output, JsonObject.class);
        assertThat(json.get("className").getAsString()).isEqualTo(className);
        assertThat(json.get("path").getAsString()).isEqualTo(path);
        assertThat(json.has("completions")).isTrue();
        assertThat(json.get("completions").getAsJsonArray()).isNotEmpty();
    }

    @Test
    void testRunWithNestedPath() {
        String className = Person.class.getName();
        String path = "address.";

        int exitCode = MapStructPathTool.run(new String[]{className, path});

        assertThat(exitCode).isEqualTo(0);
        String output = outContent.toString();
        JsonObject json = gson.fromJson(output, JsonObject.class);

        assertThat(json.get("path").getAsString()).isEqualTo(path);
        assertThat(json.get("completions").getAsJsonArray()).isNotEmpty();

        // Verify it contains expected address fields
        String jsonStr = output;
        assertThat(jsonStr).contains("street");
        assertThat(jsonStr).contains("city");
    }

    @Test
    void testRunWithPrefixMatching() {
        String className = Person.class.getName();
        String path = "first";

        int exitCode = MapStructPathTool.run(new String[]{className, path});

        assertThat(exitCode).isEqualTo(0);
        String output = outContent.toString();
        JsonObject json = gson.fromJson(output, JsonObject.class);

        // Should only contain fields starting with "first"
        String jsonStr = output;
        assertThat(jsonStr).contains("firstName");
        assertThat(jsonStr).doesNotContain("lastName");
        assertThat(jsonStr).doesNotContain("\"age\"");
    }

    @Test
    void testRunWithCollectionPath() {
        String className = Person.class.getName();
        String path = "orders.first.";

        int exitCode = MapStructPathTool.run(new String[]{className, path});

        assertThat(exitCode).isEqualTo(0);
        String output = outContent.toString();
        JsonObject json = gson.fromJson(output, JsonObject.class);

        assertThat(json.get("completions").getAsJsonArray()).isNotEmpty();

        // Verify it contains Order class fields
        String jsonStr = output;
        assertThat(jsonStr).contains("orderId");
        assertThat(jsonStr).contains("items");
    }

    @Test
    void testRunWithMissingArguments() {
        int exitCode = MapStructPathTool.run(new String[]{});

        assertThat(exitCode).isEqualTo(1);
        String errorOutput = errContent.toString();
        assertThat(errorOutput).contains("Usage:");
        assertThat(errorOutput).contains("MapStruct Path Completion Tool");
    }

    @Test
    void testRunWithOnlyOneArgument() {
        int exitCode = MapStructPathTool.run(new String[]{"com.example.User"});

        assertThat(exitCode).isEqualTo(1);
        String errorOutput = errContent.toString();
        assertThat(errorOutput).contains("Usage:");
    }

    @Test
    void testRunWithInvalidClassName() {
        String className = "com.example.NonExistentClass";
        String path = "";

        int exitCode = MapStructPathTool.run(new String[]{className, path});

        assertThat(exitCode).isEqualTo(1);
        String errorOutput = errContent.toString();
        assertThat(errorOutput).contains("Class not found");
        assertThat(errorOutput).contains(className);
        assertThat(errorOutput).contains("classpath");
    }

    @Test
    void testRunWithInvalidPath() {
        String className = Person.class.getName();
        String path = "nonExistentField.";

        int exitCode = MapStructPathTool.run(new String[]{className, path});

        assertThat(exitCode).isEqualTo(0);
        String output = outContent.toString();
        JsonObject json = gson.fromJson(output, JsonObject.class);

        // Should return empty completions
        assertThat(json.get("completions").getAsJsonArray()).isEmpty();
    }

    @Test
    void testRunJSONStructure() {
        String className = Person.class.getName();
        String path = "address.";

        int exitCode = MapStructPathTool.run(new String[]{className, path});

        assertThat(exitCode).isEqualTo(0);
        String output = outContent.toString();
        JsonObject json = gson.fromJson(output, JsonObject.class);

        // Verify JSON structure
        assertThat(json.has("className")).isTrue();
        assertThat(json.has("path")).isTrue();
        assertThat(json.has("completions")).isTrue();

        // Verify completion structure
        var completions = json.get("completions").getAsJsonArray();
        if (completions.size() > 0) {
            JsonObject firstCompletion = completions.get(0).getAsJsonObject();
            assertThat(firstCompletion.has("name")).isTrue();
            assertThat(firstCompletion.has("type")).isTrue();
            assertThat(firstCompletion.has("kind")).isTrue();
        }
    }

    @Test
    void testRunOutputIsPrettyPrinted() {
        String className = Person.class.getName();
        String path = "";

        int exitCode = MapStructPathTool.run(new String[]{className, path});

        assertThat(exitCode).isEqualTo(0);
        String output = outContent.toString();

        // Pretty printed JSON should have newlines and indentation
        assertThat(output).contains("\n");
        assertThat(output).contains("  "); // Indentation
    }

    @Test
    void testRunWithEmptyPath() {
        String className = Person.class.getName();
        String path = "";

        int exitCode = MapStructPathTool.run(new String[]{className, path});

        assertThat(exitCode).isEqualTo(0);
        String output = outContent.toString();
        JsonObject json = gson.fromJson(output, JsonObject.class);

        // Should return all fields from Person class
        var completions = json.get("completions").getAsJsonArray();
        assertThat(completions.size()).isGreaterThan(5);

        String jsonStr = output;
        assertThat(jsonStr).contains("firstName");
        assertThat(jsonStr).contains("lastName");
        assertThat(jsonStr).contains("address");
    }

    @Test
    void testRunCompletionsAreSorted() {
        String className = Person.class.getName();
        String path = "";

        int exitCode = MapStructPathTool.run(new String[]{className, path});

        assertThat(exitCode).isEqualTo(0);
        String output = outContent.toString();
        JsonObject json = gson.fromJson(output, JsonObject.class);

        var completions = json.get("completions").getAsJsonArray();

        // Extract names and verify they are sorted
        String previousName = null;
        for (var completion : completions) {
            String currentName = completion.getAsJsonObject().get("name").getAsString();
            if (previousName != null) {
                assertThat(currentName.compareTo(previousName)).isGreaterThanOrEqualTo(0);
            }
            previousName = currentName;
        }
    }

    @Test
    void testRunWithDeeplyNestedPath() {
        String className = Person.class.getName();
        String path = "address.country.";

        int exitCode = MapStructPathTool.run(new String[]{className, path});

        assertThat(exitCode).isEqualTo(0);
        String output = outContent.toString();
        JsonObject json = gson.fromJson(output, JsonObject.class);

        assertThat(json.get("completions").getAsJsonArray()).isNotEmpty();

        // Verify it contains Country class fields
        String jsonStr = output;
        assertThat(jsonStr).contains("name");
        assertThat(jsonStr).contains("code");
    }

    @Test
    void testRunWithMethodChaining() {
        String className = Person.class.getName();
        String path = "getAddress().";

        int exitCode = MapStructPathTool.run(new String[]{className, path});

        assertThat(exitCode).isEqualTo(0);
        String output = outContent.toString();
        JsonObject json = gson.fromJson(output, JsonObject.class);

        // Should navigate through the getter to Address
        assertThat(json.get("completions").getAsJsonArray()).isNotEmpty();

        String jsonStr = output;
        assertThat(jsonStr).contains("street");
        assertThat(jsonStr).contains("city");
    }

    @Test
    void testRunPrefixMatchingIsCaseInsensitive() {
        String className = Person.class.getName();
        String path = "FIRST"; // uppercase prefix

        int exitCode = MapStructPathTool.run(new String[]{className, path});

        assertThat(exitCode).isEqualTo(0);
        String output = outContent.toString();
        JsonObject json = gson.fromJson(output, JsonObject.class);

        // Should still match "firstName"
        String jsonStr = output;
        assertThat(jsonStr).contains("firstName");
    }

    @Test
    void testRunReturnsCorrectExitCodes() {
        // Success case
        int exitCode = MapStructPathTool.run(new String[]{Person.class.getName(), ""});
        assertThat(exitCode).isEqualTo(0);

        // Missing arguments case
        exitCode = MapStructPathTool.run(new String[]{});
        assertThat(exitCode).isEqualTo(1);

        // Class not found case
        exitCode = MapStructPathTool.run(new String[]{"com.invalid.Class", ""});
        assertThat(exitCode).isEqualTo(1);
    }
}
