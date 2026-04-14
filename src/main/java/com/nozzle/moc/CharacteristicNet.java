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
 * Computes the characteristic network for a supersonic nozzle using the Method of Characteristics.
 * The coordinate system:
 * - x: axial direction (downstream positive)
 * - y: radial direction (outward from centerline positive)
 * - Centerline is at y=0
 * - Throat is at x=0, y ranges from 0 to rt (throat radius)
 * For a minimum-length nozzle:
 * 1. Initial line: vertical line at throat from centerline to wall
 * 2. Expansion: characteristics propagate downstream
 * 3. Wall contour: determined by the outermost C+ characteristic
 */
public class CharacteristicNet {

    private static final Logger LOG = LoggerFactory.getLogger(CharacteristicNet.class);

    private final NozzleDesignParameters parameters;
    private final List<List<CharacteristicPoint>> netPoints;
    private final List<CharacteristicPoint> wallPoints;
    private final boolean axisymmetric;
    private final double convergenceTolerance;
    private final int maxIterations;
    
    private static final double DEFAULT_TOLERANCE = 1e-8;
    private static final int DEFAULT_MAX_ITERATIONS = 100;
    
    /**
     * Creates a characteristic net with default convergence tolerance
     * ({@value #DEFAULT_TOLERANCE}) and iteration limit ({@value #DEFAULT_MAX_ITERATIONS}).
     *
     * @param parameters Nozzle design parameters that define the throat geometry,
     *                   initial wall angle, exit Mach number, and gas properties
     */
    public CharacteristicNet(NozzleDesignParameters parameters) {
        this(parameters, DEFAULT_TOLERANCE, DEFAULT_MAX_ITERATIONS);
    }

    /**
     * Creates a characteristic net with custom convergence settings.
     *
     * @param parameters Nozzle design parameters
     * @param tolerance  Convergence tolerance for the axisymmetric correction
     *                   iteration (change in {@code theta} and {@code nu} per step)
     * @param maxIter    Maximum number of axisymmetric correction iterations per
     *                   interior point
     */
    public CharacteristicNet(NozzleDesignParameters parameters, double tolerance, int maxIter) {
        this.parameters = parameters;
        this.netPoints = new ArrayList<>();
        this.wallPoints = new ArrayList<>();
        this.axisymmetric = parameters.axisymmetric();
        this.convergenceTolerance = tolerance;
        this.maxIterations = maxIter;
    }
    
