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
import com.nozzle.thermal.AblativeNozzleModel;
import com.nozzle.thermal.HeatTransferModel;

import java.util.List;

/** Demonstrates ablative liner char-rate model for solid rocket motor nozzles. */
public class DemonstrateAblativeLiner {

    private DemonstrateAblativeLiner() {}

    /**
     * Entry point.
     * @param ignoredArgs unused
     */
    public static void main(String[] ignoredArgs) {
        System.out.println("\n--- ABLATIVE LINER ANALYSIS (SRM NOZZLE) ---\n");

        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05).exitMach(3.0).chamberPressure(7e6)
                .chamberTemperature(3500).ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20).wallAngleInitialDegrees(30)
                .lengthFraction(0.8).axisymmetric(true).build();

        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        contour.generate(60);

        System.out.println("Arrhenius Char Rate at Key Temperatures [mm/s]:");
        System.out.printf("  %-28s  %8s  %8s  %8s  %8s%n", "Material", "500 K", "1000 K", "1500 K", "2500 K");
        System.out.println("  " + "-".repeat(64));

        AblativeNozzleModel.AblativeMaterial[] materials = {
                AblativeNozzleModel.AblativeMaterial.CARBON_PHENOLIC,
                AblativeNozzleModel.AblativeMaterial.SILICA_PHENOLIC,
                AblativeNozzleModel.AblativeMaterial.EPDM,
                AblativeNozzleModel.AblativeMaterial.GRAPHITE,
                AblativeNozzleModel.AblativeMaterial.CARBON_CARBON,
        };

        for (AblativeNozzleModel.AblativeMaterial mat : materials) {
            System.out.printf("  %-28s  %8.4f  %8.4f  %8.4f  %8.4f%n",
                    mat.name(),
                    mat.charRateAt(500)  * 1000,
                    mat.charRateAt(1000) * 1000,
                    mat.charRateAt(1500) * 1000,
                    mat.charRateAt(2500) * 1000);
        }

        List<HeatTransferModel.WallThermalPoint> heatProfile =
                new HeatTransferModel(params, contour)
                        .setWallProperties(1.0, 0.020)
                        .setCoolantProperties(300.0, 0.0)
                        .calculate(List.of())
                        .getWallThermalProfile();

        System.out.printf("%nHeat-transfer baseline:%n");
        System.out.printf("  Max gas-side wall temperature: %.0f K%n",
                heatProfile.stream().mapToDouble(HeatTransferModel.WallThermalPoint::wallTemperature)
                        .max().orElse(0));

        double burnTime       = 10.0;
        double linerThickness = 0.020;

        System.out.printf("%nPure Arrhenius Recession after %.0f s burn (%.0f mm liner):%n",
                burnTime, linerThickness * 1000);
        System.out.printf("  %-28s  %12s  %12s  %10s%n",
                "Material", "Max recess (mm)", "Min remain (mm)", "Perforated");
        System.out.println("  " + "-".repeat(68));

        for (AblativeNozzleModel.AblativeMaterial mat : materials) {
            AblativeNozzleModel mdl = new AblativeNozzleModel(params, contour)
                    .setMaterial(mat).setInitialLinerThickness(linerThickness)
                    .setBurnTime(burnTime).calculate(heatProfile);

            System.out.printf("  %-28s  %12.4f  %12.4f  %10s%n",
                    mat.name(),
                    mdl.getMaxRecessionDepth()     * 1000,
                    mdl.getMinRemainingThickness() * 1000,
                    mdl.isPerforatedAnywhere() ? "YES" : "no");
        }

        double k_e = 1.0e-5;
        AblativeNozzleModel pureArrhenius = new AblativeNozzleModel(params, contour)
                .setMaterial(AblativeNozzleModel.AblativeMaterial.CARBON_PHENOLIC)
                .setInitialLinerThickness(linerThickness).setBurnTime(burnTime)
                .setErosionFactor(0.0).calculate(heatProfile);

        AblativeNozzleModel withErosion = new AblativeNozzleModel(params, contour)
                .setMaterial(AblativeNozzleModel.AblativeMaterial.CARBON_PHENOLIC)
                .setInitialLinerThickness(linerThickness).setBurnTime(burnTime)
                .setErosionFactor(k_e).calculate(heatProfile);

        System.out.printf("%nMechanical Erosion Supplement (carbon-phenolic, k_e = %.0e m/s):%n", k_e);
        System.out.printf("  Pure Arrhenius:  max recession = %.4f mm%n",
                pureArrhenius.getMaxRecessionDepth() * 1000);
        System.out.printf("  With erosion:    max recession = %.4f mm   (+%.2f%%)%n",
                withErosion.getMaxRecessionDepth() * 1000,
                (withErosion.getMaxRecessionDepth() / pureArrhenius.getMaxRecessionDepth() - 1.0) * 100);

        System.out.printf("%nAblated Mass Budget (carbon-phenolic, with erosion):%n");
        System.out.printf("  Total ablated mass:  %.4f kg%n", withErosion.getTotalAblatedMass());

        List<AblativeNozzleModel.AblativePoint> profile      = withErosion.getProfile();
        List<Double>                             massPerStation = withErosion.getAblatedMassPerStation();
        int n = profile.size();
        System.out.printf("  Per-station Δm/A [kg/m²]  (showing first 3 and last 3 of %d):%n", n);
        System.out.printf("    %-10s  %-12s  %s%n", "x (m)", "r (mm)", "Δm/A (kg/m²)");

        java.util.LinkedHashSet<Integer> indexSet = new java.util.LinkedHashSet<>();
        for (int i = 0; i < Math.min(3, n); i++) indexSet.add(i);
        for (int i = Math.max(0, n - 3); i < n; i++) indexSet.add(i);

        int prev = -1;
        for (int i : indexSet) {
            if (prev >= 0 && i > prev + 1) System.out.println("    ...");
            AblativeNozzleModel.AblativePoint pt = profile.get(i);
            System.out.printf("    %-10.4f  %-12.2f  %.4f%n",
                    pt.x(), pt.y() * 1000, massPerStation.get(i));
            prev = i;
        }

        AblativeNozzleModel ccThroat = new AblativeNozzleModel(params, contour)
                .setMaterial(AblativeNozzleModel.AblativeMaterial.CARBON_CARBON)
                .setInitialLinerThickness(0.010).setBurnTime(burnTime)
                .setErosionFactor(5.0e-6).calculate(heatProfile);

        System.out.printf("%nCarbon-Carbon Throat Insert (10 mm, k_e = 5×10⁻⁶ m/s):%n");
        System.out.printf("  Max recession:    %.4f mm%n", ccThroat.getMaxRecessionDepth() * 1000);
        System.out.printf("  Min remaining:    %.4f mm%n", ccThroat.getMinRemainingThickness() * 1000);
        System.out.printf("  Total ablated mass: %.6f kg%n", ccThroat.getTotalAblatedMass());
        System.out.printf("  Perforated:       %s%n", ccThroat.isPerforatedAnywhere() ? "YES" : "no");
    }
}
