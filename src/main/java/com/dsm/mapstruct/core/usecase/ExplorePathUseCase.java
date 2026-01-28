package com.dsm.mapstruct.core.usecase;

import com.dsm.mapstruct.core.model.CompletionResult;
import com.dsm.mapstruct.core.model.SourceParameter;
import com.dsm.mapstruct.core.usecase.ExplorePathUseCase.ExplorePathParams;
import com.dsm.mapstruct.core.usecase.helper.PathNavigator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.FieldDefaults;

import java.util.List;

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
        // Navigate and get completions using multi-parameter support
        CompletionResult result = navigator.navigateFromSources(
                input.sources,
                input.pathExpression,
                input.isEnum
        );

        // Deduplicate completions by name (keep first occurrence)
        CompletionResult deduplicated = deduplicateCompletions(result);

        // Output as JSON
        return GSON.toJson(deduplicated);
    }

    /**
     * Deduplicates completions by field name, keeping the first occurrence.
     * This handles cases where both field and getter exist for the same property.
     */
    private CompletionResult deduplicateCompletions(CompletionResult result) {
        var uniqueCompletions = result.completions().stream()
                .collect(java.util.stream.Collectors.toMap(
                        field -> field.name(),                    // key: field name
                        field -> field,                            // value: the field itself
                        (existing, replacement) -> existing,       // keep first on duplicate
                        java.util.LinkedHashMap::new               // maintain order
                ))
                .values()
                .stream()
                .toList();

        return new CompletionResult(
                result.className(),
                result.simpleName(),
                result.packageName(),
                result.path(),
                uniqueCompletions
        );
    }

    public record ExplorePathParams(
                                    List<SourceParameter> sources,
                                    String pathExpression,
                                    boolean isEnum
    ) {
        public ExplorePathParams {
            if (sources == null || sources.isEmpty()) {
                throw new IllegalArgumentException("sources list cannot be null or empty");
            }
        }
    }
}
