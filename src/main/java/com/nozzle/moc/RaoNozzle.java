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
import java.util.List;

/**
 * Implements the Rao optimum contour (bell) nozzle design.
 * Based on G.V.R. Rao's method for thrust-optimized nozzle contours.
 * Provides comparison baseline for MOC-designed nozzles.
 */
public class RaoNozzle {
    
    private final NozzleDesignParameters parameters;
    private final double lengthFraction;
    private final int numContourPoints;
    
    private final List<Point2D> contourPoints;
    private double actualLength;
    private double exitAngle;
    private double inflectionAngle;
    
    /**
     * Creates a Rao bell nozzle with default settings.
     *
     * @param parameters Design parameters
     */
    public RaoNozzle(NozzleDesignParameters parameters) {
        this(parameters, parameters.lengthFraction(), 100);
    }
    
    /**
     * Creates a Rao bell nozzle with custom settings.
     *
     * @param parameters       Design parameters
     * @param lengthFraction   Fractional length (0.6-1.0 typical)
     * @param numContourPoints Number of contour points
     */
    public RaoNozzle(NozzleDesignParameters parameters, double lengthFraction, int numContourPoints) {
        this.parameters = parameters;
        this.lengthFraction = lengthFraction;
        this.numContourPoints = numContourPoints;
        this.contourPoints = new ArrayList<>();
    }
    
    /**
     * Generates the Rao bell nozzle contour.
     *
     * @return This instance for chaining
     */
    public RaoNozzle generate() {
        contourPoints.clear();
        
        double rt = parameters.throatRadius();
        double re = parameters.exitRadius();
        double exitMach = parameters.exitMach();
        
        // Calculate key angles
        calculateAngles(exitMach);
        
        // 15-degree half angle cone reference length
        double coneLength = (re - rt) / Math.tan(Math.toRadians(15));
        actualLength = coneLength * lengthFraction;
        
        // Generate throat circular arc region
        generateThroatRegion(rt);
        
        // Generate bell contour (parabolic approximation)
        generateBellContour(rt, re);
        
        return this;
    }
    
    /**
     * Calculates the inflection and exit angles using Rao correlations.
     *
     * @param exitMach Exit Mach number
     */
    private void calculateAngles(double exitMach) {
        // Rao correlation for inflection angle (degrees)
        // Based on empirical data from NASA studies
        double areaRatio = parameters.exitAreaRatio();
        
        // Inflection angle correlation (depends on length fraction)
        if (lengthFraction >= 0.8) {
            inflectionAngle = Math.toRadians(21.0 + 3.0 * (exitMach - 2.0));
        } else if (lengthFraction >= 0.6) {
            inflectionAngle = Math.toRadians(25.0 + 4.0 * (exitMach - 2.0));
        } else {
            inflectionAngle = Math.toRadians(30.0 + 5.0 * (exitMach - 2.0));
        }
        
        // Limit inflection angle
        inflectionAngle = Math.min(inflectionAngle, Math.toRadians(45));
        inflectionAngle = Math.max(inflectionAngle, Math.toRadians(15));
        
        // Exit angle correlation (Rao optimum)
        // For optimum thrust, exit angle should be small
        double ln = Math.log(areaRatio);
        if (lengthFraction >= 0.8) {
            exitAngle = Math.toRadians(8.0 - 0.5 * ln);
        } else if (lengthFraction >= 0.6) {
            exitAngle = Math.toRadians(11.0 - 0.7 * ln);
        } else {
            exitAngle = Math.toRadians(14.0 - 0.9 * ln);
        }
        
        // Ensure exit angle is positive and reasonable
        exitAngle = Math.max(exitAngle, Math.toRadians(1));
        exitAngle = Math.min(exitAngle, Math.toRadians(15));
    }
    
    /**
     * Generates the circular arc throat region.
     *
     * @param rt Throat radius
     */
    private void generateThroatRegion(double rt) {
        // Throat downstream radius of curvature (r_cd = ratio × r_t)
        double rcd = parameters.throatCurvatureRatio() * rt;

        // Generate downstream arc (divergent section start) beginning at the throat
        int numDownstreamPoints = numContourPoints / 10;
        for (int i = 0; i <= numDownstreamPoints; i++) {
            double angle = (i * inflectionAngle) / numDownstreamPoints;
            double x = rcd * Math.sin(angle);
            double y = rt + rcd * (1 - Math.cos(angle));
            contourPoints.add(new Point2D(x, y));
        }
    }
    
