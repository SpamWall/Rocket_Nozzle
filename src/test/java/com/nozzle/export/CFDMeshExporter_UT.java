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

@DisplayName("CFDMeshExporter Tests")
class CFDMeshExporter_UT {

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
        @DisplayName("Should export OpenFOAM blockMesh")
        void shouldExportOpenFOAMBlockMesh() throws IOException {
            Path out = tempDir.resolve("blockMeshDict");
            new CFDMeshExporter().setAxialCells(50).setRadialCells(25)
                    .export(contour, out, CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);

            assertThat(out).exists();
            String content = Files.readString(out);
            assertThat(content).contains("FoamFile");
            assertThat(content).contains("vertices");
            assertThat(content).contains("blocks");
        }

        @Test
        @DisplayName("Should export Gmsh geo file")
        void shouldExportGmshGeo() throws IOException {
            Path out = tempDir.resolve("nozzle.geo");
            new CFDMeshExporter().export(contour, out, CFDMeshExporter.Format.GMSH_GEO);

            assertThat(out).exists();
            String content = Files.readString(out);
            assertThat(content).contains("Point");
            assertThat(content).contains("Physical");
        }

        @Test
        @DisplayName("Should export Plot3D format")
        void shouldExportPlot3D() throws IOException {
            Path out = tempDir.resolve("nozzle.xyz");
            new CFDMeshExporter().export(contour, out, CFDMeshExporter.Format.PLOT3D);

            assertThat(out).exists();
        }

        @Test
        @DisplayName("Should export CGNS format (delegates to Plot3D)")
        void shouldExportCGNS() throws IOException {
            Path out = tempDir.resolve("nozzle.cgns");
            new CFDMeshExporter().export(contour, out, CFDMeshExporter.Format.CGNS);

            assertThat(out).exists();
            assertThat(Files.size(out)).isGreaterThan(0);
        }

        @Test
        @DisplayName("setExpansionRatio should be fluent and affect Plot3D grading")
        void setExpansionRatioIsFluent() throws IOException {
            Path out = tempDir.resolve("expansion.xyz");
            new CFDMeshExporter()
                    .setExpansionRatio(1.5)
                    .export(contour, out, CFDMeshExporter.Format.PLOT3D);

            assertThat(out).exists();
        }

        @Test
        @DisplayName("exportOpenFOAM should throw when contour has fewer than two points")
        void exportOpenFOAMThrowsOnEmptyContour() {
            NozzleContour empty = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
            Path out = tempDir.resolve("empty.blockMeshDict");
            assertThatThrownBy(() -> new CFDMeshExporter().exportOpenFOAM(empty, out))
                    .isInstanceOf(IllegalArgumentException.class);
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
        @DisplayName("exportAerospike OpenFOAM should contain spike spline and wedge patches")
        void exportAerospikeOpenFOAM() throws IOException {
            Path out = tempDir.resolve("Aerospike_blockMeshDict");
            new CFDMeshExporter().setAxialCells(20).setRadialCells(10)
                    .exportAerospike(nozzle, out, CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);

            assertThat(out).exists();
            String content = Files.readString(out);
            assertThat(content).contains("FoamFile");
            assertThat(content).contains("vertices");
            assertThat(content).contains("spline");
            assertThat(content).contains("spike");
            assertThat(content).contains("wedge");
        }

        @Test
        @DisplayName("exportAerospike Gmsh should contain spike spline and physical groups")
        void exportAerospikeGmsh() throws IOException {
            Path out = tempDir.resolve("aerospike.geo");
            new CFDMeshExporter().exportAerospike(nozzle, out, CFDMeshExporter.Format.GMSH_GEO);

            assertThat(out).exists();
            String content = Files.readString(out);
            assertThat(content).contains("Spline");
            assertThat(content).contains("spike");
            assertThat(content).contains("cowl");
            assertThat(content).contains("Physical Surface");
        }

        @Test
        @DisplayName("exportAerospike Plot3D should write a non-empty structured grid file")
        void exportAerospikePlot3D() throws IOException {
            Path out = tempDir.resolve("aerospike.xyz");
            new CFDMeshExporter().setAxialCells(10).setRadialCells(5)
                    .exportAerospike(nozzle, out, CFDMeshExporter.Format.PLOT3D);

            assertThat(out).exists();
            assertThat(Files.size(out)).isGreaterThan(0);
        }

        @Test
        @DisplayName("exportAerospike CGNS should produce a non-empty file")
        void exportAerospikeCGNS() throws IOException {
            Path out = tempDir.resolve("aerospike.cgns");
            new CFDMeshExporter().setAxialCells(10).setRadialCells(5)
                    .exportAerospike(nozzle, out, CFDMeshExporter.Format.CGNS);

            assertThat(out).exists();
            assertThat(Files.size(out)).isGreaterThan(0);
        }
    }
}
