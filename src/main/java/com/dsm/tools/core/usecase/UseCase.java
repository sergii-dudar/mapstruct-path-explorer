package com.dsm.tools.core.usecase;

public interface UseCase<I, O> {

    O execute(I input);
}
