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
 * Dual-bell altitude-compensating nozzle.
 *
 * <h2>Concept</h2>
 * <p>A dual-bell nozzle consists of two Rao-style bell sections joined at a
 * concave re-inflection point called the <em>kink</em>:
 * <ul>
 *   <li><strong>Base bell</strong> — designed for low-altitude operation,
 *       extending from the throat to the kink at {@code transitionAreaRatio}.</li>
 *   <li><strong>Extension</strong> — designed for high-altitude (vacuum)
 *       operation, continuing from the kink to the full exit area ratio.</li>
 * </ul>
 *
 * <h2>Operating modes</h2>
 * <dl>
 *   <dt>Sea-level mode</dt>
 *   <dd>At high ambient pressure the flow separates at the kink and the nozzle
 *       behaves as the shorter base bell.  The concave wall curvature at the
 *       kink locks the separation point, preventing the random side-loads that
 *       plague conventional over-expanded bells.</dd>
 *   <dt>High-altitude mode</dt>
 *   <dd>When ambient pressure drops below the transition pressure the
 *       separated region collapses and the flow fills the full extension,
 *       delivering a larger effective area ratio and higher Isp.</dd>
 * </dl>
 *
 * <h2>Transition pressure</h2>
 * <p>The ambient pressure at which the mode switches is estimated using the
 * Summerfield criterion:
 * <pre>
 *   p_transition ≈ 0.35 × p_kink
 * </pre>
 * where {@code p_kink = Pc × (isentropic pressure ratio at M_kink)}.
 *
 * <h2>Contour generation</h2>
 * <p>The contour is built from three segments, all anchored at x = 0 (throat):
 * <ol>
 *   <li>Circular throat arc (radius = 0.382 × r_throat), same as
 *       {@link RaoNozzle}.</li>
 *   <li>Base-bell cubic Bézier from the arc endpoint to the kink at
 *       {@code (baseLength, transitionRadius)}.</li>
 *   <li>Extension cubic Bézier from the kink to the exit at
 *       {@code (totalLength, exitRadius)}.  The entry slope of the extension
 *       is {@code -tan(kinkAngle)}, creating the inward-turning re-inflection
 *       that locks the separation point.</li>
 * </ol>
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 *   NozzleDesignParameters params = NozzleDesignParameters.builder()
 *           .throatRadius(0.05).exitMach(5.0).chamberPressure(7e6)
 *           .chamberTemperature(3500).ambientPressure(101325)
 *           .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
 *           .numberOfCharLines(25).wallAngleInitialDegrees(30)
 *           .lengthFraction(0.8).axisymmetric(true).build();
 *
 *   DualBellNozzle nozzle = new DualBellNozzle(params, 4.0).generate();
 *
 *   List<Point2D> contour      = nozzle.getContourPoints();
 *   double        seaLevelIsp  = nozzle.getSeaLevelIsp();     // s
 *   double        highAltIsp   = nozzle.getHighAltitudeIsp(); // s
 *   double        switchAt     = nozzle.getTransitionPressure(); // Pa
 * }</pre>
 */
public class DualBellNozzle {

    // ------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------

    private final NozzleDesignParameters parameters;
    private final double transitionAreaRatio;
    private final double baseLengthFraction;
    private final double extensionLengthFraction;
    private final double kinkAngle;        // rad — inward wall angle at extension entry
    private final int    numContourPoints;

    // ------------------------------------------------------------------
    // Derived geometry (set by generate())
    // ------------------------------------------------------------------

    private double inflectionAngle;      // rad — throat arc exit / base bell start
    private double baseExitAngle;        // rad — base bell exit at kink (θ_E1)
    private double extensionExitAngle;   // rad — extension exit angle  (θ_E2)
    private double baseLength;           // m
    private double totalLength;          // m
    private double transitionRadius;     // m — wall radius at kink
    private double transitionMach;       // — Mach number at kink
    private int    kinkIndex;            // index in contourPoints of the kink point

    private final List<Point2D> contourPoints = new ArrayList<>();

    // ------------------------------------------------------------------
    // Performance (set by generate())
    // ------------------------------------------------------------------

    private double seaLevelCf;
    private double highAltitudeCf;
    private double transitionPressure;   // Pa

    // ------------------------------------------------------------------
    // Result record
    // ------------------------------------------------------------------

    /**
     * Thrust-performance summary for both operating modes.
     *
     * @param seaLevelCf          Thrust coefficient in sea-level mode (base bell)
     * @param highAltitudeCf      Thrust coefficient in high-altitude mode (full nozzle)
     * @param seaLevelIsp         Specific impulse in sea-level mode (s)
     * @param highAltitudeIsp     Specific impulse in high-altitude mode (s)
     * @param transitionPressure  Ambient pressure at which the mode switches (Pa)
     */
    public record PerformanceSummary(
            double seaLevelCf,
            double highAltitudeCf,
            double seaLevelIsp,
            double highAltitudeIsp,
            double transitionPressure
    ) {
        /** Isp gain from sea-level to high-altitude mode (s). */
        public double ispGain() { return highAltitudeIsp - seaLevelIsp; }
    }

