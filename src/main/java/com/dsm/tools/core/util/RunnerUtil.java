package com.dsm.tools.core.util;

import com.dsm.tools.core.usecase.ResolvePathUseCase.ResolvePathParams;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

@Slf4j
@UtilityClass
public class RunnerUtil {

    public static ResolvePathParams fromInputArgas(String[] args) {
        if (ArrayUtils.isEmpty(args)) {
            throw new IllegalStateException("""

                    requiring at least:
                        - one input params [class name]: to get list of fields of class.
                        - two imput params [class name, path to explore]: to get list of fields of class based on path.
                                        """
            );
        }
        String classNameStr = args[0];
        String path = args.length > 1 ? args[1] : StringUtils.EMPTY;

        log.info("Args: " + List.of(args).toString());
        return null;
    }
}
