package com.dsm.mapstruct.core.usecase;

import com.dsm.mapstruct.core.model.CompletionResult;
import com.dsm.mapstruct.core.usecase.ExplorePathUseCase.ExplorePathParams;
import com.dsm.mapstruct.core.usecase.helper.PathNavigator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ExplorePathUseCase implements UseCase<ExplorePathParams, String> {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    PathNavigator navigator = new PathNavigator();

    @Override
    @SneakyThrows
    public String execute(ExplorePathParams input) {
        // Navigate and get completions
        CompletionResult result = navigator.navigate(input.clazz, input.pathExpression);

        // Output as JSON
        return GSON.toJson(result);
    }

    public record ExplorePathParams(Class<?> clazz, String pathExpression) {
    }
}
