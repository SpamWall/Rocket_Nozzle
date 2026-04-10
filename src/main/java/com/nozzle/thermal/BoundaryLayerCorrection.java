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

package com.nozzle.thermal;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.geometry.Point2D;
import com.nozzle.moc.CharacteristicPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculates boundary layer effects on nozzle performance.
 * Implements displacement thickness correction for area ratio
 * and thrust coefficient adjustments.
 */
public class BoundaryLayerCorrection {
    
    private final NozzleDesignParameters parameters;
    private final NozzleContour contour;
    private final List<BoundaryLayerPoint> blProfile;
    
    // Boundary layer parameters
    private double turbulentTransitionRe = 5e5;
    private boolean forceTurbulent = true;
    
    /**
     * Creates a boundary layer correction model.
     *
     * @param parameters Design parameters
     * @param contour    Nozzle contour
     */
    public BoundaryLayerCorrection(NozzleDesignParameters parameters, NozzleContour contour) {
        this.parameters = parameters;
        this.contour = contour;
        this.blProfile = new ArrayList<>();
    }
    
    /**
     * Sets transition Reynolds number.
     *
     * @param re Transition Reynolds number
     * @return This instance
     */
    public BoundaryLayerCorrection setTransitionReynolds(double re) {
        this.turbulentTransitionRe = re;
        return this;
    }
    
    /**
     * Forces turbulent boundary layer throughout.
     *
     * @param turbulent True to force turbulent
     * @return This instance
     */
    public BoundaryLayerCorrection setForceTurbulent(boolean turbulent) {
        this.forceTurbulent = turbulent;
        return this;
    }
    
    /**
     * Calculates boundary layer along nozzle wall.
     *
     * @param flowPoints Flow field points
     * @return This instance
     */
    public BoundaryLayerCorrection calculate(List<CharacteristicPoint> flowPoints) {
        blProfile.clear();
        
        List<Point2D> contourPoints = contour.getContourPoints();
        if (contourPoints.isEmpty()) {
            contour.generate(100);
            contourPoints = contour.getContourPoints();
        }
        
        GasProperties gas = parameters.gasProperties();
        double gamma = gas.gamma();
        
        // Running length from throat
        double runningLength = 0;
        Point2D prevPoint = contourPoints.getFirst();
        
        for (int i = 0; i < contourPoints.size(); i++) {
            Point2D point = contourPoints.get(i);
            
            if (i > 0) {
                runningLength += prevPoint.distanceTo(point);
            }
            prevPoint = point;
            
            // Find local flow conditions
            CharacteristicPoint flow = findNearestFlowPoint(point, flowPoints);
            
            double mach = flow != null ? flow.mach() : estimateMach(point.x());
            double temp = flow != null ? flow.temperature() 
                    : parameters.chamberTemperature() * gas.isentropicTemperatureRatio(mach);
            double pressure = flow != null ? flow.pressure()
                    : parameters.chamberPressure() * gas.isentropicPressureRatio(mach);
            double velocity = flow != null ? flow.velocity() : mach * gas.speedOfSound(temp);
            
            double density = pressure / (gas.gasConstant() * temp);
            double viscosity = gas.calculateViscosity(temp);
            
            // Local Reynolds number
            double Re = density * velocity * runningLength / viscosity;
            
            // Determine if turbulent
            boolean isTurbulent = forceTurbulent || Re > turbulentTransitionRe;
            
            // Calculate boundary layer thickness
            double delta, deltaStar, theta;
            
            if (isTurbulent) {
                // Turbulent boundary layer (1/7 power law)
                delta = 0.37 * runningLength / Math.pow(Re, 0.2);
                deltaStar = delta / 8.0; // Displacement thickness
                theta = 7.0 / 72.0 * delta; // Momentum thickness
            } else {
                // Laminar boundary layer (Blasius)
                delta = 5.0 * runningLength / Math.sqrt(Re + 1);
                deltaStar = 1.72 * runningLength / Math.sqrt(Re + 1);
                theta = 0.664 * runningLength / Math.sqrt(Re + 1);
            }
            
            // Compressibility correction (Van Driest II)
            double compCorrection = 1.0 + 0.2 * (gamma - 1) * mach * mach;
            delta *= compCorrection;
            deltaStar *= compCorrection;
            theta *= compCorrection;
            
            // Skin friction coefficient
            double cf;
            if (isTurbulent) {
                cf = 0.074 / Math.pow(Re, 0.2);
            } else {
                cf = 1.328 / Math.sqrt(Re + 1);
            }
            
            // Compressibility correction for skin friction
            double Tw_Taw = 0.9; // Wall to adiabatic wall temp ratio
            cf *= Math.pow(Tw_Taw, 0.5);
            
            BoundaryLayerPoint blPoint = new BoundaryLayerPoint(
                    point.x(), point.y(), runningLength, Re,
                    delta, deltaStar, theta, cf, isTurbulent, mach
            );
            blProfile.add(blPoint);
        }
        
        return this;
    }
    
