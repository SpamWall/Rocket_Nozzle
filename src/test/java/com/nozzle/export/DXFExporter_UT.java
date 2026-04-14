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
import com.nozzle.moc.DualBellNozzle;
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
    @DisplayName("exportDualBellContour Tests")
    class DualBellContourExportTests {

        private DualBellNozzle nozzle;

        @BeforeEach
        void setUpDualBell() {
            nozzle = new DualBellNozzle(params, 2.0).generate();
        }

        @Test
        @DisplayName("Creates the output file with valid DXF structure")
        void createsFileWithValidDxfStructure() throws IOException {
            Path out = tempDir.resolve("dualbell_contour.dxf");
            new DXFExporter().exportDualBellContour(nozzle, out);

            String content = Files.readString(out);
            assertThat(content).contains("SECTION");
            assertThat(content).contains("ENTITIES");
            assertThat(content).contains("EOF");
        }

        @Test
        @DisplayName("Writes a WALL-layer polyline covering all contour points")
        void writesWallPolyline() throws IOException {
            Path out = tempDir.resolve("dualbell_wall.dxf");
            new DXFExporter().exportDualBellContour(nozzle, out);

            String content = Files.readString(out);
            assertThat(content).contains("POLYLINE");
            assertThat(content).contains("WALL");
            // One VERTEX per contour point
            long vertexCount = content.lines().filter("VERTEX"::equals).count();
            assertThat(vertexCount).isEqualTo(nozzle.getContourPoints().size());
        }

        @Test
        @DisplayName("Writes exactly one AXIS-layer LINE (centerline from throat to exit)")
        void writesExactlyOneAxisLine() throws IOException {
            Path out = tempDir.resolve("dualbell_axis.dxf");
            new DXFExporter().exportDualBellContour(nozzle, out);

            String content = Files.readString(out);
            assertThat(content).contains("AXIS");
            // exportDualBellContour: 1 AXIS LINE + 1 KINK LINE = 2 total
            // (compare: exportRevolutionProfile DualBell has 4 LINE entities)
            long lineCount = content.lines().filter("LINE"::equals).count();
            assertThat(lineCount).isEqualTo(2);
        }

        @Test
        @DisplayName("Writes a KINK POINT and KINK LINE at the inflection station")
        void writesKinkAnnotation() throws IOException {
            Path out = tempDir.resolve("dualbell_kink.dxf");
            new DXFExporter().exportDualBellContour(nozzle, out);

            String content = Files.readString(out);
            assertThat(content).contains("KINK");
            assertThat(content).contains("POINT");
        }

        @Test
        @DisplayName("Does not write a BASE or EXTENSION layer (those are CSV-only labels)")
        void doesNotWriteBaseOrExtensionLayers() throws IOException {
            Path out = tempDir.resolve("dualbell_layers.dxf");
            new DXFExporter().exportDualBellContour(nozzle, out);

            String content = Files.readString(out);
            assertThat(content).doesNotContain("BASE");
            assertThat(content).doesNotContain("EXTENSION");
        }

        @Test
        @DisplayName("setScaleFactor changes the coordinate values in the output")
        void setScaleFactorChangesCoordinates() throws IOException {
            Path defaultOut = tempDir.resolve("dualbell_scale_default.dxf");
            Path scaledOut  = tempDir.resolve("dualbell_scale_1.dxf");

            new DXFExporter()                    .exportDualBellContour(nozzle, defaultOut);
            new DXFExporter().setScaleFactor(1.0).exportDualBellContour(nozzle, scaledOut);

            assertThat(Files.readString(defaultOut))
                    .isNotEqualTo(Files.readString(scaledOut));
        }

        @Test
        @DisplayName("exportDualBellContour has fewer LINE entities than exportRevolutionProfile")
        void fewerLinesThanRevolutionProfile() throws IOException {
            Path contourOut    = tempDir.resolve("dualbell_c.dxf");
            Path revolutionOut = tempDir.resolve("dualbell_r.dxf");

            new DXFExporter().exportDualBellContour(nozzle, contourOut);
            new DXFExporter().exportRevolutionProfile(nozzle, revolutionOut);

            long contourLines    = Files.readString(contourOut).lines()
                    .filter("LINE"::equals).count();
            long revolutionLines = Files.readString(revolutionOut).lines()
                    .filter("LINE"::equals).count();

            // contour: 2 (1 AXIS + 1 KINK); revolution: 4 (3 AXIS closing + 1 KINK)
            assertThat(contourLines).isLessThan(revolutionLines);
        }
    }

    @Nested
    @DisplayName("exportRevolutionProfile Tests")
    class RevolutionProfileExportTests {

        // ---- NozzleContour overload ----

        @Test
        @DisplayName("exportRevolutionProfile(NozzleContour) writes valid DXF structure")
        void contourOverloadWritesValidDxfStructure() throws IOException {
            Path out = tempDir.resolve("rev_valid.dxf");
            new DXFExporter().exportRevolutionProfile(contour, out);

            String content = Files.readString(out);
            assertThat(content).contains("SECTION");
            assertThat(content).contains("ENTITIES");
            assertThat(content).contains("EOF");
        }

        @Test
        @DisplayName("exportRevolutionProfile(NozzleContour) writes a WALL-layer polyline")
        void contourOverloadWritesWallPolyline() throws IOException {
            Path out = tempDir.resolve("rev_wall.dxf");
            new DXFExporter().exportRevolutionProfile(contour, out);

            String content = Files.readString(out);
            assertThat(content).contains("POLYLINE");
            assertThat(content).contains("WALL");
        }

        @Test
        @DisplayName("exportRevolutionProfile(NozzleContour) writes three AXIS-layer closing segments")
        void contourOverloadWritesAxisLines() throws IOException {
            Path out = tempDir.resolve("rev_axis.dxf");
            new DXFExporter().exportRevolutionProfile(contour, out);

            String content = Files.readString(out);
            assertThat(content).contains("LINE");
            assertThat(content).contains("AXIS");
            // Exactly three LINE entities close the profile (exit face, axis floor, throat face)
            long lineCount = content.lines()
                    .filter(l -> l.trim().equals("LINE"))
                    .count();
            assertThat(lineCount).isEqualTo(3);
        }

        @Test
        @DisplayName("exportRevolutionProfile(NozzleContour) does not write a KINK layer")
        void contourOverloadHasNoKinkLayer() throws IOException {
            Path out = tempDir.resolve("rev_nokink.dxf");
            new DXFExporter().exportRevolutionProfile(contour, out);

            assertThat(Files.readString(out)).doesNotContain("KINK");
        }

        @Test
        @DisplayName("exportRevolutionProfile(NozzleContour) setScaleFactor changes output coordinates")
        void contourOverloadScaleFactorChangesOutput() throws IOException {
            Path defaultOut = tempDir.resolve("rev_scale_default.dxf");
            Path scaledOut  = tempDir.resolve("rev_scale_1.dxf");

            new DXFExporter()                      .exportRevolutionProfile(contour, defaultOut);
            new DXFExporter().setScaleFactor(1.0)  .exportRevolutionProfile(contour, scaledOut);

            assertThat(Files.readString(defaultOut))
                    .isNotEqualTo(Files.readString(scaledOut));
        }

        // ---- DualBellNozzle overload ----

        @Test
        @DisplayName("exportRevolutionProfile(DualBellNozzle) writes valid DXF structure")
        void dualBellOverloadWritesValidDxfStructure() throws IOException {
            DualBellNozzle nozzle = new DualBellNozzle(params, 2.0).generate();
            Path out = tempDir.resolve("rev_dualbell_valid.dxf");
            new DXFExporter().exportRevolutionProfile(nozzle, out);

            String content = Files.readString(out);
            assertThat(content).contains("SECTION");
            assertThat(content).contains("ENTITIES");
            assertThat(content).contains("EOF");
        }

        @Test
        @DisplayName("exportRevolutionProfile(DualBellNozzle) writes WALL and AXIS layers")
        void dualBellOverloadWritesWallAndAxisLayers() throws IOException {
            DualBellNozzle nozzle = new DualBellNozzle(params, 2.0).generate();
            Path out = tempDir.resolve("rev_dualbell_layers.dxf");
            new DXFExporter().exportRevolutionProfile(nozzle, out);

            String content = Files.readString(out);
            assertThat(content).contains("WALL");
            assertThat(content).contains("AXIS");
        }

        @Test
        @DisplayName("exportRevolutionProfile(DualBellNozzle) writes a KINK point and line")
        void dualBellOverloadWritesKinkAnnotation() throws IOException {
            DualBellNozzle nozzle = new DualBellNozzle(params, 2.0).generate();
            Path out = tempDir.resolve("rev_dualbell_kink.dxf");
            new DXFExporter().exportRevolutionProfile(nozzle, out);

            String content = Files.readString(out);
            assertThat(content).contains("KINK");
            assertThat(content).contains("POINT");
        }

        @Test
        @DisplayName("exportRevolutionProfile(DualBellNozzle) output differs from NozzleContour overload")
        void dualBellOutputDiffersFromContourOutput() throws IOException {
            DualBellNozzle nozzle = new DualBellNozzle(params, 2.0).generate();
            Path contourOut  = tempDir.resolve("rev_compare_contour.dxf");
            Path dualBellOut = tempDir.resolve("rev_compare_dualbell.dxf");

            new DXFExporter().exportRevolutionProfile(contour, contourOut);
            new DXFExporter().exportRevolutionProfile(nozzle, dualBellOut);

            // DualBell output includes KINK layer; contour output does not
            assertThat(Files.readString(dualBellOut)).contains("KINK");
            assertThat(Files.readString(contourOut)).doesNotContain("KINK");
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