    // ------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------

    /**
     * Creates a dual-bell nozzle with the transition area ratio set to the
     * geometric mean of 1 and the full exit area ratio (a common starting
     * point that distributes momentum thrust roughly equally between the two
     * sections), the length fraction from the design parameters, and a
     * default kink angle of 3°.
     *
     * @param parameters Design parameters (defines exit Mach, Pc, Tc, throat
     *                   radius, and ambient pressure)
     */
    public DualBellNozzle(NozzleDesignParameters parameters) {
        this(parameters,
             Math.sqrt(parameters.exitAreaRatio()),
             parameters.lengthFraction(),
             parameters.lengthFraction(),
             Math.toRadians(3.0),
             200);
    }

    /**
     * Creates a dual-bell nozzle with a specified transition area ratio.
     *
     * @param parameters          Design parameters
     * @param transitionAreaRatio A/A* at the kink; must be in {@code [1.5, exitAreaRatio)}
     */
    public DualBellNozzle(NozzleDesignParameters parameters, double transitionAreaRatio) {
        this(parameters,
             transitionAreaRatio,
             parameters.lengthFraction(),
             parameters.lengthFraction(),
             Math.toRadians(3.0),
             200);
    }

    /**
     * Full constructor.
     *
     * @param parameters              Design parameters
     * @param transitionAreaRatio     A/A* at the kink; must be in
     *                                {@code [1.5, exitAreaRatio)}
     * @param baseLengthFraction      Rao length fraction for the base bell;
     *                                must be in {@code (0, 1]}
     * @param extensionLengthFraction Rao length fraction for the extension;
     *                                must be in {@code (0, 1]}
     * @param kinkAngle               Inward wall angle at the kink entry (rad);
     *                                must be ≥ 0
     * @param numContourPoints        Total discrete contour points (≥ 20)
     */
    public DualBellNozzle(NozzleDesignParameters parameters,
                          double transitionAreaRatio,
                          double baseLengthFraction,
                          double extensionLengthFraction,
                          double kinkAngle,
                          int    numContourPoints) {
        double exitAR = parameters.exitAreaRatio();
        if (transitionAreaRatio < 1.5 || transitionAreaRatio >= exitAR) {
            throw new IllegalArgumentException(String.format(
                    "transitionAreaRatio %.2f must be in [1.5, %.2f)",
                    transitionAreaRatio, exitAR));
        }
        if (baseLengthFraction <= 0.0 || baseLengthFraction > 1.0) {
            throw new IllegalArgumentException(
                    "baseLengthFraction must be in (0, 1], got " + baseLengthFraction);
        }
        if (extensionLengthFraction <= 0.0 || extensionLengthFraction > 1.0) {
            throw new IllegalArgumentException(
                    "extensionLengthFraction must be in (0, 1], got " + extensionLengthFraction);
        }
        if (kinkAngle < 0.0) {
            throw new IllegalArgumentException(
                    "kinkAngle must be >= 0, got " + kinkAngle);
        }
        this.parameters              = parameters;
        this.transitionAreaRatio     = transitionAreaRatio;
        this.baseLengthFraction      = baseLengthFraction;
        this.extensionLengthFraction = extensionLengthFraction;
        this.kinkAngle               = kinkAngle;
        this.numContourPoints        = numContourPoints;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Generates the dual-bell contour and computes both performance modes.
     *
     * @return This instance for chaining
     */
    public DualBellNozzle generate() {
        contourPoints.clear();

        GasProperties gas = parameters.gasProperties();
        double rt = parameters.throatRadius();
        double re = parameters.exitRadius();

        // --- Kink geometry ---
        transitionMach   = gas.machFromAreaRatio(transitionAreaRatio);
        transitionRadius = rt * Math.sqrt(transitionAreaRatio);

        // --- Angle sets ---
        calculateBaseAngles();
        calculateExtensionAngles();

        // --- Nozzle lengths ---
        // Each section uses its own 15° reference cone length.
        double refBase = (transitionRadius - rt) / Math.tan(Math.toRadians(15.0));
        double refExt  = (re - transitionRadius) / Math.tan(Math.toRadians(15.0));
        baseLength  = refBase * baseLengthFraction;
        totalLength = baseLength + refExt * extensionLengthFraction;

        // --- Contour segments ---
        generateThroatArc(rt);
        generateBaseBell(rt, transitionRadius, baseLength);
        kinkIndex = contourPoints.size() - 1;
        generateExtension(transitionRadius, re, baseLength, totalLength);

        // --- Performance ---
        computePerformance(gas);

        return this;
    }

    /**
     * Returns an unmodifiable view of the contour; x is the axial coordinate
     * (m) from the throat, y is the wall radius (m).
     * Calls {@link #generate()} automatically if not yet generated.
     */
    public List<Point2D> getContourPoints() {
        if (contourPoints.isEmpty()) generate();
        return Collections.unmodifiableList(contourPoints);
    }

    /** Axial length of the base-bell section (m). */
    public double getBaseLength()        { return baseLength; }

    /** Total axial length of the full dual-bell (m). */
    public double getTotalLength()       { return totalLength; }

    /** Wall radius at the kink (m). */
    public double getTransitionRadius()  { return transitionRadius; }

    /** Mach number at the kink (isentropic, from {@code transitionAreaRatio}). */
    public double getTransitionMach()    { return transitionMach; }

    /**
     * Index into {@link #getContourPoints()} of the kink point (last point of
     * the base bell, first of the extension).
     */
    public int getKinkIndex()            { return kinkIndex; }

    /** Throat-arc end / base-bell inflection angle (rad). */
    public double getInflectionAngle()   { return inflectionAngle; }

    /** Base-bell exit angle at the kink θ_E1 (rad). */
    public double getBaseExitAngle()     { return baseExitAngle; }

    /** Extension exit angle θ_E2 (rad). */
    public double getExtensionExitAngle(){ return extensionExitAngle; }

    /** Thrust coefficient in sea-level mode (flow separated at kink). */
    public double getSeaLevelCf()        { return seaLevelCf; }

    /** Thrust coefficient in high-altitude mode (full nozzle). */
    public double getHighAltitudeCf()    { return highAltitudeCf; }

    /**
     * Ambient pressure (Pa) at which the flow reattaches through the full
     * extension (Summerfield criterion: 0.35 × static pressure at the kink).
     */
    public double getTransitionPressure(){ return transitionPressure; }

    /**
     * Specific impulse in sea-level mode (s).
     * Evaluated at {@code parameters.ambientPressure()}.
     */
    public double getSeaLevelIsp() {
        return parameters.characteristicVelocity() * seaLevelCf / 9.80665;
    }

    /**
     * Specific impulse in high-altitude (vacuum) mode (s).
     * Evaluated at ambient pressure = 0.
     */
    public double getHighAltitudeIsp() {
        return parameters.characteristicVelocity() * highAltitudeCf / 9.80665;
    }

    /** Returns a consolidated performance summary for both modes. */
    public PerformanceSummary getPerformanceSummary() {
        if (contourPoints.isEmpty()) generate();
        return new PerformanceSummary(
                seaLevelCf, highAltitudeCf,
                getSeaLevelIsp(), getHighAltitudeIsp(),
                transitionPressure);
    }

    // ------------------------------------------------------------------
    // Angle computation (Rao correlations)
    // ------------------------------------------------------------------

    /**
     * Rao inflection and base-exit angles, applied to the base bell using the
     * transition Mach number and base-bell length fraction.
     */
    private void calculateBaseAngles() {
        double mach     = transitionMach;
        double lf       = baseLengthFraction;
        double ln       = Math.log(transitionAreaRatio);

        // Inflection angle (start of bell)
        if (lf >= 0.8) {
            inflectionAngle = Math.toRadians(21.0 + 3.0 * (mach - 2.0));
        } else if (lf >= 0.6) {
            inflectionAngle = Math.toRadians(25.0 + 4.0 * (mach - 2.0));
        } else {
            inflectionAngle = Math.toRadians(30.0 + 5.0 * (mach - 2.0));
        }
        inflectionAngle = Math.clamp(inflectionAngle,
                Math.toRadians(15.0), Math.toRadians(45.0));

        // Base bell exit angle at kink
        if (lf >= 0.8) {
            baseExitAngle = Math.toRadians(8.0  - 0.5 * ln);
        } else if (lf >= 0.6) {
            baseExitAngle = Math.toRadians(11.0 - 0.7 * ln);
        } else {
            baseExitAngle = Math.toRadians(14.0 - 0.9 * ln);
        }
        baseExitAngle = Math.max(baseExitAngle, Math.toRadians(1.0));
    }

    /**
     * Rao exit angle for the extension, applied using the full exit area ratio
     * and the extension length fraction.
     */
    private void calculateExtensionAngles() {
        double lf = extensionLengthFraction;
        double ln = Math.log(parameters.exitAreaRatio());

        if (lf >= 0.8) {
            extensionExitAngle = Math.toRadians(8.0  - 0.5 * ln);
        } else if (lf >= 0.6) {
            extensionExitAngle = Math.toRadians(11.0 - 0.7 * ln);
        } else {
            extensionExitAngle = Math.toRadians(14.0 - 0.9 * ln);
        }
        extensionExitAngle = Math.max(extensionExitAngle, Math.toRadians(1.0));
    }

    // ------------------------------------------------------------------
    // Contour generation
    // ------------------------------------------------------------------

    /** Circular downstream throat arc, identical to the RaoNozzle approach. */
    private void generateThroatArc(double rt) {
        double rcd = 0.382 * rt;
        int n = Math.max(5, numContourPoints / 10);
        for (int i = 0; i <= n; i++) {
            double angle = inflectionAngle * i / n;
            contourPoints.add(new Point2D(
                    rcd * Math.sin(angle),
                    rt  + rcd * (1.0 - Math.cos(angle))));
        }
    }

    /** Cubic Bézier from the throat arc endpoint to the kink. */
    private void generateBaseBell(double rt, double rKink, double lBase) {
        double rcd = 0.382 * rt;
        double x0  = rcd * Math.sin(inflectionAngle);
        double y0  = rt  + rcd * (1.0 - Math.cos(inflectionAngle));
        int n = numContourPoints * 7 / 10;
        appendBezier(x0, y0, Math.tan(inflectionAngle),
                     lBase, rKink, Math.tan(baseExitAngle), n);
    }

    /**
     * Cubic Bézier from the kink to the full exit.  The entry slope is
     * {@code -tan(kinkAngle)}, producing the inward-turning re-inflection
     * that locks the sea-level separation point at the kink.
     */
    private void generateExtension(double rKink, double re,
                                    double lBase, double lTotal) {
        int n = Math.max(2, numContourPoints - contourPoints.size());
        appendBezier(lBase, rKink, -Math.tan(kinkAngle),
                     lTotal, re, Math.tan(extensionExitAngle), n);
    }

    /**
     * Appends {@code n} cubic Bézier sample points (t = 1/n … 1) to
     * {@code contourPoints}.  The start point (t = 0) is already present as
     * the last entry in the list.
     *
     * @param x0 Start x,  y0 Start y,  s0 Start slope (dy/dx)
     * @param x1 End x,    y1 End y,    s1 End slope
     * @param n  Number of new points to append
     */
    private void appendBezier(double x0, double y0, double s0,
                               double x1, double y1, double s1, int n) {
        double dx  = x1 - x0;
        double cx1 = x0 + dx / 3.0;
        double cy1 = y0 + (dx / 3.0) * s0;
        double cx2 = x1 - dx / 3.0;
        double cy2 = y1 - (dx / 3.0) * s1;
        for (int i = 1; i <= n; i++) {
            double t = (double) i / n;
            double u = 1.0 - t;
            contourPoints.add(new Point2D(
                    u*u*u*x0 + 3*u*u*t*cx1 + 3*u*t*t*cx2 + t*t*t*x1,
                    u*u*u*y0 + 3*u*u*t*cy1 + 3*u*t*t*cy2 + t*t*t*y1));
        }
    }

    // ------------------------------------------------------------------
    // Performance
    // ------------------------------------------------------------------

    private void computePerformance(GasProperties gas) {
        double gamma = gas.gamma();
        double gp1   = gamma + 1.0;
        double gm1   = gamma - 1.0;
        double pc    = parameters.chamberPressure();
        double pa_sl = parameters.ambientPressure();

        // Pre-computed momentum factor (depends only on γ)
        double term1 = 2.0 * gamma * gamma / gm1 * Math.pow(2.0 / gp1, gp1 / gm1);

        // Isentropic wall pressure at kink
        double pKink = pc * Math.pow(
                1.0 + gm1 / 2.0 * transitionMach * transitionMach, -gamma / gm1);

        // --- Sea-level mode (base bell, effective exit at kink) ---
        double cfMomBase  = Math.sqrt(term1 * (1.0 - Math.pow(pKink / pc, gm1 / gamma)));
        double lambdaBase = (1.0 + Math.cos(baseExitAngle)) / 2.0;
        seaLevelCf = lambdaBase * cfMomBase
                     + (pKink - pa_sl) / pc * transitionAreaRatio;

        // --- High-altitude mode (full nozzle, vacuum ambient p_a = 0) ---
        double exitMach = parameters.exitMach();
        double pExit    = pc * Math.pow(
                1.0 + gm1 / 2.0 * exitMach * exitMach, -gamma / gm1);
        double cfMomFull  = Math.sqrt(term1 * (1.0 - Math.pow(pExit / pc, gm1 / gamma)));
        double lambdaFull = (1.0 + Math.cos(extensionExitAngle)) / 2.0;
        highAltitudeCf = lambdaFull * cfMomFull
                         + pExit / pc * parameters.exitAreaRatio();   // pa = 0

        // --- Transition pressure (Summerfield criterion) ---
        transitionPressure = 0.35 * pKink;
    }
}
