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
import com.nozzle.geometry.NozzleContour;

/** Demonstrates Truncated Ideal Contour (TIC) as a fifth contour family. */
public class DemonstrateTruncatedIdealContour {

    public static void main(String[] args) {
        System.out.println("\n--- TRUNCATED IDEAL CONTOUR (TIC) ---\n");

        final double RT      = 0.05;
        final double M_D     = 3.0;
        final double THETA_N = 30.0;

        NozzleDesignParameters base = NozzleDesignParameters.builder()
                .throatRadius(RT)
                .exitMach(M_D)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(25)
                .wallAngleInitialDegrees(THETA_N)
                .lengthFraction(0.8)
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

            double rTIC   = tic.getContourPoints().getLast().y();
            double arTIC  = (rTIC / RT) * (rTIC / RT);
            double lTIC   = tic.getLength() * 1000;
            double thetaE = (1.0 - f) * THETA_N;

            System.out.printf("  %.1f   %10.3f       %12.2f        %12.2f       %5.2f°%n",
                    f, arTIC, rTIC * 1000, lTIC, thetaE);
        }

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
}
