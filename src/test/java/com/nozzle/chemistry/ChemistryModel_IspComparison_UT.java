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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Validates {@link ChemistryModel#compareIsp}, {@link ChemistryModel#calculateFrozenIsp},
 * and {@link ChemistryModel#calculateEquilibriumIsp}.
 *
 * <h2>Physics background</h2>
 *
 * <p>Equilibrium Isp exceeds frozen Isp because dissociation products that absorb
 * energy at the high-temperature chamber conditions recombine during nozzle expansion
 * and release that energy as additional kinetic energy of the exhaust.  The frozen
 * model ignores this recombination; the equilibrium model captures it through the
 * equilibrium isentropic exponent γ_s, which is lower than the frozen γ for dissociating
 * mixtures.  A lower γ_s means a longer effective expansion and higher exit velocity.
 *
 * <p>The delta is largest for propellants with high dissociation fractions:
 * LOX/LH₂ at high temperature produces significant H and OH, giving a delta of
 * roughly 2–5%.  N₂O propellants carry a large N₂ inert fraction that suppresses
 * dissociation in the C/H/O sub-system, making their delta smaller (typically < 2%).
 *
 * <h2>Test strategy</h2>
 *
 * <ul>
 *   <li>Physics invariant: equilibriumIsp ≥ frozenIsp for all physical conditions.</li>
 *   <li>Non-triviality: all four built-in propellants show a delta greater than 1 s
 *       at 3500 K, confirming the equilibrium correction is physically meaningful.</li>
 *   <li>Temperature sensitivity: higher Tc → more dissociation → larger delta.</li>
 *   <li>Consistency: {@code compareIsp} produces the same values as separate
 *       {@code calculateFrozenIsp} / {@code calculateEquilibriumIsp} calls.</li>
 *   <li>Record contract: {@code delta()} and {@code deltaPercent()} derived correctly.</li>
 * </ul>
 */
@DisplayName("ChemistryModel — frozen vs. equilibrium Isp comparison")
class ChemistryModel_IspComparison_UT {

    // Shared operating conditions used across most tests
    private static final double TC = 3500.0;    // K
    private static final double PC = 7e6;       // Pa
    private static final double ME = 3.0;       // exit Mach
    private static final double PA = 101_325.0; // Pa (sea level)

    // -----------------------------------------------------------------------
    // Physics invariant — equilibrium Isp ≥ frozen Isp
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Physics invariant — equilibrium Isp ≥ frozen Isp")
    class PhysicsInvariantTests {

        @Test
        @DisplayName("LOX/LH2 equilibrium Isp exceeds frozen Isp")
        void loxLh2EquilibriumExceedsFrozen() {
            ChemistryModel model = ChemistryModel.equilibrium(GasProperties.LOX_LH2_PRODUCTS);
            model.setLoxLh2Composition(6.0);
            model.calculateEquilibrium(TC, PC);
            assertThat(model.calculateEquilibriumIsp(TC, PC, ME, PA))
                    .isGreaterThan(model.calculateFrozenIsp(TC, PC, ME, PA));
        }

        @Test
        @DisplayName("LOX/RP-1 equilibrium Isp exceeds frozen Isp")
        void loxRp1EquilibriumExceedsFrozen() {
            ChemistryModel model = ChemistryModel.equilibrium(GasProperties.LOX_RP1_PRODUCTS);
            model.setLoxRp1Composition(2.77);
            model.calculateEquilibrium(TC, PC);
            assertThat(model.calculateEquilibriumIsp(TC, PC, ME, PA))
                    .isGreaterThan(model.calculateFrozenIsp(TC, PC, ME, PA));
        }

        @Test
        @DisplayName("LOX/CH4 equilibrium Isp exceeds frozen Isp")
        void loxCh4EquilibriumExceedsFrozen() {
            ChemistryModel model = ChemistryModel.equilibrium(GasProperties.LOX_CH4_PRODUCTS);
            model.setLoxCh4Composition(3.5);
            model.calculateEquilibrium(TC, PC);
            assertThat(model.calculateEquilibriumIsp(TC, PC, ME, PA))
                    .isGreaterThan(model.calculateFrozenIsp(TC, PC, ME, PA));
        }

        @Test
        @DisplayName("N2O/Ethanol equilibrium Isp exceeds frozen Isp")
        void n2oEthanolEquilibriumExceedsFrozen() {
            ChemistryModel model = ChemistryModel.equilibrium(GasProperties.N2O_ETHANOL_PRODUCTS);
            model.setN2oEthanolComposition(5.0);
            model.calculateEquilibrium(TC, PC);
            assertThat(model.calculateEquilibriumIsp(TC, PC, ME, PA))
                    .isGreaterThan(model.calculateFrozenIsp(TC, PC, ME, PA));
        }
    }

    // -----------------------------------------------------------------------
    // Magnitude — LOX/LH2 at high T has the largest dissociation delta
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Isp delta magnitude — LOX/LH2")
    class LoxLh2DeltaTests {

        private ChemistryModel model;

        @BeforeEach
        void setUp() {
            model = ChemistryModel.equilibrium(GasProperties.LOX_LH2_PRODUCTS);
            model.setLoxLh2Composition(6.0);
            model.calculateEquilibrium(TC, PC);
        }

        @Test
        @DisplayName("Frozen Isp is in the physically expected range (300–450 s)")
        void frozenIspInExpectedRange() {
            assertThat(model.calculateFrozenIsp(TC, PC, ME, PA))
                    .isBetween(300.0, 450.0);
        }

        @Test
        @DisplayName("Equilibrium Isp is in the physically expected range (300–450 s)")
        void equilibriumIspInExpectedRange() {
            assertThat(model.calculateEquilibriumIsp(TC, PC, ME, PA))
                    .isBetween(300.0, 450.0);
        }

        @Test
        @DisplayName("Delta is positive and non-trivial (> 1 s) for LOX/LH2 at 3500 K")
        void deltaIsNonTrivial() {
            ChemistryModel.IspComparison cmp = model.compareIsp(TC, PC, ME, PA);
            assertThat(cmp.delta()).isGreaterThan(1.0);
        }

        @Test
        @DisplayName("Delta percent is between 0.5% and 10% for LOX/LH2 at 3500 K")
        void deltaPercentInExpectedRange() {
            ChemistryModel.IspComparison cmp = model.compareIsp(TC, PC, ME, PA);
            assertThat(cmp.deltaPercent()).isBetween(0.5, 10.0);
        }
    }

    // -----------------------------------------------------------------------
    // Multiple propellants — delta is non-trivial and positive for all of them
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Non-trivial delta across propellant families")
    class DeltaAcrossPropellantsTests {

        @Test
        @DisplayName("LOX/LH2 delta exceeds 1 s at 3500 K (high H dissociation)")
        void loxLh2DeltaNonTrivial() {
            ChemistryModel model = ChemistryModel.equilibrium(GasProperties.LOX_LH2_PRODUCTS);
            model.setLoxLh2Composition(6.0);
            model.calculateEquilibrium(TC, PC);
            assertThat(model.compareIsp(TC, PC, ME, PA).delta()).isGreaterThan(1.0);
        }

        @Test
        @DisplayName("LOX/RP-1 delta exceeds 1 s at 3500 K (C/H/O dissociation)")
        void loxRp1DeltaNonTrivial() {
            ChemistryModel model = ChemistryModel.equilibrium(GasProperties.LOX_RP1_PRODUCTS);
            model.setLoxRp1Composition(2.77);
            model.calculateEquilibrium(TC, PC);
            assertThat(model.compareIsp(TC, PC, ME, PA).delta()).isGreaterThan(1.0);
        }

        @Test
        @DisplayName("N2O/Ethanol delta exceeds 1 s at 3500 K (C/H/O sub-system dissociates)")
        void n2oEthanolDeltaNonTrivial() {
            ChemistryModel model = ChemistryModel.equilibrium(GasProperties.N2O_ETHANOL_PRODUCTS);
            model.setN2oEthanolComposition(5.0);
            model.calculateEquilibrium(TC, PC);
            assertThat(model.compareIsp(TC, PC, ME, PA).delta()).isGreaterThan(1.0);
        }

        @Test
        @DisplayName("N2O/Propane delta exceeds 1 s at 3500 K")
        void n2oPropaneDeltaNonTrivial() {
            ChemistryModel model = ChemistryModel.equilibrium(GasProperties.N2O_PROPANE_PRODUCTS);
            model.setN2oPropaneComposition(7.5);
            model.calculateEquilibrium(TC, PC);
            assertThat(model.compareIsp(TC, PC, ME, PA).delta()).isGreaterThan(1.0);
        }
    }

    // -----------------------------------------------------------------------
    // Temperature sensitivity — higher Tc → larger delta
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Temperature sensitivity of Isp delta")
    class TemperatureSensitivityTests {

        @ParameterizedTest(name = "delta is positive at Tc={0} K")
        @ValueSource(doubles = {2800.0, 3000.0, 3200.0, 3500.0})
        @DisplayName("LOX/LH2 equilibrium Isp exceeds frozen Isp across temperature range")
        void deltaPositiveAcrossTemperatures(double tc) {
            ChemistryModel model = ChemistryModel.equilibrium(GasProperties.LOX_LH2_PRODUCTS);
            model.setLoxLh2Composition(6.0);
            model.calculateEquilibrium(tc, PC);
            assertThat(model.compareIsp(tc, PC, ME, PA).delta()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("LOX/LH2 delta grows from 3000 K to 3500 K (more dissociation at higher T)")
        void deltaGrowsWithTemperature() {
            ChemistryModel at3000 = ChemistryModel.equilibrium(GasProperties.LOX_LH2_PRODUCTS);
            at3000.setLoxLh2Composition(6.0);
            at3000.calculateEquilibrium(3000, PC);

            ChemistryModel at3500 = ChemistryModel.equilibrium(GasProperties.LOX_LH2_PRODUCTS);
            at3500.setLoxLh2Composition(6.0);
            at3500.calculateEquilibrium(3500, PC);

            assertThat(at3500.compareIsp(3500, PC, ME, PA).delta())
                    .isGreaterThan(at3000.compareIsp(3000, PC, ME, PA).delta());
        }
    }

    // -----------------------------------------------------------------------
    // Consistency — compareIsp matches separate method calls
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("compareIsp consistency with individual methods")
    class ConsistencyTests {

        @Test
        @DisplayName("compareIsp frozenIsp matches calculateFrozenIsp to 1e-9")
        void compareIspFrozenMatchesSeparateCall() {
            ChemistryModel model = ChemistryModel.equilibrium(GasProperties.LOX_LH2_PRODUCTS);
            model.setLoxLh2Composition(6.0);
            model.calculateEquilibrium(TC, PC);

            double separate = model.calculateFrozenIsp(TC, PC, ME, PA);
            ChemistryModel.IspComparison cmp = model.compareIsp(TC, PC, ME, PA);
            assertThat(cmp.frozenIsp()).isCloseTo(separate, within(1e-9));
        }

        @Test
        @DisplayName("compareIsp equilibriumIsp matches calculateEquilibriumIsp to 1e-9")
        void compareIspEquilibriumMatchesSeparateCall() {
            ChemistryModel model = ChemistryModel.equilibrium(GasProperties.LOX_LH2_PRODUCTS);
            model.setLoxLh2Composition(6.0);
            model.calculateEquilibrium(TC, PC);

            double separate = model.calculateEquilibriumIsp(TC, PC, ME, PA);
            ChemistryModel.IspComparison cmp = model.compareIsp(TC, PC, ME, PA);
            assertThat(cmp.equilibriumIsp()).isCloseTo(separate, within(1e-9));
        }
    }

    // -----------------------------------------------------------------------
    // IspComparison record contract
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("IspComparison record contract")
    class IspComparisonRecordTests {

        @Test
        @DisplayName("delta() equals equilibriumIsp minus frozenIsp")
        void deltaEqualsEquilibriumMinusFrozen() {
            ChemistryModel.IspComparison cmp = new ChemistryModel.IspComparison(350.0, 355.0);
            assertThat(cmp.delta()).isCloseTo(5.0, within(1e-12));
        }

        @Test
        @DisplayName("deltaPercent() equals 100 × delta / frozenIsp")
        void deltaPercentFormula() {
            ChemistryModel.IspComparison cmp = new ChemistryModel.IspComparison(350.0, 357.0);
            double expected = 100.0 * 7.0 / 350.0;
            assertThat(cmp.deltaPercent()).isCloseTo(expected, within(1e-12));
        }

        @Test
        @DisplayName("delta() is zero when frozen and equilibrium Isp are equal")
        void deltaZeroWhenEqual() {
            ChemistryModel.IspComparison cmp = new ChemistryModel.IspComparison(350.0, 350.0);
            assertThat(cmp.delta()).isCloseTo(0.0, within(1e-12));
            assertThat(cmp.deltaPercent()).isCloseTo(0.0, within(1e-12));
        }

        @Test
        @DisplayName("deltaPercent() returns 0 when frozenIsp is zero (division guard)")
        void deltaPercentZeroWhenFrozenIspIsZero() {
            ChemistryModel.IspComparison cmp = new ChemistryModel.IspComparison(0.0, 10.0);
            assertThat(cmp.deltaPercent()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("deltaPercent() returns 0 when frozenIsp is negative (guard branch)")
        void deltaPercentZeroWhenFrozenIspIsNegative() {
            ChemistryModel.IspComparison cmp = new ChemistryModel.IspComparison(-1.0, 10.0);
            assertThat(cmp.deltaPercent()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("deltaPercent() scales linearly with delta — doubling the gain doubles the percentage")
        void deltaPercentScalesLinearlyWithDelta() {
            ChemistryModel.IspComparison single = new ChemistryModel.IspComparison(350.0, 354.0);
            ChemistryModel.IspComparison doubled = new ChemistryModel.IspComparison(350.0, 358.0);
            assertThat(doubled.deltaPercent())
                    .isCloseTo(2.0 * single.deltaPercent(), within(1e-12));
        }

        @Test
        @DisplayName("deltaPercent() equals delta() / frozenIsp * 100 for arbitrary values")
        void deltaPercentConsistentWithDelta() {
            ChemistryModel.IspComparison cmp = new ChemistryModel.IspComparison(400.0, 412.0);
            assertThat(cmp.deltaPercent())
                    .isCloseTo(100.0 * cmp.delta() / cmp.frozenIsp(), within(1e-12));
        }

        @Test
        @DisplayName("Record equality: two identical instances are equal")
        void recordEquality() {
            ChemistryModel.IspComparison a = new ChemistryModel.IspComparison(350.0, 355.0);
            ChemistryModel.IspComparison b = new ChemistryModel.IspComparison(350.0, 355.0);
            assertThat(a).isEqualTo(b);
        }
    }
}
