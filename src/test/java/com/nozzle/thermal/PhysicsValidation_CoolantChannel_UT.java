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
 *  commercial purposes outside the restrictions involved by this copyright.
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
 * Validates CoolantChannel hydraulic correlations and boiling margin formula
 * against known analytical and published results.
 *
 * <h2>Correlations under test</h2>
 * <ul>
 *   <li><b>Pressure drop</b> — Darcy-Weisbach with Petukhov (1970) smooth-pipe friction factor:
 *       <pre>ΔP = f · (L / D_h) · ρV² / 2,   f = (0.790 ln Re − 1.64)⁻²</pre></li>
 *   <li><b>Heat transfer coefficient</b> — Gnielinski (1976):
 *       <pre>Nu = (f/8)(Re − 1000)Pr / [1 + 12.7 √(f/8) (Pr^{2/3} − 1)],   h = Nu · k / D_h</pre></li>
 *   <li><b>Saturation temperature</b> — Clausius-Clapeyron from the normal boiling point:
 *       <pre>T_sat(P) = T_sat,atm / [1 − (R_sp · T_sat,atm / h_vap) · ln(P / P_atm)]</pre>
 *       Reference values from NIST WebBook (water, 2025) and Perry's Chemical Engineers'
 *       Handbook 8th ed., Table 2-150.</li>
 *   <li><b>Boiling margin</b> — cold-wall temperature under convective heat flux:
 *       <pre>T_cw = T_bulk + q / h_c + q · δ / k_wall,   margin = T_sat(P) − T_cw</pre></li>
 * </ul>
 *
 * <h2>Test fluid and channel geometry</h2>
 * <p>WATER is used throughout because its saturation properties are published to high
 * precision and the resulting Reynolds number (Re ≈ 5000) falls squarely in the
 * Gnielinski turbulent regime, avoiding transition-interpolation effects.
 *
 * <pre>
 *   channel width  w = 3 mm,  height h = 5 mm,  N = 100 channels
 *   D_h = 2wh/(w+h) = 3.75 mm
 *   V   = ṁ / (ρ · N · w · h) = 2.0 / (998 × 100 × 0.003 × 0.005) = 1.336 m/s
 *   Re  = ρVD_h/μ = 5000,  Pr = μCp/k = 6.99
 *   f   = (0.790 ln 5000 − 1.64)⁻² = 0.03862
 *   Nu  = 40.4,  h = 6442 W/(m²·K),  ΔP/L = 9170 Pa/m
 * </pre>
 */
@DisplayName("Physics Validation — CoolantChannel")
class PhysicsValidation_CoolantChannel_UT {

    // Channel geometry — shared across all tests
    private static final double CHAN_W  = 0.003;    // m
    private static final double CHAN_H  = 0.005;    // m
    private static final int    N_CHAN  = 100;
    private static final double MDOT   = 2.0;       // kg/s (total)
    private static final double T_IN   = 300.0;     // K
    private static final double P_IN   = 5e6;       // Pa

    private NozzleContour contour;

    @BeforeEach
    void setUp() {
        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(10)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();
        contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        contour.generate(50);
    }

    // -------------------------------------------------------------------------
    // Inline reference implementations of the correlations
    // -------------------------------------------------------------------------

    /** Petukhov (1970) smooth-pipe Darcy friction factor. */
    private static double petukhovF(double Re) {
        if (Re <= 2300) return 64.0 / Re;
        return Math.pow(0.790 * Math.log(Re) - 1.64, -2.0);
    }

    /** Gnielinski (1976) Nusselt number; uses petukhovF internally. */
    private static double gnielinskiNu(double Re, double Pr) {
        double f = petukhovF(Re);
        return (f / 8.0) * (Re - 1000.0) * Pr
               / (1.0 + 12.7 * Math.sqrt(f / 8.0) * (Math.pow(Pr, 2.0 / 3.0) - 1.0));
    }

    // =========================================================================
    //  1. Darcy-Weisbach pressure drop
    // =========================================================================

    /**
     * Pressure drop is computed as ΔP = f · (L/D_h) · ρV²/2 summed over every
     * contour arc-length segment, where f is the Petukhov friction factor.  The
     * expected value is derived from the same inputs used to configure the channel
     * (properties of WATER, channel geometry, mass flow), so any mismatch would
     * indicate a coding error in the pressure-drop accumulation loop.
     */
    @Nested
    @DisplayName("Darcy-Weisbach pressure drop — Petukhov friction factor, WATER")
    class DarcyWeisbachPressureDrop {

