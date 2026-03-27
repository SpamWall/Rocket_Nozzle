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

package com.nozzle.chemistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SpeciesData Tests")
class SpeciesData_UT {

    // H2O NASA-7 coefficients used throughout (NASA TP-2002-211556)
    private static final double[] H2O_LOW  = {
        4.198640560e+00, -2.036434100e-03,  6.520402110e-06,
       -5.487970620e-09,  1.771978170e-12, -3.029372670e+04, -8.490322080e-01
    };
    private static final double[] H2O_HIGH = {
        3.033992490e+00,  2.176918040e-03, -1.640725180e-07,
       -9.704198700e-11,  1.682009920e-14, -3.000429710e+04,  4.966770100e+00
    };

    private SpeciesData h2o;

    @BeforeEach
    void setUp() {
        h2o = new SpeciesData("H2O", 18.015, H2O_LOW, H2O_HIGH);
    }

    @Nested
    @DisplayName("High-temperature range (T >= 1000 K)")
    class HighTemperatureTests {

        @Test
        @DisplayName("cp should be positive")
        void cpShouldBePositive() {
            assertThat(h2o.cp(2000)).isGreaterThan(1000);
        }

        @Test
        @DisplayName("enthalpy should be finite")
        void enthalpyShouldBeFinite() {
            assertThat(h2o.enthalpy(2000)).isNotNaN();
        }

        @Test
        @DisplayName("entropy should be within expected range")
        void entropyShouldBeInExpectedRange() {
            double s = h2o.entropy(2000);
            assertThat(s).isNotNaN();
            assertThat(s).isFinite();
            // H2O standard molar entropy at 2000 K ≈ 264 J/(mol·K) → ~14 650 J/(kg·K)
            assertThat(s).isBetween(12_000.0, 17_000.0);
        }

        @Test
        @DisplayName("entropy should increase with temperature")
        void entropyShouldIncreaseWithTemperature() {
            assertThat(h2o.entropy(2000)).isGreaterThan(h2o.entropy(500));
        }

        @Test
        @DisplayName("gibbsOverRT should be negative for stable species at high temperature")
        void gibbsOverRTShouldBeNegative() {
            double g = h2o.gibbsOverRT(3000);
            assertThat(g).isNotNaN();
            assertThat(g).isFinite();
            assertThat(g).isNegative();
        }
    }

    @Nested
    @DisplayName("Low-temperature range (T < 1000 K)")
    class LowTemperatureTests {

        @Test
        @DisplayName("cp uses low-temperature coefficients and produces a different value than at 2000 K")
        void cpUsesLowTemperatureCoefficients() {
            double cpLow = h2o.cp(500);
            assertThat(cpLow).isGreaterThan(1000);
            assertThat(cpLow).isNotEqualTo(h2o.cp(2000));
        }

        @Test
        @DisplayName("enthalpy uses low-temperature coefficients and is finite")
        void enthalpyUsesLowTemperatureCoefficients() {
            double hLow = h2o.enthalpy(500);
            assertThat(hLow).isNotNaN();
            assertThat(hLow).isFinite();
            // Enthalpy increases with temperature for H2O
            assertThat(h2o.enthalpy(2000)).isGreaterThan(hLow);
        }

        @Test
        @DisplayName("gibbsOverRT uses low-temperature coefficients and is negative for H2O")
        void gibbsOverRTUsesLowTemperatureCoefficients() {
            double gLow = h2o.gibbsOverRT(500);
            assertThat(gLow).isNotNaN();
            assertThat(gLow).isFinite();
            assertThat(gLow).isNegative();
        }
    }
}
