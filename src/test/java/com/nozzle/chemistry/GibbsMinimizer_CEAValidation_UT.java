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
 * Validates {@link GibbsMinimizer} species mass fractions against reference
 * equilibrium compositions computed by NASA CEA (Chemical Equilibrium with
 * Applications, <a href="https://cearun.grc.nasa.gov">cearun.grc.nasa.gov</a>).
 *
 * <h2>How to generate the reference values</h2>
 *
 * <p>Each nested test class documents its CEA input deck. Paste it verbatim
 * into the CEA web interface under "Input". The output section titled
 * "MOLE FRACTIONS" or "MASS FRACTIONS" gives the reference species values.
 * CEA's "tp" problem type matches {@code GibbsMinimizer.minimize(init, T, P)}
 * exactly — both solve for the equilibrium composition at a fixed T and P.
 *
 * <h2>Mole → mass fraction conversion</h2>
 *
 * <p>If CEA outputs mole fractions {@code xi}, convert to mass fractions {@code yi}:
 * <pre>
 *   yi = xi × MWi / Σ(xj × MWj)
 * </pre>
 * where MW values are (g/mol): H2O=18.015, H2=2.016, OH=17.007,
 * O2=32.0, H=1.008, O=16.0, CO=28.01, CO2=44.01, N2=28.014.
 *
 * <h2>Tolerances</h2>
 *
 * <p>Both CEA and this solver use NASA-7 polynomials from NASA TP-2002-211556
 * (McBride, Zehe, Gordon), so agreement should be close. Tolerances are:
 * <ul>
 *   <li>Major species ({@code y > 0.05}):  ±3 % relative</li>
 *   <li>Intermediate species (0.005–0.05): ±10 % relative</li>
 *   <li>Minor species ({@code y < 0.005}): ±0.002 absolute</li>
 * </ul>
 */
@DisplayName("GibbsMinimizer — NASA CEA validation")
class GibbsMinimizer_CEAValidation_UT {

    private GibbsMinimizer minimizer;

