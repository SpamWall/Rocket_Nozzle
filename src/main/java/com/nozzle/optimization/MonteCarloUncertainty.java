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

package com.nozzle.optimization;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.core.PerformanceCalculator;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Performs Monte Carlo uncertainty propagation for nozzle performance analysis.
 *
 * <p>Each uncertain input parameter is assigned a probability distribution
 * ({@link DistributionType#NORMAL}, {@link DistributionType#UNIFORM},
 * {@link DistributionType#TRIANGULAR}, or {@link DistributionType#LOGNORMAL}).
 * The analysis draws {@code numSamples} independent realizations from those
 * distributions, evaluates {@link com.nozzle.core.PerformanceCalculator} for
 * each realization using Java virtual threads, and accumulates the resulting
 * thrust-coefficient, specific-impulse, thrust, and efficiency distributions
 * into a {@link StatisticalSummary}.
 *
 * <p>After {@link #run()} completes, {@link #getSensitivities()} returns
 * Pearson correlation coefficients between every input parameter and each
 * output metric, providing a first-order sensitivity (tornado) ranking.
 *
 * <p>Typical usage:
 * <pre>{@code
 * MonteCarloUncertainty mc = new MonteCarloUncertainty(params, 1000, 42)
 *         .addTypicalUncertainties()
 *         .run();
 * StatisticalSummary s = mc.getSummary();
 * }</pre>
 *
 * @see PerformanceCalculator
 * @see StatisticalSummary
 */
public class MonteCarloUncertainty {
    
    private final NozzleDesignParameters nominalParameters;
    private final Map<String, UncertainParameter> uncertainParameters;
    private final int numSamples;
    private final long randomSeed;
    
    private final List<SampleResult> sampleResults;
    private StatisticalSummary summary;
    private Random random;
    
    /**
     * Creates a Monte Carlo analysis.
     *
     * @param nominalParameters Nominal design parameters
     * @param numSamples        Number of Monte Carlo samples
     * @param randomSeed        Random seed for reproducibility
     */
    public MonteCarloUncertainty(NozzleDesignParameters nominalParameters,
                                  int numSamples, long randomSeed) {
        this.nominalParameters = nominalParameters;
        this.uncertainParameters = new LinkedHashMap<>();
        this.numSamples = numSamples;
        this.randomSeed = randomSeed;
        this.sampleResults = new ArrayList<>();
    }
    
    /**
     * Creates an analysis with 10 000 samples and a time-based random seed.
     *
     * @param nominalParameters nominal nozzle design parameters around which
     *                          uncertainty is propagated
     */
    public MonteCarloUncertainty(NozzleDesignParameters nominalParameters) {
        this(nominalParameters, 10000, System.currentTimeMillis());
    }
    
    /**
     * Registers an uncertain parameter with a Gaussian (normal) distribution.
     *
     * @param name   unique parameter name used in sensitivity output and sample maps
     * @param mean   distribution mean (nominal value of the parameter)
     * @param stdDev distribution standard deviation (one-sigma spread)
     * @return this instance, for method chaining
     */
    public MonteCarloUncertainty addNormalParameter(String name, double mean, double stdDev) {
        uncertainParameters.put(name, new UncertainParameter(name, DistributionType.NORMAL,
                mean, stdDev, 0, 0));
        return this;
    }
    
    /**
     * Registers an uncertain parameter with a uniform (rectangular) distribution.
     *
     * @param name unique parameter name used in sensitivity output and sample maps
     * @param min  lower bound of the uniform distribution (inclusive)
     * @param max  upper bound of the uniform distribution (exclusive)
     * @return this instance, for method chaining
     */
    public MonteCarloUncertainty addUniformParameter(String name, double min, double max) {
        uncertainParameters.put(name, new UncertainParameter(name, DistributionType.UNIFORM,
                (min + max) / 2, 0, min, max));
        return this;
    }
    
    /**
     * Registers an uncertain parameter with a triangular distribution.
     *
     * @param name unique parameter name used in sensitivity output and sample maps
     * @param min  lower bound of the triangular distribution
     * @param mode peak (most likely) value; must satisfy {@code min ≤ mode ≤ max}
     * @param max  upper bound of the triangular distribution
     * @return this instance, for method chaining
     */
    public MonteCarloUncertainty addTriangularParameter(String name, double min, double mode, double max) {
        uncertainParameters.put(name, new UncertainParameter(name, DistributionType.TRIANGULAR,
                mode, 0, min, max));
        return this;
    }

    /**
     * Registers an uncertain parameter with a log-normal distribution.
     *
     * @param name   unique parameter name used in sensitivity output and sample maps
     * @param mean   mean of the underlying normal variate (i.e., of ln(X))
     * @param stdDev standard deviation of the underlying normal variate
     * @return this instance, for method chaining
     */
    public MonteCarloUncertainty addLognormalParameter(String name, double mean, double stdDev) {
        uncertainParameters.put(name, new UncertainParameter(name, DistributionType.LOGNORMAL,
                mean, stdDev, 0, 0));
        return this;
    }

    /**
     * Registers a representative set of manufacturing and propellant uncertainties
     * derived from typical rocket-engine production tolerances:
     * <ul>
     *   <li>Throat radius: ±0.5 % (1σ) — machining tolerance</li>
     *   <li>Chamber pressure: ±2 % (1σ) — regulator and injector variation</li>
     *   <li>Chamber temperature: ±3 % (1σ) — combustion efficiency scatter</li>
     *   <li>Specific heat ratio γ: ±2 % (1σ) — propellant composition variation</li>
     *   <li>Initial wall angle: ±0.5° (1σ) — contour machining tolerance</li>
     * </ul>
     * All distributions are Gaussian (normal).
     *
     * @return this instance, for method chaining
     */
    public MonteCarloUncertainty addTypicalUncertainties() {
        double rt = nominalParameters.throatRadius();
        double pc = nominalParameters.chamberPressure();
        double tc = nominalParameters.chamberTemperature();
        double gamma = nominalParameters.gasProperties().gamma();
        
        // Typical manufacturing tolerances
        addNormalParameter("throatRadius", rt, rt * 0.005);         // 0.5% tolerance
        addNormalParameter("chamberPressure", pc, pc * 0.02);       // 2% uncertainty
        addNormalParameter("chamberTemperature", tc, tc * 0.03);    // 3% uncertainty
        addNormalParameter("gamma", gamma, gamma * 0.02);           // 2% uncertainty
        addNormalParameter("wallAngle", 
                Math.toDegrees(nominalParameters.wallAngleInitial()), 0.5);  // ±0.5° tolerance
        
        return this;
    }
    
    /**
     * Executes the Monte Carlo analysis.
     *
     * <p>Samples are drawn from each registered {@link UncertainParameter} distribution,
     * then evaluated concurrently via a virtual-thread executor.  Failed evaluations
     * (e.g., physically invalid parameter combinations) are silently discarded.
     * Statistics are computed from the surviving valid samples.
     *
     * @return this instance, for method chaining
     */
    public MonteCarloUncertainty run() {
        random = new Random(randomSeed);
        sampleResults.clear();
        
        // Generate samples
        List<Map<String, Double>> samples = generateSamples();
        
        // Evaluate in parallel
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<SampleResult>> futures = new ArrayList<>();
            
            for (int i = 0; i < samples.size(); i++) {
                final int sampleIndex = i;
                final Map<String, Double> sample = samples.get(i);
                futures.add(executor.submit(() -> evaluateSample(sampleIndex, sample)));
            }
            
            for (Future<SampleResult> future : futures) {
                try {
                    SampleResult result = future.get();
                    if (result != null) {
                        sampleResults.add(result);
                    }
                } catch (Exception e) {
                    System.err.println("Sample evaluation failed: " + e.getMessage());
                }
            }
        }
        
        // Calculate statistics
        summary = calculateStatistics();
        
        return this;
    }
    
    /**
     * Draws {@code numSamples} independent realizations from all registered
     * parameter distributions.
     *
     * @return list of sample maps, each mapping parameter name to its drawn value
     */
    private List<Map<String, Double>> generateSamples() {
        List<Map<String, Double>> samples = new ArrayList<>();
        
        for (int i = 0; i < numSamples; i++) {
            Map<String, Double> sample = new HashMap<>();
            
            for (UncertainParameter param : uncertainParameters.values()) {
                double value = switch (param.distribution()) {
                    case NORMAL -> param.mean() + random.nextGaussian() * param.stdDev();
                    case UNIFORM -> param.min() + random.nextDouble() * (param.max() - param.min());
                    case TRIANGULAR -> sampleTriangular(param.min(), param.mean(), param.max());
                    case LOGNORMAL -> Math.exp(Math.log(param.mean()) + 
                            random.nextGaussian() * param.stdDev());
                };
                sample.put(param.name(), value);
            }
            
            samples.add(sample);
        }
        
        return samples;
    }
    
    /**
     * Draws one variate from a triangular distribution using inverse-CDF sampling.
     *
     * @param min  lower bound of the distribution
     * @param mode peak (most likely) value; must satisfy {@code min ≤ mode ≤ max}
     * @param max  upper bound of the distribution
     * @return a single random variate in {@code [min, max]}
     */
    private double sampleTriangular(double min, double mode, double max) {
        double u = random.nextDouble();
        double fc = (mode - min) / (max - min);
        
        if (u < fc) {
            return min + Math.sqrt(u * (max - min) * (mode - min));
        } else {
            return max - Math.sqrt((1 - u) * (max - min) * (max - mode));
        }
    }
    
    /**
     * Builds a perturbed {@link NozzleDesignParameters} from one sample map,
     * runs {@link PerformanceCalculator}, and returns the result.
     *
     * <p>Parameter values are clamped to physically valid bounds before use
     * (e.g., γ ∈ [1.05, 1.67], wall angle ∈ [5°, 45°]).  Returns {@code null}
     * if any exception occurs during construction or evaluation so that the
     * caller can discard the failed sample without aborting the analysis.
     *
     * @param index  zero-based sample index, stored in the returned record for traceability
     * @param sample map of parameter name to sampled value for this realization
     * @return performance result for the sample
     * @throws RuntimeException wrapping any exception thrown during parameter construction
     *                          or performance evaluation; caught by the {@code Future} and
     *                          logged at the call site in {@link #run()}
     */
    private SampleResult evaluateSample(int index, Map<String, Double> sample) {
        try {
            // Build parameters from sample
            double rt = sample.getOrDefault("throatRadius", nominalParameters.throatRadius());
            double pc = sample.getOrDefault("chamberPressure", nominalParameters.chamberPressure());
            double tc = sample.getOrDefault("chamberTemperature", nominalParameters.chamberTemperature());
            double gamma = sample.getOrDefault("gamma", nominalParameters.gasProperties().gamma());
            double wallAngle = Math.toRadians(sample.getOrDefault("wallAngle",
                    Math.toDegrees(nominalParameters.wallAngleInitial())));
            
            // Ensure valid parameter ranges
            rt = Math.max(rt, 0.001);
            pc = Math.max(pc, nominalParameters.ambientPressure() * 1.5);
            tc = Math.max(tc, 500);
            gamma = Math.max(1.05, Math.min(1.67, gamma));
            wallAngle = Math.max(Math.toRadians(5), Math.min(Math.toRadians(45), wallAngle));
            
            GasProperties gasProps = nominalParameters.gasProperties().withGamma(gamma);
            
            NozzleDesignParameters params = NozzleDesignParameters.builder()
                    .throatRadius(rt)
                    .exitMach(nominalParameters.exitMach())
                    .chamberPressure(pc)
                    .chamberTemperature(tc)
                    .ambientPressure(nominalParameters.ambientPressure())
                    .gasProperties(gasProps)
                    .numberOfCharLines(nominalParameters.numberOfCharLines())
                    .wallAngleInitial(wallAngle)
                    .lengthFraction(nominalParameters.lengthFraction())
                    .axisymmetric(nominalParameters.axisymmetric())
                    .build();
            
            PerformanceCalculator calc = PerformanceCalculator.simple(params).calculate();
            
            return new SampleResult(
                    index,
                    sample,
                    calc.getActualThrustCoefficient(),
                    calc.getSpecificImpulse(),
                    calc.getThrust(),
                    calc.getEfficiency(),
                    params.exitAreaRatio()
            );
            
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Computes the {@link StatisticalSummary} from all valid sample results.
     *
     * @return populated summary, or {@code null} if no valid samples exist
     */
    private StatisticalSummary calculateStatistics() {
        if (sampleResults.isEmpty()) {
            return null;
        }
        
        double[] cfValues = sampleResults.stream()
                .mapToDouble(SampleResult::thrustCoefficient).toArray();
        double[] ispValues = sampleResults.stream()
                .mapToDouble(SampleResult::specificImpulse).toArray();
        double[] thrustValues = sampleResults.stream()
                .mapToDouble(SampleResult::thrust).toArray();
        double[] effValues = sampleResults.stream()
                .mapToDouble(SampleResult::efficiency).toArray();
        
        return new StatisticalSummary(
                calculateStats(cfValues),
                calculateStats(ispValues),
                calculateStats(thrustValues),
                calculateStats(effValues),
                sampleResults.size()
        );
    }
    
    /**
     * Computes descriptive statistics for a one-dimensional array of sample values.
     *
     * <p>The array is sorted in place as a side effect; the caller must not rely
     * on order being preserved after this call.
     *
     * @param values sample values to summarize (must not be empty)
     * @return {@link VariableStats} containing mean, standard deviation, min, max,
     *         median, and the 5th and 95th percentiles
     */
    private VariableStats calculateStats(double[] values) {
        Arrays.sort(values);
        
        double mean = Arrays.stream(values).average().orElse(0);
        double variance = Arrays.stream(values)
                .map(v -> (v - mean) * (v - mean))
                .average().orElse(0);
        double stdDev = Math.sqrt(variance);
        
        double min = values[0];
        double max = values[values.length - 1];
        double median = values[values.length / 2];
        double p05 = values[(int) (values.length * 0.05)];
        double p95 = values[(int) (values.length * 0.95)];
        
        return new VariableStats(mean, stdDev, min, max, median, p05, p95);
    }
    
    /**
     * Returns the statistical summary computed by the most recent {@link #run()} call.
     *
     * @return the summary, or {@code null} if {@link #run()} has not been called or
     *         all samples failed evaluation
     */
    public StatisticalSummary getSummary() {
        return summary;
    }
    
    /**
     * Returns a copy of all valid sample results from the most recent {@link #run()} call.
     *
     * @return mutable list of {@link SampleResult} records; empty if {@link #run()}
     *         has not been called
     */
    public List<SampleResult> getSampleResults() {
        return new ArrayList<>(sampleResults);
    }
    
    /**
     * Computes the Pearson correlation coefficient between the sampled values of one
     * input parameter and an arbitrary output metric extracted from each
     * {@link SampleResult}.
     *
     * @param paramName       name of the input parameter as registered via
     *                        {@link #addNormalParameter} or {@link #addUniformParameter}
     * @param outputExtractor function that extracts the scalar output of interest
     *                        from a {@link SampleResult} (e.g. {@code SampleResult::specificImpulse})
     * @return Pearson r ∈ [−1, 1], or {@code 0} if no sample results are available
     */
    public double getCorrelation(String paramName, Function<SampleResult, Double> outputExtractor) {
        if (sampleResults.isEmpty()) return 0;
        
        double[] inputs = sampleResults.stream()
                .mapToDouble(r -> r.inputs().getOrDefault(paramName, 0.0))
                .toArray();
        double[] outputs = sampleResults.stream()
                .mapToDouble(outputExtractor::apply)
                .toArray();
        
        return pearsonCorrelation(inputs, outputs);
    }
    
    /**
     * Computes the Pearson product-moment correlation coefficient between two
     * equal-length arrays using the standard sum-of-products formula.
     *
     * @param x first variable sample array
     * @param y second variable sample array; must have the same length as {@code x}
     * @return Pearson r ∈ [−1, 1], or {@code 0} if the denominator is zero
     *         (e.g., one variable is constant across all samples)
     */
    private double pearsonCorrelation(double[] x, double[] y) {
        int n = x.length;
        double sumX = Arrays.stream(x).sum();
        double sumY = Arrays.stream(y).sum();
        double sumXY = 0, sumX2 = 0, sumY2 = 0;
        
        for (int i = 0; i < n; i++) {
            sumXY += x[i] * y[i];
            sumX2 += x[i] * x[i];
            sumY2 += y[i] * y[i];
        }
        
        double num = n * sumXY - sumX * sumY;
        double den = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));
        
        return den > 0 ? num / den : 0;
    }
    
    /**
     * Returns Pearson correlation coefficients between each input parameter and the
     * thrust-coefficient and specific-impulse outputs.
     *
     * <p>Map keys follow the pattern {@code "<paramName>_Cf_correlation"} and
     * {@code "<paramName>_Isp_correlation"}.  Values close to ±1 indicate strong
     * linear sensitivity; values near 0 indicate negligible influence.
     *
     * @return ordered map of sensitivity labels to correlation coefficients;
     *         empty if {@link #run()} has not been called
     */
    public Map<String, Double> getSensitivities() {
        Map<String, Double> sensitivities = new LinkedHashMap<>();
        
        for (String paramName : uncertainParameters.keySet()) {
            double corrCf = getCorrelation(paramName, SampleResult::thrustCoefficient);
            double corrIsp = getCorrelation(paramName, SampleResult::specificImpulse);
            sensitivities.put(paramName + "_Cf_correlation", corrCf);
            sensitivities.put(paramName + "_Isp_correlation", corrIsp);
        }
        
        return sensitivities;
    }
    
    // -----------------------------------------------------------------------
    // Supporting types
    // -----------------------------------------------------------------------

    /**
     * Probability distribution family used to model parameter uncertainty.
     */
    public enum DistributionType {
        /** Gaussian distribution, parameterized by mean and standard deviation. */
        NORMAL,
        /** Uniform (rectangular) distribution, parameterised by min and max. */
        UNIFORM,
        /** Triangular distribution, parameterised by min, mode (peak), and max. */
        TRIANGULAR,
        /** Log-normal distribution; {@code mean} and {@code stdDev} refer to the
         *  underlying normal variate (i.e., of ln(X)). */
        LOGNORMAL
    }
    
    /**
     * Specification of one uncertain input parameter and its probability distribution.
     *
     * @param name         unique identifier for the parameter; used as the key in sample
     *                     maps and in sensitivity output labels
     * @param distribution probability distribution family for this parameter
     * @param mean         distribution mean; for {@link DistributionType#TRIANGULAR} this
     *                     is the mode (peak), for {@link DistributionType#LOGNORMAL} this
     *                     is the mean of the underlying normal variate ln(X)
     * @param stdDev       standard deviation; used by {@link DistributionType#NORMAL} and
     *                     {@link DistributionType#LOGNORMAL}; ignored by other distributions
     * @param min          lower bound; used by {@link DistributionType#UNIFORM} and
     *                     {@link DistributionType#TRIANGULAR}; ignored by other distributions
     * @param max          upper bound; used by {@link DistributionType#UNIFORM} and
     *                     {@link DistributionType#TRIANGULAR}; ignored by other distributions
     */
    public record UncertainParameter(
            String name,
            DistributionType distribution,
            double mean,
            double stdDev,
            double min,
            double max
    ) {}
    
    /**
     * Performance outcome for one Monte Carlo realization.
     *
     * @param index            zero-based sample index within the run, for traceability
     * @param inputs           map of parameter name to the value drawn for this realization
     * @param thrustCoefficient actual thrust coefficient Cf computed by
     *                         {@link PerformanceCalculator} for this sample
     * @param specificImpulse  delivered specific impulse Isp in seconds
     * @param thrust           delivered thrust in Newtons
     * @param efficiency       ratio of actual to ideal thrust coefficient
     *                         ({@code actualCf / idealCf}), dimensionless
     * @param areaRatio        nozzle exit area ratio A/A* for this sample's geometry
     */
    public record SampleResult(
            int index,
            Map<String, Double> inputs,
            double thrustCoefficient,
            double specificImpulse,
            double thrust,
            double efficiency,
            double areaRatio
    ) {}
    
    /**
     * Descriptive statistics for one scalar output variable across all valid Monte Carlo samples.
     *
     * @param mean          arithmetic mean of the sample distribution
     * @param stdDev        population standard deviation of the sample distribution
     * @param min           minimum observed value across all samples
     * @param max           maximum observed value across all samples
     * @param median        50th-percentile value (middle of the sorted sample array)
     * @param percentile05  5th-percentile value — 5 % of samples fall below this threshold
     * @param percentile95  95th-percentile value — 95 % of samples fall below this threshold
     */
    public record VariableStats(
            double mean,
            double stdDev,
            double min,
            double max,
            double median,
            double percentile05,
            double percentile95
    ) {
        /**
         * Returns the coefficient of variation (relative standard deviation) as a percentage.
         *
         * <p>Defined as {@code (stdDev / mean) × 100}.  A higher value indicates greater
         * relative dispersion.  Returns {@code 0} when the mean is zero to avoid
         * division by zero.
         *
         * @return coefficient of variation in percent, or {@code 0} if {@code mean} is zero
         */
        public double coefficientOfVariation() {
            return mean > 0 ? stdDev / mean * 100 : 0;
        }
        
        @SuppressWarnings("NullableProblems")
        @Override
        public String toString() {
            return String.format("%.4f ± %.4f (%.4f - %.4f), CV=%.2f%%",
                    mean, stdDev, min, max, coefficientOfVariation());
        }
    }
    
    /**
     * Aggregated statistical summary across all valid Monte Carlo samples,
     * covering the four key nozzle performance outputs.
     *
     * @param thrustCoefficient statistics for the actual thrust coefficient Cf distribution
     * @param specificImpulse   statistics for the delivered specific impulse Isp (seconds)
     * @param thrust            statistics for the delivered thrust (Newtons)
     * @param efficiency        statistics for the nozzle efficiency ({@code actualCf / idealCf},
     *                          dimensionless)
     * @param validSamples      number of samples that completed successfully and are
     *                          included in the statistics (it may be less than {@code numSamples}
     *                          if some evaluations failed)
     */
    public record StatisticalSummary(
            VariableStats thrustCoefficient,
            VariableStats specificImpulse,
            VariableStats thrust,
            VariableStats efficiency,
            int validSamples
    ) {
        @SuppressWarnings("NullableProblems")
        @Override
        public String toString() {
            return String.format("""
                    Monte Carlo Summary (%d valid samples):
                      Thrust Coefficient: %s
                      Specific Impulse:   %s
                      Thrust:             %s
                      Efficiency:         %s
                    """,
                    validSamples,
                    thrustCoefficient, specificImpulse, thrust, efficiency);
        }
    }
}
