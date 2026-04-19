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
import com.nozzle.moc.DualBellNozzle;
import com.nozzle.moc.RaoNozzle;

import java.nio.file.Files;
import java.nio.file.Path;

/** Demonstrates the dual-bell altitude-compensating nozzle. */
public class DemonstrateDualBellNozzle {

    public static void main(String[] args) throws Exception {
        Path outputDir = Path.of("nozzle_output");
        Files.createDirectories(outputDir);
        System.out.println("\n--- DUAL-BELL ALTITUDE-COMPENSATING NOZZLE ---\n");

        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05).exitMach(5.0).chamberPressure(7e6)
                .chamberTemperature(3500).ambientPressure(101_325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20).wallAngleInitialDegrees(30)
                .lengthFraction(0.8).axisymmetric(true).build();

        RaoNozzle rao = new RaoNozzle(params).generate();
        double raoIsp = rao.calculateThrustCoefficient()
                        * params.characteristicVelocity() / 9.80665;

        System.out.printf("Full exit area ratio: %.1f  (Me = %.1f, γ = %.2f)%n",
                params.exitAreaRatio(), params.exitMach(), params.gasProperties().gamma());
        System.out.printf("Rao bell length:      %.3f m%n", rao.getActualLength());
        System.out.printf("Rao Isp (sea level, massively over-expanded): %.1f s%n", raoIsp);

        System.out.println();
        double[] transitionARs = {3.0, 4.0, 6.0};
        System.out.println("transition   kink       base      full     switch");
        System.out.println("  AR         radius     length   length   pressure    SL Isp  HA Isp  gain");
        System.out.println("  " + "-".repeat(75));

        for (double ar : transitionARs) {
            DualBellNozzle db = new DualBellNozzle(params, ar).generate();
            DualBellNozzle.PerformanceSummary s = db.getPerformanceSummary();
            System.out.printf("  %4.1f  %8.1f mm  %6.3f m  %6.3f m  %7.0f Pa  %5.1f s  %5.1f s  %+.1f s%n",
                    ar, db.getTransitionRadius() * 1000, db.getBaseLength(), db.getTotalLength(),
                    s.transitionPressure(), s.seaLevelIsp(), s.highAltitudeIsp(), s.ispGain());
        }

        System.out.println();
        DualBellNozzle db4 = new DualBellNozzle(params, 4.0).generate();
        System.out.printf("Dual-bell  AR_kink = 4.0  (kink at M = %.2f):%n", db4.getTransitionMach());
        System.out.printf("  Inflection angle:      %.2f°%n", Math.toDegrees(db4.getInflectionAngle()));
        System.out.printf("  Base exit angle (θ_E1):%.2f°%n", Math.toDegrees(db4.getBaseExitAngle()));
        System.out.printf("  Extension exit (θ_E2): %.2f°%n", Math.toDegrees(db4.getExtensionExitAngle()));
        System.out.printf("  Kink index / total pts:%d / %d%n",
                db4.getKinkIndex(), db4.getContourPoints().size());
        System.out.printf("  Transition pressure:   %.0f Pa  (%.1f%% of sea level)%n",
                db4.getTransitionPressure(), db4.getTransitionPressure() / 101_325.0 * 100);
        System.out.printf("  Sea-level Isp:         %.1f s   (vs Rao %.1f s — %+.1f s benefit)%n",
                db4.getSeaLevelIsp(), raoIsp, db4.getSeaLevelIsp() - raoIsp);
        System.out.printf("  High-altitude Isp:     %.1f s%n", db4.getHighAltitudeIsp());

        System.out.println("\nDLR subscale cold-flow cross-check  (N₂, γ=1.4; Génin & Stark 2009):");
        final double EPS_B  = 3.9;
        final double M_EXIT = 3.5504;
        final double PC_DLR = 1_000_000.0;
        final double PA_DLR =    10_000.0;

        NozzleDesignParameters dlrParams = NozzleDesignParameters.builder()
                .throatRadius(0.009).exitMach(M_EXIT).chamberPressure(PC_DLR)
                .chamberTemperature(293.0).ambientPressure(PA_DLR)
                .gasProperties(GasProperties.NITROGEN)
                .numberOfCharLines(20).wallAngleInitialDegrees(30.0)
                .lengthFraction(0.8).axisymmetric(false).build();

