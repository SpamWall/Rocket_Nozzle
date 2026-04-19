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

package com.nozzle.examples;

import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.core.PerformanceCalculator;
import com.nozzle.export.CSVExporter;
import com.nozzle.export.DXFExporter;
import com.nozzle.export.STEPExporter;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.solid.BatesGrain;
import com.nozzle.solid.BurnTrajectory;
import com.nozzle.solid.GrainGeometry;
import com.nozzle.solid.SolidMotorChamber;
import com.nozzle.solid.SolidPropellant;

import java.nio.file.Files;
import java.nio.file.Path;

/** Demonstrates solid propellant motor ballistics and nozzle design for APCP/HTPB and KNSU. */
public class DemonstrateSolidMotor {

    public static void main(String[] args) throws Exception {
        Path outputDir = Path.of("nozzle_output");
        Files.createDirectories(outputDir);
        System.out.println("\n--- SOLID MOTOR NOZZLE DESIGN ---\n");

        Path solidDir = outputDir.resolve("solid_motor");
        Files.createDirectories(solidDir);

        SolidPropellant propellant = SolidPropellant.APCP_HTPB();
        BatesGrain      grain      = new BatesGrain(0.100, 0.050, 0.075, 4);

        System.out.println("Propellant: APCP/HTPB composite");
        System.out.printf("  Density            : %.0f kg/m³%n",  propellant.density());
        System.out.printf("  Burn rate at 7 MPa : %.2f mm/s%n",   propellant.burnRate(7.0e6) * 1000);
        System.out.printf("  Pressure exponent n: %.3f%n",        propellant.burnRateExponent());
        System.out.printf("  Temp sensitivity   : %.4f /K%n",     propellant.temperatureSensitivity());
        System.out.printf("  c*                 : %.0f m/s%n",    propellant.characteristicVelocity());
        System.out.printf("  T_c (flame)        : %.0f K%n",      propellant.chamberTemperature());

        System.out.println("\nGrain: " + grain.name());
        System.out.printf("  Web thickness      : %.1f mm%n",     grain.webThickness()    * 1000);
        System.out.printf("  Burning area Ab(0) : %.4f m²%n",     grain.burningArea(0.0));
        System.out.printf("  Propellant volume  : %.2f cm³%n",    grain.propellantVolume() * 1.0e6);

        double targetPc = 7.0e6;
        double ab0      = grain.burningArea(0.0);
        double at       = ab0 * propellant.density()
                          * propellant.burnRateCoefficient()
                          * propellant.characteristicVelocity()
                          / Math.pow(targetPc, 1.0 - propellant.burnRateExponent());
        double rt       = Math.sqrt(at / Math.PI);

        System.out.printf("%nThroat sized for %.1f MPa initial Pc:%n", targetPc / 1e6);
        System.out.printf("  Throat area    : %.4f cm²%n", at  * 1.0e4);
        System.out.printf("  Throat radius  : %.2f mm%n",  rt  * 1000);
        System.out.printf("  Initial Kn     : %.1f%n",     ab0 / at);

        SolidMotorChamber chamber    = new SolidMotorChamber(propellant, grain, at);
        BurnTrajectory    trajectory = chamber.computeBurnTrajectory(294.0, 0.001);

        System.out.println("\nBurn trajectory (T_prop = 294 K, 21 °C):");
        System.out.printf("  Propellant mass    : %.4f kg%n",  trajectory.propellantMass());
        System.out.printf("  Burn time          : %.3f s%n",   trajectory.burnTime());
        System.out.printf("  Initial Pc         : %.3f MPa%n", chamber.chamberPressure(0.0) / 1e6);
        System.out.printf("  Average Pc         : %.3f MPa%n", trajectory.averagePressure()       / 1e6);
        System.out.printf("  Peak Pc            : %.3f MPa%n", trajectory.maxPressure()            / 1e6);
        System.out.printf("  Avg mass flow rate : %.5f kg/s%n", trajectory.averageMassFlowRate());
        System.out.printf("  Trajectory points  : %d%n",       trajectory.size());

        BurnTrajectory trajectoryCold = chamber.computeBurnTrajectory(233.0);
        BurnTrajectory trajectoryHot  = chamber.computeBurnTrajectory(344.0);

        System.out.println("\nTemperature sensitivity (MIL-STD qualification range):");
        System.out.printf("  %-12s  burn time = %.3f s,  avg Pc = %.3f MPa%n",
                "Cold (−40 °C):", trajectoryCold.burnTime(), trajectoryCold.averagePressure() / 1e6);
        System.out.printf("  %-12s  burn time = %.3f s,  avg Pc = %.3f MPa%n",
                "Nom (+21 °C):", trajectory.burnTime(), trajectory.averagePressure() / 1e6);
        System.out.printf("  %-12s  burn time = %.3f s,  avg Pc = %.3f MPa%n",
                "Hot (+71 °C):", trajectoryHot.burnTime(), trajectoryHot.averagePressure() / 1e6);
        System.out.printf("  Pressure swing cold→hot: %+.1f%%%n",
                (trajectoryHot.averagePressure() - trajectoryCold.averagePressure())
                / trajectoryCold.averagePressure() * 100.0);

        Path traceCsv = solidDir.resolve("burn_trace_apcp_htpb.csv");
        try (var w = Files.newBufferedWriter(traceCsv)) {
            w.write("time_s,web_burned_mm,chamber_pressure_MPa,"
                    + "burning_area_m2,burn_rate_mm_s,mass_flow_kg_s\n");
            for (int i = 0; i < trajectory.size(); i++) {
                w.write(String.format("%.5f,%.4f,%.5f,%.6f,%.5f,%.6f%n",
                        trajectory.timeAt(i),
                        trajectory.webBurnedAt(i)      * 1000,
                        trajectory.chamberPressureAt(i) / 1.0e6,
                        trajectory.burningAreaAt(i),
                        trajectory.burnRateAt(i)        * 1000,
                        trajectory.massFlowRateAt(i)));
            }
        }
        System.out.printf("%nBurn trace CSV     → %s  (%d points)%n",
                traceCsv.getFileName(), trajectory.size());

        NozzleDesignParameters nozzleTemplate = NozzleDesignParameters.builder()
                .throatRadius(rt)
                .exitMach(3.5)
                .ambientPressure(101325)
                .numberOfCharLines(30)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();

        NozzleDesignParameters nozzleParams = trajectory.toNozzleParameters(nozzleTemplate);

        System.out.println("\nNozzle design at average chamber conditions:");
        System.out.printf("  Chamber pressure   : %.3f MPa%n", nozzleParams.chamberPressure() / 1e6);
        System.out.printf("  Chamber temperature: %.0f K%n",   nozzleParams.chamberTemperature());
        System.out.printf("  Gas γ              : %.3f%n",     nozzleParams.gasProperties().gamma());
        System.out.printf("  Exit Mach          : %.1f%n",     nozzleParams.exitMach());
        System.out.printf("  Area ratio Ae/At   : %.3f%n",     nozzleParams.exitAreaRatio());
        System.out.printf("  Exit radius        : %.2f mm%n",  nozzleParams.exitRadius()  * 1000);
        System.out.printf("  Ideal Isp          : %.1f s%n",   nozzleParams.idealSpecificImpulse());

        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, nozzleParams);
        contour.generate(60);

