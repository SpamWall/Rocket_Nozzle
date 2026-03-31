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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Validates the {@link OFSweep} O/F sweep and optimum-search API in both
 * fixed-Tc and adiabatic modes.
 *
 * <h2>Validation strategy</h2>
 * <p>Tests verify relationships grounded in rocket propulsion theory and
 * independent of the exact Gibbs solver or NASA-7 coefficients in use.
 * Expected ranges are drawn from published sources (Sutton &amp; Biblarz 10th ed.,
 * Huzel &amp; Huang 2nd ed., NASA SP-8120).
 *
 * <h2>Shared nozzle conditions</h2>
 * <pre>
 *   Pc = 7 MPa,  Me = 3.0,  Pa = 101 325 Pa  (sea level)
 * </pre>
 */
@DisplayName("OFSweep — O/F sweep and optimum search")
class OFSweep_UT {

    private static final double PC = 7e6;
    private static final double ME = 3.0;
    private static final double PA = 101_325.0;

    private OFSweep adiabatic(OFSweep.Propellant p) {
        return OFSweep.adiabatic(p, PC, ME, PA);
    }

    // =========================================================================
    //  1. API contract
    // =========================================================================

    @Nested
    @DisplayName("API contract")
    class ApiContract {

        @Test
        @DisplayName("sweep() returns exactly 'points' entries")
        void sweepReturnsCorrectCount() {
            List<OFSweep.OFPoint> curve = adiabatic(OFSweep.Propellant.LOX_RP1)
                    .sweep(1.5, 4.5, 31);
            assertThat(curve).hasSize(31);
        }

        @Test
        @DisplayName("sweep() first and last O/F match the requested bounds")
        void sweepSpansRequestedRange() {
            List<OFSweep.OFPoint> curve = adiabatic(OFSweep.Propellant.LOX_RP1)
                    .sweep(1.5, 4.5, 11);
            assertThat(curve.getFirst().of()).isCloseTo(1.5, within(1e-9));
            assertThat(curve.getLast().of()).isCloseTo(4.5, within(1e-9));
        }

        @Test
        @DisplayName("sweep() O/F values are strictly ascending")
        void sweepValuesAscend() {
            List<OFSweep.OFPoint> curve = adiabatic(OFSweep.Propellant.LOX_RP1)
                    .sweep(1.5, 4.5, 11);
            for (int i = 1; i < curve.size(); i++) {
                assertThat(curve.get(i).of()).isGreaterThan(curve.get(i - 1).of());
            }
        }

        @Test
        @DisplayName("All performance quantities are positive at every sweep point")
        void allQuantitiesPositive() {
            List<OFSweep.OFPoint> curve = adiabatic(OFSweep.Propellant.LOX_RP1)
                    .sweep(1.5, 4.5, 11);
            for (OFSweep.OFPoint pt : curve) {
                assertThat(pt.chamberTemperature()).isGreaterThan(0.0);
                assertThat(pt.gamma()).isGreaterThan(0.0);
                assertThat(pt.molecularWeight()).isGreaterThan(0.0);
                assertThat(pt.cStar()).isGreaterThan(0.0);
                assertThat(pt.isp()).isGreaterThan(0.0);
            }
        }
    }

    // =========================================================================
    //  2. Adiabatic temperature model
    // =========================================================================

    /**
     * The adiabatic chamber temperature peaks near stoichiometric and is lower at
     * both fuel-rich and oxidizer-rich conditions — this is one of the most
     * fundamental results of combustion thermochemistry.
     */
    @Nested
    @DisplayName("Adiabatic chamber temperature profile")
    class AdiabaticTemperature {

        @Test
        @DisplayName("LOX/RP-1 Tc peaks near stoichiometric (O/F ≈ 3.4): hotter than O/F 1.5 and O/F 5.0")
        void loxRp1TcPeaksNearStoich() {
            OFSweep s = adiabatic(OFSweep.Propellant.LOX_RP1);
            double tc1p5 = s.computeAt(1.5).chamberTemperature();
            double tc3p4 = s.computeAt(3.4).chamberTemperature();
            double tc5p0 = s.computeAt(5.0).chamberTemperature();
            assertThat(tc3p4).isGreaterThan(tc1p5);
            assertThat(tc3p4).isGreaterThan(tc5p0);
        }

