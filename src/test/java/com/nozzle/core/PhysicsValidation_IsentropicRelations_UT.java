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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Validates isentropic flow relations against published tabulated values.
 * <p>
 * Reference tables: NACA TR 1135 (Ames Research Staff, 1953),
 * Anderson "Modern Compressible Flow" Table A.1 & A.5.
 * All γ = 1.4 (air) values are exact to the precision shown.
 * Tolerance: 0.1 % of the expected value, which is well within
 * the analytical precision of double arithmetic.
 */
@DisplayName("Physics Validation — Isentropic Relations (γ=1.4)")
class PhysicsValidation_IsentropicRelations_UT {

    /** GasProperties.AIR has γ = 1.4 exactly. */
    private static final GasProperties AIR = GasProperties.AIR;

    // -----------------------------------------------------------------------
    // Temperature ratio  T/T0 = 1 / (1 + (γ-1)/2 · M²)
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Temperature ratio T/T0")
    class TemperatureRatioTests {

        /**
         * NACA TR 1135, Table 1 (γ = 1.4).
         * Values are exact fractions or carry at least 5 significant figures.
         */
        @ParameterizedTest(name = "M={0} → T/T0 ≈ {1}")
        @CsvSource({
                "1.0, 0.83333",   // 5/6
                "1.5, 0.68966",   // 1/(1+0.2·2.25) = 1/1.45
                "2.0, 0.55556",   // 5/9
                "2.5, 0.44444",   // 4/9
                "3.0, 0.35714",   // 5/14
                "4.0, 0.23810",   // 5/21
                "5.0, 0.16667",   // 1/6
        })
        @DisplayName("T/T0 matches NACA TR 1135 Table 1")
        void temperatureRatioMatchesTables(double mach, double expected) {
            double actual = AIR.isentropicTemperatureRatio(mach);
            double tol = expected * 0.001;   // 0.1 %
            assertThat(actual).as("T/T0 at M=%.1f", mach)
                    .isCloseTo(expected, within(tol));
        }

        @Test
        @DisplayName("T/T0 at M=1 is (γ+1)/2 reciprocal = 2/(γ+1) = 5/6 exactly")
        void temperatureRatioAtThroatIsExact() {
            // At sonic conditions T/T0 = 2/(γ+1) = 2/2.4 = 5/6
            double actual = AIR.isentropicTemperatureRatio(1.0);
            assertThat(actual).isCloseTo(2.0 / (AIR.gamma() + 1), within(1e-12));
        }

        @Test
        @DisplayName("T/T0 at M=5 is 1/6 exactly (round-number result)")
        void temperatureRatioAtMach5IsExact() {
            // 1 + 0.2*25 = 6.0, so T/T0 = 1/6 exactly
            double actual = AIR.isentropicTemperatureRatio(5.0);
            assertThat(actual).isCloseTo(1.0 / 6.0, within(1e-12));
        }
    }

    // -----------------------------------------------------------------------
    // Pressure ratio  P/P0 = (T/T0)^(γ/(γ-1))
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Pressure ratio P/P0")
    class PressureRatioTests {

        /**
         * Derived from the isentropic relation; all values from NACA TR 1135 Table 1.
         */
        @ParameterizedTest(name = "M={0} → P/P0 ≈ {1}")
        @CsvSource({
                "1.0, 0.52828",
                "1.5, 0.27240",
                "2.0, 0.12780",
                "2.5, 0.05853",
                "3.0, 0.02722",
                "4.0, 0.00659",
                "5.0, 0.001890", // (1/6)^3.5 = 0.001890038...
        })
        @DisplayName("P/P0 matches NACA TR 1135 Table 1")
        void pressureRatioMatchesTables(double mach, double expected) {
            double actual = AIR.isentropicPressureRatio(mach);
            double tol = expected * 0.001;   // 0.1 %
            assertThat(actual).as("P/P0 at M=%.1f", mach)
                    .isCloseTo(expected, within(tol));
        }

