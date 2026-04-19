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
import com.nozzle.moc.CharacteristicPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Models two-phase flow performance losses caused by condensed-phase particles
 * (typically Al₂O₃ from aluminum-loaded solid propellants) traveling through
 * the nozzle at lower velocity and temperature than the surrounding gas.
 *
 * <h2>Physics</h2>
 * <p>Particles suspended in the expanding gas do not instantly reach gas velocity
 * and temperature due to their inertia and finite heat-transfer rate.  This
 * "velocity lag" and "temperature lag" reduce the effective exhaust momentum
 * and hence specific impulse.
 *
 * <h3>Particle equations of motion</h3>
 * <p>1-D Lagrangian tracking in the streamwise direction:
 * <pre>
 *   m_p · dV_p/dt = F_drag = 3π μ d_p (V_g − V_p) · C_D · Re_p / 24
 *
 *   m_p · c_p · dT_p/dt = π d_p² · h_conv · (T_g − T_p)
 *
 *   h_conv = (k_g / d_p) · Nu   where Nu = 2 + 0.6 Re_p^0.5 Pr^0.33 (Ranz-Marshall)
 * </pre>
 *
 * <p>The particle Reynolds number {@code Re_p = ρ_g |V_g − V_p| d_p / μ_g} is
 * used in both the drag correction {@code C_D = 24/Re_p (1 + Re_p^(2/3)/6)} and
 * the Nusselt number.  This gives the Schiller-Naumann drag law valid for
 * {@code Re_p ≤ 800}.
 *
 * <h3>Performance loss</h3>
 * <p>The two-phase Isp loss factor (Kliegel &amp; Nickerson, 1963):
 * <pre>
 *   η_2φ = (ṁ_g V_ge + ṁ_p V_pe) / ((ṁ_g + ṁ_p) · V_ge_equiv)
 * </pre>
 * where {@code V_ge_equiv} is the velocity all propellant mass would achieve
 * in an ideal single-phase expansion.  A simplified form widely used in
 * preliminary design:
 * <pre>
 *   Δ Isp / Isp_ideal ≈ α (1 − V_pe/V_ge) + β (1 − T_pe/T_ge)^0.5
 * </pre>
 * where {@code α} is the particle mass fraction and {@code β ≈ 0.1 α} is an
 * empirical temperature-lag coefficient.
 *
 * <h2>Typical inputs for APCP/HTPB with 18 % Al</h2>
 * <ul>
 *   <li>Particle mass fraction α ≈ 0.30 (Al₂O₃ yield from 18 % Al)</li>
 *   <li>Particle diameter d_p ≈ 2–10 μm (30 % Sauter mean diameter)</li>
 *   <li>Particle density ρ_p ≈ 3960 kg/m³ (Al₂O₃)</li>
 *   <li>Particle specific heat c_p ≈ 1050 J/(kg·K)</li>
 * </ul>
 */
public class TwoPhaseFlowModel {

    private static final Logger LOG = LoggerFactory.getLogger(TwoPhaseFlowModel.class);

    /**
     * State of a condensed-phase particle at one axial station.
     *
     * @param x           axial position (m)
     * @param velocity    particle velocity (m/s)
     * @param temperature particle temperature (K)
     * @param velocityLag (V_gas − V_particle) / V_gas — dimensionless lag (0 = none)
     * @param thermalLag  (T_gas − T_particle) / T_gas — dimensionless lag
     */
    public record ParticleState(
            double x,
            double velocity,
            double temperature,
            double velocityLag,
            double thermalLag
    ) {}

    // -------------------------------------------------------------------------
    // Al₂O₃ default properties
    // -------------------------------------------------------------------------
    /** Density of condensed Al₂O₃ in kg/m³. */
    public static final double AL2O3_DENSITY       = 3960.0;
    /** Specific heat of condensed Al₂O₃ in J/(kg·K). */
    public static final double AL2O3_SPECIFIC_HEAT = 1050.0;
    /** Sauter mean diameter for typical Al₂O₃ from 18 % Al in APCP (m). */
    public static final double AL2O3_DIAMETER_M    = 4e-6;

   private final GasProperties          gas;

    /** Condensed-particle mass fraction (mass of particles / total propellant mass). */
    private final double particleMassFraction;
    /** Sauter mean particle diameter in metres. */
    private final double particleDiameter;
    /** Particle material density in kg/m³. */
    private final double particleDensity;
    /** Particle specific heat in J/(kg·K). */
    private final double particleSpecificHeat;

