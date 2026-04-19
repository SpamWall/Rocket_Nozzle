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

import com.nozzle.geometry.NozzleContour;
import com.nozzle.core.Point2D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sizes regenerative coolant channels along the nozzle wall.
 * Computes local heat transfer coefficient, pressure drop, coolant temperature
 * rise, and nucleate boiling margin at each axial station.
 *
 * <p>Hydraulics use the Gnielinski (1976) correlation for turbulent pipe flow
 * and the Darcy-Weisbach equation for pressure drop, with the Petukhov (1970)
 * smooth-pipe friction factor. Saturation temperature is estimated via the
 * Clausius-Clapeyron equation from a standard atmospheric reference point.
 *
 * <p>Typical usage:
 * <pre>
 *   CoolantChannel channel = new CoolantChannel(contour)
 *       .setChannelGeometry(120, 0.003, 0.005, 0.001)
 *       .setCoolant(CoolantProperties.RP1, 2.0, 300.0, 8e6)
 *       .calculate(heatModel.getWallThermalProfile());
 *
 *   heatModel.setCoolantChannel(channel).calculate(flowPoints);
 * </pre>
 */
public class CoolantChannel {

    // Channel geometry
    private int    numberOfChannels  = 100;
    private double channelWidth      = 0.003;   // m
    private double channelHeight     = 0.005;   // m
    private double hotWallThickness  = 0.001;   // m â€” inner liner only
    private double wallConductivity  = 20.0;    // W/(mÂ·K) â€” Inconel default

    // Coolant inlet conditions
    private CoolantProperties coolantType    = CoolantProperties.RP1;
    private double            massFlowRate   = 1.0;    // kg/s (total, all channels)
    private double            inletTemperature = 300.0; // K
    private double            inletPressure    = 8e6;   // Pa
    private boolean           counterflow      = true;  // exit â†’ throat

    private final NozzleContour contour;
    private final List<ChannelPoint>     channelProfile = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Coolant property definitions
    // -------------------------------------------------------------------------

    /**
     * Representative thermodynamic properties for common rocket propellant coolants.
     * Properties are averages for sub-critical, single-phase conditions.
     */
    public enum CoolantProperties {
        /** RP-1 (rocket-grade kerosene) */
        RP1  ( 820,  2010, 2.00e-3, 0.135,  450.0,   250_000, 170.0),
        /** Liquid hydrogen */
        LH2  (  71,  9700, 1.30e-5, 0.099,   20.28,  446_000,   2.016),
        /** Liquid oxygen */
        LOX  (1140,  1700, 2.00e-4, 0.170,   90.2,   213_000,  32.0),
        /** Water (testing / steam-cooled concepts) */
        WATER( 998,  4182, 1.00e-3, 0.598,  373.15, 2_257_000,  18.015);

        /** Density in kg/mÂ³ */
        public final double density;
        /** Specific heat at constant pressure in J/(kgÂ·K) */
        public final double specificHeat;
        /** Dynamic viscosity in PaÂ·s */
        public final double viscosity;
        /** Thermal conductivity in W/(mÂ·K) */
        public final double conductivity;
        /** Normal boiling point (saturation temperature at 101 325 Pa) in K */
        public final double tSatAtm;
        /** Enthalpy of vaporization in J/kg */
        public final double hVaporization;
        /** Molecular weight in g/mol */
        public final double molecularWeight;

        CoolantProperties(double density, double specificHeat, double viscosity,
                          double conductivity, double tSatAtm,
                          double hVaporization, double molecularWeight) {
            this.density        = density;
            this.specificHeat   = specificHeat;
            this.viscosity      = viscosity;
            this.conductivity   = conductivity;
            this.tSatAtm        = tSatAtm;
            this.hVaporization  = hVaporization;
            this.molecularWeight = molecularWeight;
        }

        /**
         * Computes the Prandtl number: {@code Pr = Î¼ Â· Cp / k}.
         *
         * @return Prandtl number (dimensionless)
         */
        public double prandtlNumber() {
            return viscosity * specificHeat / conductivity;
        }

        /**
         * Saturation temperature at the given pressure using the
         * Clausius-Clapeyron equation referenced to the normal boiling point.
         *
         * @param pressure Absolute pressure in Pa
         * @return Saturation temperature in K
         */
        public double saturationTemperature(double pressure) {
            double R       = 8314.46 / molecularWeight;   // J/(kgÂ·K)
            double lnRatio = Math.log(pressure / 101_325.0);
            return tSatAtm / (1.0 - (R * tSatAtm / hVaporization) * lnRatio);
        }
    }

