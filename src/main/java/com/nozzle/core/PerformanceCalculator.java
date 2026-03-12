package com.nozzle.core;

import com.nozzle.chemistry.ChemistryModel;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.moc.CharacteristicNet;
import com.nozzle.moc.CharacteristicPoint;
import com.nozzle.thermal.BoundaryLayerCorrection;

import java.util.List;

/**
 * Calculates nozzle performance metrics including thrust coefficient,
 * specific impulse, and efficiency with various loss mechanisms.
 */
public class PerformanceCalculator {
    
    private final NozzleDesignParameters parameters;
    private final CharacteristicNet characteristicNet;
    private final NozzleContour contour;
    private final BoundaryLayerCorrection boundaryLayer;
    private final ChemistryModel chemistry;
    
    // Calculated results
    private double idealThrustCoeff;
    private double divergenceLoss;
    private double boundaryLayerLoss;
    private double chemicalLoss;
    private double actualThrustCoeff;
    private double efficiency;
    private double specificImpulse;
    private double thrust;
    private double massFlowRate;
    
    /**
     * Creates a performance calculator.
     *
     * @param parameters      Design parameters
     * @param characteristicNet Characteristic net (Could be null)
     * @param contour         Nozzle contour (Could be null)
     * @param boundaryLayer   Boundary layer model (Could be null)
     * @param chemistry       Chemistry model (Could be null)
     */
    public PerformanceCalculator(NozzleDesignParameters parameters,
                                  CharacteristicNet characteristicNet,
                                  NozzleContour contour,
                                  BoundaryLayerCorrection boundaryLayer,
                                  ChemistryModel chemistry) {
        this.parameters = parameters;
        this.characteristicNet = characteristicNet;
        this.contour = contour;
        this.boundaryLayer = boundaryLayer;
        this.chemistry = chemistry;
    }
    
    /**
     * Creates a simple performance calculator with only design parameters.
     *
     * @param parameters Design parameters
     * @return Performance calculator
     */
    public static PerformanceCalculator simple(NozzleDesignParameters parameters) {
        return new PerformanceCalculator(parameters, null, null, null, null);
    }
    
    /**
     * Calculates all performance metrics.
     *
     * @return This instance
     */
    public PerformanceCalculator calculate() {
        calculateIdealPerformance();
        calculateDivergenceLoss();
        calculateBoundaryLayerLoss();
        calculateChemicalLoss();
        calculateActualPerformance();
        return this;
    }
    
    /**
     * Calculates ideal (isentropic) performance.
     */
    private void calculateIdealPerformance() {
        GasProperties gas = parameters.gasProperties();
        double gamma = gas.gamma();
        double gp1 = gamma + 1;
        double gm1 = gamma - 1;
        
        double Pc = parameters.chamberPressure();
        double Pa = parameters.ambientPressure();
        double exitMach = parameters.exitMach();
        double areaRatio = parameters.exitAreaRatio();
        
        // Exit pressure
        double Pe = Pc * gas.isentropicPressureRatio(exitMach);
        
        // Ideal thrust coefficient (momentum + pressure)
        double term1 = 2 * gamma * gamma / gm1 * Math.pow(2.0 / gp1, gp1 / gm1);
        double prRatio = Pe / Pc;
        double term2 = 1 - Math.pow(prRatio, gm1 / gamma);
        
        double cfMomentum = Math.sqrt(term1 * term2);
        double cfPressure = (Pe - Pa) / Pc * areaRatio;
        
        idealThrustCoeff = cfMomentum + cfPressure;
        
        // Mass flow rate
        double At = parameters.throatArea();
        double cStar = parameters.characteristicVelocity();
        massFlowRate = Pc * At / cStar;
    }
    
    /**
     * Calculates thrust loss due to flow divergence at exit.
     */
    private void calculateDivergenceLoss() {
        double lambda = getLambda();

        // Divergence loss is (1 - lambda) times momentum contribution
        GasProperties gas = parameters.gasProperties();
        double gamma = gas.gamma();
        double gp1 = gamma + 1;
        double gm1 = gamma - 1;
        
        double term1 = 2 * gamma * gamma / gm1 * Math.pow(2.0 / gp1, gp1 / gm1);
        double prRatio = gas.isentropicPressureRatio(parameters.exitMach());
        double term2 = 1 - Math.pow(prRatio, gm1 / gamma);
        double cfMomentum = Math.sqrt(term1 * term2);
        
        divergenceLoss = (1 - lambda) * cfMomentum;
    }

    private double getLambda() {
        double exitAngle = 0;

        if (characteristicNet != null) {
            List<CharacteristicPoint> wallPoints = characteristicNet.getWallPoints();
            if (!wallPoints.isEmpty()) {
                exitAngle = wallPoints.getLast().theta();
            }
        } else if (contour != null) {
            List<com.nozzle.geometry.Point2D> points = contour.getContourPoints();
            if (points.size() >= 2) {
                var last = points.getLast();
                var prev = points.get(points.size() - 2);
                exitAngle = Math.atan((last.y() - prev.y()) / (last.x() - prev.x()));
            }
        } else {
            // Estimate from initial wall angle
            exitAngle = parameters.wallAngleInitial() * 0.3;
        }

        // Divergence factor (lambda)
        return (1 + Math.cos(exitAngle)) / 2;
    }

