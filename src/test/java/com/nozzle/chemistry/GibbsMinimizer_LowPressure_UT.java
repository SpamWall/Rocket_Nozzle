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
 * Validates {@link GibbsMinimizer} at low chamber pressures (0.5 MPa and 2.0 MPa),
 * typical of upper-stage engines and thrusters.
 *
 * <p>The chemical potential of each species contains a pressure term
 * {@code ln(P / P_ref)}. At lower pressures this term drives the equilibrium toward
 * products with more moles (dissociation products: OH, H, O, O₂, CO from CO₂),
 * consistent with Le Chatelier's principle. Any bug in the pressure term of the
 * solver would produce incorrect species fractions that grow in magnitude as P falls.
 *
 * <p>At 0.5 MPa compared to 7 MPa (same temperature):
 * <ul>
 *   <li>H₂O decreases by several percent absolute as OH, H, and O grow.</li>
 *   <li>For carbon propellants, CO increases and CO₂ decreases as the
 *       CO₂ ⇌ CO + ½O₂ dissociation is favored at lower pressure.</li>
 * </ul>
 *
 * <h2>Reference values</h2>
 *
 * <p>Reference values are NASA CEA outputs from the {@code tp} input decks documented
 * in each nested class.
 * Tolerances follow the same scheme as {@link GibbsMinimizer_CEAValidation_UT}:
 * ±3 % relative for major species (mass fraction &gt; 0.10),
 * ±10 % relative for intermediate species (0.005–0.10), and ±0.002 absolute for
 * minor species (&lt; 0.005).
 */
@DisplayName("GibbsMinimizer — low chamber pressure (0.5 MPa and 2.0 MPa)")
class GibbsMinimizer_LowPressure_UT {

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
    //  LOX / LH2   —   O/F = 6.0,  T = 3200 K
    // =======================================================================

    /**
     * CEA tp input deck:
     * <pre>
     * prob case = tp
     * p,bar= 5
     * t,k= 3200
     * o/f = 6
     * reac
     * fuel H2(L)  wt%=100.0
     * oxid O2(L)  wt%=100.0
     * output short mass fractions SI Units
     * end
     * </pre>
     *
     * <p>At 0.5 MPa dissociation is significantly stronger than at 7 MPa. H₂O drops
     * from ≈ 93 % to ≈ 85 %, while OH rises to ≈ 7.8 % and O appears at ≈ 1.1 %.
     */
    @Nested
    @DisplayName("LOX/LH2  O/F=6.0  T=3200 K  P=0.5 MPa")
    class LoxLh2_OF6_3200K_0p5MPa {

        // NASA CEA reference values (tp deck above).
        private static final double REF_H2O = 0.84211;
        private static final double REF_OH  = 0.08272;
        private static final double REF_O2  = 0.02055;
        private static final double REF_O   = 0.01084;
        private static final double REF_H2  = 0.03883;

        private Map<String, Double> result;

        @BeforeEach
        void solve() { result = minimizer.minimize(lh2(6.0), 3200, 0.5e6); }

        @Test
        @DisplayName("H2O is still the dominant species despite dissociation")
        void h2oMajor() {
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isCloseTo(REF_H2O, within(REF_H2O * 0.03));
        }

        @Test
        @DisplayName("OH is a major dissociation product at 0.5 MPa")
        void ohMajor() {
            assertThat(result.getOrDefault("OH", 0.0))
                    .isCloseTo(REF_OH, within(REF_OH * 0.10));
        }

        @Test
        @DisplayName("O2 is present (dissociation at low pressure)")
        void o2Present() {
            assertThat(result.getOrDefault("O2", 0.0))
                    .isCloseTo(REF_O2, within(REF_O2 * 0.10));
        }

        @Test
        @DisplayName("O is present (atomic oxygen at low pressure)")
        void oPresent() {
            assertThat(result.getOrDefault("O", 0.0))
                    .isCloseTo(REF_O, within(REF_O * 0.10));
        }

        @Test
        @DisplayName("H2 fraction is consistent with fuel-rich mixture")
        void h2Present() {
            assertThat(result.getOrDefault("H2", 0.0))
                    .isCloseTo(REF_H2, within(REF_H2 * 0.10));
        }

