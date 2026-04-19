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

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.core.Point2D;
import com.nozzle.moc.CharacteristicPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the divergent wall contour of a rocket nozzle and provides
 * geometry services (radius, slope, surface area) used by heat-transfer
 * and performance analyses.
 *
 * <p>Five contour families are supported:
 * <ul>
 *   <li>{@link ContourType#CONICAL} — straight conical wall, the simplest baseline.</li>
 *   <li>{@link ContourType#RAO_BELL} — cubic Bézier bell contour that approximates the
 *       Rao thrust-optimized shape without a full MOC solve.</li>
 *   <li>{@link ContourType#TRUNCATED_IDEAL} — Truncated Ideal Contour (TIC): the ideal
 *       nozzle (perfectly uniform exit flow at the design Mach) is parameterized in
 *       Prandtl-Meyer space and truncated at {@code lengthFraction × L_ref}.  Widely
 *       used in upper stages where simplicity and predictable separation behavior are
 *       more important than the last fraction of a percent of thrust.</li>
 *   <li>{@link ContourType#CUSTOM_SPLINE} — user-supplied control points fitted with a
 *       natural cubic spline.</li>
 *   <li>{@link ContourType#MOC_GENERATED} — wall points produced by {@link
 *       com.nozzle.moc.CharacteristicNet} and smoothed via
 *       {@link #fromMOCWallPoints(NozzleDesignParameters, java.util.List)}.</li>
 * </ul>
 *
 * <p>Usage example:
 * <pre>{@code
 * NozzleContour contour = new NozzleContour(ContourType.RAO_BELL, params);
 * contour.generate(200);
 * double r = contour.getRadiusAt(0.05); // radius at x = 50 mm
 * }</pre>
 */
public class NozzleContour {

    /**
     * Classification of the mathematical method used to define the divergent
     * wall profile.
     */
    public enum ContourType {
        /** Straight-walled conical nozzle with a circular-arc throat transition. */
        CONICAL,
        /** Rao-style bell nozzle approximated with a cubic Bézier curve. */
        RAO_BELL,
        /**
         * Truncated Ideal Contour (TIC).
         *
         * <p>The ideal nozzle for design Mach {@code exitMach} produces perfectly
         * uniform, parallel flow at its exit when run to its full length.  This
         * family truncates that ideal contour at {@code lengthFraction × L_ref}
         * (where {@code L_ref} is the 15° reference-cone length to the full exit
         * radius), yielding a shorter nozzle with a small but non-zero residual
         * exit wall angle.
         *
         * <p>The wall is parameterized in Prandtl-Meyer space: the PM angle is
         * linearly interpolated between the inflection value ν_n = ν(M_n) and
         * the design value ν_e = ν(M_D) at the truncation fraction, which gives
         * the TIC exit Mach M_TIC and radius r_TIC.  The exit wall angle is
         * {@code (1 − f) × θ_n}, where θ_n is {@code wallAngleInitial} and f is
         * {@code lengthFraction}.  A cubic Bézier blends the inflection point to
         * the TIC exit with the correct tangent slopes at both ends.
         *
         * <p>Role of each design parameter:
         * <ul>
         *   <li>{@code wallAngleInitial} — throat-arc inflection angle θ_n</li>
         *   <li>{@code exitMach}         — full ideal nozzle design Mach M_D</li>
         *   <li>{@code lengthFraction}   — truncation fraction f ∈ (0, 1]</li>
         * </ul>
         */
        TRUNCATED_IDEAL,
        /**
         * Nozzle wall defined by user-supplied control points and interpolated
         * with a natural cubic spline.
         */
        CUSTOM_SPLINE,
        /**
         * Wall points generated directly by the Method-of-Characteristics solver
         * ({@link com.nozzle.moc.CharacteristicNet}) and smoothed with a cubic
         * spline.  Use {@link NozzleContour#fromMOCWallPoints} to construct.
         */
        MOC_GENERATED
    }
    
    private final ContourType type;
    private final NozzleDesignParameters parameters;
    private final List<Point2D> controlPoints;
    private final List<Point2D> contourPoints;
    
    private double[] splineCoeffA;
    private double[] splineCoeffB;
    private double[] splineCoeffC;
    private double[] splineCoeffD;
    private double[] splineX;
    
    /**
     * Creates an empty contour of the given type.
     * Call {@link #generate(int)} (or {@link #fromMOCWallPoints} for
     * {@link ContourType#MOC_GENERATED}) to populate the contour points.
     *
     * @param type       Contour family to generate
     * @param parameters Nozzle design parameters that define throat radius,
     *                   exit radius, wall angle, and length fraction
     */
    public NozzleContour(ContourType type, NozzleDesignParameters parameters) {
        this.type = type;
        this.parameters = parameters;
        this.controlPoints = new ArrayList<>();
        this.contourPoints = new ArrayList<>();
    }
    
    /**
     * Factory method that builds a {@link ContourType#MOC_GENERATED} contour from
     * the wall-point sequence produced by a {@link com.nozzle.moc.CharacteristicNet}.
     *
     * <p>Consecutive points closer than {@code 0.05 × r_throat} are skipped to keep
     * the underlying cubic-spline system well-conditioned (closely spaced knots cause
     * the tridiagonal h-coefficients to approach zero).
     *
     * @param parameters  Nozzle design parameters (used to determine the minimum
     *                    knot spacing and as the reference for downstream queries)
     * @param wallPoints  Ordered list of wall characteristic points from the MOC
     *                    solver; must contain at least two points after filtering
     * @return A fully initialised {@code NozzleContour} with its cubic spline fitted
     *         to the filtered MOC wall points
     */
    public static NozzleContour fromMOCWallPoints(NozzleDesignParameters parameters,
                                                   List<CharacteristicPoint> wallPoints) {
        NozzleContour contour = new NozzleContour(ContourType.MOC_GENERATED, parameters);
        // Minimum x-spacing to keep cubic spline well-conditioned.
        // Closely-spaced control points (h -> 0) cause 3/h terms to explode.
        double minSpacing = parameters.throatRadius() * 0.05;
        double lastX = Double.NEGATIVE_INFINITY;
        for (CharacteristicPoint point : wallPoints) {
            if (point.x() - lastX >= minSpacing) {
                contour.controlPoints.add(new Point2D(point.x(), point.y()));
                lastX = point.x();
            }
        }
        contour.generateSpline();
        return contour;
    }

    /**
     * Returns a new {@link NozzleContour} whose wall-point sequence is the
     * convergent section prepended to this divergent contour, producing a
     * geometry-complete nozzle profile from the chamber face to the exit plane.
     *
     * <p>The {@link ConvergentSection} must have been populated by calling
     * {@link ConvergentSection#generate(int)} before passing it here.  The
     * throat point ({@code x = 0, y = r_t}) is already included as the first
     * point of this (divergent) contour, so the last point of the convergent
     * sequence — which would also be the throat — is intentionally excluded to
     * avoid duplication.
     *
     * <p>The cubic spline is refitted over the full combined x-range (from
     * the negative chamber-face x to the positive exit x), so
     * {@link #getRadiusAt(double)} remains valid for any position in
     * [{@code x_chamber}, {@code x_exit}].
     *
     * <p>Passing the returned contour to
     * {@link com.nozzle.thermal.BoundaryLayerCorrection} automatically extends
     * the boundary-layer integration upstream through the convergent section,
     * capturing the extra momentum-thickness growth before the throat.
     * All exporters ({@link com.nozzle.export.DXFExporter},
     * {@link com.nozzle.export.STLExporter},
     * {@link com.nozzle.export.STEPExporter},
     * {@link com.nozzle.export.CFDMeshExporter},
     * {@link com.nozzle.export.OpenFOAMExporter}) likewise receive the full
     * geometry without further modification.
     *
     * @param cs A fully generated {@link ConvergentSection}; must not be {@code null}
     *           and must have been populated by {@link ConvergentSection#generate(int)}
     * @return A new {@code NozzleContour} of type {@link ContourType#MOC_GENERATED}
     *         spanning from the chamber face to the nozzle exit
     * @throws IllegalStateException if the convergent section has not been generated
     */
    public NozzleContour withConvergentSection(ConvergentSection cs) {
        if (cs.getContourPoints().isEmpty()) {
            throw new IllegalStateException(
                    "ConvergentSection has no points — call generate() before withConvergentSection()");
        }
        List<Point2D> combined = new ArrayList<>(cs.getContourPoints());
        combined.addAll(this.contourPoints);
        return NozzleContour.fromPoints(parameters, combined);
    }

    /**
     * Factory method that builds a {@link ContourType#MOC_GENERATED} contour directly
     * from a pre-computed wall-point sequence (e.g. from {@link com.nozzle.moc.RaoNozzle}
     * or {@link com.nozzle.moc.DualBellNozzle}).
     *
     * <p>The supplied points are stored as-is in {@code contourPoints} so that
     * {@link #getContourPoints()} returns the original geometry without re-interpolation.
     * Control points are filtered to a minimum spacing of {@code 0.05 × r_throat} before
     * fitting the cubic spline, keeping {@link #getRadiusAt(double)} well-conditioned.
     *
     * @param parameters Design parameters for the nozzle
     * @param points     Ordered wall points (x = axial, y = radial, both in metres)
     * @return A fully initialised {@code NozzleContour} backed by the supplied points
     */
    public static NozzleContour fromPoints(NozzleDesignParameters parameters,
                                           List<Point2D> points) {
        NozzleContour contour = new NozzleContour(ContourType.MOC_GENERATED, parameters);
        double minSpacing = parameters.throatRadius() * 0.05;
        double lastX = Double.NEGATIVE_INFINITY;
        for (Point2D point : points) {
            if (point.x() - lastX >= minSpacing) {
                contour.controlPoints.add(point);
                lastX = point.x();
            }
        }
        contour.generateSpline();
        contour.contourPoints.addAll(points);
        return contour;
    }

    /**
     * Generates (or regenerates) the discrete wall-contour point list.
     * The generation algorithm is selected by the {@link ContourType} supplied
     * at construction time.
     *
     * @param numPoints Total number of contour points to generate, including
     *                  both the throat arc region and the divergent bell or
     *                  cone section
     * @return This instance for method chaining
     */
    public NozzleContour generate(int numPoints) {
        contourPoints.clear();
        
        switch (type) {
            case CONICAL          -> generateConicalContour(numPoints);
            case RAO_BELL         -> generateBellContour(numPoints);
            case TRUNCATED_IDEAL  -> generateTruncatedIdealContour(numPoints);
            case CUSTOM_SPLINE, MOC_GENERATED -> generateSplineContour(numPoints);
        }
        
        return this;
    }
    
    /**
     * Populates {@code contourPoints} with a conical divergent section preceded
     * by a circular-arc throat transition whose radius is
     * {@link NozzleDesignParameters#throatCurvatureRatio()} × r_throat.
     * The cone half-angle is taken from {@link NozzleDesignParameters#wallAngleInitial()}.
     *
     * @param numPoints Total points to distribute between the throat arc and cone
     */
    private void generateConicalContour(int numPoints) {
        double rt = parameters.throatRadius();
        double re = parameters.exitRadius();
        double halfAngle = parameters.wallAngleInitial();
        double length = (re - rt) / Math.tan(halfAngle);
        
        double rThroat = parameters.throatCurvatureRatio() * rt;
        int throatPoints = numPoints / 5;
        
        for (int i = 0; i <= throatPoints; i++) {
            double angle = i * halfAngle / throatPoints;
            double x = rThroat * Math.sin(angle);
            double y = rt + rThroat * (1 - Math.cos(angle));
            contourPoints.add(new Point2D(x, y));
        }
        
        double xStart = contourPoints.getLast().x();
        double yStart = contourPoints.getLast().y();
        int conePoints = numPoints - throatPoints - 1;
        
        for (int i = 1; i <= conePoints; i++) {
            double x = xStart + i * (length - xStart) / conePoints;
            double y = yStart + (x - xStart) * Math.tan(halfAngle);
            contourPoints.add(new Point2D(x, y));
        }
    }
    
    /**
     * Populates {@code contourPoints} with an approximate Rao bell contour.
     * A circular-arc throat section is followed by a cubic Bézier curve that
     * blends from the throat inflection angle ({@link NozzleDesignParameters#wallAngleInitial()})
     * to a small exit angle derived from the area ratio.
     *
     * @param numPoints Total points to distribute between the throat arc and the
     *                  Bézier bell
     */
    private void generateBellContour(int numPoints) {
        double rt = parameters.throatRadius();
        double re = parameters.exitRadius();
        double thetaN = parameters.wallAngleInitial();
        double lengthFrac = parameters.lengthFraction();
        
        double coneLength = (re - rt) / Math.tan(Math.toRadians(15));
        double actualLength = coneLength * lengthFrac;
        
        double thetaE = Math.toRadians(8.0 - 0.5 * Math.log(parameters.exitAreaRatio()));
        thetaE = Math.max(thetaE, Math.toRadians(1));
        
        double rcd = parameters.throatCurvatureRatio() * rt;
        int throatPoints = numPoints / 5;

        for (int i = 0; i <= throatPoints; i++) {
            double angle = i * thetaN / throatPoints;
            double x = rcd * Math.sin(angle);
            double y = rt + rcd * (1 - Math.cos(angle));
            contourPoints.add(new Point2D(x, y));
        }
        
        Point2D p0 = contourPoints.getLast();
        Point2D p3 = new Point2D(actualLength, re);
        double dx = p3.x() - p0.x();
        Point2D p1 = new Point2D(p0.x() + dx / 3, p0.y() + dx / 3 * Math.tan(thetaN));
        Point2D p2 = new Point2D(p3.x() - dx / 3, p3.y() - dx / 3 * Math.tan(thetaE));
        
        int bellPoints = numPoints - throatPoints - 1;
        for (int i = 1; i <= bellPoints; i++) {
            double t = (double) i / bellPoints;
            double u = 1 - t;
            
            double x = u*u*u*p0.x() + 3*u*u*t*p1.x() + 3*u*t*t*p2.x() + t*t*t*p3.x();
            double y = u*u*u*p0.y() + 3*u*u*t*p1.y() + 3*u*t*t*p2.y() + t*t*t*p3.y();
            contourPoints.add(new Point2D(x, y));
        }
    }
    
    /**
     * Populates {@code contourPoints} with a Truncated Ideal Contour (TIC).
     *
     * <p>The algorithm:
     * <ol>
     *   <li>Circular throat arc (radius = throatCurvatureRatio × r_t) from 0 to θ_n.</li>
     *   <li>Mach at inflection: M_n = machFromPrandtlMeyer(θ_n); ν_n = ν(M_n).</li>
     *   <li>Design PM angle: ν_e = ν(M_D) for the full ideal nozzle.</li>
     *   <li>TIC exit: ν_TIC = ν_n + f × (ν_e − ν_n) → M_TIC, r_TIC = r_t √(A/A*(M_TIC)).</li>
     *   <li>Exit wall angle: θ_e = (1 − f) × θ_n (zero at f = 1).</li>
     *   <li>TIC length: f × (r_full − r_t) / tan(15°), where r_full = r_t √(A/A*(M_D)).</li>
     *   <li>Cubic Bézier from inflection point to (x_TIC, r_TIC).</li>
     * </ol>
     *
     * @param numPoints Total points (throat arc + Bézier divergent section)
     */
    private void generateTruncatedIdealContour(int numPoints) {
        double rt      = parameters.throatRadius();
        GasProperties gas = parameters.gasProperties();
        double thetaN  = parameters.wallAngleInitial();   // inflection angle (rad)
        double mDesign = parameters.exitMach();           // full ideal design Mach
        double f       = parameters.lengthFraction();     // truncation fraction

        // ------------------------------------------------------------------
        // 1. Circular throat arc: r_cd = throatCurvatureRatio × r_t, sweeping 0 → θ_n
        // ------------------------------------------------------------------
        double rcd = parameters.throatCurvatureRatio() * rt;
        int throatPts = Math.max(5, numPoints / 10);
        for (int i = 0; i <= throatPts; i++) {
            double angle = thetaN * i / throatPts;
            contourPoints.add(new Point2D(
                    rcd * Math.sin(angle),
                    rt  + rcd * (1.0 - Math.cos(angle))));
        }
        Point2D p0 = contourPoints.getLast();

        // ------------------------------------------------------------------
        // 2. Prandtl-Meyer angles at inflection and at full design Mach
        // ------------------------------------------------------------------
        double mN  = gas.machFromPrandtlMeyer(thetaN);   // Mach at end of throat arc
        double nuN = gas.prandtlMeyerFunction(mN);        // ≈ θ_n
        double nuE = gas.prandtlMeyerFunction(mDesign);

        // ------------------------------------------------------------------
        // 3. TIC exit conditions (linear interpolation in PM space)
        //    At f = 1: ν_TIC = ν_e → M_TIC = M_D, θ_e = 0 (ideal parallel exit)
        //    At f < 1: M_TIC < M_D, θ_e = (1−f)×θ_n > 0 (residual divergence)
        // ------------------------------------------------------------------
        double nuTIC  = nuN + f * (nuE - nuN);
        double mTIC   = gas.machFromPrandtlMeyer(nuTIC);
        double rTIC   = rt * Math.sqrt(gas.areaRatio(mTIC));
        double thetaE = (1.0 - f) * thetaN;

        // ------------------------------------------------------------------
        // 4. TIC axial length = f × (reference 15° cone length to full exit)
        // ------------------------------------------------------------------
        double reFull = rt * Math.sqrt(gas.areaRatio(mDesign));
        double lTIC   = f * (reFull - rt) / Math.tan(Math.toRadians(15.0));

        // ------------------------------------------------------------------
        // 5. Cubic Bézier: inflection → (x0 + lTIC, rTIC)
        //    Entry slope = tan(θ_n);  exit slope = tan(θ_e)
        // ------------------------------------------------------------------
        double x0  = p0.x();
        double y0  = p0.y();
        double x3  = x0 + lTIC;
        double y3  = rTIC;
        double dx  = x3 - x0;
        double cx1 = x0 + dx / 3.0;
        double cy1 = y0 + (dx / 3.0) * Math.tan(thetaN);
        double cx2 = x3 - dx / 3.0;
        double cy2 = y3 - (dx / 3.0) * Math.tan(thetaE);

        int bellPts = numPoints - throatPts - 1;
        for (int i = 1; i <= bellPts; i++) {
            double t = (double) i / bellPts;
            double u = 1.0 - t;
            contourPoints.add(new Point2D(
                    u*u*u*x0 + 3*u*u*t*cx1 + 3*u*t*t*cx2 + t*t*t*x3,
                    u*u*u*y0 + 3*u*u*t*cy1 + 3*u*t*t*cy2 + t*t*t*y3));
        }
    }

    /**
     * Fits a natural cubic spline through {@code controlPoints} and stores the
     * piecewise polynomial coefficients in {@code splineCoeffA/B/C/D}.
     * The algorithm follows the standard tridiagonal-system formulation
     * (Burden &amp; Faires, §3.5) with not-a-knot boundary conditions
     * ({@code c[0] = c[n] = 0}).
     * Has no effect if fewer than two control points are present.
     */
    private void generateSpline() {
        if (controlPoints.size() < 2) return;
        
        int n = controlPoints.size() - 1;
        splineX = new double[n + 1];
        double[] y = new double[n + 1];
        
        for (int i = 0; i <= n; i++) {
            splineX[i] = controlPoints.get(i).x();
            y[i] = controlPoints.get(i).y();
        }
        
        double[] h = new double[n];
        for (int i = 0; i < n; i++) {
            h[i] = splineX[i + 1] - splineX[i];
        }
        
        double[] alpha = new double[n];
        for (int i = 1; i < n; i++) {
            alpha[i] = 3.0/h[i] * (y[i+1] - y[i]) - 3.0/h[i-1] * (y[i] - y[i-1]);
        }
        
        double[] l = new double[n + 1];
        double[] mu = new double[n + 1];
        double[] z = new double[n + 1];
        
        l[0] = 1; mu[0] = 0; z[0] = 0;
        
        for (int i = 1; i < n; i++) {
            l[i] = 2*(splineX[i+1] - splineX[i-1]) - h[i-1]*mu[i-1];
            mu[i] = h[i] / l[i];
            z[i] = (alpha[i] - h[i-1]*z[i-1]) / l[i];
        }
        
        l[n] = 1; z[n] = 0;
        
        splineCoeffA = new double[n];
        splineCoeffB = new double[n];
        splineCoeffC = new double[n + 1];
        splineCoeffD = new double[n];
        
        splineCoeffC[n] = 0;
        
        for (int j = n - 1; j >= 0; j--) {
            splineCoeffC[j] = z[j] - mu[j]*splineCoeffC[j+1];
            splineCoeffB[j] = (y[j+1] - y[j])/h[j] - h[j]*(splineCoeffC[j+1] + 2*splineCoeffC[j])/3;
            splineCoeffD[j] = (splineCoeffC[j+1] - splineCoeffC[j]) / (3*h[j]);
            splineCoeffA[j] = y[j];
        }
    }
    
    /**
     * Populates {@code contourPoints} by sampling the fitted cubic spline at
     * {@code numPoints} uniformly spaced axial positions spanning the range of
     * the control points.
     * If the spline coefficients have not yet been computed, calls
     * {@link #generateSpline()} first.
     * Has no effect if fewer than two control points are present.
     *
     * @param numPoints Number of sample points to generate
     */
    private void generateSplineContour(int numPoints) {
        if (controlPoints.size() < 2) return;
        if (splineCoeffA == null) generateSpline();
        
        double xMin = controlPoints.getFirst().x();
        double xMax = controlPoints.getLast().x();
        
        for (int i = 0; i < numPoints; i++) {
            double x = xMin + i * (xMax - xMin) / (numPoints - 1);
            double y = evaluateSpline(x);
            contourPoints.add(new Point2D(x, y));
        }
    }
    
    /**
     * Evaluates the fitted cubic spline at axial position {@code x}.
     * Performs a linear search for the enclosing interval and applies the
     * Horner-form polynomial {@code a + b·dx + c·dx² + d·dx³}.
     * Returns {@code 0} if the spline has not been initialized.
     *
     * @param x Axial position in metres (clamped to the spline domain by the
     *          interval search — out-of-range values use the last valid interval)
     * @return Interpolated wall radius in metres
     */
    private double evaluateSpline(double x) {
        if (splineX == null || splineX.length < 2) return 0;
        
        int i = 0;
        for (int j = 0; j < splineX.length - 1; j++) {
            if (x >= splineX[j] && x <= splineX[j + 1]) {
                i = j;
                break;
            }
        }
        
        double dx = x - splineX[i];
        return splineCoeffA[i] + splineCoeffB[i]*dx + splineCoeffC[i]*dx*dx + splineCoeffD[i]*dx*dx*dx;
    }
    
    /**
     * Returns the wall radius at the given axial position by linear interpolation
     * of the discrete contour points (or spline evaluation for spline-based types).
     * If no contour points have been generated yet, {@link #generate(int)} is
     * called with 100 points automatically.
     *
     * @param x Axial position in metres
     * @return Wall radius in metres; returns the exit radius if {@code x} is beyond
     *         the last contour point
     */
    public double getRadiusAt(double x) {
        if (contourPoints.isEmpty()) {
            generate(100);
        }
        
        if (type == ContourType.CUSTOM_SPLINE || type == ContourType.MOC_GENERATED) {
            return evaluateSpline(x);
        }
        
        for (int i = 1; i < contourPoints.size(); i++) {
            Point2D prev = contourPoints.get(i - 1);
            Point2D curr = contourPoints.get(i);
            if (x >= prev.x() && x <= curr.x()) {
                double t = (x - prev.x()) / (curr.x() - prev.x());
                return prev.y() + t * (curr.y() - prev.y());
            }
        }
        return parameters.exitRadius();
    }
    
    /**
     * Returns the wall slope (dr/dx) at axial position {@code x} using
     * a central-difference approximation with step {@code h = 1 µm}.
     *
     * @param x Axial position in metres
     * @return Wall slope (dimensionless, dr/dx)
     */
    public double getSlopeAt(double x) {
        double h = 1e-6;
        return (getRadiusAt(x + h) - getRadiusAt(x - h)) / (2 * h);
    }
    
    /**
     * Returns the local wall half-angle (in radians) at axial position {@code x}.
     * Computed as {@code atan(dr/dx)} via {@link #getSlopeAt(double)}.
     *
     * @param x Axial position in metres
     * @return Wall half-angle in radians
     */
    public double getAngleAt(double x) {
        return Math.atan(getSlopeAt(x));
    }
    
    /**
     * Returns an unmodifiable view of the discrete contour point list.
     * Each {@link Point2D} holds the axial ({@code x}) and radial ({@code y})
     * coordinates in metres.
     *
     * @return Unmodifiable list of contour points; empty if {@link #generate(int)}
     *         has not been called
     */
    public List<Point2D> getContourPoints() {
        return Collections.unmodifiableList(contourPoints);
    }

    /**
     * Returns the contour type used to construct this instance.
     *
     * @return The {@link ContourType} of this contour
     */
    public ContourType getType() {
        return type;
    }
    
    /**
     * Returns the axial length of the contour from its first to its last
     * discrete point.
     *
     * @return Contour axial length in metres, or {@code 0} if no points have
     *         been generated
     */
    public double getLength() {
        if (contourPoints.isEmpty()) return 0;
        return contourPoints.getLast().x() - contourPoints.getFirst().x();
    }
    
    /**
     * Calculates the inner wetted surface area of the axisymmetric nozzle wall
     * using the trapezoidal rule over the discrete contour segments:
     * {@code dA = 2π · r_avg · ds}.
     *
     * @return Wetted surface area in m²; returns {@code 0} if fewer than two
     *         contour points are available
     */
    public double calculateSurfaceArea() {
        if (contourPoints.size() < 2) return 0;
        
        double area = 0;
        for (int i = 1; i < contourPoints.size(); i++) {
            Point2D p1 = contourPoints.get(i - 1);
            Point2D p2 = contourPoints.get(i);
            double ds = p1.distanceTo(p2);
            double rAvg = (p1.y() + p2.y()) / 2;
            area += 2 * Math.PI * rAvg * ds;
        }
        return area;
    }
}
