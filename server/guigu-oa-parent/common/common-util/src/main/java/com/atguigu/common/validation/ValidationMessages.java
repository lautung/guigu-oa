package com.atguigu.common.validation;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 参数校验消息的统一拼接：去掉空值、去重，按调用方给定的顺序用「；」连接。
 */
public final class ValidationMessages {

    private static final String SEPARATOR = "；";

    private ValidationMessages() {
    }

    public static String join(List<String> messages) {
        return messages.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(SEPARATOR));
    }
}
