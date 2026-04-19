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
 * Designs a <em>Minimum-Length Nozzle</em> (MLN) using the Method of Characteristics.
 *
 * <p>The MLN is the theoretically shortest supersonic nozzle that produces a
 * specified uniform exit Mach number with no flow non-uniformity.  All expansion
 * is concentrated at the throat corner (a Prandtl-Meyer expansion fan), and the
 * wall contour downstream is derived entirely from the characteristic network
 * — no prescribed Bézier wall is used.
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li><b>Maximum wall angle</b> — {@code θ_max = ν(M_e) / 2}, where
 *       {@code ν(M_e)} is the Prandtl-Meyer angle at the design exit Mach.
 *       This is the exact 2-D result (Foelsch 1949); the axisymmetric correction
 *       is applied implicitly by the iterative source-term in the interior
 *       point solver.</li>
 *   <li><b>Initial data line</b> — {@code n + 1} points seeded on the curved
 *       sonic surface (Hall 1962) at axial positions {@code x_s(r)}.  The
 *       simple-wave condition {@code Q⁺ = θ − ν = 0} sets {@code ν = θ} at
 *       each point, so Mach number increases linearly from 1.0 at the
 *       centerline to {@code M(ν = θ_max)} at the wall.</li>
 *   <li><b>Propagation</b> — interior points are computed by the standard
 *       bilinear characteristic intersection with the same axisymmetric
 *       source-term correction used in {@link CharacteristicNet}.  The wall
 *       point at each row is found by intersecting the C⁺ from the outermost
 *       interior point with the C⁻ from the previous wall point (inviscid
 *       slip condition).</li>
 *   <li><b>Axis reflection</b> — when an interior point descends within
 *       {@code 0.1 % r_t} of the centerline, a reflected point is created
 *       with {@code θ = 0} and {@code ν} equal to the incoming C⁻ invariant
 *       (symmetry condition).</li>
 *   <li><b>Termination</b> — propagation halts when the wall flow angle
 *       {@code θ_wall ≤ 0} (uniform parallel exit flow), or when the row
 *       collapses to a single point.</li>
 * </ol>
 *
 * <p>The resulting contour is shorter than an equivalent Rao bell nozzle by
 * approximately 20–35 % for area ratios of 5–25, at the cost of slightly
 * higher throat-region heat flux due to the sharper wall curve.
 */
public class MinimumLengthNozzle {

    private static final Logger LOG = LoggerFactory.getLogger(MinimumLengthNozzle.class);

    private static final double DEFAULT_TOLERANCE  = 1e-8;
    private static final int    DEFAULT_MAX_ITER    = 100;
    private static final double AXIS_PROXIMITY      = 1e-3;   // fraction of rt

    private final NozzleDesignParameters parameters;
    private final double convergenceTolerance;
    private final int    maxIterations;

    private final List<List<CharacteristicPoint>> netPoints   = new ArrayList<>();
    private final List<CharacteristicPoint>       wallPoints  = new ArrayList<>();
    private double maximumWallAngle;   // θ_max in radians

    /**
     * Creates an MLN solver with default convergence settings.
     *
     * @param parameters nozzle design parameters; {@code exitMach} drives the
     *                   maximum wall angle
     */
    public MinimumLengthNozzle(NozzleDesignParameters parameters) {
        this(parameters, DEFAULT_TOLERANCE, DEFAULT_MAX_ITER);
    }