    /**
     * Generates the bell contour using a parabolic approximation.
     *
     * @param rt Throat radius
     * @param re Exit radius
     */
    private void generateBellContour(double rt, double re) {
        // Start point (end of throat arc)
        double rcd = parameters.throatCurvatureRatio() * rt;
        double x0 = rcd * Math.sin(inflectionAngle);
        double y0 = rt + rcd * (1 - Math.cos(inflectionAngle));
        double slope0 = Math.tan(inflectionAngle);
        
        // End point
        double xEnd = actualLength;
        @SuppressWarnings("UnnecessaryLocalVariable")
        double yEnd = re;
        double slopeEnd = Math.tan(exitAngle);
        
        // Solve for parabola coefficients
        // y = a*x^2 + b*x + c
        // With boundary conditions at start and end points
        
        // Use cubic Bézier curve for smoother transition
        // Control points for cubic Bezier
        double dx = xEnd - x0;
        double cx1 = x0 + dx / 3.0;
        double cy1 = y0 + (dx / 3.0) * slope0;
        double cx2 = xEnd - dx / 3.0;
        double cy2 = yEnd - (dx / 3.0) * slopeEnd;
        
        // Generate bell contour points using Bézier curve
        int numBellPoints = numContourPoints - contourPoints.size();
        for (int i = 1; i <= numBellPoints; i++) {
            double t = (double) i / numBellPoints;
            double u = 1 - t;
            
            // Cubic Bezier formula
            double x = u * u * u * x0 + 3 * u * u * t * cx1 + 3 * u * t * t * cx2 + t * t * t * xEnd;
            double y = u * u * u * y0 + 3 * u * u * t * cy1 + 3 * u * t * t * cy2 + t * t * t * yEnd;
            
            contourPoints.add(new Point2D(x, y));
        }
    }
    
    /**
     * Returns the nozzle contour points.
     *
     * @return List of contour points
     */
    public List<Point2D> getContourPoints() {
        if (contourPoints.isEmpty()) {
            generate();
        }
        return new ArrayList<>(contourPoints);
    }

    /**
     * Returns the design parameters used to construct this nozzle.
     *
     * @return the {@link NozzleDesignParameters} supplied at construction time
     */
    public NozzleDesignParameters getParameters() { return parameters; }

    /**
     * Returns the actual nozzle length.
     *
     * @return Nozzle length in meters
     */
    public double getActualLength() {
        return actualLength;
    }
    
    /**
     * Returns the exit angle.
     *
     * @return Exit angle in radians
     */
    public double getExitAngle() {
        return exitAngle;
    }
    
    /**
     * Returns the inflection angle.
     *
     * @return Inflection angle in radians
     */
    public double getInflectionAngle() {
        return inflectionAngle;
    }
    
    /**
     * Calculates the theoretical thrust coefficient.
     *
     * @return Thrust coefficient
     */
    public double calculateThrustCoefficient() {
        GasProperties gas = parameters.gasProperties();
        double gamma = gas.gamma();
        double pe = parameters.idealExitPressure();
        double pa = parameters.ambientPressure();
        double pc = parameters.chamberPressure();
        double areaRatio = parameters.exitAreaRatio();
        
        // Momentum thrust coefficient
        double gp1 = gamma + 1;
        double gm1 = gamma - 1;
        double term1 = 2 * gamma * gamma / gm1 * Math.pow(2.0 / gp1, gp1 / gm1);
        double pressureRatio = pe / pc;
        double term2 = 1 - Math.pow(pressureRatio, gm1 / gamma);
        double cfMomentum = Math.sqrt(term1 * term2);
        
        // Pressure thrust term
        double cfPressure = (pe - pa) / pc * areaRatio;
        
        // Divergence loss factor (approximate)
        double lambda = (1 + Math.cos(exitAngle)) / 2;
        
        return lambda * cfMomentum + cfPressure;
    }
    
    /**
     * Calculates the nozzle efficiency compared to ideal.
     *
     * @return Nozzle efficiency (0-1)
     */
    public double calculateEfficiency() {
        double cfActual = calculateThrustCoefficient();
        double cfIdeal = parameters.idealThrustCoefficient();
        return cfActual / cfIdeal;
    }
    
    /**
     * Calculates wall radius at a given axial position.
     *
     * @param x Axial position
     * @return Wall radius
     */
    public double getRadiusAt(double x) {
        if (contourPoints.isEmpty()) {
            generate();
        }

        double radius = parameters.exitRadius();

        for (int i = 1; i < contourPoints.size(); i++) {
            Point2D prev = contourPoints.get(i - 1);
            Point2D curr = contourPoints.get(i);

            if (x >= prev.x() && x <= curr.x()) {
                double t = (x - prev.x()) / (curr.x() - prev.x());
                radius = prev.y() + t * (curr.y() - prev.y());
                break;
            }
        }

        return radius;
    }
    
    /**
     * Calculates wall angle at a given axial position.
     *
     * @param x Axial position
     * @return Wall angle in radians
     */
    public double getAngleAt(double x) {
        if (contourPoints.isEmpty()) {
            generate();
        }
        
        // Find surrounding points and calculate slope
        for (int i = 1; i < contourPoints.size(); i++) {
            Point2D prev = contourPoints.get(i - 1);
            Point2D curr = contourPoints.get(i);
            
            if (x >= prev.x() && x <= curr.x()) {
                double slope = (curr.y() - prev.y()) / (curr.x() - prev.x());
                return Math.atan(slope);
            }
        }
        
        return exitAngle;
    }
    
