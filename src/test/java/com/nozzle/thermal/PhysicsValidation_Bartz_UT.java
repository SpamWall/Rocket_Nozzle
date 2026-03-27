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

package com.nozzle.thermal;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.geometry.NozzleContour;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Validates the Bartz convective heat transfer implementation
 * against known analytical results and scaling laws.
 *
 * <h2>Reference formula (Bartz 1957 with Eckert reference temperature)</h2>
 * <pre>
 *   h = (0.026 / Dt^0.2) · (μ*^0.2 · Cp* / Pr^0.6) · G*^0.8 · (Dt/rc)^0.1 · (At/A)^0.9
 * </pre>
 * where G* = Pc / c*, T* = 0.5(Tw + T_static) + 0.22√Pr · (T_aw − T_static).
 *
 * <h2>Key validated scaling laws</h2>
 * <ul>
 *   <li>h ∝ Pc^0.8  (G* = Pc/c*, and c* is independent of Pc)</li>
 *   <li>h ∝ Dt^{−0.8}  (combines the Dt^{−0.2} prefactor and G*^0.8 ∝ Dt^0 ∝ const,
 *       but the (At/A_local)^0.9 area-ratio factor makes at-throat h ∝ At^{0.9−1} → Dt^{−0.2+...}.
 *       Net effect: for a throat point where At/A=1 and rc ∝ Dt, h ∝ Dt^{−0.2} × Dt^{0.1} = Dt^{−0.1}.
 *       However, the dominant Pc^0.8 and Dt scaling are tested independently.</li>
 *   <li>h_throat / h_exit ≈ (A_exit/A_throat)^0.9  (area-ratio dependence)</li>
 *   <li>h peaks at the throat, not at the exit</li>
 * </ul>
 */
@DisplayName("Physics Validation — Bartz Heat Transfer")
class PhysicsValidation_Bartz_UT {

    /**
     * Builds a standard set of parameters and a 50-point RAO contour.
     * The contour is generated identically in every test so point indices
     * and x-locations are comparable.
     */
    private static final double RT        = 0.05;   // m  throat radius
    private static final double PC_BASE   = 7e6;    // Pa
    private static final double TC        = 3500.0; // K
    private static final double PA        = 101325.0;
    private static final double EXIT_MACH = 2.5;

    private NozzleDesignParameters baseParams;
    private NozzleContour          baseContour;

    @BeforeEach
    void setUp() {
        baseParams = buildParams(RT, PC_BASE, TC, EXIT_MACH, GasProperties.LOX_RP1_PRODUCTS);
        baseContour = buildContour(baseParams, 50);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static NozzleDesignParameters buildParams(double rt, double pc, double tc,
                                                       double exitMach, GasProperties gas) {
        return NozzleDesignParameters.builder()
                .throatRadius(rt).exitMach(exitMach)
                .chamberPressure(pc).chamberTemperature(tc)
                .ambientPressure(PA)
                .gasProperties(gas)
                .numberOfCharLines(10)
                .wallAngleInitialDegrees(25).lengthFraction(0.8)
                .axisymmetric(true).build();
    }

    private static NozzleContour buildContour(NozzleDesignParameters params, int nPoints) {
        NozzleContour c = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        c.generate(nPoints);
        return c;
    }

    private static List<HeatTransferModel.WallThermalPoint> profile(
            NozzleDesignParameters params, NozzleContour contour) {
        return new HeatTransferModel(params, contour)
                .setCoolantProperties(300.0, 10_000.0)
                .calculate(List.of())
                .getWallThermalProfile();
    }

    // -----------------------------------------------------------------------
    // Pc^0.8 scaling law
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Pc^0.8 scaling law")
    class PressureScalingTests {

        @Test
        @DisplayName("Doubling Pc multiplies peak h by 2^0.8 = 1.7411 (±3%)")
        void doublingPcMultipliesHByCorrectFactor() {
            double pc2 = PC_BASE * 2;
            NozzleDesignParameters params2 =
                    buildParams(RT, pc2, TC, EXIT_MACH, GasProperties.LOX_RP1_PRODUCTS);
            NozzleContour contour2 = buildContour(params2, 49);

            double hBase = peakH(profile(baseParams, baseContour));
            double hHigh = peakH(profile(params2, contour2));

            double expected = Math.pow(2.0, 0.8);   // 1.7411
            assertThat(hHigh / hBase)
                    .as("h ratio (2×Pc vs base Pc) should be ≈ 2^0.8")
                    .isCloseTo(expected, within(expected * 0.03));  // 3%
        }

        @Test
        @DisplayName("Quadrupling Pc multiplies peak h by 4^0.8 = 3.031 (±5%)")
        void quadruplingPcMultipliesHByCorrectFactor() {
            double pc4 = PC_BASE * 4;
            NozzleDesignParameters params4 =
                    buildParams(RT, pc4, TC, EXIT_MACH, GasProperties.LOX_RP1_PRODUCTS);
            NozzleContour contour4 = buildContour(params4, 50);

            double hBase = peakH(profile(baseParams, baseContour));
            double hHigh = peakH(profile(params4, contour4));

            double expected = Math.pow(4.0, 0.8);   // 3.031
            assertThat(hHigh / hBase)
                    .as("h ratio (4×Pc vs base Pc)")
                    .isCloseTo(expected, within(expected * 0.05));  // 5%
        }

        private double peakH(List<HeatTransferModel.WallThermalPoint> pts) {
            return pts.stream()
                    .mapToDouble(HeatTransferModel.WallThermalPoint::heatTransferCoeff)
                    .max().orElseThrow();
        }
    }

    // -----------------------------------------------------------------------
    // (At/A)^0.9 area-ratio dependence
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("(At/A)^0.9 area-ratio factor")
    class AreaRatioScalingTests {

        /**
         * For two positions along the nozzle with area ratios AR1 and AR2
         * (where AR = A/At), the Bartz equation predicts:
         *   h(1) / h(2) ≈ (AR2/AR1)^0.9   (ignoring μ* and rc variation)
         * <p>
         * We verify this approximately between the throat region (AR≈1) and the
         * exit (AR = A_e/At).  We allow ±20% because μ* varies with temperature
         * and the curvature term also changes, but the dominant (At/A)^0.9 must
         * be the primary driver.
         */
        @Test
        @DisplayName("h_throat / h_exit ≥ (A_exit/A_throat)^0.7  (area-ratio lower bound)")
        void throatToExitRatioExceedsAreaRatioLowerBound() {
            List<HeatTransferModel.WallThermalPoint> pts = profile(baseParams, baseContour);

            // throat ≈ minimum y (first contour point after x < 0 boundary, or just take the
            // point with the highest h which empirically is at the throat region)
            double hThroat = pts.stream()
                    .mapToDouble(HeatTransferModel.WallThermalPoint::heatTransferCoeff)
                    .max().orElseThrow();
            double hExit = pts.getLast().heatTransferCoeff();

            double ar = baseParams.exitAreaRatio();  // A_exit / A_throat
            double lowerBound = Math.pow(ar, 0.7);  // conservative lower bound (pure (At/A)^0.9 would give ar^0.9)

            assertThat(hThroat / hExit)
                    .as("h_throat/h_exit >= (A_e/A_t)^0.7  (area-ratio driven cooling concentration)")
                    .isGreaterThan(lowerBound);
        }

        @Test
        @DisplayName("h_throat / h_exit upper bound: cannot exceed (A_exit/A_throat)^1.2")
        void throatToExitRatioDoesNotExceedAreaRatioUpperBound() {
            List<HeatTransferModel.WallThermalPoint> pts = profile(baseParams, baseContour);

            double hThroat = pts.stream()
                    .mapToDouble(HeatTransferModel.WallThermalPoint::heatTransferCoeff)
                    .max().orElseThrow();
            double hExit = pts.getLast().heatTransferCoeff();

            double ar = baseParams.exitAreaRatio();
            double upperBound = Math.pow(ar, 1.2);  // looser upper bound

            assertThat(hThroat / hExit)
                    .as("h ratio should not be wildly larger than area-ratio to the power 0.9")
                    .isLessThan(upperBound);
        }

        @Test
        @DisplayName("Higher exit Mach (larger A_e/A_t) gives larger h_throat/h_exit ratio")
        void largerAreaRatioGivesLargerHRatio() {
            NozzleDesignParameters params3 =
                    buildParams(RT, PC_BASE, TC, 3.0, GasProperties.LOX_RP1_PRODUCTS);
            NozzleContour contour3 = buildContour(params3, 50);

            List<HeatTransferModel.WallThermalPoint> pts25 = profile(baseParams, baseContour);   // M_e=2.5
            List<HeatTransferModel.WallThermalPoint> pts30 = profile(params3, contour3);         // M_e=3.0

            double ratio25 = peakH(pts25) / pts25.getLast().heatTransferCoeff();
            double ratio30 = peakH(pts30) / pts30.getLast().heatTransferCoeff();

            assertThat(ratio30).as("h_peak/h_exit at Me=3.0 > ratio at Me=2.5").isGreaterThan(ratio25);
        }

        private double peakH(List<HeatTransferModel.WallThermalPoint> pts) {
            return pts.stream()
                    .mapToDouble(HeatTransferModel.WallThermalPoint::heatTransferCoeff)
                    .max().orElseThrow();
        }
    }

    // -----------------------------------------------------------------------
    // Absolute magnitude: analytically computed Bartz h at the throat
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Absolute Bartz h magnitude at the throat")
    class AbsoluteMagnitudeTests {

        /**
         * Independently calculates the Bartz h at the throat using the published formula
         * (Bartz 1957, eq. 3-23 in Sutton & Biblarz 9th ed.) and compares with the
         * value produced by the model.
         *
         * <p>At the throat:  A = At  →  (At/A)^0.9 = 1.
         * We assume rc ≈ rt (the code caps rc at 10·rt for near-straight sections but at
         * the highly-curved throat rc should be close to rt).  We therefore use Dt/rc ≈ 1
         * for the curvature term (within the ±30% tolerance applied below).
         *
         * <p>The large (±30%) tolerance reflects genuine model uncertainty:
         * the iterative T* loop, numerical rc estimation from finite differences,
         * and the discrete contour grid all contribute.  What we are validating is
         * that the model is in the right order of magnitude and direction — not that
         * it has been incorrectly normalized or has a unit error.
         */
        @Test
        @DisplayName("Throat h within ±30% of analytically computed Bartz value")
        void throatHWithinOrderOfMagnitudeOfBartz() {
            // Parameters
            GasProperties gas = GasProperties.LOX_RP1_PRODUCTS;
            double gamma = gas.gamma();                       // 1.24
            double Pr    = 4.0 * gamma / (9.0 * gamma - 5); // Eucken relation
            double Cp    = gas.specificHeatCp();
            double Dt    = 2 * RT;                           // 0.10 m
           // 7e6 Pa
           double cStar = baseParams.characteristicVelocity();
            double GStar = PC_BASE / cStar;                       // mass flux

            // Reference temperature T* at throat — assume Tw ≈ 0.5 T_aw, T_aw ≈ 0.9·Tc
            double Tc    = TC;
            double T_static_throat = Tc * 2.0 / (gamma + 1); // throat static T
            double T_aw  = T_static_throat * (1.0 + Math.pow(Pr, 1.0/3.0) * (gamma - 1) / 2.0);
            double T_w   = 0.5 * T_aw;                       // typical cooled wall
            double T_star = 0.5 * (T_w + T_static_throat) + 0.22 * Math.sqrt(Pr) * (T_aw - T_static_throat);

            double mu_star = gas.calculateViscosity(T_star);
            double rc      = RT;  // approximate: curvature radius ≈ throat radius

            // Bartz formula (At/A = 1 at throat, Dt/rc ≈ 2rt/rt = 2)
            double hExpected = (0.026 / Math.pow(Dt, 0.2))
                    * (Math.pow(mu_star, 0.2) * Cp / Math.pow(Pr, 0.6))
                    * Math.pow(GStar, 0.8)
                    * Math.pow(Dt / rc, 0.1)
                    * 1.0;  // (At/At)^0.9 = 1

            // Find the point in the profile with highest h (≈ throat)
            List<HeatTransferModel.WallThermalPoint> pts = profile(baseParams, baseContour);
            double hActual = pts.stream()
                    .mapToDouble(HeatTransferModel.WallThermalPoint::heatTransferCoeff)
                    .max().orElseThrow();

            assertThat(hActual)
                    .as("Peak Bartz h (≈throat) vs analytically computed h_Bartz=%.2f W/(m²K)", hExpected)
                    .isCloseTo(hExpected, within(hExpected * 0.30));   // ±30%
        }

        @Test
        @DisplayName("Peak h is in the expected rocket-engine order of magnitude (1e4–1e7 W/m²K)")
        void peakHInPhysicalRange() {
            List<HeatTransferModel.WallThermalPoint> pts = profile(baseParams, baseContour);
            double hPeak = pts.stream()
                    .mapToDouble(HeatTransferModel.WallThermalPoint::heatTransferCoeff)
                    .max().orElseThrow();

            // Practical rocket engines: 10 000–10 000 000 W/(m²·K) at the throat
            assertThat(hPeak)
                    .as("Bartz h must be in the engineering range for rocket nozzles")
                    .isBetween(1e4, 1e7);
        }
    }

    // -----------------------------------------------------------------------
    // Recovery / adiabatic wall temperature physics
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Recovery temperature physics")
    class RecoveryTemperatureTests {

        /**
         * T_aw = T_static × (1 + r·(γ−1)/2·M²)
         * where the recovery factor r = Pr^(1/3) for turbulent BL.
         * At the throat M=1, T_static = Tc·2/(γ+1).
         * So T_aw_throat = T_static_throat × (1 + Pr^(1/3)·(γ−1)/2).
         */
        @Test
        @DisplayName("Recovery temperature formula: T_aw = T_static × (1 + r·(γ-1)/2·M²)")
        void recoveryTemperatureMatchesFormula() {
            // When calculate() is called with an empty flow-point list, HeatTransferModel
            // uses a fallback: T_static = 0.8·Tc, M_fallback = 2.0.
            // We independently apply the same formula and verify the stored T_aw matches.
            GasProperties gas = GasProperties.LOX_RP1_PRODUCTS;
            double gamma  = gas.gamma();
            double Pr     = 4.0 * gamma / (9.0 * gamma - 5);
            double r      = Math.pow(Pr, 1.0 / 3.0);   // turbulent recovery factor
            double TStatic = TC * 0.8;                  // fallback in model
            double mFallback = 2.0;                      // fallback M
            double expectedTaw = TStatic * (1.0 + r * (gamma - 1) / 2.0 * mFallback * mFallback);

            List<HeatTransferModel.WallThermalPoint> pts = profile(baseParams, baseContour);

            // All points use the same fallback conditions, so T_aw should be uniform
            for (HeatTransferModel.WallThermalPoint p : pts) {
                assertThat(p.recoveryTemperature())
                        .as("T_aw at x=%.4f using fallback M=2 formula", p.x())
                        .isCloseTo(expectedTaw, within(expectedTaw * 0.001));  // 0.1%
            }
        }

        @Test
        @DisplayName("T_aw > T_wall at every point (wall is cooled, not adiabatic)")
        void recoveryTempExceedsWallTemp() {
            List<HeatTransferModel.WallThermalPoint> pts = profile(baseParams, baseContour);
            for (HeatTransferModel.WallThermalPoint p : pts) {
                assertThat(p.recoveryTemperature())
                        .as("T_aw > T_w at x=%.4f", p.x())
                        .isGreaterThan(p.wallTemperature());
            }
        }

        @Test
        @DisplayName("T_wall > T_coolant at every point (steady-state heat flow is inward)")
        void wallTempExceedsCoolantTemp() {
            double coolantTemp = 300.0;
            List<HeatTransferModel.WallThermalPoint> pts =
                    new HeatTransferModel(baseParams, baseContour)
                            .setCoolantProperties(coolantTemp, 10_000.0)
                            .calculate(List.of())
                            .getWallThermalProfile();

            for (HeatTransferModel.WallThermalPoint p : pts) {
                assertThat(p.wallTemperature())
                        .as("T_w > T_coolant at x=%.4f", p.x())
                        .isGreaterThan(coolantTemp);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Coolant h-coefficient influence on wall temperature
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Coolant-side heat transfer coefficient effect")
    class CoolantEffectTests {

        @Test
        @DisplayName("Higher coolant h_c → lower wall temperature (better cooling)")
        void higherCoolantHReducesWallTemp() {
            HeatTransferModel lowCoolant = new HeatTransferModel(baseParams, baseContour)
                    .setCoolantProperties(300.0, 1_000.0).calculate(List.of());
            HeatTransferModel highCoolant = new HeatTransferModel(baseParams, baseContour)
                    .setCoolantProperties(300.0, 50_000.0).calculate(List.of());

            assertThat(highCoolant.getMaxWallTemperature())
                    .isLessThan(lowCoolant.getMaxWallTemperature());
        }

        @Test
        @DisplayName("Increasing h_c monotonically reduces max wall temperature")
        void increasingHcReducesWallTemperatureMonotonically() {
            double coolantTemp = 300.0;
            double[] hcValues = {500.0, 2_000.0, 10_000.0, 50_000.0, 200_000.0};
            double prevMaxWall = Double.MAX_VALUE;
            for (double hc : hcValues) {
                double maxWall = new HeatTransferModel(baseParams, baseContour)
                        .setCoolantProperties(coolantTemp, hc)
                        .calculate(List.of())
                        .getMaxWallTemperature();
                assertThat(maxWall)
                        .as("Max wall temp should decrease as h_c increases to %.0f W/(m²K)", hc)
                        .isLessThan(prevMaxWall);
                prevMaxWall = maxWall;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Gas type variation
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Gas type variation")
    class GasTypeVariationTests {

        /**
         * LOX/LH2 has dramatically higher Cp (≈3360 vs ≈1870 J/kg·K for LOX/RP-1)
         * and a lower Prandtl number. Despite lower G* due to higher c*, the Cp term
         * in the Bartz numerator (μ*^0.2 · Cp / Pr^0.6) dominates and LOX/LH2
         * produces higher h at the same Pc, Tc, and nozzle geometry.
         */
        @Test
        @DisplayName("LOX/LH2 produces higher peak h than LOX/RP-1 at same Pc, Tc, geometry")
        void loxLh2GivesHigherPeakHThanRp1() {
            NozzleDesignParameters paramsLH2 =
                    buildParams(RT, PC_BASE, TC, EXIT_MACH, GasProperties.LOX_LH2_PRODUCTS);
            NozzleContour contourLH2 = buildContour(paramsLH2, 50);

            double hRp1 = peakH(profile(baseParams, baseContour));
            double hLh2 = peakH(profile(paramsLH2, contourLH2));

            assertThat(hLh2)
                    .as("LOX/LH2 peak h (%.2f) should exceed LOX/RP-1 (%.2f) due to higher Cp",
                            hLh2, hRp1)
                    .isGreaterThan(hRp1);
        }

        @Test
        @DisplayName("LOX/LH2 vs LOX/RP-1 peak h ratio is in the physically expected range (1.1–3.0)")
        void loxLh2ToRp1HRatioInPhysicalRange() {
            NozzleDesignParameters paramsLH2 =
                    buildParams(RT, PC_BASE, TC, EXIT_MACH, GasProperties.LOX_LH2_PRODUCTS);
            NozzleContour contourLH2 = buildContour(paramsLH2, 50);

            double hRp1 = peakH(profile(baseParams, baseContour));
            double hLh2 = peakH(profile(paramsLH2, contourLH2));

            assertThat(hLh2 / hRp1)
                    .as("h(LH2) / h(RP-1) should reflect the Cp and G* difference")
                    .isBetween(1.1, 3.0);
        }

        @Test
        @DisplayName("All three propellant gas types produce peak h in the engineering range (1e4–1e7 W/m²K)")
        void allGasTypesProducePhysicallyPlausibleH() {
            List<GasProperties> gases = List.of(
                    GasProperties.LOX_RP1_PRODUCTS,
                    GasProperties.LOX_LH2_PRODUCTS,
                    GasProperties.LOX_CH4_PRODUCTS);
            for (GasProperties gas : gases) {
                NozzleDesignParameters params = buildParams(RT, PC_BASE, TC, EXIT_MACH, gas);
                NozzleContour contour = buildContour(params, 50);
                double h = peakH(profile(params, contour));
                assertThat(h)
                        .as("Peak h for %s must be in the engineering range", gas)
                        .isBetween(1e4, 1e7);
            }
        }

        private double peakH(List<HeatTransferModel.WallThermalPoint> pts) {
            return pts.stream()
                    .mapToDouble(HeatTransferModel.WallThermalPoint::heatTransferCoeff)
                    .max().orElseThrow();
        }
    }

    // -----------------------------------------------------------------------
    // Throat radius scaling: h ∝ Dt^{-0.2}
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Throat radius scaling: h ∝ Dt^{-0.2}")
    class ThroatRadiusScalingTests {

        /**
         * The Bartz prefactor is 0.026 / Dt^0.2. Under uniform geometric scaling
         * (same Pc, Tc, exit Mach), G* = Pc/c* is unchanged and the area ratios
         * At/A and curvature ratio Dt/rc are also invariant, so the net change in
         * peak h is exactly the 1/Dt^0.2 prefactor: doubling Dt → h × 2^{−0.2} ≈ 0.870.
         */
        @Test
        @DisplayName("Doubling throat radius reduces peak h by factor 2^0.2 = 0.870 (±10%)")
        void doublingThroatRadiusReducesPeakHByCorrectFactor() {
            NozzleDesignParameters paramsLarge =
                    buildParams(RT * 2.0, PC_BASE, TC, EXIT_MACH, GasProperties.LOX_RP1_PRODUCTS);
            NozzleContour contourLarge = buildContour(paramsLarge, 50);

            double hBase  = peakH(profile(baseParams, baseContour));
            double hLarge = peakH(profile(paramsLarge, contourLarge));

            double expected = Math.pow(2.0, -0.2);   // 0.8706
            assertThat(hLarge / hBase)
                    .as("h(2×Dt) / h(Dt) should be ≈ 2^{−0.2} = %.4f", expected)
                    .isCloseTo(expected, within(expected * 0.10));  // ±10%
        }

        @Test
        @DisplayName("Smaller throat radius gives higher peak h (inverse Dt^0.2 dependence)")
        void smallerThroatRadiusGivesHigherPeakH() {
            NozzleDesignParameters paramsSmall =
                    buildParams(RT * 0.5, PC_BASE, TC, EXIT_MACH, GasProperties.LOX_RP1_PRODUCTS);
            NozzleContour contourSmall = buildContour(paramsSmall, 50);

            double hBase  = peakH(profile(baseParams, baseContour));
            double hSmall = peakH(profile(paramsSmall, contourSmall));

            assertThat(hSmall)
                    .as("Smaller throat (0.5×Dt) should give higher peak h")
                    .isGreaterThan(hBase);
        }

        private double peakH(List<HeatTransferModel.WallThermalPoint> pts) {
            return pts.stream()
                    .mapToDouble(HeatTransferModel.WallThermalPoint::heatTransferCoeff)
                    .max().orElseThrow();
        }
    }

    // -----------------------------------------------------------------------
    // Chamber temperature effect on h
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Chamber temperature effect on peak h")
    class ChamberTemperatureTests {

        /**
         * Higher Tc increases c* (∝ √Tc), reducing the throat mass flux G* = Pc/c*.
         * The dominant G*^0.8 term scales as Tc^{−0.4}, while the weak μ*^0.2 term
         * rises as Tc^{+0.1} (Sutherland at high T). Net: h ∝ Tc^{−0.3}, so peak h
         * falls as Tc increases at constant Pc.
         */
        @Test
        @DisplayName("Higher chamber temperature gives lower peak h at constant Pc")
        void higherTcReducesPeakH() {
            NozzleDesignParameters paramsHot =
                    buildParams(RT, PC_BASE, TC * 2.0, EXIT_MACH, GasProperties.LOX_RP1_PRODUCTS);
            NozzleContour contourHot = buildContour(paramsHot, 50);

            double hBase = peakH(profile(baseParams, baseContour));
            double hHot  = peakH(profile(paramsHot, contourHot));

            assertThat(hHot)
                    .as("Peak h should decrease when Tc doubles (G* = Pc/c* ∝ Tc^{−0.5})")
                    .isLessThan(hBase);
        }

        @Test
        @DisplayName("Peak h decreases monotonically as Tc rises from 2000 K to 5000 K")
        void hDecreasesMonotonicallyWithRisingTc() {
            double[] tcValues = {2000.0, 3000.0, 4000.0, 5000.0};
            double prevH = Double.MAX_VALUE;
            for (double tc : tcValues) {
                NozzleDesignParameters params =
                        buildParams(RT, PC_BASE, tc, EXIT_MACH, GasProperties.LOX_RP1_PRODUCTS);
                NozzleContour contour = buildContour(params, 50);
                double h = peakH(profile(params, contour));
                assertThat(h)
                        .as("Peak h at Tc=%.0f K should be less than at previous (lower) Tc", tc)
                        .isLessThan(prevH);
                prevH = h;
            }
        }

        private double peakH(List<HeatTransferModel.WallThermalPoint> pts) {
            return pts.stream()
                    .mapToDouble(HeatTransferModel.WallThermalPoint::heatTransferCoeff)
                    .max().orElseThrow();
        }
    }

    // -----------------------------------------------------------------------
    // Warm coolant temperature variation
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Warm coolant temperature variation")
    class WarmCoolantTests {

        @Test
        @DisplayName("T_wall > T_coolant at every point for warm (500 K) coolant")
        void wallTempExceedsWarmCoolantTemp() {
            double warmCoolant = 500.0;
            List<HeatTransferModel.WallThermalPoint> pts =
                    new HeatTransferModel(baseParams, baseContour)
                            .setCoolantProperties(warmCoolant, 10_000.0)
                            .calculate(List.of())
                            .getWallThermalProfile();

            for (HeatTransferModel.WallThermalPoint p : pts) {
                assertThat(p.wallTemperature())
                        .as("T_w > T_coolant (500 K) at x=%.4f", p.x())
                        .isGreaterThan(warmCoolant);
            }
        }

        @Test
        @DisplayName("T_aw > T_wall at every point for warm (500 K) coolant (wall remains cooled)")
        void recoveryTempExceedsWallTempWithWarmCoolant() {
            List<HeatTransferModel.WallThermalPoint> pts =
                    new HeatTransferModel(baseParams, baseContour)
                            .setCoolantProperties(500.0, 10_000.0)
                            .calculate(List.of())
                            .getWallThermalProfile();

            for (HeatTransferModel.WallThermalPoint p : pts) {
                assertThat(p.recoveryTemperature())
                        .as("T_aw > T_w at x=%.4f (wall still cooled despite warm coolant)", p.x())
                        .isGreaterThan(p.wallTemperature());
            }
        }

        @Test
        @DisplayName("Warmer coolant gives higher max wall temperature (reduced thermal driving force)")
        void warmerCoolantIncreasesMaxWallTemp() {
            double hc = 10_000.0;
            double maxWall300 = new HeatTransferModel(baseParams, baseContour)
                    .setCoolantProperties(300.0, hc).calculate(List.of()).getMaxWallTemperature();
            double maxWall500 = new HeatTransferModel(baseParams, baseContour)
                    .setCoolantProperties(500.0, hc).calculate(List.of()).getMaxWallTemperature();
            double maxWall800 = new HeatTransferModel(baseParams, baseContour)
                    .setCoolantProperties(800.0, hc).calculate(List.of()).getMaxWallTemperature();

            assertThat(maxWall500)
                    .as("500 K coolant → higher wall temp than 300 K coolant")
                    .isGreaterThan(maxWall300);
            assertThat(maxWall800)
                    .as("800 K coolant → higher wall temp than 500 K coolant")
                    .isGreaterThan(maxWall500);
        }
    }
}
