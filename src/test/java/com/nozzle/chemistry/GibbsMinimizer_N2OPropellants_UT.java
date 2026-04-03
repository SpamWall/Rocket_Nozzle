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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Validates {@link GibbsMinimizer} for N₂O-based propellants, which exercise the
 * N-element path in the solver (N₂ and NO species).  All other test suites use
 * only LOX (O₂) oxidizer, leaving the nitrogen Lagrange multiplier and N-containing
 * species fully untested.
 *
 * <p>N₂O (nitrous oxide, MW = 44.013 g/mol) contributes 2 N atoms and 1 O atom
 * per molecule.  At combustion temperatures essentially all nitrogen ends up as N₂,
 * which becomes the dominant product species by mass fraction (≈ 50–56 %).  The
 * large N₂ background tests that the solver correctly normalizes mole fractions
 * and evaluates chemical potentials in the presence of a stable inert.
 *
 * <h2>Stoichiometric O/F ratios</h2>
 * <ul>
 *   <li>N₂O/Ethanol (C₂H₅OH + 6 N₂O → 2 CO₂ + 3 H₂O + 6 N₂): O/F ≈ 5.73</li>
 *   <li>N₂O/Propane (C₃H₈ + 10 N₂O → 3 CO₂ + 4 H₂O + 10 N₂): O/F ≈ 9.98</li>
 * </ul>
 *
 * <h2>Reference values</h2>
 *
 * <p>Reference values are solver outputs. Verify with NASA CEA using the
 * {@code tp} input decks documented in each nested class and replace with CEA values.
 * Tolerances follow the same scheme as {@link GibbsMinimizer_CEAValidation_UT}:
 * ±3 % relative for major species (mass fraction &gt; 0.05),
 * ±10 % relative for intermediate species (0.005–0.05), and ±0.002 absolute for
 * minor species (&lt; 0.005).
 */
@DisplayName("GibbsMinimizer — N2O propellants (N-element path)")
class GibbsMinimizer_N2OPropellants_UT {

    private GibbsMinimizer minimizer;

