package com.lautung.oa.model.monitor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Objects;

/**
 * 会话时间（ADR 0060）：服务器本地时间，只认 {@code yyyy-MM-dd HH:mm:ss} 一种形状。
 *
 * <p>登记 hash、列表的时间范围过滤与倒序排序共用这一个值类型，格式器也只有这一处；
 * 比较按时刻而不是字符串字典序。解析失败返回 {@code null}，由读侧决定跳过还是当作
 * 未提供——登记字段的坏值不阻止整行被读出，查询边界的坏值不放大成整页失败。
 *
 * <p>严格解析：2 月 30 日这类不存在的日期返回 {@code null}，不会被 SMART resolver
 * 悄悄改成月末。
 */
public record SessionTime(LocalDateTime value) implements Comparable<SessionTime> {

    public static final DateTimeFormatter FORMAT = new DateTimeFormatterBuilder()
            .appendPattern("uuuu-MM-dd HH:mm:ss")
            .toFormatter()
            .withResolverStyle(ResolverStyle.STRICT);

    public SessionTime {
        Objects.requireNonNull(value, "value");
    }

    public static SessionTime now() {
        return new SessionTime(LocalDateTime.now());
    }

    /**
     * @return 空白或形状不合法时为 {@code null}
     */
    public static SessionTime parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new SessionTime(LocalDateTime.parse(text.trim(), FORMAT));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @Override
    public int compareTo(SessionTime other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return FORMAT.format(value);
    }
}
