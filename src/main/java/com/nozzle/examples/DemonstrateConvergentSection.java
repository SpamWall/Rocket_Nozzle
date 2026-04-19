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
import com.nozzle.core.PerformanceCalculator;
import com.nozzle.export.CSVExporter;
import com.nozzle.export.DXFExporter;
import com.nozzle.geometry.ConvergentSection;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.thermal.BoundaryLayerCorrection;

import java.nio.file.Files;
import java.nio.file.Path;

/** Demonstrates full convergent-section geometry, discharge coefficient, and geometry-complete exports. */
public class DemonstrateConvergentSection {

    public static void main(String[] ignoredArgs) throws Exception {
        Path outputDir = Path.of("nozzle_output");
        Files.createDirectories(outputDir);
        System.out.println("\n--- CONVERGENT SECTION: FULL NOZZLE GEOMETRY ---\n");

        final double RT = 0.05;

        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(RT).exitMach(3.0).chamberPressure(7e6)
                .chamberTemperature(3500).ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(25).wallAngleInitialDegrees(30)
                .lengthFraction(0.8).throatCurvatureRatio(0.382)
                .upstreamCurvatureRatio(1.5).convergentHalfAngleDegrees(30)
                .contractionRatio(4.0).build();

        ConvergentSection cs = new ConvergentSection(params).generate(60);

        System.out.println("Convergent section geometry:");
        System.out.printf("  Throat radius (r_t):         %.1f mm%n", RT * 1000);
        System.out.printf("  Chamber radius (r_c):        %.2f mm  (contraction ratio %.1f:1)%n",
                cs.getChamberRadius() * 1000, params.contractionRatio());
        System.out.printf("  Upstream curvature (r_cu):   %.2f mm  (= %.3f × r_t)%n",
                params.upstreamCurvatureRatio() * RT * 1000, params.upstreamCurvatureRatio());
        System.out.printf("  Convergent half-angle:       %.1f°%n", params.convergentHalfAngleDegrees());
        System.out.printf("  Arc end-point x:             %.2f mm%n", cs.getArcEndX() * 1000);
        System.out.printf("  Arc end-point radius:        %.2f mm%n", cs.getArcEndY() * 1000);
        System.out.printf("  Chamber face x:              %.2f mm%n", cs.getChamberFaceX() * 1000);
        System.out.printf("  Convergent section length:   %.2f mm%n", cs.getLength() * 1000);
        System.out.printf("  Convergent length ratio:     %.4f  (L_conv / D_t)%n",
                params.convergentLengthRatio());
        System.out.printf("  Contour wall points:         %d%n", cs.getContourPoints().size());

        System.out.println("\nDischarge coefficient (params.dischargeCoefficient()):");
        System.out.printf("  Cd  = %.5f  (r_cd/r_t=%.3f, r_cu/r_t=%.3f, γ=%.3f)%n",
                params.dischargeCoefficient(), params.throatCurvatureRatio(),
                params.upstreamCurvatureRatio(), params.gasProperties().gamma());
        System.out.printf("  ΔCd = %.3f%%%n", (1.0 - params.dischargeCoefficient()) * 100);

        System.out.println("\n  r_cu/r_t   r_cd/r_t   Cd        ΔCd (%)");
        System.out.println("  " + "-".repeat(44));
        double[] upRatios = {0.5, 1.0, 1.5, 2.0, 3.0};
        for (double uRatio : upRatios) {
            NozzleDesignParameters p2 = NozzleDesignParameters.builder()
                    .throatRadius(RT).exitMach(3.0).chamberPressure(7e6)
                    .chamberTemperature(3500).ambientPressure(101325)
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .upstreamCurvatureRatio(uRatio).build();
            double cd   = p2.dischargeCoefficient();
            String flag = Math.abs(uRatio - 1.5) < 1e-6 ? " <- default" : "";
            System.out.printf("  %-9.2f  %-9.3f  %.5f   %.3f%%%s%n",
                    uRatio, p2.throatCurvatureRatio(), cd, (1.0 - cd) * 100, flag);
        }

        NozzleContour divergent = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        divergent.generate(100);
        NozzleContour fullContour = divergent.withConvergentSection(cs);

        System.out.println("\nFull nozzle contour (chamber → exit):");
        System.out.printf("  Divergent-only length:    %.2f mm%n", divergent.getLength() * 1000);
        System.out.printf("  Full contour length:      %.2f mm%n", fullContour.getLength() * 1000);
        System.out.printf("  Total wall points:        %d%n", fullContour.getContourPoints().size());
        System.out.printf("  First point x:            %.2f mm  (chamber face)%n",
                fullContour.getContourPoints().getFirst().x() * 1000);
        System.out.printf("  Last point x:             %.2f mm  (exit plane)%n",
                fullContour.getContourPoints().getLast().x() * 1000);

        BoundaryLayerCorrection blFull =
                new BoundaryLayerCorrection(params, fullContour).calculate(null);
        BoundaryLayerCorrection blDivOnly =
                new BoundaryLayerCorrection(params, divergent).calculate(null);

        System.out.println("\nBoundary-layer comparison (divergent-only vs. full geometry):");
        System.out.printf("  Exit displacement thickness — divergent only: %.4f mm%n",
                blDivOnly.getExitDisplacementThickness() * 1000);
        System.out.printf("  Exit displacement thickness — full geometry:  %.4f mm%n",
                blFull.getExitDisplacementThickness() * 1000);

        PerformanceCalculator perf = PerformanceCalculator.simple(params).calculate();
        System.out.println("\nPerformance (Cd applied automatically from curvature ratios):");
        System.out.printf("  Cd_geo:  %.5f  (r_cd/r_t=%.3f, r_cu/r_t=%.3f)%n",
                params.dischargeCoefficient(), params.throatCurvatureRatio(),
                params.upstreamCurvatureRatio());
        System.out.printf("  thrust = %.3f kN   ṁ = %.4f kg/s   Isp = %.1f s%n",
                perf.getThrust() / 1000, perf.getMassFlowRate(), perf.getSpecificImpulse());

        new CSVExporter().exportContour(fullContour, outputDir.resolve("full_nozzle_contour.csv"));
        System.out.printf("%nExported full nozzle wall contour → full_nozzle_contour.csv%n");
        new DXFExporter().exportRevolutionProfile(fullContour, outputDir.resolve("full_nozzle_profile.dxf"));
        System.out.println("Exported full revolution profile → full_nozzle_profile.dxf");
    }
}