        @Test
        @DisplayName("H2O at 0.5 MPa is less than at 7 MPa (Le Chatelier)")
        void h2oLessThanAt7MPa() {
            Map<String, Double> highP = minimizer.minimize(lh2(6.0), 3200, 7e6);
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isLessThan(highP.getOrDefault("H2O", 0.0));
        }

        @Test
        @DisplayName("OH at 0.5 MPa is greater than at 7 MPa (Le Chatelier)")
        void ohGreaterThanAt7MPa() {
            Map<String, Double> highP = minimizer.minimize(lh2(6.0), 3200, 7e6);
            assertThat(result.getOrDefault("OH", 0.0))
                    .isGreaterThan(highP.getOrDefault("OH", 0.0));
        }
    }

    /**
     * CEA tp input deck:
     * <pre>
     * prob case = tp
     * p,bar= 20
     * t,k= 3200
     * o/f = 6
     * reac
     * fuel H2(L)  wt%=100.0
     * oxid O2(L)  wt%=100.0
     * output short mass fractions SI Units
     * end
     * </pre>
     *
     * <p>At 2.0 MPa dissociation is intermediate between 0.5 MPa and 7 MPa.
     * H₂O ≈ 91 %, OH ≈ 4.2 %. The pressure gradient from 0.5 → 2.0 → 7 MPa
     * should be monotonic for every species.
     */
    @Nested
    @DisplayName("LOX/LH2  O/F=6.0  T=3200 K  P=2.0 MPa")
    class LoxLh2_OF6_3200K_2p0MPa {

        // NASA CEA reference values (tp deck above).
        private static final double REF_H2O = 0.90678;
        private static final double REF_OH  = 0.04500;
        private static final double REF_O2  = 0.00649;
        private static final double REF_H2  = 0.03641;

        private Map<String, Double> result;

        @BeforeEach
        void solve() { result = minimizer.minimize(lh2(6.0), 3200, 2.0e6); }

        @Test
        @DisplayName("H2O is the dominant species")
        void h2oMajor() {
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isCloseTo(REF_H2O, within(REF_H2O * 0.03));
        }

        @Test
        @DisplayName("OH is present (intermediate dissociation)")
        void ohPresent() {
            assertThat(result.getOrDefault("OH", 0.0))
                    .isCloseTo(REF_OH, within(REF_OH * 0.10));
        }

        @Test
        @DisplayName("O2 is present")
        void o2Present() {
            assertThat(result.getOrDefault("O2", 0.0))
                    .isCloseTo(REF_O2, within(REF_O2 * 0.10));
        }

        @Test
        @DisplayName("H2 fraction consistent with fuel-rich mixture")
        void h2Present() {
            assertThat(result.getOrDefault("H2", 0.0))
                    .isCloseTo(REF_H2, within(REF_H2 * 0.10));
        }

        @Test
        @DisplayName("H2O at 2.0 MPa is between 0.5 MPa and 7 MPa (monotonic in P)")
        void h2oMonotonicInPressure() {
            double h2oLowP  = minimizer.minimize(lh2(6.0), 3200, 0.5e6).getOrDefault("H2O", 0.0);
            double h2oHighP = minimizer.minimize(lh2(6.0), 3200, 7.0e6).getOrDefault("H2O", 0.0);
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isGreaterThan(h2oLowP)
                    .isLessThan(h2oHighP);
        }

        @Test
        @DisplayName("OH at 2.0 MPa is between 0.5 MPa and 7 MPa (monotonic in P)")
        void ohMonotonicInPressure() {
            double ohLowP  = minimizer.minimize(lh2(6.0), 3200, 0.5e6).getOrDefault("OH", 0.0);
            double ohHighP = minimizer.minimize(lh2(6.0), 3200, 7.0e6).getOrDefault("OH", 0.0);
            assertThat(result.getOrDefault("OH", 0.0))
                    .isGreaterThan(ohHighP)
                    .isLessThan(ohLowP);
        }
    }

    // =======================================================================
    //  LOX / CH4   —   O/F = 3.5,  T = 3200 K
    // =======================================================================

