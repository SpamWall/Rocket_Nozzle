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
import com.nozzle.moc.CharacteristicNet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DXFExporter Tests")
class DXFExporter_UT {

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
        @DisplayName("Should export contour to DXF")
        void shouldExportContourToDXF() throws IOException {
            Path out = tempDir.resolve("nozzle.dxf");
            new DXFExporter().exportContour(contour, out);

            assertThat(out).exists();
            String content = Files.readString(out);
            assertThat(content).contains("SECTION");
            assertThat(content).contains("ENTITIES");
            assertThat(content).contains("EOF");
        }

        @Test
        @DisplayName("Should export revolution profile to DXF")
        void shouldExportRevolutionProfileToDXF() throws IOException {
            Path out = tempDir.resolve("revolution.dxf");
            new DXFExporter().exportRevolutionProfile(contour, out);

            assertThat(out).exists();
        }

        @Test
        @DisplayName("Should respect scale factor")
        void shouldRespectScaleFactor() throws IOException {
            Path out = tempDir.resolve("scaled.dxf");
            new DXFExporter().setScaleFactor(1000).exportContour(contour, out);

            assertThat(out).exists();
        }

        @Test
        @DisplayName("exportContour should throw when contour has no points")
        void exportContourThrowsOnEmptyContour() {
            NozzleContour empty = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
            Path out = tempDir.resolve("empty.dxf");
            assertThatThrownBy(() -> new DXFExporter().exportContour(empty, out))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("exportRevolutionProfile should throw when contour has no points")
        void exportRevolutionProfileThrowsOnEmptyContour() {
            NozzleContour empty = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
            Path out = tempDir.resolve("empty_rev.dxf");
            assertThatThrownBy(() -> new DXFExporter().exportRevolutionProfile(empty, out))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("exportCharacteristicNet should write POLYLINE when net is generated")
        void exportCharacteristicNetWithGeneratedNet() throws IOException {
            CharacteristicNet net = new CharacteristicNet(params).generate();
            Path out = tempDir.resolve("net.dxf");
            new DXFExporter().exportCharacteristicNet(net, out);

            assertThat(out).exists();
            String content = Files.readString(out);
            assertThat(content).contains("POLYLINE");
            assertThat(content).contains("VERTEX");
            assertThat(content).contains("EOF");
        }

        @Test
        @DisplayName("exportCharacteristicNet should write axis line only when net is ungenerated")
        void exportCharacteristicNetWithEmptyNet() throws IOException {
            // Ungenerated net → empty wall-points list → xMax falls back to 1.0
            CharacteristicNet emptyNet = new CharacteristicNet(params);
            Path out = tempDir.resolve("empty_net.dxf");
            new DXFExporter().exportCharacteristicNet(emptyNet, out);

            assertThat(out).exists();
            String content = Files.readString(out);
            assertThat(content).contains("LINE");
            assertThat(content).doesNotContain("POLYLINE");
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
        @DisplayName("exportAerospikeContour should produce valid DXF with SPIKE, COWL, AXIS layers")
        void exportAerospikeContourProducesValidDXF() throws IOException {
            Path out = tempDir.resolve("aerospike.dxf");
            new DXFExporter().exportAerospikeContour(nozzle, out);

            assertThat(out).exists();
            String content = Files.readString(out);
            assertThat(content).contains("SECTION");
            assertThat(content).contains("ENTITIES");
            assertThat(content).contains("SPIKE");
            assertThat(content).contains("COWL");
            assertThat(content).contains("AXIS");
            assertThat(content).contains("EOF");
        }
    }
}
