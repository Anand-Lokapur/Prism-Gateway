package com.airtribe.prism.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SimilarityUtilsTests {

    @Test
    void recognizesPasswordResetParaphrase() {
        double similarity = SimilarityUtils.semanticSimilarity(
                "How do I reset my password on the dashboard?",
                "What are the steps to reset my dashboard password?");

        assertThat(similarity).isGreaterThanOrEqualTo(0.92);
    }

    @Test
    void keepsTwoFactorResetDistinctFromPasswordReset() {
        double similarity = SimilarityUtils.semanticSimilarity(
                "How do I reset my password on the dashboard?",
                "How do I reset my two-factor authentication on the dashboard?");

        assertThat(similarity).isLessThan(0.92);
    }

    @Test
    void recognizesRefundParaphrase() {
        double similarity = SimilarityUtils.semanticSimilarity(
                "What is the refund policy for annual plans?",
                "If I bought an annual plan, can I get my money back?");

        assertThat(similarity).isGreaterThanOrEqualTo(0.92);
    }
}
