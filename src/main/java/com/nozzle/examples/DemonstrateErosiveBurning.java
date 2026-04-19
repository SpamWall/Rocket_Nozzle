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

import com.nozzle.solid.ErosiveBurningModel;
import com.nozzle.solid.SolidPropellant;

/** Demonstrates the Lenoir-Robillard erosive burning model for solid rocket grains. */
public class DemonstrateErosiveBurning {

    public static void main(String[] args) {
        System.out.println("\n--- EROSIVE BURNING MODEL (Lenoir-Robillard) ---\n");

        SolidPropellant propellant = SolidPropellant.APCP_HTPB();
        ErosiveBurningModel model = new ErosiveBurningModel(propellant);

        double pc = 7.0e6;
        double r0 = propellant.burnRate(pc);

        System.out.printf("Propellant: APCP/HTPB%n");
        System.out.printf("  Base burn rate at %.0f MPa: %.3f mm/s%n", pc / 1e6, r0 * 1000);
        System.out.printf("  Threshold flux G_th: %.0f kg/m²/s%n",
                ErosiveBurningModel.DEFAULT_THRESHOLD);
        System.out.printf("  α = %.4f,  β = %.1f%n",
                ErosiveBurningModel.DEFAULT_ALPHA, ErosiveBurningModel.DEFAULT_BETA);

        System.out.println("\nBurn rate augmentation vs core mass flux G:");
        System.out.printf("  %-14s  %-14s  %-14s  %-12s%n",
                "G (kg/m²/s)", "r_e (mm/s)", "r_total (mm/s)", "Erosive (%)");
        System.out.println("  " + "-".repeat(56));
        double[] gValues = {100, 150, 200, 300, 400, 600, 800, 1000};
        for (double G : gValues) {
            double re     = model.erosiveBurnRate(r0, G);
            double rTotal = model.augmentedBurnRate(r0, G);
            double frac   = model.erosiveFraction(r0, G) * 100.0;
            System.out.printf("  %-14.0f  %-14.3f  %-14.3f  %-12.1f%n",
                    G, re * 1000, rTotal * 1000, frac);
        }

        System.out.println("\nNo erosion below threshold (G < G_th = "
                + (int) ErosiveBurningModel.DEFAULT_THRESHOLD + " kg/m²/s):");
        double G_below = ErosiveBurningModel.DEFAULT_THRESHOLD - 1;
        System.out.printf("  G = %.0f kg/m²/s → r_e = %.6f mm/s%n",
                G_below, model.erosiveBurnRate(r0, G_below) * 1000);

        System.out.println("\nAxially-averaged erosion along a BATES grain:");
        System.out.printf("  %-14s  %-14s  %-14s%n",
                "Grain L (mm)", "Port r (mm)", "Avg r_e (mm/s)");
        System.out.println("  " + "-".repeat(44));
        double[][] cases = {{0.100, 0.025}, {0.150, 0.025}, {0.200, 0.025},
                            {0.150, 0.015}, {0.150, 0.035}};
        for (double[] c : cases) {
            double L  = c[0];
            double ri = c[1];
            double avgRe = model.axiallyAveragedErosion(r0, ri, L);
            System.out.printf("  %-14.0f  %-14.0f  %-14.3f%n",
                    L * 1000, ri * 1000, avgRe * 1000);
        }

        System.out.println("\nPressure sensitivity (r0 varies with Pc):");
        System.out.printf("  %-12s  %-14s  %-14s  %-12s%n",
                "Pc (MPa)", "r0 (mm/s)", "r_total (mm/s)", "Erosive (%)");
        System.out.println("  " + "-".repeat(54));
        double G_fixed = 400.0;
        for (double p : new double[]{3e6, 5e6, 7e6, 10e6, 15e6}) {
            double r = propellant.burnRate(p);
            double rt = model.augmentedBurnRate(r, G_fixed);
            System.out.printf("  %-12.0f  %-14.3f  %-14.3f  %-12.1f%n",
                    p / 1e6, r * 1000, rt * 1000,
                    model.erosiveFraction(r, G_fixed) * 100.0);
        }

        System.out.println("\nPhysical insight:");
        System.out.println("  The exponential blocking term exp(-β r0 ρ_p / G) means that at");
        System.out.println("  high Pc (high r0) the transpired gas shields the surface and");
        System.out.println("  reduces erosive augmentation — a self-limiting mechanism.");
        System.out.println("  Long, narrow-port grains develop the highest G near the aft end");
        System.out.println("  and experience the greatest erosive augmentation there.");
    }
}
