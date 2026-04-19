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
 * Immutable data parsed from a RASP {@code .eng} thrust-curve file.
 *
 * <p>A RASP file encodes a motor's measured thrust-time history along with
 * physical dimensions and mass data.  This class stores that raw data and
 * provides derived performance quantities (total impulse, specific impulse,
 * motor class, etc.) as well as a bridge to the nozzle design pipeline via
 * {@link #toNozzleParameters} and {@link #toNozzleParametersAtMaxPressure}.
 *
 * <h2>Chamber pressure inversion</h2>
 *
 * <p>A RASP file records thrust, not chamber pressure.  The bridge methods
 * recover chamber pressure from thrust by inverting the isentropic thrust
 * coefficient equation.  For a fully-expanded nozzle:
 * <pre>
 *   F = C_f · P_c · A_t
 *
 *   C_f = sqrt_term + (p_e/p_c − p_a/p_c) · (A_e/A_t)
 *
 *   where sqrt_term and p_e/p_c depend only on γ and M_e (from the template).
 * </pre>
 * Rearranging:
 * <pre>
 *   P_c = (F + p_a · A_e) / (sqrt_term · A_t + (p_e/p_c) · A_e)
 * </pre>
 * This is a closed-form inversion — no iteration required.  The throat area
 * and all gas properties are taken from the {@link NozzleDesignParameters}
 * template supplied by the caller; the {@code .eng} file contributes only
 * the thrust value.
 *
 * <p>Because {@code .eng} files do not record chamber temperature or
 * combustion product composition, those fields must be supplied in the
 * template.  {@link com.nozzle.core.GasProperties} constants (e.g.
 * {@code GasProperties.APCP_HTPB_PRODUCTS}) are suitable choices.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * RaspMotorData motor = RaspImporter.load(Path.of("F52-8.eng"));
 *
 * NozzleDesignParameters template = NozzleDesignParameters.builder()
 *         .throatRadius(0.010)
 *         .exitMach(3.0)
 *         .ambientPressure(101325)
 *         .chamberTemperature(3200)                   // from propellant data
 *         .gasProperties(GasProperties.APCP_HTPB_PRODUCTS)
 *         .axisymmetric(true)
 *         .lengthFraction(0.8)
 *         .build();
 *
 * NozzleDesignParameters params    = motor.toNozzleParameters(template);
 * NozzleDesignParameters paramsMax = motor.toNozzleParametersAtMaxPressure(template);
 *
 * NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
 * contour.generate(60);
 * }</pre>
 *
 * <p>Instances are created exclusively by {@link RaspImporter}.
 */
public final class RaspMotorData {

    private static final double G0 = 9.80665;  // standard gravity [m/s²]

    private final String   name;
    private final double   diameterMm;
    private final double   lengthMm;
    private final String   delays;
    private final double   propellantMassKg;
    private final double   totalMassKg;
    private final String   manufacturer;
    private final double[] timeSeconds;
    private final double[] thrustNewtons;

    // cached derived quantities computed once at construction
    private final double totalImpulseNs;
    private final double burnTime;
    private final double maxThrustN;

    /**
     * Package-private constructor — instances are created exclusively by
     * {@link RaspImporter}.
     */
    RaspMotorData(String name, double diameterMm, double lengthMm, String delays,
                  double propellantMassKg, double totalMassKg, String manufacturer,
                  double[] timeSeconds, double[] thrustNewtons) {
        this.name             = name;
        this.diameterMm       = diameterMm;
        this.lengthMm         = lengthMm;
        this.delays           = delays;
        this.propellantMassKg = propellantMassKg;
        this.totalMassKg      = totalMassKg;
        this.manufacturer     = manufacturer;
        this.timeSeconds      = timeSeconds.clone();
        this.thrustNewtons    = thrustNewtons.clone();

        double impulse = 0.0;
        double peak    = 0.0;
        for (int i = 1; i < timeSeconds.length; i++) {
            double dt = timeSeconds[i] - timeSeconds[i - 1];
            impulse += 0.5 * (thrustNewtons[i] + thrustNewtons[i - 1]) * dt;
            if (thrustNewtons[i] > peak) peak = thrustNewtons[i];
        }
        if (thrustNewtons[0] > peak) peak = thrustNewtons[0];
        this.totalImpulseNs = impulse;
        this.maxThrustN     = peak;

        double bt = 0.0;
        for (int i = timeSeconds.length - 1; i >= 0; i--) {
            if (thrustNewtons[i] > 0.0) { bt = timeSeconds[i]; break; }
        }
        this.burnTime = bt;
    }

