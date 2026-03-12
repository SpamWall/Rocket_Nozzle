package com.nozzle.optimization;

import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.core.ShockExpansionModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Optimizes nozzle design for altitude-adaptive performance.
 * Considers performance across a range of operating altitudes.
 */
public class AltitudeAdaptiveOptimizer {
    
    private final NozzleDesignParameters baseParameters;
    private final List<AltitudeCondition> altitudeProfile;
    private final OptimizationConfig config;
    
    private final List<OptimizationResult> results;
    private OptimizationResult bestResult;
    
    /**
     * Creates an altitude-adaptive optimizer.
     *
     * @param baseParameters Base design parameters
     * @param config         Optimization configuration
     */
    public AltitudeAdaptiveOptimizer(NozzleDesignParameters baseParameters,
                                      OptimizationConfig config) {
        this.baseParameters = baseParameters;
        this.altitudeProfile = new ArrayList<>();
        this.config = config;
        this.results = new ArrayList<>();
    }
    
    /**
     * Creates optimizer with default configuration.
     *
     * @param baseParameters Base design parameters
     */
    public AltitudeAdaptiveOptimizer(NozzleDesignParameters baseParameters) {
        this(baseParameters, OptimizationConfig.defaults());
    }
    
    /**
     * Adds an altitude condition to the mission profile.
     *
     * @param altitude   Altitude in meters
     * @param weight     Relative importance weight
     * @param dwellTime  Time spent at altitude in seconds
     * @return This optimizer
     */
    public AltitudeAdaptiveOptimizer addAltitudeCondition(double altitude, double weight,
                                                           double dwellTime) {
        double pressure = calculateAtmosphericPressure(altitude);
        altitudeProfile.add(new AltitudeCondition(altitude, pressure, weight, dwellTime));
        return this;
    }
    
    /**
     * Adds standard altitude profile (sea level to vacuum).
     *
     * @return This optimizer
     */
    public AltitudeAdaptiveOptimizer addStandardProfile() {
        addAltitudeCondition(0, 0.3, 60);      // Sea level launch
        addAltitudeCondition(10000, 0.25, 30); // 10 km
        addAltitudeCondition(30000, 0.2, 30);  // 30 km
        addAltitudeCondition(50000, 0.15, 30); // 50 km
        addAltitudeCondition(100000, 0.1, 60); // Vacuum
        return this;
    }
    
    /**
     * Runs the optimization using virtual threads.
     *
     * @return This optimizer
     */
    public AltitudeAdaptiveOptimizer optimize() {
        results.clear();
        
        // Generate parameter combinations
        List<NozzleDesignParameters> candidates = generateCandidates();
        
        // Evaluate in parallel using virtual threads
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<OptimizationResult>> futures = new ArrayList<>();
            
            for (NozzleDesignParameters candidate : candidates) {
                futures.add(executor.submit(() -> evaluateCandidate(candidate)));
            }
            
            for (Future<OptimizationResult> future : futures) {
                try {
                    OptimizationResult result = future.get();
                    if (result != null) {
                        results.add(result);
                    }
                } catch (Exception e) {
                    System.err.println("Optimization evaluation failed: " + e.getMessage());
                }
            }
        }
        
        // Find best result
        bestResult = results.stream()
                .max(Comparator.comparingDouble(OptimizationResult::objectiveValue))
                .orElse(null);
        