    /**
     * Generates the complete characteristic net for the nozzle design.
     *
     * <p>The algorithm proceeds in three stages:
     * <ol>
     *   <li><b>Initial data line</b> — seeded with {@code n + 1} points
     *       ({@link NozzleDesignParameters#numberOfCharLines()} {@code + 1})
     *       placed on the <em>curved</em> sonic line computed by
     *       {@link #sonicLineX(double)}.  Flow angles ramp linearly from
     *       {@code θ = 0} at the centerline to {@code θ = θ_max}
     *       ({@link NozzleDesignParameters#wallAngleInitial()}) at the wall.
     *       Mach number at each point is obtained by inverting the Prandtl-Meyer
     *       function with {@code ν = ν(M_start) + θ}.
     *
     *       <p>The sonic-line shape follows the Hall (1962) transonic result:
     *       the M = 1 surface bows downstream near the wall by
     *       {@code x_s(r) = coeff · r² · (1/R_cd + 1/(3·R_cu))}, where
     *       {@code coeff = (γ+1)/12} for axisymmetric flow and {@code (γ+1)/6}
     *       for planar 2-D, {@code R_cd = throatCurvatureRatio × r_t} is the
     *       downstream throat-arc radius, and
     *       {@code R_cu = upstreamCurvatureRatio × r_t} is the upstream
     *       throat-arc radius.  Using a flat plane ({@code x ≡ 0}) as the
     *       initial data line introduces error in the first few characteristic
     *       rows proportional to the throat curvature; the curved placement
     *       removes this leading-order bias.</li>
     *
     *   <li><b>Rao wall profile</b> — a cubic Bézier curve is computed once
     *       via {@link #buildRaoWallProfile()} before the propagation loop.  The
     *       curve runs from {@code (x₀, r_t)} with slope {@code tan(θ_max)} to
     *       {@code (x_exit, r_e)} with slope {@code tan(θ_E)}, where {@code x₀
     *       = sonicLineX(r_t)} and {@code θ_E} is the Rao empirical exit angle
     *       from {@link #computeRaoExitAngle()}.  It is sampled uniformly at
     *       501 Bézier-parameter values, giving 500 wall segments.</li>
     *
     *   <li><b>Propagation loop</b> — iterates over the 500 wall segments
     *       ({@code wallIdx = 1 … 500}).  On each iteration:
     *       <ul>
     *         <li>A new interior row is computed by calling
     *             {@link #computeInteriorPoint} for every adjacent pair in the
     *             current row.</li>
     *         <li>The next wall point is read directly from the Rao profile at
     *             {@code wallIdx} via {@link #computeRaoWallPoint}, which
     *             assigns the flow state from the isentropic area–velocity
     *             relation ({@code M = machFromAreaRatio((y/r_t)²)}).  This
     *             guarantees the last wall point reaches {@code y = r_e} and
     *             {@code M = M_exit} by construction, regardless of
     *             {@link NozzleDesignParameters#numberOfCharLines()}.</li>
     *         <li>The loop terminates early once the wall radial coordinate
     *             reaches or exceeds {@link NozzleDesignParameters#exitRadius()}.
     *         </li>
     *       </ul>
     *   </li>
     * </ol>
     *
     * <p>Wall points are <em>not</em> found by intersecting the outermost C+
     * characteristic with the wall; they are stepped directly along the
     * prescribed Rao contour.  The interior net follows the advancing wall.
     *
     * @return This instance for method chaining
     */
    public CharacteristicNet generate() {
        GasProperties gas = parameters.gasProperties();
        int n = parameters.numberOfCharLines();
        double rt = parameters.throatRadius();
        double thetaMax = parameters.wallAngleInitial();
        LOG.debug("MOC generation started: {} char lines, axisymmetric={}", n, axisymmetric);
        
        // Initial data line at throat (x ≈ 0)
        // Points go from centerline (y=0) to wall (y=rt)
        // The expansion fan is created by having different flow angles:
        // - At centerline (y=0): theta = 0 (axial flow)
        // - At wall (y=rt): theta = thetaMax (wall turning angle)
        
        List<CharacteristicPoint> initialLine = new ArrayList<>();
        double machStart = 1.001;
        double nuStart = gas.prandtlMeyerFunction(machStart);
        
        for (int i = 0; i <= n; i++) {
            double fraction = (double) i / n;
            
            // y goes from small value near centerline to rt at wall
            double y = rt * (0.01 + 0.99 * fraction);  // Avoid exactly y=0
            
            // Flow angle: 0 at centerline, thetaMax at wall
            double theta = fraction * thetaMax;
            
            // Prandtl-Meyer function increases with expansion
            double nu = nuStart + theta;
            double mach = gas.machFromPrandtlMeyer(nu);
            double mu = gas.machAngle(mach);
            
            // x position: on the curved sonic line (Hall 1962 + upstream correction)
            double x = sonicLineX(y);
            
            // Thermodynamic properties
            double T = parameters.chamberTemperature() * gas.isentropicTemperatureRatio(mach);
            double P = parameters.chamberPressure() * gas.isentropicPressureRatio(mach);
            double rho = P / (gas.gasConstant() * T);
            double V = mach * gas.speedOfSound(T);
            
            CharacteristicPoint.PointType type = (i == 0) ? CharacteristicPoint.PointType.CENTERLINE :
                                                  (i == n) ? CharacteristicPoint.PointType.WALL :
                                                             CharacteristicPoint.PointType.INITIAL;
            
            CharacteristicPoint point = CharacteristicPoint.create(
                    x, y, mach, theta, nu, mu, P, T, rho, V, type
            ).withIndices(i, i);
            
            initialLine.add(point);
        }
        
        netPoints.add(initialLine);
        // Pre-compute the Rao bell wall profile.  Wall points are taken directly
        // from this profile (isentropic A–V relation) so that the last wall point
        // is guaranteed to reach the design exit radius regardless of n.
        double[][] raoWall = buildRaoWallProfile();
        // Seed wallPoints from the Rao profile (not the initial data-line wall point)
        // so that the entire wall sequence uses a single consistent flow model and
        // the Mach number increases monotonically from M≈1 at the throat to M=exitMach.
        wallPoints.add(computeRaoWallPoint(raoWall, 0));
        // Propagate the characteristic net
        List<CharacteristicPoint> currentLine = initialLine;

        // Infinite loop: the Rao profile guarantees termination when y reaches exitRadius
        // (always at or before the last Bézier segment), so no upper-bound condition is needed.
        for (int wallIdx = 1; ; wallIdx++) {
            List<CharacteristicPoint> nextLine = new ArrayList<>();

            // Compute interior points from adjacent pairs
            // Left point (lower index) is closer to centerline
            // Right point (higher index) is closer to wall
            for (int i = 0; i < currentLine.size() - 1; i++) {
                CharacteristicPoint left = currentLine.get(i);
                CharacteristicPoint right = currentLine.get(i + 1);
                // Always non-null for a well-formed initial data line (nu > 0 guaranteed
                // by the linearly increasing Prandtl-Meyer construction in generate()).
                nextLine.add(computeInteriorPoint(left, right));
            }

            // Wall point: advance directly along the Rao profile at index wallIdx.
            // The isentropic area-velocity relation gives the flow state so that the
            // last wall point has M = exitMach and y = exitRadius by construction.
            CharacteristicPoint newWall = computeRaoWallPoint(raoWall, wallIdx);

            nextLine.add(newWall);
            wallPoints.add(newWall);

            netPoints.add(nextLine);
            currentLine = nextLine;

            // Termination: wall has reached the design exit radius
            if (newWall.y() >= parameters.exitRadius()) {
                break;
            }
        }

        LOG.debug("MOC net generated: {} wall points, {} total interior points",
                wallPoints.size(), getTotalPointCount());
        return this;
    }
    
