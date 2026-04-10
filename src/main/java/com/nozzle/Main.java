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

package com.nozzle;

import com.nozzle.chemistry.ChemistryModel;
import com.nozzle.chemistry.OFSweep;
import com.nozzle.core.FlowSeparationPredictor;
import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.core.PerformanceCalculator;
import com.nozzle.core.ShockExpansionModel;
import com.nozzle.export.*;
import com.nozzle.geometry.ConvergentSection;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.moc.AerospikeNozzle;
import com.nozzle.moc.AltitudePerformance;
import com.nozzle.moc.CharacteristicNet;
import com.nozzle.moc.DualBellNozzle;
import com.nozzle.moc.RaoNozzle;
import com.nozzle.optimization.AltitudeAdaptiveOptimizer;
import com.nozzle.optimization.MonteCarloUncertainty;
import com.nozzle.io.DesignDocument;
import com.nozzle.io.NozzleSerializer;
import com.nozzle.thermal.AblativeNozzleModel;
import com.nozzle.thermal.BoundaryLayerCorrection;
import com.nozzle.thermal.RadiationCooledExtension;
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
 * - Ablative liner analysis: Arrhenius char rate, mechanical erosion supplement, ablated mass budget
 * - Radiation-cooled extension analysis: equilibrium wall temperature, material comparison, overtemperature detection
 * - y⁺-controlled first-cell-height grading: CFDMeshExporter and OpenFOAMExporter firstLayerThickness
 * - Full 3-D revolved mesh export: RevolvedMeshExporter (OpenFOAM blockMesh, Gmsh, Plot3D)
 * - O/F sweep and optimum search: OFSweep adiabatic Isp(O/F), c*(O/F), γ(O/F) curves with golden-section optimum
 * - Dual-bell altitude-compensating nozzle: DualBellNozzle contour, sea-level and high-altitude Isp, transition pressure
 * - Throat curvature ratio sweep: effect of r_cd/r_t on exit area ratio and Cf across Rao, MOC, and conical contours
 * - Convergent section: full nozzle geometry (chamber face to exit), sonic-line Cd correction, upstream BL extension,
 *   geometry-complete exports
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
            demonstrateTruncatedIdealContour();
            demonstrateThermalAnalysis(outputDir);
            demonstrateChemistryModeling();
            demonstrateValidation();
            demonstrateOptimization();
            demonstrateUncertaintyAnalysis();
            demonstrateFlowSeparationAndShockExpansion();
            demonstrateThermalStressAnalysis(outputDir);
            demonstrateExports(outputDir);
            demonstrateAerospikeNozzle(outputDir);
            demonstrateAblativeLiner();
            demonstrateRadiationCooledExtension();
            demonstrateSerialization(outputDir);
            demonstrateYPlusGrading(outputDir);
            demonstrateRevolvedMesh(outputDir);
            demonstrateOFSweep();
            demonstrateDualBellNozzle();
            demonstrateThroatCurvatureRatio();
            demonstrateConvergentSection(outputDir);

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
     * Demonstrates Truncated Ideal Contour (TIC) as a fifth contour family.
     *
     * <p>Compares TIC at three truncation fractions against the Rao bell for the
     * same design Mach.  Shows how the TIC exit radius and nozzle length change
     * with truncation, and that f = 1 recovers the full ideal exit radius.
     */
    private static void demonstrateTruncatedIdealContour() {
        System.out.println("\n--- TRUNCATED IDEAL CONTOUR (TIC) ---\n");

        final double RT      = 0.05;
        final double M_D     = 3.0;
        final double THETA_N = 30.0;   // initial wall angle (degrees)

        NozzleDesignParameters base = NozzleDesignParameters.builder()
                .throatRadius(RT)
                .exitMach(M_D)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(25)
                .wallAngleInitialDegrees(THETA_N)
                .lengthFraction(0.8)          // overridden per row below
                .axisymmetric(true)
                .build();

        System.out.printf("Design Mach M_D = %.1f  (full exit A/A* = %.2f,  r_e = %.1f mm)%n",
                M_D, base.exitAreaRatio(), base.exitRadius() * 1000);
        System.out.printf("Throat radius   = %.0f mm   Initial wall angle θ_n = %.0f°%n%n",
                RT * 1000, THETA_N);

        System.out.println("  f     TIC exit A/A*   TIC exit r (mm)   TIC length (mm)   θ_exit (°)");
        System.out.println("  " + "-".repeat(65));

        for (double f : new double[]{0.6, 0.8, 1.0}) {
            NozzleDesignParameters p = NozzleDesignParameters.builder()
                    .throatRadius(RT).exitMach(M_D).chamberPressure(7e6)
                    .chamberTemperature(3500).ambientPressure(101325)
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .numberOfCharLines(25).wallAngleInitialDegrees(THETA_N)
                    .lengthFraction(f).axisymmetric(true).build();

            NozzleContour tic = new NozzleContour(NozzleContour.ContourType.TRUNCATED_IDEAL, p)
                    .generate(200);

            double rTIC     = tic.getContourPoints().getLast().y();
            double arTIC    = (rTIC / RT) * (rTIC / RT);   // (r/r_t)^2 = A/A*
            double lTIC     = tic.getLength() * 1000;       // mm
            double thetaE   = (1.0 - f) * THETA_N;

            System.out.printf("  %.1f   %10.3f       %12.2f        %12.2f       %5.2f°%n",
                    f, arTIC, rTIC * 1000, lTIC, thetaE);
        }

        // Compare TIC (f=0.8) with Rao bell (f=0.8) side by side
        NozzleContour tic = new NozzleContour(NozzleContour.ContourType.TRUNCATED_IDEAL, base)
                .generate(200);
        NozzleContour rao = new NozzleContour(NozzleContour.ContourType.RAO_BELL, base)
                .generate(200);

        System.out.println();
        System.out.printf("Comparison at f = 0.8 (θ_n = %.0f°):%n", THETA_N);
        System.out.printf("  TIC:      length = %6.2f mm   exit r = %5.2f mm   exit A/A* = %5.2f%n",
                tic.getLength() * 1000, tic.getContourPoints().getLast().y() * 1000,
                Math.pow(tic.getContourPoints().getLast().y() / RT, 2));
        System.out.printf("  Rao bell: length = %6.2f mm   exit r = %5.2f mm   exit A/A* = %5.2f%n",
                rao.getLength() * 1000, rao.getContourPoints().getLast().y() * 1000,
                Math.pow(rao.getContourPoints().getLast().y() / RT, 2));
        System.out.println("  (TIC exits at a lower A/A* because truncation stops before full M_D)");
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
        eqModel.calculateEquilibrium(3200, 7e6);

        System.out.println("\nEquilibrium Flow Model (LOX/LH2, O/F=6.0, T=3200K, P=7MPa):");
        System.out.printf("  Molecular Weight:        %.3f kg/kmol%n", eqModel.calculateMolecularWeight());
        System.out.printf("  Frozen gamma:            %.4f%n", eqModel.calculateGamma(3200));
        System.out.printf("  Equilibrium gamma (CEA): %.4f%n", eqModel.calculateEquilibriumGamma(3200, 7e6));
        System.out.println("  Species mass fractions:");
        eqModel.getSpeciesMassFractions().forEach((species, fraction) ->
                System.out.printf("    %s: %.1f%%%n", species, fraction * 100));

        // Frozen vs. equilibrium Isp comparison across propellant families
        System.out.println("\nFrozen vs. Equilibrium Isp  (Tc=3500 K  Pc=7 MPa  Me=3.0  Pa=101 325 Pa)");
        System.out.println("  Propellant          Frozen (s)  Equil. (s)  ΔIsp (s)  Δ%");
        System.out.println("  " + "-".repeat(58));
        record IspEntry(String label, ChemistryModel model) {}
        ChemistryModel lh2Isp = ChemistryModel.equilibrium(GasProperties.LOX_LH2_PRODUCTS);
        lh2Isp.setLoxLh2Composition(6.0);
        lh2Isp.calculateEquilibrium(3500, 7e6);
        ChemistryModel rp1Isp = ChemistryModel.equilibrium(GasProperties.LOX_RP1_PRODUCTS);
        rp1Isp.setLoxRp1Composition(2.77);
        rp1Isp.calculateEquilibrium(3500, 7e6);
        ChemistryModel ch4Isp = ChemistryModel.equilibrium(GasProperties.LOX_CH4_PRODUCTS);
        ch4Isp.setLoxCh4Composition(3.5);
        ch4Isp.calculateEquilibrium(3500, 7e6);
        ChemistryModel n2oIsp = ChemistryModel.equilibrium(GasProperties.N2O_ETHANOL_PRODUCTS);
        n2oIsp.setN2oEthanolComposition(5.0);
        n2oIsp.calculateEquilibrium(3500, 7e6);
        for (IspEntry e : List.of(
                new IspEntry("LOX/LH2   (O/F=6.0) ", lh2Isp),
                new IspEntry("LOX/RP-1  (O/F=2.77)", rp1Isp),
                new IspEntry("LOX/CH4   (O/F=3.5) ", ch4Isp),
                new IspEntry("N2O/EtOH  (O/F=5.0) ", n2oIsp))) {
            ChemistryModel.IspComparison cmp = e.model().compareIsp(3500, 7e6, 3.0, 101_325.0);
            System.out.printf("  %s  %7.1f     %7.1f     %5.1f   %4.2f%%%n",
                    e.label(), cmp.frozenIsp(), cmp.equilibriumIsp(),
                    cmp.delta(), cmp.deltaPercent());
        }
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
                .setExpansionRatio(1.2);
        
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
        System.out.println("Generating Aerospike nozzle (60% spike radius ratio, 80% truncation)...");
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
        Path aeroDir = outputDir.resolve("Aerospike");
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
        cfd.exportAerospike(nozzle, aeroDir.resolve("Aerospike_blockMeshDict"),
                CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);
        cfd.exportAerospike(nozzle, aeroDir.resolve("aerospike.geo"),
                CFDMeshExporter.Format.GMSH_GEO);
        cfd.exportAerospike(nozzle, aeroDir.resolve("aerospike.xyz"),
                CFDMeshExporter.Format.PLOT3D);
        System.out.println("  CFD:  Aerospike_blockMeshDict, aerospike.geo, aerospike.xyz");
        System.out.printf("        (annular domain: spike inner wall → r=%.0f mm outer cowl)%n",
                rt * 1000);

        System.out.printf("%nAerospike output saved to: %s%n", aeroDir.toAbsolutePath());
    }

    /**
     * Demonstrates the ablative liner char-rate model for solid rocket motor nozzles.
     *
     * <p>Shows:
     * <ul>
     *   <li>Material char-rate curves via {@code charRateAt(T)} — no full model required</li>
     *   <li>Full recession profile driven by the Bartz heat-transfer output</li>
     *   <li>Mechanical (particle-impingement) erosion supplement for aluminised SRM propellants</li>
     *   <li>Ablated mass budget: per-station [kg/m²] and total [kg]</li>
     * </ul>
     */
    private static void demonstrateAblativeLiner() {
        System.out.println("\n--- ABLATIVE LINER ANALYSIS (SRM NOZZLE) ---\n");

        // SRM-representative parameters: higher chamber pressure, shorter burn time,
        // cooler chamber temp (solid propellant vs. liquid bipropellant).
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

        // ---- 1. Material char-rate curves (no model run needed) ----
        System.out.println("Arrhenius Char Rate at Key Temperatures [mm/s]:");
        System.out.printf("  %-28s  %8s  %8s  %8s  %8s%n",
                "Material", "500 K", "1000 K", "1500 K", "2500 K");
        System.out.println("  " + "-".repeat(64));

        AblativeNozzleModel.AblativeMaterial[] materials = {
                AblativeNozzleModel.AblativeMaterial.CARBON_PHENOLIC,
                AblativeNozzleModel.AblativeMaterial.SILICA_PHENOLIC,
                AblativeNozzleModel.AblativeMaterial.EPDM,
                AblativeNozzleModel.AblativeMaterial.GRAPHITE,
                AblativeNozzleModel.AblativeMaterial.CARBON_CARBON,
        };

        for (AblativeNozzleModel.AblativeMaterial mat : materials) {
            System.out.printf("  %-28s  %8.4f  %8.4f  %8.4f  %8.4f%n",
                    mat.name(),
                    mat.charRateAt(500)  * 1000,
                    mat.charRateAt(1000) * 1000,
                    mat.charRateAt(1500) * 1000,
                    mat.charRateAt(2500) * 1000);
        }

        // ---- 2. Build heat-transfer profile for the throat region ----
        List<HeatTransferModel.WallThermalPoint> heatProfile =
                new HeatTransferModel(params, contour)
                        .setWallProperties(1.0, 0.020)     // carbon-phenolic k, 20 mm liner
                        .setCoolantProperties(300.0, 0.0)  // no active cooling (ablative only)
                        .calculate(List.of())
                        .getWallThermalProfile();

        System.out.printf("%nHeat-transfer baseline:%n");
        System.out.printf("  Max gas-side wall temperature: %.0f K%n",
                heatProfile.stream().mapToDouble(HeatTransferModel.WallThermalPoint::wallTemperature)
                        .max().orElse(0));

        // ---- 3. Pure Arrhenius recession — material comparison ----
        double burnTime       = 10.0;   // s
        double linerThickness = 0.020;  // 20 mm

        System.out.printf("%nPure Arrhenius Recession after %.0f s burn (%.0f mm liner):%n",
                burnTime, linerThickness * 1000);
        System.out.printf("  %-28s  %12s  %12s  %10s%n",
                "Material", "Max recess (mm)", "Min remain (mm)", "Perforated");
        System.out.println("  " + "-".repeat(68));

        for (AblativeNozzleModel.AblativeMaterial mat : materials) {
            AblativeNozzleModel mdl = new AblativeNozzleModel(params, contour)
                    .setMaterial(mat)
                    .setInitialLinerThickness(linerThickness)
                    .setBurnTime(burnTime)
                    .calculate(heatProfile);

            System.out.printf("  %-28s  %12.4f  %12.4f  %10s%n",
                    mat.name(),
                    mdl.getMaxRecessionDepth()      * 1000,
                    mdl.getMinRemainingThickness()  * 1000,
                    mdl.isPerforatedAnywhere() ? "YES" : "no");
        }

        // ---- 4. Mechanical erosion supplement (aluminised SRM propellant) ----
        // Alumina particles in the exhaust add an impingement erosion term:
        //   ṙ_mech = k_e · (P_c / 1 MPa)^0.8
        // k_e = 1e-5 m/s is representative for a carbon-phenolic throat with ~15% Al loading.
        double k_e = 1.0e-5;  // m/s

        AblativeNozzleModel pureArrhenius = new AblativeNozzleModel(params, contour)
                .setMaterial(AblativeNozzleModel.AblativeMaterial.CARBON_PHENOLIC)
                .setInitialLinerThickness(linerThickness)
                .setBurnTime(burnTime)
                .setErosionFactor(0.0)
                .calculate(heatProfile);

        AblativeNozzleModel withErosion = new AblativeNozzleModel(params, contour)
                .setMaterial(AblativeNozzleModel.AblativeMaterial.CARBON_PHENOLIC)
                .setInitialLinerThickness(linerThickness)
                .setBurnTime(burnTime)
                .setErosionFactor(k_e)
                .calculate(heatProfile);

        System.out.printf("%nMechanical Erosion Supplement (carbon-phenolic, k_e = %.0e m/s):%n", k_e);
        System.out.printf("  Pure Arrhenius:  max recession = %.4f mm%n",
                pureArrhenius.getMaxRecessionDepth() * 1000);
        System.out.printf("  With erosion:    max recession = %.4f mm   (+%.2f%%)%n",
                withErosion.getMaxRecessionDepth() * 1000,
                (withErosion.getMaxRecessionDepth() / pureArrhenius.getMaxRecessionDepth() - 1.0) * 100);

        // ---- 5. Ablated mass budget ----
        System.out.printf("%nAblated Mass Budget (carbon-phenolic, with erosion):%n");
        System.out.printf("  Total ablated mass:  %.4f kg%n", withErosion.getTotalAblatedMass());

        // Print per-station summary for the first 3 and last 3 stations
        List<AblativeNozzleModel.AblativePoint> profile  = withErosion.getProfile();
        List<Double>                             massPerStation = withErosion.getAblatedMassPerStation();
        int n = profile.size();
        System.out.printf("  Per-station Δm/A [kg/m²]  (showing first 3 and last 3 of %d):%n", n);
        System.out.printf("    %-10s  %-12s  %s%n", "x (m)", "r (mm)", "Δm/A (kg/m²)");

        // Build a deduplicated, ordered set of head/tail indices safe for any profile size.
        java.util.LinkedHashSet<Integer> indexSet = new java.util.LinkedHashSet<>();
        for (int i = 0; i < Math.min(3, n); i++) indexSet.add(i);
        for (int i = Math.max(0, n - 3); i < n; i++) indexSet.add(i);

        int prev = -1;
        for (int i : indexSet) {
            if (prev >= 0 && i > prev + 1) System.out.println("    ...");
            AblativeNozzleModel.AblativePoint pt = profile.get(i);
            System.out.printf("    %-10.4f  %-12.2f  %.4f%n",
                    pt.x(), pt.y() * 1000, massPerStation.get(i));
            prev = i;
        }

        // ---- 6. Carbon-carbon throat insert scenario ----
        // C/C is used at the throat where heat flux peaks; Arrhenius recession is negligible
        // but mechanical erosion from alumina drives the total wear rate.
        AblativeNozzleModel ccThroat = new AblativeNozzleModel(params, contour)
                .setMaterial(AblativeNozzleModel.AblativeMaterial.CARBON_CARBON)
                .setInitialLinerThickness(0.010)   // 10 mm C/C insert
                .setBurnTime(burnTime)
                .setErosionFactor(5.0e-6)          // lower erosion factor — C/C resists particles better
                .calculate(heatProfile);

        System.out.printf("%nCarbon-Carbon Throat Insert (10 mm, k_e = 5×10⁻⁶ m/s):%n");
        System.out.printf("  Max recession:    %.4f mm%n", ccThroat.getMaxRecessionDepth() * 1000);
        System.out.printf("  Min remaining:    %.4f mm%n", ccThroat.getMinRemainingThickness() * 1000);
        System.out.printf("  Total ablated mass: %.6f kg%n", ccThroat.getTotalAblatedMass());
        System.out.printf("  Perforated:       %s%n", ccThroat.isPerforatedAnywhere() ? "YES" : "no");
    }

    /**
     * Demonstrates the radiation-cooled extension model.
     *
     * <p>Shows:
     * <ul>
     *   <li>Equilibrium wall temperature profile driven by the Bartz/Eckert
     *       convective model balanced against surface radiation</li>
     *   <li>Material comparison across all four presets</li>
     *   <li>Effect of environment temperature (space vs. sea-level test)</li>
     *   <li>Overtemperature detection and temperature margin</li>
     *   <li>Total radiated power budget</li>
     * </ul>
     */
    private static void demonstrateRadiationCooledExtension() {
        System.out.println("\n--- RADIATION-COOLED NOZZLE EXTENSION ---\n");

        // RL-10-representative LOX/LH2 upper-stage engine (moderate chamber pressure,
        // high-expansion ratio, long burn).  The bell nozzle is regeneratively cooled
        // up to the throat region; the extension is radiation-cooled.
        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(4.0)
                .chamberPressure(3.5e6)          // 3.5 MPa — representative upper stage
                .chamberTemperature(3250)         // LOX/LH2 combustion temperature
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_LH2_PRODUCTS)
                .numberOfCharLines(20)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();

        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        contour.generate(60);

        // The regeneratively cooled section ends at x ≈ 30 % of the bell length;
        // the radiation-cooled extension begins there.
        double extensionStartX = contour.getLength() * 0.30;

        // ---- 1. Baseline: Niobium C-103 in space vacuum ----
        RadiationCooledExtension baseModel = new RadiationCooledExtension(params, contour)
                .setMaterial(RadiationCooledExtension.ExtensionMaterial.NIOBIUM_C103)
                .setExtensionStartX(extensionStartX)
                .setEnvironmentTemperature(3.0)   // deep-space vacuum
                .calculate(List.of());

        System.out.printf("Extension starts at x = %.4f m (%.0f%% of bell length)%n",
                extensionStartX, 30.0);
        System.out.printf("Extension stations in profile: %d%n%n", baseModel.getProfile().size());

        System.out.printf("Niobium C-103 baseline (space, T_env = 3 K):%n");
        System.out.printf("  Max wall temperature:   %.0f K   (limit: %.0f K)%n",
                baseModel.getMaxWallTemperature(),
                RadiationCooledExtension.ExtensionMaterial.NIOBIUM_C103.temperatureLimit());
        System.out.printf("  Min temperature margin: %.0f K%n",
                baseModel.getMinTemperatureMargin());
        System.out.printf("  Total radiated power:   %.2f kW%n",
                baseModel.getTotalRadiatedPower() / 1000);
        System.out.printf("  Overtemperature:        %s%n",
                baseModel.isOvertemperatureAnywhere() ? "YES" : "no");

        // ---- 2. Station-by-station profile (first 3 and last 3) ----
        List<RadiationCooledExtension.ExtensionPoint> extProfile = baseModel.getProfile();
        int n = extProfile.size();
        if (n > 0) {
            System.out.printf("%n  Station profile (x, r, T_wall, T_aw, margin):%n");
            System.out.printf("    %-10s  %-8s  %-10s  %-10s  %-10s%n",
                    "x (m)", "r (mm)", "T_wall (K)", "T_aw (K)", "margin (K)");

            java.util.LinkedHashSet<Integer> indexSet = new java.util.LinkedHashSet<>();
            for (int i = 0; i < Math.min(3, n); i++) indexSet.add(i);
            for (int i = Math.max(0, n - 3); i < n; i++) indexSet.add(i);

            int prev = -1;
            for (int i : indexSet) {
                if (prev >= 0 && i > prev + 1) System.out.println("    ...");
                RadiationCooledExtension.ExtensionPoint pt = extProfile.get(i);
                System.out.printf("    %-10.4f  %-8.2f  %-10.0f  %-10.0f  %-10.0f%n",
                        pt.x(), pt.y() * 1000,
                        pt.wallTemperature(), pt.recoveryTemperature(), pt.temperatureMargin());
                prev = i;
            }
        }

        // ---- 3. Material comparison ----
        RadiationCooledExtension.ExtensionMaterial[] materials = {
                RadiationCooledExtension.ExtensionMaterial.NIOBIUM_C103,
                RadiationCooledExtension.ExtensionMaterial.RHENIUM_IRIDIUM,
                RadiationCooledExtension.ExtensionMaterial.TITANIUM_6AL_4V,
                RadiationCooledExtension.ExtensionMaterial.CARBON_CARBON,
        };

        System.out.printf("%nMaterial comparison (space environment, same extension geometry):%n");
        System.out.printf("  %-34s  %10s  %10s  %10s  %10s%n",
                "Material", "Max T (K)", "Margin (K)", "Power (kW)", "Overtemp");
        System.out.println("  " + "-".repeat(82));

        for (RadiationCooledExtension.ExtensionMaterial mat : materials) {
            RadiationCooledExtension mdl = new RadiationCooledExtension(params, contour)
                    .setMaterial(mat)
                    .setExtensionStartX(extensionStartX)
                    .setEnvironmentTemperature(3.0)
                    .calculate(List.of());

            System.out.printf("  %-34s  %10.0f  %10.0f  %10.2f  %10s%n",
                    mat.name(),
                    mdl.getMaxWallTemperature(),
                    mdl.getMinTemperatureMargin(),
                    mdl.getTotalRadiatedPower() / 1000,
                    mdl.isOvertemperatureAnywhere() ? "YES" : "no");
        }

        // ---- 4. Ground test vs. space: effect of environment temperature ----
        RadiationCooledExtension groundTest = new RadiationCooledExtension(params, contour)
                .setMaterial(RadiationCooledExtension.ExtensionMaterial.NIOBIUM_C103)
                .setExtensionStartX(extensionStartX)
                .setEnvironmentTemperature(300.0)   // sea-level ground test
                .calculate(List.of());

        System.out.printf("%nEnvironment temperature effect (Niobium C-103, same extension):%n");
        System.out.printf("  Space  (T_env =   3 K): max T_wall = %.0f K, margin = %.0f K%n",
                baseModel.getMaxWallTemperature(), baseModel.getMinTemperatureMargin());
        System.out.printf("  Ground (T_env = 300 K): max T_wall = %.0f K, margin = %.0f K%n",
                groundTest.getMaxWallTemperature(), groundTest.getMinTemperatureMargin());
        System.out.printf("  Wall temperature increase on ground: +%.0f K%n",
                groundTest.getMaxWallTemperature() - baseModel.getMaxWallTemperature());
    }

   /**
     * Demonstrates a three-stage persistent workflow using {@link NozzleSerializer}.
     *
     * <p>Each stage represents a separate program invocation that loads from the
     * file written by the previous stage, mirroring how a real design tool would
     * separate initial parameterization, expensive computation, and downstream
     * analysis into independent sessions.
     *
     * <ul>
     *   <li><b>Stage 1 – Capture design intent:</b> build parameters and save a
     *       parameters-only document.  No solver is run.</li>
     *   <li><b>Stage 2 – Solve and checkpoint:</b> load the intent document, run
     *       the MOC solver and contour generator, then overwrite the file with a
     *       full design document containing the computed flow field.</li>
     *   <li><b>Stage 3 – Reload and analyze:</b> load the full document and run
     *       performance and validation calculations directly from the restored
     *       parameters — the MOC solver is not re-executed.</li>
     * </ul>
     */
    private static void demonstrateSerialization(Path outputDir) {
        System.out.println("\n--- JSON SERIALIZATION (PERSISTENT WORKFLOW) ---\n");

        Path checkpoint = outputDir.resolve("workflow_design.json");

        // ----------------------------------------------------------------
        // Stage 1 — Capture design intent (no computation yet)
        // ----------------------------------------------------------------
        System.out.println("  Stage 1: Capture design intent");

        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.5)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .build();

        NozzleSerializer.save(NozzleSerializer.document(params), checkpoint);
        System.out.printf("    Parameters saved to:    %s%n", checkpoint.getFileName());
        System.out.printf("    Throat radius:          %.1f mm%n", params.throatRadius() * 1000);
        System.out.printf("    Exit Mach:              %.2f%n", params.exitMach());
        System.out.printf("    Chamber pressure:       %.1f MPa%n", params.chamberPressure() / 1e6);

        // ----------------------------------------------------------------
        // Stage 2 — Load intent, run solver, checkpoint full design
        // ----------------------------------------------------------------
        System.out.println("\n  Stage 2: Run MOC solver and save full design");

        DesignDocument intent = NozzleSerializer.load(checkpoint);
        NozzleDesignParameters solverParams = intent.parameters();

        long t0 = System.currentTimeMillis();
        CharacteristicNet net = new CharacteristicNet(solverParams).generate();
        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, solverParams);
        contour.generate(200);
        long elapsed = System.currentTimeMillis() - t0;

        NozzleSerializer.save(NozzleSerializer.document(solverParams, net, contour), checkpoint);
        System.out.printf("    Solver completed in:    %d ms%n", elapsed);
        System.out.printf("    MOC wall points:        %d%n", net.getWallPoints().size());
        System.out.printf("    MOC net rows:           %d%n", net.getNetPoints().size());
        System.out.printf("    Contour points:         %d%n", contour.getContourPoints().size());
        System.out.printf("    Full design saved to:   %s%n", checkpoint.getFileName());

        // ----------------------------------------------------------------
        // Stage 3 — Reload full design, analyze without re-running solver
        // ----------------------------------------------------------------
        System.out.println("\n  Stage 3: Reload checkpoint and run downstream analysis");

        DesignDocument full = NozzleSerializer.load(checkpoint);
        NozzleDesignParameters rp = full.parameters();

        System.out.printf("    Schema version:         %s%n", full.version());
        System.out.printf("    Created at:             %s%n", full.createdAt());
        System.out.printf("    Wall points restored:   %d%n", full.wallPoints().size());
        System.out.printf("    Contour points restored:%d%n", full.contourPoints().size());

        // Performance analysis driven entirely by the restored parameters
        PerformanceCalculator perf = PerformanceCalculator.simple(rp).calculate();
        System.out.printf("%n    Performance (from restored parameters):%n");
        System.out.printf("      Thrust coefficient:   %.4f%n", perf.getActualThrustCoefficient());
        System.out.printf("      Specific impulse:     %.1f s%n", perf.getSpecificImpulse());
        System.out.printf("      Thrust:               %.2f kN%n", perf.getThrust() / 1000);
        System.out.printf("      Efficiency:           %.2f%%%n", perf.getEfficiency() * 100);

        // Validation against NASA SP-8120 using restored parameters
        NASASP8120Validator.ValidationResult validation =
                new NASASP8120Validator().validate(rp);
        System.out.printf("%n    NASA SP-8120 validation (from restored parameters):%n");
        System.out.printf("      Overall status:       %s%n",
                validation.isValid() ? "PASS" : "FAIL");
        System.out.printf("      Warnings:             %d%n", validation.warnings().size());

        // Confirm the round-trip preserved all numeric fields exactly
        boolean paramsMatch =
                Math.abs(rp.throatRadius()       - params.throatRadius())       < 1e-12 &&
                Math.abs(rp.exitMach()           - params.exitMach())           < 1e-12 &&
                Math.abs(rp.chamberPressure()    - params.chamberPressure())    < 1e-12 &&
                Math.abs(rp.chamberTemperature() - params.chamberTemperature()) < 1e-12 &&
                rp.numberOfCharLines() == params.numberOfCharLines();
        System.out.printf("%n    Parameter round-trip exact: %b%n", paramsMatch);
    }

    /**
     * Demonstrates y⁺-controlled first-cell-height grading for CFD meshes.
     *
     * <p>Shows how to use {@link CFDMeshExporter#setFirstLayerThickness(double)}
     * and {@link OpenFOAMExporter#setFirstLayerThickness(double)} to drive mesh
     * grading from a target first-cell height y₁ rather than a fixed expansion
     * ratio.  Grading is computed per format:
     * <ul>
     *   <li>OpenFOAM blockMesh: g = H / y₁  (strong-grading approximation)</li>
     *   <li>Gmsh "Using Progression": r = (H/y₁)^(1/N)</li>
     *   <li>Plot3D power-law exponent: p = ln(H/y₁) / ln(N)</li>
     * </ul>
     */
    private static void demonstrateYPlusGrading(Path outputDir) throws Exception {
        System.out.println("\n--- y⁺-CONTROLLED FIRST-CELL-HEIGHT GRADING ---\n");

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

        // ---- 1. Derive a first-cell height from throat flow conditions ----
        // Rough y+ target: y+ = 1  (wall-resolved LES / viscous sublayer)
        // Using Re_throat from Sutherland viscosity:
        //   μ ≈ μ_ref * (T/T_ref)^(3/2) * (T_ref + S)/(T + S)
        // For simplicity we estimate μ at throat temperature T* = 0.833*T_c.
        double gamma   = params.gasProperties().gamma();
        double Tc      = params.chamberTemperature();
        double Pc      = params.chamberPressure();
        double R       = params.gasProperties().gasConstant();
        double Tt      = Tc * 2.0 / (gamma + 1.0);  // throat static temperature
        double Pt      = Pc * Math.pow(2.0 / (gamma + 1.0), gamma / (gamma - 1.0));
        double rhoT    = Pt / (R * Tt);
        double aT      = Math.sqrt(gamma * R * Tt);  // throat speed of sound = flow speed
        double muRef   = 1.716e-5;                   // kg/(m·s) at T_ref = 273.15 K
        double Tref    = 273.15;
        double S       = 110.4;                      // Sutherland constant for air-like mixture
        double muT     = muRef * Math.pow(Tt / Tref, 1.5) * (Tref + S) / (Tt + S);
        double ReThroat = rhoT * aT * (2.0 * params.throatRadius()) / muT;

        // Friction velocity u_tau ~ U∞ * sqrt(Cf/2),  Cf ≈ 0.026 * Re^-0.2
        double Cf      = 0.026 * Math.pow(ReThroat, -0.2);
        double uTau    = aT * Math.sqrt(Cf / 2.0);
        double nu      = muT / rhoT;                 // kinematic viscosity
        double yPlus1  = 1.0;
        double y1      = yPlus1 * nu / uTau;         // first-cell height for y⁺ = 1

        System.out.printf("Throat flow conditions:%n");
        System.out.printf("  Throat temperature:   %.0f K%n",   Tt);
        System.out.printf("  Throat density:       %.3f kg/m³%n", rhoT);
        System.out.printf("  Throat Mach speed:    %.1f m/s%n",  aT);
        System.out.printf("  Dynamic viscosity:    %.3e Pa·s%n", muT);
        System.out.printf("  Throat Re (diameter): %.3e%n",      ReThroat);
        System.out.printf("  Skin-friction coeff:  %.4f%n",      Cf);
        System.out.printf("  y⁺ = 1 cell height:  %.3e m  (%.4f mm)%n",
                y1, y1 * 1000);

        // ---- 2. CFDMeshExporter — OpenFOAM blockMesh, Gmsh, Plot3D ----
        System.out.println("\nCFDMeshExporter (y⁺-graded, N_r = 80):");

        Path bmdYPlus  = outputDir.resolve("blockMeshDict_yplus");
        Path geoYPlus  = outputDir.resolve("nozzle_yplus.geo");
        Path xyzYPlus  = outputDir.resolve("nozzle_yplus.xyz");

        CFDMeshExporter yPlusExporter = new CFDMeshExporter()
                .setAxialCells(200)
                .setRadialCells(80)
                .setFirstLayerThickness(y1);

        yPlusExporter.export(contour, bmdYPlus,  CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);
        yPlusExporter.export(contour, geoYPlus,  CFDMeshExporter.Format.GMSH_GEO);
        yPlusExporter.export(contour, xyzYPlus,  CFDMeshExporter.Format.PLOT3D);

        System.out.printf("  Exported blockMeshDict → %s%n", bmdYPlus.getFileName());
        System.out.printf("  Exported Gmsh .geo     → %s%n", geoYPlus.getFileName());
        System.out.printf("  Exported Plot3D .xyz   → %s%n", xyzYPlus.getFileName());

        // ---- 3. OpenFOAMExporter — full rhoCentralFoam case, y⁺ grading ----
        System.out.println("\nOpenFOAMExporter (y⁺-graded complete case):");

        Path yPlusCase = outputDir.resolve("openfoam_case_yplus");
        new OpenFOAMExporter()
                .setAxialCells(300)
                .setRadialCells(100)
                .setFirstLayerThickness(y1)
                .setTurbulenceEnabled(true)
                .setTurbulenceIntensity(0.05)
                .exportCase(params, contour, yPlusCase);

        System.out.printf("  Exported full case     → %s%n", yPlusCase.toAbsolutePath());
        System.out.println("  Run with: blockMesh && rhoCentralFoam");

        // ---- 4. Grading ratio comparison: fixed vs y⁺-derived ----
        double exitRadius = contour.getContourPoints().getLast().y();
        double fixedGrading  = 4.0;   // OpenFOAMExporter default
        double derivedGrading = Math.max(1.0, exitRadius / y1);

        System.out.printf("%nGrading ratio comparison (domain height = exit radius = %.4f m):%n",
                exitRadius);
        System.out.printf("  Fixed radialGrading (default):  %.1f%n",      fixedGrading);
        System.out.printf("  y⁺-derived grading (y1=%.2e m): %.1f%n",      y1, derivedGrading);
        System.out.printf("  First-cell / domain ratio:       %.2e%n",      y1 / exitRadius);
    }

    /**
     * Demonstrates the full 3-D revolved mesh exporter ({@link RevolvedMeshExporter}).
     *
     * <p>Exports the same bell nozzle contour in three formats:
     * OpenFOAM {@code blockMeshDict}, Gmsh {@code .geo}, and Plot3D {@code .xyz}.
     * Prints mesh statistics (vertex count, block count, grid dimensions) for
     * each format and compares default vs y⁺-derived radial grading.
     */
    private static void demonstrateRevolvedMesh(Path outputDir) throws Exception {
        System.out.println("\n--- FULL 3-D REVOLVED MESH EXPORT ---\n");

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

        int axial     = 100;
        int radial    = 40;
        int azimuthal = 16;   // 22.5° per sector

        System.out.printf("Mesh parameters:%n");
        System.out.printf("  Axial cells:      %d%n", axial);
        System.out.printf("  Radial cells:     %d%n", radial);
        System.out.printf("  Azimuthal sectors:%d  (%.1f° each)%n",
                azimuthal, 360.0 / azimuthal);

        // ---- OpenFOAM blockMeshDict (default grading) ----
        Path bmd3d = outputDir.resolve("blockMeshDict_3d");
        new RevolvedMeshExporter()
                .setAxialCells(axial)
                .setRadialCells(radial)
                .setAzimuthalCells(azimuthal)
                .setExpansionRatio(4.0)
                .export(contour, bmd3d, RevolvedMeshExporter.Format.OPENFOAM_BLOCKMESH);

        long bmdVertices = axial + 1 + (long)(axial + 1) * azimuthal;  // axis + wall
        long bmdBlocks   = (long) axial * azimuthal;
        System.out.printf("%nOpenFOAM blockMeshDict_3d:%n");
        System.out.printf("  Vertices:  %d  (axis: %d, wall: %d)%n",
                bmdVertices, axial + 1, (axial + 1) * azimuthal);
        System.out.printf("  Hex blocks:%d  (%d axial × %d azimuthal)%n",
                bmdBlocks, axial, azimuthal);
        System.out.printf("  Total cells: %,d%n", bmdBlocks * radial);
        System.out.printf("  File size: %.1f kB%n",
                java.nio.file.Files.size(bmd3d) / 1024.0);

        // ---- Gmsh .geo ----
        Path geo3d = outputDir.resolve("nozzle_3d.geo");
        new RevolvedMeshExporter()
                .setAxialCells(axial)
                .setRadialCells(radial)
                .setAzimuthalCells(azimuthal)
                .export(contour, geo3d, RevolvedMeshExporter.Format.GMSH_GEO);

        System.out.printf("%nGmsh nozzle_3d.geo:%n");
        System.out.printf("  2-D surface extruded via Extrude { Rotate { {1,0,0}, ... 2π } }%n");
        System.out.printf("  Layers: %d  (one hex layer per azimuthal sector)%n", azimuthal);
        System.out.printf("  File size: %.1f kB%n",
                java.nio.file.Files.size(geo3d) / 1024.0);

        // ---- Plot3D .xyz ----
        Path xyz3d = outputDir.resolve("nozzle_3d.xyz");
        new RevolvedMeshExporter()
                .setAxialCells(axial)
                .setRadialCells(radial)
                .setAzimuthalCells(azimuthal)
                .export(contour, xyz3d, RevolvedMeshExporter.Format.PLOT3D);

        int ni = axial + 1, nj = radial + 1, nk = azimuthal + 1;
        System.out.printf("%nPlot3D nozzle_3d.xyz:%n");
        System.out.printf("  Grid: %d × %d × %d  (ni × nj × nk)%n", ni, nj, nk);
        System.out.printf("  Total points: %,d%n", (long) ni * nj * nk);
        System.out.printf("  File size: %.1f kB%n",
                java.nio.file.Files.size(xyz3d) / 1024.0);

        // ---- Compare default vs y⁺-derived grading ----
        double exitRadius    = contour.getContourPoints().getLast().y();
        double defaultGrading = 4.0;
        double y1            = 2e-5;   // representative y1 ≈ 20 μm for y+ ≈ 1
        double yPlusGrading  = Math.max(1.0, exitRadius / y1);

        System.out.printf("%nRadial grading comparison (domain H = exit radius = %.4f m):%n",
                exitRadius);
        System.out.printf("  Default expansionRatio:   %.1f%n", defaultGrading);
        System.out.printf("  y⁺-derived (y1 = %.0f μm): %.0f   (first cell / H = %.2e)%n",
                y1 * 1e6, yPlusGrading, y1 / exitRadius);

        Path bmd3dYPlus = outputDir.resolve("blockMeshDict_3d_yplus");
        new RevolvedMeshExporter()
                .setAxialCells(axial)
                .setRadialCells(radial)
                .setAzimuthalCells(azimuthal)
                .setFirstLayerThickness(y1)
                .export(contour, bmd3dYPlus, RevolvedMeshExporter.Format.OPENFOAM_BLOCKMESH);
        System.out.printf("  y⁺-graded mesh written → %s%n", bmd3dYPlus.getFileName());
    }

    /**
     * Demonstrates the dual-bell altitude-compensating nozzle.
     *
     * <p>Compares three configurations for a LOX/RP-1 engine sized for a
     * high-altitude stage (Me = 5.0, Pc = 7 MPa):
     * <ol>
     *   <li>Full Rao bell (single contour, sea-level ambient) — massively
     *       over-expanded at sea level.</li>
     *   <li>Dual-bell — sea-level mode (flow separated at kink, AR = 4).</li>
     *   <li>Dual-bell — high-altitude mode (full nozzle, vacuum).</li>
     * </ol>
     */
    private static void demonstrateDualBellNozzle() {
        System.out.println("\n--- DUAL-BELL ALTITUDE-COMPENSATING NOZZLE ---\n");

        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(5.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101_325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();

        // --- Reference: full Rao bell at sea level ---
        RaoNozzle rao = new RaoNozzle(params).generate();
        double raoIsp = rao.calculateThrustCoefficient()
                        * params.characteristicVelocity() / 9.80665;

        System.out.printf("Full exit area ratio: %.1f  (Me = %.1f, γ = %.2f)%n",
                params.exitAreaRatio(), params.exitMach(), params.gasProperties().gamma());
        System.out.printf("Rao bell length:      %.3f m%n", rao.getActualLength());
        System.out.printf("Rao Isp (sea level, massively over-expanded): %.1f s%n", raoIsp);

        // --- Dual-bell ---
        System.out.println();
        double[] transitionARs = {3.0, 4.0, 6.0};
        System.out.println("transition   kink       base      full     switch");
        System.out.println("  AR         radius     length   length   pressure    SL Isp  HA Isp  gain");
        System.out.println("  " + "-".repeat(75));

        for (double ar : transitionARs) {
            DualBellNozzle db = new DualBellNozzle(params, ar).generate();
            DualBellNozzle.PerformanceSummary s = db.getPerformanceSummary();
            System.out.printf("  %4.1f  %8.1f mm  %6.3f m  %6.3f m  %7.0f Pa  %5.1f s  %5.1f s  %+.1f s%n",
                    ar,
                    db.getTransitionRadius() * 1000,
                    db.getBaseLength(),
                    db.getTotalLength(),
                    s.transitionPressure(),
                    s.seaLevelIsp(),
                    s.highAltitudeIsp(),
                    s.ispGain());
        }

        // --- Detailed breakdown for AR = 4 ---
        System.out.println();
        DualBellNozzle db4 = new DualBellNozzle(params, 4.0).generate();
        System.out.printf("Dual-bell  AR_kink = 4.0  (kink at M = %.2f):%n",
                db4.getTransitionMach());
        System.out.printf("  Inflection angle:      %.2f°%n",
                Math.toDegrees(db4.getInflectionAngle()));
        System.out.printf("  Base exit angle (θ_E1):%.2f°%n",
                Math.toDegrees(db4.getBaseExitAngle()));
        System.out.printf("  Extension exit (θ_E2): %.2f°%n",
                Math.toDegrees(db4.getExtensionExitAngle()));
        System.out.printf("  Kink index / total pts:%d / %d%n",
                db4.getKinkIndex(), db4.getContourPoints().size());
        System.out.printf("  Transition pressure:   %.0f Pa  (%.1f%% of sea level)%n",
                db4.getTransitionPressure(),
                db4.getTransitionPressure() / 101_325.0 * 100);
        System.out.printf("  Sea-level Isp:         %.1f s   (vs Rao %.1f s — %+.1f s benefit)%n",
                db4.getSeaLevelIsp(), raoIsp, db4.getSeaLevelIsp() - raoIsp);
        System.out.printf("  High-altitude Isp:     %.1f s%n", db4.getHighAltitudeIsp());

        // --- Analytical Cf cross-check (DLR subscale cold-flow case) ---
        // Geometry: Génin & Stark, Shock Waves 19, 265–270 (2009), Table 1
        //   R_th = 9 mm, ε_b = 3.9, ε_e ≈ 7.1 (M ≈ 3.55), kink 15°, N₂ γ=1.4
        // Verifies computePerformance() against the hand-derived isentropic formula.
        System.out.println();
        System.out.println("DLR subscale cold-flow cross-check  (N₂, γ=1.4; Génin & Stark 2009):");
        final double EPS_B  = 3.9;
        final double M_EXIT = 3.5504;   // → ε_e ≈ 7.10 for γ=1.4
        final double PC_DLR = 1_000_000.0;
        final double PA_DLR =    10_000.0;

        NozzleDesignParameters dlrParams = NozzleDesignParameters.builder()
                .throatRadius(0.009)
                .exitMach(M_EXIT)
                .chamberPressure(PC_DLR)
                .chamberTemperature(293.0)
                .ambientPressure(PA_DLR)
                .gasProperties(GasProperties.NITROGEN)
                .numberOfCharLines(20)
                .wallAngleInitialDegrees(30.0)
                .lengthFraction(0.8)
                .axisymmetric(false)
                .build();

        DualBellNozzle dlr = new DualBellNozzle(dlrParams, EPS_B,
                0.8, 0.8, Math.toRadians(15.0), 200).generate();

        // Analytical Cf — same isentropic formula evaluated independently
        double gm1      = 0.4;
        double term1    = 9.8 * Math.pow(5.0 / 6.0, 6.0);   // 2γ²/(γ−1)×(2/(γ+1))^((γ+1)/(γ−1))
        double pKinkR   = Math.pow(1.0 + 0.2 * dlr.getTransitionMach() * dlr.getTransitionMach(), -3.5);
        double pExitR   = Math.pow(1.0 + 0.2 * M_EXIT * M_EXIT, -3.5);
        double exitAR   = GasProperties.NITROGEN.areaRatio(M_EXIT);
        double lambdaB  = (1.0 + Math.cos(dlr.getBaseExitAngle()))      / 2.0;
        double lambdaE  = (1.0 + Math.cos(dlr.getExtensionExitAngle())) / 2.0;
        double cfSlAnal = lambdaB * Math.sqrt(term1 * (1.0 - Math.pow(pKinkR, gm1 / 1.4)))
                          + (pKinkR - PA_DLR / PC_DLR) * EPS_B;
        double cfAltAnal= lambdaE * Math.sqrt(term1 * (1.0 - Math.pow(pExitR, gm1 / 1.4)))
                          + pExitR * exitAR;

        System.out.printf("  M_kink = %.4f  (ε_b = %.1f)   M_exit = %.4f  (ε_e = %.3f)%n",
                dlr.getTransitionMach(), EPS_B, M_EXIT, exitAR);
        System.out.printf("  Sea-level Cf:  model = %.5f   analytical = %.5f   diff = %+.4f%%%n",
                dlr.getSeaLevelCf(), cfSlAnal,
                (dlr.getSeaLevelCf() - cfSlAnal) / cfSlAnal * 100.0);
        System.out.printf("  High-alt  Cf:  model = %.5f   analytical = %.5f   diff = %+.4f%%%n",
                dlr.getHighAltitudeCf(), cfAltAnal,
                (dlr.getHighAltitudeCf() - cfAltAnal) / cfAltAnal * 100.0);
    }

    /**
     * Demonstrates the O/F sweep and optimum search API.
     *
     * <p>For each supported propellant combination an adiabatic sweep is run at
     * Pc = 7 MPa, Me = 3.0 (sea level).  The Isp-optimal O/F is found via
     * golden-section search and the full Isp(O/F) curve is printed at coarse
     * resolution so the shape is visible in the console.
     */
    private static void demonstrateOFSweep() {
        System.out.println("\n--- O/F SWEEP AND OPTIMUM SEARCH ---\n");

        final double PC = 7e6;
        final double ME = 3.0;
        final double PA = 101_325.0;

        // ------------------------------------------------------------------
        // 1. Quick per-propellant optimum table
        // ------------------------------------------------------------------
        record PropEntry(OFSweep.Propellant p, double ofLo, double ofHi, String label) {}
        List<PropEntry> propellants = List.of(
                new PropEntry(OFSweep.Propellant.LOX_RP1,    1.5,  5.0, "LOX/RP-1  "),
                new PropEntry(OFSweep.Propellant.LOX_CH4,    2.0,  5.5, "LOX/CH4   "),
                new PropEntry(OFSweep.Propellant.LOX_LH2,    2.0,  8.0, "LOX/LH2   "),
                new PropEntry(OFSweep.Propellant.N2O_ETHANOL, 2.0, 8.0, "N2O/EtOH  "),
                new PropEntry(OFSweep.Propellant.N2O_PROPANE, 4.0,14.0, "N2O/C3H8  ")
        );

        System.out.println("Propellant       Opt O/F   Tc (K)   γ      MW    c* (m/s)  Isp (s)");
        System.out.println("-".repeat(72));
        for (PropEntry e : propellants) {
            OFSweep sweep = OFSweep.adiabatic(e.p(), PC, ME, PA);
            OFSweep.OFPoint opt = sweep.optimumIsp(e.ofLo(), e.ofHi());
            System.out.printf("%-16s  %5.2f   %6.0f  %.3f  %5.2f  %7.0f   %6.1f%n",
                    e.label(), opt.of(), opt.chamberTemperature(),
                    opt.gamma(), opt.molecularWeight(), opt.cStar(), opt.isp());
        }

        // ------------------------------------------------------------------
        // 2. LOX/RP-1 full Isp(O/F) and c*(O/F) curve at 11 points
        // ------------------------------------------------------------------
        System.out.println("\nLOX/RP-1 adiabatic sweep  Pc=7 MPa  Me=3.0  Pa=101325 Pa");
        System.out.println("  O/F    Tc (K)   γ      MW    c* (m/s)  Isp (s)");
        System.out.println("  " + "-".repeat(54));
        OFSweep rp1Sweep = OFSweep.adiabatic(OFSweep.Propellant.LOX_RP1, PC, ME, PA);
        for (OFSweep.OFPoint pt : rp1Sweep.sweep(1.5, 4.5, 11)) {
            System.out.printf("  %4.2f   %6.0f  %.3f  %5.2f  %7.0f   %6.1f%n",
                    pt.of(), pt.chamberTemperature(), pt.gamma(),
                    pt.molecularWeight(), pt.cStar(), pt.isp());
        }

        // ------------------------------------------------------------------
        // 3. Isp-optimal vs c*-optimal O/F comparison
        // ------------------------------------------------------------------
        System.out.println("\nIsp-optimal vs c*-optimal O/F (LOX/RP-1, adiabatic):");
        OFSweep.OFPoint ispOpt   = rp1Sweep.optimumIsp(1.5, 5.0);
        OFSweep.OFPoint cstarOpt = rp1Sweep.optimumCstar(1.5, 5.0);
        System.out.printf("  Isp-optimal:   O/F=%.3f  Isp=%.1f s   c*=%.0f m/s%n",
                ispOpt.of(), ispOpt.isp(), ispOpt.cStar());
        System.out.printf("  c*-optimal:    O/F=%.3f  Isp=%.1f s   c*=%.0f m/s%n",
                cstarOpt.of(), cstarOpt.isp(), cstarOpt.cStar());

        // ------------------------------------------------------------------
        // 4. Fixed-Tc vs adiabatic comparison at one O/F point
        // ------------------------------------------------------------------
        System.out.println("\nFixed-Tc (3500 K) vs adiabatic at O/F=2.7  (LOX/RP-1):");
        OFSweep fixedSweep = new OFSweep(OFSweep.Propellant.LOX_RP1, 3500.0, PC, ME, PA);
        OFSweep.OFPoint fixed = fixedSweep.computeAt(2.7);
        OFSweep.OFPoint adiab = rp1Sweep.computeAt(2.7);
        System.out.printf("  Fixed-Tc:   Tc=%5.0f K  c*=%6.0f m/s  Isp=%6.1f s%n",
                fixed.chamberTemperature(), fixed.cStar(), fixed.isp());
        System.out.printf("  Adiabatic:  Tc=%5.0f K  c*=%6.0f m/s  Isp=%6.1f s%n",
                adiab.chamberTemperature(), adiab.cStar(), adiab.isp());
    }

    /**
     * Demonstrates the full convergent-section feature:
     * geometry, sonic-line Cd correction, upstream BL integration,
     * and geometry-complete exports.
     */
    private static void demonstrateConvergentSection(Path outputDir) throws Exception {
        System.out.println("\n--- CONVERGENT SECTION: FULL NOZZLE GEOMETRY ---\n");

        final double RT = 0.05;   // 50 mm throat radius

        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(RT)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(25)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .throatCurvatureRatio(0.382)          // r_cd / r_t (downstream)
                .upstreamCurvatureRatio(1.5)          // r_cu / r_t (upstream)
                .convergentHalfAngleDegrees(30)       // convergent cone half-angle
                .contractionRatio(4.0)                // Ac / At = 4:1
                .build();

        // ------------------------------------------------------------------
        // 1. Convergent section geometry
        // ------------------------------------------------------------------
        ConvergentSection cs = new ConvergentSection(params).generate(60);

        System.out.println("Convergent section geometry:");
        System.out.printf("  Throat radius (r_t):         %.1f mm%n", RT * 1000);
        System.out.printf("  Chamber radius (r_c):        %.2f mm  (contraction ratio %.1f:1)%n",
                cs.getChamberRadius() * 1000, params.contractionRatio());
        System.out.printf("  Upstream curvature (r_cu):   %.2f mm  (= %.3f × r_t)%n",
                params.upstreamCurvatureRatio() * RT * 1000, params.upstreamCurvatureRatio());
        System.out.printf("  Convergent half-angle:       %.1f°%n",
                params.convergentHalfAngleDegrees());
        System.out.printf("  Arc end-point x:             %.2f mm%n", cs.getArcEndX() * 1000);
        System.out.printf("  Arc end-point radius:        %.2f mm%n", cs.getArcEndY() * 1000);
        System.out.printf("  Chamber face x:              %.2f mm%n", cs.getChamberFaceX() * 1000);
        System.out.printf("  Convergent section length:   %.2f mm%n", cs.getLength() * 1000);
        System.out.printf("  Contour wall points:         %d%n", cs.getContourPoints().size());

        // ------------------------------------------------------------------
        // 2. Sonic-line discharge-coefficient correction
        // ------------------------------------------------------------------
        System.out.println("\nSonic-line Cd correction:");
        System.out.printf("  Cd_geo (default r_cu=1.5, r_cd=0.382):  %.5f%n",
                cs.getSonicLineCdCorrection());

        // Sweep over upstream curvature ratios
        System.out.println("\n  r_cu/r_t   r_cd/r_t   Cd_geo    ΔCd (%)");
        System.out.println("  " + "-".repeat(44));
        double[] upRatios = {0.5, 1.0, 1.5, 2.0, 3.0};
        for (double uRatio : upRatios) {
            NozzleDesignParameters p2 = NozzleDesignParameters.builder()
                    .throatRadius(RT).exitMach(3.0).chamberPressure(7e6)
                    .chamberTemperature(3500).ambientPressure(101325)
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .upstreamCurvatureRatio(uRatio)
                    .convergentHalfAngleDegrees(30).contractionRatio(4.0)
                    .build();
            double cd = new ConvergentSection(p2).generate(40).getSonicLineCdCorrection();
            String flag = Math.abs(uRatio - 1.5) < 1e-6 ? " <- default" : "";
            System.out.printf("  %-9.2f  %-9.3f  %.5f   %.3f%%%s%n",
                    uRatio, params.throatCurvatureRatio(), cd, (1.0 - cd) * 100, flag);
        }

        // ------------------------------------------------------------------
        // 3. Full-geometry NozzleContour (chamber face → exit)
        // ------------------------------------------------------------------
        NozzleContour divergent = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        divergent.generate(100);
        NozzleContour fullContour = divergent.withConvergentSection(cs);

        System.out.println("\nFull nozzle contour (chamber → exit):");
        System.out.printf("  Divergent-only length:    %.2f mm%n", divergent.getLength() * 1000);
        System.out.printf("  Full contour length:      %.2f mm%n", fullContour.getLength() * 1000);
        System.out.printf("  Total wall points:        %d%n", fullContour.getContourPoints().size());
        System.out.printf("  First point x:            %.2f mm  (chamber face)%n",
                fullContour.getContourPoints().getFirst().x() * 1000);
        System.out.printf("  Last point x:             %.2f mm  (exit plane)%n",
                fullContour.getContourPoints().getLast().x() * 1000);

        // ------------------------------------------------------------------
        // 4. Upstream boundary-layer integration
        //    Pass the full-geometry contour to BoundaryLayerCorrection;
        //    it will start at the chamber face automatically.
        // ------------------------------------------------------------------
        com.nozzle.thermal.BoundaryLayerCorrection blFull =
                new com.nozzle.thermal.BoundaryLayerCorrection(params, fullContour).calculate(null);
        com.nozzle.thermal.BoundaryLayerCorrection blDivOnly =
                new com.nozzle.thermal.BoundaryLayerCorrection(params, divergent).calculate(null);

        System.out.println("\nBoundary-layer comparison (divergent-only vs. full geometry):");
        System.out.printf("  Exit displacement thickness — divergent only: %.4f mm%n",
                blDivOnly.getExitDisplacementThickness() * 1000);
        System.out.printf("  Exit displacement thickness — full geometry:  %.4f mm%n",
                blFull.getExitDisplacementThickness() * 1000);
        System.out.printf("  BL starts at x = %.1f mm for full geometry (vs 0 for divergent-only)%n",
                fullContour.getContourPoints().getFirst().x() * 1000);

        // ------------------------------------------------------------------
        // 5. Performance with Cd_sonic applied
        // ------------------------------------------------------------------
        PerformanceCalculator perfNoCd = PerformanceCalculator.simple(params).calculate();
        PerformanceCalculator perfWithCd = new PerformanceCalculator(
                params, null, null, null, null, cs).calculate();

        System.out.println("\nPerformance — effect of Cd_geo correction:");
        System.out.printf("  Without Cd_geo:  thrust = %.3f kN   ṁ = %.4f kg/s%n",
                perfNoCd.getThrust() / 1000, perfNoCd.getMassFlowRate());
        System.out.printf("  With    Cd_geo:  thrust = %.3f kN   ṁ = %.4f kg/s  (Cd = %.5f)%n",
                perfWithCd.getThrust() / 1000, perfWithCd.getMassFlowRate(),
                perfWithCd.getSonicLineCdCorrection());
        System.out.printf("  Isp unchanged:   %.1f s (geometry-independent)%n",
                perfWithCd.getSpecificImpulse());

        // ------------------------------------------------------------------
        // 6. Geometry-complete CSV export
        // ------------------------------------------------------------------
        CSVExporter csv = new CSVExporter();
        csv.exportContour(fullContour, outputDir.resolve("full_nozzle_contour.csv"));
        System.out.printf("%nExported full nozzle wall contour (chamber to exit) → full_nozzle_contour.csv%n");

        // DXF export automatically includes convergent section via full contour
        DXFExporter dxf = new DXFExporter();
        dxf.exportRevolutionProfile(fullContour, outputDir.resolve("full_nozzle_profile.dxf"));
        System.out.println("Exported full revolution profile → full_nozzle_profile.dxf");
    }

    /**
     * Demonstrates the effect of the downstream throat radius-of-curvature ratio
     * (r_cd / r_t) on nozzle contour and performance.
     *
     * <p>A sweep over five representative ratios (0.25, 0.382, 0.5, 0.75, 1.0)
     * is run for three contour types — MOC (CharacteristicNet), Rao bell
     * (RaoNozzle), and conical (NozzleContour CONICAL) — so the influence on
     * exit area ratio and ideal thrust coefficient is visible across design
     * strategies.
     */
    private static void demonstrateThroatCurvatureRatio() {
        System.out.println("\n--- THROAT CURVATURE RATIO SWEEP ---\n");

        System.out.println("Downstream throat radius of curvature: r_cd = ratio × r_t");
        System.out.println("Classical Rao default: 0.382  |  Valid range: (0, 2.0]");
        System.out.println();

        final double RT   = 0.05;   // 50 mm throat radius
        final double ME   = 3.0;    // design exit Mach
        final double PC   = 7e6;    // chamber pressure (Pa)
        final double TC   = 3500.0; // chamber temperature (K)
        final double PA   = 101325; // sea-level ambient (Pa)
        final double[] RATIOS = { 0.25, 0.382, 0.5, 0.75, 1.0 };

        // ------------------------------------------------------------------
        // 1. MOC (CharacteristicNet) sweep — computed exit A/A* and ideal Cf
        // ------------------------------------------------------------------
        System.out.println("1. MOC solver (CharacteristicNet)");
        System.out.printf("   %-8s  %-10s  %-10s  %-10s%n",
                "r_cd/r_t", "A/A* (calc)", "A/A* (ideal)", "Cf (ideal)");
        System.out.println("   " + "-".repeat(46));

        for (double ratio : RATIOS) {
            NozzleDesignParameters p = NozzleDesignParameters.builder()
                    .throatRadius(RT).exitMach(ME)
                    .chamberPressure(PC).chamberTemperature(TC)
                    .ambientPressure(PA)
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .numberOfCharLines(25)
                    .wallAngleInitialDegrees(30)
                    .lengthFraction(0.8)
                    .throatCurvatureRatio(ratio)
                    .build();

            CharacteristicNet net = new CharacteristicNet(p).generate();
            double arCalc  = net.calculateExitAreaRatio();
            double arIdeal = p.exitAreaRatio();
            double cfIdeal = p.idealThrustCoefficient();

            String flag = (Math.abs(ratio - NozzleDesignParameters.DEFAULT_THROAT_CURVATURE_RATIO) < 1e-6)
                    ? " <- default" : "";
            System.out.printf("   %-8.3f  %-10.4f  %-10.4f  %-10.4f%s%n",
                    ratio, arCalc, arIdeal, cfIdeal, flag);
        }

        // ------------------------------------------------------------------
        // 2. Rao bell (RaoNozzle) sweep — contour length and exit angle
        // ------------------------------------------------------------------
        System.out.println("\n2. Rao bell nozzle (RaoNozzle)");
        System.out.printf("   %-8s  %-14s  %-12s  %-10s%n",
                "r_cd/r_t", "Length (mm)", "Exit angle °", "Wall pts");
        System.out.println("   " + "-".repeat(50));

        for (double ratio : RATIOS) {
            NozzleDesignParameters p = NozzleDesignParameters.builder()
                    .throatRadius(RT).exitMach(ME)
                    .chamberPressure(PC).chamberTemperature(TC)
                    .ambientPressure(PA)
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .numberOfCharLines(25)
                    .wallAngleInitialDegrees(30)
                    .lengthFraction(0.8)
                    .throatCurvatureRatio(ratio)
                    .build();

            RaoNozzle rao = new RaoNozzle(p);
            String flag = (Math.abs(ratio - NozzleDesignParameters.DEFAULT_THROAT_CURVATURE_RATIO) < 1e-6)
                    ? " <- default" : "";
            System.out.printf("   %-8.3f  %-14.2f  %-12.4f  %-10d%s%n",
                    ratio,
                    rao.getActualLength() * 1000,
                    Math.toDegrees(rao.getExitAngle()),
                    rao.getContourPoints().size(),
                    flag);
        }

        // ------------------------------------------------------------------
        // 3. Conical contour sweep — arc end-point radius shift
        // ------------------------------------------------------------------
        System.out.println("\n3. Conical contour (NozzleContour.CONICAL)");
        System.out.printf("   %-8s  %-16s  %-14s%n",
                "r_cd/r_t", "Arc r_cd (mm)", "Total pts");
        System.out.println("   " + "-".repeat(42));

        for (double ratio : RATIOS) {
            NozzleDesignParameters p = NozzleDesignParameters.builder()
                    .throatRadius(RT).exitMach(ME)
                    .chamberPressure(PC).chamberTemperature(TC)
                    .ambientPressure(PA)
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .numberOfCharLines(25)
                    .wallAngleInitialDegrees(15)
                    .lengthFraction(0.8)
                    .throatCurvatureRatio(ratio)
                    .build();

            NozzleContour contour = new NozzleContour(NozzleContour.ContourType.CONICAL, p);
            double rcd = ratio * RT * 1000; // mm
            String flag = (Math.abs(ratio - NozzleDesignParameters.DEFAULT_THROAT_CURVATURE_RATIO) < 1e-6)
                    ? " <- default" : "";
            System.out.printf("   %-8.3f  %-16.2f  %-14d%s%n",
                    ratio, rcd, contour.getContourPoints().size(), flag);
        }

        // ------------------------------------------------------------------
        // 4. Physical interpretation
        // ------------------------------------------------------------------
        System.out.println("\nPhysical interpretation:");
        System.out.println("  Smaller ratio -> tighter throat arc -> stronger initial expansion fan");
        System.out.println("  -> wave interactions begin closer to the throat.");
        System.out.println("  Larger ratio  -> gentler arc -> more uniform flow entering the divergent");
        System.out.println("  section, at the cost of added nozzle mass and length.");
        System.out.println("  Ideal Cf and A/A* are geometry-independent (set by Mach and gamma);");
        System.out.println("  the MOC computed A/A* confirms the solver reaches the design exit Mach.");
    }
}
