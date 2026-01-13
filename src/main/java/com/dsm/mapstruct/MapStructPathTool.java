package com.dsm.mapstruct;

import com.dsm.mapstruct.core.usecase.helper.PathNavigator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.dsm.mapstruct.adapter.api.cmd.CommandToolRunner;
import com.dsm.mapstruct.core.model.CompletionResult;

/**
 * Main entry point for the MapStruct Path Completion Tool.
 *
 * Usage:
 *   java -jar mapstruct-path-explorer.jar "com.example.User" "address."
 *   java -jar mapstruct-path-explorer.jar "com.example.User" "address.str"
 *   java -jar mapstruct-path-explorer.jar "com.example.Order" "items.getFirst().product."
 */
public class MapStructPathTool {

    public static void main(String[] args) {
        int exitCode = CommandToolRunner.run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

}
