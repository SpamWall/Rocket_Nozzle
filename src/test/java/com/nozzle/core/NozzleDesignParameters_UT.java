/*
 * Copyright (C) 2026  Craig Walters
 *
 * This program is free software: you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free
 *  Software Foundation, either version 3 of the License, or (at your option) any
 *  later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 *  more details.
 *
 *  You should have received a copy of the GNU General Public License along with this
 *  program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *  Contact the owner via the github repository if you would like to license this software for
 *  commercial purposes outside the restrictions imposed by this copyright.
 */

package com.nozzle.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("NozzleDesignParameters Tests")
class NozzleDesignParameters_UT {
    
    private NozzleDesignParameters params;
    
    @BeforeEach
    void setUp() {
        params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.AIR)
                .numberOfCharLines(30)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();
    }
    
    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {
        
        @Test
        @DisplayName("Should create valid parameters")
        void shouldCreateValidParameters() {
            assertThat(params.throatRadius()).isEqualTo(0.05);
            assertThat(params.exitMach()).isEqualTo(3.0);
            assertThat(params.chamberPressure()).isEqualTo(7e6);
            assertThat(params.chamberTemperature()).isEqualTo(3500);
        }
        
        @Test
        @DisplayName("Should reject exit Mach < 1")
        void shouldRejectSubsonicExitMach() {
            assertThatThrownBy(() -> NozzleDesignParameters.builder()
                    .exitMach(0.8)
                    .throatRadius(0.05)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.AIR)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Exit Mach");
        }
        
        @Test
        @DisplayName("Should reject negative throat radius")
        void shouldRejectNegativeThroatRadius() {
            assertThatThrownBy(() -> NozzleDesignParameters.builder()
                    .throatRadius(-0.05)
                    .exitMach(3.0)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.AIR)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class);
        }
        
        @Test
        @DisplayName("Should reject chamber pressure below ambient")
        void shouldRejectLowChamberPressure() {
            assertThatThrownBy(() -> NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(3.0)
                    .chamberPressure(50000)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.AIR)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Chamber pressure must exceed ambient");
        }
        
        @Test
        @DisplayName("Should reject invalid wall angle")
        void shouldRejectInvalidWallAngle() {
            assertThatThrownBy(() -> NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(3.0)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.AIR)
                    .wallAngleInitialDegrees(60) // > 45 degrees
                    .build())
                    .isInstanceOf(IllegalArgumentException.class);
        }
        
        @Test
        @DisplayName("Should reject few characteristic lines")
        void shouldRejectFewCharLines() {
            assertThatThrownBy(() -> NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(3.0)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.AIR)
                    .numberOfCharLines(3)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("At least 5 characteristic lines required");
        }
    }

    @Nested
    @DisplayName("Calculated Properties")
    class CalculatedPropertiesTests {
        
        @Test
        @DisplayName("Should calculate exit area ratio")
        void shouldCalculateExitAreaRatio() {
            double ar = params.exitAreaRatio();
            assertThat(ar).isGreaterThan(1.0);
            assertThat(ar).isCloseTo(params.gasProperties().areaRatio(3.0), within(0.001));
        }
        
        @Test
        @DisplayName("Should calculate exit radius")
        void shouldCalculateExitRadius() {
            double re = params.exitRadius();
            double rt = params.throatRadius();
            double ar = params.exitAreaRatio();
            
            assertThat(re).isGreaterThan(rt);
            assertThat(re * re / (rt * rt)).isCloseTo(ar, within(0.001));
        }
        
        @Test
        @DisplayName("Should calculate throat area")
        void shouldCalculateThroatArea() {
            double at = params.throatArea();
            double expected = Math.PI * 0.05 * 0.05;
            assertThat(at).isCloseTo(expected, within(1e-6));
        }
        
        @Test
        @DisplayName("Should calculate exit area")
        void shouldCalculateExitArea() {
            double ae = params.exitArea();
            double at = params.throatArea();
            double ar = params.exitAreaRatio();
            assertThat(ae).isCloseTo(at * ar, within(1e-6));
        }
        
        @Test
        @DisplayName("Should calculate pressure ratio")
        void shouldCalculatePressureRatio() {
            double pr = params.pressureRatio();
            assertThat(pr).isCloseTo(7e6 / 101325, within(0.1));
        }
        
        @Test
        @DisplayName("Should calculate ideal exit pressure")
        void shouldCalculateIdealExitPressure() {
            double pe = params.idealExitPressure();
            GasProperties gas = params.gasProperties();
            double expected = params.chamberPressure() * gas.isentropicPressureRatio(3.0);
            assertThat(pe).isCloseTo(expected, within(1.0));
        }
        
        @Test
        @DisplayName("Should calculate exit temperature")
        void shouldCalculateExitTemperature() {
            double te = params.exitTemperature();
            assertThat(te).isLessThan(params.chamberTemperature());
            assertThat(te).isGreaterThan(0);
        }
        
        @Test
        @DisplayName("Should calculate exit velocity")
        void shouldCalculateExitVelocity() {
            double ve = params.exitVelocity();
            assertThat(ve).isGreaterThan(0);
            
            // Should be supersonic at exit
            double ae = params.gasProperties().speedOfSound(params.exitTemperature());
            assertThat(ve / ae).isCloseTo(3.0, within(0.01));
        }
        
        @Test
        @DisplayName("Should calculate characteristic velocity")
        void shouldCalculateCharacteristicVelocity() {
            double cStar = params.characteristicVelocity();
            assertThat(cStar).isGreaterThan(0);
            assertThat(cStar).isLessThan(5000); // Reasonable range
        }
        
        @Test
        @DisplayName("Should calculate ideal thrust coefficient")
        void shouldCalculateIdealThrustCoefficient() {
            double cf = params.idealThrustCoefficient();
            assertThat(cf).isGreaterThan(1.0);
            assertThat(cf).isLessThan(2.5); // Reasonable range for supersonic
        }
        
        @Test
        @DisplayName("Should calculate ideal specific impulse")
        void shouldCalculateIdealSpecificImpulse() {
            double isp = params.idealSpecificImpulse();
            assertThat(isp).isGreaterThan(0);
            assertThat(isp).isLessThan(500); // Air Isp should be moderate
        }
        
        @Test
        @DisplayName("Should calculate max Prandtl-Meyer angle")
        void shouldCalculateMaxPrandtlMeyerAngle() {
            double nuMax = params.maxPrandtlMeyerAngle();
            assertThat(Math.toDegrees(nuMax)).isCloseTo(130.45, within(1.0));
        }
    }
    
    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Builder should use defaults")
        void builderShouldUseDefaults() {
            NozzleDesignParameters defaultParams = NozzleDesignParameters.builder()
                    .exitMach(2.0)
                    .build();

            assertThat(defaultParams.numberOfCharLines()).isEqualTo(NozzleDesignParameters.DEFAULT_CHAR_LINES);
            assertThat(defaultParams.axisymmetric()).isTrue();
        }

        @Test
        @DisplayName("Builder should use default throat curvature ratio of 0.382")
        void builderShouldUseDefaultThroatCurvatureRatio() {
            NozzleDesignParameters defaultParams = NozzleDesignParameters.builder()
                    .exitMach(2.0)
                    .build();

            assertThat(defaultParams.throatCurvatureRatio())
                    .isCloseTo(NozzleDesignParameters.DEFAULT_THROAT_CURVATURE_RATIO, within(1e-9));
        }

        @Test
        @DisplayName("Builder should allow planar configuration")
        void builderShouldAllowPlanar() {
            NozzleDesignParameters planarParams = NozzleDesignParameters.builder()
                    .exitMach(2.0)
                    .planar()
                    .build();

            assertThat(planarParams.axisymmetric()).isFalse();
        }

        @Test
        @DisplayName("Builder should accept wall angle in degrees")
        void builderShouldAcceptWallAngleDegrees() {
            NozzleDesignParameters p = NozzleDesignParameters.builder()
                    .exitMach(2.0)
                    .wallAngleInitialDegrees(25)
                    .build();

            assertThat(Math.toDegrees(p.wallAngleInitial())).isCloseTo(25, within(0.001));
        }

        @Test
        @DisplayName("Builder should accept custom throat curvature ratio")
        void builderShouldAcceptCustomThroatCurvatureRatio() {
            NozzleDesignParameters p = NozzleDesignParameters.builder()
                    .exitMach(2.0)
                    .throatCurvatureRatio(0.75)
                    .build();

            assertThat(p.throatCurvatureRatio()).isCloseTo(0.75, within(1e-9));
        }

        @Test
        @DisplayName("Builder should reject throat curvature ratio of zero")
        void builderShouldRejectZeroThroatCurvatureRatio() {
            assertThatThrownBy(() -> NozzleDesignParameters.builder()
                    .exitMach(2.0)
                    .throatCurvatureRatio(0.0)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Throat curvature ratio");
        }

        @Test
        @DisplayName("Builder should reject throat curvature ratio above 2.0")
        void builderShouldRejectExcessiveThroatCurvatureRatio() {
            assertThatThrownBy(() -> NozzleDesignParameters.builder()
                    .exitMach(2.0)
                    .throatCurvatureRatio(2.1)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Throat curvature ratio");
        }

        @Test
        @DisplayName("Builder should accept boundary values for throat curvature ratio")
        void builderShouldAcceptBoundaryThroatCurvatureRatio() {
            // Just above zero and exactly at the upper bound should both be accepted
            assertThatCode(() -> NozzleDesignParameters.builder()
                    .exitMach(2.0)
                    .throatCurvatureRatio(0.01)
                    .build())
                    .doesNotThrowAnyException();

            assertThatCode(() -> NozzleDesignParameters.builder()
                    .exitMach(2.0)
                    .throatCurvatureRatio(2.0)
                    .build())
                    .doesNotThrowAnyException();
        }

        // --- upstreamCurvatureRatio ---

        @Test
        @DisplayName("Builder should use default upstream curvature ratio of 1.5")
        void builderShouldUseDefaultUpstreamCurvatureRatio() {
            NozzleDesignParameters p = NozzleDesignParameters.builder().exitMach(2.0).build();
            assertThat(p.upstreamCurvatureRatio())
                    .isCloseTo(NozzleDesignParameters.DEFAULT_UPSTREAM_CURVATURE_RATIO, within(1e-9));
        }

        @Test
        @DisplayName("Builder should accept custom upstream curvature ratio")
        void builderShouldAcceptCustomUpstreamCurvatureRatio() {
            NozzleDesignParameters p = NozzleDesignParameters.builder()
                    .exitMach(2.0).upstreamCurvatureRatio(2.0).build();
            assertThat(p.upstreamCurvatureRatio()).isCloseTo(2.0, within(1e-9));
        }

        @Test
        @DisplayName("Builder should reject upstream curvature ratio above 3.0")
        void builderShouldRejectExcessiveUpstreamCurvatureRatio() {
            assertThatThrownBy(() -> NozzleDesignParameters.builder()
                    .exitMach(2.0).upstreamCurvatureRatio(3.1).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Upstream curvature ratio");
        }

        @Test
        @DisplayName("Builder should reject non-positive upstream curvature ratio")
        void builderShouldRejectZeroUpstreamCurvatureRatio() {
            assertThatThrownBy(() -> NozzleDesignParameters.builder()
                    .exitMach(2.0).upstreamCurvatureRatio(0.0).build())
                    .isInstanceOf(IllegalArgumentException.class);
        }

        // --- convergentHalfAngle ---

        @Test
        @DisplayName("Builder should use default convergent half-angle of 30 degrees")
        void builderShouldUseDefaultConvergentHalfAngle() {
            NozzleDesignParameters p = NozzleDesignParameters.builder().exitMach(2.0).build();
            assertThat(Math.toDegrees(p.convergentHalfAngle())).isCloseTo(30.0, within(0.001));
        }

        @Test
        @DisplayName("Builder should accept convergent half-angle in degrees")
        void builderShouldAcceptConvergentHalfAngleDegrees() {
            NozzleDesignParameters p = NozzleDesignParameters.builder()
                    .exitMach(2.0).convergentHalfAngleDegrees(45).build();
            assertThat(Math.toDegrees(p.convergentHalfAngle())).isCloseTo(45.0, within(0.001));
            assertThat(p.convergentHalfAngleDegrees()).isCloseTo(45.0, within(0.001));
        }

        @Test
        @DisplayName("Builder radians setter and degrees setter produce the same result")
        void convergentHalfAngleRadiansSetterMatchesDegreesSetter() {
            double rad = Math.toRadians(40.0);
            NozzleDesignParameters fromRad = NozzleDesignParameters.builder()
                    .exitMach(2.0).convergentHalfAngle(rad).build();
            NozzleDesignParameters fromDeg = NozzleDesignParameters.builder()
                    .exitMach(2.0).convergentHalfAngleDegrees(40.0).build();
            assertThat(fromRad.convergentHalfAngle())
                    .isCloseTo(fromDeg.convergentHalfAngle(), within(1e-12));
        }

        @Test
        @DisplayName("Builder should reject convergent half-angle below 5 degrees")
        void builderShouldRejectTooSmallConvergentHalfAngle() {
            assertThatThrownBy(() -> NozzleDesignParameters.builder()
                    .exitMach(2.0).convergentHalfAngleDegrees(4).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Convergent half-angle");
        }

        @Test
        @DisplayName("Builder should reject convergent half-angle above 60 degrees")
        void builderShouldRejectTooLargeConvergentHalfAngle() {
            assertThatThrownBy(() -> NozzleDesignParameters.builder()
                    .exitMach(2.0).convergentHalfAngleDegrees(61).build())
                    .isInstanceOf(IllegalArgumentException.class);
        }

        // --- contractionRatio ---

        @Test
        @DisplayName("Builder should use default contraction ratio of 4.0")
        void builderShouldUseDefaultContractionRatio() {
            NozzleDesignParameters p = NozzleDesignParameters.builder().exitMach(2.0).build();
            assertThat(p.contractionRatio())
                    .isCloseTo(NozzleDesignParameters.DEFAULT_CONTRACTION_RATIO, within(1e-9));
        }

        @Test
        @DisplayName("Builder should accept custom contraction ratio")
        void builderShouldAcceptCustomContractionRatio() {
            NozzleDesignParameters p = NozzleDesignParameters.builder()
                    .exitMach(2.0).contractionRatio(6.0).build();
            assertThat(p.contractionRatio()).isCloseTo(6.0, within(1e-9));
        }

        @Test
        @DisplayName("Builder should reject contraction ratio below 1.5")
        void builderShouldRejectTooSmallContractionRatio() {
            assertThatThrownBy(() -> NozzleDesignParameters.builder()
                    .exitMach(2.0).contractionRatio(1.4).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Contraction ratio");
        }

        @Test
        @DisplayName("Builder should reject contraction ratio above 20")
        void builderShouldRejectTooLargeContractionRatio() {
            assertThatThrownBy(() -> NozzleDesignParameters.builder()
                    .exitMach(2.0).contractionRatio(21.0).build())
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("chamberRadius() should equal throatRadius × sqrt(contractionRatio)")
        void chamberRadiusShouldMatchFormula() {
            NozzleDesignParameters p = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(2.0).contractionRatio(4.0).build();
            assertThat(p.chamberRadius()).isCloseTo(0.05 * Math.sqrt(4.0), within(1e-9));
        }

        // --- dischargeCoefficient ---

        @Test
        @DisplayName("dischargeCoefficient() should be in [0.98, 1.0]")
        void dischargeCoefficientShouldBeInValidRange() {
            NozzleDesignParameters p = NozzleDesignParameters.builder().exitMach(2.0).build();
            assertThat(p.dischargeCoefficient()).isBetween(0.98, 1.0);
        }

        @Test
        @DisplayName("dischargeCoefficient() should be strictly less than 1.0 for finite curvature")
        void dischargeCoefficientShouldBeLessThanOne() {
            NozzleDesignParameters p = NozzleDesignParameters.builder().exitMach(2.0).build();
            assertThat(p.dischargeCoefficient()).isLessThan(1.0);
        }

        @Test
        @DisplayName("dischargeCoefficient() expected value for defaults")
        void dischargeCoefficientExpectedValueForDefaults() {
            // defaults: r_cd/r_t = 0.382, r_cu/r_t = 1.5, γ = 1.4
            // κ = (0.382 + 1.5) / (2 × 0.382 × 1.5) ≈ 1.6422
            // Cd = 1 − 0.0023 × 1.6422 ≈ 0.9962
            NozzleDesignParameters p = NozzleDesignParameters.builder().exitMach(2.0).build();
            assertThat(p.dischargeCoefficient()).isCloseTo(0.9962, within(0.0005));
        }

        @Test
        @DisplayName("dischargeCoefficient() should decrease as throatCurvatureRatio decreases")
        void dischargeCoefficientDecreasesWithSmallerDownstreamRadius() {
            NozzleDesignParameters small = NozzleDesignParameters.builder()
                    .exitMach(2.0).throatCurvatureRatio(0.2).build();
            NozzleDesignParameters large = NozzleDesignParameters.builder()
                    .exitMach(2.0).throatCurvatureRatio(1.0).build();
            assertThat(small.dischargeCoefficient()).isLessThan(large.dischargeCoefficient());
        }

        @Test
        @DisplayName("dischargeCoefficient() should increase as upstreamCurvatureRatio increases")
        void dischargeCoefficientIncreasesWithLargerUpstreamRadius() {
            NozzleDesignParameters small = NozzleDesignParameters.builder()
                    .exitMach(2.0).upstreamCurvatureRatio(0.5).build();
            NozzleDesignParameters large = NozzleDesignParameters.builder()
                    .exitMach(2.0).upstreamCurvatureRatio(2.5).build();
            assertThat(small.dischargeCoefficient()).isLessThan(large.dischargeCoefficient());
        }

        @Test
        @DisplayName("dischargeCoefficient() should be independent of throatRadius")
        void dischargeCoefficientIsIndependentOfThroatRadius() {
            NozzleDesignParameters small = NozzleDesignParameters.builder()
                    .exitMach(2.0).throatRadius(0.01).build();
            NozzleDesignParameters large = NozzleDesignParameters.builder()
                    .exitMach(2.0).throatRadius(0.5).build();
            assertThat(small.dischargeCoefficient())
                    .isCloseTo(large.dischargeCoefficient(), within(1e-9));
        }
    }
    
    @Nested
    @DisplayName("Planar vs Axisymmetric")
    class PlanarVsAxisymmetricTests {

        @Test
        @DisplayName("Axisymmetric should use circular throat area")
        void axisymmetricShouldUseCircularArea() {
            NozzleDesignParameters axiParams = NozzleDesignParameters.builder()
                    .throatRadius(0.1)
                    .exitMach(2.0)
                    .axisymmetric(true)
                    .build();

            double at = axiParams.throatArea();
            assertThat(at).isCloseTo(Math.PI * 0.1 * 0.1, within(1e-6));
        }

        @Test
        @DisplayName("Planar should use 2D throat area")
        void planarShouldUse2DArea() {
            NozzleDesignParameters planarParams = NozzleDesignParameters.builder()
                    .throatRadius(0.1) // Half-height in 2D
                    .exitMach(2.0)
                    .axisymmetric(false)
                    .build();

            double at = planarParams.throatArea();
            assertThat(at).isCloseTo(2.0 * 0.1, within(1e-6));
        }
    }

    @Nested
    @DisplayName("Throat width (2D planar nozzles)")
    class ThroatWidthTests {

        @Test
        @DisplayName("Default throat width is 1.0 m (per-unit-depth convention)")
        void defaultThroatWidthIsOne() {
            NozzleDesignParameters p = NozzleDesignParameters.builder()
                    .exitMach(2.0).planar().build();
            assertThat(p.throatWidth()).isEqualTo(NozzleDesignParameters.DEFAULT_THROAT_WIDTH);
        }

        @Test
        @DisplayName("Builder stores the supplied throat width")
        void builderStoresThroatWidth() {
            NozzleDesignParameters p = NozzleDesignParameters.builder()
                    .exitMach(2.0).planar().throatWidth(0.3).build();
            assertThat(p.throatWidth()).isCloseTo(0.3, within(1e-12));
        }

        @Test
        @DisplayName("throatArea() equals 2 × throatRadius × throatWidth for planar nozzle")
        void planarThroatAreaUsesWidth() {
            NozzleDesignParameters p = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(2.0).planar().throatWidth(0.4).build();
            assertThat(p.throatArea()).isCloseTo(2.0 * 0.05 * 0.4, within(1e-12));
        }

        @Test
        @DisplayName("throatArea() scales linearly with throatWidth")
        void throatAreaScalesWithWidth() {
            NozzleDesignParameters p1 = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(2.0).planar().throatWidth(0.2).build();
            NozzleDesignParameters p2 = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(2.0).planar().throatWidth(0.4).build();
            assertThat(p2.throatArea()).isCloseTo(2.0 * p1.throatArea(), within(1e-12));
        }

        @Test
        @DisplayName("throatArea() for axisymmetric is unaffected by throatWidth")
        void axisymmetricIgnoresThroatWidth() {
            NozzleDesignParameters p = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(2.0).axisymmetric(true).throatWidth(0.5).build();
            assertThat(p.throatArea()).isCloseTo(Math.PI * 0.05 * 0.05, within(1e-12));
        }

        @Test
        @DisplayName("Zero throat width throws IllegalArgumentException")
        void zeroThroatWidthThrows() {
            assertThatThrownBy(() -> NozzleDesignParameters.builder()
                    .exitMach(2.0).planar().throatWidth(0.0).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Throat width");
        }

        @Test
        @DisplayName("Negative throat width throws IllegalArgumentException")
        void negativeThroatWidthThrows() {
            assertThatThrownBy(() -> NozzleDesignParameters.builder()
                    .exitMach(2.0).planar().throatWidth(-0.1).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Throat width");
        }

        @Test
        @DisplayName("exitArea() uses throatWidth via throatArea × exitAreaRatio")
        void exitAreaUsesWidth() {
            NozzleDesignParameters p = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(2.0).planar().throatWidth(0.3).build();
            assertThat(p.exitArea())
                    .isCloseTo(p.throatArea() * p.exitAreaRatio(), within(1e-12));
        }
    }

    @Nested
    @DisplayName("ConvergentLength Tests")
    class ConvergentLengthTests {

        private NozzleDesignParameters baseParams() {
            return NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(3.0)
                    .upstreamCurvatureRatio(1.5)
                    .convergentHalfAngleDegrees(30.0)
                    .contractionRatio(4.0)
                    .build();
        }

        @Test
        @DisplayName("convergentLength() is positive for standard geometry")
        void convergentLengthIsPositive() {
            assertThat(baseParams().convergentLength()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("convergentLength() matches closed-form formula")
        void convergentLengthMatchesFormula() {
            double rt  = 0.05;
            double rcu = 1.5 * rt;
            double tc  = Math.toRadians(30.0);
            double rc  = rt * Math.sqrt(4.0);  // chamberRadius for axisymmetric
            double yArc     = rt + rcu * (1.0 - Math.cos(tc));
            double xArc     = -rcu * Math.sin(tc);
            double xChamber = xArc - (rc - yArc) / Math.tan(tc);
            double expected = -xChamber;

            assertThat(baseParams().convergentLength())
                    .isCloseTo(expected, within(1e-12));
        }

        @Test
        @DisplayName("convergentLength() grows with larger contraction ratio")
        void convergentLengthGrowsWithContractionRatio() {
            NozzleDesignParameters low  = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0).contractionRatio(2.5).build();
            NozzleDesignParameters high = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0).contractionRatio(6.0).build();
            assertThat(low.convergentLength()).isLessThan(high.convergentLength());
        }

        @Test
        @DisplayName("convergentLength() decreases as half-angle increases (steeper cone is shorter)")
        void convergentLengthDecreasesWithLargerHalfAngle() {
            NozzleDesignParameters shallow = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0).convergentHalfAngleDegrees(20.0)
                    .contractionRatio(4.0).build();
            NozzleDesignParameters steep  = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0).convergentHalfAngleDegrees(45.0)
                    .contractionRatio(4.0).build();
            assertThat(shallow.convergentLength()).isGreaterThan(steep.convergentLength());
        }

        @Test
        @DisplayName("convergentLength() scales linearly with throatRadius")
        void convergentLengthScalesWithThroatRadius() {
            NozzleDesignParameters small = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0).contractionRatio(4.0).build();
            NozzleDesignParameters large = NozzleDesignParameters.builder()
                    .throatRadius(0.10).exitMach(3.0).contractionRatio(4.0).build();
            // doubling r_t doubles every length in the geometry
            assertThat(large.convergentLength())
                    .isCloseTo(small.convergentLength() * 2.0, within(1e-12));
        }

        @Test
        @DisplayName("convergentLengthRatio() equals convergentLength / (2 * throatRadius)")
        void convergentLengthRatioDefinition() {
            NozzleDesignParameters p = baseParams();
            double expected = p.convergentLength() / (2.0 * p.throatRadius());
            assertThat(p.convergentLengthRatio()).isCloseTo(expected, within(1e-12));
        }

        @Test
        @DisplayName("convergentLengthRatio() is positive")
        void convergentLengthRatioIsPositive() {
            assertThat(baseParams().convergentLengthRatio()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("convergentLengthRatio() grows with contraction ratio")
        void convergentLengthRatioGrowsWithContractionRatio() {
            NozzleDesignParameters low  = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0).contractionRatio(2.5).build();
            NozzleDesignParameters high = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0).contractionRatio(6.0).build();
            assertThat(low.convergentLengthRatio()).isLessThan(high.convergentLengthRatio());
        }

        @Test
        @DisplayName("convergentLengthRatio() is independent of throatRadius")
        void convergentLengthRatioIsIndependentOfThroatRadius() {
            NozzleDesignParameters small = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0).contractionRatio(4.0).build();
            NozzleDesignParameters large = NozzleDesignParameters.builder()
                    .throatRadius(0.10).exitMach(3.0).contractionRatio(4.0).build();
            assertThat(large.convergentLengthRatio())
                    .isCloseTo(small.convergentLengthRatio(), within(1e-12));
        }
    }
}
