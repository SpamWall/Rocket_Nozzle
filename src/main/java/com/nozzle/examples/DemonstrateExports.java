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
import com.nozzle.export.*;
import com.nozzle.geometry.FullNozzleGeometry;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.moc.CharacteristicNet;

import java.nio.file.Files;
import java.nio.file.Path;

/** Demonstrates CAD and mesh exports in all supported formats. */
public class DemonstrateExports {

    public static void main(String[] ignoredArgs) throws Exception {
        Path outputDir = Path.of("nozzle_output");
        Files.createDirectories(outputDir);
        System.out.println("\n--- CAD & MESH EXPORTS ---\n");

        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05).exitMach(3.0).chamberPressure(7e6)
                .chamberTemperature(3500).ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20).wallAngleInitialDegrees(30)
                .lengthFraction(0.8).axisymmetric(true).build();

        CharacteristicNet net = new CharacteristicNet(params).generate();
        NozzleContour contour = NozzleContour.fromMOCWallPoints(params, net.getWallPoints());
        contour.generate(100);

        NozzleDesignParameters fullParams = NozzleDesignParameters.builder()
                .throatRadius(0.05).exitMach(3.0).chamberPressure(7e6)
                .chamberTemperature(3500).ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20).wallAngleInitialDegrees(30)
                .lengthFraction(0.8).axisymmetric(true).contractionRatio(4.0).build();
        FullNozzleGeometry fullGeom = new FullNozzleGeometry(fullParams).generate(40, 100);

        DXFExporter dxfExporter = new DXFExporter().setScaleFactor(1000);
        dxfExporter.exportContour(contour, outputDir.resolve("nozzle_contour.dxf"));
        dxfExporter.exportRevolutionProfile(contour, outputDir.resolve("nozzle_revolution.dxf"));
        dxfExporter.exportFullNozzleProfile(fullGeom, outputDir.resolve("nozzle_full.dxf"));
        dxfExporter.exportFullNozzleRevolutionProfile(fullGeom, outputDir.resolve("nozzle_full_rev.dxf"));
        System.out.println("Exported DXF files (divergent + geometry-complete)");

        STEPExporter stepExporter = new STEPExporter();
        stepExporter.exportRevolvedSolid(contour,  outputDir.resolve("nozzle.step"));
        stepExporter.exportRevolvedSolid(fullGeom, outputDir.resolve("nozzle_full.step"));
        stepExporter.exportProfileCurve(contour,   outputDir.resolve("nozzle_profile.step"));
        System.out.println("Exported STEP files (revolved solid + profile curve)");

        STLExporter stlExporter = new STLExporter()
                .setCircumferentialSegments(72).setScaleFactor(1000).setBinaryFormat(true);
        stlExporter.exportMesh(contour, outputDir.resolve("nozzle.stl"));
        stlExporter.exportMesh(fullGeom, outputDir.resolve("nozzle_full.stl"));
        System.out.printf("Exported STL meshes (divergent %d tri, full nozzle %d tri)%n",
                stlExporter.estimateTriangleCount(100),
                stlExporter.estimateTriangleCount(fullGeom.getWallPoints().size()));

        CSVExporter csvExporter = new CSVExporter();
        csvExporter.exportContour(contour, outputDir.resolve("contour_div.csv"));
        csvExporter.exportContour(fullGeom, outputDir.resolve("contour_full.csv"));
        System.out.println("Exported CSV contour files (divergent + geometry-complete)");

        CFDMeshExporter cfdExporter = new CFDMeshExporter()
                .setAxialCells(100).setRadialCells(50).setExpansionRatio(1.2);
        cfdExporter.export(contour, outputDir.resolve("blockMeshDict"),
                CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);
        cfdExporter.export(fullGeom, outputDir.resolve("blockMeshDict_full"),
                CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);
        cfdExporter.export(contour,  outputDir.resolve("nozzle.geo"),  CFDMeshExporter.Format.GMSH_GEO);
        cfdExporter.export(fullGeom, outputDir.resolve("nozzle_full.geo"), CFDMeshExporter.Format.GMSH_GEO);
        cfdExporter.export(contour,  outputDir.resolve("nozzle.xyz"),  CFDMeshExporter.Format.PLOT3D);
        cfdExporter.export(contour,  outputDir.resolve("nozzle.cgns"), CFDMeshExporter.Format.CGNS);
        System.out.println("Exported CFD mesh files (OpenFOAM, Gmsh, Plot3D, CGNS)");

        Path foamCase     = outputDir.resolve("openfoam_case");
        Path foamCaseFull = outputDir.resolve("openfoam_case_full");
        new OpenFOAMExporter().setAxialCells(300).setRadialCells(100)
                .setRadialGrading(5.0).setTurbulenceIntensity(0.05)
                .exportCase(params, contour, foamCase);
        new OpenFOAMExporter().setAxialCells(300).setRadialCells(100)
                .setRadialGrading(5.0).setTurbulenceIntensity(0.05)
                .exportCase(fullParams, fullGeom, foamCaseFull);
        System.out.printf("Exported OpenFOAM cases → %s / %s%n",
                foamCase.getFileName(), foamCaseFull.getFileName());
        System.out.println("  Run with: blockMesh && rhoCentralFoam");
        System.out.println("\nAll export files saved to: " + outputDir.toAbsolutePath());
    }
}
