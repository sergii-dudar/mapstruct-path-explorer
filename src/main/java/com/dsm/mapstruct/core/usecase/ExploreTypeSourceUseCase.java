package com.dsm.mapstruct.core.usecase;

import com.dsm.mapstruct.core.usecase.ExploreTypeSourceUseCase.ExploreTypeSourceParams;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.FieldDefaults;

import java.nio.file.Paths;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ExploreTypeSourceUseCase implements UseCase<ExploreTypeSourceParams, String> {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    @Override
    @SneakyThrows
    public String execute(ExploreTypeSourceParams input) {
        // Navigate and get completions using multi-parameter support
        ExploreTypeSourceResult result = new ExploreTypeSourceResult(getClassLocation(input.type));
        // Output as JSON
        return GSON.toJson(result);
    }

    @SneakyThrows
    public static String getClassLocation(Class<?> clazz) {
        var cs = clazz.getProtectionDomain().getCodeSource();
        if (cs == null) {
            return null; // bootstrap classes sometimes return null
        }
        var url = cs.getLocation();
        return Paths.get(url.toURI()).toString();
    }

    public record ExploreTypeSourceParams(Class<?> type) {
    }

    public record ExploreTypeSourceResult(String sourcePath) {
    }
}