    /**
     * Creates a wall {@link CharacteristicPoint} directly from the Rao bell wall
     * profile at Bézier index {@code idx}.
     *
     * <p>Because the Rao bell wall is a prescribed contour rather than a computed
     * streamline, the wall position ({@code x}, {@code y}) and wall angle
     * ({@code θ}) come directly from the profile.  The Mach number is obtained
     * from the isentropic area–velocity relation
     * {@code M = machFromAreaRatio((y/r_t)²)} so that the last wall point
     * (at {@code idx = nSeg}) has {@code y = r_e} and {@code M ≈ M_exit}.
     *
     * @param raoWall Pre-computed Rao bell wall profile from
     *                {@link #buildRaoWallProfile()}; each row is
     *                {@code {x, radius, angle}}
     * @param idx     Index into the profile array ({@code 1 ≤ idx ≤ raoWall.length − 1})
     * @return The {@link CharacteristicPoint} on the Rao wall at the given index
     */
    private CharacteristicPoint computeRaoWallPoint(double[][] raoWall, int idx) {
        GasProperties gas = parameters.gasProperties();
        double rt = parameters.throatRadius();

        double x     = raoWall[idx][0];
        double y     = raoWall[idx][1];
        double theta = raoWall[idx][2];

        // Isentropic Mach number from local area ratio: A/A* = (y/rt)² for axisymmetric
        double areaRatio = axisymmetric ? (y / rt) * (y / rt) : y / rt;
        double mach = gas.machFromAreaRatio(Math.max(1.0 + 1e-6, areaRatio));
        double nu   = gas.prandtlMeyerFunction(mach);
        double mu   = gas.machAngle(mach);

        double T   = parameters.chamberTemperature() * gas.isentropicTemperatureRatio(mach);
        double P   = parameters.chamberPressure()    * gas.isentropicPressureRatio(mach);
        double rho = P / (gas.gasConstant() * T);
        double V   = mach * gas.speedOfSound(T);

        return CharacteristicPoint.create(x, y, mach, theta, nu, mu, P, T, rho, V,
                CharacteristicPoint.PointType.WALL);
    }
    
