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
import com.nozzle.export.DXFExporter;
import com.nozzle.export.STEPExporter;
import com.nozzle.export.STLExporter;
import com.nozzle.geometry.NozzleContour;

import java.nio.file.Files;
import java.nio.file.Path;

/** Demonstrates a planar (2-D rectangular) wind-tunnel nozzle with span sensitivity and exports. */
public class DemonstratePlanarWindTunnelNozzle {

    private DemonstratePlanarWindTunnelNozzle() {}

    /**
     * Entry point.
     * @param ignoredArgs unused
     * @throws Exception on any I/O or calculation failure
     */
    public static void main(String[] ignoredArgs) throws Exception {
        Path outputDir = Path.of("nozzle_output");
        Files.createDirectories(outputDir);
        System.out.println("\n--- PLANAR (2-D RECTANGULAR) WIND TUNNEL NOZZLE ---\n");

        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.050)
                .throatWidth(0.150)
                .exitMach(2.5)
                .chamberPressure(500_000)
                .chamberTemperature(400)
                .ambientPressure(101_325)
                .gasProperties(GasProperties.AIR)
                .numberOfCharLines(30)
                .wallAngleInitialDegrees(20)
                .lengthFraction(0.85)
                .axisymmetric(false)
                .build();

        System.out.printf("Throat half-height : %.1f mm%n",
                params.throatRadius() * 1000);
        System.out.printf("Throat span        : %.1f mm%n",
                params.throatWidth() * 1000);
        System.out.printf("Throat area (rect) : %.0f mm²  (vs π·r² = %.0f mm² axisymmetric)%n",
                params.throatArea() * 1e6,
                Math.PI * params.throatRadius() * params.throatRadius() * 1e6);
        System.out.printf("Exit area ratio    : %.4f%n", params.exitAreaRatio());
        System.out.printf("Exit half-height   : %.1f mm%n",
                params.exitRadius() * 1000);
        System.out.printf("Exit area (rect)   : %.0f mm²%n",
                params.exitArea() * 1e6);

        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        contour.generate(80);
        System.out.printf("%nContour points     : %d%n", contour.getContourPoints().size());
        System.out.printf("Nozzle length      : %.1f mm%n", contour.getLength() * 1000);

        System.out.printf("%nIdeal Isp          : %.1f s%n", params.idealSpecificImpulse());
        System.out.printf("Ideal Cf           : %.4f%n",    params.idealThrustCoefficient());
        System.out.printf("c*                 : %.1f m/s%n", params.characteristicVelocity());

        System.out.println("\nSpan sensitivity (throat area and exit area vs. span):");
        System.out.printf("  %-12s  %-14s  %-14s%n", "Span (mm)", "At (mm²)", "Ae (mm²)");
        for (double spanMm : new double[]{50, 100, 150, 200, 300}) {
            NozzleDesignParameters p = NozzleDesignParameters.builder()
                    .throatRadius(0.050).throatWidth(spanMm / 1000)
                    .exitMach(2.5).chamberPressure(500_000)
                    .chamberTemperature(400).ambientPressure(101_325)
                    .gasProperties(GasProperties.AIR)
                    .numberOfCharLines(20).wallAngleInitialDegrees(20)
                    .lengthFraction(0.85).axisymmetric(false).build();
            System.out.printf("  %-12.0f  %-14.1f  %-14.1f%n",
                    spanMm, p.throatArea() * 1e6, p.exitArea() * 1e6);
        }

        Path dxfOut = outputDir.resolve("wind_tunnel_nozzle.dxf");
        new DXFExporter().exportContour(contour, dxfOut);
        System.out.printf("%nDXF contour        → %s%n", dxfOut.getFileName());

        Path csvOut = outputDir.resolve("wind_tunnel_params.csv");
        new CSVExporter().exportDesignParameters(params, csvOut);
        System.out.printf("CSV parameters     → %s%n", csvOut.getFileName());

        Path stlOut = outputDir.resolve("wind_tunnel_nozzle.stl");
        new STLExporter().exportMesh(contour, stlOut);
        System.out.printf("STL mesh           → %s%n", stlOut.getFileName());

        Path stepOut = outputDir.resolve("wind_tunnel_profile.step");
        new STEPExporter().exportProfileCurve(contour, stepOut);
        System.out.printf("STEP profile       → %s%n", stepOut.getFileName());

        System.out.println("\nNote: for a planar nozzle the MOC uses the 2-D irrotationality");
        System.out.println("  equation (no r-term), giving a slightly different wall contour");
        System.out.println("  than the equivalent axisymmetric design at the same Mach number.");
        System.out.println("  Physical throat area = 2 × throatRadius × throatWidth.");
    }
}
