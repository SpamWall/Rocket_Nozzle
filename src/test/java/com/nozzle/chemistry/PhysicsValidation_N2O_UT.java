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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Physics-level validation that the Gibbs minimizer conserves every element
 * (H, C, O, N) exactly across the full O/F ratio range of N₂O-based propellants.
 *
 * <p>Element conservation is the hard constraint that the Newton-Lagrange
 * algorithm enforces at each iteration (see {@link GibbsMinimizer}).  A
 * violation would indicate a bug in the element-balance projection, the
 * species composition matrix, or the initial-composition setup in
 * {@link PropellantComposition}.  This test catches those bugs at the
 * physics level rather than through indirect numerical agreement with
 * reference values.
 *
 * <h2>Verification method</h2>
 *
 * <p>For each O/F ratio the test:
 * <ol>
 *   <li>Constructs the initial mass-fraction map via {@link PropellantComposition}.</li>
 *   <li>Computes moles of each element per gram of mixture using
 *       {@link GibbsMinimizer#ELEM_COMPOSITION} and the NASA molecular weights.</li>
 *   <li>Runs {@link GibbsMinimizer#minimize} to obtain the equilibrium
 *       mass-fraction map.</li>
 *   <li>Computes element moles from the equilibrium map by the same method.</li>
 *   <li>Asserts that input and output atom counts agree to within 10⁻⁵ relative
 *       (10 ppm).  The solver's internal element-projection residual tolerance is
 *       10⁻¹²; the larger test bound accommodates rounding in the final
 *       mole-to-mass-fraction conversion.</li>
 * </ol>
 *
 * <h2>Nitrogen budget cross-check</h2>
 *
 * <p>An additional test verifies that the N atom budget derived from the
 * initial composition exactly matches the analytical expression
 * {@code 2 × (O/F) / (MW_N2O × (1 + O/F))} — confirming that
 * {@link PropellantComposition} correctly converts the N₂O feed into
 * elemental N moles before the solver runs.
 *
 * <h2>Analogous tests</h2>
 *
 * <ul>
 *   <li>{@code PhysicsValidation_MOC_UT} — mass conservation in the MOC network</li>
 *   <li>{@code PhysicsValidation_IsentropicRelations_UT} — isentropic
 *       flow-relation accuracy for γ = 1.4</li>
 * </ul>
 */
@DisplayName("Physics Validation — N2O element conservation")
class PhysicsValidation_N2O_UT {

    private static final NasaSpeciesDatabase DB       = new NasaSpeciesDatabase();
    private static final GibbsMinimizer      MINIMIZER = new GibbsMinimizer(DB);

    private static final double T_K  = 3000.0;  // K  — standard combustion temperature
    private static final double P_PA = 7e6;      // Pa — 70 bar chamber pressure

    /** MW of N₂O in g/mol (same value used by PropellantComposition). */
    private static final double MW_N2O = 44.013;

    /** Element row indices in {@link GibbsMinimizer#ELEM_COMPOSITION}. */
    private static final int EL_H = 0, EL_C = 1, EL_O = 2, EL_N = 3;

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Returns moles of each element (H, C, O, N) per gram of mixture computed
     * from {@code massFractions}, using the Gibbs solver's element-composition
     * matrix and the NASA molecular-weight database.
     *
     * @return double[4] in row order H, C, O, N matching
     *         {@link GibbsMinimizer#ELEM_COMPOSITION}
     */
    private static double[] elementMoles(Map<String, Double> massFractions) {
        double[] moles = new double[GibbsMinimizer.NUM_ELEMENTS];
        for (int j = 0; j < GibbsMinimizer.NUM_SPECIES; j++) {
            String name = GibbsMinimizer.SPECIES_ORDER[j];
            double mf = massFractions.getOrDefault(name, 0.0);
            if (mf <= 0.0) continue;
            SpeciesData sd = DB.get(name);
            if (sd == null) continue;
            double molJ = mf / sd.molecularWeight();
            for (int i = 0; i < GibbsMinimizer.NUM_ELEMENTS; i++) {
                moles[i] += GibbsMinimizer.ELEM_COMPOSITION[i][j] * molJ;
            }
        }
        return moles;
    }

    private static Map<String, Double> n2oEthanolAt(double of) {
        PropellantComposition c = new PropellantComposition();
        c.setN2oEthanol(of);
        return c.get();
    }

    private static Map<String, Double> n2oPropaneAt(double of) {
        PropellantComposition c = new PropellantComposition();
        c.setN2oPropane(of);
        return c.get();
    }

    // -----------------------------------------------------------------------
    // N2O/Ethanol  —  O/F range 3.0–9.0  (stoichiometric ≈ 5.73)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("N2O/Ethanol — element conservation at 3000 K, 7 MPa")
    class N2oEthanolConservation {

        @ParameterizedTest(name = "H conserved at O/F={0}")
        @ValueSource(doubles = {3.0, 4.0, 5.0, 5.73, 7.0, 9.0})
        @DisplayName("Hydrogen atoms conserved across O/F range")
        void hydrogenConserved(double of) {
            Map<String, Double> input = n2oEthanolAt(of);
            double[] in  = elementMoles(input);
            double[] out = elementMoles(MINIMIZER.minimize(input, T_K, P_PA));
            assertThat(out[EL_H])
                    .as("H atoms conserved at O/F=%.2f", of)
                    .isCloseTo(in[EL_H], within(in[EL_H] * 1e-5));
        }

        @ParameterizedTest(name = "C conserved at O/F={0}")
        @ValueSource(doubles = {3.0, 4.0, 5.0, 5.73, 7.0, 9.0})
        @DisplayName("Carbon atoms conserved across O/F range")
        void carbonConserved(double of) {
            Map<String, Double> input = n2oEthanolAt(of);
            double[] in  = elementMoles(input);
            double[] out = elementMoles(MINIMIZER.minimize(input, T_K, P_PA));
            assertThat(out[EL_C])
                    .as("C atoms conserved at O/F=%.2f", of)
                    .isCloseTo(in[EL_C], within(in[EL_C] * 1e-5));
        }

        @ParameterizedTest(name = "O conserved at O/F={0}")
        @ValueSource(doubles = {3.0, 4.0, 5.0, 5.73, 7.0, 9.0})
        @DisplayName("Oxygen atoms conserved across O/F range")
        void oxygenConserved(double of) {
            Map<String, Double> input = n2oEthanolAt(of);
            double[] in  = elementMoles(input);
            double[] out = elementMoles(MINIMIZER.minimize(input, T_K, P_PA));
            assertThat(out[EL_O])
                    .as("O atoms conserved at O/F=%.2f", of)
                    .isCloseTo(in[EL_O], within(in[EL_O] * 1e-5));
        }

        @ParameterizedTest(name = "N conserved at O/F={0}")
        @ValueSource(doubles = {3.0, 4.0, 5.0, 5.73, 7.0, 9.0})
        @DisplayName("Nitrogen atoms conserved across O/F range")
        void nitrogenConserved(double of) {
            Map<String, Double> input = n2oEthanolAt(of);
            double[] in  = elementMoles(input);
            double[] out = elementMoles(MINIMIZER.minimize(input, T_K, P_PA));
            assertThat(out[EL_N])
                    .as("N atoms conserved at O/F=%.2f", of)
                    .isCloseTo(in[EL_N], within(in[EL_N] * 1e-5));
        }

        /**
         * Cross-check: the N atom budget in the initial composition matches the
         * analytical expression 2 × (O/F) / (MW_N2O × (1 + O/F)).
         * Each N₂O molecule contributes exactly 2 N atoms, and N₂O is the sole
         * nitrogen source.  A mismatch would indicate an error in
         * {@link PropellantComposition#setN2oEthanol}.
         */
        @ParameterizedTest(name = "N budget matches N2O supply at O/F={0}")
        @ValueSource(doubles = {3.0, 4.0, 5.0, 5.73, 7.0, 9.0})
        @DisplayName("Nitrogen budget equals 2 × n(N2O) per gram")
        void nitrogenBudgetMatchesN2OSupply(double of) {
            double n2oMassFraction = of / (1.0 + of);
            double expectedN = 2.0 * n2oMassFraction / MW_N2O;

            double[] atomsIn = elementMoles(n2oEthanolAt(of));
            assertThat(atomsIn[EL_N])
                    .as("N budget at O/F=%.2f", of)
                    .isCloseTo(expectedN, within(expectedN * 2e-5));
        }
    }

    // -----------------------------------------------------------------------
    // N2O/Propane  —  O/F range 5.0–12.0  (stoichiometric ≈ 9.98)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("N2O/Propane — element conservation at 3000 K, 7 MPa")
    class N2oPropaneConservation {

        @ParameterizedTest(name = "H conserved at O/F={0}")
        @ValueSource(doubles = {5.0, 7.0, 7.5, 9.0, 9.98, 12.0})
        @DisplayName("Hydrogen atoms conserved across O/F range")
        void hydrogenConserved(double of) {
            Map<String, Double> input = n2oPropaneAt(of);
            double[] in  = elementMoles(input);
            double[] out = elementMoles(MINIMIZER.minimize(input, T_K, P_PA));
            assertThat(out[EL_H])
                    .as("H atoms conserved at O/F=%.2f", of)
                    .isCloseTo(in[EL_H], within(in[EL_H] * 1e-5));
        }

        @ParameterizedTest(name = "C conserved at O/F={0}")
        @ValueSource(doubles = {5.0, 7.0, 7.5, 9.0, 9.98, 12.0})
        @DisplayName("Carbon atoms conserved across O/F range")
        void carbonConserved(double of) {
            Map<String, Double> input = n2oPropaneAt(of);
            double[] in  = elementMoles(input);
            double[] out = elementMoles(MINIMIZER.minimize(input, T_K, P_PA));
            assertThat(out[EL_C])
                    .as("C atoms conserved at O/F=%.2f", of)
                    .isCloseTo(in[EL_C], within(in[EL_C] * 1e-5));
        }

        @ParameterizedTest(name = "O conserved at O/F={0}")
        @ValueSource(doubles = {5.0, 7.0, 7.5, 9.0, 9.98, 12.0})
        @DisplayName("Oxygen atoms conserved across O/F range")
        void oxygenConserved(double of) {
            Map<String, Double> input = n2oPropaneAt(of);
            double[] in  = elementMoles(input);
            double[] out = elementMoles(MINIMIZER.minimize(input, T_K, P_PA));
            assertThat(out[EL_O])
                    .as("O atoms conserved at O/F=%.2f", of)
                    .isCloseTo(in[EL_O], within(in[EL_O] * 1e-5));
        }

        @ParameterizedTest(name = "N conserved at O/F={0}")
        @ValueSource(doubles = {5.0, 7.0, 7.5, 9.0, 9.98, 12.0})
        @DisplayName("Nitrogen atoms conserved across O/F range")
        void nitrogenConserved(double of) {
            Map<String, Double> input = n2oPropaneAt(of);
            double[] in  = elementMoles(input);
            double[] out = elementMoles(MINIMIZER.minimize(input, T_K, P_PA));
            assertThat(out[EL_N])
                    .as("N atoms conserved at O/F=%.2f", of)
                    .isCloseTo(in[EL_N], within(in[EL_N] * 1e-5));
        }

        @ParameterizedTest(name = "N budget matches N2O supply at O/F={0}")
        @ValueSource(doubles = {5.0, 7.0, 7.5, 9.0, 9.98, 12.0})
        @DisplayName("Nitrogen budget equals 2 × n(N2O) per gram")
        void nitrogenBudgetMatchesN2OSupply(double of) {
            double n2oMassFraction = of / (1.0 + of);
            double expectedN = 2.0 * n2oMassFraction / MW_N2O;

            double[] atomsIn = elementMoles(n2oPropaneAt(of));
            assertThat(atomsIn[EL_N])
                    .as("N budget at O/F=%.2f", of)
                    .isCloseTo(expectedN, within(expectedN * 2e-5));
        }
    }

    // -----------------------------------------------------------------------
    // Temperature independence — conservation holds at all combustion temperatures
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Element conservation across combustion temperature range")
    class TemperatureIndependence {

        /**
         * Element conservation is enforced by the Newton-Lagrange constraint
         * projection and is independent of temperature.  This test confirms
         * that the solver does not develop element drift at temperatures above
         * or below the nominal 3000 K used in the O/F sweep tests.
         */
        @ParameterizedTest(name = "All elements conserved at T={0} K")
        @ValueSource(doubles = {2500.0, 2800.0, 3000.0, 3200.0, 3500.0})
        @DisplayName("N2O/Ethanol O/F=5.73 — all elements conserved across temperature")
        void allElementsConservedAcrossTemperatures(double temperature) {
            Map<String, Double> input = n2oEthanolAt(5.73);
            double[] in  = elementMoles(input);
            double[] out = elementMoles(MINIMIZER.minimize(input, temperature, P_PA));
            for (int i = 0; i < GibbsMinimizer.NUM_ELEMENTS; i++) {
                if (in[i] > 0) {
                    assertThat(out[i])
                            .as("element %d conserved at T=%.0f K", i, temperature)
                            .isCloseTo(in[i], within(in[i] * 1e-5));
                }
            }
        }
    }
}