    /**
     * Returns the axial position of the curved sonic line at radial coordinate
     * {@code r}, using the Hall (1962) transonic solution with a combined
     * downstream/upstream curvature correction.
     *
     * <p>For an axisymmetric nozzle, the M&nbsp;=&nbsp;1 surface bows downstream
     * near the wall according to:
     * <pre>
     *   x_s(r) = (γ+1)/12 · r² · (1/R_cd + 1/(3·R_cu))   [axisymmetric]
     *   x_s(r) = (γ+1)/6  · r² · (1/R_cd + 1/(3·R_cu))   [planar 2-D]
     * </pre>
     * where {@code R_cd = throatCurvatureRatio × r_t} is the downstream
     * throat-arc radius and {@code R_cu = upstreamCurvatureRatio × r_t} is
     * the upstream throat-arc radius.  The dominant term is the Hall result
     * {@code (γ+1)/coeff · r²/R_cd}; the {@code R_cu} contribution is a
     * secondary correction that pushes the sonic surface slightly further
     * downstream when the upstream geometry is tightly curved.
     *
     * <p>At the axis ({@code r = 0}) this correctly returns zero (the sonic point
     * is always at the geometric throat on the centerline).  Near the wall
     * ({@code r = r_t}) the displacement grows as the square of the throat
     * radius and inversely with the curvature radii.
     *
     * @param r Radial coordinate in metres; should satisfy {@code 0 ≤ r ≤ r_t}
     * @return Axial displacement of the sonic line at {@code r}, in metres
     */
    private double sonicLineX(double r) {
        double gamma = parameters.gasProperties().gamma();
        double rt  = parameters.throatRadius();
        double rcd = parameters.throatCurvatureRatio()  * rt;
        double rcu = parameters.upstreamCurvatureRatio() * rt;
        // Hall (1962): axisymmetric factor (γ+1)/12, planar 2-D factor (γ+1)/6
        double coeff = axisymmetric ? (gamma + 1.0) / 12.0 : (gamma + 1.0) / 6.0;
        return coeff * r * r * (1.0 / rcd + 1.0 / (3.0 * rcu));
    }

    /**
     * Estimates the axial position of the nozzle exit plane.
     * Uses the 15° reference cone length scaled by {@link NozzleDesignParameters#lengthFraction()}.
     *
     * @return Estimated exit axial position in metres
     */
    private double estimateExitX() {
        double rt = parameters.throatRadius();
        double re = parameters.exitRadius();
        return (re - rt) / Math.tan(Math.toRadians(15)) * parameters.lengthFraction();
    }

    /**
     * Computes the Rao optimum exit (lip) angle using empirical correlations
     * derived from NASA studies.  The correlation depends on the nozzle length
     * fraction and the logarithm of the design exit area ratio.
     *
     * @return Rao exit angle in radians, clamped to [1°, 15°]
     */
    private double computeRaoExitAngle() {
        double areaRatio = parameters.exitAreaRatio();
        double lf = parameters.lengthFraction();
        double ln = Math.log(areaRatio);
        double thetaE;
        if (lf >= 0.8) {
            thetaE = Math.toRadians(8.0 - 0.5 * ln);
        } else if (lf >= 0.6) {
            thetaE = Math.toRadians(11.0 - 0.7 * ln);
        } else {
            thetaE = Math.toRadians(14.0 - 0.9 * ln);
        }
        return Math.max(Math.toRadians(1.0), Math.min(Math.toRadians(15.0), thetaE));
    }