    /**
     * Estimates the local Mach number at axial position {@code x} when no
     * MOC flow-field point is available nearby.
     *
     * <ul>
     *   <li><b>Divergent section (x ≥ 0):</b> linear interpolation from
     *       Mach 1 at the throat to the design exit Mach.</li>
     *   <li><b>Convergent section (x &lt; 0):</b> subsonic Mach from the
     *       isentropic area–velocity relation using the local wall radius
     *       interpolated from the contour spline.  Solved by bisection on
     *       A/A*(M) in M ∈ [0.001, 1].</li>
     * </ul>
     *
     * @param x Axial position in metres
     * @return Estimated local Mach number ≥ 0
     */
    private double estimateMach(double x) {
        if (x < 0) {
            return estimateSubsonicMach(x);
        }
        double re       = parameters.exitRadius();
        double rt       = parameters.throatRadius();
        double exitMach = parameters.exitMach();

        double length = contour.getLength();
        if (length <= 0) length = (re - rt) / Math.tan(parameters.wallAngleInitial());

        double fraction = Math.clamp(x / length, 0.0, 1.0);
        return 1.0 + fraction * (exitMach - 1.0);
    }

    /**
     * Returns the subsonic isentropic Mach number at the given axial position
     * in the convergent section, derived from the local wall radius via bisection
     * on the isentropic area–Mach relation A/A*(M).
     *
     * @param x Axial position (must be &lt; 0)
     * @return Subsonic Mach number in (0, 1]
     */
    private double estimateSubsonicMach(double x) {
        double rt = parameters.throatRadius();
        double r;
        try {
            r = contour.getRadiusAt(x);
        } catch (Exception e) {
            // Contour spline not defined at x; fall back to chamber M≈0 estimate
            return 0.1;
        }
        double aRatio = (r / rt) * (r / rt);   // A/A* at this position
        if (aRatio <= 1.0) return 1.0;          // at or past throat

        // Bisection on isentropic area-Mach relation for the subsonic root
        var gas = parameters.gasProperties();
        double lo = 0.001, hi = 1.0;
        for (int iter = 0; iter < 60; iter++) {
            double mid = 0.5 * (lo + hi);
            if (gas.areaRatio(mid) > aRatio) lo = mid;
            else hi = mid;
        }
        return 0.5 * (lo + hi);
    }
    
    /**
     * Finds the characteristic flow point nearest to a given wall location using
     * the minimum Euclidean distance in the (x, y) plane.
     *
     * @param wallPoint  Wall contour point whose local flow conditions are sought
     * @param flowPoints Candidate flow-field points from the MOC solution; may be
     *                   {@code null} or empty
     * @return The nearest {@link CharacteristicPoint}, or {@code null} if
     *         {@code flowPoints} is {@code null} or empty
     */
    private CharacteristicPoint findNearestFlowPoint(Point2D wallPoint,
                                                      List<CharacteristicPoint> flowPoints) {
        if (flowPoints == null || flowPoints.isEmpty()) return null;
        
        CharacteristicPoint nearest = null;
        double minDist = Double.MAX_VALUE;
        
        for (CharacteristicPoint fp : flowPoints) {
            double dx = fp.x() - wallPoint.x();
            double dy = fp.y() - wallPoint.y();
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist < minDist) {
                minDist = dist;
                nearest = fp;
            }
        }
        
