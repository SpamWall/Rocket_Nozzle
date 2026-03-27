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
import java.util.Comparator;
import java.util.List;

/**
 * Models heat transfer in rocket nozzles including convective
 * and radiative heat transfer, with wall temperature prediction.
 */
public class HeatTransferModel {
    
    private final NozzleDesignParameters parameters;
    private final NozzleContour contour;
    private final List<WallThermalPoint> wallThermalProfile;
    
    // Material properties
    private double wallThermalConductivity = 20.0; // W/(m·K) - typical for Inconel
    private double wallThickness = 0.003; // m
    private double coolantTemperature = 300.0; // K
    private double coolantHeatTransferCoeff = 5000.0; // W/(m²·K)
    
    // Optional position-varying coolant channel (overrides coolantHeatTransferCoeff when set)
    private CoolantChannel coolantChannel = null;

    // Radiation properties
    private double wallEmissivity = 0.8;
    /** Stefan–Boltzmann constant (5.67 × 10⁻⁸ W·m⁻²·K⁻⁴). */
    private static final double STEFAN_BOLTZMANN = 5.67e-8; // W/(m²·K⁴)
    
    /**
     * Creates a heat transfer model.
     *
     * @param parameters Design parameters
     * @param contour    Nozzle contour
     */
    public HeatTransferModel(NozzleDesignParameters parameters, NozzleContour contour) {
        this.parameters = parameters;
        this.contour = contour;
        this.wallThermalProfile = new ArrayList<>();
    }
    
    /**
     * Sets wall material properties.
     *
     * @param conductivity Thermal conductivity in W/(m·K)
     * @param thickness    Wall thickness in m
     * @return This instance
     */
    public HeatTransferModel setWallProperties(double conductivity, double thickness) {
        this.wallThermalConductivity = conductivity;
        this.wallThickness = thickness;
        return this;
    }
    
    /**
     * Sets coolant properties.
     *
     * @param temperature   Coolant temperature in K
     * @param heatTransCoeff Heat transfer coefficient in W/(m²·K)
     * @return This instance
     */
    public HeatTransferModel setCoolantProperties(double temperature, double heatTransCoeff) {
        this.coolantTemperature = temperature;
        this.coolantHeatTransferCoeff = heatTransCoeff;
        return this;
    }
    
    /**
     * Configures a coolant channel model whose position-varying heat transfer
     * coefficient replaces the fixed {@code coolantHeatTransferCoeff} scalar.
     * Call {@link CoolantChannel#calculate()} before passing the channel here.
     *
     * @param channel Sized coolant channel
     * @return This instance
     */
    public HeatTransferModel setCoolantChannel(CoolantChannel channel) {
        this.coolantChannel = channel;
        return this;
    }

    /**
     * Sets radiation emissivity.
     *
     * @param emissivity Wall emissivity (0-1)
     * @return This instance
     */
    public HeatTransferModel setEmissivity(double emissivity) {
        this.wallEmissivity = emissivity;
        return this;
    }
    
    /**
     * Calculates wall thermal profile along the nozzle.
     *
     * <p>When {@code flowPoints} is non-empty a {@link KdTree} is built once in
     * O(n log n) and each contour-point lookup runs in O(log n), replacing the
     * previous O(n×m) linear scan.
     *
     * @param flowPoints Flow field points for local conditions
     * @return This instance
     */
    public HeatTransferModel calculate(List<CharacteristicPoint> flowPoints) {
        wallThermalProfile.clear();

        List<Point2D> contourPoints = contour.getContourPoints();
        if (contourPoints.isEmpty()) {
            contour.generate(100);
            contourPoints = contour.getContourPoints();
        }

        KdTree kdTree = (flowPoints != null && !flowPoints.isEmpty())
                ? KdTree.build(flowPoints, 0) : null;

        GasProperties gas = parameters.gasProperties();
        double gamma = gas.gamma();
        double Pr = 4.0 * gamma / (9.0 * gamma - 5.0);         // Eucken relation
        double recoveryFactor = Math.pow(Pr, 1.0 / 3.0);       // turbulent boundary layer

        for (Point2D point : contourPoints) {
            CharacteristicPoint nearestFlow = (kdTree != null)
                    ? kdTree.nearest(point.x(), point.y()) : null;

            // Local conditions
            double gasTemp = nearestFlow != null ? nearestFlow.temperature()
                    : parameters.chamberTemperature() * 0.8;
            double mach = nearestFlow != null ? nearestFlow.mach() : 2.0;

            // Adiabatic wall (recovery) temperature
            double recoveryTemp = gasTemp * (1.0 + recoveryFactor * (gamma - 1.0) / 2.0 * mach * mach);

            // Local coolant-side h: from channel profile if available, else fixed scalar
            double hCoolant = (coolantChannel != null && !coolantChannel.getProfile().isEmpty())
                    ? coolantChannel.getHeatTransferCoeffAt(point.x())
                    : coolantHeatTransferCoeff;

            // Calculate convective heat transfer coefficient using Bartz equation
            double hGas = calculateBartzHeatTransfer(point.x(), point.y(), gasTemp, recoveryTemp, hCoolant);

            // Calculate wall temperature (steady state)
            double wallTemp = calculateWallTemperature(recoveryTemp, hGas, hCoolant);
            
            // Calculate heat flux
            double qConv = hGas * (recoveryTemp - wallTemp);
            double qRad = wallEmissivity * STEFAN_BOLTZMANN * Math.pow(wallTemp, 4);
            double qTotal = qConv - qRad;
            
            WallThermalPoint thermalPoint = new WallThermalPoint(
                    point.x(), point.y(), wallTemp, qTotal, qConv, qRad, hGas, recoveryTemp
            );
            wallThermalProfile.add(thermalPoint);
        }
        
        return this;
    }

