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

package com.nozzle.moc;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Applies viscous boundary-layer displacement correction to an inviscid MOC wall
 * contour to produce a physically realistic nozzle profile.
 *
 * <h2>Background</h2>
 * <p>An inviscid MOC solution assumes the gas fills the entire duct.  In reality
 * a boundary layer of thickness {@code δ*(x)} displaces the effective flow
 * boundary inward.  To achieve the design Mach number at the exit the
 * <em>physical</em> (machined) wall must be offset <em>outward</em> by
 * {@code δ*(x)} relative to the inviscid contour:
 * <pre>
 *   r_physical(x) = r_inviscid(x) + δ*(x)
 * </pre>
 *
 * <h2>Boundary-layer model</h2>
 * <p>The displacement thickness uses the compressible turbulent flat-plate
 * correlation of White (1974), corrected for the local Mach number via the
 * van Driest–White transformation:
 * <pre>
 *   δ*(x) = 0.046 · x · Re_x^(−1/5) · (1 + r · (γ−1)/2 · M²)^(1/2)
 * </pre>
 * where {@code Re_x = ρ V x / μ} is the local Reynolds number from the
 * throat and {@code r = Pr^(1/3)} is the recovery factor.  The integral starts
 * at the throat ({@code x = 0}) and uses flow properties from the inviscid
 * MOC wall point at each axial station.
 *
 * <h2>Throat correction</h2>
 * <p>At the throat the BL displacement contracts the effective throat radius:
 * <pre>
 *   r_t_eff = r_t − δ*(0⁺)
 *   Cd_BL   = (r_t_eff / r_t)²
 * </pre>
 * This {@code Cd_BL} multiplies the inviscid thrust coefficient to give the
 * viscous-corrected value.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * CharacteristicNet net = new CharacteristicNet(params).generate();
 * ViscousMOCSolver viscous = new ViscousMOCSolver(net).solve();
 *
 * List<ViscousWallPoint> wall = viscous.getCorrectedWall();
 * double Cd = viscous.dischargeCoefficient();
 * double ispLoss = viscous.viscousIspLossFraction();
 * }</pre>
 */
public class ViscousMOCSolver {

    private static final Logger LOG = LoggerFactory.getLogger(ViscousMOCSolver.class);

    /**
     * A single wall station with both the inviscid and viscous-corrected radii
     * together with the local boundary-layer displacement thickness.
     *
     * @param x                  axial coordinate (m)
     * @param rInviscid          inviscid wall radius (m)
     * @param displacementThickness boundary-layer displacement thickness δ*(m)
     * @param rPhysical          machined wall radius = rInviscid + δ* (m)
     * @param mach               local Mach number from inviscid MOC
     * @param wallAngle          local wall angle (radians) from inviscid MOC
     * @param localReynolds      local Reynolds number Re_x from throat
     */
    public record ViscousWallPoint(
            double x,
            double rInviscid,
            double displacementThickness,
            double rPhysical,
            double mach,
            double wallAngle,
            double localReynolds
    ) {}

    private final CharacteristicNet net;
    private final NozzleDesignParameters params;
    private final GasProperties gas;

    private final List<ViscousWallPoint> correctedWall = new ArrayList<>();
    private double dischargeCoefficient;
    private double viscousIspLossFraction;