    /**
     * Creates an MLN solver with custom convergence settings.
     *
     * @param parameters          nozzle design parameters
     * @param convergenceTolerance axisymmetric-correction convergence tolerance
     * @param maxIterations       maximum axisymmetric-correction iterations
     */
    public MinimumLengthNozzle(NozzleDesignParameters parameters,
                               double convergenceTolerance,
                               int maxIterations) {
        this.parameters           = parameters;
        this.convergenceTolerance = convergenceTolerance;
        this.maxIterations        = maxIterations;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Generates the minimum-length nozzle contour.
     *
     * @return this instance for method chaining
     */
    public MinimumLengthNozzle generate() {
        GasProperties gas = parameters.gasProperties();
        int    n   = parameters.numberOfCharLines();
        double rt  = parameters.throatRadius();

        double nuExit = gas.prandtlMeyerFunction(parameters.exitMach());
        maximumWallAngle = nuExit / 2.0;

        LOG.debug("MLN: M_exit={}, thetaMax_deg={}, n={}",
                parameters.exitMach(),
                Math.toDegrees(maximumWallAngle), n);

        List<CharacteristicPoint> initial = buildInitialLine(n, rt, gas);
        netPoints.add(initial);
        wallPoints.add(initial.getLast());

        List<CharacteristicPoint> current = initial;
        int safety = n * 20;

        for (int step = 0; step < safety; step++) {
            if (current.size() < 2) break;

            // Wall: C+ from last interior of current row + wall streamline from prevWall
            CharacteristicPoint outermostInterior = current.get(current.size() - 2);
            CharacteristicPoint prevWall          = wallPoints.getLast();
            CharacteristicPoint newWall = computeWallPoint(outermostInterior, prevWall, gas);
            if (newWall == null) break;

            newWall = newWall.withType(CharacteristicPoint.PointType.WALL);
            wallPoints.add(newWall);

            // Interior row: pairs (0,1) … (size-3, size-2), wall appended last
            List<CharacteristicPoint> nextRow = buildNextRow(current, gas, rt);
            nextRow.add(newWall);
            netPoints.add(nextRow);
            current = nextRow;

            LOG.trace("MLN step {}: wall theta_deg={}, y_m={}",
                    step, Math.toDegrees(newWall.theta()), newWall.y());

            if (newWall.theta() <= 0.0) break;
        }

        LOG.debug("MLN complete: {} wall points, nozzle length_m={}",
                wallPoints.size(), nozzleLength());
        return this;
    }

    /**
     * Returns the computed nozzle wall points in axial order.
     *
     * @return unmodifiable list of wall {@link CharacteristicPoint}s
     */
    public List<CharacteristicPoint> getWallPoints() {
        return Collections.unmodifiableList(wallPoints);
    }

    /**
     * Returns all rows of the characteristic network.
     *
     * @return unmodifiable nested list (rows × points per row)
     */
    public List<List<CharacteristicPoint>> getNetPoints() {
        return Collections.unmodifiableList(netPoints);
    }

    /**
     * Returns the maximum wall angle at the throat (θ_max = ν(M_exit)/2).
     *
     * @return θ_max in radians
     */
    public double getMaximumWallAngle() { return maximumWallAngle; }

    /**
     * Returns the axial nozzle length from throat to exit.
     *
     * @return nozzle length in metres; 0 if not yet generated
     */
    public double nozzleLength() {
        if (wallPoints.size() < 2) return 0.0;
        return wallPoints.getLast().x() - wallPoints.getFirst().x();
    }

    /**
     * Returns the exit area ratio achieved by the MLN.
     *
     * @return A_exit / A_throat
     */
    public double exitAreaRatio() {
        if (wallPoints.isEmpty()) return parameters.exitAreaRatio();
        double rt = parameters.throatRadius();
        double re = wallPoints.getLast().y();
        return (re * re) / (rt * rt);
    }

    /**
     * Returns the length ratio of this MLN compared to the equivalent 15° half-angle
     * reference cone nozzle ({@code L_ref = (r_e − r_t) / tan(15°)}).
     *
     * @return L_MLN / L_ref; typically 0.55–0.70 for area ratios of 5–25
     */
    public double lengthRatioVsCone() {
        if (wallPoints.size() < 2) return 0.0;
        double rt = parameters.throatRadius();
        double re = wallPoints.getLast().y();
        double lRef = (re - rt) / Math.tan(Math.toRadians(15.0));
        return nozzleLength() / lRef;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the initial data line on a vertical plane at {@code x = x_s(r_t)}.
     * All initial points share the same axial coordinate so that the first wall
     * computation step is guaranteed to march forward in x.
     * The simple-wave condition ν = θ (Q+ = 0) is imposed at every point.
     */
    private List<CharacteristicPoint> buildInitialLine(int n, double rt, GasProperties gas) {
        List<CharacteristicPoint> line = new ArrayList<>(n + 1);
        double x0 = sonicLineX(rt, gas);   // vertical initial line — all points at x0

        for (int i = 0; i <= n; i++) {
            double frac  = (double) i / n;
            double theta = maximumWallAngle * frac;
            double nu    = theta;                          // simple wave: Q+ = θ − ν = 0
            double mach  = (nu > 0) ? gas.machFromPrandtlMeyer(nu) : 1.001;
            double mu    = gas.machAngle(mach);
            double y     = rt * (0.01 + 0.99 * frac);    // small offset avoids y = 0 singularity

            CharacteristicPoint.PointType type =
                    (i == 0)  ? CharacteristicPoint.PointType.CENTERLINE :
                    (i == n)  ? CharacteristicPoint.PointType.WALL :
                                CharacteristicPoint.PointType.INITIAL;

            line.add(makePoint(x0, y, mach, theta, nu, mu, type));
        }
        return line;
    }

    /**
     * Computes interior characteristic points from adjacent pairs of {@code current},
     * excluding the final pair that involves the wall element.
     *
     * <p>The wall element is always {@code current.getLast()}.  Pairs run from
     * {@code (0,1)} to {@code (size-3, size-2)}, producing {@code size-2} interior
     * points.  The wall point for the current step is found separately by
     * {@link #computeWallPoint}.
     */
    private List<CharacteristicPoint> buildNextRow(List<CharacteristicPoint> current,
                                                   GasProperties gas,
                                                   double rt) {
        List<CharacteristicPoint> row = new ArrayList<>();
        double axisThreshold = AXIS_PROXIMITY * rt;

        // Stop at size-2: exclude the pair (size-2, size-1=wall)
        for (int i = 0; i < current.size() - 2; i++) {
            CharacteristicPoint left  = current.get(i);
            CharacteristicPoint right = current.get(i + 1);

            if (left.y() < axisThreshold) {
                left = reflectFromAxis(left);
            }

            CharacteristicPoint pt = computeInteriorPoint(left, right, gas);
            if (pt == null || pt.y() < 0 || Double.isNaN(pt.x())) break;
            row.add(pt);
        }
        return row;
    }

    /**
     * Computes the new wall point using the wall-streamline boundary condition.
     *
     * <p>The C⁺ from {@code interior} provides Q⁺; the C⁻ from {@code prevWall}
     * provides Q⁻.  The geometric position is found by intersecting the C⁺ from
     * {@code interior} with the wall streamline through {@code prevWall}, whose
     * slope is {@code tan((θ_prev + θ_new)/2)}.  This slope is always positive
     * for a diverging nozzle so the computed radius is strictly increasing.
     */
    private CharacteristicPoint computeWallPoint(CharacteristicPoint interior,
                                                 CharacteristicPoint prevWall,
                                                 GasProperties gas) {
        double Qplus  = interior.theta() - interior.nu();
        double Qminus = prevWall.theta() + prevWall.nu();

        double theta = (Qminus + Qplus) / 2.0;
        double nu    = (Qminus - Qplus) / 2.0;
        if (nu <= 0) return null;

        double mach = gas.machFromPrandtlMeyer(nu);
        double mu   = gas.machAngle(mach);

        double slopePlus = Math.tan((interior.theta() + theta) / 2.0
                                    + (interior.mu()   + mu)   / 2.0);
        // Wall streamline slope — always positive → y_new > prevWall.y guaranteed
        double wallSlope = Math.tan((prevWall.theta() + theta) / 2.0);

        double denom = slopePlus - wallSlope;
        if (Math.abs(denom) < 1e-14) return null;

        double x = (prevWall.y() - interior.y()
                    + slopePlus * interior.x()
                    - wallSlope * prevWall.x()) / denom;
        double y = interior.y() + slopePlus * (x - interior.x());

        if (parameters.axisymmetric()) {
            for (int iter = 0; iter < maxIterations; iter++) {
                double yAvg = (interior.y() + prevWall.y() + y) / 3.0;
                if (yAvg < 1e-10) break;

                double ds_plus  = Math.hypot(x - interior.x(), y - interior.y());
                double ds_minus = Math.hypot(x - prevWall.x(), y - prevWall.y());
                double cotMu    = Math.sqrt(Math.max(0.0, mach * mach - 1.0));
                if (cotMu < convergenceTolerance) break;

                double corr     = Math.sin((interior.theta() + prevWall.theta() + theta) / 3.0)
                                  / (yAvg * cotMu);
                double newQp    = Qplus  - ds_plus  * corr;
                double newQm    = Qminus + ds_minus * corr;
                double newTheta = (newQm + newQp) / 2.0;
                double newNu    = (newQm - newQp) / 2.0;
                if (newNu <= 0) break;

                if (Math.abs(newTheta - theta) < convergenceTolerance
                    && Math.abs(newNu    - nu)  < convergenceTolerance) break;

                theta = newTheta;
                nu    = newNu;
                mach  = gas.machFromPrandtlMeyer(nu);
                mu    = gas.machAngle(mach);

                slopePlus = Math.tan((interior.theta() + theta) / 2.0
                                      + (interior.mu()  + mu)   / 2.0);
                wallSlope = Math.tan((prevWall.theta() + theta) / 2.0);
                denom     = slopePlus - wallSlope;
                if (Math.abs(denom) < 1e-14) break;

                x = (prevWall.y() - interior.y()
                     + slopePlus * interior.x()
                     - wallSlope * prevWall.x()) / denom;
                y = interior.y() + slopePlus * (x - interior.x());
            }
        }

        if (Double.isNaN(x) || Double.isNaN(y) || y < 0) return null;
        return makePoint(x, y, mach, theta, nu, mu, CharacteristicPoint.PointType.WALL);
    }

    /**
     * Creates a centerline reflection of {@code incoming} by setting {@code θ = 0}
     * and preserving the Prandtl-Meyer angle (symmetry condition: Q⁻ = ν at axis).
     */
    private CharacteristicPoint reflectFromAxis(CharacteristicPoint incoming) {
        GasProperties gas  = parameters.gasProperties();
        double nuAxis      = incoming.theta() + incoming.nu(); // Q⁻ = θ + ν (preserved along C⁻)
        double thetaAxis   = 0.0;
        double mach        = gas.machFromPrandtlMeyer(nuAxis);
        double mu          = gas.machAngle(mach);
        return makePoint(incoming.x(), 0.0, mach, thetaAxis, nuAxis, mu,
                         CharacteristicPoint.PointType.CENTERLINE);
    }

    /**
     * Standard MOC interior-point computation with optional axisymmetric correction.
     * Mirrors {@link CharacteristicNet#computeInteriorPoint} using the same
     * algorithm so results are consistent between the two solvers.
     *
     * @param left  point nearer the centerline (C⁺ origin)
     * @param right point nearer the wall (C⁻ origin)
     * @return intersection point, or {@code null} on non-physical result
     */
    CharacteristicPoint computeInteriorPoint(CharacteristicPoint left,
                                             CharacteristicPoint right,
                                             GasProperties gas) {
        double Qplus  = left.theta()  - left.nu();    // constant along C⁺
        double Qminus = right.theta() + right.nu();   // constant along C⁻

        double theta = (Qminus + Qplus) / 2.0;
        double nu    = (Qminus - Qplus) / 2.0;
        if (nu <= 0) return null;

        double mach  = gas.machFromPrandtlMeyer(nu);
        double mu    = gas.machAngle(mach);

        double slopePlus  = Math.tan((left.theta()  + theta) / 2.0 + (left.mu()  + mu) / 2.0);
        double slopeMinus = Math.tan((right.theta() + theta) / 2.0 - (right.mu() + mu) / 2.0);
        double denom      = slopePlus - slopeMinus;
        if (Math.abs(denom) < 1e-14) return null;

        double x = (right.y() - left.y() + slopePlus * left.x() - slopeMinus * right.x()) / denom;
        double y = left.y() + slopePlus * (x - left.x());

        if (parameters.axisymmetric()) {
            // Iterative axisymmetric source-term correction
            for (int iter = 0; iter < maxIterations; iter++) {
                double yAvg = (left.y() + right.y() + y) / 3.0;
                if (yAvg < 1e-10) break;

                double ds_plus  = Math.hypot(x - left.x(),  y - left.y());
                double ds_minus = Math.hypot(x - right.x(), y - right.y());
                double cotMu    = Math.sqrt(mach * mach - 1.0);
                if (cotMu < convergenceTolerance) break;

                double corr    = Math.sin((left.theta() + right.theta() + theta) / 3.0)
                                 / (yAvg * cotMu);
                double newQp   = Qplus  - ds_plus  * corr;
                double newQm   = Qminus + ds_minus * corr;
                double newTheta = (newQm + newQp) / 2.0;
                double newNu    = (newQm - newQp) / 2.0;

                if (Math.abs(newTheta - theta) < convergenceTolerance
                    && Math.abs(newNu - nu) < convergenceTolerance) break;

                theta = newTheta;
                nu    = newNu;
                mach  = gas.machFromPrandtlMeyer(nu);
                mu    = gas.machAngle(mach);

                slopePlus  = Math.tan((left.theta()  + theta) / 2.0 + (left.mu()  + mu) / 2.0);
                slopeMinus = Math.tan((right.theta() + theta) / 2.0 - (right.mu() + mu) / 2.0);
                denom      = slopePlus - slopeMinus;
                if (Math.abs(denom) < 1e-14) break;
                x = (right.y() - left.y() + slopePlus * left.x() - slopeMinus * right.x()) / denom;
                y = left.y() + slopePlus * (x - left.x());
            }
        }

        if (Double.isNaN(x) || Double.isNaN(y) || y < 0) return null;
        return makePoint(x, y, mach, theta, nu, mu, CharacteristicPoint.PointType.INTERIOR);
    }

    /** Axial position of the curved sonic line using the Hall (1962) formula. */
    private double sonicLineX(double r, GasProperties gas) {
        double gamma = gas.gamma();
        double rt    = parameters.throatRadius();
        double rcd   = parameters.throatCurvatureRatio()  * rt;
        double rcu   = parameters.upstreamCurvatureRatio() * rt;
        double coeff = parameters.axisymmetric()
                       ? (gamma + 1.0) / 12.0
                       : (gamma + 1.0) / 6.0;
        return coeff * r * r * (1.0 / rcd + 1.0 / (3.0 * rcu));
    }

    /** Creates a fully populated {@link CharacteristicPoint} from isentropic relations. */
    private CharacteristicPoint makePoint(double x, double y, double mach,
                                          double theta, double nu, double mu,
                                          CharacteristicPoint.PointType type) {
        double T   = parameters.chamberTemperature()
                     * parameters.gasProperties().isentropicTemperatureRatio(mach);
        double P   = parameters.chamberPressure()
                     * parameters.gasProperties().isentropicPressureRatio(mach);
        double rho = P / (parameters.gasProperties().gasConstant() * T);
        double V   = mach * parameters.gasProperties().speedOfSound(T);
        return CharacteristicPoint.create(x, y, mach, theta, nu, mu, P, T, rho, V, type);
    }
}
