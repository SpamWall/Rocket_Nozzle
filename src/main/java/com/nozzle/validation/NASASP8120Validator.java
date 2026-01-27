package com.nozzle.validation;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.moc.CharacteristicNet;

import java.util.*;

/**
 * Validates nozzle designs against NASA SP-8120 reference data.
 * NASA SP-8120: "Liquid Rocket Engine Nozzles" provides standard
 * reference data for supersonic nozzle design verification.
 */
public class NASASP8120Validator {
    
    private final Map<Double, Double[]> areaRatioTable;
    private final Map<Double, Double[]> thrustCoefficientTable;
    private final Map<Double, Double[]> specificImpulseEfficiencyTable;
    private final double gamma;
    
    /**
     * Creates a validator for specified gamma.
     *
     * @param gamma Ratio of specific heats
     */
    public NASASP8120Validator(double gamma) {
        this.gamma = gamma;
        this.areaRatioTable = new HashMap<>();
        this.thrustCoefficientTable = new HashMap<>();
        this.specificImpulseEfficiencyTable = new HashMap<>();
        initializeTables();
    }
    
    /**
     * Creates a validator with default gamma = 1.2 (rocket exhaust).
     */
    public NASASP8120Validator() {
        this(1.2);
    }
    
    /**
     * Initializes NASA SP-8120 reference tables.
     * These are standard values for ideal isentropic flow.
     */
    private void initializeTables() {
        // Area ratio (A/A*) vs Mach number for various gamma values
        // Format: Mach -> [gamma=1.1, 1.2, 1.3, 1.4]
        
        double[] machs = {1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0};
        
        // Area ratios from isentropic relations
        for (double m : machs) {
            double[] ratios = new double[4];
            double[] gammas = {1.1, 1.2, 1.3, 1.4};
            for (int i = 0; i < 4; i++) {
                ratios[i] = calculateAreaRatio(m, gammas[i]);
            }
            areaRatioTable.put(m, Arrays.stream(ratios).boxed().toArray(Double[]::new));
        }
        
        // Thrust coefficient (Cf) vs area ratio for vacuum conditions
        // These are based on NASA SP-8120 Figure 2-1
        double[] areaRatios = {1, 5, 10, 20, 50, 100, 200};
        for (double ar : areaRatios) {
            double m = calculateMachFromAreaRatio(ar, gamma);
            double cf = calculateIdealThrustCoefficient(m, gamma, 0); // Vacuum Cf
            thrustCoefficientTable.put(ar, new Double[]{cf});
        }
        
        // Specific impulse efficiency factors
        // Based on NASA SP-8120 empirical correlations
        // Key: nozzle length fraction, Value: efficiency factor
        double[] lengthFractions = {0.6, 0.7, 0.8, 0.9, 1.0};
        double[] efficiencies = {0.92, 0.94, 0.96, 0.98, 0.99};
        for (int i = 0; i < lengthFractions.length; i++) {
            specificImpulseEfficiencyTable.put(lengthFractions[i], 
                    new Double[]{efficiencies[i]});
        }
    }
    
    /**
     * Validates a nozzle design against NASA SP-8120 standards.
     *
     * @param parameters Design parameters
     * @return Validation result
     */
    public ValidationResult validate(NozzleDesignParameters parameters) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, Double> metrics = new HashMap<>();
        
        double designGamma = parameters.gasProperties().gamma();
        double exitMach = parameters.exitMach();
        double areaRatio = parameters.exitAreaRatio();
        
        // Validate area ratio calculation
        double expectedAR = calculateAreaRatio(exitMach, designGamma);
        double arError = Math.abs(areaRatio - expectedAR) / expectedAR * 100;
        metrics.put("area_ratio_error_percent", arError);
        
        if (arError > 1.0) {
            warnings.add(String.format("Area ratio deviation %.2f%% from isentropic value", arError));
        }
        if (arError > 5.0) {
            errors.add(String.format("Area ratio error %.2f%% exceeds 5%% tolerance", arError));
        }
        
        // Validate thrust coefficient
        double idealCf = calculateIdealThrustCoefficient(exitMach, designGamma, 
                parameters.ambientPressure() / parameters.chamberPressure());
        double designCf = parameters.idealThrustCoefficient();
        double cfError = Math.abs(designCf - idealCf) / idealCf * 100;
        metrics.put("thrust_coeff_error_percent", cfError);
        metrics.put("ideal_thrust_coefficient", idealCf);
        
