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
import com.nozzle.moc.MinimumLengthNozzle;
import com.nozzle.moc.RaoNozzle;

/** Demonstrates the Minimum-Length Nozzle (MLN) via Method of Characteristics. */
public class DemonstrateMinimumLengthNozzle {

    public static void main(String[] ignoredArgs) {
        System.out.println("\n--- MINIMUM-LENGTH NOZZLE (MLN) ---\n");

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

        MinimumLengthNozzle mln = new MinimumLengthNozzle(params).generate();

        System.out.println("Minimum-Length Nozzle geometry:");
        System.out.printf("  Maximum wall angle:   %.2f°%n",
                Math.toDegrees(mln.getMaximumWallAngle()));
        System.out.printf("  Wall points:          %d%n", mln.getWallPoints().size());
        System.out.printf("  Nozzle length:        %.2f mm%n", mln.nozzleLength() * 1000);
        System.out.printf("  Exit area ratio:      %.4f%n", mln.exitAreaRatio());
        System.out.printf("  Length ratio vs cone: %.4f  (1.0 = 15° half-angle reference)%n",
                mln.lengthRatioVsCone());

        RaoNozzle rao = new RaoNozzle(params).generate();
        System.out.printf("%nComparison vs Rao bell (same design point):%n");
        System.out.printf("  MLN length:  %.2f mm%n", mln.nozzleLength() * 1000);
        System.out.printf("  Rao length:  %.2f mm%n", rao.getActualLength() * 1000);
        System.out.printf("  MLN is %.1f%% shorter than Rao bell%n",
                (1.0 - mln.nozzleLength() / rao.getActualLength()) * 100.0);

        System.out.println("\nExit Mach sweep (numberOfCharLines = 25):");
        System.out.printf("  %-8s  %-12s  %-12s  %-12s%n",
                "Me", "Length (mm)", "A/A*", "L/L_cone");
        System.out.println("  " + "-".repeat(48));
        for (double me : new double[]{2.0, 2.5, 3.0, 3.5, 4.0}) {
            NozzleDesignParameters p = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(me)
                    .chamberPressure(7e6).chamberTemperature(3500.0)
                    .ambientPressure(101325.0)
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .numberOfCharLines(25)
                    .wallAngleInitialDegrees(30.0)
                    .lengthFraction(0.8).axisymmetric(true).build();
            MinimumLengthNozzle m = new MinimumLengthNozzle(p).generate();
            System.out.printf("  %-8.1f  %-12.2f  %-12.4f  %-12.4f%n",
                    me, m.nozzleLength() * 1000, m.exitAreaRatio(), m.lengthRatioVsCone());
        }

        System.out.println("\nPhysical insight:");
        System.out.println("  The MLN concentrates all turning at the throat corner (Prandtl-Meyer fan).");
        System.out.println("  The downstream wall is derived purely from characteristic reflections.");
        System.out.println("  Result: theoretically shortest contour for uniform exit flow.");
        System.out.println("  Trade-off: sharper throat geometry increases local heat flux vs Rao bell.");
    }
}
