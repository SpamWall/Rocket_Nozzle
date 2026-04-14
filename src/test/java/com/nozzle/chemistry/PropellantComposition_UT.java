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

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.within;

@DisplayName("PropellantComposition Tests")
class PropellantComposition_UT {

    private PropellantComposition comp;

    @BeforeEach
    void setUp() {
        comp = new PropellantComposition();
    }

    @Nested
    @DisplayName("Direct assignment")
    class DirectAssignmentTests {

        @Test
        @DisplayName("set() normalises fractions that do not sum to 1")
        void setShouldNormalise() {
            comp.set(Map.of("H2O", 0.5, "CO2", 0.5, "CO", 0.5));
            double sum = comp.get().values().stream().mapToDouble(Double::doubleValue).sum();
            assertThat(sum).isCloseTo(1.0, within(1e-6));
        }

        @Test
        @DisplayName("set() with empty map leaves composition empty")
        void setEmptyMapLeavesCompositionEmpty() {
            comp.set(Map.of());
            assertThat(comp.isEmpty()).isTrue();
            assertThat(comp.get()).isEmpty();
        }

        @Test
        @DisplayName("isEmpty() returns true before any species are added")
        void isEmptyTrueOnFreshInstance() {
            assertThat(comp.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("isEmpty() returns false after set()")
        void isEmptyFalseAfterSet() {
            comp.set(Map.of("H2O", 1.0));
            assertThat(comp.isEmpty()).isFalse();
        }
    }

    @Nested
    @DisplayName("LOX/RP-1 composition presets")
    class LoxRp1Tests {

        @Test
        @DisplayName("Near-stoichiometric (MR 2.0–2.8) contains CO2 and H2O")
        void nearStoichiometricContainsCo2AndH2o() {
            comp.setLoxRp1(2.5);
            Map<String, Double> fractions = comp.get();
            assertThat(fractions).isNotEmpty();
            assertThat(fractions.values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
        }

        @Test
        @DisplayName("Fuel-rich (MR < 2.0) contains CO")
        void fuelRichContainsCo() {
            comp.setLoxRp1(1.5);
            assertThat(comp.get()).containsKey("CO");
            assertThat(comp.get().values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
        }

        @Test
        @DisplayName("Oxidizer-rich (MR >= 2.8) contains O2")
        void oxidizerRichContainsO2() {
            comp.setLoxRp1(3.5);
            assertThat(comp.get()).containsKey("O2");
            assertThat(comp.get().values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
        }
    }

    @Nested
    @DisplayName("LOX/CH4 composition presets")
    class LoxCh4Tests {

        @Test
        @DisplayName("Near-stoichiometric (MR 3.0–4.0) contains H2 and CO")
        void nearStoichiometricContainsH2AndCo() {
            comp.setLoxCh4(3.5);
            assertThat(comp.get()).containsKey("H2");
            assertThat(comp.get()).containsKey("CO");
            assertThat(comp.get().values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
        }

        @Test
        @DisplayName("Fuel-rich (MR < 3.0) contains CO and H2")
        void fuelRichContainsCoAndH2() {
            comp.setLoxCh4(2.0);
            assertThat(comp.get()).containsKey("CO");
            assertThat(comp.get()).containsKey("H2");
            assertThat(comp.get().values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
        }

        @Test
        @DisplayName("Oxidizer-rich (MR >= 4.0) contains O2")
        void oxidizerRichContainsO2() {
            comp.setLoxCh4(5.0);
            assertThat(comp.get()).containsKey("O2");
            assertThat(comp.get().values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
        }
    }

    @Nested
    @DisplayName("LOX/LH2 composition presets")
    class LoxLh2Tests {

        @Test
        @DisplayName("Near-stoichiometric (MR 5.0–7.0) contains H2 and O2")
        void nearStoichiometricContainsH2AndO2() {
            comp.setLoxLh2(6.0);
            assertThat(comp.get()).containsKey("H2");
            assertThat(comp.get()).containsKey("O2");
            assertThat(comp.get().values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
        }

        @Test
        @DisplayName("Fuel-rich (MR < 5.0) contains H2")
        void fuelRichContainsH2() {
            comp.setLoxLh2(4.0);
            assertThat(comp.get()).containsKey("H2");
            assertThat(comp.get().values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
        }

        @Test
        @DisplayName("Oxidizer-rich (MR >= 7.0) contains O2")
        void oxidizerRichContainsO2() {
            comp.setLoxLh2(8.0);
            assertThat(comp.get()).containsKey("O2");
            assertThat(comp.get().values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
        }
    }

    @Nested
    @DisplayName("N2O/Ethanol composition presets")
    class N2oEthanolTests {

        @Test
        @DisplayName("Nominal (MR ~5.0) is non-empty and normalized")
        void nominalIsNonEmptyAndNormalized() {
            comp.setN2oEthanol(5.0);
            assertThat(comp.get()).isNotEmpty();
            assertThat(comp.get().values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
        }

        @Test
        @DisplayName("Nominal (MR ~5.0) seeds N2 from the N2O nitrogen")
        void nominalContainsN2() {
            comp.setN2oEthanol(5.0);
            assertThat(comp.get()).containsKey("N2");
        }

        @Test
        @DisplayName("Nominal (MR ~5.0) seeds H2O from C/H/O balance")
        void nominalContainsH2O() {
            comp.setN2oEthanol(5.0);
            assertThat(comp.get()).containsKey("H2O");
        }

        @Test
        @DisplayName("Nominal (MR ~5.0) seeds OH radical")
        void nominalContainsOH() {
            comp.setN2oEthanol(5.0);
            assertThat(comp.get()).containsKey("OH");
        }

        @Test
        @DisplayName("Fuel-rich (MR 2.0) contains CO")
        void fuelRichContainsCO() {
            comp.setN2oEthanol(2.0);
            assertThat(comp.get()).containsKey("CO");
            assertThat(comp.get().values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
        }

        @Test
        @DisplayName("Near-stoichiometric (MR ~5.7) contains CO2")
        void nearStoichContainsCO2() {
            comp.setN2oEthanol(5.7);
            assertThat(comp.get()).containsKey("CO2");
            assertThat(comp.get().values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
        }

        @Test
        @DisplayName("N2 mass fraction grows with increasing MR (more N2O → more N2)")
        void n2FractionGrowsWithMixtureRatio() {
            comp.setN2oEthanol(3.0);
            double n2At3 = comp.get().getOrDefault("N2", 0.0);
            comp.setN2oEthanol(8.0);
            double n2At8 = comp.get().getOrDefault("N2", 0.0);
            assertThat(n2At8).isGreaterThan(n2At3);
        }
    }

    @Nested
    @DisplayName("N2O/Propane composition presets")
    class N2oPropaneTests {

        @Test
        @DisplayName("Nominal (MR ~8.0) is non-empty and normalized")
        void nominalIsNonEmptyAndNormalized() {
            comp.setN2oPropane(8.0);
            assertThat(comp.get()).isNotEmpty();
            assertThat(comp.get().values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
        }

        @Test
        @DisplayName("Nominal (MR ~8.0) seeds N2 from the N2O nitrogen")
        void nominalContainsN2() {
            comp.setN2oPropane(8.0);
            assertThat(comp.get()).containsKey("N2");
        }

        @Test
        @DisplayName("Nominal (MR ~8.0) seeds H2O from C/H/O balance")
        void nominalContainsH2O() {
            comp.setN2oPropane(8.0);
            assertThat(comp.get()).containsKey("H2O");
        }

        @Test
        @DisplayName("Nominal (MR ~8.0) seeds OH radical")
        void nominalContainsOH() {
            comp.setN2oPropane(8.0);
            assertThat(comp.get()).containsKey("OH");
        }

        @Test
        @DisplayName("Fuel-rich (MR 3.0) contains CO and H2")
        void fuelRichContainsCoAndH2() {
            comp.setN2oPropane(3.0);
            assertThat(comp.get()).containsKey("CO");
            assertThat(comp.get()).containsKey("H2");
            assertThat(comp.get().values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
        }

        @Test
        @DisplayName("Near-stoichiometric (MR ~10.0) contains CO2")
        void nearStoichContainsCO2() {
            comp.setN2oPropane(10.0);
            assertThat(comp.get()).containsKey("CO2");
            assertThat(comp.get().values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
        }

        @Test
        @DisplayName("N2 mass fraction grows with increasing MR (more N2O → more N2)")
        void n2FractionGrowsWithMixtureRatio() {
            comp.setN2oPropane(5.0);
            double n2At5 = comp.get().getOrDefault("N2", 0.0);
            comp.setN2oPropane(12.0);
            double n2At12 = comp.get().getOrDefault("N2", 0.0);
            assertThat(n2At12).isGreaterThan(n2At5);
        }
    }

    @Nested
    @DisplayName("H2O and OH seeding across all propellant families")
    class H2oAndOhSeedingTests {

        @Test
        @DisplayName("LOX/RP-1 near-stoich seeds H2O")
        void loxRp1SeedsH2O() {
            comp.setLoxRp1(2.5);
            assertThat(comp.get()).containsKey("H2O");
        }

        @Test
        @DisplayName("LOX/RP-1 near-stoich seeds OH radical (10% of H2O seed)")
        void loxRp1SeedsOH() {
            comp.setLoxRp1(2.5);
            assertThat(comp.get()).containsKey("OH");
        }

        @Test
        @DisplayName("LOX/CH4 near-stoich seeds H2O")
        void loxCh4SeedsH2O() {
            comp.setLoxCh4(3.5);
            assertThat(comp.get()).containsKey("H2O");
        }

        @Test
        @DisplayName("LOX/CH4 near-stoich seeds OH radical (10% of H2O seed)")
        void loxCh4SeedsOH() {
            comp.setLoxCh4(3.5);
            assertThat(comp.get()).containsKey("OH");
        }

        @Test
        @DisplayName("LOX/LH2 near-stoich seeds H2O (C-free branch)")
        void loxLh2SeedsH2O() {
            comp.setLoxLh2(6.0);
            assertThat(comp.get()).containsKey("H2O");
        }

        @Test
        @DisplayName("LOX/LH2 near-stoich seeds OH radical (3% of H2O seed, C-free branch)")
        void loxLh2SeedsOH() {
            comp.setLoxLh2(6.0);
            assertThat(comp.get()).containsKey("OH");
        }

    }
}