        @Test
        @DisplayName("Total ΔP matches Darcy-Weisbach formula integrated over contour arc length")
        void totalDropMatchesDarcyWeisbach() {
            CoolantChannel channel = new CoolantChannel(contour)
                    .setChannelGeometry(N_CHAN, CHAN_W, CHAN_H, 0.001)
                    .setCoolant(CoolantChannel.CoolantProperties.WATER, MDOT, T_IN, P_IN)
                    .calculate();

            double D_h    = channel.hydraulicDiameter();
            double A_flow = channel.totalFlowArea();
            double rho    = CoolantChannel.CoolantProperties.WATER.density;
            double mu     = CoolantChannel.CoolantProperties.WATER.viscosity;
            double V      = MDOT / (rho * A_flow);
            double Re     = rho * V * D_h / mu;
            double f      = petukhovF(Re);
            double dPdL   = f * rho * V * V / (2.0 * D_h);

            // Contour arc length — sum of segment lengths from the returned profile
            List<CoolantChannel.ChannelPoint> profile = channel.getProfile();
            double arcLength = 0.0;
            for (int i = 1; i < profile.size(); i++) {
                double dx = profile.get(i).x() - profile.get(i - 1).x();
                double dy = profile.get(i).y() - profile.get(i - 1).y();
                arcLength += Math.sqrt(dx * dx + dy * dy);
            }

            double expectedDp = dPdL * arcLength;

            assertThat(channel.getTotalPressureDrop())
                    .as("ΔP vs Darcy-Weisbach (±2 %%)")
                    .isCloseTo(expectedDp, within(expectedDp * 0.02));
        }

        @Test
        @DisplayName("Coolant exit pressure equals inlet pressure minus total drop")
        void exitPressureConsistentWithDrop() {
            CoolantChannel channel = new CoolantChannel(contour)
                    .setChannelGeometry(N_CHAN, CHAN_W, CHAN_H, 0.001)
                    .setCoolant(CoolantChannel.CoolantProperties.WATER, MDOT, T_IN, P_IN)
                    .calculate();

            // In counter-flow the coolant inlet is the last profile point (nozzle exit end).
            // The coolant outlet (lowest pressure) is the first profile point (throat end).
            double pOutlet = channel.getProfile().getFirst().coolantPressure();
            double pInlet  = channel.getProfile().getLast().coolantPressure();

            assertThat(pInlet - pOutlet)
                    .as("pressure difference equals total drop")
                    .isCloseTo(channel.getTotalPressureDrop(), within(1.0));
        }
    }

    // =========================================================================
    //  2. Gnielinski (1976) heat transfer coefficient
    // =========================================================================

    /**
     * The heat transfer coefficient is constant across the profile because the
     * channel geometry is rectangular and uniform.  The expected value is computed
     * from the Gnielinski formula with the Petukhov friction factor and compared
     * against the value stored in every ChannelPoint.
     */
    @Nested
    @DisplayName("Gnielinski (1976) heat transfer coefficient — turbulent WATER")
    class GnielinskiHeatTransferCoeff {

        @Test
        @DisplayName("h matches Gnielinski: h = Nu·k/D_h  (Re ≈ 5000, Pr ≈ 7)")
        void hMatchesGnielinski() {
            CoolantChannel channel = new CoolantChannel(contour)
                    .setChannelGeometry(N_CHAN, CHAN_W, CHAN_H, 0.001)
                    .setCoolant(CoolantChannel.CoolantProperties.WATER, MDOT, T_IN, P_IN)
                    .calculate();

            double D_h     = channel.hydraulicDiameter();
            double A_flow  = channel.totalFlowArea();
            double rho     = CoolantChannel.CoolantProperties.WATER.density;
            double mu      = CoolantChannel.CoolantProperties.WATER.viscosity;
            double k       = CoolantChannel.CoolantProperties.WATER.conductivity;
            double V       = MDOT / (rho * A_flow);
            double Re      = rho * V * D_h / mu;
            double Pr      = CoolantChannel.CoolantProperties.WATER.prandtlNumber();
            double Nu_exp  = gnielinskiNu(Re, Pr);
            double h_exp   = Nu_exp * k / D_h;

            // h is constant along the profile — check the midpoint
            CoolantChannel.ChannelPoint mid =
                    channel.getProfile().get(channel.getProfile().size() / 2);

            assertThat(mid.heatTransferCoeff())
                    .as("h vs Gnielinski (±1 %%)")
                    .isCloseTo(h_exp, within(h_exp * 0.01));
        }

