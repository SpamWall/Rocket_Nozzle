package com.nozzle.optimization;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

// Single-candidate config used throughout to keep tests fast
// 1 AR × 1 LF × 1 WA = 1 candidate
class Fixture {
    static final AltitudeAdaptiveOptimizer.OptimizationConfig SINGLE =
            new AltitudeAdaptiveOptimizer.OptimizationConfig(
                    new double[]{1.0}, new double[]{0.8}, new double[]{30});
}

@DisplayName("AltitudeAdaptiveOptimizer Tests")
class AltitudeAdaptiveOptimizer_UT {
    
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
        @DisplayName("Should create optimizer")
        void shouldCreateOptimizer() {
            AltitudeAdaptiveOptimizer opt = new AltitudeAdaptiveOptimizer(params);
            assertThat(opt).isNotNull();
        }
        
        @Test
        @DisplayName("Should create optimizer with config")
        void shouldCreateOptimizerWithConfig() {
            AltitudeAdaptiveOptimizer opt = new AltitudeAdaptiveOptimizer(params,
                    AltitudeAdaptiveOptimizer.OptimizationConfig.defaults());
            assertThat(opt).isNotNull();
        }
    }
    
    @Nested
    @DisplayName("Altitude Profile Tests")
    class AltitudeProfileTests {
        
        @Test
        @DisplayName("Should add altitude condition")
        void shouldAddAltitudeCondition() {
            AltitudeAdaptiveOptimizer opt = new AltitudeAdaptiveOptimizer(params)
                    .addAltitudeCondition(0, 0.5, 60)
                    .addAltitudeCondition(30000, 0.5, 60);
            assertThat(opt).isNotNull();
        }
        
        @Test
        @DisplayName("Should add standard profile")
        void shouldAddStandardProfile() {
            AltitudeAdaptiveOptimizer opt = new AltitudeAdaptiveOptimizer(params)
                    .addStandardProfile();
            assertThat(opt).isNotNull();
        }
    }
    
    @Nested
    @DisplayName("Optimization Tests")
    class OptimizationTests {
        
        @Test
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        @DisplayName("Should run optimization")
        void shouldRunOptimization() {
            AltitudeAdaptiveOptimizer opt = new AltitudeAdaptiveOptimizer(params,
                    new AltitudeAdaptiveOptimizer.OptimizationConfig(
                            new double[]{0.9, 1.0, 1.1},
                            new double[]{0.8},
                            new double[]{30}
                    ))
                    .addAltitudeCondition(0, 0.5, 30)
                    .addAltitudeCondition(50000, 0.5, 30)
                    .optimize();
            
            assertThat(opt.getResults()).isNotEmpty();
        }
        
        @Test
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        @DisplayName("Should find best result")
        void shouldFindBestResult() {
            AltitudeAdaptiveOptimizer opt = new AltitudeAdaptiveOptimizer(params,
                    new AltitudeAdaptiveOptimizer.OptimizationConfig(
                            new double[]{1.0},
                            new double[]{0.8},
                            new double[]{30}
                    ))
                    .addAltitudeCondition(0, 1.0, 60)
                    .optimize();
            
            AltitudeAdaptiveOptimizer.OptimizationResult best = opt.getBestResult();
            assertThat(best).isNotNull();
            assertThat(best.objectiveValue()).isGreaterThan(0);
        }
        
        @Test
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        @DisplayName("Should get top results")
        void shouldGetTopResults() {
            AltitudeAdaptiveOptimizer opt = new AltitudeAdaptiveOptimizer(params,
                    new AltitudeAdaptiveOptimizer.OptimizationConfig(
                            new double[]{0.9, 1.0, 1.1},
                            new double[]{0.8},
                            new double[]{30}
                    ))
                    .addAltitudeCondition(0, 1.0, 60)
                    .optimize();
            
            List<AltitudeAdaptiveOptimizer.OptimizationResult> top3 = opt.getTopResults(3);
            assertThat(top3).hasSizeLessThanOrEqualTo(3);
        }
    }
    
    @Nested
    @DisplayName("Atmospheric Pressure Model Tests")
    class AtmosphericPressureTests {

        /** Exercises the  altitude < 0  clamp branch. */
        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Negative altitude should be clamped and produce sea-level pressure")
        void negativeAltitudeShouldBeClampedToSeaLevel() {
            // Two conditions: one at –1000 m (clamped to 0) and one at 0 m.
            // Both must yield the same (sea-level) pressure so both evaluations succeed.
            AltitudeAdaptiveOptimizer opt = new AltitudeAdaptiveOptimizer(params, Fixture.SINGLE)
                    .addAltitudeCondition(-1000, 0.5, 30)
                    .addAltitudeCondition(0,     0.5, 30)
                    .optimize();

            assertThat(opt.getResults()).isNotEmpty();
            // Both conditions land at identical pressure → identical Isp contribution;
            // the objective is finite and positive.
            assertThat(opt.getBestResult().objectiveValue()).isGreaterThan(0);
        }

        /** Exercises the  altitude <= 25000  (lower stratosphere) branch. */
        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Altitude 15 km should use the lower-stratosphere formula")
        void lowerStratosphereShouldProduceValidPressure() {
            // 15 000 m lies in 11 000 < alt ≤ 25 000 (lower stratosphere / isothermal layer)
            AltitudeAdaptiveOptimizer opt = new AltitudeAdaptiveOptimizer(params, Fixture.SINGLE)
                    .addAltitudeCondition(15_000, 1.0, 30)
                    .optimize();

            assertThat(opt.getBestResult()).isNotNull();
            // Lower-stratosphere pressure ≈ 12 000 Pa — well within valid range
            assertThat(opt.getBestResult().objectiveValue()).isGreaterThan(0);
        }

        /** Exercises the  altitude > 100 000  (near-vacuum) branch. */
        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Altitude above 100 km should return near-vacuum pressure (1 Pa)")
        void altitudeAbove100kmShouldReturnNearVacuumPressure() {
            // 150 000 m → the else-branch → returns 1.0 Pa
            AltitudeAdaptiveOptimizer opt = new AltitudeAdaptiveOptimizer(params, Fixture.SINGLE)
                    .addAltitudeCondition(150_000, 1.0, 60)
                    .optimize();

            assertThat(opt.getBestResult()).isNotNull();
            assertThat(opt.getBestResult().objectiveValue()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Edge Case Optimization Tests")
    class EdgeCaseOptimizationTests {

        /** Exercises the  totalWeight == 0  branch in evaluateCandidate. */
        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Empty altitude profile should produce zero objective for every candidate")
        void emptyAltitudeProfileShouldProduceZeroObjective() {
            // No conditions → altitudeProfile is empty → totalWeight = 0 → normalizedObjective = 0
            AltitudeAdaptiveOptimizer opt = new AltitudeAdaptiveOptimizer(params, Fixture.SINGLE)
                    .optimize();  // no addAltitudeCondition() call

            assertThat(opt.getResults()).isNotEmpty();
            // Every candidate gets objectiveValue = 0.0
            opt.getResults().forEach(r -> assertThat(r.objectiveValue()).isZero());
            // Best result exists but has zero objective
            assertThat(opt.getBestResult()).isNotNull();
            assertThat(opt.getBestResult().objectiveValue()).isZero();
        }

        /**
         * Exercises the  perf.separated ? " (SEPARATED)" : ""  branch inside
         * OptimizationResult.toString().
         *
         * A Me=6 vacuum bell at sea level (pa ≈ 101 325 Pa) is strongly overexpanded;
         * the Summerfield criterion predicts flow separation → AltitudePerformance.separated = true.
         */
        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Separated flow should appear in OptimizationResult.toString()")
        void separatedFlowShouldAppearInToString() {
            NozzleDesignParameters vacuumBell = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(6.0)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .numberOfCharLines(10)
                    .wallAngleInitialDegrees(30)
                    .lengthFraction(0.8)
                    .axisymmetric(true)
                    .build();

            AltitudeAdaptiveOptimizer opt = new AltitudeAdaptiveOptimizer(vacuumBell, Fixture.SINGLE)
                    .addAltitudeCondition(0, 1.0, 60)   // sea level → overexpanded → SEPARATED
                    .optimize();

            assertThat(opt.getBestResult()).isNotNull();
            // At least one altitude performance point must show separation
            boolean anySeparated = opt.getBestResult().performances().stream()
                    .anyMatch(AltitudeAdaptiveOptimizer.AltitudePerformance::separated);
            assertThat(anySeparated).isTrue();

            // toString() must print "(SEPARATED)" for that point
            String str = opt.getBestResult().toString();
            assertThat(str).contains("(SEPARATED)");
        }

        /**
         * Exercises the  catch (Exception e)  block in optimize().
         *
         * Chamber pressure is set just below sea-level atmospheric (101 325 Pa) but still
         * above the base ambient pressure, so the base params are valid.  When a condition
         * at altitude = 0 is evaluated, the internal altParams builder tries to set
         * ambientPressure = 101 325 Pa > chamberPressure = 101 000 Pa, which throws
         * IllegalArgumentException inside the callable.  future.get() wraps this in
         * ExecutionException, which is caught by catch (Exception e) → System.err.println.
         */
        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Exception inside evaluateCandidate should be caught and not propagate")
        void exceptionInEvaluateCandidateShouldBeCaughtGracefully() {
            NozzleDesignParameters lowPcParams = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(3.0)
                    .chamberPressure(101_000)   // < sea-level atmospheric 101 325 Pa
                    .chamberTemperature(3500)
                    .ambientPressure(50_000)    // < chamberPressure → base params valid
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .numberOfCharLines(10)
                    .wallAngleInitialDegrees(30)
                    .lengthFraction(0.8)
                    .axisymmetric(true)
                    .build();

            // altitude = 0 → condition.pressure() = 101 325 Pa > chamberPressure = 101 000
            // → altParams builder throws inside the callable → caught by catch (Exception e)
            assertThatNoException().isThrownBy(() ->
                    new AltitudeAdaptiveOptimizer(lowPcParams, Fixture.SINGLE)
                            .addAltitudeCondition(0, 1.0, 60)
                            .optimize());

            AltitudeAdaptiveOptimizer opt = new AltitudeAdaptiveOptimizer(lowPcParams, Fixture.SINGLE)
                    .addAltitudeCondition(0, 1.0, 60)
                    .optimize();

            // All evaluations threw → no results collected → bestResult is null
            assertThat(opt.getResults()).isEmpty();
            assertThat(opt.getBestResult()).isNull();
        }
    }

    @Nested
    @DisplayName("Config Tests")
    class ConfigTests {
        
        @Test
        @DisplayName("Should have default config")
        void shouldHaveDefaultConfig() {
            AltitudeAdaptiveOptimizer.OptimizationConfig config = 
                    AltitudeAdaptiveOptimizer.OptimizationConfig.defaults();
            
            assertThat(config.areaRatioRange()).isNotEmpty();
            assertThat(config.lengthFractionRange()).isNotEmpty();
            assertThat(config.wallAngleRange()).isNotEmpty();
        }
        
        @Test
        @DisplayName("Should have fine config")
        void shouldHaveFineConfig() {
            AltitudeAdaptiveOptimizer.OptimizationConfig config = 
                    AltitudeAdaptiveOptimizer.OptimizationConfig.fine();
            
            assertThat(config.areaRatioRange().length)
                    .isGreaterThan(AltitudeAdaptiveOptimizer.OptimizationConfig.defaults().areaRatioRange().length);
        }
    }
}
