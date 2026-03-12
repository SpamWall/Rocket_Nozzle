package com.nozzle.optimization;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MonteCarloUncertainty Tests")
class MonteCarloUncertainty_UT {
    
    private NozzleDesignParameters params;
    
    @BeforeEach
    void setUp() {
        params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(10)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();
    }
    
    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {
        
        @Test
        @DisplayName("Should create Monte Carlo analysis")
        void shouldCreateMonteCarloAnalysis() {
            MonteCarloUncertainty mc = new MonteCarloUncertainty(params);
            assertThat(mc).isNotNull();
        }
        
        @Test
        @DisplayName("Should create with custom samples and seed")
        void shouldCreateWithCustomSamplesAndSeed() {
            MonteCarloUncertainty mc = new MonteCarloUncertainty(params, 500, 42);
            assertThat(mc).isNotNull();
        }
    }
    
    @Nested
    @DisplayName("Parameter Addition Tests")
    class ParameterAdditionTests {
        
        @Test
        @DisplayName("Should add normal parameter")
        void shouldAddNormalParameter() {
            MonteCarloUncertainty mc = new MonteCarloUncertainty(params)
                    .addNormalParameter("test", 1.0, 0.1);
            assertThat(mc).isNotNull();
        }
        
        @Test
        @DisplayName("Should add uniform parameter")
        void shouldAddUniformParameter() {
            MonteCarloUncertainty mc = new MonteCarloUncertainty(params)
                    .addUniformParameter("test", 0.9, 1.1);
            assertThat(mc).isNotNull();
        }
        
        @Test
        @DisplayName("Should add typical uncertainties")
        void shouldAddTypicalUncertainties() {
            MonteCarloUncertainty mc = new MonteCarloUncertainty(params)
                    .addTypicalUncertainties();
            assertThat(mc).isNotNull();
        }
    }
    
    @Nested
    @DisplayName("Analysis Tests")
    class AnalysisTests {
        
        @Test
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        @DisplayName("Should run Monte Carlo analysis")
        void shouldRunMonteCarloAnalysis() {
            MonteCarloUncertainty mc = new MonteCarloUncertainty(params, 100, 42)
                    .addTypicalUncertainties()
                    .run();
            
            assertThat(mc.getSampleResults()).isNotEmpty();
        }
        
        @Test
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        @DisplayName("Should compute statistical summary")
        void shouldComputeStatisticalSummary() {
            MonteCarloUncertainty mc = new MonteCarloUncertainty(params, 100, 42)
                    .addTypicalUncertainties()
                    .run();
            
            MonteCarloUncertainty.StatisticalSummary summary = mc.getSummary();
            assertThat(summary).isNotNull();
            assertThat(summary.validSamples()).isGreaterThan(0);
        }
        
        @Test
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        @DisplayName("Should compute variable stats")
        void shouldComputeVariableStats() {
            MonteCarloUncertainty mc = new MonteCarloUncertainty(params, 100, 42)
                    .addTypicalUncertainties()
                    .run();
            
            MonteCarloUncertainty.StatisticalSummary summary = mc.getSummary();
            MonteCarloUncertainty.VariableStats cfStats = summary.thrustCoefficient();
            
            assertThat(cfStats.mean()).isGreaterThan(0);
            assertThat(cfStats.stdDev()).isGreaterThanOrEqualTo(0);
            assertThat(cfStats.min()).isLessThanOrEqualTo(cfStats.mean());
            assertThat(cfStats.max()).isGreaterThanOrEqualTo(cfStats.mean());
        }
        
        @Test
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        @DisplayName("Should compute sensitivities")
        void shouldComputeSensitivities() {
            MonteCarloUncertainty mc = new MonteCarloUncertainty(params, 100, 42)
                    .addTypicalUncertainties()
                    .run();
            
            Map<String, Double> sensitivities = mc.getSensitivities();
            assertThat(sensitivities).isNotEmpty();
        }
    }
    
    @Nested
    @DisplayName("Statistics Tests")
    class StatisticsTests {
        
        @Test
        @DisplayName("Variable stats should compute CV")
        void variableStatsShouldComputeCV() {
            MonteCarloUncertainty.VariableStats stats = new MonteCarloUncertainty.VariableStats(
                    100, 5, 85, 115, 100, 90, 110
            );
            
            assertThat(stats.coefficientOfVariation()).isCloseTo(5.0, within(0.01));
        }
        
        @Test
        @DisplayName("Summary should have meaningful toString")
        void summaryShouldHaveMeaningfulToString() {
            MonteCarloUncertainty mc = new MonteCarloUncertainty(params, 50, 42)
                    .addTypicalUncertainties()
                    .run();
            
            String str = mc.getSummary().toString();
            assertThat(str).contains("Monte Carlo Summary");
            assertThat(str).contains("samples");
        }
    }
    
    @Nested
    @DisplayName("Reproducibility Tests")
    class ReproducibilityTests {
        
        @Test
        @Timeout(value = 120, unit = TimeUnit.SECONDS)
        @DisplayName("Same seed should give same results")
        void sameSeedShouldGiveSameResults() {
            MonteCarloUncertainty mc1 = new MonteCarloUncertainty(params, 50, 12345)
                    .addTypicalUncertainties()
                    .run();
            
            MonteCarloUncertainty mc2 = new MonteCarloUncertainty(params, 50, 12345)
                    .addTypicalUncertainties()
                    .run();
            
            assertThat(mc1.getSummary().thrustCoefficient().mean())
                    .isCloseTo(mc2.getSummary().thrustCoefficient().mean(), within(1e-6));
        }
    }
}
