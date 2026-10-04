package com.airtribe.prism.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.airtribe.prism.domain.ModelPricing;
import org.junit.jupiter.api.Test;

class CostMeterServiceTests {

    @Test
    void calculatesAndRoundsProviderReportedTokenCost() {
        CostMeterService meter = new CostMeterService();

        double cost = meter.calculate(new ModelPricing("alpha-small", 0.50, 1.50), 1_000, 2_000);

        assertThat(cost).isEqualTo(0.0035);
    }
}
