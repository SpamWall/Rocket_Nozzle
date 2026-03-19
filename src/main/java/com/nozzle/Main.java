package com.nozzle;

import com.nozzle.chemistry.ChemistryModel;
import com.nozzle.core.FlowSeparationPredictor;
import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.core.PerformanceCalculator;
import com.nozzle.core.ShockExpansionModel;
import com.nozzle.export.*;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.moc.AerospikeNozzle;
import com.nozzle.moc.AltitudePerformance;
import com.nozzle.moc.CharacteristicNet;
import com.nozzle.moc.RaoNozzle;
import com.nozzle.optimization.AltitudeAdaptiveOptimizer;
import com.nozzle.optimization.MonteCarloUncertainty;
import com.nozzle.thermal.BoundaryLayerCorrection;
import com.nozzle.thermal.CoolantChannel;
import com.nozzle.thermal.HeatTransferModel;
import com.nozzle.thermal.ThermalStressAnalysis;
import com.nozzle.validation.NASASP8120Validator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Main demonstration class for the Supersonic Nozzle Design Tool.
 * Demonstrates all features including:
 * - Method of Characteristics with axisymmetric correction
 * - Rao bell nozzle comparison
 * - Thermal and boundary layer analysis
 * - Chemistry modeling
 * - CAD export (DXF, STEP, STL)
 * - NASA SP-8120 validation
 * - Altitude-adaptive optimization
 * - Monte Carlo uncertainty analysis
 * - Flow separation prediction
 * - Shock-expansion off-design analysis
 * - OpenFOAM case export (rhoCentralFoam, axisymmetric wedge)
 * - Thermal stress and fatigue life analysis (Timoshenko, Basquin, Coffin-Manson)
 * - Aerospike (plug) nozzle design via MOC kernel-flow algorithm
 * - Altitude compensation comparison: aerospike vs. bell nozzle
 * - Aerospike export: CSV spike contour, altitude performance, DXF, STEP, STL, CFD mesh
 */
public class Main {

    /** Private utility-class constructor — not instantiable. */
    private Main() {}
    
    static void main() {
        System.out.println("=".repeat(70));
        System.out.println("  SUPERSONIC NOZZLE DESIGN - METHOD OF CHARACTERISTICS");
        System.out.println("  Java 21 Implementation with Virtual Threads");
        System.out.println("=".repeat(70));
        System.out.println();
        
        try {
            // Create output directory
            Path outputDir = Path.of("nozzle_output");
            Files.createDirectories(outputDir);
            
            // Run demonstrations
            demonstrateBasicDesign(outputDir);
            demonstrateRaoComparison();
            demonstrateThermalAnalysis(outputDir);
            demonstrateChemistryModeling();
            demonstrateValidation();
            demonstrateOptimization();
            demonstrateUncertaintyAnalysis();
            demonstrateFlowSeparationAndShockExpansion();
            demonstrateThermalStressAnalysis(outputDir);
            demonstrateExports(outputDir);
            demonstrateAerospikeNozzle(outputDir);

            System.out.printf("\n%s%n", "=".repeat(70));
            System.out.println("  All demonstrations completed successfully!");
            System.out.printf("  Output files saved to: %s%n", outputDir.toAbsolutePath());
            System.out.println("=".repeat(70));

        } catch (Exception e) {
           System.err.printf("Error: %s\n%s%n", e.getMessage(), Arrays.toString(e.getStackTrace()));
        }
    }
    
    /**
     * Demonstrates basic MOC nozzle design.
     */
    private static void demonstrateBasicDesign(Path outputDir) throws Exception {
        System.out.println("\n--- BASIC MOC NOZZLE DESIGN ---\n");
        
        // Define design parameters for a LOX/RP-1 engine
        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05)              // 50 mm throat radius
                .exitMach(3.5)                   // Mach 3.5 exit
                .chamberPressure(7e6)            // 7 MPa chamber pressure
                .chamberTemperature(3500)        // 3500 K chamber temp
                .ambientPressure(101325)         // Sea level
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(30)           // 30 characteristic lines
                .wallAngleInitialDegrees(30)     // 30° initial wall angle
                .lengthFraction(0.8)             // 80% bell length
                .axisymmetric(true)
                .build();
        
        System.out.println("Design Parameters:");
        System.out.printf("  Throat Radius:      %.1f mm%n", params.throatRadius() * 1000);
        System.out.printf("  Exit Mach:          %.2f%n", params.exitMach());
        System.out.printf("  Area Ratio:         %.2f%n", params.exitAreaRatio());
        System.out.printf("  Chamber Pressure:   %.1f MPa%n", params.chamberPressure() / 1e6);
        System.out.printf("  Chamber Temp:       %.0f K%n", params.chamberTemperature());
        System.out.printf("  Gamma:              %.2f%n", params.gasProperties().gamma());
        System.out.printf("  Ideal Cf:           %.4f%n", params.idealThrustCoefficient());
        System.out.printf("  Ideal Isp:          %.1f s%n", params.idealSpecificImpulse());
        
