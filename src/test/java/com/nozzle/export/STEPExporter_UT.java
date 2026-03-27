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

package com.nozzle.export;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.moc.AerospikeNozzle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

@DisplayName("STEPExporter Tests")
class STEPExporter_UT {

    private NozzleDesignParameters params;
    private NozzleContour contour;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(2.5)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.AIR)
                .numberOfCharLines(10)
                .wallAngleInitialDegrees(25)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();

        contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        contour.generate(50);
    }

    @Nested
    @DisplayName("Bell Nozzle Export Tests")
    class BellNozzleExportTests {

        @Test
        @DisplayName("Should export to STEP format")
        void shouldExportToSTEP() throws IOException {
            Path out = tempDir.resolve("nozzle.step");
            new STEPExporter().exportRevolvedSolid(contour, out);

            assertThat(out).exists();
            String content = Files.readString(out);
            assertThat(content).contains("ISO-10303-21");
            assertThat(content).contains("HEADER");
            assertThat(content).contains("DATA");
        }

        @Test
        @DisplayName("Should export profile curve to STEP")
        void shouldExportProfileCurveToSTEP() throws IOException {
            Path out = tempDir.resolve("profile.step");
            new STEPExporter().exportProfileCurve(contour, out);

            assertThat(out).exists();
        }

        @Test
        @DisplayName("exportRevolvedSolid should throw when contour has no points")
        void exportRevolvedSolidThrowsOnEmptyContour() {
            NozzleContour empty = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
            Path out = tempDir.resolve("empty.step");
            assertThatThrownBy(() -> new STEPExporter().exportRevolvedSolid(empty, out))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("exportProfileCurve should throw when contour has no points")
        void exportProfileCurveThrowsOnEmptyContour() {
            NozzleContour empty = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
            Path out = tempDir.resolve("empty_profile.step");
            assertThatThrownBy(() -> new STEPExporter().exportProfileCurve(empty, out))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("setAuthor and setOrganization should appear in STEP file header")
        void setAuthorAndOrganizationAppearsInHeader() throws IOException {
            Path out = tempDir.resolve("authored.step");
            new STEPExporter()
                    .setAuthor("Test Author")
                    .setOrganization("Test Org")
                    .exportRevolvedSolid(contour, out);

            String content = Files.readString(out);
            assertThat(content).contains("Test Author");
            assertThat(content).contains("Test Org");
        }

        @Test
        @DisplayName("setScaleFactor should scale coordinates in output")
        void setScaleFactorScalesCoordinates() throws IOException {
            Path outDefault = tempDir.resolve("scale_default.step");
            Path outScaled  = tempDir.resolve("scale_1.step");

            new STEPExporter().exportRevolvedSolid(contour, outDefault);
            new STEPExporter().setScaleFactor(1.0).exportRevolvedSolid(contour, outScaled);

            // With scale=1000 (default) coordinates are ~1000× larger than scale=1.0
            assertThat(Files.readString(outDefault)).isNotEqualTo(Files.readString(outScaled));
        }
    }

    @Nested
    @DisplayName("Aerospike Export Tests")
    class AerospikeExportTests {

        private AerospikeNozzle nozzle;

        @BeforeEach
        void setUpAerospike() {
            nozzle = new AerospikeNozzle(params).generate();
        }

        @Test
        @DisplayName("exportAerospikeRevolvedSolid should produce valid ISO-10303-21 file")
        void exportAerospikeRevolvedSolidProducesValidSTEP() throws IOException {
            Path out = tempDir.resolve("aerospike.step");
            new STEPExporter().exportAerospikeRevolvedSolid(nozzle, out);

            assertThat(out).exists();
            String content = Files.readString(out);
            assertThat(content).contains("ISO-10303-21");
            assertThat(content).contains("HEADER");
            assertThat(content).contains("DATA");
            assertThat(content).contains("SURFACE_OF_REVOLUTION");
            assertThat(content).contains("END-ISO-10303-21");
        }
    }
}
