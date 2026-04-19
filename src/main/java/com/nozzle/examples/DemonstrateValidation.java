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
import com.nozzle.validation.NASASP8120Validator;

/** Demonstrates NASA SP-8120 validation. */
public class DemonstrateValidation {

    public static void main(String[] ignoredArgs) {
        System.out.println("\n--- NASA SP-8120 VALIDATION ---\n");

        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(25)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();

        NASASP8120Validator validator = new NASASP8120Validator();
        NASASP8120Validator.ValidationResult result = validator.validate(params);

        System.out.printf("Validation Result: %s%n", result.isValid() ? "PASSED" : "FAILED");

        if (!result.errors().isEmpty()) {
            System.out.println("Errors:");
            result.errors().forEach(e -> System.out.printf("  - %s%n", e));
        }

        if (!result.warnings().isEmpty()) {
            System.out.println("Warnings:");
            result.warnings().forEach(w -> System.out.printf("  - %s%n", w));
        }

        System.out.println("Key Metrics:");
        result.metrics().forEach((key, value) ->
                System.out.printf("  %s: %.4f%n", key, value));
    }
}
