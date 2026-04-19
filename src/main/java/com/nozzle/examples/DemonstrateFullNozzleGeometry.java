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
import com.nozzle.export.DXFExporter;
import com.nozzle.geometry.FullNozzleGeometry;
import com.nozzle.moc.CharacteristicNet;

import java.nio.file.Files;
import java.nio.file.Path;

/** Demonstrates geometry-complete (chamber face → exit) nozzle with DXF exports. */
public class DemonstrateFullNozzleGeometry {

    public static void main(String[] ignoredArgs) throws Exception {
        Path outputDir = Path.of("nozzle_output");
        Files.createDirectories(outputDir);
        System.out.println("\n--- FULL NOZZLE GEOMETRY (CHAMBER FACE → EXIT) ---\n");

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
                .upstreamCurvatureRatio(1.5)
                .convergentHalfAngleDegrees(30.0)
                .build();

        FullNozzleGeometry fullGeom = new FullNozzleGeometry(params).generate(50, 100);

        System.out.println("Full nozzle geometry (Rao bell divergent section):");
        System.out.printf("  Chamber radius:     %.2f mm%n",
                Units.metersToMillimeters(fullGeom.getChamberRadius()));
        System.out.printf("  Throat radius:      %.2f mm%n",
                Units.metersToMillimeters(fullGeom.getThroatRadius()));
        System.out.printf("  Exit radius:        %.2f mm%n",
                Units.metersToMillimeters(fullGeom.getExitRadius()));
        System.out.printf("  Chamber face x:     %.2f mm%n",
                Units.metersToMillimeters(fullGeom.getChamberFaceX()));
        System.out.printf("  Exit x:             %.2f mm%n",
                Units.metersToMillimeters(fullGeom.getExitX()));
        System.out.printf("  Convergent length:  %.2f mm%n",
                Units.metersToMillimeters(fullGeom.getConvergentLength()));
        System.out.printf("  Divergent length:   %.2f mm%n",
                Units.metersToMillimeters(fullGeom.getDivergentLength()));
        System.out.printf("  Total length:       %.2f mm%n",
                Units.metersToMillimeters(fullGeom.getTotalLength()));
        System.out.printf("  Sonic-line Cd:      %.4f%n", fullGeom.getSonicLineCd());
        System.out.printf("  Total wall points:  %d%n", fullGeom.getWallPoints().size());

        CharacteristicNet net = new CharacteristicNet(params).generate();
        FullNozzleGeometry mocGeom = FullNozzleGeometry.fromMOC(params, net).generate(50, 0);

        System.out.println("\nMOC-backed full nozzle geometry:");
        System.out.printf("  Total wall points:  %d (convergent + MOC wall)%n",
                mocGeom.getWallPoints().size());
        System.out.printf("  Total length:       %.2f mm%n",
                Units.metersToMillimeters(mocGeom.getTotalLength()));

        DXFExporter dxf = new DXFExporter();
        Path fullNozzleDxf    = outputDir.resolve("full_nozzle_rao.dxf");
        Path fullNozzleRevDxf = outputDir.resolve("full_nozzle_rao_rev.dxf");
        Path mocDxf           = outputDir.resolve("full_nozzle_moc.dxf");

        dxf.exportFullNozzleProfile(fullGeom, fullNozzleDxf);
        dxf.exportFullNozzleRevolutionProfile(fullGeom, fullNozzleRevDxf);
        dxf.exportFullNozzleProfile(mocGeom, mocDxf);

        System.out.printf("%nExported geometry-complete DXF files:%n");
        System.out.printf("  %s  (Rao bell, profile only)%n", fullNozzleDxf.getFileName());
        System.out.printf("  %s  (Rao bell, closed revolution sketch)%n", fullNozzleRevDxf.getFileName());
        System.out.printf("  %s  (MOC divergent + convergent)%n", mocDxf.getFileName());
        System.out.println("  All three include the convergent section from the injector face.");
    }
}
