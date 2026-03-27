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

@DisplayName("GibbsMinimizer Tests")
class GibbsMinimizer_UT {

    private GibbsMinimizer minimizer;

    @BeforeEach
    void setUp() {
        minimizer = new GibbsMinimizer(new NasaSpeciesDatabase());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Computes moles of each element (H, C, O, N) from a mass-fraction map. */
    private static double[] computeElementalMoles(Map<String, Double> massFractions) {
        String[] speciesOrder = {"H2O", "CO2", "H2", "CO", "OH", "O2", "N2", "H", "O"};
        double[] mw = {18.015, 44.01, 2.016, 28.01, 17.007, 32.0, 28.014, 1.008, 16.0};
        double[][] elemComp = {
                {2, 0, 2, 0, 1, 0, 0, 1, 0},  // H
                {0, 1, 0, 1, 0, 0, 0, 0, 0},  // C
                {1, 2, 0, 1, 1, 2, 0, 0, 1},  // O
                {0, 0, 0, 0, 0, 0, 2, 0, 0},  // N
        };
        double[] elements = new double[4];
        for (int j = 0; j < speciesOrder.length; j++) {
            double y = massFractions.getOrDefault(speciesOrder[j], 0.0);
            double moles = y / mw[j];
            for (int i = 0; i < 4; i++) {
                elements[i] += elemComp[i][j] * moles;
            }
        }
        return elements;
    }

    private static Map<String, Double> lh2Initial(double mixtureRatio) {
        PropellantComposition comp = new PropellantComposition();
        comp.setLoxLh2(mixtureRatio);
        return comp.get();
    }

    private static Map<String, Double> ch4Initial(double mixtureRatio) {
        PropellantComposition comp = new PropellantComposition();
        comp.setLoxCh4(mixtureRatio);
        return comp.get();
    }

    private static Map<String, Double> rp1Initial(double mixtureRatio) {
        PropellantComposition comp = new PropellantComposition();
        comp.setLoxRp1(mixtureRatio);
        return comp.get();
    }

    // -------------------------------------------------------------------------
    // Core equilibrium tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Equilibrium Calculation Tests")
    class EquilibriumCalculationTests {

        @Test
        @DisplayName("minimize() should change composition relative to initial")
        void minimizeShouldModifyComposition() {
            Map<String, Double> before = lh2Initial(6.0);
            Map<String, Double> after = minimizer.minimize(before, 3200, 7e6);

            assertThat(after).isNotEmpty();
            assertThat(after).isNotEqualTo(before);
            assertThat(after.values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
        }

        @Test
        @DisplayName("Equilibrium should conserve elemental composition (H, C, O, N)")
        void equilibriumShouldConserveElements() {
            Map<String, Double> initial = lh2Initial(6.0);
            double[] elementsBefore = computeElementalMoles(initial);

            double[] elementsAfter = computeElementalMoles(minimizer.minimize(initial, 3200, 7e6));

            for (int i = 0; i < elementsBefore.length; i++) {
                if (elementsBefore[i] > 1e-15) {
                    assertThat(elementsAfter[i])
                            .isCloseTo(elementsBefore[i], within(elementsBefore[i] * 1e-4));
                }
            }
        }

        @Test
        @DisplayName("Higher pressure should reduce dissociation (Le Chatelier)")
        void higherPressureShouldReduceDissociation() {
            Map<String, Double> initial = lh2Initial(6.0);
            double h2oLowP  = minimizer.minimize(initial, 3200, 1e5).getOrDefault("H2O", 0.0);
            double h2oHighP = minimizer.minimize(initial, 3200, 1e7).getOrDefault("H2O", 0.0);

            assertThat(h2oHighP).isGreaterThan(h2oLowP);
        }

        @Test
        @DisplayName("Higher temperature should increase dissociation")
        void higherTemperatureShouldIncreaseDissociation() {
            Map<String, Double> initial = lh2Initial(6.0);
            double h2oLowT  = minimizer.minimize(initial, 1500, 7e6).getOrDefault("H2O", 0.0);
            double h2oHighT = minimizer.minimize(initial, 4000, 7e6).getOrDefault("H2O", 0.0);

            assertThat(h2oLowT).isGreaterThan(h2oHighT);
        }

        @Test
        @DisplayName("minimize() should be idempotent at the same conditions")
        void minimizeShouldBeIdempotent() {
            Map<String, Double> initial = lh2Initial(6.0);
            Map<String, Double> first   = minimizer.minimize(initial, 3200, 7e6);
            Map<String, Double> second  = minimizer.minimize(Map.copyOf(first), 3200, 7e6);

            for (String species : first.keySet()) {
                assertThat(second.getOrDefault(species, 0.0))
                        .isCloseTo(first.get(species), within(1e-6));
            }
        }

        @Test
        @DisplayName("Mass fractions should sum to 1.0 after minimize()")
        void massFractionsShouldSumToOne() {
            Map<String, Double> result = minimizer.minimize(lh2Initial(6.0), 3200, 7e6);
            assertThat(result.values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
        }

        @Test
        @DisplayName("LOX/CH4 equilibrium should conserve elements and respond to temperature")
        void loxCh4EquilibriumShouldWork() {
            Map<String, Double> initial = ch4Initial(3.5);
            double[] elementsBefore = computeElementalMoles(initial);

            Map<String, Double> result = minimizer.minimize(initial, 3200, 7e6);
            assertThat(result.values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));

            double[] elementsAfter = computeElementalMoles(result);
            for (int i = 0; i < elementsBefore.length; i++) {
                if (elementsBefore[i] > 1e-15) {
                    assertThat(elementsAfter[i])
                            .isCloseTo(elementsBefore[i], within(elementsBefore[i] * 1e-4));
                }
            }

            double h2oLowT  = minimizer.minimize(initial, 1500, 7e6).getOrDefault("H2O", 0.0);
            double h2oHighT = minimizer.minimize(initial, 4000, 7e6).getOrDefault("H2O", 0.0);
            assertThat(h2oLowT).isGreaterThan(h2oHighT);
        }
    }

    // -------------------------------------------------------------------------
    // Solver edge-case / branch coverage
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Solver Edge Cases")
    class SolverEdgeCaseTests {

        @Test
        @DisplayName("Starting from pure reactants triggers Newton-step damping")
        void startingFromPureReactantsTriggersDamping() {
            // Pure H2 + O2 — no H2O in initial composition.
            // First Newton step: correction >> 2 → damping fires (maxCorrMajor > 2.0 branch).
            PropellantComposition comp = new PropellantComposition();
            comp.set(Map.of("H2", 0.111, "O2", 0.889));

            Map<String, Double> result = minimizer.minimize(comp.get(), 3500, 5e6);

            assertThat(result).isNotEmpty();
            assertThat(result).containsKey("H2O");
            assertThat(result.values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-5));
        }

        @Test
        @DisplayName("Low-temperature equilibrium exercises minor-species and trace-filter branches")
        void lowTemperatureExercisesMinorAndTraceFilterBranches() {
            // At 800 K, H and O approach MIN_MOLES (minor-species clamping path) and
            // may fall below TRACE_THRESHOLD (filter branch).
            Map<String, Double> result = minimizer.minimize(lh2Initial(6.0), 800, 7e6);

            assertThat(result).isNotEmpty();
            assertThat(result.getOrDefault("H2O", 0.0)).isGreaterThan(0.5);
            assertThat(result.values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
            result.values().forEach(v -> assertThat(v).isGreaterThan(1e-10));
        }

        @Test
        @DisplayName("LOX/RP-1 fuel-rich equilibrium conserves C, H, O elements")
        void loxRp1FuelRichEquilibriumConverges() {
            Map<String, Double> result = minimizer.minimize(rp1Initial(1.5), 3000, 7e6);
            assertThat(result).isNotEmpty();
            assertThat(result.values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-5));
        }

        @Test
        @DisplayName("LOX/CH4 oxidizer-rich equilibrium conserves elements")
        void loxCh4OxidizerRichEquilibriumConverges() {
            Map<String, Double> result = minimizer.minimize(ch4Initial(5.0), 3000, 7e6);
            assertThat(result).isNotEmpty();
            assertThat(result.values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-5));
        }

        @Test
        @DisplayName("LOX/LH2 oxidizer-rich equilibrium converges")
        void loxLh2OxidizerRichEquilibriumConverges() {
            Map<String, Double> result = minimizer.minimize(lh2Initial(8.0), 3200, 7e6);
            assertThat(result).isNotEmpty();
            assertThat(result.values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-5));
        }
    }
}
