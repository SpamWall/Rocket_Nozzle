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

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.within;

@DisplayName("ChemistryModel Tests")
class ChemistryModel_UT {

    private ChemistryModel frozenModel;
    private ChemistryModel equilibriumModel;

    @BeforeEach
    void setUp() {
        frozenModel      = ChemistryModel.frozen(GasProperties.LOX_RP1_PRODUCTS);
        equilibriumModel = ChemistryModel.equilibrium(GasProperties.LOX_LH2_PRODUCTS);
    }

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {

        @Test
        @DisplayName("frozen() should produce a FROZEN model")
        void shouldCreateFrozenModel() {
            assertThat(frozenModel.getModelType()).isEqualTo(ChemistryModel.ModelType.FROZEN);
        }

        @Test
        @DisplayName("equilibrium() should produce an EQUILIBRIUM model")
        void shouldCreateEquilibriumModel() {
            assertThat(equilibriumModel.getModelType()).isEqualTo(ChemistryModel.ModelType.EQUILIBRIUM);
        }
    }

    // -------------------------------------------------------------------------
    // Property calculations (exercising the orchestration path)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Property Calculations")
    class PropertyCalculationsTests {

        @Test
        @DisplayName("calculateMolecularWeight should return a realistic value")
        void shouldCalculateMolecularWeight() {
            frozenModel.setLoxRp1Composition(2.5);
            assertThat(frozenModel.calculateMolecularWeight()).isBetween(15.0, 35.0);
        }

        @Test
        @DisplayName("calculateCp should return a value above 1000 J/(kg·K)")
        void shouldCalculateCp() {
            frozenModel.setLoxRp1Composition(2.5);
            assertThat(frozenModel.calculateCp(3000)).isGreaterThan(1000);
        }

        @Test
        @DisplayName("calculateGamma should be in the physical range")
        void shouldCalculateGamma() {
            frozenModel.setLoxRp1Composition(2.5);
            assertThat(frozenModel.calculateGamma(3000)).isBetween(1.1, 1.5);
        }

        @Test
        @DisplayName("getEffectiveProperties should return properties with gamma in physical range")
        void shouldGetEffectiveProperties() {
            frozenModel.setLoxRp1Composition(2.5);
            assertThat(frozenModel.getEffectiveProperties(3000).gamma()).isBetween(1.1, 1.5);
        }
    }

    // -------------------------------------------------------------------------
    // Fallback behaviour when composition is absent or unknown
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Property Fallback Branch Coverage")
    class PropertyFallbackTests {

        @Test
        @DisplayName("calculateMolecularWeight returns base value when no fractions are set")
        void molecularWeightEmptyFractions() {
            assertThat(frozenModel.calculateMolecularWeight())
                    .isEqualTo(GasProperties.LOX_RP1_PRODUCTS.molecularWeight());
        }

        @Test
        @DisplayName("calculateCp returns base value when no fractions are set")
        void cpEmptyFractions() {
            assertThat(frozenModel.calculateCp(3000))
                    .isEqualTo(GasProperties.LOX_RP1_PRODUCTS.specificHeatCp());
        }

        @Test
        @DisplayName("calculateMolecularWeight falls back when all species are unknown")
        void molecularWeightUnknownSpecies() {
            frozenModel.setSpeciesMassFractions(Map.of("XYZZY_FUEL", 1.0));
            assertThat(frozenModel.calculateMolecularWeight())
                    .isEqualTo(GasProperties.LOX_RP1_PRODUCTS.molecularWeight());
        }

        @Test
        @DisplayName("calculateCp falls back when all species are unknown")
        void cpUnknownSpecies() {
            frozenModel.setSpeciesMassFractions(Map.of("XYZZY_FUEL", 1.0));
            assertThat(frozenModel.calculateCp(3000))
                    .isEqualTo(GasProperties.LOX_RP1_PRODUCTS.specificHeatCp());
        }

        @Test
        @DisplayName("getEffectiveProperties returns base properties for EQUILIBRIUM model with no fractions")
        void effectivePropertiesEquilibriumEmptyFractions() {
            assertThat(equilibriumModel.getEffectiveProperties(3000).gamma())
                    .isEqualTo(GasProperties.LOX_LH2_PRODUCTS.gamma());
        }

        @Test
        @DisplayName("getEffectiveProperties computes effective properties for EQUILIBRIUM model with fractions set")
        void effectivePropertiesEquilibriumWithFractions() {
            equilibriumModel.setLoxLh2Composition(6.0);
            assertThat(equilibriumModel.getEffectiveProperties(3000).gamma()).isBetween(1.1, 1.5);
        }
    }

    // -------------------------------------------------------------------------
    // ModelType guard in calculateEquilibrium()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Equilibrium Guard Tests")
    class EquilibriumGuardTests {

        @Test
        @DisplayName("FROZEN model should not change composition on calculateEquilibrium()")
        void frozenModelShouldNotChangeOnEquilibriumCall() {
            frozenModel.setLoxRp1Composition(2.5);
            Map<String, Double> before = Map.copyOf(frozenModel.getSpeciesMassFractions());

            frozenModel.calculateEquilibrium(3500, 7e6);

            assertThat(frozenModel.getSpeciesMassFractions()).isEqualTo(before);
        }

        @Test
        @DisplayName("FINITE_RATE model should not change composition on calculateEquilibrium()")
        void finiteRateModelShouldNotChangeOnEquilibriumCall() {
            ChemistryModel finiteRate = new ChemistryModel(
                    ChemistryModel.ModelType.FINITE_RATE, GasProperties.LOX_LH2_PRODUCTS);
            finiteRate.setLoxLh2Composition(6.0);
            Map<String, Double> before = Map.copyOf(finiteRate.getSpeciesMassFractions());

            finiteRate.calculateEquilibrium(3200, 7e6);

            assertThat(finiteRate.getSpeciesMassFractions()).isEqualTo(before);
        }

        @Test
        @DisplayName("EQUILIBRIUM model should change composition on calculateEquilibrium()")
        void equilibriumModelShouldChangeComposition() {
            equilibriumModel.setLoxLh2Composition(6.0);
            Map<String, Double> before = Map.copyOf(equilibriumModel.getSpeciesMassFractions());

            equilibriumModel.calculateEquilibrium(3200, 7e6);

            Map<String, Double> after = equilibriumModel.getSpeciesMassFractions();
            assertThat(after).isNotEmpty();
            assertThat(after).isNotEqualTo(before);
            assertThat(after.values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
        }
    }
}