    @BeforeEach
    void setUp() {
        minimizer = new GibbsMinimizer(new NasaSpeciesDatabase());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Map<String, Double> n2oEthanol(double of) {
        PropellantComposition c = new PropellantComposition();
        c.setN2oEthanol(of);
        return c.get();
    }

    private Map<String, Double> n2oPropane(double of) {
        PropellantComposition c = new PropellantComposition();
        c.setN2oPropane(of);
        return c.get();
    }

    // =======================================================================
    //  N₂O / Ethanol   —   O/F = 5.0,  T = 3000 K,  P = 7 MPa
    //  Slightly fuel-rich (stoichiometric O/F ≈ 5.73)
    // =======================================================================

    /**
     * CEA tp input deck:
     * <pre>
     * prob case = tp
     * p,bar= 70
     * t,k= 3000
     * o/f = 5.0
     * reac
     * fuel C2H5OH  wt%=100.0  t(k)=298.15
     * oxid N2O     wt%=100.0  t(k)=298.15
     * output short mass fractions SI Units
     * end
     * </pre>
     *
     * <p>N₂ is the dominant product (≈ 53 %) because each N₂O molecule contributes
     * 2 N atoms that end up as N₂ while contributing only 1 O atom for combustion.
     * The H/C/O equilibrium is nearly identical to a LOX/ethanol case with the same
     * effective O/C ratio, except all mole fractions are diluted by the N₂ background.
     */
    @Nested
    @DisplayName("N2O/Ethanol  O/F=5.0  T=3000 K  P=7 MPa")
    class N2oEthanol_OF5_3000K_7MPa {

        // Reference values from solver output. Verify with CEA tp deck above.
        private static final double REF_N2  = 0.53041;
        private static final double REF_CO2 = 0.20490;
        private static final double REF_H2O = 0.17699;
        private static final double REF_CO  = 0.07226;
        private static final double REF_OH  = 0.00741;

        private Map<String, Double> result;

        @BeforeEach
        void solve() { result = minimizer.minimize(n2oEthanol(5.0), 3000, 7e6); }

        @Test
        @DisplayName("N2 is the dominant product (N-element path exercised)")
        void n2Dominant() {
            assertThat(result.getOrDefault("N2", 0.0))
                    .as("N2 mass fraction — confirms N-element path is active")
                    .isCloseTo(REF_N2, within(REF_N2 * 0.03));
        }

        @Test
        @DisplayName("CO2 mass fraction matches reference")
        void co2Major() {
            assertThat(result.getOrDefault("CO2", 0.0))
                    .isCloseTo(REF_CO2, within(REF_CO2 * 0.03));
        }

        @Test
        @DisplayName("H2O mass fraction matches reference")
        void h2oMajor() {
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isCloseTo(REF_H2O, within(REF_H2O * 0.03));
        }

        @Test
        @DisplayName("CO mass fraction matches reference (fuel-rich, some CO₂ dissociation)")
        void coIntermediate() {
            assertThat(result.getOrDefault("CO", 0.0))
                    .isCloseTo(REF_CO, within(REF_CO * 0.10));
        }

        @Test
        @DisplayName("OH is present as a dissociation product")
        void ohIntermediate() {
            assertThat(result.getOrDefault("OH", 0.0))
                    .isCloseTo(REF_OH, within(REF_OH * 0.10));
        }

        @Test
        @DisplayName("N2 fraction exceeds all individual combustion products")
        void n2ExceedsAllOtherSpecies() {
            double n2 = result.getOrDefault("N2", 0.0);
            for (Map.Entry<String, Double> e : result.entrySet()) {
                if (!"N2".equals(e.getKey())) {
                    assertThat(n2).isGreaterThan(e.getValue());
                }
            }
        }

        @Test
        @DisplayName("N2 fraction increases at higher O/F (more N2O oxidizer)")
        void n2IncreasesWithHigherOF() {
            Map<String, Double> higherOF = minimizer.minimize(n2oEthanol(5.73), 3000, 7e6);
            assertThat(result.getOrDefault("N2", 0.0))
                    .isLessThan(higherOF.getOrDefault("N2", 0.0));
        }

        @Test
        @DisplayName("CO2 at O/F=5.0 is less than at O/F=5.73 (closer to stoichiometric)")
        void co2IncreasesNearStoichiometric() {
            Map<String, Double> stoich = minimizer.minimize(n2oEthanol(5.73), 3000, 7e6);
            assertThat(result.getOrDefault("CO2", 0.0))
                    .isLessThan(stoich.getOrDefault("CO2", 0.0));
        }
    }

    // =======================================================================
    //  N₂O / Propane   —   O/F = 7.5,  T = 3000 K,  P = 7 MPa
    //  Fuel-rich (stoichiometric O/F ≈ 9.98)
    // =======================================================================

    /**
     * CEA tp input deck:
     * <pre>
     * prob case = tp
     * p,bar= 70
     * t,k= 3000
     * o/f = 7.5
     * reac
     * fuel C3H8    wt%=100.0  t(k)=298.15
     * oxid N2O     wt%=100.0  t(k)=298.15
     * output short mass fractions SI Units
     * end
     * </pre>
     *
     * <p>At O/F = 7.5 (75 % of stoichiometric), the mixture is substantially
     * fuel-rich: insufficient oxygen to convert all carbon to CO₂, so CO and CO₂
     * are nearly equal in mass fraction (≈ 14 % each). N₂ again dominates (≈ 56 %).
     * Propane has no oxygen, so all oxidizing oxygen comes from N₂O, maximizing
     * the N₂ yield relative to the combustion products.
     */
    @Nested
    @DisplayName("N2O/Propane  O/F=7.5  T=3000 K  P=7 MPa")
    class N2oPropane_OF7p5_3000K_7MPa {

        // Reference values from solver output. Verify with CEA tp deck above.
        private static final double REF_N2  = 0.56161;
        private static final double REF_H2O = 0.15493;
        private static final double REF_CO2 = 0.13793;
        private static final double REF_CO  = 0.13640;

        private Map<String, Double> result;

        @BeforeEach
        void solve() { result = minimizer.minimize(n2oPropane(7.5), 3000, 7e6); }

        @Test
        @DisplayName("N2 is the dominant product (N-element path exercised)")
        void n2Dominant() {
            assertThat(result.getOrDefault("N2", 0.0))
                    .as("N2 mass fraction — confirms N-element path is active")
                    .isCloseTo(REF_N2, within(REF_N2 * 0.03));
        }

        @Test
        @DisplayName("H2O mass fraction matches reference")
        void h2oMajor() {
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isCloseTo(REF_H2O, within(REF_H2O * 0.03));
        }

        @Test
        @DisplayName("CO2 mass fraction matches reference")
        void co2Major() {
            assertThat(result.getOrDefault("CO2", 0.0))
                    .isCloseTo(REF_CO2, within(REF_CO2 * 0.03));
        }

        @Test
        @DisplayName("CO mass fraction matches reference (fuel-rich: CO ≈ CO₂)")
        void coMajor() {
            assertThat(result.getOrDefault("CO", 0.0))
                    .isCloseTo(REF_CO, within(REF_CO * 0.03));
        }

        @Test
        @DisplayName("N2 fraction is higher than for N2O/Ethanol at similar T and P")
        void n2HigherThanEthanol() {
            Map<String, Double> ethanolResult = minimizer.minimize(n2oEthanol(5.0), 3000, 7e6);
            assertThat(result.getOrDefault("N2", 0.0))
                    .isGreaterThan(ethanolResult.getOrDefault("N2", 0.0));
        }

        @Test
        @DisplayName("CO fraction decreases as O/F increases toward stoichiometric")
        void coDecreasesNearStoichiometric() {
            Map<String, Double> higherOF = minimizer.minimize(n2oPropane(9.5), 3000, 7e6);
            assertThat(result.getOrDefault("CO", 0.0))
                    .isGreaterThan(higherOF.getOrDefault("CO", 0.0));
        }

        @Test
        @DisplayName("CO2 fraction increases as O/F increases toward stoichiometric")
        void co2IncreasesNearStoichiometric() {
            Map<String, Double> higherOF = minimizer.minimize(n2oPropane(9.5), 3000, 7e6);
            assertThat(result.getOrDefault("CO2", 0.0))
                    .isLessThan(higherOF.getOrDefault("CO2", 0.0));
        }
    }

    // =======================================================================
    //  NO (nitric oxide) formation from N2 + O2 equilibrium at high T
    //  NO is the only N/O mixed species in the database; its formation is
    //  thermodynamically favoured above ~2500 K and grows strongly with T.
    // =======================================================================

    /**
     * Verifies that NO appears in the equilibrium products of N₂O combustion
     * at high temperature, and that its mole fraction is absent for nitrogen-free
     * propellants.
     *
     * <p>The N₂ + O₂ ⇌ 2 NO equilibrium constant K_p ≈ 0.024 at 3000 K
     * (Kaye &amp; Laby, NIST-JANAF), so measurable NO forms wherever both N₂
     * and sufficient O (from O₂ or atomic O) are present.  For N₂O propellants
     * the N₂ pool is large (≈ 50–56 % by mass), making NO a non-negligible
     * minor species even in fuel-rich conditions.
     *
     * <p>For LOX/LH₂ there is no nitrogen, so the N element is absent from the
     * element balance and NO is correctly locked at the trace floor by the
     * {@code speciesActive[]} guard in the solver.
     */
    @Nested
    @DisplayName("NO formation at high temperature")
    class NoFormationAtHighTemperature {

        @Test
        @DisplayName("NO is present at 3000 K in near-stoichiometric N2O/Ethanol")
        void noPresent_N2oEthanol_nearStoichiometric_3000K() {
            // O/F = 5.73 ≈ stoichiometric; more free O increases NO yield
            Map<String, Double> result = minimizer.minimize(n2oEthanol(5.73), 3000, 7e6);
            assertThat(result.getOrDefault("NO", 0.0))
                    .as("NO mass fraction at near-stoichiometric N2O/Ethanol 3000 K")
                    .isGreaterThan(1e-5);
        }

        @Test
        @DisplayName("NO is present at 3000 K in N2O/Propane near stoichiometric")
        void noPresent_N2oPropane_nearStoichiometric_3000K() {
            // O/F = 9.5 ≈ close to stoichiometric (≈ 9.98)
            Map<String, Double> result = minimizer.minimize(n2oPropane(9.5), 3000, 7e6);
            assertThat(result.getOrDefault("NO", 0.0))
                    .as("NO mass fraction at near-stoichiometric N2O/Propane 3000 K")
                    .isGreaterThan(1e-5);
        }

        @Test
        @DisplayName("NO fraction increases with temperature (thermal equilibrium)")
        void noIncreasesWithTemperature_N2oEthanol() {
            // K_p(NO) grows strongly with T; higher T → more NO
            Map<String, Double> at3000 = minimizer.minimize(n2oEthanol(5.73), 3000, 7e6);
            Map<String, Double> at3300 = minimizer.minimize(n2oEthanol(5.73), 3300, 7e6);
            assertThat(at3300.getOrDefault("NO", 0.0))
                    .as("NO mass fraction must increase from 3000 K to 3300 K")
                    .isGreaterThan(at3000.getOrDefault("NO", 0.0));
        }

        @Test
        @DisplayName("NO is absent for LOX/LH2 (no nitrogen in propellant)")
        void noAbsent_LoxLh2() {
            PropellantComposition lh2 = new PropellantComposition();
            lh2.setLoxLh2(6.0);
            Map<String, Double> result = minimizer.minimize(lh2.get(), 3000, 7e6);
            // N element absent → speciesActive[NO] = false → NO not in output
            assertThat(result).doesNotContainKey("NO");
        }
    }
}