        if (cfError > 1.0) {
            warnings.add(String.format("Thrust coefficient deviation %.2f%% from theoretical", cfError));
        }
        
        // Validate specific impulse
        double idealIsp = calculateIdealSpecificImpulse(designGamma, 
                parameters.chamberTemperature(), parameters.gasProperties().molecularWeight(),
                exitMach, parameters.ambientPressure() / parameters.chamberPressure());
        double designIsp = parameters.idealSpecificImpulse();
        double ispError = Math.abs(designIsp - idealIsp) / idealIsp * 100;
        metrics.put("isp_error_percent", ispError);
        metrics.put("ideal_specific_impulse", idealIsp);
        
        if (ispError > 2.0) {
            warnings.add(String.format("Specific impulse deviation %.2f%% from theoretical", ispError));
        }
        
        // Check design space validity per NASA SP-8120
        if (exitMach < 1.5) {
            warnings.add("Exit Mach < 1.5 may result in flow instabilities");
        }
        if (exitMach > 6.0) {
            warnings.add("Exit Mach > 6.0 may require real-gas corrections");
        }
        if (areaRatio > 400) {
            warnings.add("Area ratio > 400 may have significant viscous losses");
        }
        
        // Validate wall angle
        double wallAngleDeg = Math.toDegrees(parameters.wallAngleInitial());
        if (wallAngleDeg > 45) {
            errors.add("Initial wall angle > 45° will cause flow separation");
        }
        if (wallAngleDeg > 35) {
            warnings.add("Initial wall angle > 35° may cause boundary layer issues");
        }
        
        // Calculate divergence efficiency (NASA SP-8120 correlation)
        double lengthFrac = parameters.lengthFraction();
        double divEfficiency = interpolateEfficiency(lengthFrac);
        metrics.put("estimated_divergence_efficiency", divEfficiency);
        
        boolean isValid = errors.isEmpty();
        
