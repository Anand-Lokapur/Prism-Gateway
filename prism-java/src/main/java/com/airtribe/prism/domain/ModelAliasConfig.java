package com.airtribe.prism.domain;

import java.util.List;

public record ModelAliasConfig(
        String alias,
        String primary,
        List<String> fallbacks
) {}
