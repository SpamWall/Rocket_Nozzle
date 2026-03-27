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
import com.nozzle.geometry.Point2D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Computes and holds the spike (plug) contour for an Aerospike nozzle using the
 * Method of Characteristics kernel-flow algorithm.
 *
 * <p>The spike contour is the inner streamline of the centered Prandtl-Meyer
 * expansion fan that originates at the cowl lip {@code (0, rt)}.  At each
 * angular step {@code δ_i} (0 → ν(M_exit)) the next contour point is found
 * at the intersection of:
 * <ol>
 *   <li>The C⁻ characteristic from the lip at turning angle {@code δ_i}:
 *       {@code r = rt − x · tan(δ_i + μ_i)}.</li>
 *   <li>The streamline from the previous contour point with average flow angle
 *       {@code ½(δ_{i-1} + δ_i)}:
 *       {@code r = r_{i-1} − (x − x_{i-1}) · tan(δ_avg)}.</li>
 * </ol>
 * The initial condition is {@code (x=0, r=ri)}: the spike surface starts at the
 * inner edge of the annular throat.
 *
 * <p>This class is an implementation detail of {@link AerospikeNozzle}; callers
 * should use {@code AerospikeNozzle} for the complete nozzle design and
 * performance API.
 */
class AerospikeContour {

    private final NozzleDesignParameters parameters;
    /** ri / rt  (inner throat radius / outer throat radius). */
    private final double spikeRadiusRatio;
    /** Fraction of the full ideal spike length used for the physical spike (0 < f ≤ 1). */
    private final double truncationFraction;
    /** Number of discrete points on the spike contour. */
    private final int numSpikePoints;

    /** Full ideal spike contour; empty until {@link #generate()} is called. */
    private final List<Point2D> fullSpikeContour;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Creates an Aerospike contour generator.  All parameter validation is
     * performed by the enclosing {@link AerospikeNozzle} constructor.
     *
     * @param parameters         Nozzle design parameters
     * @param spikeRadiusRatio   {@code ri / rt}; caller guarantees (0, 1)
     * @param truncationFraction Fraction of full spike length to retain; caller guarantees (0, 1]
     * @param numSpikePoints     Contour resolution; caller guarantees ≥ 2
     */
    AerospikeContour(NozzleDesignParameters parameters,
                     double spikeRadiusRatio,
                     double truncationFraction,
                     int numSpikePoints) {
        this.parameters = parameters;
        this.spikeRadiusRatio = spikeRadiusRatio;
        this.truncationFraction = truncationFraction;
        this.numSpikePoints = numSpikePoints;
        this.fullSpikeContour = new ArrayList<>();
    }

    // -------------------------------------------------------------------------
    // Generation
    // -------------------------------------------------------------------------

    /**
     * Computes the full ideal spike contour using the MOC kernel-flow algorithm.
     *
     * <p>The algorithm traces the inner streamline of the centered Prandtl-Meyer
     * expansion fan at the cowl lip.  At each of the {@code numSpikePoints} angular
     * steps the next spike-contour point is found at the intersection of the current
     * C⁻ characteristic from the lip and the streamline from the previous spike point.
     *
     * @return This instance, for method chaining
     */
    AerospikeContour generate() {
        fullSpikeContour.clear();

        GasProperties gas = parameters.gasProperties();
        double rt      = parameters.throatRadius();
        double ri      = rt * spikeRadiusRatio;
        double nu_exit = gas.prandtlMeyerFunction(parameters.exitMach());

        // Initial spike point: inner edge of the annular throat.
        // At δ = 0: flow is axial (M = 1 at sonic throat), spike starts at (0, ri).
        double xPrev     = 0.0;
        double rPrev     = ri;
        double deltaPrev = 0.0;
        fullSpikeContour.add(new Point2D(xPrev, rPrev));

        for (int i = 1; i <= numSpikePoints; i++) {
            double delta_i = (double) i / numSpikePoints * nu_exit;
            double mach_i  = gas.machFromPrandtlMeyer(delta_i);
            double mu_i    = gas.machAngle(mach_i);

            // C⁻ characteristic from lip (0, rt) at turning angle δ_i:
            //   r = rt − x · tan(δ_i + μ_i)
            double slopeChar = Math.tan(delta_i + mu_i);

            // Streamline from previous spike point with average flow angle:
            //   r = rPrev − (x − xPrev) · tan(δ_avg)
            double deltaAvg    = (deltaPrev + delta_i) / 2.0;
            double slopeStream = Math.tan(deltaAvg);

            // denom = slopeChar − slopeStream is always >> 0 for a valid Prandtl-Meyer
            // expansion: near the throat slopeChar → ∞ while slopeStream → 0, and across
            // the full fan they remain distinct.  No guard needed.
            double denom = slopeChar - slopeStream;

            // Intersection: characteristic meets streamline
            //   rt − x_i · slopeChar = rPrev − (x_i − xPrev) · slopeStream
            //   x_i · (slopeChar − slopeStream) = rt − rPrev − xPrev · slopeStream
            double x_i = (rt - rPrev - xPrev * slopeStream) / denom;
            double r_i = rt - x_i * slopeChar;

            // Enforce physical monotonicity: spike must advance forward and curve inward.
            // Floating-point accumulated error can occasionally push an intersection
            // slightly backward; clamp to the previous point and re-evaluate r on the
            // current characteristic so the contour stays consistent.
            if (x_i < xPrev) {
                x_i = xPrev;
                r_i = rt - x_i * slopeChar;
            }
            r_i = Math.min(r_i, rPrev);

            fullSpikeContour.add(new Point2D(x_i, r_i));
            xPrev     = x_i;
            rPrev     = r_i;
            deltaPrev = delta_i;
        }

        return this;
    }