        // Generate characteristic net
        System.out.println("\nGenerating characteristic net (using virtual threads)...");
        long startTime = System.currentTimeMillis();
        
        CharacteristicNet net = new CharacteristicNet(params).generate();
        
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.printf("  Completed in %d ms%n", elapsed);
        System.out.printf("  Total points: %d%n", net.getTotalPointCount());
        System.out.printf("  Wall points:  %d%n", net.getWallPoints().size());
        System.out.printf("  Computed A/A*: %.2f%n", net.calculateExitAreaRatio());
        
        // Calculate performance
        PerformanceCalculator perf = PerformanceCalculator.simple(params).calculate();
        System.out.println("\nPerformance:");
        System.out.printf("  Actual Cf:    %.4f%n", perf.getActualThrustCoefficient());
        System.out.printf("  Efficiency:   %.2f%%%n", perf.getEfficiency() * 100);
        System.out.printf("  Isp:          %.1f s%n", perf.getSpecificImpulse());
        System.out.printf("  Thrust:       %.2f kN%n", perf.getThrust() / 1000);
        
        // Export CSV
        CSVExporter csvExporter = new CSVExporter();
        csvExporter.exportWallContour(net, outputDir.resolve("wall_contour.csv"));
        csvExporter.exportDesignParameters(params, outputDir.resolve("design_params.csv"));
        System.out.println("\nExported wall contour and parameters to CSV");
    }
    
    /**
     * Demonstrates Rao bell nozzle comparison.
     */
    private static void demonstrateRaoComparison() {
        System.out.println("\n--- RAO BELL NOZZLE COMPARISON ---\n");
        
        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(25)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();
        
        // Generate MOC nozzle
        CharacteristicNet mocNet = new CharacteristicNet(params).generate();
        
        // Generate Rao bell nozzle
        RaoNozzle raoNozzle = new RaoNozzle(params).generate();
        
        System.out.println("Rao Bell Nozzle Properties:");
        System.out.printf("  Length:           %.4f m%n", raoNozzle.getActualLength());
        System.out.printf("  Inflection Angle: %.2f°%n", Math.toDegrees(raoNozzle.getInflectionAngle()));
        System.out.printf("  Exit Angle:       %.2f°%n", Math.toDegrees(raoNozzle.getExitAngle()));
        System.out.printf("  Thrust Coeff:     %.4f%n", raoNozzle.calculateThrustCoefficient());
        System.out.printf("  Efficiency:       %.2f%%%n", raoNozzle.calculateEfficiency() * 100);
        
        // Compare
        RaoNozzle.NozzleComparison comparison = raoNozzle.compareTo(mocNet);
        System.out.println("\nComparison MOC vs Rao:");
        System.out.printf("  Max radius diff:  %.3f mm%n", comparison.maxRadiusDifference() * 1000);
        System.out.printf("  Avg radius diff:  %.3f mm%n", comparison.avgRadiusDifference() * 1000);
        System.out.printf("  Max angle diff:   %.2f°%n", Math.toDegrees(comparison.maxAngleDifference()));
        System.out.printf("  Cf difference:    %.4f%n", comparison.thrustCoefficientDifference());
    }
    
    /**
     * Demonstrates thermal and boundary layer analysis.
     */
    private static void demonstrateThermalAnalysis(Path outputDir) throws Exception {
        System.out.println("\n--- THERMAL & BOUNDARY LAYER ANALYSIS ---\n");
        
        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();
        
        CharacteristicNet net = new CharacteristicNet(params).generate();
        NozzleContour contour = NozzleContour.fromMOCWallPoints(params, net.getWallPoints());
        contour.generate(100);
        
        // --- Coolant channel sizing ---
        // 120 rectangular RP-1 channels, 3×5 mm cross-section, 1 mm hot-wall liner
        CoolantChannel channel = new CoolantChannel(contour)
                .setChannelGeometry(120, 0.003, 0.005, 0.001)
                .setWallConductivity(20.0)                          // Inconel
                .setCoolant(CoolantChannel.CoolantProperties.RP1,
                            2.0,   // kg/s total mass flow
                            300.0, // inlet temperature (K)
                            8.5e6) // inlet pressure (Pa, slightly above Pc)
                .calculate();

        System.out.println("Coolant Channel Sizing (hydraulics only):");
        System.out.printf("  Hydraulic diameter:   %.2f mm%n",  channel.hydraulicDiameter() * 1000);
        System.out.printf("  Flow area (total):    %.2f mm²%n", channel.totalFlowArea() * 1e6);
        CoolantChannel.ChannelPoint throatChannel = channel.getProfile().getFirst();
        System.out.printf("  Velocity:             %.2f m/s%n",  throatChannel.velocity());
        System.out.printf("  Reynolds number:      %.0f%n",      throatChannel.reynoldsNumber());
        System.out.printf("  Nusselt number:       %.1f%n",      throatChannel.nusseltNumber());
        System.out.printf("  h_coolant:            %.0f W/(m²·K)%n", throatChannel.heatTransferCoeff());
        System.out.printf("  Total pressure drop:  %.2f kPa%n", channel.getTotalPressureDrop() / 1000);

        // --- Heat transfer with position-varying coolant h ---
        HeatTransferModel heatModel = new HeatTransferModel(params, contour)
                .setWallProperties(20.0, 0.003)  // Inconel, 3 mm total wall
                .setEmissivity(0.8)
                .setCoolantChannel(channel)       // replaces fixed scalar h_coolant
                .calculate(net.getAllPoints());

        System.out.println("\nHeat Transfer Results (with sized channel):");
        System.out.printf("  Max Wall Temp:    %.0f K%n",    heatModel.getMaxWallTemperature());
        System.out.printf("  Max Heat Flux:    %.2e W/m²%n", heatModel.getMaxHeatFlux());
        System.out.printf("  Total Heat Load:  %.2f kW%n",   heatModel.getTotalHeatLoad() / 1000);

        // --- Full thermal analysis of channel (boiling margin) ---
        channel.calculate(heatModel.getWallThermalProfile());

        System.out.println("\nCoolant Channel Thermal Analysis:");
        System.out.printf("  Coolant temp rise:    %.1f K%n",  channel.getCoolantTemperatureRise());
        System.out.printf("  Min boiling margin:   %.1f K%n",  channel.getMinBoilingMargin());
        System.out.printf("  Fully subcooled:      %s%n",
                channel.isFullySubcooled() ? "YES" : "NO — nucleate boiling predicted");
        
        // Boundary layer analysis
        BoundaryLayerCorrection blModel = new BoundaryLayerCorrection(params, contour)
                .setForceTurbulent(true)
                .calculate(net.getAllPoints());
        
        System.out.println("\nBoundary Layer Results:");
        System.out.printf("  Exit δ*:          %.3f mm%n", blModel.getExitDisplacementThickness() * 1000);
        System.out.printf("  Exit θ:           %.3f mm%n", blModel.getExitMomentumThickness() * 1000);
        System.out.printf("  Corrected A/A*:   %.2f%n", blModel.getCorrectedAreaRatio());
        System.out.printf("  Cf Loss:          %.4f%n", blModel.getThrustCoefficientLoss());
        
        // Export thermal data
        CSVExporter csvExporter = new CSVExporter();
        csvExporter.exportThermalProfile(heatModel, outputDir.resolve("thermal_profile.csv"));
        csvExporter.exportBoundaryLayerProfile(blModel, outputDir.resolve("boundary_layer.csv"));
        System.out.println("\nExported thermal and BL profiles to CSV");
    }
    
    /**
     * Demonstrates chemistry modeling.
     */
    private static void demonstrateChemistryModeling() {
        System.out.println("\n--- CHEMISTRY MODELING ---\n");
        
        // Frozen flow model
        ChemistryModel frozenModel = ChemistryModel.frozen(GasProperties.LOX_RP1_PRODUCTS);
        frozenModel.setLoxRp1Composition(2.5);  // O/F = 2.5
        
        System.out.println("Frozen Flow Model (LOX/RP-1, O/F=2.5):");
        System.out.printf("  Molecular Weight: %.2f kg/kmol%n", frozenModel.calculateMolecularWeight());
        System.out.printf("  Gamma at 3500K:   %.3f%n", frozenModel.calculateGamma(3500));
        System.out.printf("  Gamma at 2000K:   %.3f%n", frozenModel.calculateGamma(2000));
        System.out.printf("  Cp at 3500K:      %.0f J/(kg·K)%n", frozenModel.calculateCp(3500));
        
        // Equilibrium flow model
        ChemistryModel eqModel = ChemistryModel.equilibrium(GasProperties.LOX_LH2_PRODUCTS);
        eqModel.setLoxLh2Composition(6.0);  // O/F = 6.0
        
        System.out.println("\nEquilibrium Flow Model (LOX/LH2, O/F=6.0):");
        System.out.printf("  Molecular Weight: %.2f kg/kmol%n", eqModel.calculateMolecularWeight());
        System.out.printf("  Gamma at 3200K:   %.3f%n", eqModel.calculateGamma(3200));
        
        eqModel.calculateEquilibrium(3200, 7e6);
        System.out.println("  Species after equilibrium:");
        eqModel.getSpeciesMassFractions().forEach((species, fraction) -> 
                System.out.printf("    %s: %.1f%%%n", species, fraction * 100));
    }
    
    /**
     * Demonstrates NASA SP-8120 validation.
     */
    private static void demonstrateValidation() {
        System.out.println("\n--- NASA SP-8120 VALIDATION ---\n");
        
        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(25)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();
        
        NASASP8120Validator validator = new NASASP8120Validator();
        NASASP8120Validator.ValidationResult result = validator.validate(params);

        System.out.printf("Validation Result: %s%n", result.isValid() ? "PASSED" : "FAILED");
        
        if (!result.errors().isEmpty()) {
            System.out.println("Errors:");
            result.errors().forEach(e -> System.out.printf("  - %s%n", e));
        }
        
        if (!result.warnings().isEmpty()) {
            System.out.println("Warnings:");
            result.warnings().forEach(w -> System.out.printf("  - %s%n", w));
        }
        
        System.out.println("Key Metrics:");
        result.metrics().forEach((key, value) -> 
                System.out.printf("  %s: %.4f%n", key, value));
    }
    
    /**
     * Demonstrates altitude-adaptive optimization.
     */
    private static void demonstrateOptimization() {
        System.out.println("\n--- ALTITUDE-ADAPTIVE OPTIMIZATION ---\n");
        
        NozzleDesignParameters baseParams = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(15)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();
        
        System.out.println("Running altitude-adaptive optimization...");
        long startTime = System.currentTimeMillis();
        
        AltitudeAdaptiveOptimizer optimizer = new AltitudeAdaptiveOptimizer(baseParams)
                .addStandardProfile()
                .optimize();
        
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.printf("Completed in %d ms%n", elapsed);
        
        AltitudeAdaptiveOptimizer.OptimizationResult best = optimizer.getBestResult();
        if (best != null) {
            System.out.println("\nBest Design:");
            System.out.printf("  Exit Mach:        %.2f%n", best.parameters().exitMach());
            System.out.printf("  Area Ratio:       %.1f%n", best.parameters().exitAreaRatio());
            System.out.printf("  Length Fraction:  %.2f%n", best.parameters().lengthFraction());
            System.out.printf("  Objective (Isp):  %.1f s%n", best.objectiveValue());
        }
        
        System.out.println("\nTop 3 Designs:");
        List<AltitudeAdaptiveOptimizer.OptimizationResult> top3 = optimizer.getTopResults(3);
        for (int i = 0; i < top3.size(); i++) {
            var result = top3.get(i);
            System.out.printf("  %d. AR=%.1f, Lf=%.2f, Obj=%.1f s%n",
                    i + 1, result.parameters().exitAreaRatio(),
                    result.parameters().lengthFraction(),
                    result.objectiveValue());
        }
    }
    
    /**
     * Demonstrates Monte Carlo uncertainty analysis.
     */
    private static void demonstrateUncertaintyAnalysis() {
        System.out.println("\n--- MONTE CARLO UNCERTAINTY ANALYSIS ---\n");
        
        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(15)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();
        
        System.out.println("Running Monte Carlo analysis (1000 samples)...");
        long startTime = System.currentTimeMillis();
        
        MonteCarloUncertainty mc = new MonteCarloUncertainty(params, 1000, 42)
                .addTypicalUncertainties()
                .run();
        
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.printf("Completed in %d ms%n", elapsed);
        
        MonteCarloUncertainty.StatisticalSummary summary = mc.getSummary();
        if (summary != null) {
            System.out.println("\nResults:");
            System.out.printf("  Cf:  %.4f ± %.4f (CV=%.2f%%)%n",
                    summary.thrustCoefficient().mean(),
                    summary.thrustCoefficient().stdDev(),
                    summary.thrustCoefficient().coefficientOfVariation());
            System.out.printf("  Isp: %.1f ± %.1f s (CV=%.2f%%)%n",
                    summary.specificImpulse().mean(),
                    summary.specificImpulse().stdDev(),
                    summary.specificImpulse().coefficientOfVariation());
            System.out.printf("  95%% Isp range: %.1f - %.1f s%n",
                    summary.specificImpulse().percentile05(),
                    summary.specificImpulse().percentile95());
        }
        
        System.out.println("\nSensitivity Analysis:");
        mc.getSensitivities().forEach((param, corr) -> {
            if (param.endsWith("_Isp_correlation")) {
                String name = param.replace("_Isp_correlation", "");
                System.out.printf("  %s -> Isp: r = %.3f%n", name, corr);
            }
        });
    }
    
    /**
     * Demonstrates flow separation prediction and shock-expansion off-design analysis.
     */
    private static void demonstrateFlowSeparationAndShockExpansion() {
        System.out.println("\n--- FLOW SEPARATION & SHOCK-EXPANSION OFF-DESIGN ---\n");

        // Two nozzles: sea-level optimized (Me=3) and a vacuum bell (Me=6).
        NozzleDesignParameters seaLevel = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .build();

        NozzleDesignParameters vacuumBell = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(6.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .build();

        // ---- 1. Separation prediction (three criteria) ----
        System.out.println("Flow Separation Prediction — vacuum bell (Me=6) at sea level:");
        System.out.printf("  Design exit pressure:  %.0f Pa  (ambient: %.0f Pa)%n",
                vacuumBell.idealExitPressure(), vacuumBell.ambientPressure());
        System.out.println();

        for (FlowSeparationPredictor.Criterion c : FlowSeparationPredictor.Criterion.values()) {
            FlowSeparationPredictor.SeparationResult sep =
                    new FlowSeparationPredictor(vacuumBell).predict(c);
            System.out.printf("  [%-11s]  p_sep = %6.0f Pa  |  separated: %s",
                    c, sep.separationPressurePa(), sep.separated() ? "YES" : "NO ");
            if (sep.separated()) {
                System.out.printf("  |  M_sep=%.2f  AR_sep=%.2f  mode=%-3s  side-load=%.0f N",
                        sep.separationMach(), sep.separationAreaRatio(),
                        sep.mode(), sep.estimatedSideLoadN());
            }
            System.out.println();
        }

        // ---- 2. Shock-expansion regime sweep across altitude ----
        System.out.println("\nShock-Expansion Altitude Sweep — sea-level nozzle (Me=3):");
        System.out.printf("  %-8s  %-6s  %-25s  %-6s  %-6s  %-10s%n",
                "Alt (km)", "pa (kPa)", "Regime", "Cf", "Isp (s)", "Wave (°)");
        System.out.println("  " + "-".repeat(72));

        double[] altitudes = {0, 5, 10, 20, 40, 80};
        ShockExpansionModel seaLevelModel = new ShockExpansionModel(seaLevel);
        for (double alt : altitudes) {
            ShockExpansionModel.OffDesignResult r = seaLevelModel.computeAtAltitude(alt * 1000);
            double waveAngle = Double.isNaN(r.waveAngleDeg()) ? 0.0 : r.waveAngleDeg();
            System.out.printf("  %-8.0f  %-6.1f  %-25s  %-6.4f  %-6.1f  %-10.1f%n",
                    alt,
                    ShockExpansionModel.isaAtmosphere(alt * 1000) / 1000,
                    r.regime(),
                    r.thrustCoefficient(),
                    r.specificImpulse(),
                    waveAngle);
        }

        // ---- 3. Vacuum bell off-design at several altitudes ----
        System.out.println("\nShock-Expansion Altitude Sweep — vacuum bell (Me=6):");
        System.out.printf("  %-8s  %-6s  %-25s  %-6s  %-6s%n",
                "Alt (km)", "pa (kPa)", "Regime", "Cf", "Isp (s)");
        System.out.println("  " + "-".repeat(58));

        ShockExpansionModel vacuumModel = new ShockExpansionModel(vacuumBell);
        for (double alt : altitudes) {
            ShockExpansionModel.OffDesignResult r = vacuumModel.computeAtAltitude(alt * 1000);
            System.out.printf("  %-8.0f  %-6.1f  %-25s  %-6.4f  %-6.1f%n",
                    alt,
                    ShockExpansionModel.isaAtmosphere(alt * 1000) / 1000,
                    r.regime(),
                    r.thrustCoefficient(),
                    r.specificImpulse());
        }

        // ---- 4. Side-by-side Cf comparison at sea level ----
        System.out.println("\nCf comparison at sea level (pa = 101 325 Pa):");
        ShockExpansionModel.OffDesignResult slResult = seaLevelModel.compute(101325);
        ShockExpansionModel.OffDesignResult vacResult = vacuumModel.compute(101325);
        System.out.printf("  Sea-level nozzle (Me=3):  Cf=%.4f  Isp=%.1f s  regime=%s%n",
                slResult.thrustCoefficient(), slResult.specificImpulse(), slResult.regime());
        System.out.printf("  Vacuum bell    (Me=6):    Cf=%.4f  Isp=%.1f s  regime=%s%n",
                vacResult.thrustCoefficient(), vacResult.specificImpulse(), vacResult.regime());
        if (vacResult.separationResult() != null) {
            FlowSeparationPredictor.SeparationResult sr = vacResult.separationResult();
            System.out.printf("    → Separation at M=%.2f (%.1f%% of nozzle length),"
                            + " est. side-load %.0f N%n",
                    sr.separationMach(),
                    sr.separationAxialFraction() * 100,
                    sr.estimatedSideLoadN());
        }
    }

    /**
     * Demonstrates thermal stress and fatigue life analysis.
     * Uses the HeatTransferModel output (wall temperature + heat flux) as input
     * to ThermalStressAnalysis, which applies Timoshenko thin-shell thermal stress,
     * thin-wall Lamé pressure stress, von Mises combination, and Basquin/Coffin-Manson
     * fatigue life estimation.
     */
    private static void demonstrateThermalStressAnalysis(Path outputDir) throws Exception {
        System.out.println("\n--- THERMAL STRESS & FATIGUE LIFE ANALYSIS ---\n");

        // Same LOX/RP-1 engine as the thermal section
        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();

        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        contour.generate(60);

        // Wall properties: Cu-Cr-Zr inner liner (high-conductivity regenerative cooling)
        double wallThickness    = 0.003;   // 3 mm
        double wallConductivity = 320.0;   // W/(m·K) — Cu-Cr-Zr (C18150)

        HeatTransferModel heatModel = new HeatTransferModel(params, contour)
                .setWallProperties(wallConductivity, wallThickness)
                .setCoolantProperties(300.0, 25_000.0)   // 25 kW/(m²·K) — aggressively cooled
                .calculate(List.of());

        List<HeatTransferModel.WallThermalPoint> thermalProfile = heatModel.getWallThermalProfile();

        System.out.printf("Thermal baseline (Cu-Cr-Zr liner, k=%.0f W/(m·K), t=%.0f mm):%n",
                wallConductivity, wallThickness * 1000);
        System.out.printf("  Max wall temperature: %.0f K%n",  heatModel.getMaxWallTemperature());
        System.out.printf("  Max heat flux:        %.2e W/m²%n", heatModel.getMaxHeatFlux());
        System.out.printf("  Wall points analysed: %d%n", thermalProfile.size());

        // ---- Per-material stress + fatigue sweep ----
        System.out.println("\nMaterial comparison (same wall geometry and thermal load):");
        System.out.printf("  %-20s  %8s  %6s  %8s  %8s  %10s%n",
                "Material", "σ_VM_max", "SF_min", "ΔT_max", "N_f_min", "Regime");
        System.out.printf("  %-20s  %8s  %6s  %8s  %8s  %10s%n",
                "", "(MPa)", "(–)", "(K)", "(cycles)", "");
        System.out.println("  " + "-".repeat(68));

        ThermalStressAnalysis.Material[] materials = {
                ThermalStressAnalysis.Material.COPPER_ALLOY_CuCrZr,
                ThermalStressAnalysis.Material.INCONEL_718,
                ThermalStressAnalysis.Material.STAINLESS_304,
        };

        ThermalStressAnalysis bestAnalysis = null;

        for (ThermalStressAnalysis.Material mat : materials) {
            ThermalStressAnalysis analysis =
                    new ThermalStressAnalysis(params, thermalProfile, mat, wallThickness, wallConductivity)
                            .calculate();

            double sigVM_max = analysis.getMaxVonMisesStress();
            double sfMin     = analysis.getMinSafetyFactor();
            double dtMax     = analysis.getMaxDeltaT();
            double nfMin     = analysis.getMinFatigueCycles();
            String regime    = sigVM_max > mat.yieldStrength() ? "PLASTIC" : "elastic";
            String nfStr     = Double.isInfinite(nfMin) ? "∞" : String.format("%.1f", nfMin);

            System.out.printf("  %-20s  %8.1f  %6.2f  %8.1f  %8s  %10s%n",
                    mat.name(), sigVM_max / 1e6, sfMin, dtMax, nfStr, regime);

            if (mat == ThermalStressAnalysis.Material.COPPER_ALLOY_CuCrZr) {
                bestAnalysis = analysis;
            }
        }

        // ---- Critical-point drill-down for Cu-Cr-Zr ----
        if (bestAnalysis != null) {
            ThermalStressAnalysis.WallStressPoint cp = bestAnalysis.getCriticalPoint();
            System.out.printf("%n  Cu-Cr-Zr critical point (highest σ_VM):%n");
            System.out.printf("    Axial position:    x = %.4f m%n",   cp.x());
            System.out.printf("    Wall temperature:  T = %.0f K%n",   cp.wallTemperature());
            System.out.printf("    Through-wall ΔT:   %.1f K%n",        cp.deltaT());
            System.out.printf("    σ_thermal (hoop):  %.1f MPa%n",      cp.thermalHoopStress()  / 1e6);
            System.out.printf("    σ_hoop  (pressure): %.1f MPa%n",     cp.pressureHoopStress() / 1e6);
            System.out.printf("    σ_axial (pressure): %.1f MPa%n",     cp.pressureAxialStress()/ 1e6);
            System.out.printf("    σ_VM (combined):   %.1f MPa%n",      cp.vonMisesStress()     / 1e6);
            System.out.printf("    Safety factor:     %.2f%n",           cp.safetyFactor());
            double nf = cp.estimatedCycles();
            System.out.printf("    Fatigue life:      %s start-shutdown cycles%n",
                    Double.isInfinite(nf) ? "∞ (below endurance limit)"
                                          : String.format("%.1f", nf));

            // Export stress profile CSV
            CSVExporter csv = new CSVExporter();
            csv.exportStressProfile(bestAnalysis, outputDir.resolve("stress_profile.csv"));
            System.out.println("\nExported stress profile to: stress_profile.csv");
        }
    }

    /**
     * Demonstrates CAD exports.
     */
    private static void demonstrateExports(Path outputDir) throws Exception {
        System.out.println("\n--- CAD & MESH EXPORTS ---\n");
        
        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();
        
        CharacteristicNet net = new CharacteristicNet(params).generate();
        NozzleContour contour = NozzleContour.fromMOCWallPoints(params, net.getWallPoints());
        contour.generate(100);
        
        // DXF Export
        DXFExporter dxfExporter = new DXFExporter().setScaleFactor(1000);
        dxfExporter.exportContour(contour, outputDir.resolve("nozzle_contour.dxf"));
        dxfExporter.exportRevolutionProfile(contour, outputDir.resolve("nozzle_revolution.dxf"));
        System.out.println("Exported DXF files");
        
        // STEP Export
        STEPExporter stepExporter = new STEPExporter();
        stepExporter.exportRevolvedSolid(contour, outputDir.resolve("nozzle.step"));
        System.out.println("Exported STEP file");
        
        // STL Export
        STLExporter stlExporter = new STLExporter()
                .setCircumferentialSegments(72)
                .setScaleFactor(1000)
                .setBinaryFormat(true);
        stlExporter.exportMesh(contour, outputDir.resolve("nozzle.stl"));
        System.out.printf("Exported STL mesh (%d triangles)%n", stlExporter.estimateTriangleCount(100));
        
        // CFD Mesh Export
        CFDMeshExporter cfdExporter = new CFDMeshExporter()
                .setAxialCells(100)
                .setRadialCells(50)
                .setBoundaryLayerParams(1e-6, 1.2);
        
        cfdExporter.export(contour, outputDir.resolve("blockMeshDict"), CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);
        cfdExporter.export(contour, outputDir.resolve("nozzle.geo"), CFDMeshExporter.Format.GMSH_GEO);
        System.out.println("Exported CFD mesh files (OpenFOAM, Gmsh)");

        // OpenFOAM complete case export (rhoCentralFoam)
        Path foamCase = outputDir.resolve("openfoam_case");
        new OpenFOAMExporter()
                .setAxialCells(300)
                .setRadialCells(100)
                .setRadialGrading(5.0)
                .setTurbulenceIntensity(0.05)
                .exportCase(params, contour, foamCase);
        System.out.printf("Exported OpenFOAM case → %s%n", foamCase.toAbsolutePath());
        System.out.println("  Run with: blockMesh && rhoCentralFoam");
        System.out.printf("  Files: %s%n",
                String.join(", ",
                        "system/blockMeshDict", "system/controlDict",
                        "system/fvSchemes",     "system/fvSolution",
                        "constant/thermophysicalProperties", "constant/turbulenceProperties",
                        "0/p", "0/T", "0/U", "0/k", "0/omega"));

        System.out.println("\nAll export files saved to: " + outputDir.toAbsolutePath());
    }

    /**
     * Demonstrates the aerospike (plug) nozzle: design, geometry, performance, altitude
     * compensation, and all six new export formats.
     */
    private static void demonstrateAerospikeNozzle(Path outputDir) throws Exception {
        System.out.println("\n--- AEROSPIKE (PLUG) NOZZLE ---\n");

        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05)          // 50 mm outer throat radius
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();

        // ---- 1. Generate spike contour ----
        System.out.println("Generating aerospike nozzle (60% spike radius ratio, 80% truncation)...");
        long t0 = System.currentTimeMillis();
        AerospikeNozzle nozzle = new AerospikeNozzle(params, 0.60, 0.80, 100).generate();
        System.out.printf("  Completed in %d ms%n", System.currentTimeMillis() - t0);

        // ---- 2. Spike geometry ----
        double rt  = params.throatRadius();
        double ri  = rt * nozzle.getSpikeRadiusRatio();
        System.out.println("\nSpike Geometry:");
        System.out.printf("  Outer throat radius (rt):     %.1f mm%n",  rt  * 1000);
        System.out.printf("  Inner throat radius (ri):     %.1f mm%n",  ri  * 1000);
        System.out.printf("  Spike radius ratio (ri/rt):   %.2f%n",     nozzle.getSpikeRadiusRatio());
        System.out.printf("  Truncation fraction:          %.2f%n",     nozzle.getTruncationFraction());
        System.out.printf("  Full spike length:            %.4f m%n",   nozzle.getFullSpikeLength());
        System.out.printf("  Truncated spike length:       %.4f m%n",   nozzle.getTruncatedLength());
        System.out.printf("  Truncated base radius:        %.2f mm%n",  nozzle.getTruncatedBaseRadius() * 1000);
        System.out.printf("  Spike contour points (full):  %d%n",       nozzle.getFullSpikeContour().size());
        System.out.printf("  Spike contour points (trunc): %d%n",       nozzle.getTruncatedSpikeContour().size());

        // ---- 3. Annular area calculations ----
        System.out.println("\nAnnular Flow Areas:");
        System.out.printf("  Throat area (At):   %.4f cm²%n", nozzle.getAnnularThroatArea() * 1e4);
        System.out.printf("  Exit area   (Ae):   %.4f cm²%n", nozzle.getAnnularExitArea()   * 1e4);
        System.out.printf("  Ae/At ratio:        %.3f  (design: %.3f)%n",
                nozzle.getAnnularExitArea() / nozzle.getAnnularThroatArea(),
                params.exitAreaRatio());

        // ---- 4. Thrust coefficient vs. bell nozzle ----
        System.out.println("\nThrust Coefficient Comparison (Aerospike vs. Bell Nozzle):");
        System.out.printf("  %-18s  %-10s  %-10s  %-10s%n",
                "Condition", "Aerospike", "Bell", "Advantage");
        System.out.println("  " + "-".repeat(54));
        double[][] conditions = {
            {101325,  0},   // sea level
            { 50000,  5},   // ~5 km
            { 20000, 12},   // ~12 km
            {  5000, 20},   // ~20 km
            {  1000, 32},   // ~32 km
        };
        for (double[] c : conditions) {
            double pa  = c[0];
            double alt = c[1];
            double cfA = nozzle.calculateThrustCoefficient(pa);
            double cfB = nozzle.calculateBellNozzleThrustCoefficient(pa);
            System.out.printf("  pa=%.0f Pa (~%.0f km)  %.4f     %.4f     %+.4f%n",
                    pa, alt, cfA, cfB, cfA - cfB);
        }

        // ---- 5. Altitude performance sweep ----
        double[] altitudePressures = {101325, 70000, 50000, 30000, 20000, 10000, 5000, 2000, 1000};
        AltitudePerformance perf = nozzle.calculateAltitudePerformance(altitudePressures);

        System.out.println("\nAltitude Performance Sweep:");
        System.out.printf("  %-14s  %-10s  %-10s  %-12s%n",
                "pa (Pa)", "Aero Cf", "Bell Cf", "Aero Isp (s)");
        System.out.println("  " + "-".repeat(50));
        for (int i = 0; i < altitudePressures.length; i++) {
            System.out.printf("  %-14.0f  %-10.4f  %-10.4f  %-12.1f%n",
                    perf.ambientPressures()[i],
                    perf.aerospikeCf()[i],
                    perf.bellNozzleCf()[i],
                    perf.aerospikeIsp()[i]);
        }

        int iMaxAdv = perf.indexOfMaxAltitudeAdvantage();
        System.out.printf("%n  Max altitude advantage at pa=%.0f Pa: +%.4f Cf%n",
                perf.ambientPressures()[iMaxAdv],
                perf.aerospikeCf()[iMaxAdv] - perf.bellNozzleCf()[iMaxAdv]);
        System.out.printf("  Average advantage over sweep:         +%.4f Cf%n",
                perf.averageAltitudeAdvantage());

        // ---- 6. Isp at sea level ----
        System.out.printf("%n  Isp at sea level:  %.1f s%n", nozzle.calculateIsp(101325));
        System.out.printf("  Isp at ~vacuum:    %.1f s%n",   nozzle.calculateIsp(1000));

        // ---- 7. Exports ----
        System.out.println("\nAerospike Exports:");
        Path aeroDir = outputDir.resolve("aerospike");
        Files.createDirectories(aeroDir);

        // CSV
        CSVExporter csv = new CSVExporter();
        csv.exportSpikeContour(nozzle, aeroDir.resolve("spike_contour.csv"));
        csv.exportAltitudePerformance(perf, aeroDir.resolve("altitude_performance.csv"));
        csv.exportAerospikeReport(nozzle, altitudePressures, aeroDir.resolve("report"));
        System.out.println("  CSV: spike_contour.csv, altitude_performance.csv, report/");

        // DXF
        new DXFExporter().setScaleFactor(1000)
                .exportAerospikeContour(nozzle, aeroDir.resolve("aerospike.dxf"));
        System.out.println("  DXF: aerospike.dxf  (layers: SPIKE, COWL, AXIS)");

        // STEP
        new STEPExporter().exportAerospikeRevolvedSolid(nozzle, aeroDir.resolve("aerospike.step"));
        System.out.println("  STEP: aerospike.step  (truncated spike, revolved solid)");

        // STL
        STLExporter stl = new STLExporter().setCircumferentialSegments(72).setBinaryFormat(true);
        stl.exportAerospikeMesh(nozzle, aeroDir.resolve("aerospike.stl"));
        System.out.printf("  STL:  aerospike.stl  (%d triangles, binary)%n",
                stl.estimateTriangleCount(nozzle.getTruncatedSpikeContour().size()));

        // CFD meshes
        CFDMeshExporter cfd = new CFDMeshExporter().setAxialCells(100).setRadialCells(50);
        cfd.exportAerospike(nozzle, aeroDir.resolve("aerospike_blockMeshDict"),
                CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);
        cfd.exportAerospike(nozzle, aeroDir.resolve("aerospike.geo"),
                CFDMeshExporter.Format.GMSH_GEO);
        cfd.exportAerospike(nozzle, aeroDir.resolve("aerospike.xyz"),
                CFDMeshExporter.Format.PLOT3D);
        System.out.println("  CFD:  aerospike_blockMeshDict, aerospike.geo, aerospike.xyz");
        System.out.printf("        (annular domain: spike inner wall → r=%.0f mm outer cowl)%n",
                rt * 1000);

        System.out.printf("%nAerospike output saved to: %s%n", aeroDir.toAbsolutePath());
    }
}
