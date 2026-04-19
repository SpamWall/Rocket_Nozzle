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
import com.nozzle.moc.CharacteristicNet;
import com.nozzle.moc.RaoNozzle;

/** Demonstrates the effect of downstream throat curvature ratio on MOC, Rao bell, and conical contours. */
public class DemonstrateThroatCurvatureRatio {

    private DemonstrateThroatCurvatureRatio() {}

    /**
     * Entry point.
     * @param ignoredArgs unused
     */
    public static void main(String[] ignoredArgs) {
        System.out.println("\n--- THROAT CURVATURE RATIO SWEEP ---\n");

        System.out.println("Downstream throat radius of curvature: r_cd = ratio × r_t");
        System.out.println("Classical Rao default: 0.382  |  Valid range: (0, 2.0]");
        System.out.println();

        final double RT   = 0.05;
        final double ME   = 3.0;
        final double PC   = 7e6;
        final double TC   = 3500.0;
        final double PA   = 101325;
        final double[] RATIOS = { 0.25, 0.382, 0.5, 0.75, 1.0 };

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
            double rcd = ratio * RT * 1000;
            String flag = (Math.abs(ratio - NozzleDesignParameters.DEFAULT_THROAT_CURVATURE_RATIO) < 1e-6)
                    ? " <- default" : "";
            System.out.printf("   %-8.3f  %-16.2f  %-14d%s%n",
                    ratio, rcd, contour.getContourPoints().size(), flag);
        }

        System.out.println("\nPhysical interpretation:");
        System.out.println("  Smaller ratio -> tighter throat arc -> stronger initial expansion fan");
        System.out.println("  -> wave interactions begin closer to the throat.");
        System.out.println("  Larger ratio  -> gentler arc -> more uniform flow entering the divergent");
        System.out.println("  section, at the cost of added nozzle mass and length.");
        System.out.println("  Ideal Cf and A/A* are geometry-independent (set by Mach and gamma);");
        System.out.println("  the MOC computed A/A* confirms the solver reaches the design exit Mach.");
    }
}
