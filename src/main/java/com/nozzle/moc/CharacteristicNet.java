package com.nozzle.moc;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;

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
     *   <li><b>Initial data line</b> — a vertical line at the throat
     *       ({@code x ≈ 0}) seeded with {@link NozzleDesignParameters#numberOfCharLines()}
     *       characteristic points whose flow angles ramp from {@code 0} at the
     *       centerline to {@link NozzleDesignParameters#wallAngleInitial()} at the wall.</li>
     *   <li><b>Net propagation</b> — each row of interior points is computed by
     *       intersecting adjacent C+ and C− characteristics via
     *       {@link #computeInteriorPoint}.  Rows are generated until flow is
     *       nearly axial (max {@code |θ| < 0.5°}) or the row budget is exhausted.</li>
     *   <li><b>Wall points</b> — after each interior row, the outermost C+
     *       characteristic is extended to the evolving wall contour via
     *       {@link #computeWallPoint}.</li>
     * </ol>
     *
     * @return This instance for method chaining
     */
    public CharacteristicNet generate() {
        GasProperties gas = parameters.gasProperties();
        int n = parameters.numberOfCharLines();
        double rt = parameters.throatRadius();
        double thetaMax = parameters.wallAngleInitial();
        
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
            
            // x position: small offset, increases slightly with y to give C+ slope
            double x = rt * 0.01 * (1 + fraction);
            
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
        wallPoints.add(initialLine.get(n));  // Last point is wall point
        // Propagate the characteristic net
        List<CharacteristicPoint> currentLine = initialLine;
        int maxRows = 2 * n + 20;
        
        for (int row = 0; row < maxRows && currentLine.size() >= 2; row++) {
            List<CharacteristicPoint> nextLine = new ArrayList<>();
            
            // Compute interior points from adjacent pairs
            // Left point (lower index) is closer to centerline
            // Right point (higher index) is closer to wall
            for (int i = 0; i < currentLine.size() - 1; i++) {
                CharacteristicPoint left = currentLine.get(i);
                CharacteristicPoint right = currentLine.get(i + 1);
                CharacteristicPoint interior = computeInteriorPoint(left, right);
                
                if (interior != null) {
                    nextLine.add(interior);
                }
            }
            
            if (nextLine.isEmpty()) {
                break;
            }
            
            // Add wall point: extend C+ characteristic from last interior point
            CharacteristicPoint lastInterior = nextLine.getLast();
            CharacteristicPoint prevWall = wallPoints.getLast();
            CharacteristicPoint newWall = computeWallPoint(lastInterior, prevWall);
            
            if (newWall != null && newWall.y() > prevWall.y() && newWall.x() > prevWall.x()) {
                nextLine.add(newWall);
                wallPoints.add(newWall);
            }
            

            netPoints.add(nextLine);
            currentLine = nextLine;
            
            // Termination: flow nearly axial
            double maxTheta = currentLine.stream().mapToDouble(p -> Math.abs(p.theta())).max().orElse(0);
            if (maxTheta < Math.toRadians(0.5)) {
                break;
            }
        }
        
        return this;
    }
    
    /**
     * Extends the outermost C+ characteristic from an interior point to the
     * evolving nozzle wall and returns the resulting wall-point.
     *
     * <p>The wall angle is assumed to decay linearly from
     * {@link NozzleDesignParameters#wallAngleInitial()} at the throat to zero at
     * the estimated exit ({@link #estimateExitX()}).  The C+ Riemann invariant
     * {@code Q+ = θ − ν} is preserved along the characteristic.  An iterative
     * predictor–corrector scheme (20 sweeps) resolves the intersection of the
     * characteristic line with the linearly-interpolated wall segment.
     *
     * @param interior  Last interior characteristic point on the current row
     *                  (the C+ characteristic originates here)
     * @param prevWall  The preceding wall point; defines the starting segment
     *                  of the wall for the intersection search
     * @return The new {@link CharacteristicPoint} on the wall, or {@code null}
     *         if the computed position is physically unreasonable (NaN, infinite,
     *         outside the valid radial bounds, or upstream of {@code prevWall})
     */
    private CharacteristicPoint computeWallPoint(CharacteristicPoint interior, CharacteristicPoint prevWall) {
        GasProperties gas = parameters.gasProperties();
        double rt = parameters.throatRadius();
        double thetaMax = parameters.wallAngleInitial();
        
        // Q+ is constant along C+ characteristic
        double Qplus = interior.theta() - interior.nu();
        
        // Estimate wall position
        double exitX = estimateExitX();
        double dx = rt * 0.05;
        
        // C+ characteristic slope
        double slopePlus = Math.tan(interior.theta() + interior.mu());
        
        // Initial estimate
        double x = interior.x() + dx;
        double y = interior.y() + slopePlus * dx;
        
        // Wall angle decreases from thetaMax to 0 over the nozzle length
        double wallTheta = thetaMax * Math.max(0, 1 - x / exitX);
        
        // From C+: theta = wallTheta (flow tangent to wall)
        // nu = theta - Qplus
        double theta = wallTheta;
        double nu = theta - Qplus;
        
        // Ensure valid expansion
        if (nu < 0.001) {
            nu = 0.001;
            theta = Qplus + nu;
        }
        
        double mach = gas.machFromPrandtlMeyer(nu);
        double mu = gas.machAngle(mach);
        
        // Iterate to find wall intersection
        for (int iter = 0; iter < 20; iter++) {
            // Average slope
            double avgSlope = Math.tan((interior.theta() + theta) / 2 + (interior.mu() + mu) / 2);
            
            // Wall contour: y = prevWall.y + tan(avgWallTheta) * (x - prevWall.x)
            // where avgWallTheta is average wall angle
            double avgWallTheta = (prevWall.theta() + wallTheta) / 2;
            
            // Find x where characteristic meets wall
            // From interior: y = interior.y + avgSlope * (x - interior.x)
            // Wall: y = prevWall.y + tan(avgWallTheta) * (x - prevWall.x)
            
            double wallSlope = Math.tan(avgWallTheta);
            double denom = avgSlope - wallSlope;
            
            if (Math.abs(denom) > 1e-10) {
                x = (prevWall.y() - interior.y() + avgSlope * interior.x() - wallSlope * prevWall.x()) / denom;
                y = interior.y() + avgSlope * (x - interior.x());
            }
            
            // Update wall angle at new x
            wallTheta = thetaMax * Math.max(0, 1 - x / exitX);
            theta = wallTheta;
            nu = theta - Qplus;
            
            if (nu < 0.001) {
                nu = 0.001;
                theta = Qplus + nu;
            }
            
            mach = gas.machFromPrandtlMeyer(nu);
            mu = gas.machAngle(mach);
        }
        
        // Ensure expansion (y must increase for divergent nozzle)
        if (y <= prevWall.y()) {
            y = prevWall.y() + rt * 0.01;
        }
        if (x <= prevWall.x()) {
            x = prevWall.x() + rt * 0.02;
        }
        
        // Validate wall point is physically reasonable
        double re = parameters.exitRadius();
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isInfinite(x) || Double.isInfinite(y) ||
                y < parameters.throatRadius() * 0.8 || y > re * 3.0 || x < 0) {
            return null;
        }

        double T = parameters.chamberTemperature() * gas.isentropicTemperatureRatio(mach);
        double P = parameters.chamberPressure() * gas.isentropicPressureRatio(mach);
        double rho = P / (gas.gasConstant() * T);
        double V = mach * gas.speedOfSound(T);

        return CharacteristicPoint.create(x, y, mach, theta, nu, mu, P, T, rho, V,
                CharacteristicPoint.PointType.WALL);
    }
    
    /**
     * Estimates the axial position of the nozzle exit plane for use in the
     * wall-angle linear-decay model inside {@link #computeWallPoint}.
     * The estimate uses the 15° reference cone length scaled by the length fraction.
     *
     * @return Estimated exit axial position in metres
     */
    private double estimateExitX() {
        double rt = parameters.throatRadius();
        double re = parameters.exitRadius();
        return (re - rt) / Math.tan(Math.toRadians(15)) * parameters.lengthFraction();
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
        
        if (Math.abs(denom) < 1e-12) {
            x = (left.x() + right.x()) / 2;
            y = (left.y() + right.y()) / 2;
        } else {
            // Intersection of two lines:
            // From left: y = left.y + slopePlus * (x - left.x)
            // From right: y = right.y + slopeMinus * (x - right.x)
            x = (right.y() - left.y() + slopePlus * left.x() - slopeMinus * right.x()) / denom;
            y = left.y() + slopePlus * (x - left.x());
        }
        
        // Axisymmetric correction
        if (axisymmetric) {
            for (int iter = 0; iter < maxIterations; iter++) {
                double yAvg = (left.y() + right.y() + y) / 3;
                if (yAvg < 1e-10) break;
                
                double ds_plus = Math.hypot(x - left.x(), y - left.y());
                double ds_minus = Math.hypot(x - right.x(), y - right.y());
                
                double machAvg = (left.mach() + right.mach() + mach) / 3;
                double sinThetaAvg = Math.sin((left.theta() + right.theta() + theta) / 3);
                double cotMu = Math.sqrt(machAvg * machAvg - 1);
                
                if (cotMu < 1e-10) break;
                
                double corr = sinThetaAvg / (yAvg * cotMu);
                double deltaPlus = ds_plus * corr;
                double deltaMinus = ds_minus * corr;
                
                double newQplus = Qplus - deltaPlus;
                double newQminus = Qminus + deltaMinus;
                
                double newTheta = (newQminus + newQplus) / 2;
                double newNu = (newQminus - newQplus) / 2;
                
                if (Math.abs(newTheta - theta) < convergenceTolerance &&
                    Math.abs(newNu - nu) < convergenceTolerance) {
                    break;
                }
                
                theta = newTheta;
                nu = newNu;
                
                if (nu <= 0) return null;
                
                mach = gas.machFromPrandtlMeyer(nu);
                mu = gas.machAngle(mach);
                
                slopePlus = Math.tan((left.theta() + theta) / 2 + (left.mu() + mu) / 2);
                slopeMinus = Math.tan((right.theta() + theta) / 2 - (right.mu() + mu) / 2);
                
                denom = slopePlus - slopeMinus;
                if (Math.abs(denom) > 1e-12) {
                    x = (right.y() - left.y() + slopePlus * left.x() - slopeMinus * right.x()) / denom;
                    y = left.y() + slopePlus * (x - left.x());
                }
            }
        }
        
        // Validate
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(mach) ||
            Double.isInfinite(x) || Double.isInfinite(y) ||
            mach < 1.0 || y < 0 || y > parameters.exitRadius() * 5.0) {
            return null;
        }
        
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
     *   <li>The net and wall-point lists are non-empty.</li>
     *   <li>Wall points are strictly monotonically increasing in the axial direction.</li>
     *   <li>Every point in the net has a Mach number of at least 1.0 (fully supersonic).</li>
     * </ul>
     *
     * @return {@code true} if all checks pass; {@code false} otherwise
     */
    public boolean validate() {
        if (netPoints.isEmpty() || wallPoints.isEmpty()) return false;
        for (int i = 1; i < wallPoints.size(); i++) {
            if (wallPoints.get(i).x() <= wallPoints.get(i-1).x()) return false;
        }
        for (CharacteristicPoint p : getAllPoints()) {
            if (p.mach() < 1.0) return false;
        }
        return true;
    }
}
