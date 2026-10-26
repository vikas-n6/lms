package com.lms.notification.domain;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders a template body by substituting {@code {placeholder}} tokens with values (phase prompt
 * task 5). Template texts are versioned configuration, never hard-coded in Java (CLAUDE.md). Pure:
 * an unknown placeholder is left as an empty string so a missing value never leaks a raw token, and
 * no PII is logged by the caller.
 */
public final class TemplateRenderer {

    private static final Pattern TOKEN = Pattern.compile("\\{([a-zA-Z0-9_]+)}");

    private TemplateRenderer() {}

    public static String render(String template, Map<String, String> values) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(values, "values");
        Matcher matcher = TOKEN.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = values.getOrDefault(key, "");
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
