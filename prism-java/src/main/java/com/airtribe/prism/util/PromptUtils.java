package com.airtribe.prism.util;

import com.airtribe.prism.domain.ChatMessage;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class PromptUtils {

    private PromptUtils() {}

    public static String joinMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(message -> message.role() + ": " + message.content())
                .collect(Collectors.joining("\n"));
    }

    public static String lastUserContent(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if ("user".equalsIgnoreCase(message.role())) {
                return message.content();
            }
        }
        return messages.isEmpty() ? "" : messages.get(messages.size() - 1).content();
    }

    public static Set<String> tokenize(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return Set.of();
        }
        return new HashSet<>(Arrays.asList(normalized.split("\\s+")));
    }

    public static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }
}