    /**
     * Creates a viscous MOC solver wrapping a completed inviscid characteristic net.
     *
     * @param net a fully generated {@link CharacteristicNet} (must have called
     *            {@link CharacteristicNet#generate()} before passing here)
     * @throws IllegalArgumentException if the net has no wall points
     */
    public ViscousMOCSolver(CharacteristicNet net) {
        if (net.getWallPoints().isEmpty()) {
            throw new IllegalArgumentException(
                    "CharacteristicNet must be generated before ViscousMOCSolver");
        }
        this.net    = net;
        this.params = net.getParameters();
        this.gas    = params.gasProperties();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Computes the viscous-corrected wall profile.
     *
     * @return this instance for method chaining
     */
    public ViscousMOCSolver solve() {
        List<CharacteristicPoint> wall = net.getWallPoints();

        double Pr  = prandtlNumber();
        double r   = Math.pow(Pr, 1.0 / 3.0);   // recovery factor
        double rt  = params.throatRadius();

        // Accumulate running path length along the wall from x=0
        double pathLength = 0.0;

        for (int i = 0; i < wall.size(); i++) {
            CharacteristicPoint wp = wall.get(i);
            double x = wp.x();
            double rw = wp.y();

            if (i > 0) {
                CharacteristicPoint prev = wall.get(i - 1);
                pathLength += Math.hypot(x - prev.x(), rw - prev.y());
            }

            double mach   = wp.mach();
            double T      = wp.temperature();
            double rho    = wp.density();
            double V      = wp.velocity();
            double mu     = gas.calculateViscosity(T);

            // Local Reynolds number along wall running-length from throat
            double reX = (pathLength > 1e-10)
                         ? rho * V * pathLength / mu
                         : 1.0;

            // Displacement thickness: White compressible turbulent flat-plate
            double compFactor = Math.sqrt(1.0 + r * (gas.gamma() - 1.0) / 2.0 * mach * mach);
            double deltaStarRaw = (reX > 1.0)
                                  ? 0.046 * pathLength * Math.pow(reX, -0.2)
                                  : 0.0;
            double deltaStar = deltaStarRaw * compFactor;

            double rPhysical = rw + deltaStar;

            double wallAngle = (i > 0)
                    ? Math.atan2(rw - wall.get(i - 1).y(), x - wall.get(i - 1).x())
                    : wp.theta();

            correctedWall.add(new ViscousWallPoint(x, rw, deltaStar, rPhysical, mach, wallAngle, reX));
        }

        // Throat correction: BL at throat displaces effective radius inward
        double deltaThroat = correctedWall.isEmpty() ? 0.0
                : correctedWall.getFirst().displacementThickness();
        double rtEff = rt - deltaThroat;
        dischargeCoefficient = (rtEff / rt) * (rtEff / rt);
        dischargeCoefficient = Math.clamp(dischargeCoefficient, 0.90, 1.0);

        // Viscous Isp loss: friction loss ≈ 0.5 × Cd_BL deficit as first-order estimate
        viscousIspLossFraction = 1.0 - dischargeCoefficient;

        LOG.debug("Viscous MOC: Cd_BL={}, Isp_loss_pct={}, wallPoints={}",
                dischargeCoefficient, viscousIspLossFraction * 100, correctedWall.size());
        return this;
    }

    /**
     * Returns the viscous-corrected wall profile.  Each point contains both the
     * inviscid and physical (machined) wall radii.
     *
     * @return unmodifiable list of {@link ViscousWallPoint}s in axial order
     */
    public List<ViscousWallPoint> getCorrectedWall() {
        return Collections.unmodifiableList(correctedWall);
    }

    /**
     * Returns the boundary-layer discharge coefficient
     * {@code Cd = (r_t_eff / r_t)²}, accounting for throat-area contraction
     * by the boundary layer.
     *
     * @return Cd ∈ [0.90, 1.00]; close to 1.0 for well-designed nozzles
     */
    public double dischargeCoefficient() { return dischargeCoefficient; }

    /**
     * Returns the fractional Isp loss due to viscous effects,
     * approximately {@code 1 − Cd_BL}.
     *
     * @return fractional loss in [0, 0.10]; multiply by 100 for percent
     */
    public double viscousIspLossFraction() { return viscousIspLossFraction; }

    /**
     * Returns the maximum displacement thickness along the wall in metres.
     *
     * @return peak δ*(m), or 0 if not yet solved
     */
    public double maxDisplacementThickness() {
        return correctedWall.stream()
                .mapToDouble(ViscousWallPoint::displacementThickness)
                .max()
                .orElse(0.0);
    }

    /**
     * Returns the physical (machined) exit radius, enlarged by the exit-plane
     * boundary-layer displacement thickness.
     *
     * @return physical exit radius in metres, or the inviscid exit radius if
     *         not yet solved
     */
    public double physicalExitRadius() {
        if (correctedWall.isEmpty()) return params.exitRadius();
        return correctedWall.getLast().rPhysical();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Estimates Prandtl number from viscosity and specific heat. */
    private double prandtlNumber() {
        double Cp = gas.specificHeatCp();
        double mu = gas.calculateViscosity(params.chamberTemperature() * 0.6);
        // Thermal conductivity from Eucken approximation: k ≈ μ × Cp × (9γ−5)/(4γ)
        double kThermal = mu * Cp * (9.0 * gas.gamma() - 5.0) / (4.0 * gas.gamma());
        double Pr = mu * Cp / kThermal;
        return Math.clamp(Pr, 0.5, 1.0);   // physical range for gases
    }
}
