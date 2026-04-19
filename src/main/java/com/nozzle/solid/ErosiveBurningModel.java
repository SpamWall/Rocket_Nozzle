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

/**
 * Computes erosive burning augmentation for solid rocket propellant grains
 * using the Lenoir-Robillard (1957) model extended for practical implementation.
 *
 * <h2>Background</h2>
 * <p>In long solid rocket motors the core flow velocity along the grain port
 * can be significant compared to the burn rate.  The resulting turbulent shear
 * at the burning surface thins the thermal boundary layer and increases the heat
 * flux into the propellant, augmenting the burn rate beyond the Vieille-law value.
 * This effect—<em>erosive burning</em>—shifts the pressure profile forward in the
 * motor and can cause significant overpressure if under-predicted.
 *
 * <h2>Lenoir-Robillard model</h2>
 * <pre>
 *   r_total = r_0 + r_e
 *
 *   r_e = α · G^0.8 · exp(−β · r_0 · ρ_p / G)   if G &gt; G_threshold
 *        = 0                                       if G ≤ G_threshold
 * </pre>
 * where:
 * <ul>
 *   <li>{@code G = ρ_g V_g} is the core gas mass flux (kg/m²/s)</li>
 *   <li>{@code r_0} is the base Vieille-law burn rate (m/s)</li>
 *   <li>{@code ρ_p} is the propellant density (kg/m³)</li>
 *   <li>{@code α = 0.0387} — empirical heat-transfer coefficient (SI)</li>
 *   <li>{@code β = 53.0}  — empirical transpiration blocking factor (SI)</li>
 *   <li>{@code G_threshold = 150 kg/(m²·s)} — minimum flux for erosive onset</li>
 * </ul>
 *
 * <p>The exponential {@code exp(−β r_0 ρ_p / G)} models <em>blocking</em> by the
 * transpired combustion gas leaving the surface; at high burn rate relative to
 * G the injected gas blows away the boundary layer and erosive burning diminishes.
 *
 * <h2>Core gas flux</h2>
 * <p>For a BATES grain the local mass flux in the cylindrical port at axial
 * position {@code z} from the head end is:
 * <pre>
 *   G(z) = ρ_p · r_0 · A_b(z) / A_port(z)
 * </pre>
 * where {@code A_b(z)} is the cumulative burning surface up to station {@code z}
 * and {@code A_port(z) = π r_port²} is the local port cross-section.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * SolidPropellant prop  = SolidPropellant.APCP_HTPB();
 * ErosiveBurningModel m = new ErosiveBurningModel(prop);
 *
 * double r_total = m.augmentedBurnRate(r0, G);
 * double erosiveFraction = m.erosiveFraction(r0, G);
 * }</pre>
 */
public class ErosiveBurningModel {


    /** Default Lenoir-Robillard α coefficient (SI units). */
    public static final double DEFAULT_ALPHA     = 0.0387;
    /** Default Lenoir-Robillard β coefficient (SI units). */
    public static final double DEFAULT_BETA      = 53.0;
    /** Default threshold mass flux below which erosive burning is negligible. */
    public static final double DEFAULT_THRESHOLD = 150.0;   // kg/(m²·s)

    private final SolidPropellant propellant;
    private final double alpha;
    private final double beta;
    private final double gThreshold;

    /**
     * Creates an erosive burning model with Lenoir-Robillard default coefficients.
     *
     * @param propellant the base propellant whose density and burn-rate law are used
     */
    public ErosiveBurningModel(SolidPropellant propellant) {
        this(propellant, DEFAULT_ALPHA, DEFAULT_BETA, DEFAULT_THRESHOLD);
    }

    /**
     * Creates an erosive burning model with custom Lenoir-Robillard coefficients.
     *
     * @param propellant  the propellant; provides density and Vieille-law parameters
     * @param alpha       heat-transfer coefficient α (SI); calibrate per propellant lot
     * @param beta        blocking factor β (SI)
     * @param gThreshold  minimum core mass flux for erosive onset (kg/m²/s)
     */
    public ErosiveBurningModel(SolidPropellant propellant,
                               double alpha,
                               double beta,
                               double gThreshold) {
        if (propellant == null) throw new IllegalArgumentException("Propellant must not be null");
        if (alpha <= 0)         throw new IllegalArgumentException("alpha must be positive");
        if (beta  <= 0)         throw new IllegalArgumentException("beta must be positive");
        if (gThreshold < 0)     throw new IllegalArgumentException("gThreshold must be non-negative");
        this.propellant = propellant;
        this.alpha      = alpha;
        this.beta       = beta;
        this.gThreshold = gThreshold;
    }