    /**
     * CEA tp input deck:
     * <pre>
     * prob case = tp
     * p,bar= 5
     * t,k= 3200
     * o/f = 3.5
     * reac
     * fuel CH4  wt%=100.0
     * oxid O2(L)  wt%=100.0
     * output short mass fractions SI Units
     * end
     * </pre>
     *
     * <p>At 0.5 MPa, CO₂ ⇌ CO + ½O₂ dissociation is favored: CO rises to ≈ 23 %
     * and CO₂ drops to ≈ 25 %, converging toward equal fractions. OH reaches ≈ 5.9 %
     * and O ≈ 1.4 %.
     */
    @Nested
    @DisplayName("LOX/CH4  O/F=3.5  T=3200 K  P=0.5 MPa")
    class LoxCh4_OF3p5_3200K_0p5MPa {

        // NASA CEA reference values (tp deck above).
        private static final double REF_H2O = 0.37755;
        private static final double REF_CO2 = 0.24459;
        private static final double REF_CO  = 0.23232;
        private static final double REF_OH  = 0.06343;
        private static final double REF_O2  = 0.05795;
        private static final double REF_O   = 0.01421;

        private Map<String, Double> result;

        @BeforeEach
        void solve() { result = minimizer.minimize(ch4(3.5), 3200, 0.5e6); }

        @Test
        @DisplayName("H2O is a major product")
        void h2oMajor() {
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isCloseTo(REF_H2O, within(REF_H2O * 0.03));
        }

        @Test
        @DisplayName("CO2 is reduced by dissociation at 0.5 MPa")
        void co2Present() {
            assertThat(result.getOrDefault("CO2", 0.0))
                    .isCloseTo(REF_CO2, within(REF_CO2 * 0.03));
        }

        @Test
        @DisplayName("CO is elevated by dissociation at 0.5 MPa")
        void coElevated() {
            assertThat(result.getOrDefault("CO", 0.0))
                    .isCloseTo(REF_CO, within(REF_CO * 0.03));
        }

        @Test
        @DisplayName("OH is a significant dissociation product at 0.5 MPa")
        void ohPresent() {
            assertThat(result.getOrDefault("OH", 0.0))
                    .isCloseTo(REF_OH, within(REF_OH * 0.10));
        }

        @Test
        @DisplayName("O2 is elevated by CO2 dissociation at 0.5 MPa")
        void o2Present() {
            assertThat(result.getOrDefault("O2", 0.0))
                    .isCloseTo(REF_O2, within(REF_O2 * 0.10));
        }

        @Test
        @DisplayName("CO at 0.5 MPa is greater than at 7 MPa (Le Chatelier)")
        void coGreaterThanAt7MPa() {
            Map<String, Double> highP = minimizer.minimize(ch4(3.5), 3200, 7e6);
            assertThat(result.getOrDefault("CO", 0.0))
                    .isGreaterThan(highP.getOrDefault("CO", 0.0));
        }

        @Test
        @DisplayName("CO2 at 0.5 MPa is less than at 7 MPa (Le Chatelier)")
        void co2LessThanAt7MPa() {
            Map<String, Double> highP = minimizer.minimize(ch4(3.5), 3200, 7e6);
            assertThat(result.getOrDefault("CO2", 0.0))
                    .isLessThan(highP.getOrDefault("CO2", 0.0));
        }
    }

    /**
     * CEA tp input deck:
     * <pre>
     * prob case = tp
     * p,bar= 20
     * t,k= 3200
     * o/f = 3.5
     * reac
     * fuel CH4  wt%=100.0
     * oxid O2(L)  wt%=100.0
     * output short mass fractions SI Units
     * end
     * </pre>
     *
     * <p>At 2.0 MPa CO drops to ≈ 19 % and CO₂ recovers to ≈ 31 % relative to the
     * 0.5 MPa case, confirming a monotonic pressure response.
     */
    @Nested
    @DisplayName("LOX/CH4  O/F=3.5  T=3200 K  P=2.0 MPa")
    class LoxCh4_OF3p5_3200K_2p0MPa {

        // NASA CEA reference values (tp deck above).
        private static final double REF_H2O = 0.41821;
        private static final double REF_CO2 = 0.30597;
        private static final double REF_CO  = 0.19325;
        private static final double REF_OH  = 0.03978;
        private static final double REF_O2  = 0.03094;

        private Map<String, Double> result;

        @BeforeEach
        void solve() { result = minimizer.minimize(ch4(3.5), 3200, 2.0e6); }

        @Test
        @DisplayName("H2O is a major product")
        void h2oMajor() {
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isCloseTo(REF_H2O, within(REF_H2O * 0.03));
        }