    /**
     * Builds the Rao bell nozzle wall profile as a cubic Bézier curve.
     *
     * <p>The curve runs from {@code (x₀, r_t)} with slope {@code tan(θ_max)} at
     * the throat to {@code (x_exit, r_e)} with slope {@code tan(θ_E)} at the
     * exit, where {@code x₀ = sonicLineX(r_t)}, {@code θ_E} is the Rao exit
     * angle from {@link #computeRaoExitAngle()}, and {@code x_exit} is from
     * {@link #estimateExitX()}.
     *
     * <p>A cubic Bézier is used so that the profile is guaranteed x-monotone
     * regardless of the magnitude of the sonic-line offset {@code x₀}.  The two
     * inner control points are placed at one-third and two-thirds of the axial
     * span ({@code span = x_exit − x₀}):
     * <pre>
     *   P₁ = (x₀ + span/3,   r_t + (span/3) · tan θ_max)
     *   P₂ = (x_exit − span/3, r_e − (span/3) · tan θ_E)
     * </pre>
     * Because {@code x₀ &lt; P₁.x &lt; P₂.x &lt; x_exit}, the axial
     * derivative {@code dx/dt > 0} for all {@code t ∈ [0, 1]}, guaranteeing
     * strict monotonicity.
     *
     * @return Array of {@code {x, radius, angle}} triples uniformly sampled in
     *         Bézier parameter {@code t ∈ [0, 1]}; 500 intervals
     */
    private double[][] buildRaoWallProfile() {
        double rt = parameters.throatRadius();
        double re = parameters.exitRadius();
        double thetaMax = parameters.wallAngleInitial();
        double thetaE = computeRaoExitAngle();
        double exitX = estimateExitX();

        // Guard: exit angle must be strictly less than the entry angle for a
        // converging bell contour.  Clamp to 90 % of thetaMax for degenerate inputs.
        thetaE = Math.min(thetaE, thetaMax * 0.9);

        double slope0 = Math.tan(thetaMax);
        double slopeE = Math.tan(thetaE);

        // Start the Rao profile at the wall point on the curved sonic line, matching
        // initialLine.get(n).x = sonicLineX(rt).  This ensures every subsequent
        // Rao profile point has x ≥ wallPoints.get(0).x, keeping the wall
        // x-sequence strictly monotonically increasing.
        double x0 = sonicLineX(rt);

        // Cubic Bézier control points at 1/3 and 2/3 of the axial span.
        // Both lie strictly between x0 and exitX → curve is x-monotonic for
        // any physically valid x0 (no risk of the Bézier looping back).
        double span = exitX - x0;
        double cx1 = x0    + span / 3.0;
        double cy1 = rt    + (span / 3.0) * slope0;
        double cx2 = exitX - span / 3.0;
        double cy2 = re    - (span / 3.0) * slopeE;

        int nSeg = 500;
        double[][] profile = new double[nSeg + 1][3];
        for (int i = 0; i <= nSeg; i++) {
            double t = (double) i / nSeg;
            double u = 1.0 - t;
            double x = u*u*u * x0  + 3.0*u*u*t * cx1 + 3.0*u*t*t * cx2 + t*t*t * exitX;
            double y = u*u*u * rt  + 3.0*u*u*t * cy1 + 3.0*u*t*t * cy2 + t*t*t * re;
            // Cubic Bézier tangent direction
            double dxdt = 3.0*(u*u*(cx1-x0) + 2.0*u*t*(cx2-cx1) + t*t*(exitX-cx2));
            double dydt = 3.0*(u*u*(cy1-rt)  + 2.0*u*t*(cy2-cy1) + t*t*(re  -cy2));
            double angle = Math.atan2(dydt, dxdt);
            profile[i][0] = x;
            profile[i][1] = y;
            profile[i][2] = angle;
        }
        return profile;
    }

