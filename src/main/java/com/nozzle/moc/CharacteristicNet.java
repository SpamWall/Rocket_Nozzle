package com.nozzle.moc;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Computes the characteristic network for a supersonic nozzle using the Method of Characteristics.
 * 
 * The coordinate system:
 * - x: axial direction (downstream positive)
 * - y: radial direction (outward from centerline positive)
 * - Centerline is at y=0
 * - Throat is at x=0, y ranges from 0 to rt (throat radius)
 * 
 * For a minimum-length nozzle:
 * 1. Initial line: vertical line at throat from centerline to wall
 * 2. Expansion: characteristics propagate downstream
 * 3. Wall contour: determined by the outermost C+ characteristic
 */
public class CharacteristicNet {
    
    private final NozzleDesignParameters parameters;
    private final List<List<CharacteristicPoint>> netPoints;
    private final List<CharacteristicPoint> wallPoints;
    private final List<CharacteristicPoint> centerlinePoints;
    private final boolean axisymmetric;
    private final double convergenceTolerance;
    private final int maxIterations;
    
    private static final double DEFAULT_TOLERANCE = 1e-8;
    private static final int DEFAULT_MAX_ITERATIONS = 100;
    
    public CharacteristicNet(NozzleDesignParameters parameters) {
        this(parameters, DEFAULT_TOLERANCE, DEFAULT_MAX_ITERATIONS);
    }
    
    public CharacteristicNet(NozzleDesignParameters parameters, double tolerance, int maxIter) {
        this.parameters = parameters;
        this.netPoints = new ArrayList<>();
        this.wallPoints = new ArrayList<>();
        this.centerlinePoints = new ArrayList<>();
        this.axisymmetric = parameters.axisymmetric();
        this.convergenceTolerance = tolerance;
        this.maxIterations = maxIter;
    }
    
    /**
     * Generates the complete characteristic net.
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
        centerlinePoints.add(initialLine.get(0));  // First point is centerline
        
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
            CharacteristicPoint lastInterior = nextLine.get(nextLine.size() - 1);
            CharacteristicPoint prevWall = wallPoints.get(wallPoints.size() - 1);
            CharacteristicPoint newWall = computeWallPoint(lastInterior, prevWall);
            
            if (newWall != null && newWall.y() > prevWall.y() && newWall.x() > prevWall.x()) {
                nextLine.add(newWall);
                wallPoints.add(newWall);
            }
            
            // Check for centerline point
            if (nextLine.get(0).y() < rt * 0.02) {
                centerlinePoints.add(nextLine.get(0).withType(CharacteristicPoint.PointType.CENTERLINE));
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
     * Computes a wall point by extending the C+ characteristic from the interior point.
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
        
        double T = parameters.chamberTemperature() * gas.isentropicTemperatureRatio(mach);
        double P = parameters.chamberPressure() * gas.isentropicPressureRatio(mach);
        double rho = P / (gas.gasConstant() * T);
        double V = mach * gas.speedOfSound(T);
        
        return CharacteristicPoint.create(x, y, mach, theta, nu, mu, P, T, rho, V,
                CharacteristicPoint.PointType.WALL);
    }
    
    private double estimateExitX() {
        double rt = parameters.throatRadius();
        double re = parameters.exitRadius();
        return (re - rt) / Math.tan(Math.toRadians(15)) * parameters.lengthFraction();
    }
    
    /**
     * Computes an interior point from two adjacent points.
     * Left point is closer to centerline (lower y), right point is closer to wall (higher y).
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
            mach < 1.0 || y < 0) {
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
    
    // Batch generation
    public static List<CharacteristicNet> generateBatch(List<NozzleDesignParameters> parametersList) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<CharacteristicNet>> futures = parametersList.stream()
                    .map(params -> executor.submit(() -> new CharacteristicNet(params).generate()))
                    .toList();
            
            return futures.stream()
                    .map(f -> { try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); } })
                    .toList();
        }
    }
    
    // Accessors
    public NozzleDesignParameters getParameters() { return parameters; }
    public List<List<CharacteristicPoint>> getNetPoints() { return Collections.unmodifiableList(netPoints); }
    public List<CharacteristicPoint> getWallPoints() { return Collections.unmodifiableList(wallPoints); }
    public List<CharacteristicPoint> getCenterlinePoints() { return Collections.unmodifiableList(centerlinePoints); }
    public int getTotalPointCount() { return netPoints.stream().mapToInt(List::size).sum(); }
    public List<CharacteristicPoint> getAllPoints() { return netPoints.stream().flatMap(List::stream).toList(); }
    
    public double calculateExitAreaRatio() {
        if (wallPoints.isEmpty()) return parameters.exitAreaRatio();
        double rt = parameters.throatRadius();
        double re = wallPoints.get(wallPoints.size() - 1).y();
        return (re * re) / (rt * rt);
    }
    
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
