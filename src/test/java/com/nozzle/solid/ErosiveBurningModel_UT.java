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

package com.nozzle.solid;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ErosiveBurningModel Tests")
class ErosiveBurningModel_UT {

    private SolidPropellant propellant;
    private ErosiveBurningModel model;

    @BeforeEach
    void setUp() {
        propellant = SolidPropellant.APCP_HTPB();
        model      = new ErosiveBurningModel(propellant);
    }

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("Should construct with default L-R coefficients")
        void shouldConstructDefault() {
            ErosiveBurningModel m = new ErosiveBurningModel(propellant);
            assertThat(m.getAlpha()).isCloseTo(ErosiveBurningModel.DEFAULT_ALPHA, within(1e-6));
            assertThat(m.getBeta()).isCloseTo(ErosiveBurningModel.DEFAULT_BETA, within(1e-3));
            assertThat(m.getThresholdFlux()).isCloseTo(ErosiveBurningModel.DEFAULT_THRESHOLD, within(1.0));
        }

        @Test
        @DisplayName("Should construct with custom L-R coefficients")
        void shouldConstructCustom() {
            ErosiveBurningModel m = new ErosiveBurningModel(propellant, 0.05, 60.0, 200.0);
            assertThat(m.getAlpha()).isCloseTo(0.05, within(1e-6));
            assertThat(m.getBeta()).isCloseTo(60.0, within(1e-3));
            assertThat(m.getThresholdFlux()).isCloseTo(200.0, within(0.1));
        }

