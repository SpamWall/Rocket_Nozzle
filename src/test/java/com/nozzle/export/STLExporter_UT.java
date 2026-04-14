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
import com.nozzle.geometry.FullNozzleGeometry;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.moc.AerospikeNozzle;
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

@DisplayName("STLExporter Tests")
class STLExporter_UT {

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
        @DisplayName("Should export to binary STL")
        void shouldExportToBinarySTL() throws IOException {
            Path out = tempDir.resolve("nozzle.stl");
            new STLExporter().setCircumferentialSegments(36).setBinaryFormat(true)
                    .exportMesh(contour, out);

            assertThat(out).exists();
            assertThat(Files.size(out)).isGreaterThan(80);
        }

        @Test
        @DisplayName("Should export to ASCII STL")
        void shouldExportToAsciiSTL() throws IOException {
            Path out = tempDir.resolve("nozzle_ascii.stl");
            new STLExporter().setCircumferentialSegments(18).setBinaryFormat(false)
                    .exportMesh(contour, out);

            assertThat(out).exists();
            String content = Files.readString(out);
            assertThat(content).contains("solid");
            assertThat(content).contains("facet normal");
            assertThat(content).contains("endsolid");
        }

        @Test
        @DisplayName("Should estimate triangle count")
        void shouldEstimateTriangleCount() {
            int count = new STLExporter().setCircumferentialSegments(36).estimateTriangleCount(50);
            assertThat(count).isGreaterThan(0);
        }

        @Test
        @DisplayName("setScaleFactor should change coordinate values in output")
        void setScaleFactorChangesCoordinates() throws IOException {
            Path outDefault = tempDir.resolve("scale_default.stl");
            Path outOne     = tempDir.resolve("scale_1.stl");

            new STLExporter().setCircumferentialSegments(6).setBinaryFormat(false)
                    .exportMesh(contour, outDefault);
            new STLExporter().setCircumferentialSegments(6).setBinaryFormat(false)
                    .setScaleFactor(1.0).exportMesh(contour, outOne);

            assertThat(Files.readString(outDefault)).isNotEqualTo(Files.readString(outOne));
        }

