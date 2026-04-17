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
 *  commercial purposes outside the restrictions induced by this copyright.
 */

package com.nozzle.thermal;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.geometry.FullNozzleGeometry;
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
     * Calculates boundary layer starting from the injector face (chamber inlet)
     * and integrating through both the convergent and divergent sections.
     *
     * <p>Starting the boundary layer at the injector face rather than the throat
     * produces a physically correct running-length at the throat.  The accumulated
     * displacement thickness at x = 0 reduces the effective throat area and
     * supplements the geometric Cd correction from sonic-line curvature:
     *
     * <pre>
     *   r_eff_throat = r_t − δ*(x=0)
     *   Cd_BL        = (r_eff_throat / r_t)²
     *   Cd_total     = Cd_geometric × Cd_BL
     * </pre>
     *
     * @param fullGeometry Full nozzle geometry (must have been generated)
     * @param flowPoints   MOC flow-field points for local conditions; may be
     *                     {@code null} or empty to use the isentropic estimate
     * @return This instance for method chaining
     * @throws IllegalStateException if {@code fullGeometry} has not been generated
     */
    public BoundaryLayerCorrection calculateFromInjectorFace(
            FullNozzleGeometry fullGeometry,
            List<CharacteristicPoint> flowPoints) {

        if (!fullGeometry.isGenerated()) {
            throw new IllegalStateException(
                    "FullNozzleGeometry must be generated before calling calculateFromInjectorFace");
        }

        blProfile.clear();

        GasProperties gas = parameters.gasProperties();
        double gamma = gas.gamma();

        List<Point2D> allPoints = fullGeometry.getWallPoints();
        if (allPoints.isEmpty()) return this;

        double runningLength = 0.0;
        Point2D prevPoint = allPoints.getFirst();

        for (int i = 0; i < allPoints.size(); i++) {
            Point2D point = allPoints.get(i);

            if (i > 0) {
                runningLength += prevPoint.distanceTo(point);
            }
            prevPoint = point;

            CharacteristicPoint flow = findNearestFlowPoint(point, flowPoints);

            double mach, temp, pressure, velocity;
            if (flow != null) {
                mach     = flow.mach();
                temp     = flow.temperature();
                pressure = flow.pressure();
                velocity = flow.velocity();
            } else {
                mach     = estimateMachFromFullGeometry(fullGeometry, point.x(), point.y());
                temp     = parameters.chamberTemperature() * gas.isentropicTemperatureRatio(mach);
                pressure = parameters.chamberPressure()    * gas.isentropicPressureRatio(mach);
                velocity = mach * gas.speedOfSound(temp);
            }

            double density   = pressure / (gas.gasConstant() * temp);
            double viscosity = gas.calculateViscosity(temp);
            double Re = runningLength > 0 ? density * velocity * runningLength / viscosity : 0.0;
            boolean isTurbulent = forceTurbulent || Re > turbulentTransitionRe;

            blProfile.add(computeBlKernel(point.x(), point.y(), runningLength,
                    Re, isTurbulent, gamma, mach));
        }

        return this;
    }

    /**
     * Returns the displacement thickness δ* at the throat (x ≈ 0), computed by
     * {@link #calculateFromInjectorFace}.  Used to correct the effective throat
     * area for boundary-layer displacement.
     *
     * @return δ* at throat in metres; 0 if no profile has been calculated
     */
    public double getThroatDisplacementThickness() {
        if (blProfile.isEmpty()) return 0.0;
        double rt = parameters.throatRadius();
        double tolerance = rt * 0.2;
        BoundaryLayerPoint nearest = null;
        double minDist = Double.MAX_VALUE;
        for (BoundaryLayerPoint p : blProfile) {
            double d = Math.abs(p.x());
            if (d < minDist) {
                minDist = d;
                nearest = p;
            }
            if (d < tolerance) break;
        }
        return nearest != null ? nearest.displacementThickness() : 0.0;
    }

    /**
     * Returns the boundary-layer discharge-coefficient correction factor.
     *
     * <pre>
     *   Cd_BL = (1 − δ*(x=0) / r_t)²
     * </pre>
     *
     * @return Cd_BL ∈ (0, 1]; 1.0 if no throat BL data is available
     */
    public double getBoundaryLayerCdCorrection() {
        double deltaStar = getThroatDisplacementThickness();
        if (deltaStar <= 0.0) return 1.0;
        double rt   = parameters.throatRadius();
        double rEff = Math.max(rt - deltaStar, rt * 0.5);
        return (rEff / rt) * (rEff / rt);
    }

    /**
     * Returns the combined discharge coefficient including both the geometric
     * sonic-line curvature correction and the boundary-layer correction:
     *
     * <pre>
     *   Cd_total = Cd_geometric × Cd_BL
     * </pre>
     *
     * <p>Only meaningful after {@link #calculateFromInjectorFace} has been called.
     *
     * @return Combined Cd ∈ (0, 1]
     */
    public double getCombinedCd() {
        return parameters.dischargeCoefficient() * getBoundaryLayerCdCorrection();
    }

    /**
     * Estimates the local Mach number from the local wall radius for a point
     * anywhere in the full nozzle (convergent or divergent).
     *
     * @param geom Full nozzle geometry
     * @param x    Axial position in metres
     * @param y    Radial wall position in metres
     * @return Estimated Mach number ≥ 0
     */
    private double estimateMachFromFullGeometry(FullNozzleGeometry geom,
                                                double x, double y) {
        double rt     = geom.getThroatRadius();
        double aRatio = (y / rt) * (y / rt);
        GasProperties gas = parameters.gasProperties();
        if (x >= 0.0) {
            return gas.machFromAreaRatio(Math.max(1.0, aRatio));
        }
        // Convergent: subsonic bisection
        if (aRatio <= 1.0) return 1.0;
        double lo = 0.001, hi = 1.0;
        for (int iter = 0; iter < 60; iter++) {
            double mid = 0.5 * (lo + hi);
            if (gas.areaRatio(mid) > aRatio) lo = mid;
            else hi = mid;
        }
        return 0.5 * (lo + hi);
    }

    /**
     * Calculates boundary layer along the divergent nozzle wall only.
     * The running length starts from the first contour point of the divergent
     * section; use {@link #calculateFromInjectorFace} for the physically correct
     * full-nozzle calculation.
     *
     * @param flowPoints Flow field points from MOC solution
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

        double runningLength = 0;
        Point2D prevPoint = contourPoints.getFirst();

        for (int i = 0; i < contourPoints.size(); i++) {
            Point2D point = contourPoints.get(i);

            if (i > 0) {
                runningLength += prevPoint.distanceTo(point);
            }
            prevPoint = point;

            CharacteristicPoint flow = findNearestFlowPoint(point, flowPoints);

            double mach = flow != null ? flow.mach() : estimateMach(point.x());
            double temp = flow != null ? flow.temperature()
                    : parameters.chamberTemperature() * gas.isentropicTemperatureRatio(mach);
            double pressure = flow != null ? flow.pressure()
                    : parameters.chamberPressure() * gas.isentropicPressureRatio(mach);
            double velocity = flow != null ? flow.velocity() : mach * gas.speedOfSound(temp);

            double density   = pressure / (gas.gasConstant() * temp);
            double viscosity = gas.calculateViscosity(temp);
            double Re = density * velocity * runningLength / viscosity;
            boolean isTurbulent = forceTurbulent || Re > turbulentTransitionRe;

            blProfile.add(computeBlKernel(point.x(), point.y(), runningLength,
                    Re, isTurbulent, gamma, mach));
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
            return 0.1;
        }
        double aRatio = (r / rt) * (r / rt);
        if (aRatio <= 1.0) return 1.0;

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
     * Computes boundary-layer thicknesses and skin friction at a single wall point.
     * Applies the 1/7-power-law (turbulent) or Blasius (laminar) flat-plate
     * correlations with a Van Driest compressibility correction and a Tw/Taw = 0.9
     * wall-temperature skin-friction factor.
     *
     * @param x             Axial position in metres
     * @param y             Radial wall position in metres
     * @param runningLength Wall arc length from the first profile point (metres)
     * @param Re            Local running-length Reynolds number
     * @param isTurbulent   {@code true} for turbulent correlations
     * @param gamma         Specific heat ratio
     * @param mach          Local Mach number
     * @return Computed {@link BoundaryLayerPoint}
     */
    private static BoundaryLayerPoint computeBlKernel(
            double x, double y, double runningLength,
            double Re, boolean isTurbulent, double gamma, double mach) {

        double delta, deltaStar, theta;
        if (runningLength < 1e-12) {
            delta = 0; deltaStar = 0; theta = 0;
        } else if (isTurbulent) {
            delta     = 0.37  * runningLength / Math.pow(Re, 0.2);
            deltaStar = delta / 8.0;
            theta     = 7.0 / 72.0 * delta;
        } else {
            delta     = 5.0   * runningLength / Math.sqrt(Re + 1.0);
            deltaStar = 1.72  * runningLength / Math.sqrt(Re + 1.0);
            theta     = 0.664 * runningLength / Math.sqrt(Re + 1.0);
        }

        double compCorrection = 1.0 + 0.2 * (gamma - 1.0) * mach * mach;
        delta     *= compCorrection;
        deltaStar *= compCorrection;
        theta     *= compCorrection;

        double cf;
        if (runningLength < 1e-12 || Re < 1.0) {
            cf = 0.0;
        } else if (isTurbulent) {
            cf = 0.074 / Math.pow(Re, 0.2);
        } else {
            cf = 1.328 / Math.sqrt(Re + 1.0);
        }
        cf *= 0.9; // Tw/Taw wall-temperature correction

        return new BoundaryLayerPoint(x, y, runningLength, Re,
                delta, deltaStar, theta, cf, isTurbulent, mach);
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

        double dragCoeff = 0;
        List<Point2D> contourPoints = contour.getContourPoints();

        for (int i = 1; i < blProfile.size() && i < contourPoints.size(); i++) {
            BoundaryLayerPoint prev = blProfile.get(i - 1);
            BoundaryLayerPoint curr = blProfile.get(i);
            Point2D p1 = contourPoints.get(i - 1);
            Point2D p2 = contourPoints.get(i);

            double ds   = p1.distanceTo(p2);
            double rAvg = (p1.y() + p2.y()) / 2;
            double cfAvg = (prev.skinFrictionCoeff() + curr.skinFrictionCoeff()) / 2;

            double dA = 2 * Math.PI * rAvg * ds;
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
}