    // -------------------------------------------------------------------------
    // Result record
    // -------------------------------------------------------------------------

    /**
     * Thermal and hydraulic state at one axial station of the coolant channel.
     *
     * @param x                      Axial position (m)
     * @param y                      Radial position / wall radius (m)
     * @param coolantTemperature     Bulk coolant temperature (K)
     * @param coolantPressure        Local coolant pressure (Pa)
     * @param velocity               Bulk flow velocity (m/s)
     * @param reynoldsNumber         Channel Reynolds number
     * @param nusseltNumber          Nusselt number from correlation
     * @param heatTransferCoeff      Coolant-side heat transfer coefficient W/(mÂ²Â·K)
     * @param pressureDropCumulative Cumulative pressure loss from inlet (Pa)
     * @param coldWallTemperature    Coolant-side wall surface temperature (K)
     * @param saturationTemperature  Saturation temperature at local pressure (K)
     * @param boilingMargin          T_sat âˆ’ T_wall_cold (K); positive = subcooled
     */
    public record ChannelPoint(
            double x,
            double y,
            double coolantTemperature,
            double coolantPressure,
            double velocity,
            double reynoldsNumber,
            double nusseltNumber,
            double heatTransferCoeff,
            double pressureDropCumulative,
            double coldWallTemperature,
            double saturationTemperature,
            double boilingMargin
    ) {
        /**
         * Returns {@code true} if nucleate boiling is predicted at this station
         * (i.e., the local wall temperature exceeds the coolant saturation temperature).
         *
         * @return {@code true} when {@code boilingMargin} &lt; 0 (wall hotter than T_sat)
         */
        public boolean isNucleateBoiling() { return boilingMargin < 0.0; }
    }

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Creates a coolant channel model for the given nozzle contour.
     *
     * @param contour Nozzle wall contour (must be generated before calling calculate)
     */
    public CoolantChannel(NozzleContour contour) {
        this.contour = contour;
    }

    // -------------------------------------------------------------------------
    // Fluent configuration
    // -------------------------------------------------------------------------

    /**
     * Sets rectangular channel geometry.
     *
     * @param nChannels    Number of channels around the circumference
     * @param width        Channel width in m
     * @param height       Channel depth (height) in m
     * @param hotWallThick Inner liner (hot wall) thickness in m
     * @return This instance
     */
    public CoolantChannel setChannelGeometry(int nChannels, double width,
                                              double height, double hotWallThick) {
        this.numberOfChannels = nChannels;
        this.channelWidth     = width;
        this.channelHeight    = height;
        this.hotWallThickness = hotWallThick;
        return this;
    }

    /**
     * Sets the wall material thermal conductivity used for cold-wall temperature estimation.
     *
     * @param conductivity W/(mÂ·K)
     * @return This instance
     */
    public CoolantChannel setWallConductivity(double conductivity) {
        this.wallConductivity = conductivity;
        return this;
    }

    /**
     * Sets the coolant type and inlet conditions.
     *
     * @param type       Coolant propellant
     * @param massFlow   Total coolant mass flow rate across all channels (kg/s)
     * @param inletTemp  Coolant inlet temperature (K)
     * @param inletPress Coolant inlet pressure (Pa)
     * @return This instance
     */
    public CoolantChannel setCoolant(CoolantProperties type, double massFlow,
                                      double inletTemp, double inletPress) {
        this.coolantType       = type;
        this.massFlowRate      = massFlow;
        this.inletTemperature  = inletTemp;
        this.inletPressure     = inletPress;
        return this;
    }

    /**
     * Sets the coolant flow direction.
     *
     * @param counterflow {@code true} = exit â†’ throat (standard counter-current regenerative cooling)
     * @return This instance
     */
    public CoolantChannel setCounterflow(boolean counterflow) {
        this.counterflow = counterflow;
        return this;
    }

    // -------------------------------------------------------------------------
    // Calculation
    // -------------------------------------------------------------------------

    /**
     * Computes hydraulic quantities (h_coolant, velocity, Re, Nu, pressure drop)
     * without a thermal profile. Cold-wall temperature and boiling margin are
     * not computed.
     *
     * @return This instance
     */
    public CoolantChannel calculate() {
        return calculate(List.of());
    }