        return this;
    }
    
    /**
     * Generates candidate parameter sets for optimization.
     */
    private List<NozzleDesignParameters> generateCandidates() {
        List<NozzleDesignParameters> candidates = new ArrayList<>();
        
        double[] areaRatioMultipliers = config.areaRatioRange();
        double[] lengthFractions = config.lengthFractionRange();
        double[] wallAngles = config.wallAngleRange();
        
        for (double arMultiplier : areaRatioMultipliers) {
            for (double lf : lengthFractions) {
                for (double wa : wallAngles) {
                    // Calculate new exit Mach for modified area ratio
                    double newAR = baseParameters.exitAreaRatio() * arMultiplier;
                    double newExitMach = baseParameters.gasProperties().machFromAreaRatio(newAR);
                    
                    NozzleDesignParameters candidate = NozzleDesignParameters.builder()
                            .throatRadius(baseParameters.throatRadius())
                            .exitMach(newExitMach)
                            .chamberPressure(baseParameters.chamberPressure())
                            .chamberTemperature(baseParameters.chamberTemperature())
                            .ambientPressure(baseParameters.ambientPressure())
                            .gasProperties(baseParameters.gasProperties())
                            .numberOfCharLines(baseParameters.numberOfCharLines())
                            .wallAngleInitial(Math.toRadians(wa))
                            .lengthFraction(lf)
                            .axisymmetric(baseParameters.axisymmetric())
                            .build();
                    
                    candidates.add(candidate);
                }
            }
        }
        
        return candidates;
    }
    
    /**
     * Evaluates a candidate design across all altitude conditions.
     */
    private OptimizationResult evaluateCandidate(NozzleDesignParameters candidate) {
        double totalObjective = 0;
        double totalWeight = 0;
        List<AltitudePerformance> performances = new ArrayList<>();
        
        for (AltitudeCondition condition : altitudeProfile) {
            // Create modified parameters for this altitude
            NozzleDesignParameters altParams = NozzleDesignParameters.builder()
                    .throatRadius(candidate.throatRadius())
                    .exitMach(candidate.exitMach())
                    .chamberPressure(candidate.chamberPressure())
                    .chamberTemperature(candidate.chamberTemperature())
                    .ambientPressure(condition.pressure())
                    .gasProperties(candidate.gasProperties())
                    .numberOfCharLines(candidate.numberOfCharLines())
                    .wallAngleInitial(candidate.wallAngleInitial())
                    .lengthFraction(candidate.lengthFraction())
                    .axisymmetric(candidate.axisymmetric())
                    .build();
            
            // Calculate off-design performance with shock-expansion correction.
            ShockExpansionModel shockModel = new ShockExpansionModel(altParams);
            ShockExpansionModel.OffDesignResult offDesign = shockModel.compute(condition.pressure());

            double cf = offDesign.thrustCoefficient();
            double performanceMetric = offDesign.specificImpulse();
            boolean separated = !offDesign.isFullyFlowing();

            // Thrust from corrected Cf (already accounts for separation truncation).
            double thrust = cf * candidate.chamberPressure() * candidate.throatArea();

           performances.add(new AltitudePerformance(condition.altitude(),
                    cf, performanceMetric, thrust, separated));
            
            totalObjective += condition.weight() * condition.dwellTime() * performanceMetric;
            totalWeight += condition.weight() * condition.dwellTime();
        }
        
        double normalizedObjective = totalWeight > 0 ? totalObjective / totalWeight : 0;
        
        return new OptimizationResult(candidate, normalizedObjective, performances);
    }
    
    /**
     * Calculates atmospheric pressure at altitude (standard atmosphere).
     */
    private double calculateAtmosphericPressure(double altitude) {
        if (altitude < 0) altitude = 0;
        
        if (altitude <= 11000) {
            // Troposphere
            double T = 288.15 - 0.0065 * altitude;
            return 101325 * Math.pow(T / 288.15, 5.2561);
        } else if (altitude <= 25000) {
            // Lower stratosphere
            double P11 = 22632.1;
            return P11 * Math.exp(-0.0001577 * (altitude - 11000));
        } else if (altitude <= 100000) {
            // Upper atmosphere approximation
            return 101325 * Math.exp(-altitude / 7000);
        } else {
            // Near vacuum
            return 1.0; // 1 Pa
        }
    }
    
    /**
     * Gets all optimization results.
     */
    public List<OptimizationResult> getResults() {
        return new ArrayList<>(results);
    }
    
    /**
     * Gets the best result.
     */
    public OptimizationResult getBestResult() {
        return bestResult;
    }
    
    /**
     * Gets top N results.
     */
    public List<OptimizationResult> getTopResults(int n) {
        return results.stream()
                .sorted(Comparator.comparingDouble(OptimizationResult::objectiveValue).reversed())
                .limit(n)
                .toList();
    }
    
    /**
     * Altitude condition record.
     */
    public record AltitudeCondition(
            double altitude,
            double pressure,
            double weight,
            double dwellTime
    ) {}
    
    /**
     * Performance at specific altitude.
     */
    public record AltitudePerformance(
            double altitude,
            double thrustCoefficient,
            double specificImpulse,
            double thrust,
            boolean separated
    ) {}
    
    /**
     * Optimization result record.
     */
    public record OptimizationResult(
            NozzleDesignParameters parameters,
            double objectiveValue,
            List<AltitudePerformance> performances
    ) {
        @SuppressWarnings("NullableProblems")
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Objective: %.2f s\n", objectiveValue));
            sb.append(String.format("Exit Mach: %.2f, Area Ratio: %.1f\n",
                    parameters.exitMach(), parameters.exitAreaRatio()));
            sb.append(String.format("Length Fraction: %.2f, Wall Angle: %.1f°\n",
                    parameters.lengthFraction(), Math.toDegrees(parameters.wallAngleInitial())));
            sb.append("Performance by Altitude:\n");
            for (AltitudePerformance perf : performances) {
                sb.append(String.format("  %.0f m: Isp=%.1f s, Cf=%.3f%s\n",
                        perf.altitude, perf.specificImpulse, perf.thrustCoefficient,
                        perf.separated ? " (SEPARATED)" : ""));
            }
            return sb.toString();
        }
    }
    
    /**
     * Optimization configuration.
     */
    public record OptimizationConfig(
            double[] areaRatioRange,
            double[] lengthFractionRange,
            double[] wallAngleRange
    ) {
        public static OptimizationConfig defaults() {
            return new OptimizationConfig(
                    new double[]{0.8, 0.9, 1.0, 1.1, 1.2},
                    new double[]{0.7, 0.8, 0.9},
                    new double[]{25, 30, 35}
            );
        }
        
        public static OptimizationConfig fine() {
            return new OptimizationConfig(
                    new double[]{0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.3},
                    new double[]{0.6, 0.7, 0.8, 0.9, 1.0},
                    new double[]{20, 25, 30, 35, 40}
            );
        }
    }
}
