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
import com.nozzle.export.CFDMeshExporter;
import com.nozzle.export.OpenFOAMExporter;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.moc.CharacteristicNet;

import java.nio.file.Files;
import java.nio.file.Path;

/** Demonstrates y⁺-controlled first-cell-height grading for CFD meshes. */
public class DemonstrateYPlusGrading {

    public static void main(String[] ignoredArgs) throws Exception {
        Path outputDir = Path.of("nozzle_output");
        Files.createDirectories(outputDir);
        System.out.println("\n--- y⁺-CONTROLLED FIRST-CELL-HEIGHT GRADING ---\n");

        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05).exitMach(3.0).chamberPressure(7e6)
                .chamberTemperature(3500).ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20).wallAngleInitialDegrees(30)
                .lengthFraction(0.8).axisymmetric(true).build();

        CharacteristicNet net = new CharacteristicNet(params).generate();
        NozzleContour contour = NozzleContour.fromMOCWallPoints(params, net.getWallPoints());
        contour.generate(100);

        double gamma   = params.gasProperties().gamma();
        double Tc      = params.chamberTemperature();
        double Pc      = params.chamberPressure();
        double R       = params.gasProperties().gasConstant();
        double Tt      = Tc * 2.0 / (gamma + 1.0);
        double Pt      = Pc * Math.pow(2.0 / (gamma + 1.0), gamma / (gamma - 1.0));
        double rhoT    = Pt / (R * Tt);
        double aT      = Math.sqrt(gamma * R * Tt);
        double muRef   = 1.716e-5;
        double Tref    = 273.15;
        double S       = 110.4;
        double muT     = muRef * Math.pow(Tt / Tref, 1.5) * (Tref + S) / (Tt + S);
        double ReThroat = rhoT * aT * (2.0 * params.throatRadius()) / muT;
        double Cf      = 0.026 * Math.pow(ReThroat, -0.2);
        double uTau    = aT * Math.sqrt(Cf / 2.0);
        double nu      = muT / rhoT;
        double y1      = nu / uTau;

        System.out.printf("Throat flow conditions:%n");
        System.out.printf("  Throat temperature:   %.0f K%n",   Tt);
        System.out.printf("  Throat density:       %.3f kg/m³%n", rhoT);
        System.out.printf("  Throat Mach speed:    %.1f m/s%n",  aT);
        System.out.printf("  Dynamic viscosity:    %.3e Pa·s%n", muT);
        System.out.printf("  Throat Re (diameter): %.3e%n",      ReThroat);
        System.out.printf("  Skin-friction coeff:  %.4f%n",      Cf);
        System.out.printf("  y⁺ = 1 cell height:  %.3e m  (%.4f mm)%n", y1, y1 * 1000);

        System.out.println("\nCFDMeshExporter (y⁺-graded, N_r = 80):");
        Path bmdYPlus = outputDir.resolve("blockMeshDict_yplus");
        Path geoYPlus = outputDir.resolve("nozzle_yplus.geo");
        Path xyzYPlus = outputDir.resolve("nozzle_yplus.xyz");

        CFDMeshExporter yPlusExporter = new CFDMeshExporter()
                .setAxialCells(200).setRadialCells(80).setFirstLayerThickness(y1);
        yPlusExporter.export(contour, bmdYPlus,  CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);
        yPlusExporter.export(contour, geoYPlus,  CFDMeshExporter.Format.GMSH_GEO);
        yPlusExporter.export(contour, xyzYPlus,  CFDMeshExporter.Format.PLOT3D);

        System.out.printf("  Exported blockMeshDict → %s%n", bmdYPlus.getFileName());
        System.out.printf("  Exported Gmsh .geo     → %s%n", geoYPlus.getFileName());
        System.out.printf("  Exported Plot3D .xyz   → %s%n", xyzYPlus.getFileName());

        System.out.println("\nOpenFOAMExporter (y⁺-graded complete case):");
        Path yPlusCase = outputDir.resolve("openfoam_case_yplus");
        new OpenFOAMExporter().setAxialCells(300).setRadialCells(100)
                .setFirstLayerThickness(y1).setTurbulenceEnabled(true)
                .setTurbulenceIntensity(0.05).exportCase(params, contour, yPlusCase);

        System.out.printf("  Exported full case     → %s%n", yPlusCase.toAbsolutePath());
        System.out.println("  Run with: blockMesh && rhoCentralFoam");

        double exitRadius     = contour.getContourPoints().getLast().y();
        double derivedGrading = Math.max(1.0, exitRadius / y1);
        System.out.printf("%nGrading ratio comparison (domain height = exit radius = %.4f m):%n", exitRadius);
        System.out.printf("  Fixed radialGrading (default):  %.1f%n",      4.0);
        System.out.printf("  y⁺-derived grading (y1=%.2e m): %.1f%n",      y1, derivedGrading);
        System.out.printf("  First-cell / domain ratio:       %.2e%n",      y1 / exitRadius);
    }
}
