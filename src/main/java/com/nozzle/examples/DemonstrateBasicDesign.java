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
import com.nozzle.moc.CharacteristicNet;

import java.nio.file.Files;
import java.nio.file.Path;

/** Demonstrates basic MOC nozzle design. */
public class DemonstrateBasicDesign {

    private DemonstrateBasicDesign() {}

    /**
     * Entry point.
     * @param ignoredArgs unused
     * @throws Exception on any I/O or calculation failure
     */
    public static void main(String[] ignoredArgs) throws Exception {
        Path outputDir = Path.of("nozzle_output");
        Files.createDirectories(outputDir);
        System.out.println("\n--- BASIC MOC NOZZLE DESIGN ---\n");

        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.5)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(30)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
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

        double gamma   = params.gasProperties().gamma();
        double rt      = params.throatRadius();
        double rcd     = params.throatCurvatureRatio()  * rt;
        double rcu     = params.upstreamCurvatureRatio() * rt;
        double sonicWallX = (gamma + 1.0) / 12.0 * rt * rt * (1.0 / rcd + 1.0 / (3.0 * rcu));

        System.out.println("\nGenerating characteristic net...");
        System.out.printf("  Sonic-line wall offset x_s(r_t): %.4f m  (Hall 1962; R_cd=%.3f m, R_cu=%.3f m)%n",
                sonicWallX, rcd, rcu);
        long startTime = System.currentTimeMillis();

        CharacteristicNet net = new CharacteristicNet(params).generate();

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.printf("  Completed in %d ms%n", elapsed);
        System.out.printf("  Total points: %d%n", net.getTotalPointCount());
        System.out.printf("  Wall points:  %d%n", net.getWallPoints().size());
        System.out.printf("  Computed A/A*: %.2f%n", net.calculateExitAreaRatio());
        System.out.printf("  Initial data line wall x: %.4f m (on curved sonic surface)%n",
                net.getNetPoints().getFirst().getLast().x());

        PerformanceCalculator perf = PerformanceCalculator.simple(params).calculate();
        System.out.println("\nPerformance:");
        System.out.printf("  Actual Cf:    %.4f%n", perf.getActualThrustCoefficient());
        System.out.printf("  Efficiency:   %.2f%%%n", perf.getEfficiency() * 100);
        System.out.printf("  Isp:          %.1f s%n", perf.getSpecificImpulse());
        System.out.printf("  Thrust:       %.2f kN%n", perf.getThrust() / 1000);

        CSVExporter csvExporter = new CSVExporter();
        csvExporter.exportWallContour(net, outputDir.resolve("wall_contour.csv"));
        csvExporter.exportDesignParameters(params, outputDir.resolve("design_params.csv"));
        System.out.println("\nExported wall contour and parameters to CSV");
    }
}