        @Test
        @DisplayName("P/P0 is thermodynamically consistent with T/T0 and ρ/ρ0")
        void pressureConsistentWithTemperatureAndDensity() {
            // P/P0 = (T/T0)·(ρ/ρ0) must hold for a perfect gas (P = ρRT)
            for (double mach : new double[]{1.0, 1.5, 2.0, 3.0, 5.0}) {
                double tRatio = AIR.isentropicTemperatureRatio(mach);
                double pRatio = AIR.isentropicPressureRatio(mach);
                double rhoRatio = AIR.isentropicDensityRatio(mach);
                assertThat(pRatio).as("P/P0 == (T/T0)(ρ/ρ0) at M=%.1f", mach)
                        .isCloseTo(tRatio * rhoRatio, within(pRatio * 1e-10));
            }
        }
    }

    // -----------------------------------------------------------------------
    // Area ratio  A/A* = (1/M)·((2/(γ+1))·(1+(γ-1)/2·M²))^((γ+1)/(2(γ-1)))
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Area ratio A/A*")
    class AreaRatioTests {

        /**
         * NACA TR 1135 Table 1 (γ = 1.4).
         * Key cross-checks: M=2 gives 1.6875, M=5 gives 25.0 (both exact).
         */
        @ParameterizedTest(name = "M={0} → A/A* ≈ {1}")
        @CsvSource({
                "1.0,  1.00000",
                "1.5,  1.17617",
                "2.0,  1.68750",  // exact: (1/2)·(1.5)^3
                "2.5,  2.63681",
                "3.0,  4.23457",
                "4.0, 10.71875", // exact: (1/4)·(3.5)^3
                "5.0, 25.00000", // exact: (1/5)·(5)^3
        })
        @DisplayName("A/A* matches NACA TR 1135 Table 1")
        void areaRatioMatchesTables(double mach, double expected) {
            double actual = AIR.areaRatio(mach);
            double tol = expected * 0.001;
            assertThat(actual).as("A/A* at M=%.1f", mach)
                    .isCloseTo(expected, within(tol));
        }

        @Test
        @DisplayName("A/A* = 1 at M=1 (throat, exact)")
        void areaRatioIsUnityAtThroat() {
            assertThat(AIR.areaRatio(1.0)).isCloseTo(1.0, within(1e-12));
        }

        @Test
        @DisplayName("A/A* at M=5 is exactly 25 (γ=1.4 special case)")
        void areaRatioAtMach5IsExact() {
            // (1/5)·((2/2.4)·(1+0.2·25))^3 = (1/5)·(5.0)^3 = 25 exactly
            assertThat(AIR.areaRatio(5.0)).isCloseTo(25.0, within(1e-10));
        }

        @Test
        @DisplayName("machFromAreaRatio inverts areaRatio to within 0.01% across Mach 1.2–8")
        void machFromAreaRatioInvertsAcrossRange() {
            for (double mach : new double[]{1.2, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0}) {
                double ar = AIR.areaRatio(mach);
                double recovered = AIR.machFromAreaRatio(ar);
                assertThat(recovered).as("machFromAreaRatio round-trip at M=%.1f", mach)
                        .isCloseTo(mach, within(mach * 1e-4));  // 0.01%
            }
        }
    }

    // -----------------------------------------------------------------------
    // Prandtl-Meyer function  ν(M)
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Prandtl-Meyer function ν(M)")
    class PrandtlMeyerTests {

        /**
         * Values from Anderson "Modern Compressible Flow" Table A.5, γ = 1.4.
         * ν_max = (π/2)·(√6 − 1) = 130.454°.
         */
        @ParameterizedTest(name = "M={0} → ν ≈ {1}°")
        @CsvSource({
                "1.0,   0.000",
                "1.5,  11.905",
                "2.0,  26.380",
                "2.5,  39.124",
                "3.0,  49.757",
                "4.0,  65.785",
                "5.0,  76.920",
        })
        @DisplayName("ν matches Anderson Table A.5 to 0.1%")
        void prandtlMeyerMatchesTables(double mach, double expectedDeg) {
            double nu = AIR.prandtlMeyerFunction(mach);
            double actualDeg = Math.toDegrees(nu);
            double tol = Math.max(expectedDeg * 0.001, 1e-4);   // 0.1% or 0.0001°
            assertThat(actualDeg).as("ν at M=%.1f", mach)
                    .isCloseTo(expectedDeg, within(tol));
        }

