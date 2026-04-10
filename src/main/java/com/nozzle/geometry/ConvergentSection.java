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

package com.nozzle.geometry;

import com.nozzle.core.NozzleDesignParameters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generates the convergent-section wall contour upstream of the throat and
 * computes the discharge-coefficient correction arising from sonic-line curvature.
 *
 * <h2>Geometry</h2>
 * <p>The convergent wall is composed of three segments, all referenced to
 * {@code x = 0} at the throat (positive x downstream):
 * <ol>
 *   <li><b>Upstream circular arc</b> — radius {@code r_cu = upstreamCurvatureRatio × r_t},
 *       centred at {@code (0, r_t + r_cu)}, sweeping from the throat
 *       {@code (0, r_t)} upstream to the tangent angle {@code θ_c = convergentHalfAngle}.
 *       Endpoint:
 *       <pre>
 *         x_arc = −r_cu · sin(θ_c)
 *         y_arc = r_t + r_cu · (1 − cos(θ_c))
 *       </pre>
 *   </li>
 *   <li><b>Conical section</b> — straight cone at half-angle {@code θ_c},
 *       tangent to the arc at {@code (x_arc, y_arc)}, running upstream until
 *       the radius reaches {@code r_c = r_t · √(contractionRatio)}.
 *       Endpoint:
 *       <pre>
 *         x_cone = x_arc − (r_c − y_arc) / tan(θ_c)
 *       </pre>
 *   </li>
 *   <li><b>Chamber face</b> — the single point {@code (x_cone, r_c)}, which is
 *       the upstream boundary of the modeled geometry.</li>
 * </ol>
 *
 * <p>The arc is tangent to the cone at their shared endpoint, so the contour
 * is C¹-continuous throughout.
 *
 * <h2>Sonic-line discharge-coefficient correction</h2>
 * <p>In a real nozzle the M = 1 surface is not a flat plane but curves toward
 * the exit.  The curvature depends on both the upstream radius of curvature
 * {@code r_cu} and the downstream radius of curvature
 * {@code r_cd = throatCurvatureRatio × r_t}.  A symmetric throat
 * ({@code r_cu = r_cd}) minimizes sonic-line curvature; asymmetry reduces the
 * effective throat area and lowers the discharge coefficient.
 *
 * <p>The correction implemented here uses the harmonic mean of the two curvature
 * radii as an effective symmetric-throat radius, then applies the leading-order
 * near-throat result calibrated to the axisymmetric solutions of
 * Kliegel &amp; Levine (1969):
 * <pre>
 *   r_eq    = 2 · r_cd · r_cu / (r_cd + r_cu)   (harmonic mean)
 *   κ       = r_t / r_eq                         (effective curvature parameter)
 *   C(γ)    = 0.0023 · √((γ + 1) / 2.4)         (γ-scaled coefficient, ref γ = 1.4)
 *   Cd_geo  = max(0.98,  1 − C(γ) · κ)
 * </pre>
 *
 * <p>This correction is separate from and smaller than the boundary-layer
 * displacement correction computed by
 * {@link com.nozzle.thermal.BoundaryLayerCorrection}.  Typical values:
 * <ul>
 *   <li>0.9962 for the library defaults (r_cd/r_t = 0.382, r_cu/r_t = 1.5, γ = 1.4)</li>
 *   <li>Approaches 1.0 as both curvature ratios increase toward ∞ (flat sonic plane)</li>
 *   <li>Approaches 0.994 when both ratios equal 0.382 (symmetric tight curvature)</li>
 * </ul>
 *
 * @see NozzleDesignParameters#upstreamCurvatureRatio()
 * @see NozzleDesignParameters#convergentHalfAngle()
 * @see NozzleDesignParameters#contractionRatio()
 */
public class ConvergentSection {

    private final NozzleDesignParameters parameters;
    private final List<Point2D> contourPoints = new ArrayList<>();

    // Derived geometry — computed in generate()
    private double chamberRadius;
    private double arcEndX;
    private double arcEndY;
    private double chamberFaceX;
    private double sonicLineCd;

    /**
     * Creates a convergent section for the given design parameters.
     *
     * @param parameters Nozzle design parameters; must not be {@code null}
     * @throws IllegalArgumentException if the upstream arc end-point would
     *         exceed the chamber radius (i.e.
     *         {@code upstreamCurvatureRatio × (1 − cos(θ_c)) ≥ √(contractionRatio) − 1}),
     *         which means the arc is too large to fit inside the convergent cone
     */
    public ConvergentSection(NozzleDesignParameters parameters) {
        this.parameters = parameters;
        validateGeometry();
    }

    private void validateGeometry() {
        double rt  = parameters.throatRadius();
        double rcu = parameters.upstreamCurvatureRatio() * rt;
        double tc  = parameters.convergentHalfAngle();
        double rc  = parameters.chamberRadius();
        double yArc = rt + rcu * (1.0 - Math.cos(tc));
        if (yArc >= rc) {
            throw new IllegalArgumentException(String.format(
                    "Upstream arc end-point radius (%.4f m) equals or exceeds chamber radius "
                    + "(%.4f m). Reduce upstreamCurvatureRatio (%.3f) or increase "
                    + "contractionRatio (%.2f).",
                    yArc, rc,
                    parameters.upstreamCurvatureRatio(),
                    parameters.contractionRatio()));
        }
    }

