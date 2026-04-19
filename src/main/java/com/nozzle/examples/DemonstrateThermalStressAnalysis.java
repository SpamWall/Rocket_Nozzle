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
import com.nozzle.export.CSVExporter;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.thermal.HeatTransferModel;
import com.nozzle.thermal.ThermalStressAnalysis;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Demonstrates thermal stress and fatigue life analysis. */
public class DemonstrateThermalStressAnalysis {

    public static void main(String[] args) throws Exception {
        Path outputDir = Path.of("nozzle_output");
        Files.createDirectories(outputDir);
        System.out.println("\n--- THERMAL STRESS & FATIGUE LIFE ANALYSIS ---\n");

        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05).exitMach(3.0).chamberPressure(7e6)
                .chamberTemperature(3500).ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20).wallAngleInitialDegrees(30)
                .lengthFraction(0.8).axisymmetric(true).build();

        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        contour.generate(60);

        double wallThickness    = 0.003;
        double wallConductivity = 320.0;

        HeatTransferModel heatModel = new HeatTransferModel(params, contour)
                .setWallProperties(wallConductivity, wallThickness)
                .setCoolantProperties(300.0, 25_000.0)
                .calculate(List.of());

        List<HeatTransferModel.WallThermalPoint> thermalProfile = heatModel.getWallThermalProfile();

        System.out.printf("Thermal baseline (Cu-Cr-Zr liner, k=%.0f W/(m·K), t=%.0f mm):%n",
                wallConductivity, wallThickness * 1000);
        System.out.printf("  Max wall temperature: %.0f K%n",  heatModel.getMaxWallTemperature());
        System.out.printf("  Max heat flux:        %.2e W/m²%n", heatModel.getMaxHeatFlux());
        System.out.printf("  Wall points analysed: %d%n", thermalProfile.size());

        System.out.println("\nMaterial comparison (same wall geometry and thermal load):");
        System.out.printf("  %-20s  %8s  %6s  %8s  %8s  %10s%n",
                "Material", "σ_VM_max", "SF_min", "ΔT_max", "N_f_min", "Regime");
        System.out.printf("  %-20s  %8s  %6s  %8s  %8s  %10s%n",
                "", "(MPa)", "(–)", "(K)", "(cycles)", "");
        System.out.println("  " + "-".repeat(68));

        ThermalStressAnalysis.Material[] materials = {
                ThermalStressAnalysis.Material.COPPER_ALLOY_CuCrZr,
                ThermalStressAnalysis.Material.INCONEL_718,
                ThermalStressAnalysis.Material.STAINLESS_304,
        };

        ThermalStressAnalysis bestAnalysis = null;

        for (ThermalStressAnalysis.Material mat : materials) {
            ThermalStressAnalysis analysis =
                    new ThermalStressAnalysis(params, thermalProfile, mat, wallThickness, wallConductivity)
                            .calculate();

            double sigVM_max = analysis.getMaxVonMisesStress();
            double sfMin     = analysis.getMinSafetyFactor();
            double dtMax     = analysis.getMaxDeltaT();
            double nfMin     = analysis.getMinFatigueCycles();
            String regime    = sigVM_max > mat.yieldStrength() ? "PLASTIC" : "elastic";
            String nfStr     = Double.isInfinite(nfMin) ? "∞" : String.format("%.1f", nfMin);

            System.out.printf("  %-20s  %8.1f  %6.2f  %8.1f  %8s  %10s%n",
                    mat.name(), sigVM_max / 1e6, sfMin, dtMax, nfStr, regime);

            if (mat == ThermalStressAnalysis.Material.COPPER_ALLOY_CuCrZr) {
                bestAnalysis = analysis;
            }
        }

        if (bestAnalysis != null) {
            ThermalStressAnalysis.WallStressPoint cp = bestAnalysis.getCriticalPoint();
            System.out.printf("%n  Cu-Cr-Zr critical point (highest σ_VM):%n");
            System.out.printf("    Axial position:    x = %.4f m%n",   cp.x());
            System.out.printf("    Wall temperature:  T = %.0f K%n",   cp.wallTemperature());
            System.out.printf("    Through-wall ΔT:   %.1f K%n",        cp.deltaT());
            System.out.printf("    σ_thermal (hoop):  %.1f MPa%n",      cp.thermalHoopStress()  / 1e6);
            System.out.printf("    σ_hoop  (pressure): %.1f MPa%n",     cp.pressureHoopStress() / 1e6);
            System.out.printf("    σ_axial (pressure): %.1f MPa%n",     cp.pressureAxialStress()/ 1e6);
            System.out.printf("    σ_VM (combined):   %.1f MPa%n",      cp.vonMisesStress()     / 1e6);
            System.out.printf("    Safety factor:     %.2f%n",           cp.safetyFactor());
            double nf = cp.estimatedCycles();
            System.out.printf("    Fatigue life:      %s start-shutdown cycles%n",
                    Double.isInfinite(nf) ? "∞ (below endurance limit)"
                                          : String.format("%.1f", nf));

            CSVExporter csv = new CSVExporter();
            csv.exportStressProfile(bestAnalysis, outputDir.resolve("stress_profile.csv"));
            System.out.println("\nExported stress profile to: stress_profile.csv");
        }
    }
}
