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

import com.nozzle.core.GasProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Validates the full equilibrium property pipeline —
 * {@link ChemistryModel#calculateEquilibrium} → {@link ChemistryModel#calculateMolecularWeight}
 * → {@link ChemistryModel#calculateEquilibriumGamma} — for N₂O-based propellants.
 *
 * <p>Complements {@link ChemistryModel_CEAValidation_UT} (which covers LOX propellants) and
 * {@link GibbsMinimizer_N2OPropellants_UT} (which validates species fractions) by exercising
 * the scalar output of the complete pipeline: Gibbs minimizer → NASA-7 Cp evaluation →
 * mean molecular weight and equilibrium gamma derivation.
 *
 * <p>N₂O dominates the nitrogen budget: each molecule contributes 2 N atoms that appear
 * entirely as N₂ in the equilibrium products.  N₂ is thermally stable at 3000 K and does
 * not dissociate, so it acts as a heavy inert diluent.  Its presence raises M̄ relative to
 * LOX/hydrocarbon mixtures (N₂ MW = 28.014 g/mol vs combustion-product averages of ≈ 20–26)
 * and reduces the magnitude of the equilibrium composition shift (only the C/H/O sub-system
 * shifts; the N₂ fraction is kinematically frozen), driving γ_s closer to the frozen γ.
 *
 * <h2>How to obtain CEA reference values</h2>
 *
 * <p>Run the {@code tp} input decks documented in each nested class.  From the CEA output read:
 * <ul>
 *   <li>{@code M, (1/n)} (g/mol) → {@code REF_MW}</li>
 *   <li>{@code GAMMAs} → {@code REF_GAMMA}</li>
 * </ul>
 *
 * <h2>Tolerances</h2>
 *
 * <p>±1 % relative for both M̄ and γ_s, consistent with
 * {@link ChemistryModel_CEAValidation_UT}.
 */
@DisplayName("ChemistryModel — N2O propellants (MW and gamma pipeline)")
class ChemistryModel_N2OPropellants_UT {

    // =======================================================================
    //  N₂O / Ethanol   —   O/F = 5.0,  T = 3000 K,  P = 7 MPa
    //  Conditions match GibbsMinimizer_N2OPropellants_UT.N2oEthanol_OF5_3000K_7MPa
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
     */
    @Nested
    @DisplayName("N2O/Ethanol  O/F=5.0  T=3000 K  P=7 MPa")
    class N2oEthanol_OF5_3000K_7MPa {

        // NASA CEA reference values (tp deck above).
        private static final double REF_MW    = 26.594;
        private static final double REF_GAMMA = 1.1596;

        private ChemistryModel model;

        @BeforeEach
        void solve() {
            model = ChemistryModel.equilibrium(GasProperties.N2O_ETHANOL_PRODUCTS);
            model.setN2oEthanolComposition(5.0);
            model.calculateEquilibrium(3000, 7e6);
        }

        @Test
        @DisplayName("Mean molecular weight matches reference")
        void molecularWeight() {
            assertThat(model.calculateMolecularWeight())
                    .isCloseTo(REF_MW, within(REF_MW * 0.01));
        }

        @Test
        @DisplayName("Equilibrium specific-heat ratio matches reference")
        void gamma() {
            assertThat(model.calculateEquilibriumGamma(3000, 7e6))
                    .isCloseTo(REF_GAMMA, within(REF_GAMMA * 0.01));
        }

        @Test
        @DisplayName("MW is much higher than LOX/LH2 products (N2 inert raises mixture weight)")
        void mwHigherThanLoxLh2() {
            ChemistryModel lh2 = ChemistryModel.equilibrium(GasProperties.LOX_LH2_PRODUCTS);
            lh2.setLoxLh2Composition(6.0);
            lh2.calculateEquilibrium(3000, 7e6);
            assertThat(model.calculateMolecularWeight())
                    .isGreaterThan(lh2.calculateMolecularWeight());
        }

        @Test
        @DisplayName("Frozen gamma exceeds equilibrium gamma (C/H/O dissociation contribution)")
        void frozenGammaExceedsEquilibrium() {
            assertThat(model.calculateGamma(3000))
                    .isGreaterThan(model.calculateEquilibriumGamma(3000, 7e6));
        }

        @Test
        @DisplayName("MW increases at lower T (less dissociation in C/H/O sub-system)")
        void mwIncreasesAtLowerTemperature() {
            ChemistryModel cool = ChemistryModel.equilibrium(GasProperties.N2O_ETHANOL_PRODUCTS);
            cool.setN2oEthanolComposition(5.0);
            cool.calculateEquilibrium(2500, 7e6);
            assertThat(cool.calculateMolecularWeight())
                    .isGreaterThan(model.calculateMolecularWeight());
        }
    }

    // =======================================================================
    //  N₂O / Propane   —   O/F = 7.5,  T = 3000 K,  P = 7 MPa
    //  Conditions match GibbsMinimizer_N2OPropellants_UT.N2oPropane_OF7p5_3000K_7MPa
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
     */
    @Nested
    @DisplayName("N2O/Propane  O/F=7.5  T=3000 K  P=7 MPa")
    class N2oPropane_OF7p5_3000K_7MPa {

        // NASA CEA reference values (tp deck above).
        private static final double REF_MW    = 25.632;
        private static final double REF_GAMMA = 1.1904;

        private ChemistryModel model;

        @BeforeEach
        void solve() {
            model = ChemistryModel.equilibrium(GasProperties.N2O_PROPANE_PRODUCTS);
            model.setN2oPropaneComposition(7.5);
            model.calculateEquilibrium(3000, 7e6);
        }

        @Test
        @DisplayName("Mean molecular weight matches reference")
        void molecularWeight() {
            assertThat(model.calculateMolecularWeight())
                    .isCloseTo(REF_MW, within(REF_MW * 0.01));
        }

        @Test
        @DisplayName("Equilibrium specific-heat ratio matches reference")
        void gamma() {
            assertThat(model.calculateEquilibriumGamma(3000, 7e6))
                    .isCloseTo(REF_GAMMA, within(REF_GAMMA * 0.01));
        }

        @Test
        @DisplayName("MW is higher than LOX/CH4 products (N2 inert raises mixture weight)")
        void mwHigherThanLoxCh4() {
            ChemistryModel ch4 = ChemistryModel.equilibrium(GasProperties.LOX_CH4_PRODUCTS);
            ch4.setLoxCh4Composition(3.5);
            ch4.calculateEquilibrium(3000, 7e6);
            assertThat(model.calculateMolecularWeight())
                    .isGreaterThan(ch4.calculateMolecularWeight());
        }

        @Test
        @DisplayName("Frozen gamma exceeds equilibrium gamma (C/H/O dissociation contribution)")
        void frozenGammaExceedsEquilibrium() {
            assertThat(model.calculateGamma(3000))
                    .isGreaterThan(model.calculateEquilibriumGamma(3000, 7e6));
        }

        @Test
        @DisplayName("MW increases at lower T (less dissociation in C/H/O sub-system)")
        void mwIncreasesAtLowerTemperature() {
            ChemistryModel cool = ChemistryModel.equilibrium(GasProperties.N2O_PROPANE_PRODUCTS);
            cool.setN2oPropaneComposition(7.5);
            cool.calculateEquilibrium(2500, 7e6);
            assertThat(cool.calculateMolecularWeight())
                    .isGreaterThan(model.calculateMolecularWeight());
        }
    }
}