    // -------------------------------------------------------------------------
    // Raw-data accessors
    // -------------------------------------------------------------------------

    /**
     * Motor designation (e.g. {@code "F52-8"}).
     * @return motor designation
     */
    public String name()             { return name; }

    /**
     * Casing outer diameter.
     * @return diameter [mm]
     */
    public double diameterMm()       { return diameterMm; }

    /**
     * Motor overall length.
     * @return length [mm]
     */
    public double lengthMm()         { return lengthMm; }

    /**
     * Ejection-charge delay string (e.g. {@code "8"}, {@code "P"}).
     * @return delay string
     */
    public String delays()           { return delays; }

    /**
     * Propellant mass.
     * @return mass [kg]
     */
    public double propellantMassKg() { return propellantMassKg; }

    /**
     * Loaded (total) motor mass.
     * @return mass [kg]
     */
    public double totalMassKg()      { return totalMassKg; }

    /**
     * Manufacturer identifier string.
     * @return manufacturer
     */
    public String manufacturer()     { return manufacturer; }

    /**
     * Number of data points in the thrust-time array.
     * @return point count
     */
    public int size()                { return timeSeconds.length; }

    /**
     * Elapsed time at the given data-point index.
     * @param i data-point index (0-based)
     * @return time [s]
     */
    public double timeAt(int i)      { return timeSeconds[i]; }

    /**
     * Thrust at the given data-point index.
     * @param i data-point index (0-based)
     * @return thrust [N]
     */
    public double thrustAt(int i)    { return thrustNewtons[i]; }

    // -------------------------------------------------------------------------
    // Derived performance quantities
    // -------------------------------------------------------------------------

    /**
     * Total impulse computed by trapezoidal integration of the thrust curve [N·s].
     *
     * @return total impulse [N·s]
     */
    public double totalImpulseNs() { return totalImpulseNs; }

    /**
     * Burn time — elapsed time of the last data point with positive thrust [s].
     *
     * @return burn time [s]
     */
    public double burnTime() { return burnTime; }

    /**
     * Peak thrust from the thrust-curve data [N].
     *
     * @return maximum thrust [N]
     */
    public double maxThrustN() { return maxThrustN; }

    /**
     * Time-average thrust over the burn ({@code totalImpulse / burnTime}) [N].
     *
     * @return average thrust [N]
     */
    public double averageThrustN() {
        return burnTime > 0.0 ? totalImpulseNs / burnTime : 0.0;
    }

    /**
     * Delivered specific impulse ({@code totalImpulse / (propellantMass × g₀)}) [s].
     *
     * @return specific impulse [s]
     */
    public double specificImpulseSeconds() {
        return propellantMassKg > 0.0
                ? totalImpulseNs / (propellantMassKg * G0)
                : 0.0;
    }

    /**
     * Average propellant mass flow rate ({@code propellantMass / burnTime}) [kg/s].
     *
     * @return average mass flow rate [kg/s]
     */
    public double averageMassFlowRateKgPerS() {
        return burnTime > 0.0 ? propellantMassKg / burnTime : 0.0;
    }

    /**
     * NAR/NFPA 1125 motor classification letter based on total impulse.
     *
     * <p>Each letter class spans a total-impulse range of 0–2.5 N·s (A),
     * 2.5–5 N·s (B), doubling each subsequent class through O+ (&gt;20 480 N·s).
     *
     * @return classification letter, e.g. {@code "F"} or {@code "O+"}
     */
    public String motorClass() {
        if (totalImpulseNs <=     2.5) return "A";
        if (totalImpulseNs <=     5.0) return "B";
        if (totalImpulseNs <=    10.0) return "C";
        if (totalImpulseNs <=    20.0) return "D";
        if (totalImpulseNs <=    40.0) return "E";
        if (totalImpulseNs <=    80.0) return "F";
        if (totalImpulseNs <=   160.0) return "G";
        if (totalImpulseNs <=   320.0) return "H";
        if (totalImpulseNs <=   640.0) return "I";
        if (totalImpulseNs <=  1280.0) return "J";
        if (totalImpulseNs <=  2560.0) return "K";
        if (totalImpulseNs <=  5120.0) return "L";
        if (totalImpulseNs <= 10240.0) return "M";
        if (totalImpulseNs <= 20480.0) return "N";
        return "O+";
    }