    /**
     * 2-D k-d tree for O(log n) nearest-neighbor queries over a set of
     * {@link CharacteristicPoint}s in the (x, y) plane.
     *
     * <p>Build cost is O(n log n); each query cost is O(log n) expected.
     * Axis alternates between x (depth even) and y (depth odd).
     */
        private record KdTree(CharacteristicPoint point, KdTree left, KdTree right, int axis) {

        /**
         * Builds a balanced k-d tree from {@code pts} by recursively
         * median-partitioning on alternating axes.
         *
         * @param pts   points to index (copied internally; original list is unchanged)
         * @param depth current recursion depth (0 for the root)
         * @return root node of the built tree, or {@code null} if {@code pts} is empty
         */
            static KdTree build(List<CharacteristicPoint> pts, int depth) {
                if (pts.isEmpty()) return null;
                int axis = depth % 2;
                List<CharacteristicPoint> sorted = new ArrayList<>(pts);
                sorted.sort(axis == 0
                      ? Comparator.comparingDouble(CharacteristicPoint::x)
                      : Comparator.comparingDouble(CharacteristicPoint::y));
                int mid = sorted.size() / 2;
                return new KdTree(
                      sorted.get(mid),
                      build(sorted.subList(0, mid), depth + 1),
                      build(sorted.subList(mid + 1, sorted.size()), depth + 1),
                      axis);
            }

            /**
             * Returns the point in this tree closest to {@code (qx, qy)}.
             *
             * @param qx query x-coordinate
             * @param qy query y-coordinate
             * @return nearest {@link CharacteristicPoint}
             */
            CharacteristicPoint nearest(double qx, double qy) {
                return search(qx, qy, this, null, Double.MAX_VALUE);
            }

            private static CharacteristicPoint search(double qx, double qy, KdTree node,
                                                      CharacteristicPoint best, double bestDistSq) {
                if (node == null) return best;

                double dx = node.point.x() - qx;
                double dy = node.point.y() - qy;
                double distSq = dx * dx + dy * dy;
                if (distSq < bestDistSq) {
                    best = node.point;
                    bestDistSq = distSq;
                }

                double axisDiff = node.axis == 0 ? qx - node.point.x() : qy - node.point.y();
                KdTree near = axisDiff <= 0 ? node.left : node.right;
                KdTree far = axisDiff <= 0 ? node.right : node.left;

                best = search(qx, qy, near, best, bestDistSq);
                bestDistSq = dist2(best, qx, qy);

                if (axisDiff * axisDiff < bestDistSq) {
                    best = search(qx, qy, far, best, bestDistSq);
                }

                return best;
            }

            private static double dist2(CharacteristicPoint p, double qx, double qy) {
                if (p == null) return Double.MAX_VALUE;
                double dx = p.x() - qx;
                double dy = p.y() - qy;
                return dx * dx + dy * dy;
            }
        }
    