        @Test
        @DisplayName("CO2 is a major product")
        void co2Major() {
            assertThat(result.getOrDefault("CO2", 0.0))
                    .isCloseTo(REF_CO2, within(REF_CO2 * 0.03));
        }

        @Test
        @DisplayName("CO is a major product")
        void coMajor() {
            assertThat(result.getOrDefault("CO", 0.0))
                    .isCloseTo(REF_CO, within(REF_CO * 0.03));
        }

        @Test
        @DisplayName("OH is present")
        void ohPresent() {
            assertThat(result.getOrDefault("OH", 0.0))
                    .isCloseTo(REF_OH, within(REF_OH * 0.10));
        }

        @Test
        @DisplayName("CO at 2.0 MPa is between 0.5 MPa and 7 MPa (monotonic in P)")
        void coMonotonicInPressure() {
            double coLowP  = minimizer.minimize(ch4(3.5), 3200, 0.5e6).getOrDefault("CO", 0.0);
            double coHighP = minimizer.minimize(ch4(3.5), 3200, 7.0e6).getOrDefault("CO", 0.0);
            assertThat(result.getOrDefault("CO", 0.0))
                    .isGreaterThan(coHighP)
                    .isLessThan(coLowP);
        }

        @Test
        @DisplayName("CO2 at 2.0 MPa is between 0.5 MPa and 7 MPa (monotonic in P)")
        void co2MonotonicInPressure() {
            double co2LowP  = minimizer.minimize(ch4(3.5), 3200, 0.5e6).getOrDefault("CO2", 0.0);
            double co2HighP = minimizer.minimize(ch4(3.5), 3200, 7.0e6).getOrDefault("CO2", 0.0);
            assertThat(result.getOrDefault("CO2", 0.0))
                    .isGreaterThan(co2LowP)
                    .isLessThan(co2HighP);
        }
    }

    // =======================================================================
    //  LOX / RP-1   —   O/F = 2.77,  T = 3000 K
    //  RP-1 modeled as C12H23.4, MW = 167.587 g/mol
    // =======================================================================

    /**
     * CEA tp input deck:
     * <pre>
     * prob case = tp
     * p,bar= 5
     * t,k= 3000
     * o/f = 2.77
     * reac
     * fuel C12H26  wt%=100.0  t(k)=300
     * oxid O2(L)   wt%=100.0
     * output short mass fractions SI Units
     * end
     * </pre>
     *
     * <p>At 0.5 MPa, CO rises to ≈ 28 % as CO₂ drops to ≈ 39 %. OH reaches ≈ 2.5 %
     * and O₂ ≈ 2.0 %. The three major species (CO₂, CO, H₂O) have similar fractions,
     * unlike the 7 MPa case where CO₂ dominates.
     *
     * <p>Note: CEA uses "C12H26" as a close RP-1 surrogate; the solver uses C12H23.4.
     * A small systematic offset in CO and H₂O is expected.
     */
    @Nested
    @DisplayName("LOX/RP-1  O/F=2.77  T=3000 K  P=0.5 MPa")
    class LoxRp1_OF2p77_3000K_0p5MPa {

        // NASA CEA reference values (tp deck above).
        private static final double REF_CO2 = 0.38713;
        private static final double REF_CO  = 0.28520;
        private static final double REF_H2O = 0.27075;
        private static final double REF_OH  = 0.02667;
        private static final double REF_O2  = 0.02070;

        private Map<String, Double> result;

        @BeforeEach
        void solve() { result = minimizer.minimize(rp1(2.77), 3000, 0.5e6); }

        @Test
        @DisplayName("CO2 is the dominant species")
        void co2Dominant() {
            assertThat(result.getOrDefault("CO2", 0.0))
                    .isCloseTo(REF_CO2, within(REF_CO2 * 0.03));
        }

        @Test
        @DisplayName("CO is elevated by dissociation at 0.5 MPa")
        void coElevated() {
            assertThat(result.getOrDefault("CO", 0.0))
                    .isCloseTo(REF_CO, within(REF_CO * 0.03));
        }

        @Test
        @DisplayName("H2O is present")
        void h2oPresent() {
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isCloseTo(REF_H2O, within(REF_H2O * 0.03));
        }

