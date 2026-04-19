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
import com.nozzle.moc.CharacteristicNet;
import com.nozzle.moc.RaoNozzle;

/** Demonstrates Rao bell nozzle comparison against MOC. */
public class DemonstrateRaoComparison {

    public static void main(String[] args) {
        System.out.println("\n--- RAO BELL NOZZLE COMPARISON ---\n");

        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(25)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();

        CharacteristicNet mocNet = new CharacteristicNet(params).generate();
        RaoNozzle raoNozzle = new RaoNozzle(params).generate();

        System.out.println("Rao Bell Nozzle Properties:");
        System.out.printf("  Length:           %.4f m%n", raoNozzle.getActualLength());
        System.out.printf("  Inflection Angle: %.2f°%n", Math.toDegrees(raoNozzle.getInflectionAngle()));
        System.out.printf("  Exit Angle:       %.2f°%n", Math.toDegrees(raoNozzle.getExitAngle()));
        System.out.printf("  Thrust Coeff:     %.4f%n", raoNozzle.calculateThrustCoefficient());
        System.out.printf("  Efficiency:       %.2f%%%n", raoNozzle.calculateEfficiency() * 100);

        RaoNozzle.NozzleComparison comparison = raoNozzle.compareTo(mocNet);
        System.out.println("\nComparison MOC vs Rao:");
        System.out.printf("  Max radius diff:  %.3f mm%n", comparison.maxRadiusDifference() * 1000);
        System.out.printf("  Avg radius diff:  %.3f mm%n", comparison.avgRadiusDifference() * 1000);
        System.out.printf("  Max angle diff:   %.2f°%n", Math.toDegrees(comparison.maxAngleDifference()));
        System.out.printf("  Cf difference:    %.4f%n", comparison.thrustCoefficientDifference());
    }
}
