package com.atguigu.common.observability;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks CN mobile numbers inside free-form log text: 13800131234 becomes 138****1234.
 *
 * <p>Covers the presentations a failed login actually carries (username is user input):
 * bare 11 digits, {@code +86} country code, and dash/space separated groups. Digit-boundary
 * guards keep longer runs (ids, tokens) intact — only an exact 11-digit run shaped like a CN
 * mobile number is masked, wherever it appears in the value.
 */
public final class PhoneNumberMasker {

    private static final Pattern CN_MOBILE =
            Pattern.compile("(?<!\\d)(\\+86)?(1[3-9](?:[-\\s]?\\d){9})(?!\\d)");

    private PhoneNumberMasker() {
    }

    public static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        Matcher matcher = CN_MOBILE.matcher(value);
        if (!matcher.find()) {
            return value;
        }
        StringBuilder masked = new StringBuilder();
        do {
            String prefix = matcher.group(1) == null ? "" : matcher.group(1);
            String digits = matcher.group(2).replaceAll("\\D", "");
            matcher.appendReplacement(masked,
                    Matcher.quoteReplacement(prefix + digits.substring(0, 3) + "****" + digits.substring(7)));
        } while (matcher.find());
        matcher.appendTail(masked);
        return masked.toString();
    }
}
