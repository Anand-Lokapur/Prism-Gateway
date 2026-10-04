package com.airtribe.prism.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AutoRouterServiceTests {
    private final AutoRouterService router = new AutoRouterService();

    @Test
    void routesShortProofToSmart() {
        assertThat(router.classify("Prove that the square root of 2 is irrational.").tier()).isEqualTo("smart");
    }

    @Test
    void routesLongRosterLookupToFast() {
        assertThat(router.classify("Here is an on-call roster for many days. Who is on call this Thursday?").tier()).isEqualTo("fast");
    }

    @Test
    void routesAlgorithmicWorkToSmart() {
        assertThat(router.classify("Write an algorithm for the longest palindromic substring.").tier()).isEqualTo("smart");
    }
}
