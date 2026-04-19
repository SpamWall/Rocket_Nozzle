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

/** Demonstrates US-customary ↔ SI unit conversion utilities. */
public class DemonstrateUnitsConversion {

    private DemonstrateUnitsConversion() {}

    /**
     * Entry point.
     * @param ignoredArgs unused
     */
    public static void main(String[] ignoredArgs) {
        System.out.println("\n--- UNITS CONVERSION UTILITIES ---\n");

        double throatIn   = 2.0;
        double chamberPsi = 1000.0;
        double chamberR   = 6300.0;
        double exitDiamIn = 8.0;

        double throatM   = Units.inchesToMeters(throatIn / 2.0);
        double chamberPa = Units.psiToPascals(chamberPsi);
        double chamberK  = Units.rankineToKelvin(chamberR);
        double exitM     = Units.inchesToMeters(exitDiamIn / 2.0);
        double areaRatio = (exitM / throatM) * (exitM / throatM);

        System.out.println("Input conversion (US customary → SI):");
        System.out.printf("  Throat dia   %5.2f in  →  %6.3f mm%n",
                throatIn, Units.metersToMillimeters(throatM));
        System.out.printf("  Chamber pres %5.0f psia →  %6.3f MPa%n",
                chamberPsi, Units.pascalsToMegapascals(chamberPa));
        System.out.printf("  Chamber temp %5.0f °R   →  %6.1f K%n",
                chamberR, chamberK);
        System.out.printf("  Exit dia     %5.2f in  →  %6.1f mm  (A/A*=%.2f)%n",
                exitDiamIn, Units.metersToMillimeters(exitM), areaRatio);

        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(throatM)
                .exitMach(3.0)
                .chamberPressure(chamberPa)
                .chamberTemperature(chamberK)
                .ambientPressure(101325.0)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20)
                .wallAngleInitialDegrees(30.0)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();

        double thrustN = params.idealThrustCoefficient()
                         * params.chamberPressure()
                         * params.throatArea();
        double mdotKgs = params.chamberPressure() * params.throatArea()
                         / params.characteristicVelocity();

        System.out.println("\nOutput conversion (SI → US customary):");
        System.out.printf("  Throat area  %8.4f m²  →  %8.4f in²%n",
                params.throatArea(), Units.squareMetersToSquareInches(params.throatArea()));
        System.out.printf("  Ideal thrust %8.1f N   →  %8.1f lbf%n",
                thrustN, Units.newtonsToLbf(thrustN));
        System.out.printf("  Mass flow    %8.2f kg/s →  %8.2f lb/s%n",
                mdotKgs, Units.kgPerSecToLbPerSec(mdotKgs));
        System.out.printf("  Ideal Isp    %8.1f s    (same in SI and US customary)%n",
                params.idealSpecificImpulse());
        System.out.printf("  Exit temp    %8.1f K   →  %8.1f °R%n",
                params.exitTemperature(), Units.kelvinToRankine(params.exitTemperature()));
        System.out.printf("  Exit vel     %8.1f m/s →  %8.1f ft/s%n",
                params.exitVelocity(), Units.metersPerSecToFeetPerSec(params.exitVelocity()));
    }
}
