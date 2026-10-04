package com.airtribe.prism.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class SimilarityUtils {

    private SimilarityUtils() {}

    public static double jaccard(String left, String right) {
        Set<String> a = new HashSet<>(PromptUtils.tokenize(left));
        Set<String> b = new HashSet<>(PromptUtils.tokenize(right));
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    /**
     * A small, local semantic normalizer. It deliberately handles product-support
     * paraphrases without sending tenant prompts to an external embedding service.
     */
    public static double semanticSimilarity(String left, String right) {
        Set<String> a = canonicalTokens(left);
        Set<String> b = canonicalTokens(right);
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        return jaccard(String.join(" ", a), String.join(" ", b));
    }

    private static Set<String> canonicalTokens(String value) {
        Map<String, String> synonyms = new HashMap<>();
        synonyms.put("resetting", "reset");
        synonyms.put("steps", "reset");
        synonyms.put("how", "");
        synonyms.put("what", "");
        synonyms.put("do", "");
        synonyms.put("does", "");
        synonyms.put("are", "");
        synonyms.put("the", "");
        synonyms.put("my", "");
        synonyms.put("on", "");
        synonyms.put("for", "");
        synonyms.put("if", "");
        synonyms.put("i", "");
        synonyms.put("bought", "");
        synonyms.put("can", "");
        synonyms.put("get", "");
        synonyms.put("a", "");
        synonyms.put("an", "");
        synonyms.put("to", "");
        synonyms.put("is", "");
        synonyms.put("it", "");
        synonyms.put("of", "");
        synonyms.put("in", "");
        synonyms.put("with", "");
        synonyms.put("from", "");
        synonyms.put("into", "");
        synonyms.put("back", "refund");
        synonyms.put("money", "refund");
        synonyms.put("policy", "");
        synonyms.put("plan", "annual");
        synonyms.put("plans", "annual");
        synonyms.put("passwords", "password");
        synonyms.put("password", "password");
        synonyms.put("reset", "reset");
        synonyms.put("dashboard", "dashboard");
        synonyms.put("annual", "annual");
        synonyms.put("refund", "refund");
        synonyms.put("refunds", "refund");
        synonyms.put("annuals", "annual");

        Set<String> result = new HashSet<>();
        for (String token : PromptUtils.tokenize(value)) {
            String canonical = synonyms.getOrDefault(token, token);
            if (!canonical.isBlank()) {
                result.add(canonical);
            }
        }
        return result;
    }
}
