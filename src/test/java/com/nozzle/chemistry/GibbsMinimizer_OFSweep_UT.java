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
 * O/F mixture ratio sweep validation for {@link GibbsMinimizer}.
 *
 * <p>Complements {@link GibbsMinimizer_CEAValidation_UT} (which fixes O/F and sweeps T/P)
 * by fixing T and P and varying the mixture ratio across fuel-rich, near-stoichiometric,
 * and oxidizer-rich conditions for each propellant combination.
 *
 * <h2>Reference values</h2>
 *
 * <p>Reference values are NASA CEA outputs verified using the {@code tp} input decks
 * documented in each nested class.
 * Tolerances follow the same scheme as {@link GibbsMinimizer_CEAValidation_UT}:
 * ±3 % relative for major species (mass fraction &gt; 0.10),
 * ±10 % relative for intermediate species (0.005–0.10), and ±0.002 absolute for minor
 * species (&lt; 0.005).
 *
 * <h2>Monotonic trend tests</h2>
 *
 * <p>Each propellant section includes assertions about the direction of change with O/F
 * that are independent of exact reference values and should hold regardless of how
 * the CEA reference values are updated.
 */
@DisplayName("GibbsMinimizer — O/F mixture ratio sweep")
class GibbsMinimizer_OFSweep_UT {

    private GibbsMinimizer minimizer;

