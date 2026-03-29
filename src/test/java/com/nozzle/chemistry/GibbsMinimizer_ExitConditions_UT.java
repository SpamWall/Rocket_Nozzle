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
 * Validates {@link GibbsMinimizer} at nozzle-exit conditions (1000–2000 K, 0.1 MPa).
 *
 * <p>At these temperatures the equilibrium strongly favors stable combustion products.
 * The behaviour is driven by two distinct effects depending on propellant:
 * <ul>
 *   <li><b>LOX/LH2</b>: dissociation is negligible at both temperatures; H₂O and H₂
 *       fractions are essentially frozen at the 1000 K values up to 2000 K.</li>
 *   <li><b>LOX/CH4 and LOX/RP-1</b>: the water-gas-shift equilibrium
 *       CO + H₂O ⇌ CO₂ + H₂ controls the CO/CO₂ split. At 1000 K the equilibrium
 *       constant Kp ≈ 1.6, so a significant CO fraction persists; at 2000 K Kp falls
 *       further, shifting the balance toward CO.</li>
 * </ul>
 *
 * <h2>Reference values</h2>
 *
 * <p>Reference values are NASA CEA outputs verified using the {@code tp} input decks
 * documented in each nested class.
 * Tolerances follow the same scheme as {@link GibbsMinimizer_CEAValidation_UT}:
 * ±3 % relative for major species (mass fraction &gt; 0.10),
 * ±10 % relative for intermediate species (0.005–0.10), and ±0.002 absolute for
 * minor species (&lt; 0.005).
 */
@DisplayName("GibbsMinimizer — nozzle-exit conditions (1000–2000 K, 0.1 MPa)")
class GibbsMinimizer_ExitConditions_UT {

    private GibbsMinimizer minimizer;

