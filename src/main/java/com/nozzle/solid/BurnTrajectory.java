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

package com.nozzle.solid;

import com.nozzle.core.NozzleDesignParameters;

/**
 * Immutable time-history of a solid motor burn computed by
 * {@link SolidMotorChamber#computeBurnTrajectory}.
 *
 * <p>All arrays are indexed from ignition (index 0) to burnout
 * (index {@link #size()} − 1) and share the same length.  Physical quantities:
 * <ul>
 *   <li>{@link #timeAt(int)}          — elapsed time [s]</li>
 *   <li>{@link #webBurnedAt(int)}     — cumulative regression depth [m]</li>
 *   <li>{@link #chamberPressureAt(int)} — quasi-steady chamber pressure [Pa]</li>
 *   <li>{@link #burningAreaAt(int)}   — burning surface area A_b [m²]</li>
 *   <li>{@link #burnRateAt(int)}      — propellant regression rate [m/s]</li>
 *   <li>{@link #massFlowRateAt(int)}  — propellant mass generation rate
 *                                       ρ_p · A_b · r [kg/s]</li>
 * </ul>
 *
 * <p><b>Integration with the nozzle design pipeline:</b>
 * {@link #toNozzleParameters(NozzleDesignParameters)} and
 * {@link #toNozzleParametersAtMaxPressure(NozzleDesignParameters)} project the
 * chamber conditions from this burn trajectory onto a
 * {@link NozzleDesignParameters} template so that the contour, thermal, and
 * performance calculators can be applied directly.
 *
 * <pre>{@code
 * BurnTrajectory traj = chamber.computeBurnTrajectory(294.0);
 * NozzleDesignParameters params = traj.toNozzleParameters(
 *         NozzleDesignParameters.builder()
 *                 .throatRadius(0.025)
 *                 .exitMach(3.5)
 *                 .ambientPressure(101325)
 *                 .build());
 * NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
 * contour.generate(50);
 * }</pre>
 */
public final class BurnTrajectory {

    private final double[] time;
    private final double[] webBurned;
    private final double[] chamberPressure;
    private final double[] burningArea;
    private final double[] burnRate;
    private final double[] massFlowRate;

    private final double burnTime;
    private final double propellantMass;
    private final double averagePressure;
    private final double maxPressure;
    private final double averageMassFlowRate;

    private final double propellantChamberTemperature;
    private final com.nozzle.core.GasProperties combustionProducts;

    /**
     * Package-private constructor — instances are created only by
     * {@link SolidMotorChamber}.
     */
    BurnTrajectory(double[] time,
                   double[] webBurned,
                   double[] chamberPressure,
                   double[] burningArea,
                   double[] burnRate,
                   double[] massFlowRate,
                   double propellantMass,
                   double propellantChamberTemperature,
                   com.nozzle.core.GasProperties combustionProducts) {
        this.time            = time.clone();
        this.webBurned       = webBurned.clone();
        this.chamberPressure = chamberPressure.clone();
        this.burningArea     = burningArea.clone();
        this.burnRate        = burnRate.clone();
        this.massFlowRate    = massFlowRate.clone();

        this.burnTime    = time[time.length - 1];
        this.propellantMass = propellantMass;

        this.propellantChamberTemperature = propellantChamberTemperature;
        this.combustionProducts           = combustionProducts;

        double sumP    = 0.0;
        double sumMdot = 0.0;
        double peakP   = 0.0;
        for (int i = 0; i < time.length; i++) {
            double dt = (i == 0) ? (time.length > 1 ? time[1] - time[0] : 0.0)
                                 : time[i] - time[i - 1];
            sumP    += chamberPressure[i] * dt;
            sumMdot += massFlowRate[i]    * dt;
            if (chamberPressure[i] > peakP) {
                peakP = chamberPressure[i];
            }
        }
        this.averagePressure     = (burnTime > 0) ? sumP    / burnTime : chamberPressure[0];
        this.averageMassFlowRate = (burnTime > 0) ? sumMdot / burnTime : massFlowRate[0];
        this.maxPressure         = peakP;
    }

    // -------------------------------------------------------------------------
    // Array accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the number of data points in the trajectory.
     *
     * @return trajectory length (number of time steps + 1 for t=0)
     */
    public int size() { return time.length; }

    /**
     * Returns elapsed time at index {@code i} [s].
     *
     * @param i data index; must be in [0, {@link #size()} − 1]
     * @return time [s]
     */
    public double timeAt(int i) { return time[i]; }

    /**
     * Returns cumulative web regression depth at index {@code i} [m].
     *
     * @param i data index
     * @return web burned [m]
     */
    public double webBurnedAt(int i) { return webBurned[i]; }

    /**
     * Returns quasi-steady chamber pressure at index {@code i} [Pa].
     *
     * @param i data index
     * @return chamber pressure [Pa]
     */
    public double chamberPressureAt(int i) { return chamberPressure[i]; }

