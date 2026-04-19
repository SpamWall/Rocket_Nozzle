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
import com.nozzle.optimization.MonteCarloUncertainty;

/** Demonstrates Monte Carlo uncertainty analysis. */
public class DemonstrateUncertaintyAnalysis {

    private DemonstrateUncertaintyAnalysis() {}

    /**
     * Entry point.
     * @param ignoredArgs unused
     */
    public static void main(String[] ignoredArgs) {
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
}