    private final List<ParticleState> particleTrajectory = new ArrayList<>();
    private double twoPhaseEfficiency;
    private double ispLossFraction;

    /**
     * Creates a two-phase flow model with Al₂O₃ particle defaults suitable for
     * APCP/HTPB propellant with 18 % aluminum loading.
     *
     * @param params              nozzle design parameters
     * @param particleMassFraction condensed-phase mass fraction (0 ≤ α ≤ 1)
     */
    public TwoPhaseFlowModel(NozzleDesignParameters params, double particleMassFraction) {
        this(params, particleMassFraction, AL2O3_DIAMETER_M, AL2O3_DENSITY, AL2O3_SPECIFIC_HEAT);
    }

    /**
     * Creates a two-phase flow model with fully specified particle properties.
     *
     * @param params               nozzle design parameters
     * @param particleMassFraction condensed-phase mass fraction (0 ≤ α ≤ 1)
     * @param particleDiameter     Sauter mean particle diameter (m)
     * @param particleDensity      particle material density (kg/m³)
     * @param particleSpecificHeat particle specific heat (J/(kg·K))
     */
    public TwoPhaseFlowModel(NozzleDesignParameters params,
                             double particleMassFraction,
                             double particleDiameter,
                             double particleDensity,
                             double particleSpecificHeat) {
        if (particleMassFraction < 0 || particleMassFraction >= 1) {
            throw new IllegalArgumentException(
                    "Particle mass fraction must be in [0, 1); got " + particleMassFraction);
        }
        this.gas                  = params.gasProperties();
        this.particleMassFraction = particleMassFraction;
        this.particleDiameter     = particleDiameter;
        this.particleDensity      = particleDensity;
        this.particleSpecificHeat = particleSpecificHeat;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Performs Lagrangian particle tracking through the nozzle using wall-point
     * gas properties from the provided characteristic network, then computes the
     * two-phase Isp efficiency.
     *
     * @param wallPoints inviscid MOC wall points (in axial order) providing
     *                   gas velocity, temperature, and density at each station
     * @return this instance for method chaining
     */
    public TwoPhaseFlowModel solve(List<CharacteristicPoint> wallPoints) {
        if (wallPoints.isEmpty()) {
            throw new IllegalArgumentException("Wall points must not be empty");
        }

        double mParticle = (Math.PI / 6.0) * particleDensity
                           * Math.pow(particleDiameter, 3.0);

        // Initialize particles at the throat with gas conditions
        CharacteristicPoint throat = wallPoints.getFirst();
        double Vp = throat.velocity() * 0.95;    // slight initial velocity lag at throat
        double Tp = throat.temperature() * 1.05; // slight initial temperature lag

        particleTrajectory.add(new ParticleState(throat.x(), Vp, Tp, 0.05, -0.05));

        for (int i = 1; i < wallPoints.size(); i++) {
            CharacteristicPoint prev = wallPoints.get(i - 1);
            CharacteristicPoint curr = wallPoints.get(i);

            double dx = curr.x() - prev.x();
            if (dx <= 0) continue;

            // Gas properties at mid-station
            double Vg  = (prev.velocity()    + curr.velocity())    / 2.0;
            double Tg  = (prev.temperature() + curr.temperature()) / 2.0;
            double rho = (prev.density()     + curr.density())     / 2.0;
            double mu  = gas.calculateViscosity(Tg);
            double kThermal = mu * gas.specificHeatCp()
                              * (9.0 * gas.gamma() - 5.0) / (4.0 * gas.gamma());

            // Particle Reynolds number (Schiller-Naumann)
            double relV  = Math.abs(Vg - Vp);
            double Rep   = rho * relV * particleDiameter / Math.max(mu, 1e-20);

            // Drag coefficient (Schiller-Naumann, valid Re_p ≤ 800)
            double Cd = (Rep < 1e-4) ? 0.0
                    : 24.0 / Rep * (1.0 + Math.pow(Rep, 2.0 / 3.0) / 6.0);

            // Drag force per particle
            double Fdrag = 0.5 * Cd * rho * relV * relV
                           * (Math.PI / 4.0) * particleDiameter * particleDiameter
                           * Math.signum(Vg - Vp);

            // Nusselt number (Ranz-Marshall)
            double Pr  = mu * gas.specificHeatCp() / Math.max(kThermal, 1e-20);
            double Nu  = 2.0 + 0.6 * Math.pow(Math.max(Rep, 0), 0.5)
                              * Math.pow(Math.max(Pr, 0), 1.0 / 3.0);
            double hConv = Nu * kThermal / particleDiameter;

            // Time step: dt = dx / Vp (Lagrangian particle advection)
            double dt = (Vp > 1e-3) ? dx / Vp : dx / (Vg * 0.1);

            // Forward-Euler update of particle velocity and temperature
            double aParticle = Fdrag / mParticle;
            double dTp_dt    = Math.PI * particleDiameter * particleDiameter
                               * hConv * (Tg - Tp)
                               / (mParticle * particleSpecificHeat);

            Vp = Vp + aParticle * dt;
            Tp = Tp + dTp_dt   * dt;

            // Clamp: particle cannot exceed gas velocity or drop below wall temperature
            Vp = Math.min(Vp, Vg);
            Tp = Math.max(Tp, 300.0);

            double vLag = (Vg > 1e-3) ? (Vg - Vp) / Vg : 0.0;
            double tLag = (Tg > 1.0)  ? (Tg - Tp) / Tg : 0.0;

            particleTrajectory.add(new ParticleState(curr.x(), Vp, Tp, vLag, tLag));
        }

        computeEfficiency(wallPoints.getLast());

        LOG.debug("2-phase: alpha={}, V_pe/V_ge={}, T_pe/T_ge={}, efficiency={}",
                particleMassFraction,
                particleTrajectory.getLast().velocity() / wallPoints.getLast().velocity(),
                particleTrajectory.getLast().temperature() / wallPoints.getLast().temperature(),
                twoPhaseEfficiency);
        return this;
    }

    /**
     * Returns the particle trajectory through the nozzle.
     *
     * @return unmodifiable list of {@link ParticleState}s from throat to exit
     */
    public List<ParticleState> getParticleTrajectory() {
        return Collections.unmodifiableList(particleTrajectory);
    }

    /**
     * Returns the two-phase Isp efficiency factor
     * {@code η_2φ = Isp_actual / Isp_ideal}.
     *
     * @return η_2φ ∈ (0, 1]; values near 0.95 are typical for 18 % Al APCP
     */
    public double twoPhaseEfficiency() { return twoPhaseEfficiency; }

    /**
     * Returns the fractional Isp loss due to two-phase effects,
     * {@code 1 − η_2φ}.
     *
     * @return fractional loss; multiply by 100 for percent
     */
    public double ispLossFraction() { return ispLossFraction; }

    /**
     * Returns the exit velocity lag {@code (V_gas − V_particle) / V_gas} at the nozzle exit.
     *
     * @return dimensionless exit velocity lag; 0 means perfect gas tracking
     */
    public double exitVelocityLag() {
        return particleTrajectory.isEmpty() ? 0.0 : particleTrajectory.getLast().velocityLag();
    }

    /**
     * Returns the exit thermal lag {@code (T_gas − T_particle) / T_gas} at the exit.
     *
     * @return dimensionless exit temperature lag
     */
    public double exitThermalLag() {
        return particleTrajectory.isEmpty() ? 0.0 : particleTrajectory.getLast().thermalLag();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Computes two-phase Isp efficiency from exit velocity and temperature lag. */
    private void computeEfficiency(CharacteristicPoint exitPoint) {
        if (particleTrajectory.isEmpty()) {
            twoPhaseEfficiency = 1.0;
            ispLossFraction    = 0.0;
            return;
        }

        ParticleState exitParticle = particleTrajectory.getLast();
        double Vge = exitPoint.velocity();
        double Vpe = exitParticle.velocity();
        double Tge = exitPoint.temperature();
        double Tpe = exitParticle.temperature();

        // Velocity lag contribution: α × (1 − V_pe/V_ge)
        double vLagTerm = particleMassFraction * (1.0 - Vpe / Math.max(Vge, 1.0));

        // Temperature lag contribution (empirical factor β = 0.1 α)
        double tLagTerm = 0.1 * particleMassFraction
                          * Math.sqrt(Math.abs(1.0 - Tpe / Math.max(Tge, 1.0)));

        ispLossFraction    = Math.max(0.0, vLagTerm + tLagTerm);
        twoPhaseEfficiency = 1.0 - ispLossFraction;
    }
}