        return nearest;
    }
    
    /**
     * Calculates effective area ratio with boundary layer correction.
     *
     * @return Corrected area ratio
     */
    public double getCorrectedAreaRatio() {
        if (blProfile.isEmpty()) return parameters.exitAreaRatio();
        
        double rt = parameters.throatRadius();
        BoundaryLayerPoint exitBL = blProfile.getLast();
        
        // Effective radius reduced by displacement thickness
        double rEffective = exitBL.y() - exitBL.displacementThickness();
        
        return (rEffective * rEffective) / (rt * rt);
    }
    
    /**
     * Calculates thrust coefficient loss due to boundary layer.
     *
     * @return Thrust coefficient loss (as a positive value to subtract)
     */
    public double getThrustCoefficientLoss() {
        if (blProfile.isEmpty()) return 0;
        
        // Integrate skin friction drag
        double dragCoeff = 0;
        List<Point2D> contourPoints = contour.getContourPoints();
        
        for (int i = 1; i < blProfile.size() && i < contourPoints.size(); i++) {
            BoundaryLayerPoint prev = blProfile.get(i - 1);
            BoundaryLayerPoint curr = blProfile.get(i);
            Point2D p1 = contourPoints.get(i - 1);
            Point2D p2 = contourPoints.get(i);
            
            double ds = p1.distanceTo(p2);
            double rAvg = (p1.y() + p2.y()) / 2;
            double cfAvg = (prev.skinFrictionCoeff() + curr.skinFrictionCoeff()) / 2;
            
            // Surface area element
            double dA = 2 * Math.PI * rAvg * ds;
            
            // Add to drag coefficient (normalized by throat area)
            double At = Math.PI * parameters.throatRadius() * parameters.throatRadius();
            dragCoeff += cfAvg * dA / At;
        }
        
        return dragCoeff;
    }
    
    /**
     * Gets the boundary layer profile.
     *
     * @return List of boundary layer points
     */
    public List<BoundaryLayerPoint> getBoundaryLayerProfile() {
        return new ArrayList<>(blProfile);
    }
    
    /**
     * Gets displacement thickness at exit.
     *
     * @return Exit displacement thickness in m
     */
    public double getExitDisplacementThickness() {
        if (blProfile.isEmpty()) return 0;
        return blProfile.getLast().displacementThickness();
    }
    
    /**
     * Gets momentum thickness at exit.
     *
     * @return Exit momentum thickness in m
     */
    public double getExitMomentumThickness() {
        if (blProfile.isEmpty()) return 0;
        return blProfile.getLast().momentumThickness();
    }
    
    /**
     * Immutable snapshot of boundary-layer quantities at a single wall point.
     *
     * @param x                     Axial position in metres (from contour origin)
     * @param y                     Radial position (wall radius) in metres
     * @param runningLength         Integrated wall arc length from the first contour
     *                              point to this point, used as the reference length
     *                              for boundary-layer scaling (metres)
     * @param reynoldsNumber        Local running-length Reynolds number
     *                              {@code Re = ρ · V · s / μ} (dimensionless)
     * @param thickness             Full boundary-layer thickness δ in metres, including
     *                              the Van Driest compressibility correction
     * @param displacementThickness Displacement thickness δ* in metres
     *                              (δ/8 turbulent, 1.72·s/√Re laminar)
     * @param momentumThickness     Momentum thickness θ in metres
     *                              (7/72·δ turbulent, 0.664·s/√Re laminar)
     * @param skinFrictionCoeff     Local skin-friction coefficient cf (dimensionless),
     *                              corrected for the wall-to-adiabatic-wall temperature ratio
     * @param isTurbulent           {@code true} if the boundary layer is turbulent at this point
     *                              (either forced turbulent or Re exceeds transition threshold)
     * @param mach                  Local Mach number used for the compressibility correction
     */
    public record BoundaryLayerPoint(
            double x,
            double y,
            double runningLength,
            double reynoldsNumber,
            double thickness,
            double displacementThickness,
            double momentumThickness,
            double skinFrictionCoeff,
            boolean isTurbulent,
            double mach
    ) {
        /**
         * Returns the incompressible shape factor {@code H = δ* / θ}.
         * Typical values: ~1.3 turbulent, ~2.6 laminar.
         *
         * @return Shape factor; returns {@code 1.4} if momentum thickness is zero
         */
        public double shapeFactor() {
            return momentumThickness > 0 ? displacementThickness / momentumThickness : 1.4;
        }
        
        @SuppressWarnings("NullableProblems")
        @Override
        public String toString() {
            return String.format("BL[x=%.4f, δ*=%.2e, cf=%.4f, %s]",
                    x, displacementThickness, skinFrictionCoeff,
                    isTurbulent ? "turbulent" : "laminar");
        }
    }
}