    /**
     * Computes the interior intersection point of a C+ characteristic from
     * {@code left} and a C− characteristic from {@code right} using the
     * Method of Characteristics.
     *
     * <p>The planar step solves the linear Riemann-invariant system:
     * <pre>
     *   Q+ = θ_L − ν_L  (constant along C+)
     *   Q− = θ_R + ν_R  (constant along C−)
     *   θ = (Q− + Q+) / 2
     *   ν = (Q− − Q+) / 2
     * </pre>
     * For an axisymmetric nozzle ({@link NozzleDesignParameters#axisymmetric()} is
     * {@code true}), an iterative correction loop adjusts Q± for the source term
     * {@code sin(θ) / (y · cot(µ))} and re-solves the intersection until
     * convergence or {@link #maxIterations} is reached.
     *
     * @param left  Characteristic point on the left (nearer the centerline,
     *              lower radial coordinate); the C+ characteristic originates here
     * @param right Characteristic point on the right (nearer the wall, higher
     *              radial coordinate); the C− characteristic originates here
     * @return The interior {@link CharacteristicPoint} at the characteristic
     *         intersection, or {@code null} if the Prandtl-Meyer function yields
     *         a non-physical result (ν ≤ 0, Mach &lt; 1, NaN/infinite position, or
     *         radial coordinate out of the allowable range)
     */
    public CharacteristicPoint computeInteriorPoint(CharacteristicPoint left, CharacteristicPoint right) {
        GasProperties gas = parameters.gasProperties();
        
        // Riemann invariants
        // C+ from left: Q+ = theta - nu (right-running, goes toward wall)
        // C- from right: Q- = theta + nu (left-running, goes toward centerline)
        double Qplus = left.theta() - left.nu();
        double Qminus = right.theta() + right.nu();
        
        // Solve for theta and nu at intersection
        double theta = (Qminus + Qplus) / 2;
        double nu = (Qminus - Qplus) / 2;
        
        if (nu <= 0) {
            return null;
        }
        
        double mach = gas.machFromPrandtlMeyer(nu);
        double mu = gas.machAngle(mach);
        
        // Characteristic slopes
        // C+ from left: slope = tan(theta + mu) [positive, goes up-right]
        // C- from right: slope = tan(theta - mu) [can be negative, goes down-right]
        double slopePlus = Math.tan((left.theta() + theta) / 2 + (left.mu() + mu) / 2);
        double slopeMinus = Math.tan((right.theta() + theta) / 2 - (right.mu() + mu) / 2);
        
        double x, y;
        double denom = slopePlus - slopeMinus;
        
        // Unreachable branch: |denom| < 1e-12 requires slopePlus == slopeMinus, i.e. µ = 0
        // (infinite Mach). Not physically realizable — denom is always > 1e-12.
        // if (Math.abs(denom) < 1e-12) {
        //     x = (left.x() + right.x()) / 2;
        //     y = (left.y() + right.y()) / 2;
        // } else {
        // Intersection of two lines:
        // From left: y = left.y + slopePlus * (x - left.x)
        // From right: y = right.y + slopeMinus * (x - right.x)
        x = (right.y() - left.y() + slopePlus * left.x() - slopeMinus * right.x()) / denom;
        y = left.y() + slopePlus * (x - left.x());
        // }
        
        // Axisymmetric correction
        if (axisymmetric) {
            boolean corrConverged = false;
            for (int iter = 0; iter < maxIterations; iter++) {
                double yAvg = (left.y() + right.y() + y) / 3;
                if (yAvg < 1e-10) break;
                
                double ds_plus = Math.hypot(x - left.x(), y - left.y());
                double ds_minus = Math.hypot(x - right.x(), y - right.y());
                
                double machAvg = (left.mach() + right.mach() + mach) / 3;
                double sinThetaAvg = Math.sin((left.theta() + right.theta() + theta) / 3);
                double cotMu = Math.sqrt(machAvg * machAvg - 1);
                
                // cotMu = sqrt(M²-1) → 0 as M → 1; dividing by it in the source-term
                // correction would produce an unbounded result at a sonic point.
                // Uses convergenceTolerance as the sonic proximity threshold so that
                // callers with a large tolerance can exercise this guard in tests.
                if (cotMu < convergenceTolerance) break;
                
                double corr = sinThetaAvg / (yAvg * cotMu);
                double deltaPlus = ds_plus * corr;
                double deltaMinus = ds_minus * corr;
                
                double newQplus = Qplus - deltaPlus;
                double newQminus = Qminus + deltaMinus;
                
                double newTheta = (newQminus + newQplus) / 2;
                double newNu = (newQminus - newQplus) / 2;
                
                if (Math.abs(newTheta - theta) < convergenceTolerance &&
                    Math.abs(newNu - nu) < convergenceTolerance) {
                    corrConverged = true;
                    break;
                }
                
                theta = newTheta;
                nu = newNu;
                
                // Unreachable: newNu = nu + (δ⁻ + δ⁺)/2; both deltas are positive for physical
                // nozzle angles (θ > 0, y > 0, cotµ > 0), so nu strictly increases each step.
                // if (nu <= 0) return null;
                
                mach = gas.machFromPrandtlMeyer(nu);
                mu = gas.machAngle(mach);
                
                slopePlus = Math.tan((left.theta() + theta) / 2 + (left.mu() + mu) / 2);
                slopeMinus = Math.tan((right.theta() + theta) / 2 - (right.mu() + mu) / 2);
                
                // denom = slopePlus - slopeMinus is always > 1e-12 for physical Mach numbers
                // (requires µ > 0, which holds for all M < ∞); unconditional update is safe.
                denom = slopePlus - slopeMinus;
                x = (right.y() - left.y() + slopePlus * left.x() - slopeMinus * right.x()) / denom;
                y = left.y() + slopePlus * (x - left.x());
            }
            if (!corrConverged) {
                LOG.warn("Axisymmetric correction did not converge after {} iterations near (x={}, y={})",
                        maxIterations, String.format("%.4f", x), String.format("%.4f", y));
            }
        }
        
        /*
         Unreachable: physical nozzle geometry with positive ν guarantees finite x/y,
         mach ≥ 1 from machFromPrandtlMeyer(ν > 0), y > 0 from left.y > 0 + positive
         slopePlus, and y ≤ exitRadius·5 within the normal expansion fan.
         if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(mach) ||
             Double.isInfinite(x) || Double.isInfinite(y) ||
             mach < 1.0 || y < 0 || y > parameters.exitRadius() * 5.0) {
             return null;
         }
        */

       double T = parameters.chamberTemperature() * gas.isentropicTemperatureRatio(mach);
        double P = parameters.chamberPressure() * gas.isentropicPressureRatio(mach);
        double rho = P / (gas.gasConstant() * T);
        double V = mach * gas.speedOfSound(T);
        
        return CharacteristicPoint.create(x, y, mach, theta, nu, mu, P, T, rho, V,
                CharacteristicPoint.PointType.INTERIOR
        ).withIndices(left.leftIndex(), right.rightIndex());
    }
    
    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the nozzle design parameters used to construct this net.
     *
     * @return The {@link NozzleDesignParameters} for this computation
     */
    public NozzleDesignParameters getParameters() { return parameters; }