        @Test
        @DisplayName("LOX/RP-1 adiabatic Tc at near-stoichiometric is in plausible range (3000–4000 K)")
        void loxRp1TcPlausible() {
            double tc = adiabatic(OFSweep.Propellant.LOX_RP1).computeAt(3.4).chamberTemperature();
            assertThat(tc).isBetween(3000.0, 4000.0);
        }

        @Test
        @DisplayName("LOX/LH2 adiabatic Tc at near-stoichiometric is in plausible range (2500–4000 K)")
        void loxLh2TcPlausible() {
            double tc = adiabatic(OFSweep.Propellant.LOX_LH2).computeAt(8.0).chamberTemperature();
            assertThat(tc).isBetween(2500.0, 4000.0);
        }

        @Test
        @DisplayName("N2O/ethanol Tc is lower than LOX/RP-1 Tc at respective stoichiometric O/F")
        void n2oEthanolCoolerThanLoxRp1() {
            double tcN2O = adiabatic(OFSweep.Propellant.N2O_ETHANOL).computeAt(5.73).chamberTemperature();
            double tcRp1 = adiabatic(OFSweep.Propellant.LOX_RP1).computeAt(3.4).chamberTemperature();
            assertThat(tcN2O).isLessThan(tcRp1);
        }
    }

    // =========================================================================
    //  3. Thermodynamic sanity
    // =========================================================================

    @Nested
    @DisplayName("Thermodynamic sanity")
    class ThermodynamicSanity {

        @Test
        @DisplayName("γ < 1.4 for hot combustion products (high DOF reduces γ below diatomic limit)")
        void gammaSubDiatomic() {
            List<OFSweep.OFPoint> curve = adiabatic(OFSweep.Propellant.LOX_RP1)
                    .sweep(1.5, 4.5, 11);
            for (OFSweep.OFPoint pt : curve) {
                assertThat(pt.gamma())
                        .as("γ at O/F=%.2f should be below 1.4", pt.of())
                        .isLessThan(1.4);
            }
        }

        @Test
        @DisplayName("γ > 1.0 everywhere (thermodynamic stability requirement)")
        void gammaAboveUnity() {
            List<OFSweep.OFPoint> curve = adiabatic(OFSweep.Propellant.LOX_RP1)
                    .sweep(1.5, 4.5, 11);
            for (OFSweep.OFPoint pt : curve) {
                assertThat(pt.gamma())
                        .as("γ at O/F=%.2f must exceed 1", pt.of())
                        .isGreaterThan(1.0);
            }
        }

        @Test
        @DisplayName("c* is in the plausible range for LOX/RP-1 (1400–1900 m/s)")
        void cstarInPlausibleRange() {
            // Textbook range: ~1500–1800 m/s at 7 MPa (Sutton & Biblarz, Table 5-5)
            List<OFSweep.OFPoint> curve = adiabatic(OFSweep.Propellant.LOX_RP1)
                    .sweep(1.5, 4.5, 11);
            for (OFSweep.OFPoint pt : curve) {
                assertThat(pt.cStar())
                        .as("c* at O/F=%.2f", pt.of())
                        .isBetween(1400.0, 1900.0);
            }
        }

        @Test
        @DisplayName("Isp is in the plausible range for LOX/RP-1 at Me=3 (150–380 s)")
        void ispInPlausibleRange() {
            List<OFSweep.OFPoint> curve = adiabatic(OFSweep.Propellant.LOX_RP1)
                    .sweep(1.5, 4.5, 11);
            for (OFSweep.OFPoint pt : curve) {
                assertThat(pt.isp())
                        .as("Isp at O/F=%.2f", pt.of())
                        .isBetween(150.0, 380.0);
            }
        }
    }

    // =========================================================================
    //  4. Unimodal curve shape
    // =========================================================================

    @Nested
    @DisplayName("Curve shape — Isp and c* peak in the interior")
    class CurveShape {

        @Test
        @DisplayName("LOX/RP-1 Isp at O/F 2.5 exceeds Isp at both boundaries (O/F 1.5 and 4.5)")
        void loxRp1IspPeaksInterior() {
            OFSweep s = adiabatic(OFSweep.Propellant.LOX_RP1);
            double isp1p5 = s.computeAt(1.5).isp();
            double isp2p5 = s.computeAt(2.5).isp();
            double isp4p5 = s.computeAt(4.5).isp();
            assertThat(isp2p5).isGreaterThan(isp1p5);
            assertThat(isp2p5).isGreaterThan(isp4p5);
        }