        @Test
        @DisplayName("exportMesh should throw when contour has fewer than two points")
        void exportMeshThrowsOnEmptyContour() {
            NozzleContour empty = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
            Path out = tempDir.resolve("empty.stl");
            assertThatThrownBy(() -> new STLExporter().exportMesh(empty, out))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Contour with on-axis profile points skips both end caps and produces degenerate normals")
        void tinyThroatCoversNoCapsAndDegenerateNormal() throws IOException {
            // throatRadius = 1e-8 m → firstPoint.y() ≈ 1e-8 < 1e-6 (no start cap)
            // exitRadius   ≈ 1.6e-8 m                              (no end cap)
            // With default scaleFactor=1000, all edges are ~1e-5 mm → cross products
            // are ~1e-10 or smaller → len ≤ 1e-10 (degenerate normal branch).
            NozzleDesignParameters tinyParams = NozzleDesignParameters.builder()
                    .throatRadius(1e-8)
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
            NozzleContour tinyContour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, tinyParams);
            tinyContour.generate(10);

            Path out = tempDir.resolve("tiny.stl");
            new STLExporter().setCircumferentialSegments(6).setBinaryFormat(false)
                    .exportMesh(tinyContour, out);

            assertThat(out).exists();
            assertThat(Files.readString(out)).contains("solid").contains("endsolid");
        }
    }

    @Nested
    @DisplayName("exportMesh(NozzleContour) — deep format tests")
    class ExportMeshContourTests {

        @Test
        @DisplayName("Binary file size equals 84 + triangleCount × 50 bytes")
        void binaryFileSizeMatchesFormula() throws IOException {
            int segments = 36;
            Path out = tempDir.resolve("mesh_size.stl");
            new STLExporter().setCircumferentialSegments(segments).setBinaryFormat(true)
                    .exportMesh(contour, out);

            // estimateTriangleCount includes both end caps (both endpoints have r > 1µm)
            int expected = new STLExporter().setCircumferentialSegments(segments)
                    .estimateTriangleCount(contour.getContourPoints().size());
            assertThat(Files.size(out)).isEqualTo(84L + (long) expected * 50);
        }

        @Test
        @DisplayName("ASCII facet count equals estimateTriangleCount")
        void asciiFacetCountMatchesEstimate() throws IOException {
            int segments = 24;
            Path out = tempDir.resolve("mesh_facets.stl");
            new STLExporter().setCircumferentialSegments(segments).setBinaryFormat(false)
                    .exportMesh(contour, out);

            int expected = new STLExporter().setCircumferentialSegments(segments)
                    .estimateTriangleCount(contour.getContourPoints().size());
            long actual = Files.readString(out).lines()
                    .filter(l -> l.contains("facet normal")).count();
            assertThat(actual).isEqualTo(expected);
        }

        @Test
        @DisplayName("More circumferential segments produce a larger file")
        void moreSegmentsProduceLargerFile() throws IOException {
            Path coarse = tempDir.resolve("mesh_coarse.stl");
            Path fine   = tempDir.resolve("mesh_fine.stl");

            new STLExporter().setCircumferentialSegments(12).setBinaryFormat(true)
                    .exportMesh(contour, coarse);
            new STLExporter().setCircumferentialSegments(48).setBinaryFormat(true)
                    .exportMesh(contour, fine);

            assertThat(Files.size(fine)).isGreaterThan(Files.size(coarse));
        }

        @Test
        @DisplayName("ASCII output starts with 'solid nozzle' and ends with 'endsolid nozzle'")
        void asciiHasCorrectSolidDelimiters() throws IOException {
            Path out = tempDir.resolve("mesh_delimiters.stl");
            new STLExporter().setBinaryFormat(false).exportMesh(contour, out);

            String content = Files.readString(out);
            assertThat(content).startsWith("solid nozzle");
            assertThat(content.stripTrailing()).endsWith("endsolid nozzle");
        }

        @Test
        @DisplayName("ASCII each facet has outer loop with exactly three vertex lines")
        void asciiEachFacetHasThreeVertices() throws IOException {
            Path out = tempDir.resolve("mesh_vertices.stl");
            new STLExporter().setCircumferentialSegments(6).setBinaryFormat(false)
                    .exportMesh(contour, out);

            String content = Files.readString(out);
            long facets   = content.lines().filter(l -> l.contains("facet normal")).count();
            long vertices = content.lines().filter(l -> l.trim().startsWith("vertex")).count();
            assertThat(vertices).isEqualTo(facets * 3);
        }
    }

    @Nested
    @DisplayName("exportMesh(FullNozzleGeometry) Tests")
    class ExportMeshFullGeometryTests {

        private FullNozzleGeometry fullGeometry;

        @BeforeEach
        void setUpFullGeometry() {
            fullGeometry = new FullNozzleGeometry(params).generate();
        }

        @Test
        @DisplayName("Binary produces a non-trivial file")
        void binaryProducesNonTrivialFile() throws IOException {
            Path out = tempDir.resolve("full_mesh.stl");
            new STLExporter().setCircumferentialSegments(12).setBinaryFormat(true)
                    .exportMesh(fullGeometry, out);

            // At minimum: 80-byte header + 4-byte count + at least one triangle record
            assertThat(Files.size(out)).isGreaterThan(84L);
        }

        @Test
        @DisplayName("ASCII has correct STL structure")
        void asciiHasCorrectStructure() throws IOException {
            Path out = tempDir.resolve("full_mesh_ascii.stl");
            new STLExporter().setCircumferentialSegments(12).setBinaryFormat(false)
                    .exportMesh(fullGeometry, out);

            String content = Files.readString(out);
            assertThat(content).contains("solid nozzle");
            assertThat(content).contains("facet normal");
            assertThat(content).contains("outer loop");
            assertThat(content).contains("endsolid nozzle");
        }

        @Test
        @DisplayName("Throws IllegalStateException when FullNozzleGeometry has not been generated")
        void throwsWhenNotGenerated() {
            FullNozzleGeometry ungenerated = new FullNozzleGeometry(params);
            Path out = tempDir.resolve("full_mesh_empty.stl");
            assertThatThrownBy(() -> new STLExporter().exportMesh(ungenerated, out))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Produces more triangles than the divergent-only contour")
        void moreTrianglesThanDivergentOnly() throws IOException {
            int segments = 12;
            Path fullOut      = tempDir.resolve("full_mesh_count.stl");
            Path divergentOut = tempDir.resolve("div_mesh_count.stl");

            new STLExporter().setCircumferentialSegments(segments).setBinaryFormat(true)
                    .exportMesh(fullGeometry, fullOut);
            new STLExporter().setCircumferentialSegments(segments).setBinaryFormat(true)
                    .exportMesh(contour, divergentOut);

            assertThat(Files.size(fullOut)).isGreaterThan(Files.size(divergentOut));
        }

        @Test
        @DisplayName("ASCII facet count equals estimateTriangleCount for the full wall")
        void facetCountMatchesEstimate() throws IOException {
            int segments = 18;
            Path out = tempDir.resolve("full_mesh_facets.stl");
            new STLExporter().setCircumferentialSegments(segments).setBinaryFormat(false)
                    .exportMesh(fullGeometry, out);

            int expected = new STLExporter().setCircumferentialSegments(segments)
                    .estimateTriangleCount(fullGeometry.getWallPoints().size());
            long actual = Files.readString(out).lines()
                    .filter(l -> l.contains("facet normal")).count();
            assertThat(actual).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("exportMesh(DualBellNozzle) Tests")
    class ExportMeshDualBellTests {

        private DualBellNozzle nozzle;

        @BeforeEach
        void setUpDualBell() {
            nozzle = new DualBellNozzle(params, 2.0).generate();
        }

        @Test
        @DisplayName("Binary produces a non-trivial file")
        void binaryProducesNonTrivialFile() throws IOException {
            Path out = tempDir.resolve("dualbell_mesh.stl");
            new STLExporter().setCircumferentialSegments(12).setBinaryFormat(true)
                    .exportMesh(nozzle, out);

            assertThat(Files.size(out)).isGreaterThan(84L);
        }

        @Test
        @DisplayName("ASCII has correct STL structure")
        void asciiHasCorrectStructure() throws IOException {
            Path out = tempDir.resolve("dualbell_mesh_ascii.stl");
            new STLExporter().setCircumferentialSegments(12).setBinaryFormat(false)
                    .exportMesh(nozzle, out);

            String content = Files.readString(out);
            assertThat(content).contains("solid nozzle");
            assertThat(content).contains("facet normal");
            assertThat(content).contains("endsolid nozzle");
        }

        @Test
        @DisplayName("ASCII facet count equals estimateTriangleCount for the dual-bell contour")
        void facetCountMatchesEstimate() throws IOException {
            int segments = 18;
            Path out = tempDir.resolve("dualbell_mesh_facets.stl");
            new STLExporter().setCircumferentialSegments(segments).setBinaryFormat(false)
                    .exportMesh(nozzle, out);

            int expected = new STLExporter().setCircumferentialSegments(segments)
                    .estimateTriangleCount(nozzle.getContourPoints().size());
            long actual = Files.readString(out).lines()
                    .filter(l -> l.contains("facet normal")).count();
            assertThat(actual).isEqualTo(expected);
        }

        @Test
        @DisplayName("More circumferential segments produce a larger file")
        void moreSegmentsProduceLargerFile() throws IOException {
            Path coarse = tempDir.resolve("dualbell_coarse.stl");
            Path fine   = tempDir.resolve("dualbell_fine.stl");

            new STLExporter().setCircumferentialSegments(12).setBinaryFormat(true)
                    .exportMesh(nozzle, coarse);
            new STLExporter().setCircumferentialSegments(48).setBinaryFormat(true)
                    .exportMesh(nozzle, fine);

            assertThat(Files.size(fine)).isGreaterThan(Files.size(coarse));
        }

        @Test
        @DisplayName("DualBell output size matches equivalent NozzleContour for same point count")
        void outputSizeMatchesEquivalentContour() throws IOException {
            int segments = 12;
            NozzleContour fromNozzle = NozzleContour.fromPoints(
                    nozzle.getParameters(), nozzle.getContourPoints());

            Path dualBellOut = tempDir.resolve("db_equiv_db.stl");
            Path contourOut  = tempDir.resolve("db_equiv_c.stl");

            new STLExporter().setCircumferentialSegments(segments).setBinaryFormat(true)
                    .exportMesh(nozzle, dualBellOut);
            new STLExporter().setCircumferentialSegments(segments).setBinaryFormat(true)
                    .exportMesh(fromNozzle, contourOut);

            // Both call generateTriangles with the same number of profile points
            assertThat(Files.size(dualBellOut)).isEqualTo(Files.size(contourOut));
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
        @DisplayName("exportAerospikeMesh (binary) should produce a non-trivial file")
        void exportAerospikeMeshBinary() throws IOException {
            Path out = tempDir.resolve("aerospike.stl");
            new STLExporter().setBinaryFormat(true).exportAerospikeMesh(nozzle, out);

            assertThat(out).exists();
            assertThat(Files.size(out)).isGreaterThan(84); // 80-byte header + 4-byte count
        }

        @Test
        @DisplayName("exportAerospikeMesh (ASCII) should contain facet normal entries")
        void exportAerospikeMeshAscii() throws IOException {
            Path out = tempDir.resolve("aerospike_ascii.stl");
            new STLExporter().setBinaryFormat(false).setCircumferentialSegments(18)
                    .exportAerospikeMesh(nozzle, out);

            assertThat(out).exists();
            String content = Files.readString(out);
            assertThat(content).contains("solid");
            assertThat(content).contains("facet normal");
            assertThat(content).contains("endsolid");
        }
    }
}
