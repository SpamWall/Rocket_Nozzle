package com.nozzle.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PerformanceCalculator Tests")
class PerformanceCalculatorTest {
    
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
                .numberOfCharLines(20)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();
    }
    
    @Nested
    @DisplayName("Simple Calculator Tests")
    class SimpleCalculatorTests {
        
        @Test
        @DisplayName("Should create simple calculator")
        void shouldCreateSimpleCalculator() {
            PerformanceCalculator calc = PerformanceCalculator.simple(params);
            assertThat(calc).isNotNull();
        }
        
        @Test
        @DisplayName("Should calculate performance")
        void shouldCalculatePerformance() {
            PerformanceCalculator calc = PerformanceCalculator.simple(params).calculate();
            
            assertThat(calc.getIdealThrustCoefficient()).isGreaterThan(1.0);
            assertThat(calc.getActualThrustCoefficient()).isGreaterThan(0);
            assertThat(calc.getEfficiency()).isBetween(0.8, 1.0);
        }
        
        @Test
        @DisplayName("Should calculate specific impulse")
        void shouldCalculateSpecificImpulse() {
            PerformanceCalculator calc = PerformanceCalculator.simple(params).calculate();
            
            double isp = calc.getSpecificImpulse();
            assertThat(isp).isGreaterThan(100);
            assertThat(isp).isLessThan(500);
        }
        
        @Test
        @DisplayName("Should calculate thrust")
        void shouldCalculateThrust() {
            PerformanceCalculator calc = PerformanceCalculator.simple(params).calculate();
            
            double thrust = calc.getThrust();
            assertThat(thrust).isGreaterThan(0);
        }
        
        @Test
        @DisplayName("Should calculate mass flow rate")
        void shouldCalculateMassFlowRate() {
            PerformanceCalculator calc = PerformanceCalculator.simple(params).calculate();
            
            double mdot = calc.getMassFlowRate();
            assertThat(mdot).isGreaterThan(0);
        }
    }
    
    @Nested
    @DisplayName("Loss Calculations")
    class LossCalculationsTests {
        
        @Test
        @DisplayName("Should calculate divergence loss")
        void shouldCalculateDivergenceLoss() {
            PerformanceCalculator calc = PerformanceCalculator.simple(params).calculate();
            
            double divLoss = calc.getDivergenceLoss();
            assertThat(divLoss).isGreaterThanOrEqualTo(0);
            assertThat(divLoss).isLessThan(calc.getIdealThrustCoefficient() * 0.1);
        }
        
        @Test
        @DisplayName("Should calculate boundary layer loss")
        void shouldCalculateBoundaryLayerLoss() {
            PerformanceCalculator calc = PerformanceCalculator.simple(params).calculate();
            
            double blLoss = calc.getBoundaryLayerLoss();
            assertThat(blLoss).isGreaterThanOrEqualTo(0);
        }
        
        @Test
        @DisplayName("Should calculate total loss")
        void shouldCalculateTotalLoss() {
            PerformanceCalculator calc = PerformanceCalculator.simple(params).calculate();
            
            double totalLoss = calc.getTotalLoss();
            double expectedTotal = calc.getDivergenceLoss() + calc.getBoundaryLayerLoss() 
                    + calc.getChemicalLoss();
            
            assertThat(totalLoss).isCloseTo(expectedTotal, within(1e-6));
        }
        
        @Test
        @DisplayName("Actual Cf should be less than ideal")
        void actualCfShouldBeLessThanIdeal() {
            PerformanceCalculator calc = PerformanceCalculator.simple(params).calculate();
            
            assertThat(calc.getActualThrustCoefficient())
                    .isLessThanOrEqualTo(calc.getIdealThrustCoefficient());
        }
    }
    
    @Nested
    @DisplayName("Summary Tests")
    class SummaryTests {
        
        @Test
        @DisplayName("Should generate summary")
        void shouldGenerateSummary() {
            PerformanceCalculator calc = PerformanceCalculator.simple(params).calculate();
            
            PerformanceCalculator.PerformanceSummary summary = calc.getSummary();
            assertThat(summary).isNotNull();
            assertThat(summary.idealCf()).isEqualTo(calc.getIdealThrustCoefficient());
            assertThat(summary.actualCf()).isEqualTo(calc.getActualThrustCoefficient());
        }
        
        @Test
        @DisplayName("Summary should have meaningful toString")
        void summaryShouldHaveMeaningfulToString() {
            PerformanceCalculator calc = PerformanceCalculator.simple(params).calculate();
            
            String str = calc.getSummary().toString();
            assertThat(str).contains("Performance Summary");
            assertThat(str).contains("Isp");
            assertThat(str).contains("Thrust");
        }
    }
}
