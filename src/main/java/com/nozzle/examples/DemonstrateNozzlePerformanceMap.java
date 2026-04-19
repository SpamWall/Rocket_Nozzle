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
import com.nozzle.core.NozzlePerformanceMap;

/** Demonstrates the 2D Isp performance map vs altitude and expansion ratio. */
public class DemonstrateNozzlePerformanceMap {

    private DemonstrateNozzlePerformanceMap() {}

    /**
     * Entry point.
     * @param ignoredArgs unused
     */
    public static void main(String[] ignoredArgs) {
        System.out.println("\n--- NOZZLE PERFORMANCE MAP (Isp vs altitude and expansion ratio) ---\n");

        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500.0)
                .ambientPressure(101325.0)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(25)
                .wallAngleInitialDegrees(30.0)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();

        NozzlePerformanceMap map = new NozzlePerformanceMap(params)
                .altitudePoints(20)
                .expansionRatioPoints(25)
                .maxExpansionRatio(80.0)
                .generate();

        System.out.printf("Map grid: %d altitude × %d expansion-ratio points%n",
                map.getAltitudes().length, map.getExpansionRatios().length);

        System.out.println("\nOptimum expansion ratio vs altitude:");
        System.out.printf("  %-12s  %-14s  %-12s%n", "Alt (km)", "Opt ε", "Peak Isp (s)");
        System.out.println("  " + "-".repeat(40));
        for (NozzlePerformanceMap.AltitudeOptimum opt : map.getOptima()) {
            if (opt.altitudeKm() % 10 < 5) {
                System.out.printf("  %-12.1f  %-14.2f  %-12.1f%n",
                        opt.altitudeKm(), opt.optimumEpsilon(), opt.peakIsp());
            }
        }

        double[] checkAlts = {0.0, 10.0, 30.0, 60.0};
        double[] checkEps  = {5.0, 15.0, 40.0};
        System.out.println("\nIsp (s) at selected (altitude, ε) cells:");
        System.out.printf("  %-12s", "Alt (km)");
        for (double eps : checkEps) System.out.printf("  ε=%-8.0f", eps);
        System.out.println();
        System.out.println("  " + "-".repeat(12 + checkEps.length * 12));
        for (double alt : checkAlts) {
            System.out.printf("  %-12.1f", alt);
            for (double eps : checkEps) {
                System.out.printf("  %-10.1f", map.isp(alt, eps));
            }
            System.out.println();
        }

        System.out.println("\nFixed nozzle (ε = 20): peak Isp across all altitudes:");
        System.out.printf("  Peak Isp at ε = 20: %.1f s  (altitude = vacuum)%n",
                map.peakIspForFixedNozzle(20.0));

        System.out.println("\nOptimum expansion ratio (non-decreasing with altitude):");
        double prevOpt = 0.0;
        boolean monotone = true;
        for (NozzlePerformanceMap.AltitudeOptimum opt : map.getOptima()) {
            if (opt.optimumEpsilon() < prevOpt - 0.01) { monotone = false; break; }
            prevOpt = opt.optimumEpsilon();
        }
        System.out.printf("  Monotone non-decreasing: %s%n", monotone ? "YES" : "NO");
        System.out.printf("  Sea-level optimum ε:     %.2f%n",
                map.optimumExpansionRatio(0.0));
        System.out.printf("  80 km optimum ε:         %.2f%n",
                map.optimumExpansionRatio(80.0));

        System.out.println("\nPhysical insight:");
        System.out.println("  At sea level the optimum ε is limited by over-expansion losses.");
        System.out.println("  In vacuum every increase in ε adds pressure thrust, so optimum → ∞.");
        System.out.println("  The map identifies the trajectory segment where a fixed-ε nozzle");
        System.out.println("  is near-optimal, guiding stage separation and throttle decisions.");
    }
}
