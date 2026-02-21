package com.nozzle;

import com.nozzle.chemistry.ChemistryModel;
import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.core.PerformanceCalculator;
import com.nozzle.export.*;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.moc.CharacteristicNet;
import com.nozzle.moc.RaoNozzle;
import com.nozzle.optimization.AltitudeAdaptiveOptimizer;
import com.nozzle.optimization.MonteCarloUncertainty;
import com.nozzle.thermal.BoundaryLayerCorrection;
import com.nozzle.thermal.HeatTransferModel;
import com.nozzle.validation.NASASP8120Validator;

import java.nio.file.Files;
import java.nio.file.Path;
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
 */
public class Main {
    
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
            demonstrateExports(outputDir);
            
            System.out.println("\n" + "=".repeat(70));
            System.out.println("  All demonstrations completed successfully!");
            System.out.println("  Output files saved to: " + outputDir.toAbsolutePath());
            System.out.println("=".repeat(70));
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
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
        
        // Heat transfer analysis
        HeatTransferModel heatModel = new HeatTransferModel(params, contour)
                .setWallProperties(20.0, 0.003)  // Inconel, 3mm thick
                .setCoolantProperties(300, 5000) // Regenerative cooling
                .setEmissivity(0.8)
                .calculate(net.getAllPoints());
        
        System.out.println("Heat Transfer Results:");
        System.out.printf("  Max Wall Temp:    %.0f K%n", heatModel.getMaxWallTemperature());
        System.out.printf("  Max Heat Flux:    %.2e W/m²%n", heatModel.getMaxHeatFlux());
        System.out.printf("  Total Heat Load:  %.2f kW%n", heatModel.getTotalHeatLoad() / 1000);
        
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
        
        NASASP8120Validator validator = new NASASP8120Validator(params.gasProperties().gamma());
        NASASP8120Validator.ValidationResult result = validator.validate(params);
        
        System.out.println("Validation Result: " + (result.isValid() ? "PASSED" : "FAILED"));
        
        if (!result.errors().isEmpty()) {
            System.out.println("Errors:");
            result.errors().forEach(e -> System.out.println("  - " + e));
        }
        
        if (!result.warnings().isEmpty()) {
            System.out.println("Warnings:");
            result.warnings().forEach(w -> System.out.println("  - " + w));
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
        System.out.println("Exported STL mesh (" + stlExporter.estimateTriangleCount(100) + " triangles)");
        
        // CFD Mesh Export
        CFDMeshExporter cfdExporter = new CFDMeshExporter()
                .setAxialCells(100)
                .setRadialCells(50)
                .setBoundaryLayerParams(1e-6, 1.2);
        
        cfdExporter.export(contour, outputDir.resolve("blockMeshDict"), CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);
        cfdExporter.export(contour, outputDir.resolve("nozzle.geo"), CFDMeshExporter.Format.GMSH_GEO);
        System.out.println("Exported CFD mesh files (OpenFOAM, Gmsh)");
        
        System.out.println("\nAll export files saved to: " + outputDir.toAbsolutePath());
    }
}
