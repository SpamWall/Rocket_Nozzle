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
import com.nozzle.thermal.RadiationCooledExtension;

import java.util.List;

/** Demonstrates the radiation-cooled extension model. */
public class DemonstrateRadiationCooledExtension {

    public static void main(String[] ignoredArgs) {
        System.out.println("\n--- RADIATION-COOLED NOZZLE EXTENSION ---\n");

        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05).exitMach(4.0).chamberPressure(3.5e6)
                .chamberTemperature(3250).ambientPressure(101325)
                .gasProperties(GasProperties.LOX_LH2_PRODUCTS)
                .numberOfCharLines(20).wallAngleInitialDegrees(30)
                .lengthFraction(0.8).axisymmetric(true).build();

        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        contour.generate(60);

        double extensionStartX = contour.getLength() * 0.30;

        RadiationCooledExtension baseModel = new RadiationCooledExtension(params, contour)
                .setMaterial(RadiationCooledExtension.ExtensionMaterial.NIOBIUM_C103)
                .setExtensionStartX(extensionStartX)
                .setEnvironmentTemperature(3.0)
                .calculate(List.of());

        System.out.printf("Extension starts at x = %.4f m (%.0f%% of bell length)%n",
                extensionStartX, 30.0);
        System.out.printf("Extension stations in profile: %d%n%n", baseModel.getProfile().size());

        System.out.printf("Niobium C-103 baseline (space, T_env = 3 K):%n");
        System.out.printf("  Max wall temperature:   %.0f K   (limit: %.0f K)%n",
                baseModel.getMaxWallTemperature(),
                RadiationCooledExtension.ExtensionMaterial.NIOBIUM_C103.temperatureLimit());
        System.out.printf("  Min temperature margin: %.0f K%n",  baseModel.getMinTemperatureMargin());
        System.out.printf("  Total radiated power:   %.2f kW%n", baseModel.getTotalRadiatedPower() / 1000);
        System.out.printf("  Overtemperature:        %s%n",
                baseModel.isOvertemperatureAnywhere() ? "YES" : "no");

        List<RadiationCooledExtension.ExtensionPoint> extProfile = baseModel.getProfile();
        int n = extProfile.size();
        if (n > 0) {
            System.out.printf("%n  Station profile (x, r, T_wall, T_aw, margin):%n");
            System.out.printf("    %-10s  %-8s  %-10s  %-10s  %-10s%n",
                    "x (m)", "r (mm)", "T_wall (K)", "T_aw (K)", "margin (K)");

            java.util.LinkedHashSet<Integer> indexSet = new java.util.LinkedHashSet<>();
            for (int i = 0; i < Math.min(3, n); i++) indexSet.add(i);
            for (int i = Math.max(0, n - 3); i < n; i++) indexSet.add(i);

            int prev = -1;
            for (int i : indexSet) {
                if (prev >= 0 && i > prev + 1) System.out.println("    ...");
                RadiationCooledExtension.ExtensionPoint pt = extProfile.get(i);
                System.out.printf("    %-10.4f  %-8.2f  %-10.0f  %-10.0f  %-10.0f%n",
                        pt.x(), pt.y() * 1000,
                        pt.wallTemperature(), pt.recoveryTemperature(), pt.temperatureMargin());
                prev = i;
            }
        }

        RadiationCooledExtension.ExtensionMaterial[] materials = {
                RadiationCooledExtension.ExtensionMaterial.NIOBIUM_C103,
                RadiationCooledExtension.ExtensionMaterial.RHENIUM_IRIDIUM,
                RadiationCooledExtension.ExtensionMaterial.TITANIUM_6AL_4V,
                RadiationCooledExtension.ExtensionMaterial.CARBON_CARBON,
        };

        System.out.printf("%nMaterial comparison (space environment, same extension geometry):%n");
        System.out.printf("  %-34s  %10s  %10s  %10s  %10s%n",
                "Material", "Max T (K)", "Margin (K)", "Power (kW)", "Overtemp");
        System.out.println("  " + "-".repeat(82));

        for (RadiationCooledExtension.ExtensionMaterial mat : materials) {
            RadiationCooledExtension mdl = new RadiationCooledExtension(params, contour)
                    .setMaterial(mat).setExtensionStartX(extensionStartX)
                    .setEnvironmentTemperature(3.0).calculate(List.of());

            System.out.printf("  %-34s  %10.0f  %10.0f  %10.2f  %10s%n",
                    mat.name(), mdl.getMaxWallTemperature(), mdl.getMinTemperatureMargin(),
                    mdl.getTotalRadiatedPower() / 1000, mdl.isOvertemperatureAnywhere() ? "YES" : "no");
        }

        RadiationCooledExtension groundTest = new RadiationCooledExtension(params, contour)
                .setMaterial(RadiationCooledExtension.ExtensionMaterial.NIOBIUM_C103)
                .setExtensionStartX(extensionStartX)
                .setEnvironmentTemperature(300.0).calculate(List.of());

        System.out.printf("%nEnvironment temperature effect (Niobium C-103, same extension):%n");
        System.out.printf("  Space  (T_env =   3 K): max T_wall = %.0f K, margin = %.0f K%n",
                baseModel.getMaxWallTemperature(), baseModel.getMinTemperatureMargin());
        System.out.printf("  Ground (T_env = 300 K): max T_wall = %.0f K, margin = %.0f K%n",
                groundTest.getMaxWallTemperature(), groundTest.getMinTemperatureMargin());
        System.out.printf("  Wall temperature increase on ground: +%.0f K%n",
                groundTest.getMaxWallTemperature() - baseModel.getMaxWallTemperature());
    }
}
