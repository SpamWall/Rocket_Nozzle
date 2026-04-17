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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Quasi-steady solid motor ballistic simulator.
 *
 * <p>At each instant the chamber pressure is computed from the quasi-steady
 * equilibrium between propellant mass generation and throat discharge:
 * <pre>
 *   ṁ_gen  = ρ_p · A_b(y) · r(P, T)
 *   ṁ_exit = P · A_t / c*
 *
 *   Setting ṁ_gen = ṁ_exit:
 *   P_c = (ρ_p · a(T) · Kn · c*)^(1/(1−n))     [quasi-steady]
 * </pre>
 * The quasi-steady approximation is valid whenever the pressure equilibration
 * time constant τ = V_c / (A_t · c*) is much shorter than the burn time, which
 * is satisfied for essentially all production solid motors.
 *
 * <p>Time marching uses forward Euler: at step {@code k},
 * <ol>
 *   <li>Compute A_b(y_k) from the grain geometry.</li>
 *   <li>Solve for P_c using the Kn equation above.</li>
 *   <li>Compute r_k = {@link SolidPropellant#burnRate(double, double)}.</li>
 *   <li>Advance: y_{k+1} = y_k + r_k · Δt.</li>
 * </ol>
 * Step accuracy is proportional to Δt; a time step of 0.1 % of burn time
 * (auto-computed in {@link #computeBurnTrajectory(double)}) gives errors below
 * 0.01 % for typical motors.
 *
 * <p>Typical usage:
 * <pre>{@code
 * SolidPropellant prop   = SolidPropellant.APCP_HTPB();
 * GrainGeometry   grain  = new BatesGrain(0.10, 0.05, 0.08, 4);
 * SolidMotorChamber chamber = new SolidMotorChamber(prop, grain, 7.854e-4); // 1 cm radius
 *
 * BurnTrajectory traj = chamber.computeBurnTrajectory(294.0);
 * System.out.println(traj);
 *
 * // Bridge to nozzle design
 * NozzleDesignParameters params = traj.toNozzleParameters(
 *         NozzleDesignParameters.builder().throatRadius(0.01).exitMach(3.0).build());
 * }</pre>
 *
 * @see SolidPropellant
 * @see GrainGeometry
 * @see BurnTrajectory
 *
 * NOTE: This class should NOT be a Record Class.  A Record Class is data: describing what something is.
 * SolidMotorChamber is a simulator: its purpose is computeBurnTrajectory(). The analogous pair in the rest of the
 * library is NozzleDesignParameters (record) vs. PerformanceCalculator (class) — the same split between configuration
 * carrier and computation engine.
 * Making it a record would be technically valid but semantically misleading: a reader seeing record SolidMotorChamber
 * expects a data type, not something that runs a forward-Euler integration.
 */
public class SolidMotorChamber {

    private static final Logger LOG = LoggerFactory.getLogger(SolidMotorChamber.class);

    /**
     * Maximum number of integration steps before the simulation aborts.
     * Prevents runaway loops caused by excessively small time steps.
     */
    private static final int MAX_STEPS = 500_000;

    /**
     * Number of time steps used when auto-computing the time step.
     * Results in step error below 0.01 % for typical motor burn profiles.
     */
    private static final int AUTO_STEPS = 1_000;

    private final SolidPropellant propellant;
    private final GrainGeometry   grain;
    private final double          throatArea;

    /**
     * Creates a motor chamber simulation.
     *
     * @param propellant  solid propellant properties; must not be {@code null}
     * @param grain       grain geometry model; must not be {@code null}
     * @param throatArea  nozzle throat area A_t [m²]; must be positive
     * @throws IllegalArgumentException if any argument is invalid
     */
    public SolidMotorChamber(SolidPropellant propellant,
                              GrainGeometry   grain,
                              double          throatArea) {
        if (propellant == null) throw new IllegalArgumentException("Propellant must not be null");
        if (grain == null)      throw new IllegalArgumentException("Grain must not be null");
        if (throatArea <= 0)    throw new IllegalArgumentException(
                "Throat area must be positive; got " + throatArea);

        this.propellant = propellant;
        this.grain      = grain;
        this.throatArea = throatArea;
    }

    // -------------------------------------------------------------------------
    // Point calculations
    // -------------------------------------------------------------------------

    /**
     * Returns the Klemmung Kn = A_b / A_t at the given regression depth.
     *
     * @param webBurned regression depth [m]
     * @return Kn (dimensionless)
     */
    public double klemmung(double webBurned) {
        return grain.burningArea(webBurned) / throatArea;
    }

    /**
     * Returns the quasi-steady equilibrium chamber pressure at the given
     * regression depth and reference propellant temperature.
     *
     * @param webBurned regression depth [m]
     * @return equilibrium chamber pressure [Pa]
     */
    public double chamberPressure(double webBurned) {
        return chamberPressure(webBurned, propellant.referenceTemperature());
    }

    /**
     * Returns the quasi-steady equilibrium chamber pressure at the given
     * regression depth and propellant temperature.
     *
     * @param webBurned              regression depth [m]
     * @param propellantTemperatureK propellant bulk temperature [K]
     * @return equilibrium chamber pressure [Pa]
     */
    public double chamberPressure(double webBurned, double propellantTemperatureK) {
        double ab = grain.burningArea(webBurned);
        return propellant.equilibriumPressure(ab, throatArea, propellantTemperatureK);
    }

    /**
     * Returns the burn rate at the given regression depth and reference temperature.
     *
     * @param webBurned regression depth [m]
     * @return burn rate [m/s]
     */
    public double burnRate(double webBurned) {
        return burnRate(webBurned, propellant.referenceTemperature());
    }

    /**
     * Returns the burn rate at the given regression depth and propellant temperature.
     *
     * @param webBurned              regression depth [m]
     * @param propellantTemperatureK propellant bulk temperature [K]
     * @return burn rate [m/s]
     */
    public double burnRate(double webBurned, double propellantTemperatureK) {
        double pc = chamberPressure(webBurned, propellantTemperatureK);
        return propellant.burnRate(pc, propellantTemperatureK);
    }

    /**
     * Returns a rough estimate of burn time based on the burn rate at the
     * initial (y = 0) equilibrium pressure.
     *
     * @param propellantTemperatureK propellant bulk temperature [K]
     * @return estimated burn time [s]
     */
    public double estimateBurnTime(double propellantTemperatureK) {
        double r0 = burnRate(0.0, propellantTemperatureK);
        return grain.webThickness() / r0;
    }

    // -------------------------------------------------------------------------
    // Trajectory simulation
    // -------------------------------------------------------------------------

    /**
     * Computes a full quasi-steady burn trajectory using an automatically
     * chosen time step ({@code webThickness / (AUTO_STEPS × r_initial)}).
     *
     * @param propellantTemperatureK propellant bulk temperature [K]
     * @return complete burn trajectory from ignition to burnout
     * @throws IllegalStateException if the simulation exceeds {@value #MAX_STEPS} steps
     */
    public BurnTrajectory computeBurnTrajectory(double propellantTemperatureK) {
        double r0 = burnRate(0.0, propellantTemperatureK);
        double dt = grain.webThickness() / (AUTO_STEPS * r0);
        return computeBurnTrajectory(propellantTemperatureK, dt);
    }

    /**
     * Computes a full quasi-steady burn trajectory using the specified time step.
     *
     * <p>Integration proceeds from {@code y = 0} until {@code y ≥ webThickness()}.
     * A burnout data point is appended at the exact {@code webThickness()} by
     * linearly interpolating the final partial step.
     *
     * @param propellantTemperatureK propellant bulk temperature [K]; typically 233–344 K
     *                               (−40 °C to +71 °C per MIL-SPEC temperature range)
     * @param timeStepSeconds        integration time step Δt [s]; must be positive.
     *                               Smaller values give higher fidelity but more data
     *                               points.  Recommend Δt ≤ 0.1 % of estimated burn time.
     * @return complete burn trajectory from ignition to burnout
     * @throws IllegalArgumentException if {@code timeStepSeconds} is non-positive
     * @throws IllegalStateException    if the simulation exceeds {@value #MAX_STEPS} steps,
     *                                  indicating an unreasonably small time step
     */
    public BurnTrajectory computeBurnTrajectory(double propellantTemperatureK,
                                                 double timeStepSeconds) {
        if (timeStepSeconds <= 0) {
            throw new IllegalArgumentException(
                    "Time step must be positive; got " + timeStepSeconds);
        }

        List<Double> tList    = new ArrayList<>();
        List<Double> yList    = new ArrayList<>();
        List<Double> pcList   = new ArrayList<>();
        List<Double> abList   = new ArrayList<>();
        List<Double> rList    = new ArrayList<>();
        List<Double> mdotList = new ArrayList<>();

        double t = 0.0;
        double y = 0.0;
        double web = grain.webThickness();
        int steps = 0;

        while (y < web) {
            if (steps >= MAX_STEPS) {
                throw new IllegalStateException(
                        "Simulation exceeded " + MAX_STEPS + " steps without burnout. "
                        + "Check that the time step is not unreasonably small and that "
                        + "the grain geometry and propellant are physically realistic.");
            }

            double ab   = grain.burningArea(y);
            double pc   = propellant.equilibriumPressure(ab, throatArea, propellantTemperatureK);
            double r    = propellant.burnRate(pc, propellantTemperatureK);
            double mdot = propellant.density() * ab * r;

            tList.add(t);
            yList.add(y);
            pcList.add(pc);
            abList.add(ab);
            rList.add(r);
            mdotList.add(mdot);

            double yNext = y + r * timeStepSeconds;

            if (yNext > web) {
                double dtFinal = (web - y) / r;
                t += dtFinal;
                y  = web;
            } else {
                t += timeStepSeconds;
                y  = yNext;
            }
            steps++;
        }

        // Burnout point
        double abEnd   = grain.burningArea(web);
        double pcEnd   = propellant.equilibriumPressure(abEnd, throatArea, propellantTemperatureK);
        double rEnd    = propellant.burnRate(pcEnd, propellantTemperatureK);
        double mdotEnd = propellant.density() * abEnd * rEnd;
        tList.add(t);
        yList.add(web);
        pcList.add(pcEnd);
        abList.add(abEnd);
        rList.add(rEnd);
        mdotList.add(mdotEnd);

        int n = tList.size();
        double[] tArr    = toArray(tList,    n);
        double[] yArr    = toArray(yList,    n);
        double[] pcArr   = toArray(pcList,   n);
        double[] abArr   = toArray(abList,   n);
        double[] rArr    = toArray(rList,    n);
        double[] mdotArr = toArray(mdotList, n);

        double propMass = propellant.density() * grain.propellantVolume();

        LOG.debug("SolidMotorChamber: grain='{}' steps={} burnTime={}s avgPc={}MPa",
                grain.name(), n - 1,
                String.format("%.3f", tArr[n - 1]),
                String.format("%.2f", pcArr[0] / 1e6));

        return new BurnTrajectory(
                tArr, yArr, pcArr, abArr, rArr, mdotArr,
                propMass,
                propellant.chamberTemperature(),
                propellant.combustionProducts());
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the propellant associated with this chamber.
     *
     * @return solid propellant
     */
    public SolidPropellant propellant() { return propellant; }

    /**
     * Returns the grain geometry associated with this chamber.
     *
     * @return grain geometry
     */
    public GrainGeometry grain() { return grain; }

    /**
     * Returns the nozzle throat area [m²].
     *
     * @return throat area [m²]
     */
    public double throatArea() { return throatArea; }

    // -------------------------------------------------------------------------

    private static double[] toArray(List<Double> list, int n) {
        double[] arr = new double[n];
        for (int i = 0; i < n; i++) arr[i] = list.get(i);
        return arr;
    }
}