    /**
     * Computes hydraulic quantities and, when {@code thermalProfile} is non-empty,
     * integrates coolant temperature rise and nucleate boiling margin.
     *
     * <p>The channel is marched from the coolant inlet to outlet. For counter-flow
     * (the default), the inlet is at the nozzle exit. The returned profile is
     * always in axial order (throat â†’ exit) for consistency with other models.
     *
     * @param thermalProfile Wall thermal points from
     *                       {@link HeatTransferModel#getWallThermalProfile()};
     *                       pass an empty list for hydraulics-only mode
     * @return This instance
     */
    public CoolantChannel calculate(List<HeatTransferModel.WallThermalPoint> thermalProfile) {
        channelProfile.clear();

        List<Point2D> contourPoints = contour.getContourPoints();
        if (contourPoints.isEmpty()) {
            contour.generate(100);
            contourPoints = contour.getContourPoints();
        }

        // Hydraulic geometry â€” constant for rectangular channels
        double D_h    = 2.0 * channelWidth * channelHeight / (channelWidth + channelHeight);
        double A_flow = numberOfChannels * channelWidth * channelHeight;

        double velocity = massFlowRate / (coolantType.density * A_flow);
        double Re       = coolantType.density * velocity * D_h / coolantType.viscosity;
        double Pr       = coolantType.prandtlNumber();
        double Nu       = nusseltNumber(Re, Pr);
        double h_c      = Nu * coolantType.conductivity / D_h;
        double f_darcy  = darcyFrictionFactor(Re);

        // March from coolant inlet; counter-flow starts at the exit end
        List<Point2D> marching = new ArrayList<>(contourPoints);
        if (counterflow) Collections.reverse(marching);

        double T_c           = inletTemperature;
        double P_c           = inletPressure;
        double dP_cumulative = 0.0;

        for (int i = 0; i < marching.size(); i++) {
            Point2D pt = marching.get(i);

            // Arc-length element
            double ds = (i == 0) ? 0.0 : marching.get(i - 1).distanceTo(pt);

            // Pressure drop over this element (Darcy-Weisbach)
            double dP = f_darcy * (ds / Math.max(D_h, 1e-9))
                        * (coolantType.density * velocity * velocity / 2.0);
            dP_cumulative += dP;
            P_c           -= dP;

            // Coolant temperature rise from total heat input at this surface element
            if (!thermalProfile.isEmpty() && ds > 0) {
                double q  = interpolateConvectiveFlux(pt.x(), thermalProfile);
                double dQ = q * 2.0 * Math.PI * pt.y() * ds;   // axisymmetric surface
                T_c += dQ / (massFlowRate * coolantType.specificHeat);
            }

            // Boiling margin
            double coldWall = 0.0;
            double T_sat    = 0.0;
            double margin   = 0.0;
            if (!thermalProfile.isEmpty()) {
                double q = interpolateConvectiveFlux(pt.x(), thermalProfile);
                // Cold-side wall temperature: bulk coolant + convective film + conduction through liner
                coldWall = T_c + q / h_c + q * hotWallThickness / wallConductivity;
                T_sat    = coolantType.saturationTemperature(Math.max(P_c, 1000.0));
                margin   = T_sat - coldWall;
            }

            channelProfile.add(new ChannelPoint(
                    pt.x(), pt.y(),
                    T_c, P_c,
                    velocity, Re, Nu, h_c,
                    dP_cumulative,
                    coldWall, T_sat, margin
            ));
        }

        // Restore axial order (throat â†’ exit) regardless of flow direction
        if (counterflow) Collections.reverse(channelProfile);

        return this;
    }

    // -------------------------------------------------------------------------
    // Hydraulic correlations
    // -------------------------------------------------------------------------

    /**
     * Nusselt number using Gnielinski (1976) for turbulent flow (Re â‰¥ 4 000),
     * the constant-wall-temperature laminar value (3.66) for Re â‰¤ 2 300,
     * and linear interpolation through the transition regime.
     */
    private double nusseltNumber(double Re, double Pr) {
        if (Re <= 2300) return 3.66;
        double Nu_turbulent = gnielinskiNusselt(Re, Pr);
        if (Re < 4000) {
            double t = (Re - 2300.0) / (4000.0 - 2300.0);
            return 3.66 + t * (Nu_turbulent - 3.66);
        }
        return Nu_turbulent;
    }