    @BeforeEach
    void setUp() {
        minimizer = new GibbsMinimizer(new NasaSpeciesDatabase());
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // LOX / LH2  —  O/F = 6.0  —  3500 K / 7 MPa
    // -----------------------------------------------------------------------

    /**
     * CEA input deck used to generate reference values (rocket/IAC problem;
     * chamber equilibrium temperature came out to 3485 K at 70 bar):
     * <pre>
     * prob case = rocket ro equilibrium
     * p,bar= 70
     * o/f = 6
     * reac
     * fuel H2(L)  wt%=100.0
     * oxid O2(L)  wt%=100.0
     * output short mass fractions SI Units
     * end
     * </pre>
     *
     * <p>Reference values are taken from the CHAMBER column of the CEA MASS
     * FRACTIONS output. The adiabatic flame temperature at O/F=6.0, 70 bar is
     * 3485 K, so the minimizer is evaluated at that temperature.
     */
    @Nested
    @DisplayName("LOX/LH2  O/F=6.0  T=3485 K  P=7 MPa")
    class LoxLh2_3485K_7MPa {

        // ---- CEA reference values — chamber column, NASA CEA2 (2004) ----
        private static final double REF_H2O = 0.89163;
        private static final double REF_H2  = 0.03719;
        private static final double REF_OH  = 0.05644;
        private static final double REF_H   = 0.00255;
        private static final double REF_O   = 0.00395;
        private static final double REF_O2  = 0.00815;

        private Map<String, Double> result;

        @BeforeEach
        void solve() {
            result = minimizer.minimize(lh2Initial(6.0), 3485, 7e6);
        }

        @Test
        @DisplayName("H2O mass fraction matches CEA to 3%")
        void h2oMajorSpecies() {
            assertThat(result.getOrDefault("H2O", 0.0))
                    .as("H2O mass fraction at 3485 K / 7 MPa")
                    .isCloseTo(REF_H2O, within(REF_H2O * 0.03));
        }

        @Test
        @DisplayName("H2 mass fraction matches CEA to 3%")
        void h2MajorSpecies() {
            assertThat(result.getOrDefault("H2", 0.0))
                    .as("H2 mass fraction at 3485 K / 7 MPa")
                    .isCloseTo(REF_H2, within(REF_H2 * 0.03));
        }

        @Test
        @DisplayName("OH intermediate species matches CEA to 10%")
        void ohIntermediateSpecies() {
            assertThat(result.getOrDefault("OH", 0.0))
                    .as("OH mass fraction at 3485 K / 7 MPa")
                    .isCloseTo(REF_OH, within(REF_OH * 0.10));
        }

        @Test
        @DisplayName("H minor species matches CEA to ±0.002 absolute")
        void hMinorSpecies() {
            assertThat(result.getOrDefault("H", 0.0))
                    .as("H mass fraction at 3485 K / 7 MPa")
                    .isCloseTo(REF_H, within(0.002));
        }

        @Test
        @DisplayName("O minor species matches CEA to ±0.002 absolute")
        void oMinorSpecies() {
            assertThat(result.getOrDefault("O", 0.0))
                    .as("O mass fraction at 3485 K / 7 MPa")
                    .isCloseTo(REF_O, within(0.002));
        }

        @Test
        @DisplayName("O2 intermediate species matches CEA to 10%")
        void o2IntermediateSpecies() {
            assertThat(result.getOrDefault("O2", 0.0))
                    .as("O2 mass fraction at 3485 K / 7 MPa")
                    .isCloseTo(REF_O2, within(REF_O2 * 0.10));
        }
    }

    // -----------------------------------------------------------------------
    // LOX / LH2  —  O/F = 6.0  —  3200 K / 7 MPa
    // -----------------------------------------------------------------------

    /**
     * CEA input deck (tp problem, exact T and P specified):
     * <pre>
     * prob case = tp
     * p,bar= 70
     * t,k= 3200
     * o/f = 6
     * reac
     * fuel H2(L)  wt%=100.0
     * oxidizer O2(L)  wt%=100.0
     * output short mass fractions SI Units
     * end
     * </pre>
     *
     * <p>At lower temperature, less dissociation is expected: H2O fraction should
     * be higher and OH / H fractions should be lower than at 3485 K.
     */
    @Nested
    @DisplayName("LOX/LH2  O/F=6.0  T=3200 K  P=7 MPa")
    class LoxLh2_3200K_7MPa {

        // ---- CEA reference values — NASA CEA2 tp problem (2004) ----
        private static final double REF_H2O = 0.93546;
        private static final double REF_H2  = 0.03549;
        private static final double REF_OH  = 0.02489;
        private static final double REF_H   = 0.00121;
        private static final double REF_O   = 0.00088;

        private Map<String, Double> result;

        @BeforeEach
        void solve() {
            result = minimizer.minimize(lh2Initial(6.0), 3200, 7e6);
        }

        @Test
        @DisplayName("H2O mass fraction matches CEA to 3%")
        void h2oMajorSpecies() {
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isCloseTo(REF_H2O, within(REF_H2O * 0.03));
        }

        @Test
        @DisplayName("H2 mass fraction matches CEA to 3%")
        void h2MajorSpecies() {
            assertThat(result.getOrDefault("H2", 0.0))
                    .isCloseTo(REF_H2, within(REF_H2 * 0.03));
        }

        @Test
        @DisplayName("OH intermediate species matches CEA to 10%")
        void ohIntermediateSpecies() {
            assertThat(result.getOrDefault("OH", 0.0))
                    .isCloseTo(REF_OH, within(REF_OH * 0.10));
        }

        @Test
        @DisplayName("H minor species matches CEA to ±0.002 absolute")
        void hMinorSpecies() {
            assertThat(result.getOrDefault("H", 0.0))
                    .isCloseTo(REF_H, within(0.002));
        }

        @Test
        @DisplayName("O minor species matches CEA to ±0.002 absolute")
        void oMinorSpecies() {
            assertThat(result.getOrDefault("O", 0.0))
                    .isCloseTo(REF_O, within(0.002));
        }

        @Test
        @DisplayName("At 3200 K, H2O fraction is higher than at 3485 K (less dissociation)")
        void lessDissociationAtLowerTemperature() {
            Map<String, Double> high = minimizer.minimize(lh2Initial(6.0), 3485, 7e6);
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isGreaterThan(high.getOrDefault("H2O", 0.0));
        }
    }

    // -----------------------------------------------------------------------
    // LOX / LH2  —  O/F = 6.0  —  3500 K / 10 MPa  (pressure sensitivity)
    // -----------------------------------------------------------------------

    /**
     * CEA input deck — same temperature, higher pressure:
     * <pre>
     * problem  tp
     *   p(bar) = 100.0
     *   t(k)   = 3500
     * react
     *   oxidizer = O2   wt = 1.0   t(k) = 90.17
     *   fuel = H2   wt = 1.0   t(k) = 20.27
     *   o/f  = 6.0
     * output  mass fractions
     * end
     * </pre>
     *
     * <p>Higher pressure suppresses dissociation (Le Châtelier): H2O should be
     * higher, and OH / H / O should all be lower than at 7 MPa.
     */
    @Nested
    @DisplayName("LOX/LH2  O/F=6.0  T=3500 K  P=10 MPa  (pressure sensitivity)")
    class LoxLh2_3500K_10MPa {

        // ---- CEA reference values — NASA CEA2 tp problem (2004) ----
        private static final double REF_H2O = 0.90161;   // higher than 7 MPa (0.89163) ✓
        private static final double REF_H2  = 0.03682;
        private static final double REF_OH  = 0.04989;   // lower than 7 MPa (0.05644) ✓
        private static final double REF_H   = 0.00219;   // lower than 7 MPa (0.00255) ✓

        private Map<String, Double> result;

        @BeforeEach
        void solve() {
            result = minimizer.minimize(lh2Initial(6.0), 3500, 10e6);
        }

        @Test
        @DisplayName("H2O mass fraction matches CEA to 3%")
        void h2oMajorSpecies() {
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isCloseTo(REF_H2O, within(REF_H2O * 0.03));
        }

        @Test
        @DisplayName("H2 mass fraction matches CEA to 3%")
        void h2MajorSpecies() {
            assertThat(result.getOrDefault("H2", 0.0))
                    .isCloseTo(REF_H2, within(REF_H2 * 0.03));
        }

        @Test
        @DisplayName("OH matches CEA to 10%")
        void ohIntermediateSpecies() {
            assertThat(result.getOrDefault("OH", 0.0))
                    .isCloseTo(REF_OH, within(REF_OH * 0.10));
        }

        @Test
        @DisplayName("H matches CEA to ±0.002 absolute")
        void hMinorSpecies() {
            assertThat(result.getOrDefault("H", 0.0))
                    .isCloseTo(REF_H, within(0.002));
        }
    }

    // -----------------------------------------------------------------------
    // LOX / CH4  —  O/F = 3.5  —  3200 K / 7 MPa
    // -----------------------------------------------------------------------

    /**
     * CEA input deck for LOX/CH4:
     * <pre>
     * problem  tp
     *   p(bar) = 70.0
     *   t(k)   = 3200
     * react
     *   oxidizer = O2   wt = 1.0   t(k) = 90.17
     *   fuel = CH4  wt = 1.0   t(k) = 111.7
     *   o/f  = 3.5
     * output  mass fractions
     * end
     * </pre>
     *
     * <p>LOX/CH4 at O/F=3.5 is near-stoichiometric (stoic ~4.0). Expect a
     * mix of H2O, CO2, CO, H2, OH with significant CO at this temperature.
     */
    @Nested
    @DisplayName("LOX/CH4  O/F=3.5  T=3200 K  P=7 MPa")
    class LoxCh4_3200K_7MPa {

        // ---- CEA reference values — NASA CEA2 tp problem (2004), gaseous CH4 fuel ----
        private static final double REF_H2O = 0.44000;
        private static final double REF_CO2 = 0.34614;
        private static final double REF_CO  = 0.16768;
        private static final double REF_H2  = 0.00481;
        private static final double REF_OH  = 0.02452;

        private Map<String, Double> result;

        @BeforeEach
        void solve() {
            result = minimizer.minimize(ch4Initial(3.5), 3200, 7e6);
        }

        @Test
        @DisplayName("H2O mass fraction matches CEA to 3%")
        void h2oMajorSpecies() {
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isCloseTo(REF_H2O, within(REF_H2O * 0.03));
        }

        @Test
        @DisplayName("CO2 mass fraction matches CEA to 10%")
        void co2Intermediate() {
            assertThat(result.getOrDefault("CO2", 0.0))
                    .isCloseTo(REF_CO2, within(REF_CO2 * 0.10));
        }

        @Test
        @DisplayName("CO mass fraction matches CEA to 10%")
        void coIntermediate() {
            assertThat(result.getOrDefault("CO", 0.0))
                    .isCloseTo(REF_CO, within(REF_CO * 0.10));
        }

        @Test
        @DisplayName("H2 mass fraction matches CEA to 10%")
        void h2Intermediate() {
            assertThat(result.getOrDefault("H2", 0.0))
                    .isCloseTo(REF_H2, within(REF_H2 * 0.10));
        }

        @Test
        @DisplayName("OH mass fraction matches CEA to 10%")
        void ohIntermediate() {
            assertThat(result.getOrDefault("OH", 0.0))
                    .isCloseTo(REF_OH, within(REF_OH * 0.10));
        }
    }

    // -----------------------------------------------------------------------
    // LOX / RP-1  —  O/F = 2.77  —  3000 K / 7 MPa
    // -----------------------------------------------------------------------

    /**
     * CEA input deck for LOX/RP-1:
     * <pre>
     * problem  tp
     *   p(bar) = 70.0
     *   t(k)   = 3000
     * react
     *   oxidizer = O2   wt = 1.0   t(k) = 90.17
     *   fuel     = C12H26  wt = 1.0  t(k) = 300.0   ! RP-1 approximation
     *   o/f      = 2.77
     * output  mass fractions
     * end
     * </pre>
     *
     * <p>Note: CEA's "C12H26" is a reasonable RP-1 surrogate. The code's
     * {@code setLoxRp1} method uses a similar C/H ratio. At O/F=2.77 (fuel-rich, stoic ~3.4)
     * expect CO, H2, H2O and some CO2.
     */
    @Nested
    @DisplayName("LOX/RP-1  O/F=2.77  T=3000 K  P=7 MPa")
    class LoxRp1_3000K_7MPa {

        // ---- CEA reference values — NASA CEA2 tp problem (2004), native RP-1 species ----
        private static final double REF_H2O = 0.29241;
        private static final double REF_CO  = 0.25004;
        private static final double REF_CO2 = 0.44236;
        private static final double REF_H2  = 0.00393;

        private Map<String, Double> result;

        @BeforeEach
        void solve() {
            result = minimizer.minimize(rp1Initial(2.77), 3000, 7e6);
        }

        @Test
        @DisplayName("H2O mass fraction matches CEA to 5%")
        void h2oMajorSpecies() {
            assertThat(result.getOrDefault("H2O", 0.0))
                    .isCloseTo(REF_H2O, within(REF_H2O * 0.05));
        }

        @Test
        @DisplayName("CO mass fraction matches CEA to 10%")
        void coIntermediate() {
            assertThat(result.getOrDefault("CO", 0.0))
                    .isCloseTo(REF_CO, within(REF_CO * 0.10));
        }

        @Test
        @DisplayName("CO2 mass fraction matches CEA to 10%")
        void co2Intermediate() {
            assertThat(result.getOrDefault("CO2", 0.0))
                    .isCloseTo(REF_CO2, within(REF_CO2 * 0.10));
        }

        @Test
        @DisplayName("H2 mass fraction matches CEA to 10%")
        void h2Intermediate() {
            assertThat(result.getOrDefault("H2", 0.0))
                    .isCloseTo(REF_H2, within(REF_H2 * 0.10));
        }
    }
}
