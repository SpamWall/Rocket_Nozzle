package com.nozzle.optimization;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.core.PerformanceCalculator;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.DoubleStream;

/**
 * Performs Monte Carlo uncertainty propagation analysis for nozzle design.
 * Quantifies how uncertainties in input parameters affect performance.
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
     * Creates analysis with default 10000 samples.
     */
    public MonteCarloUncertainty(NozzleDesignParameters nominalParameters) {
        this(nominalParameters, 10000, System.currentTimeMillis());
    }
    
    /**
     * Adds an uncertain parameter with normal distribution.
     *
     * @param name   Parameter name
     * @param mean   Mean value
     * @param stdDev Standard deviation
     * @return This instance
     */
    public MonteCarloUncertainty addNormalParameter(String name, double mean, double stdDev) {
        uncertainParameters.put(name, new UncertainParameter(name, DistributionType.NORMAL,
                mean, stdDev, 0, 0));
        return this;
    }
    
    /**
     * Adds an uncertain parameter with uniform distribution.
     *
     * @param name Parameter name
     * @param min  Minimum value
     * @param max  Maximum value
     * @return This instance
     */
    public MonteCarloUncertainty addUniformParameter(String name, double min, double max) {
        uncertainParameters.put(name, new UncertainParameter(name, DistributionType.UNIFORM,
                (min + max) / 2, 0, min, max));
        return this;
    }
    
    /**
     * Adds typical manufacturing uncertainties.
     *
     * @return This instance
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
     * Runs the Monte Carlo analysis using virtual threads.
     *
     * @return This instance
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
     * Generates random samples from input distributions.
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
     * Samples from triangular distribution.
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
     * Evaluates a single Monte Carlo sample.
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
            return null;
        }
    }
    
    /**
     * Calculates statistical summary of results.
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
     * Calculates statistics for an array of values.
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
     * Gets the statistical summary.
     */
    public StatisticalSummary getSummary() {
        return summary;
    }
    
    /**
     * Gets all sample results.
     */
    public List<SampleResult> getSampleResults() {
        return new ArrayList<>(sampleResults);
    }
    
    /**
     * Gets correlation between input parameter and output.
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
     * Calculates Pearson correlation coefficient.
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
     * Gets sensitivity analysis results.
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
    
    // Records
    
    public enum DistributionType {
        NORMAL, UNIFORM, TRIANGULAR, LOGNORMAL
    }
    
    public record UncertainParameter(
            String name,
            DistributionType distribution,
            double mean,
            double stdDev,
            double min,
            double max
    ) {}
    
    public record SampleResult(
            int index,
            Map<String, Double> inputs,
            double thrustCoefficient,
            double specificImpulse,
            double thrust,
            double efficiency,
            double areaRatio
    ) {}
    
    public record VariableStats(
            double mean,
            double stdDev,
            double min,
            double max,
            double median,
            double percentile05,
            double percentile95
    ) {
        public double coefficientOfVariation() {
            return mean > 0 ? stdDev / mean * 100 : 0;
        }
        
        @Override
        public String toString() {
            return String.format("%.4f ± %.4f (%.4f - %.4f), CV=%.2f%%",
                    mean, stdDev, min, max, coefficientOfVariation());
        }
    }
    
    public record StatisticalSummary(
            VariableStats thrustCoefficient,
            VariableStats specificImpulse,
            VariableStats thrust,
            VariableStats efficiency,
            int validSamples
    ) {
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
