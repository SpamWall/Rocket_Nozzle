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

package com.nozzle.solid;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("RaspImporter Tests")
class RaspImporter_UT {

    // Minimal valid single-motor .eng content used across most tests
    private static final String SINGLE_MOTOR = """
            ; AeroTech F52-8
            F52-8 29 124 8 0.068 0.117 AT
               0.000   0.000
               0.100  53.000
               0.500  54.000
               0.800   0.000
            ;
            """;

    private static final String MULTI_MOTOR = """
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
    // Parsing — happy path
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Parsing — happy path")
    class HappyPath {

        @Test
        @DisplayName("parse() returns a non-null RaspMotorData")
        void parseReturnsNonNull() {
            assertThat(RaspImporter.parse(SINGLE_MOTOR)).isNotNull();
        }

        @Test
        @DisplayName("Header fields are parsed correctly")
        void headerFieldsParsed() {
            RaspMotorData m = RaspImporter.parse(SINGLE_MOTOR);
            assertThat(m.name()).isEqualTo("F52-8");
            assertThat(m.diameterMm()).isCloseTo(29.0, within(1e-9));
            assertThat(m.lengthMm()).isCloseTo(124.0, within(1e-9));
            assertThat(m.delays()).isEqualTo("8");
            assertThat(m.propellantMassKg()).isCloseTo(0.068, within(1e-9));
            assertThat(m.totalMassKg()).isCloseTo(0.117, within(1e-9));
            assertThat(m.manufacturer()).isEqualTo("AT");
        }

        @Test
        @DisplayName("Data points are parsed in order")
        void dataPointsParsedInOrder() {
            RaspMotorData m = RaspImporter.parse(SINGLE_MOTOR);
            assertThat(m.size()).isEqualTo(4);
            assertThat(m.timeAt(0)).isCloseTo(0.000, within(1e-9));
            assertThat(m.thrustAt(0)).isCloseTo(0.000, within(1e-9));
            assertThat(m.timeAt(1)).isCloseTo(0.100, within(1e-9));
            assertThat(m.thrustAt(1)).isCloseTo(53.000, within(1e-9));
            assertThat(m.timeAt(3)).isCloseTo(0.800, within(1e-9));
            assertThat(m.thrustAt(3)).isCloseTo(0.000, within(1e-9));
        }

        @Test
        @DisplayName("parseAll() returns both motors from a two-motor file")
        void parseAllReturnsBothMotors() {
            List<RaspMotorData> motors = RaspImporter.parseAll(MULTI_MOTOR);
            assertThat(motors).hasSize(2);
            assertThat(motors.get(0).name()).isEqualTo("F52-8");
            assertThat(motors.get(1).name()).isEqualTo("G79-7");
        }

        @Test
        @DisplayName("Second motor data is independent of first motor")
        void secondMotorDataIndependent() {
            List<RaspMotorData> motors = RaspImporter.parseAll(MULTI_MOTOR);
            RaspMotorData g = motors.get(1);
            assertThat(g.propellantMassKg()).isCloseTo(0.101, within(1e-9));
            assertThat(g.thrustAt(1)).isCloseTo(79.000, within(1e-9));
        }

        @Test
        @DisplayName("Comments and blank lines before and after the motor are ignored")
        void commentsAndBlanksIgnored() {
            String withComments = """
                    ; preamble comment

                    ; another comment
                    F52-8 29 124 8 0.068 0.117 AT
                       0.000   0.000
                       0.400  53.000
                       0.800   0.000
                    ; trailing comment
                    """;
            RaspMotorData m = RaspImporter.parse(withComments);
            assertThat(m.name()).isEqualTo("F52-8");
            assertThat(m.size()).isEqualTo(3);
        }

        @Test
        @DisplayName("Motor with no trailing semicolon is accepted (EOF terminator)")
        void noTrailingSemicolon() {
            String noTerminator = """
                    H128-14 38 279 14 0.187 0.369 AT
                       0.000    0.000
                       0.050  145.000
                       1.460    0.000
                    """;
            RaspMotorData m = RaspImporter.parse(noTerminator);
            assertThat(m.size()).isEqualTo(3);
        }

        @Test
        @DisplayName("Plugged-delay field (P) is accepted")
        void pluggedDelayAccepted() {
            String plugged = """
                    F39-P 24 95 P 0.041 0.069 AT
                       0.000   0.000
                       1.000  39.000
                       2.500   0.000
                    ;
                    """;
            assertThatCode(() -> RaspImporter.parse(plugged)).doesNotThrowAnyException();
            assertThat(RaspImporter.parse(plugged).delays()).isEqualTo("P");
        }
    }

    // -----------------------------------------------------------------------
    // File I/O
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("File I/O")
    class FileIO {

        @Test
        @DisplayName("load(Path) reads a single motor from a file on disk")
        void loadFromFile(@TempDir Path tmp) throws IOException {
            Path file = tmp.resolve("test.eng");
            Files.writeString(file, SINGLE_MOTOR);
            RaspMotorData m = RaspImporter.load(file);
            assertThat(m.name()).isEqualTo("F52-8");
        }

        @Test
        @DisplayName("loadAll(Path) reads multiple motors from a file on disk")
        void loadAllFromFile(@TempDir Path tmp) throws IOException {
            Path file = tmp.resolve("multi.eng");
            Files.writeString(file, MULTI_MOTOR);
            assertThat(RaspImporter.loadAll(file)).hasSize(2);
        }
    }

    // -----------------------------------------------------------------------
    // Error handling
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("Empty content returns empty list from parseAll()")
        void emptyContentReturnsEmptyList() {
            assertThat(RaspImporter.parseAll("")).isEmpty();
            assertThat(RaspImporter.parseAll("; just a comment\n")).isEmpty();
        }

        @Test
        @DisplayName("parse() on empty content throws IllegalArgumentException")
        void emptyContentThrowsOnParse() {
            assertThatThrownBy(() -> RaspImporter.parse("; only comment\n"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Header with fewer than 7 fields throws IllegalArgumentException")
        void shortHeaderThrows() {
            String bad = "F52-8 29 124 8 0.068\n 0.0 0.0\n;\n";
            assertThatThrownBy(() -> RaspImporter.parse(bad))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("7 fields");
        }

        @Test
        @DisplayName("Non-numeric diameter throws IllegalArgumentException")
        void badNumberThrows() {
            String bad = "F52-8 XX 124 8 0.068 0.117 AT\n 0.0 0.0\n;\n";
            assertThatThrownBy(() -> RaspImporter.parse(bad))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("diameter");
        }

        @Test
        @DisplayName("Motor with no data points throws IllegalArgumentException")
        void noDataPointsThrows() {
            String bad = "F52-8 29 124 8 0.068 0.117 AT\n;\n";
            assertThatThrownBy(() -> RaspImporter.parse(bad))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no data points");
        }
    }
}