    /**
     * Gnielinski (1976) correlation.
     * Valid for 3 000 &lt; Re &lt; 5Ã—10â¶ and 0.5 &lt; Pr &lt; 2 000.
     */
    private double gnielinskiNusselt(double Re, double Pr) {
        double f = darcyFrictionFactor(Re);
        return (f / 8.0) * (Re - 1000.0) * Pr
               / (1.0 + 12.7 * Math.sqrt(f / 8.0) * (Math.pow(Pr, 2.0 / 3.0) - 1.0));
    }

    /**
     * Petukhov (1970) smooth-pipe Darcy friction factor.
     * Falls back to the laminar value (64/Re) for Re â‰¤ 2 300.
     */
    private double darcyFrictionFactor(double Re) {
        if (Re <= 2300) return 64.0 / Re;
        double lnRe = Math.log(Re);
        return Math.pow(0.790 * lnRe - 1.64, -2.0);
    }

    // -------------------------------------------------------------------------
    // Interpolation
    // -------------------------------------------------------------------------

    /** Nearest-neighbor interpolation of convective heat flux at axial position x. */
    private double interpolateConvectiveFlux(double x,
                                              List<HeatTransferModel.WallThermalPoint> profile) {
        HeatTransferModel.WallThermalPoint nearest = profile.getFirst();
        double minDx = Math.abs(nearest.x() - x);
        for (HeatTransferModel.WallThermalPoint pt : profile) {
            double dx = Math.abs(pt.x() - x);
            if (dx < minDx) { minDx = dx; nearest = pt; }
        }
        return nearest.convectiveHeatFlux();
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the channel profile in axial order (throat â†’ exit).
     *
     * @return Unmodifiable list of channel points
     */
    public List<ChannelPoint> getProfile() {
        return Collections.unmodifiableList(channelProfile);
    }

    /**
     * Returns the coolant-side heat transfer coefficient at axial position x
     * by nearest-neighbor lookup in the computed profile.
     * Falls back to 5 000 W/(mÂ²Â·K) if the channel has not been calculated.
     *
     * @param x Axial position (m)
     * @return h_coolant in W/(mÂ²Â·K)
     */
    public double getHeatTransferCoeffAt(double x) {
        if (channelProfile.isEmpty()) return 5000.0;
        ChannelPoint nearest = channelProfile.getFirst();
        double minDx = Math.abs(nearest.x() - x);
        for (ChannelPoint pt : channelProfile) {
            double dx = Math.abs(pt.x() - x);
            if (dx < minDx) { minDx = dx; nearest = pt; }
        }
        return nearest.heatTransferCoeff();
    }

    /**
     * Returns the total coolant temperature rise from inlet to outlet.
     *
     * @return Temperature rise in K
     */
    public double getCoolantTemperatureRise() {
        if (channelProfile.size() < 2) return 0.0;
        return Math.abs(channelProfile.getLast().coolantTemperature()
                      - channelProfile.getFirst().coolantTemperature());
    }

    /**
     * Returns the total pressure drop from coolant inlet to outlet.
     *
     * @return Pressure drop in Pa
     */
    public double getTotalPressureDrop() {
        return channelProfile.stream()
                .mapToDouble(ChannelPoint::pressureDropCumulative)
                .max()
                .orElse(0.0);
    }

    /**
     * Returns the minimum boiling margin across the entire profile.
     * A negative value indicates nucleate boiling is predicted at that station.
     *
     * @return Minimum margin in K; {@code Double.MAX_VALUE} if no thermal profile was supplied
     */
    public double getMinBoilingMargin() {
        return channelProfile.stream()
                .mapToDouble(ChannelPoint::boilingMargin)
                .filter(m -> m != 0.0)
                .min()
                .orElse(Double.MAX_VALUE);
    }

    /**
     * Returns {@code true} if no nucleate boiling is predicted anywhere in the channel.
     *
     * @return {@code true} = fully subcooled
     */
    public boolean isFullySubcooled() {
        return channelProfile.stream().noneMatch(ChannelPoint::isNucleateBoiling);
    }

    /**
     * Returns the channel hydraulic diameter: D_h = 2wh / (w + h).
     *
     * @return D_h in m
     */
    public double hydraulicDiameter() {
        return 2.0 * channelWidth * channelHeight / (channelWidth + channelHeight);
    }

    /**
     * Returns the total coolant flow area across all channels.
     *
     * @return A_flow in mÂ²
     */
    public double totalFlowArea() {
        return numberOfChannels * channelWidth * channelHeight;
    }
}
