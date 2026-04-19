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
import com.nozzle.moc.ViscousMOCSolver;

/** Demonstrates the ViscousMOCSolver boundary-layer displacement correction. */
public class DemonstrateViscousMOCSolver {

    public static void main(String[] args) {
        System.out.println("\n--- VISCOUS MOC SOLVER (BL DISPLACEMENT CORRECTION) ---\n");

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

        CharacteristicNet net = new CharacteristicNet(params).generate();

        System.out.println("Inviscid characteristic net:");
        System.out.printf("  Wall points:          %d%n", net.getWallPoints().size());
        System.out.printf("  Exit area ratio:      %.4f%n", net.calculateExitAreaRatio());

        ViscousMOCSolver viscous = new ViscousMOCSolver(net).solve();

        System.out.println("\nViscous-corrected wall profile:");
        System.out.printf("  Corrected wall pts:   %d%n", viscous.getCorrectedWall().size());
        System.out.printf("  Discharge coefficient:%.5f%n", viscous.dischargeCoefficient());
        System.out.printf("  Viscous Isp loss:     %.3f%%%n",
                viscous.viscousIspLossFraction() * 100.0);

        System.out.println("\nSample wall stations (inviscid vs physical radius):");
        System.out.printf("  %-8s  %-14s  %-14s  %-12s  %-8s%n",
                "x (mm)", "r_inv (mm)", "r_phys (mm)", "δ* (μm)", "Mach");
        System.out.println("  " + "-".repeat(58));

        var wall = viscous.getCorrectedWall();
        int step = Math.max(1, wall.size() / 8);
        for (int i = 0; i < wall.size(); i += step) {
            ViscousMOCSolver.ViscousWallPoint wp = wall.get(i);
            System.out.printf("  %-8.2f  %-14.4f  %-14.4f  %-12.2f  %-8.4f%n",
                    wp.x() * 1000,
                    wp.rInviscid() * 1000,
                    wp.rPhysical() * 1000,
                    wp.displacementThickness() * 1e6,
                    wp.mach());
        }

        System.out.println("\nChamber pressure sensitivity (Cd vs Pc):");
        System.out.printf("  %-12s  %-14s  %-12s%n", "Pc (MPa)", "Cd (inviscid)", "Cd (viscous)");
        System.out.println("  " + "-".repeat(42));
        for (double pc : new double[]{3e6, 5e6, 7e6, 10e6, 15e6}) {
            NozzleDesignParameters p = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0)
                    .chamberPressure(pc).chamberTemperature(3500.0)
                    .ambientPressure(101325.0)
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .numberOfCharLines(25).wallAngleInitialDegrees(30.0)
                    .lengthFraction(0.8).axisymmetric(true).build();
            CharacteristicNet n2 = new CharacteristicNet(p).generate();
            ViscousMOCSolver v2 = new ViscousMOCSolver(n2).solve();
            System.out.printf("  %-12.1f  %-14.5f  %-12.5f%n",
                    pc / 1e6, p.dischargeCoefficient(), v2.dischargeCoefficient());
        }

        System.out.println("\nPhysical insight:");
        System.out.println("  The viscous wall is outset by δ*(x) relative to the inviscid contour.");
        System.out.println("  Higher chamber pressure → thinner BL → smaller displacement → Cd closer to 1.");
        System.out.println("  The machined wall must incorporate this outset to achieve design exit Mach.");
    }
}
