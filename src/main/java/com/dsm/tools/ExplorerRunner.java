package com.dsm.tools;

import com.dsm.tools.core.usecase.ResolvePathUseCase;
import com.dsm.tools.core.usecase.ResolvePathUseCase.ResolvePathParams;
import lombok.extern.slf4j.Slf4j;

/**
 * Hello world!
 */
@Slf4j
public class ExplorerRunner {
    public static void main(String[] args) {

        log.info("Info works");
        log.debug("Debug works");
        log.error("Error works");

        System.out.println("Output:");
        var params = new ResolvePathParams("", "");
        ResolvePathUseCase pathUseCase = resolvePathUseCase();
        String cmdOutput = String.join(System.lineSeparator(), pathUseCase.execute(params));
        System.out.println(cmdOutput);
        System.out.println("Done!");
    }

    private static ResolvePathUseCase resolvePathUseCase() {
        return new ResolvePathUseCase();
    }
}
