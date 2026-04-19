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

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.core.PerformanceCalculator;
import com.nozzle.export.CSVExporter;
import com.nozzle.export.DXFExporter;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.solid.RaspImporter;
import com.nozzle.solid.RaspMotorData;

import java.nio.file.Files;
import java.nio.file.Path;

/** Demonstrates RASP .eng import and bridge to the nozzle design pipeline. */
public class DemonstrateRaspImport {

    public static void main(String[] args) throws Exception {
        Path outputDir = Path.of("nozzle_output");
        Files.createDirectories(outputDir);
        System.out.println("\n--- RASP .ENG IMPORT (OpenMotor bridge) ---\n");

        Path engDir  = outputDir.resolve("rasp_import");
        Files.createDirectories(engDir);
        Path engFile = engDir.resolve("H174-14.eng");
        Files.writeString(engFile,
                "; Synthetic APCP/HTPB H-class motor — demonstration\n" +
                "H174-14 38 193 14 0.101 0.187 DEMO\n" +
                "   0.000    0.000\n" +
                "   0.050  155.000\n" +
                "   0.150  178.000\n" +
                "   0.300  181.000\n" +
                "   0.450  180.000\n" +
                "   0.500  175.000\n" +
                "   0.550  172.000\n" +
                "   0.580    0.000\n" +
                ";\n");
        System.out.printf("Wrote synthetic .eng file: %s%n%n", engFile.getFileName());

        RaspMotorData motor = RaspImporter.load(engFile);
        System.out.println("Parsed motor: " + motor);
        System.out.printf("  Class              : %s%n",      motor.motorClass());
        System.out.printf("  Total impulse      : %.1f N·s%n", motor.totalImpulseNs());
        System.out.printf("  Burn time          : %.3f s%n",   motor.burnTime());
        System.out.printf("  Average thrust     : %.1f N%n",   motor.averageThrustN());
        System.out.printf("  Peak thrust        : %.1f N%n",   motor.maxThrustN());
        System.out.printf("  Delivered Isp      : %.1f s%n",   motor.specificImpulseSeconds());
        System.out.printf("  Avg mass flow      : %.5f kg/s%n", motor.averageMassFlowRateKgPerS());
        System.out.printf("  Data points        : %d%n",       motor.size());

        double throatRadius = 0.009;
        NozzleDesignParameters template = NozzleDesignParameters.builder()
                .throatRadius(throatRadius)
                .exitMach(3.0)
                .ambientPressure(101325)
                .chamberPressure(5.0e6)
                .chamberTemperature(3200)
                .gasProperties(GasProperties.APCP_HTPB_PRODUCTS)
                .axisymmetric(true)
                .lengthFraction(0.8)
                .numberOfCharLines(25)
                .wallAngleInitialDegrees(30)
                .build();

        NozzleDesignParameters params    = motor.toNozzleParameters(template);
        NozzleDesignParameters paramsMax = motor.toNozzleParametersAtMaxPressure(template);

        System.out.println("\nChamber conditions from thrust-curve inversion:");
        System.out.printf("  Average Pc (design): %.3f MPa%n", params.chamberPressure()    / 1e6);
        System.out.printf("  Peak Pc (structural): %.3f MPa%n", paramsMax.chamberPressure() / 1e6);
        System.out.printf("  Area ratio Ae/At   : %.3f%n",      params.exitAreaRatio());
        System.out.printf("  Exit radius        : %.2f mm%n",   params.exitRadius() * 1000);
        System.out.printf("  Ideal Isp          : %.1f s%n",    params.idealSpecificImpulse());

        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        contour.generate(60);

        PerformanceCalculator perf = new PerformanceCalculator(
                params, null, contour, null, null);
        perf.calculate();
        PerformanceCalculator.PerformanceSummary summary = perf.getSummary();

        System.out.println("\nRao bell nozzle performance (at average Pc):");
        System.out.printf("  Ideal Cf           : %.4f%n",   summary.idealCf());
        System.out.printf("  Actual Cf          : %.4f%n",   summary.actualCf());
        System.out.printf("  Nozzle efficiency  : %.2f%%%n", summary.efficiency() * 100);
        System.out.printf("  Delivered Isp      : %.1f s%n", summary.specificImpulseSeconds());
        System.out.printf("  Thrust             : %.2f N%n", summary.thrustNewtons());

        new DXFExporter().exportContour(contour, engDir.resolve("H174_nozzle.dxf"));
        new CSVExporter().exportContour(contour, engDir.resolve("H174_nozzle.csv"));
        System.out.printf("%nExports saved to: %s%n", engDir.toAbsolutePath());
    }
}