        PerformanceCalculator perf = new PerformanceCalculator(
                nozzleParams, null, contour, null, null);
        perf.calculate();
        PerformanceCalculator.PerformanceSummary summary = perf.getSummary();

        System.out.println("\nRao bell nozzle delivered performance:");
        System.out.printf("  Ideal Cf           : %.4f%n",    summary.idealCf());
        System.out.printf("  Actual Cf          : %.4f%n",    summary.actualCf());
        System.out.printf("  Nozzle efficiency  : %.2f%%%n",  summary.efficiency() * 100);
        System.out.printf("  Delivered Isp      : %.1f s%n",  summary.specificImpulseSeconds());
        System.out.printf("  Thrust             : %.3f kN%n", summary.thrustNewtons()       / 1000);
        System.out.printf("  Mass flow rate     : %.5f kg/s%n", summary.massFlowRateKgPerSec());

        Path dxfPath  = solidDir.resolve("srm_nozzle.dxf");
        Path csvPath  = solidDir.resolve("srm_nozzle_contour.csv");
        Path stepPath = solidDir.resolve("srm_nozzle.step");
        new DXFExporter().exportContour(contour, dxfPath);
        new CSVExporter().exportContour(contour, csvPath);
        new STEPExporter().exportRevolvedSolid(contour, stepPath);
        System.out.println("\nNozzle contour exports:");
        System.out.printf("  DXF profile        → %s%n", dxfPath.getFileName());
        System.out.printf("  CSV contour        → %s%n", csvPath.getFileName());
        System.out.printf("  STEP revolved solid→ %s%n", stepPath.getFileName());

        System.out.println("\nKNSU candy-propellant comparison motor:");
        SolidPropellant knsu      = SolidPropellant.KNSU();
        GrainGeometry   knsuGrain = new BatesGrain(0.075, 0.030, 0.055, 2);

        double knsuAb0 = knsuGrain.burningArea(0.0);
        double knsuAt  = knsuAb0 * knsu.density()
                         * knsu.burnRateCoefficient()
                         * knsu.characteristicVelocity()
                         / Math.pow(2.0e6, 1.0 - knsu.burnRateExponent());
        SolidMotorChamber knsuChamber    = new SolidMotorChamber(knsu, knsuGrain, knsuAt);
        BurnTrajectory    knsuTrajectory = knsuChamber.computeBurnTrajectory(294.0);

        NozzleDesignParameters knsuNozzle = knsuTrajectory.toNozzleParameters(
                NozzleDesignParameters.builder()
                        .throatRadius(Math.sqrt(knsuAt / Math.PI))
                        .exitMach(3.0)
                        .ambientPressure(101325)
                        .numberOfCharLines(20)
                        .wallAngleInitialDegrees(30)
                        .lengthFraction(0.8)
                        .axisymmetric(true)
                        .build());

        System.out.printf("  Grain              : %s%n",       knsuGrain.name());
        System.out.printf("  c*                 : %.0f m/s  (APCP: %.0f m/s)%n",
                knsu.characteristicVelocity(), propellant.characteristicVelocity());
        System.out.printf("  Burn time          : %.3f s%n",   knsuTrajectory.burnTime());
        System.out.printf("  Avg Pc             : %.3f MPa%n", knsuTrajectory.averagePressure() / 1e6);
        System.out.printf("  Ideal Isp          : %.1f s  (APCP: %.1f s)%n",
                knsuNozzle.idealSpecificImpulse(), nozzleParams.idealSpecificImpulse());

        System.out.printf("%nSolid motor output saved to: %s%n", solidDir.toAbsolutePath());
    }
}