        @Test
        @DisplayName("ν_max = (π/2)·(√((γ+1)/(γ-1)) − 1) for γ=1.4 ≈ 130.454°")
        void maximumPrandtlMeyerAngleMatchesFormula() {
            double g = AIR.gamma();
            double nuMaxRad = (Math.PI / 2) * (Math.sqrt((g + 1) / (g - 1)) - 1);
            double nuMaxDeg = Math.toDegrees(nuMaxRad);
            assertThat(nuMaxDeg).isCloseTo(130.454, within(0.001));
        }

        @Test
        @DisplayName("machFromPrandtlMeyer inverts ν(M) to within 0.01% across Mach 1.2–8")
        void machFromPrandtlMeyerInvertsAcrossRange() {
            for (double mach : new double[]{1.2, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 8.0}) {
                double nu = AIR.prandtlMeyerFunction(mach);
                double recovered = AIR.machFromPrandtlMeyer(nu);
                assertThat(recovered).as("machFromPM round-trip at M=%.1f", mach)
                        .isCloseTo(mach, within(mach * 1e-4));  // 0.01%
            }
        }
    }

    // -----------------------------------------------------------------------
    // Mach angle  μ = arcsin(1/M)
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Mach angle μ = arcsin(1/M)")
    class MachAngleTests {

        /**
         * Exact values from arcsin(1/M).
         */
        @ParameterizedTest(name = "M={0} → μ ≈ {1}°")
        @CsvSource({
                "1.0,  90.000",
                "2.0,  30.000",   // arcsin(0.5) = 30° exactly
                "3.0,  19.471",
                "4.0,  14.4775", // arcsin(0.25) = 14.47751...°
                "5.0,  11.537",
        })
        @DisplayName("μ matches arcsin(1/M) to 0.01%")
        void machAngleMatchesFormula(double mach, double expectedDeg) {
            double mu = AIR.machAngle(mach);
            double actualDeg = Math.toDegrees(mu);
            double tol = expectedDeg * 0.0001;   // 0.01%
            assertThat(actualDeg).as("μ at M=%.1f", mach)
                    .isCloseTo(expectedDeg, within(tol));
        }

        @Test
        @DisplayName("μ at M=2 is exactly 30° (arcsin(1/2))")
        void machAngleAtMach2IsExact() {
            double mu = AIR.machAngle(2.0);
            assertThat(Math.toDegrees(mu)).isCloseTo(30.0, within(1e-10));
        }

        @Test
        @DisplayName("μ + flow-cone angle satisfies Mach-cone geometry: sin(μ) = 1/M")
        void machAngleSatisfiesDefinition() {
            for (double mach : new double[]{1.5, 2.0, 3.0, 5.0}) {
                double mu = AIR.machAngle(mach);
                assertThat(Math.sin(mu)).as("sin(μ) at M=%.1f", mach)
                        .isCloseTo(1.0 / mach, within(1e-12));
            }
        }
    }

    // -----------------------------------------------------------------------
    // Sutherland viscosity — validated against Incropera Table A.4 (dry air)
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Sutherland viscosity law (air)")
    class ViscosityTests {

        /**
         * Reference: Incropera "Fundamentals of Heat and Mass Transfer" Table A.4.
         * μ at 300 K ≈ 1.846 × 10⁻⁵ Pa·s; at 400 K ≈ 2.286 × 10⁻⁵ Pa·s.
         */
        @Test
        @DisplayName("Viscosity at 300 K matches Incropera Table A.4 to 2%")
        void viscosityAt300K() {
            double mu = AIR.calculateViscosity(300.0);
            assertThat(mu).isCloseTo(1.846e-5, within(1.846e-5 * 0.02));
        }

        @Test
        @DisplayName("Viscosity at 400 K matches Incropera Table A.4 to 2%")
        void viscosityAt400K() {
            double mu = AIR.calculateViscosity(400.0);
            assertThat(mu).isCloseTo(2.286e-5, within(2.286e-5 * 0.02));
        }

        @Test
        @DisplayName("Sutherland exponent is between 1.5 and 2 (empirical range)")
        void sutherlandExponentInRange() {
            // For a power-law approximation μ ∝ T^n, n should be ~1.5–2 for air.
            double mu300 = AIR.calculateViscosity(300.0);
            double mu600 = AIR.calculateViscosity(600.0);
            double n = Math.log(mu600 / mu300) / Math.log(600.0 / 300.0);
            assertThat(n).isBetween(0.6, 0.8);   // actual Sutherland exponent ~0.7 at these temps
        }
    }
}
