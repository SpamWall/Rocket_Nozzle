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

import com.nozzle.core.FlowSeparationPredictor;
import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.core.ShockExpansionModel;

/** Demonstrates flow separation prediction and shock-expansion off-design analysis. */
public class DemonstrateFlowSeparationAndShockExpansion {

    public static void main(String[] args) {
        System.out.println("\n--- FLOW SEPARATION & SHOCK-EXPANSION OFF-DESIGN ---\n");

        NozzleDesignParameters seaLevel = NozzleDesignParameters.builder()
                .throatRadius(0.05).exitMach(3.0).chamberPressure(7e6)
                .chamberTemperature(3500).ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20).wallAngleInitialDegrees(30).lengthFraction(0.8)
                .build();

        NozzleDesignParameters vacuumBell = NozzleDesignParameters.builder()
                .throatRadius(0.05).exitMach(6.0).chamberPressure(7e6)
                .chamberTemperature(3500).ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20).wallAngleInitialDegrees(30).lengthFraction(0.8)
                .build();

        System.out.println("Flow Separation Prediction — vacuum bell (Me=6) at sea level:");
        System.out.printf("  Design exit pressure:  %.0f Pa  (ambient: %.0f Pa)%n",
                vacuumBell.idealExitPressure(), vacuumBell.ambientPressure());
        System.out.println();

        for (FlowSeparationPredictor.Criterion c : FlowSeparationPredictor.Criterion.values()) {
            FlowSeparationPredictor.SeparationResult sep =
                    new FlowSeparationPredictor(vacuumBell).predict(c);
            System.out.printf("  [%-11s]  p_sep = %6.0f Pa  |  separated: %s",
                    c, sep.separationPressurePa(), sep.separated() ? "YES" : "NO ");
            if (sep.separated()) {
                System.out.printf("  |  M_sep=%.2f  AR_sep=%.2f  mode=%-3s  side-load=%.0f N",
                        sep.separationMach(), sep.separationAreaRatio(),
                        sep.mode(), sep.estimatedSideLoadN());
            }
            System.out.println();
        }

        System.out.println("\nShock-Expansion Altitude Sweep — sea-level nozzle (Me=3):");
        System.out.printf("  %-8s  %-6s  %-25s  %-6s  %-6s  %-10s%n",
                "Alt (km)", "pa (kPa)", "Regime", "Cf", "Isp (s)", "Wave (°)");
        System.out.println("  " + "-".repeat(72));

        double[] altitudes = {0, 5, 10, 20, 40, 80};
        ShockExpansionModel seaLevelModel = new ShockExpansionModel(seaLevel);
        for (double alt : altitudes) {
            ShockExpansionModel.OffDesignResult r = seaLevelModel.computeAtAltitude(alt * 1000);
            double waveAngle = Double.isNaN(r.waveAngleDeg()) ? 0.0 : r.waveAngleDeg();
            System.out.printf("  %-8.0f  %-6.1f  %-25s  %-6.4f  %-6.1f  %-10.1f%n",
                    alt, ShockExpansionModel.isaAtmosphere(alt * 1000) / 1000,
                    r.regime(), r.thrustCoefficient(), r.specificImpulse(), waveAngle);
        }

        System.out.println("\nShock-Expansion Altitude Sweep — vacuum bell (Me=6):");
        System.out.printf("  %-8s  %-6s  %-25s  %-6s  %-6s%n",
                "Alt (km)", "pa (kPa)", "Regime", "Cf", "Isp (s)");
        System.out.println("  " + "-".repeat(58));

        ShockExpansionModel vacuumModel = new ShockExpansionModel(vacuumBell);
        for (double alt : altitudes) {
            ShockExpansionModel.OffDesignResult r = vacuumModel.computeAtAltitude(alt * 1000);
            System.out.printf("  %-8.0f  %-6.1f  %-25s  %-6.4f  %-6.1f%n",
                    alt, ShockExpansionModel.isaAtmosphere(alt * 1000) / 1000,
                    r.regime(), r.thrustCoefficient(), r.specificImpulse());
        }

        System.out.println("\nCf comparison at sea level (pa = 101 325 Pa):");
        ShockExpansionModel.OffDesignResult slResult = seaLevelModel.compute(101325);
        ShockExpansionModel.OffDesignResult vacResult = vacuumModel.compute(101325);
        System.out.printf("  Sea-level nozzle (Me=3):  Cf=%.4f  Isp=%.1f s  regime=%s%n",
                slResult.thrustCoefficient(), slResult.specificImpulse(), slResult.regime());
        System.out.printf("  Vacuum bell    (Me=6):    Cf=%.4f  Isp=%.1f s  regime=%s%n",
                vacResult.thrustCoefficient(), vacResult.specificImpulse(), vacResult.regime());
        if (vacResult.separationResult() != null) {
            FlowSeparationPredictor.SeparationResult sr = vacResult.separationResult();
            System.out.printf("    → Separation at M=%.2f (%.1f%% of nozzle length),"
                            + " est. side-load %.0f N%n",
                    sr.separationMach(), sr.separationAxialFraction() * 100,
                    sr.estimatedSideLoadN());
        }
    }
}
