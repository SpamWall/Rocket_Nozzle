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

import com.nozzle.core.GasProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SolidPropellant Tests")
class SolidPropellant_UT {

    // -----------------------------------------------------------------------
    // Construction validation
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {

        @Test
        @DisplayName("Valid parameters construct without exception")
        void validParametersConstruct() {
            assertThatCode(() -> new SolidPropellant(
                    1750, 4e-5, 0.35, 0.002, 294, 3200, 1590, GasProperties.APCP_HTPB_PRODUCTS))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("density <= 0 throws IllegalArgumentException")
        void nonPositiveDensityThrows() {
            assertThatThrownBy(() -> new SolidPropellant(
                    0, 4e-5, 0.35, 0.002, 294, 3200, 1590, GasProperties.APCP_HTPB_PRODUCTS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("density");
        }

        @Test
        @DisplayName("burnRateExponent >= 1 throws (unstable combustion)")
        void exponentGeOneThrows() {
            assertThatThrownBy(() -> new SolidPropellant(
                    1750, 4e-5, 1.0, 0.002, 294, 3200, 1590, GasProperties.APCP_HTPB_PRODUCTS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("stable combustion");
        }

        @Test
        @DisplayName("burnRateExponent <= 0 throws")
        void exponentLeZeroThrows() {
            assertThatThrownBy(() -> new SolidPropellant(
                    1750, 4e-5, 0.0, 0.002, 294, 3200, 1590, GasProperties.APCP_HTPB_PRODUCTS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("stable combustion");
        }

        @Test
        @DisplayName("negative temperatureSensitivity throws")
        void negativeSigmaThrows() {
            assertThatThrownBy(() -> new SolidPropellant(
                    1750, 4e-5, 0.35, -0.001, 294, 3200, 1590, GasProperties.APCP_HTPB_PRODUCTS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sensitivity");
        }

        @Test
        @DisplayName("null combustionProducts throws IllegalArgumentException")
        void nullGasPropertiesThrows() {
            assertThatThrownBy(() -> new SolidPropellant(
                    1750, 4e-5, 0.35, 0.002, 294, 3200, 1590, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // -----------------------------------------------------------------------
    // Factory propellants existence and physical plausibility
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Factory Propellant Tests")
    class FactoryTests {

        @Test
        @DisplayName("APCP_HTPB() creates a non-null propellant with positive density")
        void apcpHtpbCreated() {
            SolidPropellant p = SolidPropellant.APCP_HTPB();
            assertThat(p).isNotNull();
            assertThat(p.density()).isGreaterThan(0);
            assertThat(p.chamberTemperature()).isGreaterThan(2000);
            assertThat(p.characteristicVelocity()).isGreaterThan(1000);
        }

        @Test
        @DisplayName("APCP_PBAN() creates a non-null propellant")
        void apcpPbanCreated() {
            assertThat(SolidPropellant.APCP_PBAN()).isNotNull();
        }

        @Test
        @DisplayName("KNSU() creates a non-null propellant with lower c* than APCP")
        void knsuCreated() {
            SolidPropellant knsu = SolidPropellant.KNSU();
            SolidPropellant apcp = SolidPropellant.APCP_HTPB();
            assertThat(knsu).isNotNull();
            assertThat(knsu.characteristicVelocity()).isLessThan(apcp.characteristicVelocity());
        }

        @Test
        @DisplayName("KNDX() creates a non-null propellant")
        void kndxCreated() {
            assertThat(SolidPropellant.KNDX()).isNotNull();
        }

        @Test
        @DisplayName("DOUBLE_BASE() creates a non-null propellant with n < 1")
        void doubleBaseCreated() {
            SolidPropellant db = SolidPropellant.DOUBLE_BASE();
            assertThat(db).isNotNull();
            assertThat(db.burnRateExponent()).isLessThan(1.0);
        }

        @Test
        @DisplayName("All factory propellants have burn-rate exponent in (0, 1)")
        void allExponentsInRange() {
            SolidPropellant[] all = {
                SolidPropellant.APCP_HTPB(), SolidPropellant.APCP_PBAN(),
                SolidPropellant.KNSU(),      SolidPropellant.KNDX(),
                SolidPropellant.DOUBLE_BASE()
            };
            for (SolidPropellant p : all) {
                assertThat(p.burnRateExponent())
                        .as("exponent for " + p.combustionProducts())
                        .isGreaterThan(0.0)
                        .isLessThan(1.0);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Burn-rate law
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Burn Rate Tests")
    class BurnRateTests {

        @Test
        @DisplayName("APCP_HTPB burnRate(7 MPa) ≈ 10 mm/s at reference temperature")
        void apcpHtpbBurnRateAtReferenceConditions() {
            SolidPropellant p = SolidPropellant.APCP_HTPB();
            double r = p.burnRate(7.0e6);
            assertThat(r).isCloseTo(0.010, within(5e-5));  // ±0.05 mm/s tolerance
        }

        @Test
        @DisplayName("KNSU burnRate(1 MPa) ≈ 13 mm/s at reference temperature")
        void knsuBurnRateAtReferenceConditions() {
            SolidPropellant p = SolidPropellant.KNSU();
            double r = p.burnRate(1.0e6);
            assertThat(r).isCloseTo(0.013, within(5e-5));
        }

        @Test
        @DisplayName("KNDX burnRate(1 MPa) ≈ 7.5 mm/s at reference temperature")
        void kndxBurnRateAtReferenceConditions() {
            SolidPropellant p = SolidPropellant.KNDX();
            double r = p.burnRate(1.0e6);
            assertThat(r).isCloseTo(0.0075, within(5e-5));
        }

        @Test
        @DisplayName("burnRate increases monotonically with pressure (n > 0)")
        void burnRateMonotonicallyIncreasing() {
            SolidPropellant p = SolidPropellant.APCP_HTPB();
            double rPrev = p.burnRate(1.0e5);
            for (double pressure : new double[]{5e5, 1e6, 3e6, 7e6, 12e6}) {
                double r = p.burnRate(pressure);
                assertThat(r).isGreaterThan(rPrev);
                rPrev = r;
            }
        }

        @Test
        @DisplayName("burnRate(P, T_ref) equals burnRate(P) (no temperature offset)")
        void burnRateAtRefTempMatchesSingleArg() {
            SolidPropellant p = SolidPropellant.APCP_HTPB();
            double pc = 7.0e6;
            assertThat(p.burnRate(pc, p.referenceTemperature()))
                    .isEqualTo(p.burnRate(pc));
        }

        @Test
        @DisplayName("Temperature sensitivity: higher T → higher burn rate")
        void higherTemperatureIncrasesBurnRate() {
            SolidPropellant p = SolidPropellant.APCP_HTPB();
            double pc = 7.0e6;
            double rCold = p.burnRate(pc, 233.0);  // -40 °C
            double rWarm = p.burnRate(pc, 344.0);  // +71 °C
            assertThat(rWarm).isGreaterThan(rCold);
        }

        @ParameterizedTest(name = "pressure = {0} Pa")
        @ValueSource(doubles = {1e5, 1e6, 5e6, 1e7})
        @DisplayName("burnRate(P) is positive for all positive pressures")
        void burnRateIsPositive(double pressure) {
            assertThat(SolidPropellant.APCP_HTPB().burnRate(pressure)).isGreaterThan(0.0);
        }
    }

    // -----------------------------------------------------------------------
    // Equilibrium pressure
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Equilibrium Pressure Tests")
    class EquilibriumPressureTests {

        @Test
        @DisplayName("Equilibrium pressure is positive for positive Ab and At")
        void equilibriumPressureIsPositive() {
            SolidPropellant p = SolidPropellant.APCP_HTPB();
            assertThat(p.equilibriumPressure(0.01, 1e-4)).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("Higher Kn (larger Ab) produces higher equilibrium pressure")
        void higherKnHigherPressure() {
            SolidPropellant p = SolidPropellant.APCP_HTPB();
            double at = 1e-4;
            double p1 = p.equilibriumPressure(0.010, at);
            double p2 = p.equilibriumPressure(0.020, at);
            assertThat(p2).isGreaterThan(p1);
        }

        @Test
        @DisplayName("Klemmung = burningArea / throatArea")
        void klemmungFormula() {
            SolidPropellant p = SolidPropellant.APCP_HTPB();
            assertThat(p.klemmung(0.05, 0.001)).isCloseTo(50.0, within(1e-10));
        }

        @Test
        @DisplayName("Temperature correction: cold propellant gives lower equilibrium pressure")
        void colderPropellantLowerPressure() {
            SolidPropellant p = SolidPropellant.APCP_HTPB();
            double ab = 0.02, at = 1e-4;
            double pCold = p.equilibriumPressure(ab, at, 233.0);
            double pWarm = p.equilibriumPressure(ab, at, 344.0);
            assertThat(pWarm).isGreaterThan(pCold);
        }

        @Test
        @DisplayName("equilibriumPressure at referenceTemperature matches single-arg overload")
        void equilibriumPressureRefTempMatchesSingleArg() {
            SolidPropellant p = SolidPropellant.APCP_HTPB();
            double ab = 0.01, at = 5e-5;
            assertThat(p.equilibriumPressure(ab, at, p.referenceTemperature()))
                    .isCloseTo(p.equilibriumPressure(ab, at), within(1e-6));
        }
    }
}