    @BeforeEach
    void setUp() {
        minimizer = new GibbsMinimizer(new NasaSpeciesDatabase());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Map<String, Double> lh2(double of)  {
        PropellantComposition c = new PropellantComposition(); c.setLoxLh2(of);  return c.get();
    }
    private Map<String, Double> ch4(double of)  {
        PropellantComposition c = new PropellantComposition(); c.setLoxCh4(of);  return c.get();
    }
    private Map<String, Double> rp1(double of)  {
        PropellantComposition c = new PropellantComposition(); c.setLoxRp1(of);  return c.get();
    }

    // =======================================================================
    //  LOX / LH2   —   T = 3200 K, P = 7 MPa
    // =======================================================================

    // -----------------------------------------------------------------------
    // LOX / LH2  —  O/F = 4.0  (fuel-rich)
    // -----------------------------------------------------------------------

    /**
     * CEA tp input deck:
     * <pre>
     * prob case = tp
     * p,bar= 70
     * t,k= 3200
     * o/f = 4
     * reac
     * fuel H2(L)  wt%=100.0
     * oxid O2(L)  wt%=100.0
     * output short mass fractions SI Units
     * end
     * </pre>
     *
     * <p>At O/F=4 (H/O atom ratio = 3.97) the mixture is strongly fuel-rich. Without
     * dissociation: H2O=0.9008, H2=0.0992. Dissociation at 3200 K reduces H2O
     * by ~3 % absolute and raises OH; the large H2 excess suppresses OH relative to
     * the O/F=6 case.
     */
    @Nested
    @DisplayName("LOX/LH2  O/F=4.0  T=3200 K  P=7 MPa  (fuel-rich)")
    class LoxLh2_OF4_3200K_7MPa {

        // NASA CEA reference values.
        private static final double REF_H2O = 0.88226;
        private static final double REF_H2  = 0.09791;
        private static final double REF_OH  = 0.01670;

        private Map<String, Double> result;

        @BeforeEach
        void solve() { result = minimizer.minimize(lh2(4.0), 3200, 7e6); }

        @Test
        @DisplayName("H2O is the dominant species")
        void h2oMajor() {
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isCloseTo(REF_H2O, within(REF_H2O * 0.03));
        }

        @Test
        @DisplayName("H2 is the second-largest species (large fuel excess)")
        void h2Major() {
            assertThat(result.getOrDefault("H2", 0.0))
                    .isCloseTo(REF_H2, within(REF_H2 * 0.10));
        }

        @Test
        @DisplayName("OH intermediate species present")
        void ohIntermediate() {
            assertThat(result.getOrDefault("OH", 0.0))
                    .isCloseTo(REF_OH, within(REF_OH * 0.10));
        }

        @Test
        @DisplayName("H2 at O/F=4 is larger than at O/F=6 (more fuel excess)")
        void h2GreaterThanAtOf6() {
            Map<String, Double> of6 = minimizer.minimize(lh2(6.0), 3200, 7e6);
            assertThat(result.getOrDefault("H2", 0.0))
                    .isGreaterThan(of6.getOrDefault("H2", 0.0));
        }

        @Test
        @DisplayName("O2 is absent or trace (no free oxygen in fuel-rich mixture)")
        void o2AbsentOrTrace() {
            assertThat(result.getOrDefault("O2", 0.0))
                    .isLessThan(0.005);
        }
    }

    // -----------------------------------------------------------------------
    // LOX / LH2  —  O/F = 8.0  (oxidizer-rich)
    // -----------------------------------------------------------------------

    /**
     * CEA tp input deck:
     * <pre>
     * prob case = tp
     * p,bar= 70
     * t,k= 3200
     * o/f = 8
     * reac
     * fuel H2(L)  wt%=100.0
     * oxid O2(L)  wt%=100.0
     * output short mass fractions SI Units
     * end
     * </pre>
     *
     * <p>At O/F=8 (H/O atom ratio = 1.984) the mixture is slightly oxidizer-rich
     * (stoichiometric H/O = 2.0). Without dissociation: H2O=0.9927, O2=0.007. At 3200 K,
     * dissociation is stronger than the O/F=6 case because there is less H2 to buffer
     * the equilibrium: OH rises and O2 increases.
     */
    @Nested
    @DisplayName("LOX/LH2  O/F=8.0  T=3200 K  P=7 MPa  (oxidizer-rich)")
    class LoxLh2_OF8_3200K_7MPa {

        // NASA CEA reference values.
        private static final double REF_H2O = 0.90070;
        private static final double REF_OH  = 0.04858;
        private static final double REF_O2  = 0.03956;

        private Map<String, Double> result;

        @BeforeEach
        void solve() { result = minimizer.minimize(lh2(8.0), 3200, 7e6); }

        @Test
        @DisplayName("H2O is the dominant species")
        void h2oMajor() {
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isCloseTo(REF_H2O, within(REF_H2O * 0.03));
        }

        @Test
        @DisplayName("OH intermediate species present")
        void ohIntermediate() {
            assertThat(result.getOrDefault("OH", 0.0))
                    .isCloseTo(REF_OH, within(REF_OH * 0.10));
        }

        @Test
        @DisplayName("O2 present (oxidizer-rich mixture)")
        void o2Present() {
            assertThat(result.getOrDefault("O2", 0.0))
                    .isCloseTo(REF_O2, within(REF_O2 * 0.10));
        }

        @Test
        @DisplayName("OH at O/F=8 is larger than at O/F=6 (more oxygen available)")
        void ohGreaterThanAtOf6() {
            Map<String, Double> of6 = minimizer.minimize(lh2(6.0), 3200, 7e6);
            assertThat(result.getOrDefault("OH", 0.0))
                    .isGreaterThan(of6.getOrDefault("OH", 0.0));
        }

        @Test
        @DisplayName("H2 at O/F=8 is smaller than at O/F=6 (less fuel excess)")
        void h2LessThanAtOf6() {
            Map<String, Double> of6 = minimizer.minimize(lh2(6.0), 3200, 7e6);
            assertThat(result.getOrDefault("H2", 0.0))
                    .isLessThan(of6.getOrDefault("H2", 0.0));
        }
    }

    // =======================================================================
    //  LOX / CH4   —   T = 3200 K, P = 7 MPa
    // =======================================================================

    // -----------------------------------------------------------------------
    // LOX / CH4  —  O/F = 2.5  (fuel-rich)
    // -----------------------------------------------------------------------

    /**
     * CEA tp input deck:
     * <pre>
     * prob case = tp
     * p,bar= 70
     * t,k= 3200
     * o/f = 2.5
     * reac
     * fuel CH4  wt%=100.0
     * oxid O2(L)  wt%=100.0
     * output short mass fractions SI Units
     * end
     * </pre>
     *
     * <p>LOX/CH4 stoichiometric O/F ≈ 4.0 (CH4 + 2O2 → CO2 + 2H2O). At O/F=2.5 the
     * mixture is fuel-rich: less oxygen is available per carbon atom so CO is favored
     * over CO2, and the excess hydrogen produces significant H2.
     */
    @Nested
    @DisplayName("LOX/CH4  O/F=2.5  T=3200 K  P=7 MPa  (fuel-rich)")
    class LoxCh4_OF2p5_3200K_7MPa {

        // NASA CEA reference values.
        private static final double REF_H2O = 0.40841;
        private static final double REF_CO2 = 0.15115;
        private static final double REF_CO  = 0.40261;
        private static final double REF_H2  = 0.02456;

        private Map<String, Double> result;

        @BeforeEach
        void solve() { result = minimizer.minimize(ch4(2.5), 3200, 7e6); }

        @Test
        @DisplayName("H2O is a major product")
        void h2oMajor() {
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isCloseTo(REF_H2O, within(REF_H2O * 0.03));
        }

        @Test
        @DisplayName("CO2 is present")
        void co2Present() {
            assertThat(result.getOrDefault("CO2", 0.0))
                    .isCloseTo(REF_CO2, within(REF_CO2 * 0.03));
        }

        @Test
        @DisplayName("CO is a major product (fuel-rich, less O per C)")
        void coMajor() {
            assertThat(result.getOrDefault("CO", 0.0))
                    .isCloseTo(REF_CO, within(REF_CO * 0.03));
        }

        @Test
        @DisplayName("H2 is present (hydrogen excess)")
        void h2Present() {
            assertThat(result.getOrDefault("H2", 0.0))
                    .isCloseTo(REF_H2, within(REF_H2 * 0.10));
        }

        @Test
        @DisplayName("CO at O/F=2.5 is larger than at O/F=3.5 (more fuel-rich)")
        void coGreaterThanAtOf3p5() {
            Map<String, Double> of35 = minimizer.minimize(ch4(3.5), 3200, 7e6);
            assertThat(result.getOrDefault("CO", 0.0))
                    .isGreaterThan(of35.getOrDefault("CO", 0.0));
        }

        @Test
        @DisplayName("CO2 at O/F=2.5 is smaller than at O/F=3.5 (less O per C)")
        void co2LessThanAtOf3p5() {
            Map<String, Double> of35 = minimizer.minimize(ch4(3.5), 3200, 7e6);
            assertThat(result.getOrDefault("CO2", 0.0))
                    .isLessThan(of35.getOrDefault("CO2", 0.0));
        }

        @Test
        @DisplayName("O2 is absent or trace (fuel-rich, no free oxygen)")
        void o2AbsentOrTrace() {
            assertThat(result.getOrDefault("O2", 0.0))
                    .isLessThan(0.005);
        }
    }

    // -----------------------------------------------------------------------
    // LOX / CH4  —  O/F = 5.0  (oxidizer-rich)
    // -----------------------------------------------------------------------

    /**
     * CEA tp input deck:
     * <pre>
     * prob case = tp
     * p,bar= 70
     * t,k= 3200
     * o/f = 5.0
     * reac
     * fuel CH4  wt%=100.0
     * oxid O2(L)  wt%=100.0
     * output short mass fractions SI Units
     * end
     * </pre>
     *
     * <p>At O/F=5.0 (above stoichiometric ≈ 4.0) the mixture is oxidizer-rich: all carbon
     * is fully oxidized to CO2 with oxygen left over. O2 appears in the equilibrium
     * composition and CO is suppressed.
     */
    @Nested
    @DisplayName("LOX/CH4  O/F=5.0  T=3200 K  P=7 MPa  (oxidizer-rich)")
    class LoxCh4_OF5p0_3200K_7MPa {

        // NASA CEA reference values.
        private static final double REF_H2O = 0.34295;
        private static final double REF_CO2 = 0.37967;
        private static final double REF_O2  = 0.18086;

        private Map<String, Double> result;

        @BeforeEach
        void solve() { result = minimizer.minimize(ch4(5.0), 3200, 7e6); }

        @Test
        @DisplayName("H2O is a major product")
        void h2oMajor() {
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isCloseTo(REF_H2O, within(REF_H2O * 0.03));
        }

        @Test
        @DisplayName("CO2 is the dominant carbon-bearing product")
        void co2Major() {
            assertThat(result.getOrDefault("CO2", 0.0))
                    .isCloseTo(REF_CO2, within(REF_CO2 * 0.03));
        }

        @Test
        @DisplayName("O2 is present (oxidizer-rich mixture)")
        void o2Present() {
            assertThat(result.getOrDefault("O2", 0.0))
                    .isCloseTo(REF_O2, within(REF_O2 * 0.03));
        }

        @Test
        @DisplayName("CO2 at O/F=5.0 is larger than at O/F=3.5 (more O per C)")
        void co2GreaterThanAtOf3p5() {
            Map<String, Double> of35 = minimizer.minimize(ch4(3.5), 3200, 7e6);
            assertThat(result.getOrDefault("CO2", 0.0))
                    .isGreaterThan(of35.getOrDefault("CO2", 0.0));
        }

        @Test
        @DisplayName("CO at O/F=5.0 is smaller than at O/F=3.5 (excess O drives CO to CO2)")
        void coLessThanAtOf3p5() {
            Map<String, Double> of35 = minimizer.minimize(ch4(3.5), 3200, 7e6);
            assertThat(result.getOrDefault("CO", 0.0))
                    .isLessThan(of35.getOrDefault("CO", 0.0));
        }

        @Test
        @DisplayName("H2 is absent or trace (oxidizer-rich, all H burned)")
        void h2AbsentOrTrace() {
            assertThat(result.getOrDefault("H2", 0.0))
                    .isLessThan(0.005);
        }
    }

    // =======================================================================
    //  LOX / RP-1   —   T = 3000 K, P = 7 MPa
    //  RP-1 modeled as C12H23.4, MW = 167.587 g/mol
    // =======================================================================

    // -----------------------------------------------------------------------
    // LOX / RP-1  —  O/F = 1.8  (strongly fuel-rich)
    // -----------------------------------------------------------------------

    /**
     * CEA tp input deck:
     * <pre>
     * prob case = tp
     * p,bar= 70
     * t,k= 3000
     * o/f = 1.8
     * reac
     * fuel C12H26  wt%=100.0  t(k)=300
     * oxid O2(L)   wt%=100.0
     * output short mass fractions SI Units
     * end
     * </pre>
     *
     * <p>RP-1 stoichiometric O/F ≈ 3.4 (C12H23.4 + 17.35 O2). At O/F=1.8 the mixture
     * is strongly fuel-rich: insufficient oxygen to oxidize all carbon to CO2, so CO is
     * the dominant carbon product. Water-gas shift (CO2 + H2 ⇌ CO + H2O) further
     * favors CO at 3000 K.
     *
     * <p>Note: CEA uses "C12H26" as a close RP-1 surrogate; the solver uses C12H23.4.
     * A small systematic offset in the C/H ratio is expected.
     */
    @Nested
    @DisplayName("LOX/RP-1  O/F=1.8  T=3000 K  P=7 MPa  (strongly fuel-rich)")
    class LoxRp1_OF1p8_3000K_7MPa {

        // NASA CEA reference values.
        private static final double REF_CO  = 0.63777;
        private static final double REF_CO2 = 0.12246;
        private static final double REF_H2O = 0.21042;
        private static final double REF_H2  = 0.02603;

        private Map<String, Double> result;

        @BeforeEach
        void solve() { result = minimizer.minimize(rp1(1.8), 3000, 7e6); }

        @Test
        @DisplayName("CO is the dominant species (strongly fuel-rich)")
        void coDominant() {
            assertThat(result.getOrDefault("CO", 0.0))
                    .isCloseTo(REF_CO, within(REF_CO * 0.03));
        }

        @Test
        @DisplayName("CO2 is present")
        void co2Present() {
            assertThat(result.getOrDefault("CO2", 0.0))
                    .isCloseTo(REF_CO2, within(REF_CO2 * 0.03));
        }

        @Test
        @DisplayName("H2O is present")
        void h2oPresent() {
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isCloseTo(REF_H2O, within(REF_H2O * 0.03));
        }

        @Test
        @DisplayName("H2 is present (excess hydrogen)")
        void h2Present() {
            assertThat(result.getOrDefault("H2", 0.0))
                    .isCloseTo(REF_H2, within(REF_H2 * 0.10));
        }

        @Test
        @DisplayName("CO at O/F=1.8 is larger than at O/F=2.77 (more fuel-rich)")
        void coGreaterThanAtOf2p77() {
            Map<String, Double> of277 = minimizer.minimize(rp1(2.77), 3000, 7e6);
            assertThat(result.getOrDefault("CO", 0.0))
                    .isGreaterThan(of277.getOrDefault("CO", 0.0));
        }

        @Test
        @DisplayName("O2 is absent or trace (strongly fuel-rich)")
        void o2AbsentOrTrace() {
            assertThat(result.getOrDefault("O2", 0.0))
                    .isLessThan(0.005);
        }
    }

    // -----------------------------------------------------------------------
    // LOX / RP-1  —  O/F = 3.5  (oxidizer-rich)
    // -----------------------------------------------------------------------

    /**
     * CEA tp input deck:
     * <pre>
     * prob case = tp
     * p,bar= 70
     * t,k= 3000
     * o/f = 3.5
     * reac
     * fuel C12H26  wt%=100.0  t(k)=300
     * oxid O2(L)   wt%=100.0
     * output short mass fractions SI Units
     * end
     * </pre>
     *
     * <p>At O/F=3.5 (above stoichiometric ≈ 3.4) the mixture is slightly oxidizer-rich:
     * essentially all carbon is oxidized to CO2 with a small O2 surplus. H2O is the
     * dominant hydrogen product and CO is suppressed relative to the fuel-rich case.
     */
    @Nested
    @DisplayName("LOX/RP-1  O/F=3.5  T=3000 K  P=7 MPa  (oxidizer-rich)")
    class LoxRp1_OF3p5_3000K_7MPa {

        // NASA CEA reference values.
        private static final double REF_CO2 = 0.59555;
        private static final double REF_H2O = 0.26358;
        private static final double REF_CO  = 0.06632;
        private static final double REF_O2  = 0.05530;

        private Map<String, Double> result;

        @BeforeEach
        void solve() { result = minimizer.minimize(rp1(3.5), 3000, 7e6); }

        @Test
        @DisplayName("CO2 is the dominant product")
        void co2Dominant() {
            assertThat(result.getOrDefault("CO2", 0.0))
                    .isCloseTo(REF_CO2, within(REF_CO2 * 0.03));
        }

        @Test
        @DisplayName("H2O is present")
        void h2oPresent() {
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isCloseTo(REF_H2O, within(REF_H2O * 0.03));
        }

        @Test
        @DisplayName("CO is present but smaller than H2O and CO2")
        void coPresent() {
            assertThat(result.getOrDefault("CO", 0.0))
                    .isCloseTo(REF_CO, within(REF_CO * 0.10));
        }

        @Test
        @DisplayName("O2 is present (oxidizer-rich mixture)")
        void o2Present() {
            assertThat(result.getOrDefault("O2", 0.0))
                    .isCloseTo(REF_O2, within(REF_O2 * 0.10));
        }

        @Test
        @DisplayName("CO2 at O/F=3.5 is larger than at O/F=2.77 (more O per C)")
        void co2GreaterThanAtOf2p77() {
            Map<String, Double> of277 = minimizer.minimize(rp1(2.77), 3000, 7e6);
            assertThat(result.getOrDefault("CO2", 0.0))
                    .isGreaterThan(of277.getOrDefault("CO2", 0.0));
        }

        @Test
        @DisplayName("CO at O/F=3.5 is smaller than at O/F=2.77 (excess O drives CO to CO2)")
        void coLessThanAtOf2p77() {
            Map<String, Double> of277 = minimizer.minimize(rp1(2.77), 3000, 7e6);
            assertThat(result.getOrDefault("CO", 0.0))
                    .isLessThan(of277.getOrDefault("CO", 0.0));
        }
    }
}
