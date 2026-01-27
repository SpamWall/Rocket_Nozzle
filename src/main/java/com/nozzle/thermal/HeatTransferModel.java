package com.nozzle.thermal;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.geometry.Point2D;
import com.nozzle.moc.CharacteristicPoint;

import java.util.ArrayList;
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
    
    // Radiation properties
    private double wallEmissivity = 0.8;
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
        
        for (Point2D point : contourPoints) {
            // Find nearest flow point
            CharacteristicPoint nearestFlow = findNearestFlowPoint(point, flowPoints);
            
            // Calculate local conditions
            double gasTemp = nearestFlow != null ? nearestFlow.temperature() 
                    : parameters.chamberTemperature() * 0.8;
            double mach = nearestFlow != null ? nearestFlow.mach() : 2.0;
            double pressure = nearestFlow != null ? nearestFlow.pressure() 
                    : parameters.chamberPressure() * 0.1;
            double velocity = nearestFlow != null ? nearestFlow.velocity() : 2000.0;
            
            // Calculate recovery temperature
            double recoveryFactor = Math.pow(parameters.gasProperties().gamma(), 1.0/3.0);
            double recoveryTemp = gasTemp * (1 + recoveryFactor * 
                    (parameters.gasProperties().gamma() - 1) / 2 * mach * mach);
            
            // Calculate convective heat transfer coefficient using Bartz equation
            double hGas = calculateBartzHeatTransfer(point.x(), point.y(), mach, pressure, gasTemp);
            
            // Calculate wall temperature (steady state)
            double wallTemp = calculateWallTemperature(recoveryTemp, hGas);
            
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
     * Finds the nearest flow point to a wall location.
     */
    private CharacteristicPoint findNearestFlowPoint(Point2D wallPoint, 
                                                      List<CharacteristicPoint> flowPoints) {
        if (flowPoints == null || flowPoints.isEmpty()) {
            return null;
        }
        
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
     * Calculates convective heat transfer coefficient using Bartz correlation.
     *
     * @param x        Axial position
     * @param r        Radius
     * @param mach     Local Mach number
     * @param pressure Local pressure
     * @param temp     Local gas temperature
     * @return Heat transfer coefficient in W/(m²·K)
     */
    private double calculateBartzHeatTransfer(double x, double r, double mach, 
                                               double pressure, double temp) {
        GasProperties gas = parameters.gasProperties();
        double gamma = gas.gamma();
        double Pr = 0.72; // Prandtl number (approximate)
        
        // Reference conditions (throat)
        double Pc = parameters.chamberPressure();
        double Tc = parameters.chamberTemperature();
        double rt = parameters.throatRadius();
        double At = Math.PI * rt * rt;
        
        // C* and mass flow rate
        double cStar = parameters.characteristicVelocity();
        double mdot = Pc * At / cStar;
        
        // Throat conditions
        double Dt = 2 * rt;
        double rhoT = Pc / (gas.gasConstant() * Tc) * Math.pow(2.0 / (gamma + 1), 1.0 / (gamma - 1));
        double muT = gas.calculateViscosity(Tc * 2.0 / (gamma + 1));
        double cpT = gas.specificHeatCp();
        
        // Area ratio at current position
        double A = Math.PI * r * r;
        double sigma = calculateSigma(mach, gamma);
        
        // Bartz equation
        double term1 = 0.026 / Math.pow(Dt, 0.2);
        double term2 = Math.pow(muT, 0.2) * cpT / Math.pow(Pr, 0.6);
        double term3 = Math.pow(Pc / cStar, 0.8);
        double term4 = Math.pow(Dt / (rt * 2), 0.1); // Curvature correction
        double term5 = Math.pow(At / A, 0.9);
        
        return term1 * term2 * term3 * term4 * term5 * sigma;
    }
    
    /**
     * Calculates the sigma correction factor for Bartz equation.
     */
    private double calculateSigma(double mach, double gamma) {
        double gm1 = gamma - 1;
        double gp1 = gamma + 1;
        
        double Tw_Tc = 0.5; // Approximate wall/chamber temp ratio
        double term1 = 0.5 * Tw_Tc * (1 + gm1 / 2 * mach * mach) + 0.5;
        double term2 = 1 + gm1 / 2 * mach * mach;
        
        return 1.0 / (Math.pow(term1, 0.68) * Math.pow(term2, 0.12));
    }
    
    /**
     * Calculates steady-state wall temperature.
     */
    private double calculateWallTemperature(double recoveryTemp, double hGas) {
        // Thermal resistance network
        double Rgas = 1.0 / hGas;
        double Rwall = wallThickness / wallThermalConductivity;
        double Rcoolant = 1.0 / coolantHeatTransferCoeff;
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
     * Record containing wall thermal data at a point.
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
        @Override
        public String toString() {
            return String.format("ThermalPoint[x=%.4f, Tw=%.1f K, q=%.2e W/m²]",
                    x, wallTemperature, totalHeatFlux);
        }
    }
}