        DualBellNozzle dlr = new DualBellNozzle(dlrParams, EPS_B,
                0.8, 0.8, Math.toRadians(15.0), 200).generate();

        double gm1      = 0.4;
        double term1    = 9.8 * Math.pow(5.0 / 6.0, 6.0);
        double pKinkR   = Math.pow(1.0 + 0.2 * dlr.getTransitionMach() * dlr.getTransitionMach(), -3.5);
        double pExitR   = Math.pow(1.0 + 0.2 * M_EXIT * M_EXIT, -3.5);
        double exitAR   = GasProperties.NITROGEN.areaRatio(M_EXIT);
        double lambdaB  = (1.0 + Math.cos(dlr.getBaseExitAngle()))      / 2.0;
        double lambdaE  = (1.0 + Math.cos(dlr.getExtensionExitAngle())) / 2.0;
        double cfSlAnal = lambdaB * Math.sqrt(term1 * (1.0 - Math.pow(pKinkR, gm1 / 1.4)))
                          + (pKinkR - PA_DLR / PC_DLR) * EPS_B;
        double cfAltAnal = lambdaE * Math.sqrt(term1 * (1.0 - Math.pow(pExitR, gm1 / 1.4)))
                          + pExitR * exitAR;

        System.out.printf("  M_kink = %.4f  (ε_b = %.1f)   M_exit = %.4f  (ε_e = %.3f)%n",
                dlr.getTransitionMach(), EPS_B, M_EXIT, exitAR);
        System.out.printf("  Sea-level Cf:  model = %.5f   analytical = %.5f   diff = %+.4f%%%n",
                dlr.getSeaLevelCf(), cfSlAnal,
                (dlr.getSeaLevelCf() - cfSlAnal) / cfSlAnal * 100.0);
        System.out.printf("  High-alt  Cf:  model = %.5f   analytical = %.5f   diff = %+.4f%%%n",
                dlr.getHighAltitudeCf(), cfAltAnal,
                (dlr.getHighAltitudeCf() - cfAltAnal) / cfAltAnal * 100.0);

        System.out.println("\nDual-Bell Exports:");
        Path dbDir = outputDir.resolve("DualBell");
        Files.createDirectories(dbDir);

        new CSVExporter().exportDualBellReport(db4, dbDir.resolve("report"));
        System.out.println("  CSV: report/ (design_parameters, contour with section labels, performance)");

        DXFExporter dbDxf = new DXFExporter().setScaleFactor(1000);
        dbDxf.exportDualBellContour(db4, dbDir.resolve("dual_bell.dxf"));
        dbDxf.exportRevolutionProfile(db4, dbDir.resolve("dual_bell_revolution.dxf"));
        System.out.println("  DXF: dual_bell.dxf, dual_bell_revolution.dxf  (KINK layer marks inflection point)");

        STEPExporter dbStep = new STEPExporter();
        dbStep.exportRevolvedSolid(db4, dbDir.resolve("dual_bell.step"));
        dbStep.exportProfileCurve(db4, dbDir.resolve("dual_bell_profile.step"));
        System.out.println("  STEP: dual_bell.step (solid), dual_bell_profile.step (B-spline curve)");

        new STLExporter().setCircumferentialSegments(72).setBinaryFormat(true)
                .exportMesh(db4, dbDir.resolve("dual_bell.stl"));
        System.out.printf("  STL:  dual_bell.stl  (%d triangles, binary)%n",
                new STLExporter().estimateTriangleCount(db4.getContourPoints().size()));

        RevolvedMeshExporter dbRev = new RevolvedMeshExporter().setAxialCells(100).setRadialCells(40);
        dbRev.export(db4, dbDir.resolve("dual_bell_3d_blockMeshDict"), RevolvedMeshExporter.Format.OPENFOAM_BLOCKMESH);
        dbRev.export(db4, dbDir.resolve("dual_bell_3d.geo"),           RevolvedMeshExporter.Format.GMSH_GEO);
        dbRev.export(db4, dbDir.resolve("dual_bell_3d.xyz"),           RevolvedMeshExporter.Format.PLOT3D);
        System.out.println("  3D revolved: dual_bell_3d_blockMeshDict, dual_bell_3d.geo, dual_bell_3d.xyz");
        System.out.printf("%nDual-bell output saved to: %s%n", dbDir.toAbsolutePath());
    }
}