        @Test
        @DisplayName("LOX/CH4 Isp peaks between O/F 2.0 and 5.0 (interior to sweep range)")
        void loxCh4IspPeakIsInterior() {
            OFSweep s = adiabatic(OFSweep.Propellant.LOX_CH4);
            OFSweep.OFPoint opt = s.optimumIsp(2.0, 5.5);
            assertThat(opt.of())
                    .as("LOX/CH4 Isp optimum O/F should be interior to [2.0, 5.5]")
                    .isBetween(2.1, 5.4);
        }
    }

    // =========================================================================
    //  5. optimumIsp — golden-section search
    // =========================================================================

    /**
     * The optimum O/F for common propellants at sea-level conditions is well
     * established.  The ranges below are from multiple published sources
     * (Sutton &amp; Biblarz 10th ed. Table 5-5; Huzel &amp; Huang 2nd ed. Fig 3-1)
     * and are intentionally wide to accommodate the simplified Gibbs solver and
     * frozen-at-chamber approximation used here.
     */
    @Nested
    @DisplayName("optimumIsp — golden-section search (adiabatic mode)")
    class OptimumIsp {

        @Test
        @DisplayName("LOX/RP-1 Isp-optimal O/F is in the textbook range [1.8, 3.5]")
        void loxRp1IspOptimumInRange() {
            OFSweep.OFPoint opt = adiabatic(OFSweep.Propellant.LOX_RP1).optimumIsp(1.5, 5.0);
            assertThat(opt.of())
                    .as("LOX/RP-1 Isp optimum O/F (textbook ≈ 2.5–2.8)")
                    .isBetween(1.8, 3.5);
        }

        @Test
        @DisplayName("LOX/CH4 Isp-optimal O/F is in the textbook range [2.0, 4.5]")
        void loxCh4IspOptimumInRange() {
            OFSweep.OFPoint opt = adiabatic(OFSweep.Propellant.LOX_CH4).optimumIsp(2.0, 5.5);
            assertThat(opt.of())
                    .as("LOX/CH4 Isp optimum O/F (textbook ≈ 3.2–3.6)")
                    .isBetween(2.0, 4.5);
        }

        @Test
        @DisplayName("LOX/LH2 Isp-optimal O/F is in the plausible range [2.0, 5.0]")
        void loxLh2IspOptimumInRange() {
            OFSweep.OFPoint opt = adiabatic(OFSweep.Propellant.LOX_LH2).optimumIsp(2.0, 8.0);
            assertThat(opt.of())
                    .as("LOX/LH2 Isp optimum O/F")
                    .isBetween(2.0, 5.0);
        }

        @Test
        @DisplayName("optimumIsp Isp is not less than any point in the discrete sweep (±1%)")
        void optimumIspNotBelowSweepMaximum() {
            OFSweep s = adiabatic(OFSweep.Propellant.LOX_RP1);
            double sweepMax = s.sweep(1.5, 5.0, 21).stream()
                    .mapToDouble(OFSweep.OFPoint::isp)
                    .max()
                    .orElseThrow();
            OFSweep.OFPoint opt = s.optimumIsp(1.5, 5.0);
            assertThat(opt.isp()).isCloseTo(sweepMax, within(sweepMax * 0.01));
        }
    }

    // =========================================================================
    //  6. optimumCstar vs optimumIsp
    // =========================================================================

    /**
     * The c* optimum lies at a lower O/F than the Isp optimum for
     * oxygen-bearing propellants in adiabatic mode.  The physical reason:
     * Cf increases with γ (higher γ → better expansion), and γ tends toward
     * the oxidiser-rich side, so c*·Cf peaks at higher O/F than c* alone.
     */
    @Nested
    @DisplayName("optimumCstar vs optimumIsp")
    class CstarVsIspOptimum {

        @Test
        @DisplayName("LOX/RP-1: c* optimal O/F < Isp optimal O/F")
        void cstarOptimumBelowIspOptimumLoxRp1() {
            OFSweep s = adiabatic(OFSweep.Propellant.LOX_RP1);
            double ofCstar = s.optimumCstar(1.5, 5.0).of();
            double ofIsp   = s.optimumIsp(1.5, 5.0).of();
            assertThat(ofCstar)
                    .as("c* optimal O/F should be less than Isp optimal O/F for LOX/RP-1")
                    .isLessThan(ofIsp);
        }