        @Test
        @DisplayName("Nu stored in profile matches Gnielinski formula")
        void nuMatchesGnielinski() {
            CoolantChannel channel = new CoolantChannel(contour)
                    .setChannelGeometry(N_CHAN, CHAN_W, CHAN_H, 0.001)
                    .setCoolant(CoolantChannel.CoolantProperties.WATER, MDOT, T_IN, P_IN)
                    .calculate();

            double D_h    = channel.hydraulicDiameter();
            double A_flow = channel.totalFlowArea();
            double rho    = CoolantChannel.CoolantProperties.WATER.density;
            double mu     = CoolantChannel.CoolantProperties.WATER.viscosity;
            double V      = MDOT / (rho * A_flow);
            double Re     = rho * V * D_h / mu;
            double Pr     = CoolantChannel.CoolantProperties.WATER.prandtlNumber();
            double Nu_exp = gnielinskiNu(Re, Pr);

            assertThat(channel.getProfile().getFirst().nusseltNumber())
                    .as("Nu vs Gnielinski formula (±1 %%)")
                    .isCloseTo(Nu_exp, within(Nu_exp * 0.01));
        }

        @Test
        @DisplayName("Re stored in profile matches ρVD_h/μ")
        void reynoldsMatchesFormula() {
            CoolantChannel channel = new CoolantChannel(contour)
                    .setChannelGeometry(N_CHAN, CHAN_W, CHAN_H, 0.001)
                    .setCoolant(CoolantChannel.CoolantProperties.WATER, MDOT, T_IN, P_IN)
                    .calculate();

            double D_h    = channel.hydraulicDiameter();
            double A_flow = channel.totalFlowArea();
            double rho    = CoolantChannel.CoolantProperties.WATER.density;
            double mu     = CoolantChannel.CoolantProperties.WATER.viscosity;
            double V      = MDOT / (rho * A_flow);
            double Re_exp = rho * V * D_h / mu;

            assertThat(channel.getProfile().getFirst().reynoldsNumber())
                    .as("Re vs ρVD_h/μ (±0.1 %%)")
                    .isCloseTo(Re_exp, within(Re_exp * 0.001));
        }
    }

    // =========================================================================
    //  3. Clausius-Clapeyron saturation temperature — WATER vs NIST steam tables
    // =========================================================================

    /**
     * The Clausius-Clapeyron equation linearises the Clausius-Clapeyron slope around
     * the normal boiling point.  For water it agrees with NIST steam-table values
     * (NIST WebBook, 2025; Perry's 8th ed. Table 2-150) to within ±2 K over the
     * pressure range 1–10 atm, which is tighter than the ±5 % engineering tolerance
     * on the pressure-drop correlation.
     *
     * <pre>
     *   P [atm]  T_sat,table [K]  T_sat,CC [K]  error
     *     2          393.4           394.0        +0.6 K
     *     5          424.9           425.4        +0.5 K
     *    10          453.0           452.7        −0.3 K
     * </pre>
     */
    @Nested
    @DisplayName("Clausius-Clapeyron saturation temperature — WATER vs NIST steam tables")
    class ClausiusClapeyronSaturationTemp {

        @Test
        @DisplayName("T_sat at 2 atm matches steam table: 393.4 K ± 2 K")
        void satTempAt2Atm() {
            assertThat(CoolantChannel.CoolantProperties.WATER.saturationTemperature(2.0 * 101_325.0))
                    .isCloseTo(393.4, within(2.0));
        }

        @Test
        @DisplayName("T_sat at 5 atm matches steam table: 424.9 K ± 2 K")
        void satTempAt5Atm() {
            assertThat(CoolantChannel.CoolantProperties.WATER.saturationTemperature(5.0 * 101_325.0))
                    .isCloseTo(424.9, within(2.0));
        }

        @Test
        @DisplayName("T_sat at 10 atm matches steam table: 453.0 K ± 2 K")
        void satTempAt10Atm() {
            assertThat(CoolantChannel.CoolantProperties.WATER.saturationTemperature(10.0 * 101_325.0))
                    .isCloseTo(453.0, within(2.0));
        }

        @Test
        @DisplayName("T_sat recovers normal boiling point at 1 atm")
        void satTempAt1Atm() {
            assertThat(CoolantChannel.CoolantProperties.WATER.saturationTemperature(101_325.0))
                    .isCloseTo(373.15, within(0.01));
        }
    }

    // =========================================================================
    //  4. Boiling margin formula — T_sat - T_cold_wall at coolant inlet station
    // =========================================================================

