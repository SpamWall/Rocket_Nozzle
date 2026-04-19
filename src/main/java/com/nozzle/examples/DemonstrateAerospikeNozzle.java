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
import com.nozzle.moc.AerospikeNozzle;
import com.nozzle.moc.AltitudePerformance;

import java.nio.file.Files;
import java.nio.file.Path;

/** Demonstrates the aerospike (plug) nozzle: design, geometry, performance, and exports. */
public class DemonstrateAerospikeNozzle {

    private DemonstrateAerospikeNozzle() {}

    /**
     * Entry point.
     * @param ignoredArgs unused
     * @throws Exception on any I/O or calculation failure
     */
    public static void main(String[] ignoredArgs) throws Exception {
        Path outputDir = Path.of("nozzle_output");
        Files.createDirectories(outputDir);
        System.out.println("\n--- AEROSPIKE (PLUG) NOZZLE ---\n");

        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05).exitMach(3.0).chamberPressure(7e6)
                .chamberTemperature(3500).ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20).wallAngleInitialDegrees(30)
                .lengthFraction(0.8).axisymmetric(true).build();

        System.out.println("Generating Aerospike nozzle (60% spike radius ratio, 80% truncation)...");
        long t0 = System.currentTimeMillis();
        AerospikeNozzle nozzle = new AerospikeNozzle(params, 0.60, 0.80, 100).generate();
        System.out.printf("  Completed in %d ms%n", System.currentTimeMillis() - t0);

        double rt  = params.throatRadius();
        double ri  = rt * nozzle.getSpikeRadiusRatio();
        System.out.println("\nSpike Geometry:");
        System.out.printf("  Outer throat radius (rt):     %.1f mm%n",  rt  * 1000);
        System.out.printf("  Inner throat radius (ri):     %.1f mm%n",  ri  * 1000);
        System.out.printf("  Spike radius ratio (ri/rt):   %.2f%n",     nozzle.getSpikeRadiusRatio());
        System.out.printf("  Truncation fraction:          %.2f%n",     nozzle.getTruncationFraction());
        System.out.printf("  Full spike length:            %.4f m%n",   nozzle.getFullSpikeLength());
        System.out.printf("  Truncated spike length:       %.4f m%n",   nozzle.getTruncatedLength());
        System.out.printf("  Truncated base radius:        %.2f mm%n",  nozzle.getTruncatedBaseRadius() * 1000);
        System.out.printf("  Spike contour points (full):  %d%n",       nozzle.getFullSpikeContour().size());
        System.out.printf("  Spike contour points (trunc): %d%n",       nozzle.getTruncatedSpikeContour().size());

        System.out.println("\nAnnular Flow Areas:");
        System.out.printf("  Throat area (At):   %.4f cm²%n", nozzle.getAnnularThroatArea() * 1e4);
        System.out.printf("  Exit area   (Ae):   %.4f cm²%n", nozzle.getAnnularExitArea()   * 1e4);
        System.out.printf("  Ae/At ratio:        %.3f  (design: %.3f)%n",
                nozzle.getAnnularExitArea() / nozzle.getAnnularThroatArea(), params.exitAreaRatio());

        System.out.println("\nThrust Coefficient Comparison (Aerospike vs. Bell Nozzle):");
        System.out.printf("  %-18s  %-10s  %-10s  %-10s%n", "Condition", "Aerospike", "Bell", "Advantage");
        System.out.println("  " + "-".repeat(54));
        double[][] conditions = {{101325,0},{50000,5},{20000,12},{5000,20},{1000,32}};
        for (double[] c : conditions) {
            double cfA = nozzle.calculateThrustCoefficient(c[0]);
            double cfB = nozzle.calculateBellNozzleThrustCoefficient(c[0]);
            System.out.printf("  pa=%.0f Pa (~%.0f km)  %.4f     %.4f     %+.4f%n",
                    c[0], c[1], cfA, cfB, cfA - cfB);
        }

        double[] altitudePressures = {101325, 70000, 50000, 30000, 20000, 10000, 5000, 2000, 1000};
        AltitudePerformance perf = nozzle.calculateAltitudePerformance(altitudePressures);

        System.out.println("\nAltitude Performance Sweep:");
        System.out.printf("  %-14s  %-10s  %-10s  %-12s%n", "pa (Pa)", "Aero Cf", "Bell Cf", "Aero Isp (s)");
        System.out.println("  " + "-".repeat(50));
        for (int i = 0; i < altitudePressures.length; i++) {
            System.out.printf("  %-14.0f  %-10.4f  %-10.4f  %-12.1f%n",
                    perf.ambientPressures()[i], perf.aerospikeCf()[i],
                    perf.bellNozzleCf()[i], perf.aerospikeIsp()[i]);
        }

        int iMaxAdv = perf.indexOfMaxAltitudeAdvantage();
        System.out.printf("%n  Max altitude advantage at pa=%.0f Pa: +%.4f Cf%n",
                perf.ambientPressures()[iMaxAdv],
                perf.aerospikeCf()[iMaxAdv] - perf.bellNozzleCf()[iMaxAdv]);
        System.out.printf("  Average advantage over sweep:         +%.4f Cf%n",
                perf.averageAltitudeAdvantage());
        System.out.printf("%n  Isp at sea level:  %.1f s%n", nozzle.calculateIsp(101325));
        System.out.printf("  Isp at ~vacuum:    %.1f s%n",   nozzle.calculateIsp(1000));

        System.out.println("\nAerospike Exports:");
        Path aeroDir = outputDir.resolve("Aerospike");
        Files.createDirectories(aeroDir);

        CSVExporter csv = new CSVExporter();
        csv.exportSpikeContour(nozzle, aeroDir.resolve("spike_contour.csv"));
        csv.exportAltitudePerformance(perf, aeroDir.resolve("altitude_performance.csv"));
        csv.exportAerospikeReport(nozzle, altitudePressures, aeroDir.resolve("report"));
        System.out.println("  CSV: spike_contour.csv, altitude_performance.csv, report/");

        new DXFExporter().setScaleFactor(1000)
                .exportAerospikeContour(nozzle, aeroDir.resolve("aerospike.dxf"));
        System.out.println("  DXF: aerospike.dxf  (layers: SPIKE, COWL, AXIS)");

        new STEPExporter().exportAerospikeRevolvedSolid(nozzle, aeroDir.resolve("aerospike.step"));
        System.out.println("  STEP: aerospike.step  (truncated spike, revolved solid)");

        STLExporter stl = new STLExporter().setCircumferentialSegments(72).setBinaryFormat(true);
        stl.exportAerospikeMesh(nozzle, aeroDir.resolve("aerospike.stl"));
        System.out.printf("  STL:  aerospike.stl  (%d triangles, binary)%n",
                stl.estimateTriangleCount(nozzle.getTruncatedSpikeContour().size()));

        CFDMeshExporter cfd = new CFDMeshExporter().setAxialCells(100).setRadialCells(50);
        cfd.exportAerospike(nozzle, aeroDir.resolve("Aerospike_blockMeshDict"),
                CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);
        cfd.exportAerospike(nozzle, aeroDir.resolve("aerospike.geo"), CFDMeshExporter.Format.GMSH_GEO);
        cfd.exportAerospike(nozzle, aeroDir.resolve("aerospike.xyz"), CFDMeshExporter.Format.PLOT3D);
        System.out.println("  CFD:  Aerospike_blockMeshDict, aerospike.geo, aerospike.xyz");

        Path foamDir = aeroDir.resolve("aerospike_openfoam");
        new OpenFOAMExporter().setAxialCells(150).setRadialCells(60)
                .exportAerospikeCase(nozzle, foamDir);
        System.out.println("  OpenFOAM rhoCentralFoam case: aerospike_openfoam/");
        System.out.printf("%nAerospike output saved to: %s%n", aeroDir.toAbsolutePath());
    }
}