        @Test
        @DisplayName("LOX/CH4: c* optimal O/F < Isp optimal O/F")
        void cstarOptimumBelowIspOptimumLoxCh4() {
            OFSweep s = adiabatic(OFSweep.Propellant.LOX_CH4);
            double ofCstar = s.optimumCstar(2.0, 5.5).of();
            double ofIsp   = s.optimumIsp(2.0, 5.5).of();
            assertThat(ofCstar)
                    .as("c* optimal O/F should be less than Isp optimal O/F for LOX/CH4")
                    .isLessThan(ofIsp);
        }
    }

    // =========================================================================
    //  7. Propellant ranking
    // =========================================================================

    /**
     * LOX/LH2 delivers higher peak Isp than any hydrocarbon propellant — H₂ has
     * the lowest product molecular weight, which is the primary driver of Isp.
     */
    @Nested
    @DisplayName("Propellant ranking (adiabatic mode)")
    class PropellantRanking {

        @Test
        @DisplayName("LOX/LH2 peak Isp exceeds LOX/RP-1 peak Isp")
        void loxLh2OutperformsLoxRp1() {
            double ispLh2 = adiabatic(OFSweep.Propellant.LOX_LH2).optimumIsp(2.0, 8.0).isp();
            double ispRp1 = adiabatic(OFSweep.Propellant.LOX_RP1).optimumIsp(1.5, 5.0).isp();
            assertThat(ispLh2).isGreaterThan(ispRp1);
        }

        @Test
        @DisplayName("LOX/LH2 peak Isp exceeds LOX/CH4 peak Isp")
        void loxLh2OutperformsLoxCh4() {
            double ispLh2 = adiabatic(OFSweep.Propellant.LOX_LH2).optimumIsp(2.0, 8.0).isp();
            double ispCh4 = adiabatic(OFSweep.Propellant.LOX_CH4).optimumIsp(2.0, 5.5).isp();
            assertThat(ispLh2).isGreaterThan(ispCh4);
        }

        @Test
        @DisplayName("LOX/LH2 equilibrium product M̄ is much lower than LOX/RP-1 (H₂O vs CO₂/H₂O)")
        void loxLh2LowerMolecularWeight() {
            OFSweep.OFPoint lh2 = adiabatic(OFSweep.Propellant.LOX_LH2).optimumIsp(2.0, 8.0);
            OFSweep.OFPoint rp1 = adiabatic(OFSweep.Propellant.LOX_RP1).optimumIsp(1.5, 5.0);
            assertThat(lh2.molecularWeight()).isLessThan(rp1.molecularWeight());
        }

        @Test
        @DisplayName("LOX/LH2 Isp-optimal O/F is higher than LOX/RP-1 (higher stoichiometric O/F)")
        void loxLh2OptimalOfHigherThanLoxRp1() {
            double ofLh2 = adiabatic(OFSweep.Propellant.LOX_LH2).optimumIsp(2.0, 8.0).of();
            double ofRp1 = adiabatic(OFSweep.Propellant.LOX_RP1).optimumIsp(1.5, 5.0).of();
            assertThat(ofLh2).isGreaterThan(ofRp1);
        }
    }

    // =========================================================================
    //  8. Fixed-Tc mode API check
    // =========================================================================

    @Nested
    @DisplayName("Fixed-Tc mode")
    class FixedTcMode {

        @Test
        @DisplayName("Fixed-Tc sweep returns the specified temperature at every point")
        void fixedTcIsConstant() {
            double TC = 3500.0;
            OFSweep s = new OFSweep(OFSweep.Propellant.LOX_RP1, TC, PC, ME, PA);
            List<OFSweep.OFPoint> curve = s.sweep(1.5, 4.5, 11);
            for (OFSweep.OFPoint pt : curve) {
                assertThat(pt.chamberTemperature())
                        .as("Tc at O/F=%.2f must equal the fixed value", pt.of())
                        .isCloseTo(TC, within(0.01));
            }
        }

        @Test
        @DisplayName("Adiabatic Tc at near-stoichiometric is higher than fixed Tc=2800 K")
        void adiabaticHotterThanLowFixedTc() {
            double tcFixed    = new OFSweep(OFSweep.Propellant.LOX_RP1, 2800.0, PC, ME, PA)
                    .computeAt(3.0).chamberTemperature();
            double tcAdiabatic = adiabatic(OFSweep.Propellant.LOX_RP1)
                    .computeAt(3.0).chamberTemperature();
            assertThat(tcAdiabatic).isGreaterThan(tcFixed);
        }
    }

}
