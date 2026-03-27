package com.nozzle.core;

import com.nozzle.chemistry.ChemistryModel;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.moc.CharacteristicNet;
import com.nozzle.thermal.BoundaryLayerCorrection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PerformanceCalculator Tests")
class PerformanceCalculator_UT {
    
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
    @DisplayName("Divergence Loss via CharacteristicNet")
    class WithCharacteristicNetTests {

        @Test
        @DisplayName("Lambda from non-empty CharacteristicNet wall points reduces divergence loss")
        void nonEmptyNetShouldUseFinalWallAngle() {
            CharacteristicNet net = new CharacteristicNet(params).generate();
            PerformanceCalculator calc = new PerformanceCalculator(params, net, null, null, null)
                    .calculate();

            // With a real MOC net the exit wall angle is near 0 → lambda ≈ 1 → small divergence loss
            assertThat(calc.getDivergenceLoss()).isGreaterThanOrEqualTo(0.0);
            assertThat(calc.getActualThrustCoefficient()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("Empty CharacteristicNet wall list falls back to zero exit angle")
        void emptyNetWallPointsShouldFallBackToZeroAngle() {
            // CharacteristicNet constructed but NOT generated → getWallPoints() is empty
            CharacteristicNet emptyNet = new CharacteristicNet(params);
            PerformanceCalculator calc = new PerformanceCalculator(params, emptyNet, null, null, null)
                    .calculate();

            // exitAngle stays 0 → lambda = 1 → divergenceLoss = 0
            assertThat(calc.getDivergenceLoss()).isCloseTo(0.0, within(1e-9));
        }
    }

    @Nested
    @DisplayName("Divergence Loss via NozzleContour")
    class WithContourTests {

        @Test
        @DisplayName("Generated contour (size >= 2) computes exit angle from last two points")
        void generatedContourShouldComputeExitAngle() {
            NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
            contour.generate(50);

            PerformanceCalculator calc = new PerformanceCalculator(params, null, contour, null, null)
                    .calculate();

            assertThat(calc.getDivergenceLoss()).isGreaterThanOrEqualTo(0.0);
            assertThat(calc.getActualThrustCoefficient()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("Ungenerated contour (size < 2) falls back to zero exit angle")
        void ungeneratedContourShouldFallBackToZeroAngle() {
            // Contour constructed but NOT generated → getContourPoints() is empty (< 2)
            NozzleContour emptyContour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
            PerformanceCalculator calc = new PerformanceCalculator(params, null, emptyContour, null, null)
                    .calculate();

            // points.size() < 2 → exitAngle stays 0 → lambda = 1 → divergenceLoss = 0
            assertThat(calc.getDivergenceLoss()).isCloseTo(0.0, within(1e-9));
        }
    }

    @Nested
    @DisplayName("Boundary Layer Loss")
    class WithBoundaryLayerTests {

        @Test
        @DisplayName("Provided BoundaryLayerCorrection replaces the estimated BL loss")
        void providedBoundaryLayerShouldBeUsedDirectly() {
            NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
            contour.generate(50);
            CharacteristicNet net = new CharacteristicNet(params).generate();

            BoundaryLayerCorrection bl = new BoundaryLayerCorrection(params, contour)
                    .calculate(net.getAllPoints());

            PerformanceCalculator withBL   = new PerformanceCalculator(params, null, null, bl, null).calculate();
            PerformanceCalculator withoutBL = PerformanceCalculator.simple(params).calculate();

            // Both should produce a positive BL loss; the values may differ
            assertThat(withBL.getBoundaryLayerLoss()).isGreaterThanOrEqualTo(0.0);
            // The BL loss reported by the calculator must match what the BL model reports
            assertThat(withBL.getBoundaryLayerLoss())
                    .isCloseTo(bl.getThrustCoefficientLoss(), within(1e-9));
            // Having an explicit BL model produces a different (typically larger) estimate
            // than the crude Re-based fallback at this nozzle scale
            assertThat(withBL.getBoundaryLayerLoss())
                    .isNotEqualTo(withoutBL.getBoundaryLayerLoss());
        }
    }

    @Nested
    @DisplayName("Chemical Loss")
    class WithChemistryTests {

        @Test
        @DisplayName("Frozen chemistry uses the 1% flat estimate")
        void frozenChemistryShouldUseFlatEstimate() {
            ChemistryModel frozen = ChemistryModel.frozen(GasProperties.LOX_RP1_PRODUCTS);
            PerformanceCalculator calc = new PerformanceCalculator(params, null, null, null, frozen)
                    .calculate();

            // frozen branch: chemicalLoss = idealCf * 0.01
            assertThat(calc.getChemicalLoss())
                    .isCloseTo(calc.getIdealThrustCoefficient() * 0.01, within(1e-9));
        }

        @Test
        @DisplayName("Equilibrium chemistry computes loss from gamma variation")
        void equilibriumChemistryShouldComputeGammaLoss() {
            ChemistryModel eq = ChemistryModel.equilibrium(GasProperties.LOX_RP1_PRODUCTS);
            PerformanceCalculator calc = new PerformanceCalculator(params, null, null, null, eq)
                    .calculate();

            // Non-frozen branch executes; chemical loss must still be non-negative
            assertThat(calc.getChemicalLoss()).isGreaterThanOrEqualTo(0.0);
            // And should differ from the flat 1% frozen estimate
            assertThat(calc.getChemicalLoss())
                    .isNotCloseTo(calc.getIdealThrustCoefficient() * 0.01, within(1e-9));
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
