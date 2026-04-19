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
import com.nozzle.moc.CharacteristicNet;
import com.nozzle.thermal.BoundaryLayerCorrection;
import com.nozzle.thermal.CoolantChannel;
import com.nozzle.thermal.HeatTransferModel;

import java.nio.file.Files;
import java.nio.file.Path;

/** Demonstrates thermal and boundary layer analysis with coolant channel sizing. */
public class DemonstrateThermalAnalysis {

    public static void main(String[] ignoredArgs) throws Exception {
        Path outputDir = Path.of("nozzle_output");
        Files.createDirectories(outputDir);
        System.out.println("\n--- THERMAL & BOUNDARY LAYER ANALYSIS ---\n");

        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();

        CharacteristicNet net = new CharacteristicNet(params).generate();
        NozzleContour contour = NozzleContour.fromMOCWallPoints(params, net.getWallPoints());
        contour.generate(100);

        CoolantChannel channel = new CoolantChannel(contour)
                .setChannelGeometry(120, 0.003, 0.005, 0.001)
                .setWallConductivity(20.0)
                .setCoolant(CoolantChannel.CoolantProperties.RP1, 2.0, 300.0, 8.5e6)
                .calculate();

        System.out.println("Coolant Channel Sizing (hydraulics only):");
        System.out.printf("  Hydraulic diameter:   %.2f mm%n",  channel.hydraulicDiameter() * 1000);
        System.out.printf("  Flow area (total):    %.2f mm²%n", channel.totalFlowArea() * 1e6);
        CoolantChannel.ChannelPoint throatChannel = channel.getProfile().getFirst();
        System.out.printf("  Velocity:             %.2f m/s%n",  throatChannel.velocity());
        System.out.printf("  Reynolds number:      %.0f%n",      throatChannel.reynoldsNumber());
        System.out.printf("  Nusselt number:       %.1f%n",      throatChannel.nusseltNumber());
        System.out.printf("  h_coolant:            %.0f W/(m²·K)%n", throatChannel.heatTransferCoeff());
        System.out.printf("  Total pressure drop:  %.2f kPa%n", channel.getTotalPressureDrop() / 1000);

        HeatTransferModel heatModel = new HeatTransferModel(params, contour)
                .setWallProperties(20.0, 0.003)
                .setEmissivity(0.8)
                .setCoolantChannel(channel)
                .calculate(net.getAllPoints());

        System.out.println("\nHeat Transfer Results (with sized channel):");
        System.out.printf("  Max Wall Temp:    %.0f K%n",    heatModel.getMaxWallTemperature());
        System.out.printf("  Max Heat Flux:    %.2e W/m²%n", heatModel.getMaxHeatFlux());
        System.out.printf("  Total Heat Load:  %.2f kW%n",   heatModel.getTotalHeatLoad() / 1000);

        channel.calculate(heatModel.getWallThermalProfile());

        System.out.println("\nCoolant Channel Thermal Analysis:");
        System.out.printf("  Coolant temp rise:    %.1f K%n",  channel.getCoolantTemperatureRise());
        System.out.printf("  Min boiling margin:   %.1f K%n",  channel.getMinBoilingMargin());
        System.out.printf("  Fully subcooled:      %s%n",
                channel.isFullySubcooled() ? "YES" : "NO — nucleate boiling predicted");

        BoundaryLayerCorrection blModel = new BoundaryLayerCorrection(params, contour)
                .setForceTurbulent(true)
                .calculate(net.getAllPoints());

        System.out.println("\nBoundary Layer Results:");
        System.out.printf("  Exit δ*:          %.3f mm%n", blModel.getExitDisplacementThickness() * 1000);
        System.out.printf("  Exit θ:           %.3f mm%n", blModel.getExitMomentumThickness() * 1000);
        System.out.printf("  Corrected A/A*:   %.2f%n", blModel.getCorrectedAreaRatio());
        System.out.printf("  Cf Loss:          %.4f%n", blModel.getThrustCoefficientLoss());

        CSVExporter csvExporter = new CSVExporter();
        csvExporter.exportThermalProfile(heatModel, outputDir.resolve("thermal_profile.csv"));
        csvExporter.exportBoundaryLayerProfile(blModel, outputDir.resolve("boundary_layer.csv"));
        System.out.println("\nExported thermal and BL profiles to CSV");
    }
}
