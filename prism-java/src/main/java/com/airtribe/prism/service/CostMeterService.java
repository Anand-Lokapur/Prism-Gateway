package com.airtribe.prism.service;

import com.airtribe.prism.domain.ModelPricing;
import org.springframework.stereotype.Service;

@Service
public class CostMeterService {

    public double calculate(ModelPricing pricing, int promptTokens, int completionTokens) {
        double inputCost = (promptTokens / 1_000_000.0) * pricing.inputPer1m();
        double outputCost = (completionTokens / 1_000_000.0) * pricing.outputPer1m();
        return round6(inputCost + outputCost);
    }

    private double round6(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }
}
