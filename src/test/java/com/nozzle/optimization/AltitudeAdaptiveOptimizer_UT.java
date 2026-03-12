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
