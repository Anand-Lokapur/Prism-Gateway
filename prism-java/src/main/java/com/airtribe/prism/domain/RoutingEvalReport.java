package com.airtribe.prism.domain;

import java.util.List;

public record RoutingEvalReport(int total, int correct, double accuracy, List<RoutingEvalResult> cases) {}
