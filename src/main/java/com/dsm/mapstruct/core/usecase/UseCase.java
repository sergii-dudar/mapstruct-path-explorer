package com.dsm.mapstruct.core.usecase;

public interface UseCase<I, O> {
    O execute(I params);
}
