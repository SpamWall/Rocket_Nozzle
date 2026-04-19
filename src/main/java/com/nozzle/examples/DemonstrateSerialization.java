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
import com.nozzle.geometry.NozzleContour;
import com.nozzle.io.DesignDocument;
import com.nozzle.io.NozzleSerializer;
import com.nozzle.moc.CharacteristicNet;
import com.nozzle.validation.NASASP8120Validator;

import java.nio.file.Files;
import java.nio.file.Path;

/** Demonstrates a three-stage persistent workflow using NozzleSerializer. */
public class DemonstrateSerialization {

    public static void main(String[] args) throws Exception {
        Path outputDir = Path.of("nozzle_output");
        Files.createDirectories(outputDir);
        System.out.println("\n--- JSON SERIALIZATION (PERSISTENT WORKFLOW) ---\n");

        Path checkpoint = outputDir.resolve("workflow_design.json");

        System.out.println("  Stage 1: Capture design intent");
        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05).exitMach(3.5).chamberPressure(7e6)
                .chamberTemperature(3500).ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20).wallAngleInitialDegrees(30)
                .lengthFraction(0.8).build();

        NozzleSerializer.save(NozzleSerializer.document(params), checkpoint);
        System.out.printf("    Parameters saved to:    %s%n", checkpoint.getFileName());
        System.out.printf("    Throat radius:          %.1f mm%n", params.throatRadius() * 1000);
        System.out.printf("    Exit Mach:              %.2f%n", params.exitMach());
        System.out.printf("    Chamber pressure:       %.1f MPa%n", params.chamberPressure() / 1e6);

        System.out.println("\n  Stage 2: Run MOC solver and save full design");
        DesignDocument intent = NozzleSerializer.load(checkpoint);
        NozzleDesignParameters solverParams = intent.parameters();

        long t0 = System.currentTimeMillis();
        CharacteristicNet net = new CharacteristicNet(solverParams).generate();
        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, solverParams);
        contour.generate(200);
        long elapsed = System.currentTimeMillis() - t0;

        NozzleSerializer.save(NozzleSerializer.document(solverParams, net, contour), checkpoint);
        System.out.printf("    Solver completed in:    %d ms%n", elapsed);
        System.out.printf("    MOC wall points:        %d%n", net.getWallPoints().size());
        System.out.printf("    MOC net rows:           %d%n", net.getNetPoints().size());
        System.out.printf("    Contour points:         %d%n", contour.getContourPoints().size());
        System.out.printf("    Full design saved to:   %s%n", checkpoint.getFileName());

        System.out.println("\n  Stage 3: Reload checkpoint and run downstream analysis");
        DesignDocument full = NozzleSerializer.load(checkpoint);
        NozzleDesignParameters rp = full.parameters();

        System.out.printf("    Schema version:         %s%n", full.version());
        System.out.printf("    Created at:             %s%n", full.createdAt());
        System.out.printf("    Wall points restored:   %d%n", full.wallPoints().size());
        System.out.printf("    Contour points restored:%d%n", full.contourPoints().size());

        PerformanceCalculator perf = PerformanceCalculator.simple(rp).calculate();
        System.out.printf("%n    Performance (from restored parameters):%n");
        System.out.printf("      Thrust coefficient:   %.4f%n", perf.getActualThrustCoefficient());
        System.out.printf("      Specific impulse:     %.1f s%n", perf.getSpecificImpulse());
        System.out.printf("      Thrust:               %.2f kN%n", perf.getThrust() / 1000);
        System.out.printf("      Efficiency:           %.2f%%%n", perf.getEfficiency() * 100);

        NASASP8120Validator.ValidationResult validation = new NASASP8120Validator().validate(rp);
        System.out.printf("%n    NASA SP-8120 validation (from restored parameters):%n");
        System.out.printf("      Overall status:       %s%n", validation.isValid() ? "PASS" : "FAIL");
        System.out.printf("      Warnings:             %d%n", validation.warnings().size());

        boolean paramsMatch =
                Math.abs(rp.throatRadius()       - params.throatRadius())       < 1e-12 &&
                Math.abs(rp.exitMach()           - params.exitMach())           < 1e-12 &&
                Math.abs(rp.chamberPressure()    - params.chamberPressure())    < 1e-12 &&
                Math.abs(rp.chamberTemperature() - params.chamberTemperature()) < 1e-12 &&
                rp.numberOfCharLines() == params.numberOfCharLines();
        System.out.printf("%n    Parameter round-trip exact: %b%n", paramsMatch);
    }
}