    // -------------------------------------------------------------------------
    // Contour accessors
    // -------------------------------------------------------------------------

    /**
     * Returns an unmodifiable view of the full ideal spike contour.
     * Calls {@link #generate()} first if the contour has not yet been computed.
     *
     * @return List of {@link Point2D} contour points from the throat (index 0) to the
     *         spike tip (last index), in axial order
     */
    List<Point2D> getFullSpikeContour() {
        if (fullSpikeContour.isEmpty()) {
            generate();
        }
        return Collections.unmodifiableList(fullSpikeContour);
    }

    /**
     * Returns the truncated spike contour — only the portion of the full spike up to
     * {@code truncationFraction × fullSpikeLength}.
     *
     * <p>The first contour point is always at {@code x = 0}, and
     * {@code cutX = fullSpikeLength × truncationFraction ≥ 0}, so the returned
     * list is never empty.
     *
     * @return List of {@link Point2D} for the truncated spike, never empty after generation
     */
    List<Point2D> getTruncatedSpikeContour() {
        List<Point2D> full = getFullSpikeContour();
        double cutX = getFullSpikeLength() * truncationFraction;

        List<Point2D> truncated = new ArrayList<>();
        for (Point2D p : full) {
            if (p.x() <= cutX) {
                truncated.add(p);
            }
        }
        return Collections.unmodifiableList(truncated);
    }

    // -------------------------------------------------------------------------
    // Geometry
    // -------------------------------------------------------------------------

    /**
     * Returns the axial length of the full ideal spike.
     *
     * @return Full spike length in metres; 0 if {@link #generate()} has not been called
     */
    double getFullSpikeLength() {
        if (fullSpikeContour.isEmpty()) {
            return 0.0;
        }
        return fullSpikeContour.getLast().x();
    }

    /**
     * Returns the axial length of the truncated spike.
     *
     * @return Truncated spike length = {@code truncationFraction × fullSpikeLength} in metres
     */
    double getTruncatedLength() {
        return getFullSpikeLength() * truncationFraction;
    }

    /**
     * Returns the annular throat flow area.
     *
     * <p>The annular throat is bounded by the outer cowl lip at radius {@code rt} and the
     * spike surface at radius {@code ri = rt × spikeRadiusRatio}.
     *
     * @return Annular throat area in m²; {@code π(rt² − ri²)}
     */
    double getAnnularThroatArea() {
        double rt = parameters.throatRadius();
        double ri = rt * spikeRadiusRatio;
        return Math.PI * (rt * rt - ri * ri);
    }

    /**
     * Returns the annular exit area of the full ideal Aerospike.
     *
     * <p>The exit area is computed from mass-flow conservation:
     * {@code Ae = At × (Ae/At)} where {@code Ae/At} is the isentropic area ratio at
     * the design exit Mach number.
     *
     * @return Design exit area in m²
     */
    double getAnnularExitArea() {
        return getAnnularThroatArea() * parameters.exitAreaRatio();
    }

    /**
     * Returns the radius at the tip of the truncated spike (the base radius).
     *
     * @return Base radius of the truncated spike in metres
     */
    double getTruncatedBaseRadius() {
        return getTruncatedSpikeContour().getLast().y();
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    NozzleDesignParameters getParameters()  { return parameters; }
    double getSpikeRadiusRatio()             { return spikeRadiusRatio; }
    double getTruncationFraction()           { return truncationFraction; }
}
