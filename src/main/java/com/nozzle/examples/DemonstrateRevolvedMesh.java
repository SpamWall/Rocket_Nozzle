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
import com.nozzle.export.RevolvedMeshExporter;
import com.nozzle.geometry.FullNozzleGeometry;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.moc.CharacteristicNet;

import java.nio.file.Files;
import java.nio.file.Path;

/** Demonstrates full 3-D revolved mesh export in OpenFOAM, Gmsh, and Plot3D formats. */
public class DemonstrateRevolvedMesh {

    public static void main(String[] args) throws Exception {
        Path outputDir = Path.of("nozzle_output");
        Files.createDirectories(outputDir);
        System.out.println("\n--- FULL 3-D REVOLVED MESH EXPORT ---\n");

        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05).exitMach(3.0).chamberPressure(7e6)
                .chamberTemperature(3500).ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20).wallAngleInitialDegrees(30)
                .lengthFraction(0.8).axisymmetric(true).build();

        CharacteristicNet net = new CharacteristicNet(params).generate();
        NozzleContour contour = NozzleContour.fromMOCWallPoints(params, net.getWallPoints());
        contour.generate(100);

        int axial = 100, radial = 40, azimuthal = 16;

        System.out.printf("Mesh parameters:%n");
        System.out.printf("  Axial cells:      %d%n", axial);
        System.out.printf("  Radial cells:     %d%n", radial);
        System.out.printf("  Azimuthal sectors:%d  (%.1f° each)%n", azimuthal, 360.0 / azimuthal);

        Path bmd3d = outputDir.resolve("blockMeshDict_3d");
        new RevolvedMeshExporter().setAxialCells(axial).setRadialCells(radial)
                .setAzimuthalCells(azimuthal).setExpansionRatio(4.0)
                .export(contour, bmd3d, RevolvedMeshExporter.Format.OPENFOAM_BLOCKMESH);

        long bmdVertices = axial + 1 + (long)(axial + 1) * azimuthal;
        long bmdBlocks   = (long) axial * azimuthal;
        System.out.printf("%nOpenFOAM blockMeshDict_3d:%n");
        System.out.printf("  Vertices:  %d  (axis: %d, wall: %d)%n",
                bmdVertices, axial + 1, (axial + 1) * azimuthal);
        System.out.printf("  Hex blocks:%d  (%d axial × %d azimuthal)%n", bmdBlocks, axial, azimuthal);
        System.out.printf("  Total cells: %,d%n", bmdBlocks * radial);
        System.out.printf("  File size: %.1f kB%n", java.nio.file.Files.size(bmd3d) / 1024.0);

        Path geo3d = outputDir.resolve("nozzle_3d.geo");
        new RevolvedMeshExporter().setAxialCells(axial).setRadialCells(radial)
                .setAzimuthalCells(azimuthal)
                .export(contour, geo3d, RevolvedMeshExporter.Format.GMSH_GEO);
        System.out.printf("%nGmsh nozzle_3d.geo:%n");
        System.out.printf("  2-D surface extruded via Extrude { Rotate { {1,0,0}, ... 2π } }%n");
        System.out.printf("  Layers: %d  (one hex layer per azimuthal sector)%n", azimuthal);
        System.out.printf("  File size: %.1f kB%n", java.nio.file.Files.size(geo3d) / 1024.0);

        Path xyz3d = outputDir.resolve("nozzle_3d.xyz");
        new RevolvedMeshExporter().setAxialCells(axial).setRadialCells(radial)
                .setAzimuthalCells(azimuthal)
                .export(contour, xyz3d, RevolvedMeshExporter.Format.PLOT3D);
        int ni = axial + 1, nj = radial + 1, nk = azimuthal + 1;
        System.out.printf("%nPlot3D nozzle_3d.xyz:%n");
        System.out.printf("  Grid: %d × %d × %d  (ni × nj × nk)%n", ni, nj, nk);
        System.out.printf("  Total points: %,d%n", (long) ni * nj * nk);
        System.out.printf("  File size: %.1f kB%n", java.nio.file.Files.size(xyz3d) / 1024.0);

        double exitRadius   = contour.getContourPoints().getLast().y();
        double y1           = 2e-5;
        double yPlusGrading = Math.max(1.0, exitRadius / y1);
        System.out.printf("%nRadial grading comparison (domain H = exit radius = %.4f m):%n", exitRadius);
        System.out.printf("  Default expansionRatio:   %.1f%n", 4.0);
        System.out.printf("  y⁺-derived (y1 = %.0f μm): %.0f   (first cell / H = %.2e)%n",
                y1 * 1e6, yPlusGrading, y1 / exitRadius);

        Path bmd3dYPlus = outputDir.resolve("blockMeshDict_3d_yplus");
        new RevolvedMeshExporter().setAxialCells(axial).setRadialCells(radial)
                .setAzimuthalCells(azimuthal).setFirstLayerThickness(y1)
                .export(contour, bmd3dYPlus, RevolvedMeshExporter.Format.OPENFOAM_BLOCKMESH);
        System.out.printf("  y⁺-graded mesh written → %s%n", bmd3dYPlus.getFileName());

        NozzleDesignParameters fullParams = NozzleDesignParameters.builder()
                .throatRadius(0.05).exitMach(3.0).chamberPressure(7e6)
                .chamberTemperature(3500).ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20).wallAngleInitialDegrees(30)
                .lengthFraction(0.8).axisymmetric(true).contractionRatio(4.0).build();
        FullNozzleGeometry fullGeom = new FullNozzleGeometry(fullParams).generate(40, 100);

        Path bmd3dFull = outputDir.resolve("blockMeshDict_3d_full");
        new RevolvedMeshExporter().setAxialCells(axial).setRadialCells(radial).setAzimuthalCells(azimuthal)
                .export(fullGeom, bmd3dFull, RevolvedMeshExporter.Format.OPENFOAM_BLOCKMESH);
        System.out.printf("%nGeometry-complete 3-D mesh (%d wall points, conv + div):%n",
                fullGeom.getWallPoints().size());
        System.out.printf("  Written → %s%n", bmd3dFull.getFileName());
    }
}
