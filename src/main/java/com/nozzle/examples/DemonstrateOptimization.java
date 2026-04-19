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
import com.nozzle.optimization.AltitudeAdaptiveOptimizer;

import java.util.List;

/** Demonstrates altitude-adaptive optimization. */
public class DemonstrateOptimization {

    public static void main(String[] ignoredArgs) {
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
}