    // -------------------------------------------------------------------------
    // Pipeline bridge
    // -------------------------------------------------------------------------

    /**
     * Creates a {@link NozzleDesignParameters} instance by overlaying the
     * <em>average</em> chamber pressure (derived from the average thrust) onto
     * the supplied template.
     *
     * <p>Only {@code chamberPressure} is overridden; all other fields —
     * including {@code chamberTemperature} and {@code gasProperties} — are
     * taken from {@code template} unchanged.  The caller must therefore set
     * appropriate gas properties and flame temperature on the template before
     * calling this method.
     *
     * @param template  baseline nozzle parameters; must supply {@code throatRadius},
     *                  {@code exitMach}, {@code ambientPressure},
     *                  {@code chamberTemperature}, and {@code gasProperties}
     * @return new {@link NozzleDesignParameters} at average chamber pressure
     * @throws IllegalArgumentException if the template has insufficient data
     *         to invert the thrust coefficient equation
     */
    public NozzleDesignParameters toNozzleParameters(NozzleDesignParameters template) {
        double avgPc = chamberPressureFromThrust(averageThrustN(), template);
        return buildFrom(template, avgPc);
    }

    /**
     * Creates a {@link NozzleDesignParameters} instance at the <em>peak</em>
     * chamber pressure (derived from the peak thrust), suitable for structural
     * and thermal worst-case analysis.
     *
     * @param template baseline nozzle parameters; see {@link #toNozzleParameters}
     * @return new {@link NozzleDesignParameters} at peak chamber pressure
     */
    public NozzleDesignParameters toNozzleParametersAtMaxPressure(NozzleDesignParameters template) {
        double maxPc = chamberPressureFromThrust(maxThrustN(), template);
        return buildFrom(template, maxPc);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Inverts the isentropic thrust-coefficient equation to recover chamber
     * pressure from a measured thrust value.
     *
     * <p>The derivation (Sutton &amp; Biblarz, eq. 3-30 rearranged):
     * <pre>
     *   F = C_f · P_c · A_t
     *   C_f = sqrt_term + (p_e − p_a) / P_c · (A_e/A_t)
     *
     *   ⟹ F + p_a · A_e = P_c · (sqrt_term · A_t + (p_e/P_c) · A_e)
     *
     *   Because p_e/P_c is a function of γ and M_e only (isentropic):
     *   P_c = (F + p_a · A_e) / (sqrt_term · A_t + peOverPc · A_e)
     * </pre>
     */
    private static double chamberPressureFromThrust(double thrustN,
                                                    NozzleDesignParameters t) {
        double gamma    = t.gasProperties().gamma();
        double me       = t.exitMach();
        double at       = Math.PI * t.throatRadius() * t.throatRadius();
        double pa       = t.ambientPressure();

        double tt       = 1.0 + 0.5 * (gamma - 1.0) * me * me;
        double peOverPc = Math.pow(tt, -gamma / (gamma - 1.0));
        double aeOverAt = (1.0 / me)
                * Math.pow((2.0 / (gamma + 1.0)) * tt,
                           (gamma + 1.0) / (2.0 * (gamma - 1.0)));
        double ae       = aeOverAt * at;

        double sqrtTerm = Math.sqrt(
                2.0 * gamma * gamma / (gamma - 1.0)
                * Math.pow(2.0 / (gamma + 1.0), (gamma + 1.0) / (gamma - 1.0))
                * (1.0 - Math.pow(peOverPc, (gamma - 1.0) / gamma)));

        return (thrustN + pa * ae) / (sqrtTerm * at + peOverPc * ae);
    }

    private static NozzleDesignParameters buildFrom(NozzleDesignParameters t, double pc) {
        return NozzleDesignParameters.builder()
                .throatRadius(t.throatRadius())
                .exitMach(t.exitMach())
                .chamberPressure(pc)
                .chamberTemperature(t.chamberTemperature())
                .ambientPressure(t.ambientPressure())
                .gasProperties(t.gasProperties())
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

    @Override
    public String toString() {
        return String.format(
                "RaspMotorData{name='%s', mfr='%s', class=%s, " +
                "totalImpulse=%.1f N·s, burnTime=%.3f s, Isp=%.1f s}",
                name, manufacturer, motorClass(),
                totalImpulseNs, burnTime, specificImpulseSeconds());
    }
}
