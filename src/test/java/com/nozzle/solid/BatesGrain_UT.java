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
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("BatesGrain Tests")
class BatesGrain_UT {

    // Grain: Do=100mm, Dp=50mm, L=75mm, N=1
    // Web = (100-50)/2 = 25 mm
    private static final double DO = 0.100;
    private static final double DP = 0.050;
    private static final double L  = 0.075;

    private BatesGrain grain;

    @BeforeEach
    void setUp() {
        grain = new BatesGrain(DO, DP, L, 1);
    }

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {

        @Test
        @DisplayName("Valid parameters construct without exception")
        void validParametersConstruct() {
            assertThatCode(() -> new BatesGrain(0.10, 0.05, 0.08, 2))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("outerDiameter <= 0 throws IllegalArgumentException")
        void nonPositiveOuterDiameterThrows() {
            assertThatThrownBy(() -> new BatesGrain(0.0, 0.05, 0.08, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Outer diameter");
        }

        @Test
        @DisplayName("portDiameter >= outerDiameter throws IllegalArgumentException")
        void portDiameterEqualToOuterThrows() {
            assertThatThrownBy(() -> new BatesGrain(0.10, 0.10, 0.08, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Port diameter");
        }

        @Test
        @DisplayName("portDiameter <= 0 throws IllegalArgumentException")
        void nonPositivePortDiameterThrows() {
            assertThatThrownBy(() -> new BatesGrain(0.10, 0.0, 0.08, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Port diameter");
        }

        @Test
        @DisplayName("segmentLength <= 0 throws IllegalArgumentException")
        void nonPositiveSegmentLengthThrows() {
            assertThatThrownBy(() -> new BatesGrain(0.10, 0.05, 0.0, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Segment length");
        }

        @Test
        @DisplayName("numSegments < 1 throws IllegalArgumentException")
        void zeroSegmentsThrows() {
            assertThatThrownBy(() -> new BatesGrain(0.10, 0.05, 0.08, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("segments");
        }
    }

    // -----------------------------------------------------------------------
    // Geometry accessors
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Geometry Accessor Tests")
    class GeometryAccessorTests {

        @Test
        @DisplayName("webThickness() = min((Do-Dp)/2, L/2) — radial web for this grain")
        void webThickness() {
            // (Do-Dp)/2 = 0.025, L/2 = 0.0375 → radial burnout first
            assertThat(grain.webThickness()).isCloseTo(0.025, within(1e-12));
        }

        @Test
        @DisplayName("propellantVolume() = N * pi/4 * (Do^2 - Dp^2) * L")
        void propellantVolume() {
            double expected = Math.PI / 4.0 * (DO * DO - DP * DP) * L;
            assertThat(grain.propellantVolume()).isCloseTo(expected, within(1e-12));
        }

        @Test
        @DisplayName("name() contains segment count, Do, Dp, L")
        void nameContainsKeyInfo() {
            String n = grain.name();
            assertThat(n).contains("1").contains("100").contains("50").contains("75");
        }

        @Test
        @DisplayName("neutralSegmentLength() = (3*Do + Dp) / 2")
        void neutralSegmentLength() {
            // (3*0.10 + 0.05) / 2 = 0.175 m
            assertThat(grain.neutralSegmentLength()).isCloseTo(0.175, within(1e-12));
        }

        @Test
        @DisplayName("Accessors return constructed values")
        void accessorsReturnConstructedValues() {
            assertThat(grain.outerDiameter()).isEqualTo(DO);
            assertThat(grain.portDiameter()).isEqualTo(DP);
            assertThat(grain.segmentLength()).isEqualTo(L);
            assertThat(grain.numSegments()).isEqualTo(1);
        }
    }

    // -----------------------------------------------------------------------
    // Burning area formula
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Burning Area Tests")
    class BurningAreaTests {

        @Test
        @DisplayName("burningArea(0) matches analytical formula at y=0")
        void burningAreaAtZero() {
            // A_b(0) = pi*Dp*L + pi/2*(Do^2-Dp^2)  [L_eff at y=0 is full L]
            double expected = Math.PI * DP * L
                              + 0.5 * Math.PI * (DO * DO - DP * DP);
            assertThat(grain.burningArea(0.0)).isCloseTo(expected, within(1e-10));
        }

        @Test
        @DisplayName("burningArea(webThickness) = N*pi*Do*(L-Do+Dp) (end faces and bore meet)")
        void burningAreaAtBurnout() {
            // At radial burnout (y=w): bore=Do, L_eff=L-2w=L-(Do-Dp); end faces gone
            double w           = grain.webThickness();  // 0.025
            double effectiveL  = L - 2.0 * w;           // 0.075 - 0.05 = 0.025
            double expected    = Math.PI * DO * effectiveL;
            assertThat(grain.burningArea(w)).isCloseTo(expected, within(1e-10));
        }

        @Test
        @DisplayName("burningArea() is positive throughout the burn")
        void burningAreaPositiveThroughout() {
            double w = grain.webThickness();
            for (int i = 0; i <= 20; i++) {
                double y = w * i / 20.0;
                assertThat(grain.burningArea(y)).isGreaterThan(0.0);
            }
        }

        @Test
        @DisplayName("N=2 grain has exactly twice the burning area of N=1")
        void multiSegmentScalesWithN() {
            BatesGrain g2 = new BatesGrain(DO, DP, L, 2);
            for (int i = 0; i <= 10; i++) {
                double y = grain.webThickness() * i / 10.0;
                assertThat(g2.burningArea(y)).isCloseTo(2.0 * grain.burningArea(y), within(1e-10));
            }
        }

        @Test
        @DisplayName("Neutral condition: burningArea(0) == burningArea(web) when L = neutralSegmentLength")
        void neutralBurnAtNeutralLength() {
            double lNeutral = grain.neutralSegmentLength();  // = (3*Do+Dp)/2 = 0.175 m
            BatesGrain neutral = new BatesGrain(DO, DP, lNeutral, 1);
            assertThat(neutral.burningArea(0.0))
                    .isCloseTo(neutral.burningArea(neutral.webThickness()), within(1e-9));
        }
    }

    // -----------------------------------------------------------------------
    // GrainGeometry interface contract
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("GrainGeometry Interface Tests")
    class InterfaceTests {

        @Test
        @DisplayName("isBurnedOut() false before web, true at web")
        void isBurnedOut() {
            assertThat(grain.isBurnedOut(0.0)).isFalse();
            assertThat(grain.isBurnedOut(grain.webThickness() - 1e-9)).isFalse();
            assertThat(grain.isBurnedOut(grain.webThickness())).isTrue();
            assertThat(grain.isBurnedOut(grain.webThickness() + 1.0)).isTrue();
        }

        @ParameterizedTest(name = "segments = {0}")
        @ValueSource(ints = {1, 2, 4, 8})
        @DisplayName("propellantVolume scales linearly with numSegments")
        void propellantVolumeScalesWithSegments(int n) {
            BatesGrain g = new BatesGrain(DO, DP, L, n);
            assertThat(g.propellantVolume()).isCloseTo(n * grain.propellantVolume(), within(1e-12));
        }
    }
}