    @BeforeEach
    void setUp() {
        minimizer = new GibbsMinimizer(new NasaSpeciesDatabase());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Map<String, Double> lh2(double of) {
        PropellantComposition c = new PropellantComposition(); c.setLoxLh2(of); return c.get();
    }
    private Map<String, Double> ch4(double of) {
        PropellantComposition c = new PropellantComposition(); c.setLoxCh4(of); return c.get();
    }
    private Map<String, Double> rp1(double of) {
        PropellantComposition c = new PropellantComposition(); c.setLoxRp1(of); return c.get();
    }

    // =======================================================================
    //  LOX / LH2   —   O/F = 6.0,  P = 0.1 MPa
    // =======================================================================

    /**
     * CEA tp input deck:
     * <pre>
     * prob case = tp
     * p,bar= 1
     * t,k= 1000
     * o/f = 6
     * reac
     * fuel H2(L)  wt%=100.0
     * oxid O2(L)  wt%=100.0
     * output short mass fractions SI Units
     * end
     * </pre>
     *
     * <p>At 1000 K dissociation is negligible. H₂O and H₂ fractions are within
     * 0.03 % of the undissociated equilibrium limit (H₂O = 0.9649, H₂ = 0.0351).
     * No radical species appear above 1 × 10⁻⁶ g/g.
     */
    @Nested
    @DisplayName("LOX/LH2  O/F=6.0  T=1000 K  P=0.1 MPa")
    class LoxLh2_OF6_1000K_0p1MPa {

        // NASA CEA reference values.
        private static final double REF_H2O = 0.96514;
        private static final double REF_H2  = 0.03486;

        private Map<String, Double> result;

        @BeforeEach
        void solve() { result = minimizer.minimize(lh2(6.0), 1000, 1e5); }

        @Test
        @DisplayName("H2O is the dominant species at the undissociated limit")
        void h2oDominant() {
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isCloseTo(REF_H2O, within(REF_H2O * 0.03));
        }

        @Test
        @DisplayName("H2 matches undissociated limit (no excess burned to H2O)")
        void h2Present() {
            assertThat(result.getOrDefault("H2", 0.0))
                    .isCloseTo(REF_H2, within(REF_H2 * 0.10));
        }

        @Test
        @DisplayName("OH is absent (no dissociation at 1000 K)")
        void ohAbsent() {
            assertThat(result.getOrDefault("OH", 0.0)).isLessThan(0.002);
        }

        @Test
        @DisplayName("H is absent (no dissociation at 1000 K)")
        void hAbsent() {
            assertThat(result.getOrDefault("H", 0.0)).isLessThan(0.002);
        }

        @Test
        @DisplayName("H2O at 1000 K is greater than at 3200 K (less dissociation)")
        void h2oGreaterThanAt3200K() {
            Map<String, Double> hot = minimizer.minimize(lh2(6.0), 3200, 1e5);
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isGreaterThan(hot.getOrDefault("H2O", 0.0));
        }
    }

    /**
     * CEA tp input deck:
     * <pre>
     * prob case = tp
     * p,bar= 1
     * t,k= 2000
     * o/f = 6
     * reac
     * fuel H2(L)  wt%=100.0
     * oxid O2(L)  wt%=100.0
     * output short mass fractions SI Units
     * end
     * </pre>
     *
     * <p>At 2000 K dissociation remains negligible for LOX/LH2. The H₂O fraction drops
     * by less than 0.04 % compared to the 1000 K case; OH peaks at roughly 0.03 % g/g.
     */
    @Nested
    @DisplayName("LOX/LH2  O/F=6.0  T=2000 K  P=0.1 MPa")
    class LoxLh2_OF6_2000K_0p1MPa {

        // NASA CEA reference values.
        private static final double REF_H2O = 0.96478;
        private static final double REF_H2  = 0.03482;

        private Map<String, Double> result;

        @BeforeEach
        void solve() { result = minimizer.minimize(lh2(6.0), 2000, 1e5); }

        @Test
        @DisplayName("H2O is still dominant (dissociation negligible at 2000 K)")
        void h2oDominant() {
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isCloseTo(REF_H2O, within(REF_H2O * 0.03));
        }

        @Test
        @DisplayName("H2 fraction is unchanged from 1000 K within tolerance")
        void h2Present() {
            assertThat(result.getOrDefault("H2", 0.0))
                    .isCloseTo(REF_H2, within(REF_H2 * 0.10));
        }

        @Test
        @DisplayName("OH is trace only (< 0.002 g/g)")
        void ohTrace() {
            assertThat(result.getOrDefault("OH", 0.0)).isLessThan(0.002);
        }

        @Test
        @DisplayName("H2O at 2000 K is less than at 1000 K (slight dissociation increase)")
        void h2oLessThanAt1000K() {
            Map<String, Double> cold = minimizer.minimize(lh2(6.0), 1000, 1e5);
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isLessThan(cold.getOrDefault("H2O", 0.0));
        }
    }

    // =======================================================================
    //  LOX / CH4   —   O/F = 3.5,  P = 0.1 MPa
    // =======================================================================

    /**
     * CEA tp input deck:
     * <pre>
     * prob case = tp
     * p,bar= 1
     * t,k= 1000
     * o/f = 3.5
     * reac
     * fuel CH4  wt%=100.0
     * oxid O2(L)  wt%=100.0
     * output short mass fractions SI Units
     * end
     * </pre>
     *
     * <p>At 1000 K the water-gas-shift equilibrium constant Kp ≈ 1.6, so a significant
     * CO fraction (≈ 5.1 %) persists alongside CO₂ and H₂O. No radical species
     * appear above 1 × 10⁻⁶ g/g.
     */
    @Nested
    @DisplayName("LOX/CH4  O/F=3.5  T=1000 K  P=0.1 MPa")
    class LoxCh4_OF3p5_1000K_0p1MPa {

        // NASA CEA reference values.
        private static final double REF_CO2 = 0.52907;
        private static final double REF_H2O = 0.40966;
        private static final double REF_CO  = 0.05127;
        private static final double REF_H2  = 0.01001;

        private Map<String, Double> result;

        @BeforeEach
        void solve() { result = minimizer.minimize(ch4(3.5), 1000, 1e5); }

        @Test
        @DisplayName("CO2 is the dominant species (water-gas shift at 1000 K)")
        void co2Dominant() {
            assertThat(result.getOrDefault("CO2", 0.0))
                    .isCloseTo(REF_CO2, within(REF_CO2 * 0.03));
        }

        @Test
        @DisplayName("H2O is the second-largest species")
        void h2oMajor() {
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isCloseTo(REF_H2O, within(REF_H2O * 0.03));
        }

        @Test
        @DisplayName("CO is present (water-gas shift not complete at 1000 K)")
        void coPresent() {
            assertThat(result.getOrDefault("CO", 0.0))
                    .isCloseTo(REF_CO, within(REF_CO * 0.10));
        }

        @Test
        @DisplayName("H2 is present (water-gas shift product)")
        void h2Present() {
            assertThat(result.getOrDefault("H2", 0.0))
                    .isCloseTo(REF_H2, within(REF_H2 * 0.10));
        }

        @Test
        @DisplayName("OH is absent (no dissociation at 1000 K)")
        void ohAbsent() {
            assertThat(result.getOrDefault("OH", 0.0)).isLessThan(0.002);
        }

        @Test
        @DisplayName("CO at 1000 K is less than at 2000 K (Kp falls as T rises)")
        void coLessThanAt2000K() {
            Map<String, Double> hot = minimizer.minimize(ch4(3.5), 2000, 1e5);
            assertThat(result.getOrDefault("CO", 0.0))
                    .isLessThan(hot.getOrDefault("CO", 0.0));
        }
    }

    /**
     * CEA tp input deck:
     * <pre>
     * prob case = tp
     * p,bar= 1
     * t,k= 2000
     * o/f = 3.5
     * reac
     * fuel CH4  wt%=100.0
     * oxid O2(L)  wt%=100.0
     * output short mass fractions SI Units
     * end
     * </pre>
     *
     * <p>At 2000 K Kp for the water-gas shift has fallen further, shifting the balance
     * toward CO. CO rises from ≈ 5.1 % at 1000 K to ≈ 12.1 % at 2000 K, while CO₂
     * falls from ≈ 52.9 % to ≈ 42.0 %.
     */
    @Nested
    @DisplayName("LOX/CH4  O/F=3.5  T=2000 K  P=0.1 MPa")
    class LoxCh4_OF3p5_2000K_0p1MPa {

        // NASA CEA reference values.
        private static final double REF_CO2 = 0.41988;
        private static final double REF_H2O = 0.45399;
        private static final double REF_CO  = 0.12077;
        private static final double REF_H2  = 0.00501;

        private Map<String, Double> result;

        @BeforeEach
        void solve() { result = minimizer.minimize(ch4(3.5), 2000, 1e5); }

        @Test
        @DisplayName("CO2 is still a major product at 2000 K")
        void co2Major() {
            assertThat(result.getOrDefault("CO2", 0.0))
                    .isCloseTo(REF_CO2, within(REF_CO2 * 0.03));
        }

        @Test
        @DisplayName("H2O is the dominant species at 2000 K")
        void h2oMajor() {
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isCloseTo(REF_H2O, within(REF_H2O * 0.03));
        }

        @Test
        @DisplayName("CO is a major product at 2000 K (water-gas shift reversed)")
        void coMajor() {
            assertThat(result.getOrDefault("CO", 0.0))
                    .isCloseTo(REF_CO, within(REF_CO * 0.03));
        }

        @Test
        @DisplayName("H2 is minor at 2000 K")
        void h2Minor() {
            assertThat(result.getOrDefault("H2", 0.0))
                    .isCloseTo(REF_H2, within(REF_H2 * 0.10));
        }

        @Test
        @DisplayName("CO2 at 2000 K is less than at 1000 K (water-gas shift reversal)")
        void co2LessThanAt1000K() {
            Map<String, Double> cold = minimizer.minimize(ch4(3.5), 1000, 1e5);
            assertThat(result.getOrDefault("CO2", 0.0))
                    .isLessThan(cold.getOrDefault("CO2", 0.0));
        }
    }

    // =======================================================================
    //  LOX / RP-1   —   O/F = 2.77,  P = 0.1 MPa
    //  RP-1 modelled as C12H23.4, MW = 167.587 g/mol
    // =======================================================================

    /**
     * CEA tp input deck:
     * <pre>
     * prob case = tp
     * p,bar= 1
     * t,k= 1000
     * o/f = 2.77
     * reac
     * fuel C12H26  wt%=100.0  t(k)=300
     * oxid O2(L)   wt%=100.0
     * output short mass fractions SI Units
     * end
     * </pre>
     *
     * <p>RP-1 is carbon-heavy (C12H23.4), so CO₂ is the largest fraction at all
     * exit temperatures. At 1000 K Kp ≈ 1.6 leaves CO ≈ 13.0 % alongside CO₂ ≈ 63.0 %.
     * No radical species appear above 1 × 10⁻⁶ g/g.
     *
     * <p>Note: CEA uses "C12H26" as a close RP-1 surrogate; the solver uses C12H23.4.
     * A small systematic offset in the C/H ratio is expected.
     */
    @Nested
    @DisplayName("LOX/RP-1  O/F=2.77  T=1000 K  P=0.1 MPa")
    class LoxRp1_OF2p77_1000K_0p1MPa {

        // NASA CEA reference values.
        private static final double REF_CO2 = 0.63042;
        private static final double REF_CO  = 0.13031;
        private static final double REF_H2O = 0.22739;
        private static final double REF_H2  = 0.01185;

        private Map<String, Double> result;

        @BeforeEach
        void solve() { result = minimizer.minimize(rp1(2.77), 1000, 1e5); }

        @Test
        @DisplayName("CO2 is the dominant species")
        void co2Dominant() {
            assertThat(result.getOrDefault("CO2", 0.0))
                    .isCloseTo(REF_CO2, within(REF_CO2 * 0.03));
        }

        @Test
        @DisplayName("CO is present (water-gas shift not complete at 1000 K)")
        void coPresent() {
            // ±5 % rather than ±3 % because the C12H23.4 vs C12H26 surrogate
            // difference shifts the C/H ratio and the water-gas shift equilibrium.
            assertThat(result.getOrDefault("CO", 0.0))
                    .isCloseTo(REF_CO, within(REF_CO * 0.05));
        }

        @Test
        @DisplayName("H2O is present")
        void h2oPresent() {
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isCloseTo(REF_H2O, within(REF_H2O * 0.03));
        }

        @Test
        @DisplayName("H2 is present (water-gas shift product)")
        void h2Present() {
            assertThat(result.getOrDefault("H2", 0.0))
                    .isCloseTo(REF_H2, within(REF_H2 * 0.10));
        }

        @Test
        @DisplayName("OH is absent (no dissociation at 1000 K)")
        void ohAbsent() {
            assertThat(result.getOrDefault("OH", 0.0)).isLessThan(0.002);
        }

        @Test
        @DisplayName("CO at 1000 K is less than at 2000 K (Kp falls as T rises)")
        void coLessThanAt2000K() {
            Map<String, Double> hot = minimizer.minimize(rp1(2.77), 2000, 1e5);
            assertThat(result.getOrDefault("CO", 0.0))
                    .isLessThan(hot.getOrDefault("CO", 0.0));
        }
    }

    /**
     * CEA tp input deck:
     * <pre>
     * prob case = tp
     * p,bar= 1
     * t,k= 2000
     * o/f = 2.77
     * reac
     * fuel C12H26  wt%=100.0  t(k)=300
     * oxid O2(L)   wt%=100.0
     * output short mass fractions SI Units
     * end
     * </pre>
     *
     * <p>At 2000 K CO rises to ≈ 22.3 % as the water-gas-shift Kp falls. CO₂ drops from
     * ≈ 63.0 % to ≈ 48.3 %. H₂O rises slightly due to H₂ being consumed by the shift
     * reaction in the reverse direction.
     */
    @Nested
    @DisplayName("LOX/RP-1  O/F=2.77  T=2000 K  P=0.1 MPa")
    class LoxRp1_OF2p77_2000K_0p1MPa {

        // NASA CEA reference values.
        private static final double REF_CO2 = 0.48321;
        private static final double REF_CO  = 0.22406;
        private static final double REF_H2O = 0.28741;
        private static final double REF_H2  = 0.00511;

        private Map<String, Double> result;

        @BeforeEach
        void solve() { result = minimizer.minimize(rp1(2.77), 2000, 1e5); }

        @Test
        @DisplayName("CO2 is still the dominant species at 2000 K")
        void co2Dominant() {
            assertThat(result.getOrDefault("CO2", 0.0))
                    .isCloseTo(REF_CO2, within(REF_CO2 * 0.03));
        }

        @Test
        @DisplayName("CO is a major product at 2000 K (water-gas shift reversal)")
        void coMajor() {
            assertThat(result.getOrDefault("CO", 0.0))
                    .isCloseTo(REF_CO, within(REF_CO * 0.03));
        }

        @Test
        @DisplayName("H2O is a major product at 2000 K")
        void h2oMajor() {
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isCloseTo(REF_H2O, within(REF_H2O * 0.03));
        }

        @Test
        @DisplayName("H2 is minor at 2000 K")
        void h2Minor() {
            assertThat(result.getOrDefault("H2", 0.0))
                    .isCloseTo(REF_H2, within(REF_H2 * 0.10));
        }

        @Test
        @DisplayName("CO2 at 2000 K is less than at 1000 K (water-gas shift reversal)")
        void co2LessThanAt1000K() {
            Map<String, Double> cold = minimizer.minimize(rp1(2.77), 1000, 1e5);
            assertThat(result.getOrDefault("CO2", 0.0))
                    .isLessThan(cold.getOrDefault("CO2", 0.0));
        }
    }
}