    /**
     * With a uniform synthetic heat flux the cold-wall temperature at the coolant
     * inlet station (nozzle exit end, first marching step, no temperature rise yet)
     * is:
     * <pre>T_cw = T_in + q/h_c + q·δ/k_wall</pre>
     * where the first term is the film resistance and the second is conduction
     * through the hot-wall liner.  The boiling margin is then:
     * <pre>margin = T_sat(P_in) − T_cw</pre>
     *
     * <p>Counter-flow note: the coolant inlet is the last element in the returned
     * profile (axial order throat → exit) because the marching direction is
     * reversed.  At that station ds = 0, so there is no upstream temperature rise
     * and P = P_in exactly.
     */
    @Nested
    @DisplayName("Boiling margin formula — T_sat - T_cold_wall at coolant inlet station")
    class BoilingMarginFormula {

        /** Uniform synthetic convective heat flux supplied to all contour stations. */
        private static final double Q_WALL        = 1e5;   // W/m²
        private static final double HOT_WALL_THICK = 0.001; // m, default value
        private static final double K_WALL         = 20.0;  // W/(m·K), default Inconel

        /** Single-point thermal profile — nearest-neighbour applies Q_WALL everywhere. */
        private List<HeatTransferModel.WallThermalPoint> thermalProfile() {
            return List.of(new HeatTransferModel.WallThermalPoint(
                    0.0, 0.05, 3000.0, Q_WALL, Q_WALL, 0.0, 1e4, 2000.0));
        }

        @Test
        @DisplayName("Cold-wall temp at coolant inlet = T_in + q/h_c + q·δ/k_wall")
        void coldWallFormulaAtInlet() {
            CoolantChannel channel = new CoolantChannel(contour)
                    .setChannelGeometry(N_CHAN, CHAN_W, CHAN_H, HOT_WALL_THICK)
                    .setCoolant(CoolantChannel.CoolantProperties.WATER, MDOT, T_IN, P_IN)
                    .calculate(thermalProfile());

            // Counter-flow: coolant inlet is the last profile element (nozzle exit end)
            CoolantChannel.ChannelPoint inlet = channel.getProfile().getLast();

            // h is uniform — read from any point
            double h_c          = channel.getProfile().getFirst().heatTransferCoeff();
            double expectedColdWall = T_IN
                    + Q_WALL / h_c
                    + Q_WALL * HOT_WALL_THICK / K_WALL;

            assertThat(inlet.coolantTemperature())
                    .as("bulk temperature at inlet must equal T_in (ds = 0, no rise)")
                    .isCloseTo(T_IN, within(0.1));

            assertThat(inlet.coldWallTemperature())
                    .as("cold-wall temperature vs formula (±1 %%)")
                    .isCloseTo(expectedColdWall, within(expectedColdWall * 0.01));
        }

        @Test
        @DisplayName("Boiling margin = T_sat(P_in) - T_cold_wall at coolant inlet station")
        void boilingMarginEqualsMarginFormula() {
            CoolantChannel channel = new CoolantChannel(contour)
                    .setChannelGeometry(N_CHAN, CHAN_W, CHAN_H, HOT_WALL_THICK)
                    .setCoolant(CoolantChannel.CoolantProperties.WATER, MDOT, T_IN, P_IN)
                    .calculate(thermalProfile());

            CoolantChannel.ChannelPoint inlet = channel.getProfile().getLast();

            double h_c            = channel.getProfile().getFirst().heatTransferCoeff();
            double coldWall       = T_IN + Q_WALL / h_c + Q_WALL * HOT_WALL_THICK / K_WALL;
            double tSat           = CoolantChannel.CoolantProperties.WATER.saturationTemperature(P_IN);
            double expectedMargin = tSat - coldWall;

            assertThat(inlet.boilingMargin())
                    .as("boiling margin vs T_sat − T_cw (±1 %%)")
                    .isCloseTo(expectedMargin, within(Math.abs(expectedMargin) * 0.01));
        }

        @Test
        @DisplayName("WATER at 5 MPa with 1e5 W/m² is fully subcooled (margin > 100 K)")
        void waterAt5MPaIsSubcooled() {
            CoolantChannel channel = new CoolantChannel(contour)
                    .setChannelGeometry(N_CHAN, CHAN_W, CHAN_H, HOT_WALL_THICK)
                    .setCoolant(CoolantChannel.CoolantProperties.WATER, MDOT, T_IN, P_IN)
                    .calculate(thermalProfile());

            assertThat(channel.getMinBoilingMargin())
                    .as("minimum boiling margin should exceed 100 K")
                    .isGreaterThan(100.0);
            assertThat(channel.isFullySubcooled()).isTrue();
        }
    }
}