    /**
     * Calculates thrust loss due to boundary layer.
     */
    private void calculateBoundaryLayerLoss() {
        if (boundaryLayer != null) {
            boundaryLayerLoss = boundaryLayer.getThrustCoefficientLoss();
        } else {
            // Estimate based on nozzle size and operating conditions
            double Re = estimateThroatReynolds();
            
            // Typical boundary layer loss is 0.5-2% of thrust
            boundaryLayerLoss = idealThrustCoeff * 0.01 / Math.pow(Re / 1e6, 0.2);
            boundaryLayerLoss = Math.min(boundaryLayerLoss, idealThrustCoeff * 0.03);
        }
    }
    
    /**
     * Estimates throat Reynolds number.
     */
    private double estimateThroatReynolds() {
        GasProperties gas = parameters.gasProperties();
        double Tc = parameters.chamberTemperature();
        double Pc = parameters.chamberPressure();
        double rt = parameters.throatRadius();
        
        // Throat conditions
        double gamma = gas.gamma();
        double Tt = Tc * 2.0 / (gamma + 1);
        double rhot = Pc / (gas.gasConstant() * Tc) * Math.pow(2.0 / (gamma + 1), 1.0 / (gamma - 1));
        double at = gas.speedOfSound(Tt);
        double mut = gas.calculateViscosity(Tt);
        
        return rhot * at * 2 * rt / mut;
    }
    
    /**
     * Calculates thrust loss due to chemical kinetics.
     */
    private void calculateChemicalLoss() {
        if (chemistry != null && chemistry.getModelType() != ChemistryModel.ModelType.FROZEN) {
            // Equilibrium vs frozen loss
            double Tc = parameters.chamberTemperature();
            double Te = parameters.exitTemperature();
            
            double gammaChamber = chemistry.calculateGamma(Tc);
            double gammaExit = chemistry.calculateGamma(Te);
            
            // Loss due to gamma change
            double gammaLoss = (gammaChamber - gammaExit) / gammaChamber;
            chemicalLoss = idealThrustCoeff * gammaLoss * 0.5;
        } else {
            // Estimate frozen flow loss (typically 1-3%)
            chemicalLoss = idealThrustCoeff * 0.01;
        }
    }
    
    /**
     * Calculates actual performance with all losses.
     */
    private void calculateActualPerformance() {
        actualThrustCoeff = idealThrustCoeff - divergenceLoss - boundaryLayerLoss - chemicalLoss;
        actualThrustCoeff = Math.max(actualThrustCoeff, idealThrustCoeff * 0.85);
        
        efficiency = actualThrustCoeff / idealThrustCoeff;
        
        double cStar = parameters.characteristicVelocity();
        double g0 = 9.80665;
        
        specificImpulse = cStar * actualThrustCoeff / g0;
        
        thrust = actualThrustCoeff * parameters.chamberPressure() * parameters.throatArea();
    }
    
    // Getters
    public double getIdealThrustCoefficient() { return idealThrustCoeff; }
    public double getActualThrustCoefficient() { return actualThrustCoeff; }
    public double getDivergenceLoss() { return divergenceLoss; }
    public double getBoundaryLayerLoss() { return boundaryLayerLoss; }
    public double getChemicalLoss() { return chemicalLoss; }
    public double getEfficiency() { return efficiency; }
    public double getSpecificImpulse() { return specificImpulse; }
    public double getThrust() { return thrust; }
    public double getMassFlowRate() { return massFlowRate; }
    
    /**
     * Gets total thrust coefficient loss.
     */
    public double getTotalLoss() {
        return divergenceLoss + boundaryLayerLoss + chemicalLoss;
    }
    
    /**
     * Creates a performance summary.
     */
    public PerformanceSummary getSummary() {
        return new PerformanceSummary(
                idealThrustCoeff, actualThrustCoeff,
                divergenceLoss, boundaryLayerLoss, chemicalLoss,
                efficiency, specificImpulse, thrust, massFlowRate
        );
    }
    
    /**
     * Record containing performance summary.
     */
    public record PerformanceSummary(
            double idealCf,
            double actualCf,
            double divergenceLoss,
            double boundaryLayerLoss,
            double chemicalLoss,
            double efficiency,
            double specificImpulseSeconds,
            double thrustNewtons,
            double massFlowRateKgPerSec
    ) {
        @SuppressWarnings("NullableProblems")
        @Override
        public String toString() {
            return String.format("""
                    Performance Summary:
                      Ideal Cf:        %.4f
                      Actual Cf:       %.4f
                      Divergence Loss: %.4f (%.2f%%)
                      BL Loss:         %.4f (%.2f%%)
                      Chemical Loss:   %.4f (%.2f%%)
                      Efficiency:      %.2f%%
                      Isp:             %.1f s
                      Thrust:          %.2f kN
                      Mass Flow:       %.2f kg/s
                    """,
                    idealCf, actualCf,
                    divergenceLoss, divergenceLoss / idealCf * 100,
                    boundaryLayerLoss, boundaryLayerLoss / idealCf * 100,
                    chemicalLoss, chemicalLoss / idealCf * 100,
                    efficiency * 100,
                    specificImpulseSeconds,
                    thrustNewtons / 1000,
                    massFlowRateKgPerSec);
        }
    }
}