    /**
     * Returns burning surface area at index {@code i} [m²].
     *
     * @param i data index
     * @return burning area [m²]
     */
    public double burningAreaAt(int i) { return burningArea[i]; }

    /**
     * Returns propellant regression rate at index {@code i} [m/s].
     *
     * @param i data index
     * @return burn rate [m/s]
     */
    public double burnRateAt(int i) { return burnRate[i]; }

    /**
     * Returns propellant mass generation rate at index {@code i} [kg/s].
     *
     * @param i data index
     * @return mass flow rate ρ_p · A_b · r [kg/s]
     */
    public double massFlowRateAt(int i) { return massFlowRate[i]; }

    // -------------------------------------------------------------------------
    // Scalar summary quantities
    // -------------------------------------------------------------------------

    /**
     * Returns the total burn time from ignition to web burnout [s].
     *
     * @return burn time [s]
     */
    public double burnTime() { return burnTime; }

    /**
     * Returns the initial propellant mass ρ_p × V_p [kg].
     *
     * @return propellant mass [kg]
     */
    public double propellantMass() { return propellantMass; }

    /**
     * Returns the time-weighted average chamber pressure over the burn [Pa].
     *
     * @return average chamber pressure [Pa]
     */
    public double averagePressure() { return averagePressure; }

    /**
     * Returns the peak chamber pressure reached during the burn [Pa].
     *
     * @return maximum chamber pressure [Pa]
     */
    public double maxPressure() { return maxPressure; }

    /**
     * Returns the time-weighted average propellant mass flow rate [kg/s].
     *
     * @return average mass flow rate [kg/s]
     */
    public double averageMassFlowRate() { return averageMassFlowRate; }

    // -------------------------------------------------------------------------
    // Pipeline bridge
    // -------------------------------------------------------------------------

    /**
     * Creates a {@link NozzleDesignParameters} instance by overlaying this
     * trajectory's average chamber conditions onto the supplied template.
     *
     * <p>Overrides {@code chamberPressure}, {@code chamberTemperature}, and
     * {@code gasProperties}; all other parameters (throat radius, exit Mach,
     * ambient pressure, geometry ratios, etc.) are taken unchanged from
     * {@code template}.  Use the returned object with the existing
     * {@link com.nozzle.geometry.NozzleContour},
     * {@link com.nozzle.core.PerformanceCalculator}, and thermal pipeline.
     *
     * @param template baseline nozzle parameters providing throat geometry and
     *                 ambient conditions; must not be {@code null}
     * @return new {@link NozzleDesignParameters} at average chamber conditions
     */
    public NozzleDesignParameters toNozzleParameters(NozzleDesignParameters template) {
        return buildFrom(template, averagePressure);
    }

    /**
     * Creates a {@link NozzleDesignParameters} instance at the <em>peak</em>
     * chamber pressure, suitable for structural and thermal worst-case analysis.
     *
     * @param template baseline nozzle parameters; must not be {@code null}
     * @return new {@link NozzleDesignParameters} at peak chamber pressure
     */
    public NozzleDesignParameters toNozzleParametersAtMaxPressure(
            NozzleDesignParameters template) {
        return buildFrom(template, maxPressure);
    }

    private NozzleDesignParameters buildFrom(NozzleDesignParameters t, double pc) {
        return NozzleDesignParameters.builder()
                .throatRadius(t.throatRadius())
                .exitMach(t.exitMach())
                .chamberPressure(pc)
                .chamberTemperature(propellantChamberTemperature)
                .ambientPressure(t.ambientPressure())
                .gasProperties(combustionProducts)
                .numberOfCharLines(t.numberOfCharLines())
                .wallAngleInitial(t.wallAngleInitial())
                .lengthFraction(t.lengthFraction())
                .axisymmetric(t.axisymmetric())
                .throatWidth(t.throatWidth())
                .throatCurvatureRatio(t.throatCurvatureRatio())
                .upstreamCurvatureRatio(t.upstreamCurvatureRatio())
                .convergentHalfAngle(t.convergentHalfAngle())
                .contractionRatio(t.contractionRatio())
                .build();
    }

    /**
     * Returns a formatted summary of the key burn statistics.
     *
     * @return multi-line summary string
     */
    @Override
    public String toString() {
        return String.format("""
                BurnTrajectory:
                  Data points:      %d
                  Burn time:        %.3f s
                  Propellant mass:  %.3f kg
                  Average Pc:       %.3f MPa
                  Max Pc:           %.3f MPa
                  Avg mass flow:    %.4f kg/s
                """,
                size(),
                burnTime,
                propellantMass,
                averagePressure  / 1e6,
                maxPressure      / 1e6,
                averageMassFlowRate);
    }
}
