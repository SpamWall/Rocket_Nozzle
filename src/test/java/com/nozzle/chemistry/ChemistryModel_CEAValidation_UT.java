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
 * → {@link ChemistryModel#calculateEquilibriumGamma} — against NASA CEA reference values
 * for mean molecular weight (M̄) and equilibrium isentropic exponent (γ_s).
 *
 * <p>These quantities directly feed the characteristic velocity
 * c* = √(γ R T / M̄) · f(γ) and the specific impulse Isp. A composition error that
 * passes the ±3 % species-fraction tolerances in {@link GibbsMinimizer_CEAValidation_UT}
 * can still shift γ or M̄ by a fraction of a percent — enough to affect engine
 * performance predictions. This suite provides a scalar cross-check at the output
 * of the complete pipeline: Gibbs minimizer → NASA-7 Cp evaluation → property derivation.
 *
 * <h2>How to obtain CEA reference values</h2>
 *
 * <p>Run the same {@code tp} input decks documented in {@link GibbsMinimizer_CEAValidation_UT}.
 * From the CEA output read:
 * <ul>
 *   <li>{@code M, (1/n)} (g/mol) → {@code REF_MW}</li>
 *   <li>{@code GAMMAs} (equilibrium isentropic exponent,
 *       γ_s = Cp_eq / [(−β)·Cp_eq − (R/M̄)·α²]) → {@code REF_GAMMA}</li>
 * </ul>
 *
 * <h2>Tolerances</h2>
 *
 * <p>Both CEA and this solver evaluate thermodynamic properties from NASA-7 polynomials
 * sourced from NASA TP-2002-211556, so agreement is expected to be close.
 * Tolerances are ±1 % relative for both M̄ and γ_s.
 */
@DisplayName("ChemistryModel — NASA CEA validation (MW and gamma)")
class ChemistryModel_CEAValidation_UT {

    // =======================================================================
    //  LOX / LH2   —   O/F = 6.0,  T = 3485 K,  P = 7 MPa
    // =======================================================================

    /**
     * Conditions match the primary LOX/LH2 case in
     * {@link GibbsMinimizer_CEAValidation_UT.LoxLh2_3485K_7MPa}.
     */
    @Nested
    @DisplayName("LOX/LH2  O/F=6.0  T=3485 K  P=7 MPa")
    class LoxLh2_3485K_7MPa {

        // NASA CEA reference values (tp deck: T=3485 K, P=70 bar, O/F=6, LOX/LH2).
        private static final double REF_MW    = 13.460;
        private static final double REF_GAMMA = 1.1402;

        private ChemistryModel model;

        @BeforeEach
        void solve() {
            model = ChemistryModel.equilibrium(GasProperties.LOX_LH2_PRODUCTS);
            model.setLoxLh2Composition(6.0);
            model.calculateEquilibrium(3485, 7e6);
        }

        @Test
        @DisplayName("Mean molecular weight matches CEA")
        void molecularWeight() {
            assertThat(model.calculateMolecularWeight())
                    .isCloseTo(REF_MW, within(REF_MW * 0.01));
        }

        @Test
        @DisplayName("Equilibrium specific-heat ratio matches CEA")
        void gamma() {
            assertThat(model.calculateEquilibriumGamma(3485, 7e6))
                    .isCloseTo(REF_GAMMA, within(REF_GAMMA * 0.01));
        }
    }

    // =======================================================================
    //  LOX / LH2   —   O/F = 6.0,  T = 3200 K,  P = 7 MPa
    // =======================================================================

    /**
     * Conditions match {@link GibbsMinimizer_CEAValidation_UT.LoxLh2_3200K_7MPa}.
     */
    @Nested
    @DisplayName("LOX/LH2  O/F=6.0  T=3200 K  P=7 MPa")
    class LoxLh2_3200K_7MPa {

        // NASA CEA reference values (tp deck: T=3200 K, P=70 bar, O/F=6, LOX/LH2).
        private static final double REF_MW    = 13.828;
        private static final double REF_GAMMA = 1.1510;

        private ChemistryModel model;

        @BeforeEach
        void solve() {
            model = ChemistryModel.equilibrium(GasProperties.LOX_LH2_PRODUCTS);
            model.setLoxLh2Composition(6.0);
            model.calculateEquilibrium(3200, 7e6);
        }

        @Test
        @DisplayName("Mean molecular weight matches CEA")
        void molecularWeight() {
            assertThat(model.calculateMolecularWeight())
                    .isCloseTo(REF_MW, within(REF_MW * 0.01));
        }

        @Test
        @DisplayName("Equilibrium specific-heat ratio matches CEA")
        void gamma() {
            assertThat(model.calculateEquilibriumGamma(3200, 7e6))
                    .isCloseTo(REF_GAMMA, within(REF_GAMMA * 0.01));
        }

        @Test
        @DisplayName("MW at 3200 K is greater than at 3485 K (less dissociation at lower T)")
        void mwGreaterThanAt3485K() {
            ChemistryModel hot = ChemistryModel.equilibrium(GasProperties.LOX_LH2_PRODUCTS);
            hot.setLoxLh2Composition(6.0);
            hot.calculateEquilibrium(3485, 7e6);
            assertThat(model.calculateMolecularWeight())
                    .isGreaterThan(hot.calculateMolecularWeight());
        }
    }

    // =======================================================================
    //  LOX / LH2   —   O/F = 6.0,  T = 3500 K,  P = 10 MPa
    // =======================================================================

    /**
     * Conditions match {@link GibbsMinimizer_CEAValidation_UT.LoxLh2_3500K_10MPa}.
     */
    @Nested
    @DisplayName("LOX/LH2  O/F=6.0  T=3500 K  P=10 MPa")
    class LoxLh2_3500K_10MPa {

        // NASA CEA reference values (tp deck: T=3500 K, P=100 bar, O/F=6, LOX/LH2).
        private static final double REF_MW    = 13.549;
        private static final double REF_GAMMA = 1.1431;

        private ChemistryModel model;

        @BeforeEach
        void solve() {
            model = ChemistryModel.equilibrium(GasProperties.LOX_LH2_PRODUCTS);
            model.setLoxLh2Composition(6.0);
            model.calculateEquilibrium(3500, 10e6);
        }

        @Test
        @DisplayName("Mean molecular weight matches CEA")
        void molecularWeight() {
            assertThat(model.calculateMolecularWeight())
                    .isCloseTo(REF_MW, within(REF_MW * 0.01));
        }

        @Test
        @DisplayName("Equilibrium specific-heat ratio matches CEA")
        void gamma() {
            assertThat(model.calculateEquilibriumGamma(3500, 10e6))
                    .isCloseTo(REF_GAMMA, within(REF_GAMMA * 0.01));
        }

        @Test
        @DisplayName("MW at 10 MPa is greater than at 7 MPa (less dissociation at higher P)")
        void mwGreaterThanAt7MPa() {
            ChemistryModel lowP = ChemistryModel.equilibrium(GasProperties.LOX_LH2_PRODUCTS);
            lowP.setLoxLh2Composition(6.0);
            lowP.calculateEquilibrium(3500, 7e6);
            assertThat(model.calculateMolecularWeight())
                    .isGreaterThan(lowP.calculateMolecularWeight());
        }
    }

    // =======================================================================
    //  LOX / CH4   —   O/F = 3.5,  T = 3200 K,  P = 7 MPa
    // =======================================================================

    /**
     * Conditions match {@link GibbsMinimizer_CEAValidation_UT.LoxCh4_3200K_7MPa}.
     */
    @Nested
    @DisplayName("LOX/CH4  O/F=3.5  T=3200 K  P=7 MPa")
    class LoxCh4_3200K_7MPa {

        // NASA CEA reference values (tp deck: T=3200 K, P=70 bar, O/F=3.5, LOX/CH4).
        private static final double REF_MW    = 23.246;
        private static final double REF_GAMMA = 1.1311;

        private ChemistryModel model;

        @BeforeEach
        void solve() {
            model = ChemistryModel.equilibrium(GasProperties.LOX_CH4_PRODUCTS);
            model.setLoxCh4Composition(3.5);
            model.calculateEquilibrium(3200, 7e6);
        }

        @Test
        @DisplayName("Mean molecular weight matches CEA")
        void molecularWeight() {
            assertThat(model.calculateMolecularWeight())
                    .isCloseTo(REF_MW, within(REF_MW * 0.01));
        }

        @Test
        @DisplayName("Equilibrium specific-heat ratio matches CEA")
        void gamma() {
            assertThat(model.calculateEquilibriumGamma(3200, 7e6))
                    .isCloseTo(REF_GAMMA, within(REF_GAMMA * 0.01));
        }

        @Test
        @DisplayName("MW is larger than LOX/LH2 (heavier combustion products)")
        void mwLargerThanLh2() {
            ChemistryModel lh2 = ChemistryModel.equilibrium(GasProperties.LOX_LH2_PRODUCTS);
            lh2.setLoxLh2Composition(6.0);
            lh2.calculateEquilibrium(3200, 7e6);
            assertThat(model.calculateMolecularWeight())
                    .isGreaterThan(lh2.calculateMolecularWeight());
        }
    }

    // =======================================================================
    //  LOX / RP-1   —   O/F = 2.77,  T = 3000 K,  P = 7 MPa
    // =======================================================================

    /**
     * Conditions match {@link GibbsMinimizer_CEAValidation_UT.LoxRp1_3000K_7MPa}.
     */
    @Nested
    @DisplayName("LOX/RP-1  O/F=2.77  T=3000 K  P=7 MPa")
    class LoxRp1_3000K_7MPa {

        // NASA CEA reference values (tp deck: T=3000 K, P=70 bar, O/F=2.77, LOX/RP-1).
        private static final double REF_MW    = 26.381;
        private static final double REF_GAMMA = 1.1517;

        private ChemistryModel model;

        @BeforeEach
        void solve() {
            model = ChemistryModel.equilibrium(GasProperties.LOX_RP1_PRODUCTS);
            model.setLoxRp1Composition(2.77);
            model.calculateEquilibrium(3000, 7e6);
        }

        @Test
        @DisplayName("Mean molecular weight matches CEA")
        void molecularWeight() {
            assertThat(model.calculateMolecularWeight())
                    .isCloseTo(REF_MW, within(REF_MW * 0.01));
        }

        @Test
        @DisplayName("Equilibrium specific-heat ratio matches CEA")
        void gamma() {
            assertThat(model.calculateEquilibriumGamma(3000, 7e6))
                    .isCloseTo(REF_GAMMA, within(REF_GAMMA * 0.01));
        }

        @Test
        @DisplayName("MW is larger than LOX/CH4 (heavier carbon-rich products)")
        void mwLargerThanCh4() {
            ChemistryModel ch4 = ChemistryModel.equilibrium(GasProperties.LOX_CH4_PRODUCTS);
            ch4.setLoxCh4Composition(3.5);
            ch4.calculateEquilibrium(3000, 7e6);
            assertThat(model.calculateMolecularWeight())
                    .isGreaterThan(ch4.calculateMolecularWeight());
        }
    }
}
