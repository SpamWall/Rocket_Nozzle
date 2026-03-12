package com.nozzle.thermal;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.geometry.NozzleContour;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("BoundaryLayerCorrection Tests")
class BoundaryLayerCorrection_UT {
    
    private NozzleDesignParameters params;
    private NozzleContour contour;
    
    @BeforeEach
    void setUp() {
        params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(2.5)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.AIR)
                .numberOfCharLines(10)
                .wallAngleInitialDegrees(25)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();
        
        contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        contour.generate(50);
    }
    
    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {
        
        @Test
        @DisplayName("Should create boundary layer model")
        void shouldCreateBoundaryLayerModel() {
            BoundaryLayerCorrection bl = new BoundaryLayerCorrection(params, contour);
            assertThat(bl).isNotNull();
        }
        
        @Test
        @DisplayName("Should set transition Reynolds")
        void shouldSetTransitionReynolds() {
            BoundaryLayerCorrection bl = new BoundaryLayerCorrection(params, contour)
                    .setTransitionReynolds(1e6);
            assertThat(bl).isNotNull();
        }
        
        @Test
        @DisplayName("Should set force turbulent")
        void shouldSetForceTurbulent() {
            BoundaryLayerCorrection bl = new BoundaryLayerCorrection(params, contour)
                    .setForceTurbulent(true);
            assertThat(bl).isNotNull();
        }
    }
    
    @Nested
    @DisplayName("Calculation Tests")
    class CalculationTests {
        
        @Test
        @DisplayName("Should calculate boundary layer profile")
        void shouldCalculateBoundaryLayerProfile() {
            BoundaryLayerCorrection bl = new BoundaryLayerCorrection(params, contour)
                    .calculate(List.of());
            
            List<BoundaryLayerCorrection.BoundaryLayerPoint> profile = bl.getBoundaryLayerProfile();
            assertThat(profile).isNotEmpty();
        }
        
        @Test
        @DisplayName("Should calculate exit displacement thickness")
        void shouldCalculateExitDisplacementThickness() {
            BoundaryLayerCorrection bl = new BoundaryLayerCorrection(params, contour)
                    .calculate(List.of());
            
            double deltaStar = bl.getExitDisplacementThickness();
            assertThat(deltaStar).isGreaterThan(0);
            assertThat(deltaStar).isLessThan(params.exitRadius() * 0.1);
        }
        
        @Test
        @DisplayName("Should calculate corrected area ratio")
        void shouldCalculateCorrectedAreaRatio() {
            BoundaryLayerCorrection bl = new BoundaryLayerCorrection(params, contour)
                    .calculate(List.of());
            
            double correctedAR = bl.getCorrectedAreaRatio();
            assertThat(correctedAR).isLessThan(params.exitAreaRatio());
        }
        
        @Test
        @DisplayName("Should calculate thrust coefficient loss")
        void shouldCalculateThrustCoefficientLoss() {
            BoundaryLayerCorrection bl = new BoundaryLayerCorrection(params, contour)
                    .calculate(List.of());
            
            double cfLoss = bl.getThrustCoefficientLoss();
            assertThat(cfLoss).isGreaterThanOrEqualTo(0);
        }
    }
    
    @Nested
    @DisplayName("BoundaryLayerPoint Tests")
    class BoundaryLayerPointTests {
        
        @Test
        @DisplayName("Points should have valid Reynolds numbers")
        void pointsShouldHaveValidReynoldsNumbers() {
            BoundaryLayerCorrection bl = new BoundaryLayerCorrection(params, contour)
                    .setForceTurbulent(true)
                    .calculate(List.of());
            
            for (BoundaryLayerCorrection.BoundaryLayerPoint point : bl.getBoundaryLayerProfile()) {
                assertThat(point.reynoldsNumber()).isGreaterThanOrEqualTo(0);
            }
        }
        
        @Test
        @DisplayName("Shape factor should be reasonable")
        void shapeFactorShouldBeReasonable() {
            BoundaryLayerCorrection bl = new BoundaryLayerCorrection(params, contour)
                    .calculate(List.of());
            
            for (BoundaryLayerCorrection.BoundaryLayerPoint point : bl.getBoundaryLayerProfile()) {
                double H = point.shapeFactor();
                assertThat(H).isBetween(1.0, 3.0);
            }
        }
    }
}
