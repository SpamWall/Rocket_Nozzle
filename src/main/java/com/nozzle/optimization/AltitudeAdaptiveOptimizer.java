package com.nozzle.optimization;

import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.core.PerformanceCalculator;
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
     * Generates the full Cartesian product of candidate nozzle designs by
     * varying the exit area ratio, Rao bell length fraction, and initial wall
     * angle over the ranges defined in {@link OptimizationConfig}.
     * For each area-ratio multiplier, the corresponding exit Mach number is
     * recomputed from the isentropic area-ratio relation.
     *
     * @return List of fully specified {@link NozzleDesignParameters} covering
     *         every combination of the search-space ranges
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
     * Evaluates a single candidate nozzle design across all altitude conditions
     * registered in {@link #altitudeProfile}.
     * Geometry-dependent losses (divergence, boundary-layer, chemistry) are computed
     * once via {@link PerformanceCalculator} and applied as a fixed efficiency factor
     * to the altitude-varying off-design thrust coefficient from
     * {@link com.nozzle.core.ShockExpansionModel}, which handles flow separation and
     * over/under-expansion shocks.
     * The mission-averaged objective is the dwell-time-and-weight-weighted mean Isp.
     *
     * @param candidate The nozzle design parameters to evaluate
     * @return An {@link OptimizationResult} containing per-altitude performance data
     *         and the scalar objective value
     */
    private OptimizationResult evaluateCandidate(NozzleDesignParameters candidate) {
        double totalObjective = 0;
        double totalWeight = 0;
        List<AltitudePerformance> performances = new ArrayList<>();

        // Geometry-dependent losses (divergence via Rao exit-angle correlation, BL, chemistry)
        // are invariant across altitudes — compute once per candidate.
        double geometryEfficiency = PerformanceCalculator.simple(candidate).calculate().getEfficiency();

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

            // Off-design Cf from shock-expansion model (handles separation, over/under-expansion).
            ShockExpansionModel shockModel = new ShockExpansionModel(altParams);
            ShockExpansionModel.OffDesignResult offDesign = shockModel.compute(condition.pressure());

            // Apply geometry losses to the altitude-corrected Cf.
            double cf = offDesign.thrustCoefficient() * geometryEfficiency;
            double performanceMetric = candidate.characteristicVelocity() * cf / 9.80665;
            boolean separated = !offDesign.isFullyFlowing();

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
     * Calculates ambient static pressure at the given geometric altitude using a
     * simplified International Standard Atmosphere (ISA) model.
     * Four altitude layers are modelled:
     * <ul>
     *   <li>Troposphere (0–11 000 m): temperature lapse rate 6.5 K/km.</li>
     *   <li>Lower stratosphere (11 000–25 000 m): isothermal, exponential decay.</li>
     *   <li>Upper atmosphere (25 000–100 000 m): scale-height approximation
     *       (H = 7 000 m).</li>
     *   <li>Near vacuum (above 100 000 m): constant 1 Pa.</li>
     * </ul>
     *
     * @param altitude Geometric altitude in metres; negative values are clamped to 0
     * @return Ambient pressure in Pa
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
     * Returns all candidate results produced by the most recent {@link #optimize()} call,
     * ordered by evaluation sequence.
     *
     * @return Unmodifiable copy of all {@link OptimizationResult}s
     */
    public List<OptimizationResult> getResults() {
        return new ArrayList<>(results);
    }
    
    /**
     * Returns the single {@link OptimizationResult} with the highest mission-averaged
     * specific impulse from the most recent {@link #optimize()} call.
     *
     * @return Best {@link OptimizationResult}, or {@code null} if no optimization has run
     */
    public OptimizationResult getBestResult() {
        return bestResult;
    }
    
    /**
     * Returns the top {@code n} results sorted by descending mission-averaged specific impulse.
     *
     * @param n Maximum number of results to return
     * @return List of up to {@code n} {@link OptimizationResult}s, best first
     */
    public List<OptimizationResult> getTopResults(int n) {
        return results.stream()
                .sorted(Comparator.comparingDouble(OptimizationResult::objectiveValue).reversed())
                .limit(n)
                .toList();
    }
    
    /**
     * A single altitude point that contributes to the mission-averaged objective.
     *
     * @param altitude  Altitude above sea level in metres
     * @param pressure  Ambient static pressure at this altitude in Pa
     * @param weight    Relative weighting of this altitude in the mission-average
     *                  (dimensionless; weights across all conditions need not sum to 1
     *                  as they are normalised internally)
     * @param dwellTime Fraction of total burn time spent at this altitude (0–1);
     *                  used together with {@code weight} to form the objective contribution
     */
    public record AltitudeCondition(
            double altitude,
            double pressure,
            double weight,
            double dwellTime
    ) {}
    
    /**
     * Computed performance of a candidate nozzle design at one altitude.
     *
     * @param altitude          Altitude above sea level in metres
     * @param thrustCoefficient Actual thrust coefficient Cf at this altitude
     *                          (includes divergence, boundary-layer, and separation losses)
     * @param specificImpulse   Delivered specific impulse Isp in seconds
     * @param thrust            Delivered thrust in Newtons: {@code Cf · Pc · At}
     * @param separated         {@code true} if the flow separation predictor determined
     *                          that the nozzle flow is separated at this altitude
     */
    public record AltitudePerformance(
            double altitude,
            double thrustCoefficient,
            double specificImpulse,
            double thrust,
            boolean separated
    ) {}
    
    /**
     * The outcome of evaluating one candidate nozzle design across all altitude conditions.
     *
     * @param parameters    Full nozzle design parameters for this candidate
     * @param objectiveValue Mission-averaged specific impulse in seconds — the scalar
     *                       value maximised by the optimizer (higher is better)
     * @param performances  Per-altitude breakdown of thrust coefficient, Isp, thrust,
     *                       and separation state, one entry per {@link AltitudeCondition}
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
     * Search-space bounds used by the optimizer's parameter sweep.
     *
     * @param areaRatioRange      Candidate exit-area-ratio multipliers relative to the
     *                            base design (dimensionless; e.g. {@code {0.8, 1.0, 1.2}})
     * @param lengthFractionRange Candidate Rao bell-length fractions to evaluate
     *                            (dimensionless, 0–1; e.g. {@code {0.7, 0.8, 0.9}})
     * @param wallAngleRange      Candidate initial wall angles at the throat in degrees
     *                            (e.g. {@code {25, 30, 35}})
     */
    public record OptimizationConfig(
            double[] areaRatioRange,
            double[] lengthFractionRange,
            double[] wallAngleRange
    ) {
        /**
         * Returns a coarse default search grid: 5 area-ratio steps, 3 length fractions,
         * and 3 wall angles — 45 candidate designs in total.
         *
         * @return Default {@link OptimizationConfig}
         */
        public static OptimizationConfig defaults() {
            return new OptimizationConfig(
                    new double[]{0.8, 0.9, 1.0, 1.1, 1.2},
                    new double[]{0.7, 0.8, 0.9},
                    new double[]{25, 30, 35}
            );
        }
        
        /**
         * Returns a finer search grid: 7 area-ratio steps, 5 length fractions,
         * and 5 wall angles — 175 candidate designs in total.
         *
         * @return Fine-resolution {@link OptimizationConfig}
         */
        public static OptimizationConfig fine() {
            return new OptimizationConfig(
                    new double[]{0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.3},
                    new double[]{0.6, 0.7, 0.8, 0.9, 1.0},
                    new double[]{20, 25, 30, 35, 40}
            );
        }
    }
}