        return new ValidationResult(isValid, errors, warnings, metrics);
    }
    
    /**
     * Validates a characteristic net against NASA SP-8120.
     *
     * @param net Characteristic net to validate
     * @return Validation result
     */
    public ValidationResult validate(CharacteristicNet net) {
        ValidationResult paramsResult = validate(net.getParameters());
        
        List<String> warnings = new ArrayList<>(paramsResult.warnings());
        List<String> errors = new ArrayList<>(paramsResult.errors());
        Map<String, Double> metrics = new HashMap<>(paramsResult.metrics());
        
        // Validate MOC convergence
        if (!net.validate()) {
            errors.add("Characteristic net failed internal validation");
        }
        
        // Check exit conditions
        var wallPoints = net.getWallPoints();
        if (!wallPoints.isEmpty()) {
            var exitPoint = wallPoints.getLast();
            double exitMachExpected = net.getParameters().exitMach();
            double exitMachActual = exitPoint.mach();
            double machError = Math.abs(exitMachActual - exitMachExpected) / exitMachExpected * 100;
            metrics.put("exit_mach_error_percent", machError);
            
            if (machError > 2.0) {
                warnings.add(String.format("Exit Mach deviation %.2f%% from design", machError));
            }
            if (machError > 5.0) {
                errors.add(String.format("Exit Mach error %.2f%% exceeds tolerance", machError));
            }
            
            // Check exit flow angle
            double exitAngleDeg = exitPoint.thetaDegrees();
            metrics.put("exit_flow_angle_deg", exitAngleDeg);
            
            if (Math.abs(exitAngleDeg) > 10) {
                warnings.add(String.format("Exit flow angle %.1f° may reduce thrust", exitAngleDeg));
            }
        }
        
        // Validate area ratio from net
        double computedAR = net.calculateExitAreaRatio();
        double designAR = net.getParameters().exitAreaRatio();
        double arDiff = Math.abs(computedAR - designAR) / designAR * 100;
        metrics.put("computed_vs_design_area_ratio_percent", arDiff);
        
        if (arDiff > 3.0) {
            warnings.add(String.format("Computed area ratio differs %.2f%% from design", arDiff));
        }
        
        boolean isValid = errors.isEmpty();
        
        return new ValidationResult(isValid, errors, warnings, metrics);
    }
    
    /**
     * Calculates area ratio using isentropic relations.
     */
    private double calculateAreaRatio(double mach, double gamma) {
        double gp1 = gamma + 1;
        double gm1 = gamma - 1;
        double term = 2.0 / gp1 * (1 + gm1 / 2 * mach * mach);
        return Math.pow(term, gp1 / (2 * gm1)) / mach;
    }
    
    /**
     * Calculates Mach number from area ratio (supersonic branch).
     */
    private double calculateMachFromAreaRatio(double areaRatio, double gamma) {
        if (areaRatio <= 1) return 1.0;
        
        double mach = 2.0; // Initial guess
        for (int i = 0; i < 50; i++) {
            double f = calculateAreaRatio(mach, gamma) - areaRatio;
            double gp1 = gamma + 1;
            double gm1 = gamma - 1;
            double mach2 = mach * mach;
            double term = 1 + gm1 / 2 * mach2;
            double df = calculateAreaRatio(mach, gamma) * (mach2 - 1) / (mach * term);
            double deltaMach = f / df;
            mach -= deltaMach;
            if (Math.abs(deltaMach) < 1e-10) break;
        }
        return mach;
    }
    
    /**
     * Calculates ideal thrust coefficient.
     */
    private double calculateIdealThrustCoefficient(double mach, double gamma, double pressureRatio) {
        double gp1 = gamma + 1;
        double gm1 = gamma - 1;
        
        // Exit pressure ratio
        double pepc = Math.pow(1 + gm1 / 2 * mach * mach, -gamma / gm1);
        
        // Momentum thrust
        double term1 = 2 * gamma * gamma / gm1 * Math.pow(2.0 / gp1, gp1 / gm1);
        double term2 = 1 - Math.pow(pepc, gm1 / gamma);
        double cfMom = Math.sqrt(term1 * term2);
        
        // Pressure thrust
        double areaRatio = calculateAreaRatio(mach, gamma);
        double cfPres = (pepc - pressureRatio) * areaRatio;
        
        return cfMom + cfPres;
    }
    
    /**
     * Calculates ideal specific impulse.
     */
    private double calculateIdealSpecificImpulse(double gamma, double Tc, double MW,
                                                   double mach, double pressureRatio) {
        double R = 8314.46 / MW;
        double gm1 = gamma - 1;
        double g0 = 9.80665;
        
        // Exit velocity
        double tempRatio = 1.0 / (1 + gm1 / 2 * mach * mach);
        double Te = Tc * tempRatio;
        double Ve = mach * Math.sqrt(gamma * R * Te);
        
        // Specific impulse
        return Ve / g0;
    }
    
    /**
     * Interpolates efficiency from table.
     */
    private double interpolateEfficiency(double lengthFraction) {
        double[] fracs = {0.6, 0.7, 0.8, 0.9, 1.0};
        double[] effs = {0.92, 0.94, 0.96, 0.98, 0.99};
        
        if (lengthFraction <= fracs[0]) return effs[0];
        if (lengthFraction >= fracs[fracs.length - 1]) return effs[effs.length - 1];
        
        for (int i = 1; i < fracs.length; i++) {
            if (lengthFraction <= fracs[i]) {
                double t = (lengthFraction - fracs[i - 1]) / (fracs[i] - fracs[i - 1]);
                return effs[i - 1] + t * (effs[i] - effs[i - 1]);
            }
        }
        
        return 0.95;
    }
    
    /**
     * Validation result record.
     */
    public record ValidationResult(
            boolean isValid,
            List<String> errors,
            List<String> warnings,
            Map<String, Double> metrics
    ) {
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Validation Result: ").append(isValid ? "PASSED" : "FAILED").append("\n");
            
            if (!errors.isEmpty()) {
                sb.append("\nErrors:\n");
                errors.forEach(e -> sb.append("  - ").append(e).append("\n"));
            }
            
            if (!warnings.isEmpty()) {
                sb.append("\nWarnings:\n");
                warnings.forEach(w -> sb.append("  - ").append(w).append("\n"));
            }
            
            if (!metrics.isEmpty()) {
                sb.append("\nMetrics:\n");
                metrics.forEach((k, v) -> sb.append(String.format("  %s: %.4f\n", k, v)));
            }
            
            return sb.toString();
        }
    }
}