    /**
     * Calculates convective heat transfer coefficient using the Bartz correlation
     * with the Eckert reference temperature method. Gas properties (viscosity, Cp)
     * are evaluated at the Eckert reference temperature T* rather than fixed throat
     * conditions, giving more accurate results across the full Mach range.
     * An iterative loop resolves the circular dependency between T*, h, and T_w.
     * The curvature correction term uses the local wall radius of curvature derived
     * from the nozzle contour at axial position x.
     *
     * @param x        Axial position (m)
     * @param r        Local wall radius (m)
     * @param temp     Local static gas temperature (K)
     * @param T_aw     Adiabatic wall (recovery) temperature (K)
     * @param hCoolant Coolant-side heat transfer coefficient W/(m²·K)
     * @return Gas-side heat transfer coefficient in W/(m²·K)
     */
    private double calculateBartzHeatTransfer(double x, double r, double temp, double T_aw,
                                               double hCoolant) {
        GasProperties gas = parameters.gasProperties();
        double gamma = gas.gamma();
        double Pr = 4.0 * gamma / (9.0 * gamma - 5.0);  // Eucken relation

        double rt = parameters.throatRadius();
        double At = Math.PI * rt * rt;
        double Dt = 2.0 * rt;
        double A = Math.PI * r * r;

        // Mass flux at throat: G* = ṁ/A_t = P_c / c*
        double G_Star = parameters.chamberPressure() / parameters.characteristicVelocity();

        // Local wall radius of curvature for the (D_t/r_c)^0.1 correction
        double r_c = localRadiusOfCurvature(x);

        // Iterative convergence: T* depends on T_w, T_w depends on h, h depends on T*
        // Typically converges within 2–3 iterations
        double T_w = 0.9 * T_aw;  // initial estimate (typical cooled-wall ratio)
        double h = 0.0;
        for (int i = 0; i < 3; i++) {
            // Eckert reference temperature (Eckert 1955)
            double T_star = 0.5 * (T_w + temp) + 0.22 * Math.sqrt(Pr) * (T_aw - temp);

            double mu_star = gas.calculateViscosity(T_star);
            double cp_star = gas.specificHeatCp();

            h = (0.026 / Math.pow(Dt, 0.2))
                    * (Math.pow(mu_star, 0.2) * cp_star / Math.pow(Pr, 0.6))
                    * Math.pow(G_Star, 0.8)
                    * Math.pow(Dt / r_c, 0.1)
                    * Math.pow(At / A, 0.9);

            T_w = calculateWallTemperature(T_aw, h, hCoolant);
        }
        return h;
    }

    /**
     * Estimates the local wall radius of curvature at axial position {@code x} using
     * non-uniform second-order finite differences on the discrete contour points.
     * Falls back to the throat radius if the contour contains fewer than three
     * points or if the neighbouring knots are too closely spaced ({@code h < 1 pm}).
     * The result is capped at {@code 10 × r_throat} to prevent the Bartz curvature
     * correction from vanishing on near-straight wall sections.
     *
     * @param x Axial position in metres
     * @return Local radius of curvature in metres
     */
    private double localRadiusOfCurvature(double x) {
        List<Point2D> pts = contour.getContourPoints();
        if (pts.size() < 3) {
            return parameters.throatRadius();
        }

        // Find the index of the contour point nearest to x
        int idx = 1;
        double minDx = Double.MAX_VALUE;
        for (int i = 0; i < pts.size(); i++) {
            double dx = Math.abs(pts.get(i).x() - x);
            if (dx < minDx) {
                minDx = dx;
                idx = i;
            }
        }
        idx = Math.max(1, Math.min(idx, pts.size() - 2));

        double x0 = pts.get(idx - 1).x(), y0 = pts.get(idx - 1).y();
        double x1 = pts.get(idx).x(),     y1 = pts.get(idx).y();
        double x2 = pts.get(idx + 1).x(), y2 = pts.get(idx + 1).y();

        double h1 = x1 - x0;
        double h2 = x2 - x1;
        if (h1 < 1e-12 || h2 < 1e-12) {
            return parameters.throatRadius();
        }

        // Non-uniform finite differences
        double dydx   = (y2 - y0) / (x2 - x0);
        double d2ydx2 = 2.0 * (h1 * y2 - (h1 + h2) * y1 + h2 * y0)
                            / (h1 * h2 * (h1 + h2));

        if (Math.abs(d2ydx2) < 1e-10) {
            // Near-straight wall: cap so the correction term stays bounded
            return 10.0 * parameters.throatRadius();
        }

        double r_c = Math.pow(1.0 + dydx * dydx, 1.5) / Math.abs(d2ydx2);
        // Cap at 10*rt to prevent the correction term from vanishing in straight sections
        return Math.min(r_c, 10.0 * parameters.throatRadius());
    }
    