        @Test
        @DisplayName("OH is a dissociation product at 0.5 MPa")
        void ohPresent() {
            assertThat(result.getOrDefault("OH", 0.0))
                    .isCloseTo(REF_OH, within(REF_OH * 0.10));
        }

        @Test
        @DisplayName("CO at 0.5 MPa is greater than at 7 MPa (Le Chatelier)")
        void coGreaterThanAt7MPa() {
            Map<String, Double> highP = minimizer.minimize(rp1(2.77), 3000, 7e6);
            assertThat(result.getOrDefault("CO", 0.0))
                    .isGreaterThan(highP.getOrDefault("CO", 0.0));
        }

        @Test
        @DisplayName("CO2 at 0.5 MPa is less than at 7 MPa (Le Chatelier)")
        void co2LessThanAt7MPa() {
            Map<String, Double> highP = minimizer.minimize(rp1(2.77), 3000, 7e6);
            assertThat(result.getOrDefault("CO2", 0.0))
                    .isLessThan(highP.getOrDefault("CO2", 0.0));
        }
    }

    /**
     * CEA tp input deck:
     * <pre>
     * prob case = tp
     * p,bar= 20
     * t,k= 3000
     * o/f = 2.77
     * reac
     * fuel C12H26  wt%=100.0  t(k)=300
     * oxid O2(L)   wt%=100.0
     * output short mass fractions SI Units
     * end
     * </pre>
     *
     * <p>At 2.0 MPa CO drops to ≈ 26 % and CO₂ recovers to ≈ 43 %, confirming a
     * monotonic pressure response. OH falls to ≈ 1.4 %.
     */
    @Nested
    @DisplayName("LOX/RP-1  O/F=2.77  T=3000 K  P=2.0 MPa")
    class LoxRp1_OF2p77_3000K_2p0MPa {

        // NASA CEA reference values (tp deck above).
        private static final double REF_CO2 = 0.42621;
        private static final double REF_CO  = 0.26033;
        private static final double REF_H2O = 0.28564;
        private static final double REF_OH  = 0.01482;
        private static final double REF_O2  = 0.00731;

        private Map<String, Double> result;

        @BeforeEach
        void solve() { result = minimizer.minimize(rp1(2.77), 3000, 2.0e6); }

        @Test
        @DisplayName("CO2 is the dominant species")
        void co2Dominant() {
            assertThat(result.getOrDefault("CO2", 0.0))
                    .isCloseTo(REF_CO2, within(REF_CO2 * 0.03));
        }

        @Test
        @DisplayName("CO is a major product")
        void coMajor() {
            assertThat(result.getOrDefault("CO", 0.0))
                    .isCloseTo(REF_CO, within(REF_CO * 0.03));
        }

        @Test
        @DisplayName("H2O is a major product")
        void h2oMajor() {
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isCloseTo(REF_H2O, within(REF_H2O * 0.03));
        }

        @Test
        @DisplayName("OH is an intermediate dissociation product")
        void ohPresent() {
            assertThat(result.getOrDefault("OH", 0.0))
                    .isCloseTo(REF_OH, within(REF_OH * 0.10));
        }

        @Test
        @DisplayName("CO at 2.0 MPa is between 0.5 MPa and 7 MPa (monotonic in P)")
        void coMonotonicInPressure() {
            double coLowP  = minimizer.minimize(rp1(2.77), 3000, 0.5e6).getOrDefault("CO", 0.0);
            double coHighP = minimizer.minimize(rp1(2.77), 3000, 7.0e6).getOrDefault("CO", 0.0);
            assertThat(result.getOrDefault("CO", 0.0))
                    .isGreaterThan(coHighP)
                    .isLessThan(coLowP);
        }

        @Test
        @DisplayName("CO2 at 2.0 MPa is between 0.5 MPa and 7 MPa (monotonic in P)")
        void co2MonotonicInPressure() {
            double co2LowP  = minimizer.minimize(rp1(2.77), 3000, 0.5e6).getOrDefault("CO2", 0.0);
            double co2HighP = minimizer.minimize(rp1(2.77), 3000, 7.0e6).getOrDefault("CO2", 0.0);
            assertThat(result.getOrDefault("CO2", 0.0))
                    .isGreaterThan(co2LowP)
                    .isLessThan(co2HighP);
        }
    }
}