        @Test
        @DisplayName("Should reject null propellant")
        void shouldRejectNullPropellant() {
            assertThatThrownBy(() -> new ErosiveBurningModel(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should reject non-positive alpha")
        void shouldRejectNonPositiveAlpha() {
            assertThatThrownBy(() -> new ErosiveBurningModel(propellant, 0.0, 53.0, 150.0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should reject non-positive beta")
        void shouldRejectNonPositiveBeta() {
            assertThatThrownBy(() -> new ErosiveBurningModel(propellant, 0.04, 0.0, 150.0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("augmentedBurnRate")
    class AugmentedBurnRateTests {

        @Test
        @DisplayName("Below threshold G should return base burn rate unchanged")
        void belowThresholdNoErosion() {
            double r0 = 0.005;
            double G  = 100.0;  // below 150 kg/m²/s threshold

            double rTotal = model.augmentedBurnRate(r0, G);

            assertThat(rTotal).isCloseTo(r0, within(1e-10));
        }

        @Test
        @DisplayName("Above threshold G should increase total burn rate")
        void aboveThresholdAugmented() {
            double r0    = 0.005;
            double G     = 500.0;  // well above threshold

            double rTotal = model.augmentedBurnRate(r0, G);

            assertThat(rTotal).isGreaterThan(r0);
        }

        @Test
        @DisplayName("Total burn rate should always be >= base rate")
        void augmentedAlwaysGeBase() {
            double r0 = 0.005;
            for (double G : new double[]{0, 50, 150, 300, 600, 1500}) {
                double rTotal = model.augmentedBurnRate(r0, G);
                assertThat(rTotal)
                        .as("r_total >= r_0 at G=%.0f", G)
                        .isGreaterThanOrEqualTo(r0 - 1e-12);
            }
        }

        @Test
        @DisplayName("Higher G should produce higher total burn rate (below blocking saturation)")
        void higherGHigherBurnRate() {
            double r0    = 0.005;
            double rLow  = model.augmentedBurnRate(r0, 300.0);
            double rHigh = model.augmentedBurnRate(r0, 800.0);

            assertThat(rHigh).isGreaterThan(rLow);
        }

        @ParameterizedTest(name = "r0={0} m/s, G={1} kg/m²/s")
        @CsvSource({"0.004, 200", "0.006, 400", "0.010, 600", "0.015, 800"})
        @DisplayName("Should produce finite positive result for valid inputs")
        void finitePositiveForValidInputs(double r0, double G) {
            double rTotal = model.augmentedBurnRate(r0, G);
            assertThat(rTotal).isFinite().isGreaterThan(0.0);
        }
    }

    @Nested
    @DisplayName("erosiveBurnRate")
    class ErosiveBurnRateTests {

        @Test
        @DisplayName("Should return zero below G threshold")
        void zeroErosionBelowThreshold() {
            assertThat(model.erosiveBurnRate(0.005, 100.0)).isZero();
        }

        @Test
        @DisplayName("Should return zero for zero r0")
        void zeroErosionForZeroR0() {
            assertThat(model.erosiveBurnRate(0.0, 500.0)).isZero();
        }

        @Test
        @DisplayName("Should return zero for zero G")
        void zeroErosionForZeroG() {
            assertThat(model.erosiveBurnRate(0.005, 0.0)).isZero();
        }

        @Test
        @DisplayName("Erosive rate should be non-negative")
        void erosiveRateNonNegative() {
            for (double G : new double[]{0, 100, 200, 500, 1000}) {
                assertThat(model.erosiveBurnRate(0.005, G))
                        .as("r_e >= 0 at G=%.0f", G)
                        .isGreaterThanOrEqualTo(0.0);
            }
        }
    }

    @Nested
    @DisplayName("erosiveFraction")
    class ErosiveFractionTests {

        @Test
        @DisplayName("Erosive fraction should be zero below threshold")
        void fractionZeroBelowThreshold() {
            assertThat(model.erosiveFraction(0.005, 50.0)).isZero();
        }

        @Test
        @DisplayName("Erosive fraction should be non-negative above threshold")
        void fractionNonNegative() {
            double fraction = model.erosiveFraction(0.005, 500.0);
            assertThat(fraction).isGreaterThanOrEqualTo(0.0);
        }

        @Test
        @DisplayName("Erosive fraction = re / r0")
        void fractionEqualsRatioOfRates() {
            double r0 = 0.005;
            double G  = 400.0;
            double re = model.erosiveBurnRate(r0, G);
            assertThat(model.erosiveFraction(r0, G)).isCloseTo(re / r0, within(1e-10));
        }
    }

    @Nested
    @DisplayName("coreFlux")
    class CoreFluxTests {

        @Test
        @DisplayName("Core flux should increase linearly with axial position")
        void coreFluxLinearWithZ() {
            double r0 = 0.005;
            double ri = 0.025;

            double G1 = model.coreFlux(r0, ri, 0.1);
            double G2 = model.coreFlux(r0, ri, 0.2);

            assertThat(G2).isCloseTo(2.0 * G1, within(1e-8));
        }

        @Test
        @DisplayName("Core flux at z=0 should be zero")
        void coreFluxZeroAtHead() {
            assertThat(model.coreFlux(0.005, 0.025, 0.0)).isZero();
        }

        @Test
        @DisplayName("Core flux should be zero for zero inner radius")
        void coreFluxZeroForZeroRadius() {
            assertThat(model.coreFlux(0.005, 0.0, 0.1)).isZero();
        }

        @Test
        @DisplayName("Core flux = 2 × ρ_p × r_0 × z / r_i")
        void coreFluxFormula() {
            double r0 = 0.005;
            double ri = 0.025;
            double z  = 0.15;

            double expected = 2.0 * propellant.density() * r0 * z / ri;
            assertThat(model.coreFlux(r0, ri, z)).isCloseTo(expected, within(1e-8));
        }
    }

    @Nested
    @DisplayName("axiallyAveragedErosion")
    class AxialAveragingTests {

        @Test
        @DisplayName("Averaged erosion should be non-negative")
        void averagedErosionNonNegative() {
            double avg = model.axiallyAveragedErosion(0.005, 0.025, 0.3);
            assertThat(avg).isGreaterThanOrEqualTo(0.0);
        }

        @Test
        @DisplayName("Longer grain should produce larger average erosion (more head-end burning)")
        void longerGrainMoreErosion() {
            double r0 = 0.005;
            double ri = 0.025;

            double avgShort = model.axiallyAveragedErosion(r0, ri, 0.1);
            double avgLong  = model.axiallyAveragedErosion(r0, ri, 0.5);

            assertThat(avgLong).isGreaterThanOrEqualTo(avgShort);
        }

        @ParameterizedTest(name = "grain length {0} m")
        @ValueSource(doubles = {0.05, 0.10, 0.20, 0.40, 0.80})
        @DisplayName("Averaged erosion should be finite for any grain length")
        void averagedErosionFinite(double L) {
            double avg = model.axiallyAveragedErosion(0.005, 0.025, L);
            assertThat(avg).isFinite().isGreaterThanOrEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("Accessors")
    class AccessorTests {

        @Test
        @DisplayName("getPropellant() should return the propellant")
        void getPropellantReturnsCorrectPropellant() {
            assertThat(model.getPropellant()).isEqualTo(propellant);
        }

        @Test
        @DisplayName("Default alpha, beta, threshold match class constants")
        void defaultConstantsMatchFields() {
            assertThat(model.getAlpha()).isEqualTo(ErosiveBurningModel.DEFAULT_ALPHA);
            assertThat(model.getBeta()).isEqualTo(ErosiveBurningModel.DEFAULT_BETA);
            assertThat(model.getThresholdFlux()).isEqualTo(ErosiveBurningModel.DEFAULT_THRESHOLD);
        }
    }
}
