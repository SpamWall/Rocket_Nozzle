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

import static org.assertj.core.api.Assertions.*;

@DisplayName("EndBurningGrain Tests")
class EndBurningGrain_UT {

    private static final double D = 0.075;  // 75 mm diameter
    private static final double L = 0.200;  // 200 mm length

    private EndBurningGrain grain;

    @BeforeEach
    void setUp() {
        grain = new EndBurningGrain(D, L);
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
            assertThatCode(() -> new EndBurningGrain(0.10, 0.30))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("diameter <= 0 throws IllegalArgumentException")
        void nonPositiveDiameterThrows() {
            assertThatThrownBy(() -> new EndBurningGrain(0.0, 0.30))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Diameter");
        }

        @Test
        @DisplayName("length <= 0 throws IllegalArgumentException")
        void nonPositiveLengthThrows() {
            assertThatThrownBy(() -> new EndBurningGrain(0.10, -1.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Length");
        }
    }

    // -----------------------------------------------------------------------
    // Geometry
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Geometry Tests")
    class GeometryTests {

        @Test
        @DisplayName("webThickness() equals grain length")
        void webThicknessEqualsLength() {
            assertThat(grain.webThickness()).isCloseTo(L, within(1e-15));
        }

        @Test
        @DisplayName("propellantVolume() = pi/4 * D^2 * L")
        void propellantVolume() {
            double expected = 0.25 * Math.PI * D * D * L;
            assertThat(grain.propellantVolume()).isCloseTo(expected, within(1e-12));
        }

        @Test
        @DisplayName("Accessors return constructed values")
        void accessorsReturnConstructedValues() {
            assertThat(grain.diameter()).isEqualTo(D);
            assertThat(grain.length()).isEqualTo(L);
        }

        @Test
        @DisplayName("name() contains diameter and length in mm")
        void nameContainsKeyInfo() {
            String n = grain.name();
            assertThat(n).contains("75").contains("200");
        }
    }

    // -----------------------------------------------------------------------
    // Neutral burn character
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Burn Character Tests")
    class BurnCharacterTests {

        @Test
        @DisplayName("burningArea() is constant throughout the burn (perfectly neutral)")
        void burningAreaConstant() {
            double expected = 0.25 * Math.PI * D * D;
            double w = grain.webThickness();
            for (int i = 0; i <= 20; i++) {
                double y = w * i / 20.0;
                assertThat(grain.burningArea(y))
                        .as("At y = %.4f m", y)
                        .isCloseTo(expected, within(1e-15));
            }
        }

        @Test
        @DisplayName("burningArea at zero equals pi/4 * D^2")
        void burningAreaAtZero() {
            assertThat(grain.burningArea(0.0))
                    .isCloseTo(0.25 * Math.PI * D * D, within(1e-12));
        }

        @Test
        @DisplayName("burningArea is independent of web burned")
        void burningAreaIndependentOfWeb() {
            double ab0  = grain.burningArea(0.0);
            double abMid = grain.burningArea(L / 2.0);
            double abEnd = grain.burningArea(L);
            assertThat(abMid).isEqualTo(ab0);
            assertThat(abEnd).isEqualTo(ab0);
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
            assertThat(grain.isBurnedOut(L - 1e-9)).isFalse();
            assertThat(grain.isBurnedOut(L)).isTrue();
        }
    }
}
