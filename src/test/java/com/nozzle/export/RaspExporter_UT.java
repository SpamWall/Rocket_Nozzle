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
 *  commercial purposes contact the owner via the github repository.
 */

package com.nozzle.export;

import com.nozzle.solid.RaspImporter;
import com.nozzle.solid.RaspMotorData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("RaspExporter Tests")
class RaspExporter_UT {

    private static final double TOL = 1e-6;

    private static final String SINGLE_ENG = """
            ; AeroTech F52-8
            F52-8 29 124 8 0.068 0.117 AT
               0.000   0.000
               0.100  53.000
               0.500  54.000
               0.800   0.000
            ;
            """;

    private static final String MULTI_ENG = """
            ; Motor 1
            F52-8 29 124 8 0.068 0.117 AT
               0.000   0.000
               0.400  53.000
               0.800   0.000
            ;
            ; Motor 2
            G79-7 29 193 7 0.101 0.167 AT
               0.000   0.000
               0.600  79.000
               1.200   0.000
            ;
            """;

    // -----------------------------------------------------------------------
    // Round-trip — format() output can be re-parsed by RaspImporter
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Round-trip fidelity")
    class RoundTrip {

        @Test
        @DisplayName("format() produces parseable output")
        void formatProducesParseable() {
            RaspMotorData original = RaspImporter.parse(SINGLE_ENG);
            String exported = RaspExporter.format(original);
            assertThatCode(() -> RaspImporter.parse(exported)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Header fields survive a round-trip")
        void headerFieldsRoundTrip() {
            RaspMotorData original = RaspImporter.parse(SINGLE_ENG);
            RaspMotorData roundTripped = RaspImporter.parse(RaspExporter.format(original));

            assertThat(roundTripped.name()).isEqualTo(original.name());
            assertThat(roundTripped.diameterMm()).isCloseTo(original.diameterMm(), within(TOL));
            assertThat(roundTripped.lengthMm()).isCloseTo(original.lengthMm(), within(TOL));
            assertThat(roundTripped.delays()).isEqualTo(original.delays());
            assertThat(roundTripped.propellantMassKg()).isCloseTo(original.propellantMassKg(), within(TOL));
            assertThat(roundTripped.totalMassKg()).isCloseTo(original.totalMassKg(), within(TOL));
            assertThat(roundTripped.manufacturer()).isEqualTo(original.manufacturer());
        }

        @Test
        @DisplayName("Data points survive a round-trip")
        void dataPointsRoundTrip() {
            RaspMotorData original = RaspImporter.parse(SINGLE_ENG);
            RaspMotorData roundTripped = RaspImporter.parse(RaspExporter.format(original));

            assertThat(roundTripped.size()).isEqualTo(original.size());
            for (int i = 0; i < original.size(); i++) {
                assertThat(roundTripped.timeAt(i)).isCloseTo(original.timeAt(i), within(TOL));
                assertThat(roundTripped.thrustAt(i)).isCloseTo(original.thrustAt(i), within(TOL));
            }
        }

        @Test
        @DisplayName("Derived quantities survive a round-trip")
        void derivedQuantitiesRoundTrip() {
            RaspMotorData original = RaspImporter.parse(SINGLE_ENG);
            RaspMotorData roundTripped = RaspImporter.parse(RaspExporter.format(original));

            assertThat(roundTripped.totalImpulseNs()).isCloseTo(original.totalImpulseNs(), within(TOL));
            assertThat(roundTripped.maxThrustN()).isCloseTo(original.maxThrustN(), within(TOL));
            assertThat(roundTripped.burnTime()).isCloseTo(original.burnTime(), within(TOL));
            assertThat(roundTripped.motorClass()).isEqualTo(original.motorClass());
        }

        @Test
        @DisplayName("Multi-motor list survives a round-trip via formatAll()")
        void multiMotorRoundTrip() {
            List<RaspMotorData> originals = RaspImporter.parseAll(MULTI_ENG);
            List<RaspMotorData> roundTripped = RaspImporter.parseAll(
                    RaspExporter.formatAll(originals));

            assertThat(roundTripped).hasSize(originals.size());
            for (int m = 0; m < originals.size(); m++) {
                assertThat(roundTripped.get(m).name()).isEqualTo(originals.get(m).name());
                assertThat(roundTripped.get(m).size()).isEqualTo(originals.get(m).size());
            }
        }

        @Test
        @DisplayName("Plugged delay string (P) round-trips correctly")
        void pluggedDelayRoundTrips() {
            String plugged = """
                    F39-P 24 95 P 0.041 0.069 AT
                       0.000   0.000
                       1.000  39.000
                       2.500   0.000
                    ;
                    """;
            RaspMotorData original = RaspImporter.parse(plugged);
            RaspMotorData roundTripped = RaspImporter.parse(RaspExporter.format(original));
            assertThat(roundTripped.delays()).isEqualTo("P");
        }
    }

    // -----------------------------------------------------------------------
    // Output structure
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Output structure")
    class OutputStructure {

        @Test
        @DisplayName("format() output contains a comment line with the motor name")
        void outputContainsNameComment() {
            RaspMotorData motor = RaspImporter.parse(SINGLE_ENG);
            String out = RaspExporter.format(motor);
            assertThat(out).contains("; F52-8");
        }

        @Test
        @DisplayName("format() output is terminated with a semicolon line")
        void outputTerminatedWithSemicolon() {
            RaspMotorData motor = RaspImporter.parse(SINGLE_ENG);
            String out = RaspExporter.format(motor).stripTrailing();
            assertThat(out).endsWith(";");
        }

        @Test
        @DisplayName("formatAll() output for two motors contains both name comments")
        void formatAllContainsBothNames() {
            List<RaspMotorData> motors = RaspImporter.parseAll(MULTI_ENG);
            String out = RaspExporter.formatAll(motors);
            assertThat(out).contains("; F52-8");
            assertThat(out).contains("; G79-7");
        }

        @Test
        @DisplayName("formatAll() with empty list throws IllegalArgumentException")
        void formatAllEmptyThrows() {
            assertThatThrownBy(() -> RaspExporter.formatAll(List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // -----------------------------------------------------------------------
    // File I/O
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("File I/O")
    class FileIO {

        @Test
        @DisplayName("save() writes a file that RaspImporter.load() can read")
        void saveRoundTripViaFile(@TempDir Path tmp) throws IOException {
            Path file = tmp.resolve("output.eng");
            RaspMotorData original = RaspImporter.parse(SINGLE_ENG);
            RaspExporter.save(original, file);

            assertThat(Files.exists(file)).isTrue();
            RaspMotorData loaded = RaspImporter.load(file);
            assertThat(loaded.name()).isEqualTo(original.name());
            assertThat(loaded.size()).isEqualTo(original.size());
        }

        @Test
        @DisplayName("saveAll() writes a file that RaspImporter.loadAll() can read")
        void saveAllRoundTripViaFile(@TempDir Path tmp) throws IOException {
            Path file = tmp.resolve("multi_output.eng");
            List<RaspMotorData> originals = RaspImporter.parseAll(MULTI_ENG);
            RaspExporter.saveAll(originals, file);

            List<RaspMotorData> loaded = RaspImporter.loadAll(file);
            assertThat(loaded).hasSize(originals.size());
            assertThat(loaded.get(0).name()).isEqualTo(originals.get(0).name());
            assertThat(loaded.get(1).name()).isEqualTo(originals.get(1).name());
        }
    }
}