    // -------------------------------------------------------------------------
    // Core computations
    // -------------------------------------------------------------------------

    /**
     * Computes the total (Vieille + erosive) burn rate at the given base burn
     * rate and local core mass flux.
     *
     * @param r0 base Vieille-law burn rate at current P, T (m/s); must be positive
     * @param G  local core gas mass flux (kg/m²/s); zero or negative → no erosion
     * @return total burn rate r_total = r_0 + r_e (m/s), always ≥ r_0
     */
    public double augmentedBurnRate(double r0, double G) {
        return r0 + erosiveBurnRate(r0, G);
    }

    /**
     * Returns the erosive augmentation {@code r_e} alone (the increase above r_0).
     *
     * @param r0 base burn rate (m/s)
     * @param G  core mass flux (kg/m²/s)
     * @return r_e ≥ 0 (m/s)
     */
    public double erosiveBurnRate(double r0, double G) {
        if (G <= gThreshold || r0 <= 0) return 0.0;
        double blocking = beta * r0 * propellant.density() / G;
        double re = alpha * Math.pow(G, 0.8) * Math.exp(-blocking);
        return Math.max(re, 0.0);
    }

    /**
     * Returns the fractional erosive augmentation {@code r_e / r_0}.
     *
     * @param r0 base burn rate (m/s)
     * @param G  core mass flux (kg/m²/s)
     * @return dimensionless erosive fraction; 0 means no erosive burning
     */
    public double erosiveFraction(double r0, double G) {
        if (r0 <= 0) return 0.0;
        return erosiveBurnRate(r0, G) / r0;
    }

    /**
     * Computes the local core mass flux for a cylindrical port at axial position
     * {@code z} from the head end of the grain.
     *
     * <p>The cumulative burning surface up to station {@code z} in a cylindrical
     * BATES-type bore (inner radius {@code ri}, outer radius {@code ro}) is:
     * <pre>
     *   A_b_cum(z) = 2π · ri · z  (lateral surface only, neglecting ends for long grains)
     *   G(z) = ρ_p · r_0 · A_b_cum(z) / (π · ri²)
     * </pre>
     * This simplifies to {@code G(z) = 2 ρ_p r_0 z / ri}.
     *
     * @param r0             base burn rate (m/s) at current pressure
     * @param innerRadius    current port inner radius (m)
     * @param axialPosition  axial distance from head end (m)
     * @return core mass flux G (kg/m²/s)
     */
    public double coreFlux(double r0, double innerRadius, double axialPosition) {
        if (innerRadius <= 0 || axialPosition <= 0) return 0.0;
        return 2.0 * propellant.density() * r0 * axialPosition / innerRadius;
    }

    /**
     * Computes the axially-averaged erosive augmentation over a grain of length
     * {@code L} and current inner radius {@code ri}.
     *
     * <p>Integrates {@code r_e(z)} from {@code z = 0} to {@code z = L} using
     * Simpson's rule with 50 intervals and returns the mean value.
     *
     * @param r0          base burn rate (m/s)
     * @param innerRadius current inner bore radius (m)
     * @param grainLength grain length (m)
     * @return axially averaged erosive burn rate (m/s)
     */
    public double axiallyAveragedErosion(double r0, double innerRadius, double grainLength) {
        int N    = 50;
        double h = grainLength / N;
        double sum = 0.0;
        for (int i = 0; i <= N; i++) {
            double z = i * h;
            double G = coreFlux(r0, innerRadius, z);
            double re = erosiveBurnRate(r0, G);
            double weight = (i == 0 || i == N) ? 1.0 : (i % 2 == 0 ? 2.0 : 4.0);
            sum += weight * re;
        }
        return sum * h / 3.0 / grainLength;
    }

    /**
     * Returns the minimum core mass flux required to onset erosive burning.
     *
     * @return G_threshold in kg/(m²·s)
     */
    public double getThresholdFlux() { return gThreshold; }

    /**
     * Returns the Lenoir-Robillard α coefficient.
     *
     * @return α (SI)
     */
    public double getAlpha() { return alpha; }

    /**
     * Returns the Lenoir-Robillard β (blocking) coefficient.
     *
     * @return β (SI)
     */
    public double getBeta() { return beta; }

    /**
     * Returns the propellant used by this model.
     *
     * @return the {@link SolidPropellant}
     */
    public SolidPropellant getPropellant() { return propellant; }
}