    /**
     * Compares this Rao nozzle with a MOC-generated nozzle.
     *
     * @param mocNet MOC characteristic net
     * @return Comparison result
     */
    public NozzleComparison compareTo(CharacteristicNet mocNet) {
        if (contourPoints.isEmpty()) {
            generate();
        }
        
        List<CharacteristicPoint> mocWall = mocNet.getWallPoints();
        
        double maxRadiusDiff = 0;
        double avgRadiusDiff = 0;
        double maxAngleDiff = 0;
        
        int count = 0;
        for (CharacteristicPoint mocPoint : mocWall) {
            double x = mocPoint.x();
            double raoRadius = getRadiusAt(x);
            double raoAngle = getAngleAt(x);
            
            double radiusDiff = Math.abs(mocPoint.y() - raoRadius);
            double angleDiff = Math.abs(mocPoint.theta() - raoAngle);
            
            maxRadiusDiff = Math.max(maxRadiusDiff, radiusDiff);
            avgRadiusDiff += radiusDiff;
            maxAngleDiff = Math.max(maxAngleDiff, angleDiff);
            count++;
        }
        
        avgRadiusDiff /= Math.max(count, 1);
        
        double raoCf = calculateThrustCoefficient();
        double mocCf = calculateMOCThrustCoefficient(mocNet);
        
        return new NozzleComparison(
                maxRadiusDiff,
                avgRadiusDiff,
                maxAngleDiff,
                raoCf,
                mocCf,
                actualLength,
                mocWall.isEmpty() ? 0 : mocWall.getLast().x()
        );
    }
    
    /**
     * Calculates the thrust coefficient for a MOC-generated nozzle design by
     * applying the divergence-factor correction to the ideal thrust coefficient.
     * The exit flow angle is read from the last wall point of {@code mocNet}.
     *
     * @param mocNet MOC characteristic net whose wall-point sequence defines the
     *               exit flow angle used for the divergence factor
     *               {@code λ = (1 + cos θ_exit) / 2}
     * @return Corrected thrust coefficient; returns the ideal value if
     *         {@code mocNet} has no wall points
     */
    private double calculateMOCThrustCoefficient(CharacteristicNet mocNet) {
        List<CharacteristicPoint> wallPoints = mocNet.getWallPoints();
        if (wallPoints.isEmpty()) {
            return parameters.idealThrustCoefficient();
        }
        
        // Get exit conditions from last wall point
        CharacteristicPoint exitPoint = wallPoints.getLast();
        double exitTheta = exitPoint.theta();
        
        // Divergence factor
        double lambda = (1 + Math.cos(exitTheta)) / 2;
        
        return lambda * parameters.idealThrustCoefficient();
    }
    
    /**
     * Immutable result of a point-wise comparison between a Rao bell nozzle contour
     * and a MOC-generated nozzle contour at the same design point.
     *
     * @param maxRadiusDifference   Maximum absolute radial difference between the
     *                              two contours, sampled at each MOC wall point, in metres
     * @param avgRadiusDifference   Mean absolute radial difference across all sampled
     *                              MOC wall points, in metres
     * @param maxAngleDifference    Maximum absolute flow-angle difference between the
     *                              MOC wall tangent and the Rao contour tangent at the
     *                              same axial position, in radians
     * @param raoThrustCoefficient  Thrust coefficient of the Rao bell nozzle, including
     *                              the divergence-factor correction for {@link #exitAngle}
     * @param mocThrustCoefficient  Thrust coefficient of the MOC nozzle, including the
     *                              divergence-factor correction for the MOC exit flow angle
     * @param raoLength             Axial length of the Rao bell nozzle in metres
     * @param mocLength             Axial length of the MOC nozzle (x-coordinate of the
     *                              last wall point) in metres
     */
    public record NozzleComparison(
            double maxRadiusDifference,
            double avgRadiusDifference,
            double maxAngleDifference,
            double raoThrustCoefficient,
            double mocThrustCoefficient,
            double raoLength,
            double mocLength
    ) {
        /**
         * Returns the absolute difference in thrust coefficients between the
         * Rao and MOC nozzle designs.
         *
         * @return {@code |Cf_Rao − Cf_MOC|}
         */
        public double thrustCoefficientDifference() {
            return Math.abs(raoThrustCoefficient - mocThrustCoefficient);
        }
        
        @SuppressWarnings("NullableProblems")
        @Override
        public String toString() {
            return String.format(
                    "NozzleComparison[maxΔr=%.4f mm, avgΔr=%.4f mm, maxΔθ=%.2f°, " +
                    "Cf_Rao=%.4f, Cf_MOC=%.4f, L_Rao=%.4f m, L_MOC=%.4f m]",
                    maxRadiusDifference * 1000, avgRadiusDifference * 1000,
                    Math.toDegrees(maxAngleDifference),
                    raoThrustCoefficient, mocThrustCoefficient,
                    raoLength, mocLength
            );
        }
    }
}
