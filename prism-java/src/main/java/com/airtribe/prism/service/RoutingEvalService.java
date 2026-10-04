package com.airtribe.prism.service;

import com.airtribe.prism.config.GatewayProperties;
import com.airtribe.prism.domain.RoutingEvalCase;
import com.airtribe.prism.domain.RoutingEvalReport;
import com.airtribe.prism.domain.RoutingEvalResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RoutingEvalService {

    private final AutoRouterService autoRouterService;
    private final GatewayProperties properties;
    private final ObjectMapper objectMapper;

    public RoutingEvalService(AutoRouterService autoRouterService, GatewayProperties properties, ObjectMapper objectMapper) {
        this.autoRouterService = autoRouterService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public RoutingEvalReport evaluate() throws IOException {
        Path path = resolvePath(properties.dataPaths().gatewayConfig()).getParent().resolve("routing_eval.jsonl");
        if (!Files.exists(path)) {
            path = Path.of("..", "prism-capstone", "data", "routing_eval.jsonl").normalize();
        }
        List<RoutingEvalResult> cases = new ArrayList<>();
        int total = 0;
        int correct = 0;
        for (String line : Files.readAllLines(path)) {
            if (line == null || line.isBlank()) {
                continue;
            }
            RoutingEvalCase testCase = objectMapper.readValue(line, RoutingEvalCase.class);
            String actual = autoRouterService.classify(testCase.prompt()).tier();
            if (testCase.expectedTier().equalsIgnoreCase(actual)) {
                correct++;
            }
            total++;
            cases.add(new RoutingEvalResult(testCase.id(), testCase.expectedTier(), actual, autoRouterService.classify(testCase.prompt()).reason()));
        }
        double accuracy = total == 0 ? 0.0 : (double) correct / total;
        return new RoutingEvalReport(total, correct, accuracy, cases);
    }

    private Path resolvePath(String configuredPath) {
        Path path = Path.of(configuredPath);
        if (Files.exists(path)) {
            return path;
        }
        return Path.of("..", configuredPath).normalize();
    }
}