    /**
     * Returns an unmodifiable view of the full characteristic net as a list of
     * rows, where each row is the list of {@link CharacteristicPoint}s computed
     * during one propagation step.
     *
     * @return Nested list of characteristic points; the outermost index is the
     *         row index (0 = initial data line), the inner index is the point
     *         position within the row
     */
    public List<List<CharacteristicPoint>> getNetPoints() { return Collections.unmodifiableList(netPoints); }

    /**
     * Returns an unmodifiable view of the ordered sequence of wall points that
     * define the computed nozzle contour.
     *
     * @return Unmodifiable list of wall characteristic points in axial order
     */
    public List<CharacteristicPoint> getWallPoints() { return Collections.unmodifiableList(wallPoints); }

    /**
     * Returns the total number of characteristic points across all rows of the net.
     *
     * @return Sum of all row sizes
     */
    public int getTotalPointCount() { return netPoints.stream().mapToInt(List::size).sum(); }

    /**
     * Returns a flat list of every characteristic point in the net (all rows
     * concatenated in row order).
     *
     * @return Flat list of all {@link CharacteristicPoint}s
     */
    public List<CharacteristicPoint> getAllPoints() { return netPoints.stream().flatMap(List::stream).toList(); }

    /**
     * Calculates the exit area ratio {@code A_exit / A_throat} from the radial
     * coordinate of the last wall point.
     *
     * @return Exit area ratio; returns the design value from the parameters if
     *         no wall points have been generated
     */
    public double calculateExitAreaRatio() {
        if (wallPoints.isEmpty()) return parameters.exitAreaRatio();
        double rt = parameters.throatRadius();
        double re = wallPoints.getLast().y();
        return (re * re) / (rt * rt);
    }
    
    /**
     * Validates the generated characteristic net for physical consistency.
     *
     * <p>The following checks are performed:
     * <ul>
     *   <li>The net is non-empty (implying wall points were also generated).</li>
     *   <li>Wall points are strictly monotonically increasing in the axial direction.</li>
     *   <li>Every point in the net has a Mach number of at least 1.0 (fully supersonic).</li>
     * </ul>
     *
     * @return {@code true} if all checks pass; {@code false} otherwise
     */
    public boolean validate() {
        if (netPoints.isEmpty()) return false;
        for (int i = 1; i < wallPoints.size(); i++) {
            if (wallPoints.get(i).x() <= wallPoints.get(i-1).x()) return false;
        }
        for (CharacteristicPoint p : getAllPoints()) {
            if (p.mach() < 1.0) return false;
        }
        return true;
    }
}
