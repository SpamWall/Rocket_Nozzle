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
        @DisplayName("exportInnerSurfaceMesh should produce an identical file to exportMesh")
        void exportInnerSurfaceMeshDelegatesToExportMesh() throws IOException {
            Path outMesh  = tempDir.resolve("mesh.stl");
            Path outInner = tempDir.resolve("inner.stl");

            new STLExporter().setCircumferentialSegments(6).setBinaryFormat(false)
                    .exportMesh(contour, outMesh);
            new STLExporter().setCircumferentialSegments(6).setBinaryFormat(false)
                    .exportInnerSurfaceMesh(contour, outInner);

            assertThat(Files.readString(outInner)).isEqualTo(Files.readString(outMesh));
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
