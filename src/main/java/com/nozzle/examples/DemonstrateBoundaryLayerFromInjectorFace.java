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
import com.nozzle.core.Units;
import com.nozzle.geometry.FullNozzleGeometry;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.thermal.BoundaryLayerCorrection;

/** Demonstrates boundary-layer calculation from the injector face vs. throat-only. */
public class DemonstrateBoundaryLayerFromInjectorFace {

    private DemonstrateBoundaryLayerFromInjectorFace() {}

    /**
     * Entry point.
     * @param ignoredArgs unused
     */
    public static void main(String[] ignoredArgs) {
        System.out.println("\n--- BOUNDARY LAYER FROM INJECTOR FACE ---\n");

        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.5)
                .chamberPressure(7e6)
                .chamberTemperature(3500.0)
                .ambientPressure(101325.0)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(30)
                .wallAngleInitialDegrees(30.0)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .contractionRatio(4.0)
                .build();

        FullNozzleGeometry fullGeom = new FullNozzleGeometry(params).generate(50, 100);

        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        BoundaryLayerCorrection blcFull = new BoundaryLayerCorrection(params, contour)
                .setForceTurbulent(true)
                .calculateFromInjectorFace(fullGeom, null);

        contour.generate(100);
        BoundaryLayerCorrection blcThroat = new BoundaryLayerCorrection(params, contour)
                .setForceTurbulent(true)
                .calculate(null);

        System.out.println("From-injector-face BL (physically correct):");
        System.out.printf("  Total wall points:          %d%n",
                blcFull.getBoundaryLayerProfile().size());
        System.out.printf("  Running length at exit:     %.1f mm%n",
                Units.metersToMillimeters(blcFull.getBoundaryLayerProfile().getLast().runningLength()));
        System.out.printf("  δ* at throat:               %.3f mm%n",
                Units.metersToMillimeters(blcFull.getThroatDisplacementThickness()));
        System.out.printf("  δ* at exit:                 %.3f mm%n",
                Units.metersToMillimeters(blcFull.getExitDisplacementThickness()));
        System.out.printf("  BL Cd correction:           %.5f%n",
                blcFull.getBoundaryLayerCdCorrection());
        System.out.printf("  Geometric Cd:               %.5f%n",
                params.dischargeCoefficient());
        System.out.printf("  Combined Cd (geo × BL):     %.5f%n",
                blcFull.getCombinedCd());

        System.out.println("\nFrom-throat BL (old behaviour, for comparison):");
        if (!blcThroat.getBoundaryLayerProfile().isEmpty()) {
            System.out.printf("  Running length at exit:     %.1f mm%n",
                    Units.metersToMillimeters(blcThroat.getBoundaryLayerProfile().getLast().runningLength()));
        }
        System.out.printf("  δ* at exit:                 %.3f mm%n",
                Units.metersToMillimeters(blcThroat.getExitDisplacementThickness()));

        System.out.println("\nPhysical insight:");
        System.out.println("  The convergent section arc length (~"
                + String.format("%.0f", Units.metersToMillimeters(fullGeom.getConvergentLength()))
                + " mm) is added to the BL");
        System.out.println("  running length, increasing the throat δ* and reducing the");
        System.out.println("  effective throat area below what a throat-only calculation predicts.");
        System.out.println("  Combined Cd accounts for both sonic-line curvature and BL displacement.");
    }
}
