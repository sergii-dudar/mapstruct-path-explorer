package com.dsm.tools.core.usecase;

import com.dsm.tools.core.usecase.ResolvePathUseCase.ResolvePathParams;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ResolvePathUseCase implements UseCase<ResolvePathParams, List<String>> {

    @Override
    public List<String> execute(ResolvePathParams params) {
        // TODO:
        return List.of("testItem1", "testItem2");
    }

    public record ResolvePathParams(String className,
                                    String path) {
    }
}