    /**
     * Calculates the steady-state gas-side wall temperature using a three-element
     * thermal-resistance network: gas film → wall conduction → coolant film.
     *
     * <p>The heat flux is {@code q = (T_aw − T_coolant) / R_total} and the
     * gas-side wall temperature is {@code T_w = T_aw − q / h_gas}.
     * The result is clamped between {@code T_coolant + 50 K} and
     * {@code T_aw − 100 K} to remain physically plausible.
     *
     * @param recoveryTemp Adiabatic wall (recovery) temperature in K
     * @param hGas         Gas-side heat-transfer coefficient in W/(m²·K)
     * @param hCoolant     Coolant-side heat-transfer coefficient in W/(m²·K)
     * @return Gas-side wall temperature in K
     */
    private double calculateWallTemperature(double recoveryTemp, double hGas, double hCoolant) {
        // Thermal resistance network
        double Rgas     = 1.0 / hGas;
        double Rwall    = wallThickness / wallThermalConductivity;
        double Rcoolant = 1.0 / hCoolant;
        double Rtotal = Rgas + Rwall + Rcoolant;
        
        // Wall temperature (gas side)
        double qFlux = (recoveryTemp - coolantTemperature) / Rtotal;
        double wallTemp = recoveryTemp - qFlux * Rgas;
        
        // Limit to reasonable range
        wallTemp = Math.max(wallTemp, coolantTemperature + 50);
        wallTemp = Math.min(wallTemp, recoveryTemp - 100);
        
        return wallTemp;
    }
    
    /**
     * Gets the wall thermal profile.
     *
     * @return List of thermal points
     */
    public List<WallThermalPoint> getWallThermalProfile() {
        return new ArrayList<>(wallThermalProfile);
    }
    
    /**
     * Gets the maximum wall temperature.
     *
     * @return Maximum wall temperature in K
     */
    public double getMaxWallTemperature() {
        return wallThermalProfile.stream()
                .mapToDouble(WallThermalPoint::wallTemperature)
                .max()
                .orElse(0);
    }
    
    /**
     * Gets the maximum heat flux.
     *
     * @return Maximum heat flux in W/m²
     */
    public double getMaxHeatFlux() {
        return wallThermalProfile.stream()
                .mapToDouble(WallThermalPoint::totalHeatFlux)
                .max()
                .orElse(0);
    }
    
    /**
     * Calculates total heat load.
     *
     * @return Total heat load in W
     */
    public double getTotalHeatLoad() {
        double totalHeat = 0;
        List<Point2D> contourPoints = contour.getContourPoints();
        
        for (int i = 1; i < wallThermalProfile.size() && i < contourPoints.size(); i++) {
            WallThermalPoint prev = wallThermalProfile.get(i - 1);
            WallThermalPoint curr = wallThermalProfile.get(i);
            Point2D p1 = contourPoints.get(i - 1);
            Point2D p2 = contourPoints.get(i);
            
            double ds = p1.distanceTo(p2);
            double rAvg = (p1.y() + p2.y()) / 2;
            double qAvg = (prev.totalHeatFlux() + curr.totalHeatFlux()) / 2;
            
            // Surface area element (axisymmetric)
            double dA = 2 * Math.PI * rAvg * ds;
            totalHeat += qAvg * dA;
        }
        
        return totalHeat;
    }
    
    /**
     * Immutable snapshot of all thermal quantities at a single point on the nozzle wall.
     *
     * @param x                   Axial position in metres (measured from the throat)
     * @param y                   Radial position (wall radius) in metres
     * @param wallTemperature     Gas-side wall temperature in K, computed by the
     *                            three-resistance network (gas film + wall + coolant)
     * @param totalHeatFlux       Net heat flux into the wall in W/m²:
     *                            {@code q_conv − q_rad}; positive values represent
     *                            net heating of the wall
     * @param convectiveHeatFlux  Gas-side convective heat flux in W/m²:
     *                            {@code h_gas × (T_aw − T_wall)}
     * @param radiativeHeatFlux   Radiative heat loss from the outer wall surface in
     *                            W/m²: {@code ε · σ · T_wall⁴}
     * @param heatTransferCoeff   Gas-side heat-transfer coefficient h in W/(m²·K)
     *                            as computed by the Bartz correlation
     * @param recoveryTemperature Adiabatic wall (recovery) temperature in K:
     *                            {@code T_gas × (1 + r·(γ−1)/2 · M²)}, where
     *                            {@code r = Pr^(1/3)} for turbulent flow
     */
    public record WallThermalPoint(
            double x,
            double y,
            double wallTemperature,
            double totalHeatFlux,
            double convectiveHeatFlux,
            double radiativeHeatFlux,
            double heatTransferCoeff,
            double recoveryTemperature
    ) {
        @SuppressWarnings("NullableProblems")
        @Override
        public String toString() {
            return String.format("ThermalPoint[x=%.4f, Tw=%.1f K, q=%.2e W/m²]",
                    x, wallTemperature, totalHeatFlux);
        }
    }
}