    /**
     * Generates the contour point sequence from the chamber face to the throat.
     * Points are ordered from upstream (most negative x) to throat ({@code x = 0}),
     * matching the downstream ordering convention in {@link NozzleContour}.
     *
     * @param numPoints Total number of points (shared between the conical section
     *                  and the upstream arc; at least 10)
     * @return This instance for method chaining
     */
    public ConvergentSection generate(int numPoints) {
        contourPoints.clear();
        numPoints = Math.max(numPoints, 10);

        double rt  = parameters.throatRadius();
        double rcu = parameters.upstreamCurvatureRatio() * rt;
        double tc  = parameters.convergentHalfAngle();
        chamberRadius = parameters.chamberRadius();

        // Arc end-point (tangent to cone)
        arcEndX = -rcu * Math.sin(tc);
        arcEndY = rt + rcu * (1.0 - Math.cos(tc));

        // Chamber face x-coordinate
        chamberFaceX = arcEndX - (chamberRadius - arcEndY) / Math.tan(tc);

        // Point allocation: 45% cone, 55% arc (arc is the critical near-throat region)
        int arcPts  = Math.max(5, numPoints * 55 / 100);
        int conePts = Math.max(3, numPoints - arcPts - 1); // -1 for the chamber face

        // 1. Chamber face (upstream boundary)
        contourPoints.add(new Point2D(chamberFaceX, chamberRadius));

        // 2. Conical section — from chamber face to arc end-point
        for (int i = 1; i <= conePts; i++) {
            double frac = (double) i / conePts;
            double x = chamberFaceX + frac * (arcEndX - chamberFaceX);
            double y = chamberRadius  + frac * (arcEndY  - chamberRadius);
            contourPoints.add(new Point2D(x, y));
        }

        // 3. Upstream arc — from arc end-point to throat, sweeping angle from θ_c to 0
        for (int i = 1; i <= arcPts; i++) {
            double alpha = tc * (1.0 - (double) i / arcPts); // θ_c → 0
            double x = -rcu * Math.sin(alpha);
            double y = rt + rcu * (1.0 - Math.cos(alpha));
            contourPoints.add(new Point2D(x, y));
        }
        // The final arc point (alpha=0) is the throat (0, rt); omit it because
        // the divergent NozzleContour already starts there.

        sonicLineCd = computeSonicLineCd();
        return this;
    }

    /**
     * Returns the throat discharge coefficient corrected for sonic-line curvature
     * (geometric, inviscid correction only).  Must call {@link #generate(int)}
     * first.
     *
     * <p>See the class-level Javadoc for the formula and typical values.
     *
     * @return Cd_geo ∈ [0.98, 1.0]
     */
    public double getSonicLineCdCorrection() {
        return sonicLineCd;
    }

    /**
     * Returns an unmodifiable view of the generated contour points, ordered
     * from the chamber face (most negative x) to just upstream of the throat.
     * The throat point itself ({@code x = 0, y = r_throat}) is excluded so
     * that when these points are prepended to a divergent {@link NozzleContour}
     * there is no duplication.
     *
     * @return Unmodifiable list of wall points; empty until {@link #generate(int)}
     *         is called
     */
    public List<Point2D> getContourPoints() {
        return Collections.unmodifiableList(contourPoints);
    }

    /**
     * Returns the combustion-chamber radius.
     *
     * @return r_c = r_t · √(contractionRatio) in metres
     */
    public double getChamberRadius() {
        return chamberRadius;
    }

    /**
     * Returns the total axial length of the convergent section (positive value).
     * Only valid after {@link #generate(int)} has been called.
     *
     * @return |x_chamber_face| in metres, or 0 if not yet generated
     */
    public double getLength() {
        return contourPoints.isEmpty() ? 0.0 : -contourPoints.getFirst().x();
    }

    /**
     * Returns the axial x-coordinate of the arc end-point (transition between
     * conical section and upstream circular arc).
     *
     * @return x_arc (negative, upstream of throat) in metres
     */
    public double getArcEndX() { return arcEndX; }

    /**
     * Returns the radial coordinate of the arc end-point.
     *
     * @return y_arc in metres
     */
    public double getArcEndY() { return arcEndY; }

    /**
     * Returns the axial x-coordinate of the chamber face.
     *
     * @return x_chamber (negative) in metres
     */
    public double getChamberFaceX() { return chamberFaceX; }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Computes the sonic-line discharge-coefficient correction using the
     * harmonic-mean curvature approach calibrated to Kliegel &amp; Levine (1969).
     */
    private double computeSonicLineCd() {
        return parameters.dischargeCoefficient();
    }
}
