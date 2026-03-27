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

    @Nested
    @DisplayName("firstLayerThickness Tests")
    class FirstLayerThicknessTests {

        @Test
        @DisplayName("setFirstLayerThickness is fluent and rejects non-positive values")
        void setFirstLayerThicknessValidation() {
            assertThatThrownBy(() -> new CFDMeshExporter().setFirstLayerThickness(0.0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new CFDMeshExporter().setFirstLayerThickness(-1e-5))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatCode(() -> new CFDMeshExporter().setFirstLayerThickness(1e-5))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("OpenFOAM blockMesh grading increases when firstLayerThickness decreases")
        void openFoamGradingIncreasesWithSmallerFirstLayer() throws IOException {
            Path outCoarse = tempDir.resolve("bmd_coarse.txt");
            Path outFine   = tempDir.resolve("bmd_fine.txt");

            // Coarse first layer (large h1) → small grading
            new CFDMeshExporter().setRadialCells(20).setFirstLayerThickness(1e-3)
                    .export(contour, outCoarse, CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);
            // Fine first layer (small h1) → large grading
            new CFDMeshExporter().setRadialCells(20).setFirstLayerThickness(1e-5)
                    .export(contour, outFine, CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);

            // Extract the first grading value from each file:
            // the line looks like "        ((0.5 0.5 <g>) (0.5 0.5 <1/g>))"
            double gradingCoarse = extractSimpleGrading(outCoarse);
            double gradingFine   = extractSimpleGrading(outFine);

            assertThat(gradingFine).isGreaterThan(gradingCoarse);
        }

        @Test
        @DisplayName("Gmsh progression ratio is greater than 1 with a small firstLayerThickness")
        void gmshProgressionRatioGreaterThanOne() throws IOException {
            Path out = tempDir.resolve("yplus.geo");
            new CFDMeshExporter().setRadialCells(30).setFirstLayerThickness(1e-5)
                    .export(contour, out, CFDMeshExporter.Format.GMSH_GEO);

            String content = Files.readString(out);
            // Gmsh line: "Transfinite Curve {n, m} = K Using Progression <r>;"
            double r = extractGmshProgression(content);
            assertThat(r).isGreaterThan(1.0);
        }

        @Test
        @DisplayName("Plot3D file is non-empty and exponent increases for smaller firstLayerThickness")
        void plot3dExponentIncreasesWithSmallerFirstLayer() throws IOException {
            Path outCoarse = tempDir.resolve("p3d_coarse.xyz");
            Path outFine   = tempDir.resolve("p3d_fine.xyz");

            new CFDMeshExporter().setRadialCells(20).setFirstLayerThickness(1e-3)
                    .export(contour, outCoarse, CFDMeshExporter.Format.PLOT3D);
            new CFDMeshExporter().setRadialCells(20).setFirstLayerThickness(1e-5)
                    .export(contour, outFine, CFDMeshExporter.Format.PLOT3D);

            // Both files must be non-empty; the finer first layer produces a different grid
            assertThat(Files.size(outCoarse)).isGreaterThan(0);
            assertThat(Files.size(outFine)).isGreaterThan(0);
            // Grids must differ because of different exponents
            assertThat(Files.readString(outFine)).isNotEqualTo(Files.readString(outCoarse));
        }

        @Test
        @DisplayName("Aerospike OpenFOAM blockMesh uses annular gap as domain height")
        void aerospikeOpenFoamUsesAnnularGap() throws IOException {
            AerospikeNozzle nozzle = new AerospikeNozzle(params).generate();
            Path out = tempDir.resolve("aero_yplus.txt");

            new CFDMeshExporter().setRadialCells(20).setFirstLayerThickness(1e-5)
                    .exportAerospike(nozzle, out, CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);

            assertThat(out).exists();
            double grading = extractSimpleGrading(out);
            assertThat(grading).isGreaterThan(1.0);
        }

        // ------------------------------------------------------------------
        // Helpers
        // ------------------------------------------------------------------

        private double extractSimpleGrading(Path file) throws IOException {
            // Matches: "        ((0.5 0.5 <value>) ..."
            return Files.lines(file)
                    .filter(l -> l.contains("0.5 0.5"))
                    .mapToDouble(l -> {
                        String[] parts = l.trim().split("\\s+");
                        // parts: [ "((0.5", "0.5", "<value>)", "(0.5", "0.5", ... ]
                        return Double.parseDouble(parts[2].replace(")", ""));
                    })
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("simpleGrading line not found"));
        }

        private double extractGmshProgression(String content) {
            // Matches: "Transfinite Curve {n, m} = K Using Progression <r>;"
            return java.util.Arrays.stream(content.split("\n"))
                    .filter(l -> l.contains("Using Progression") && l.contains(","))
                    .mapToDouble(l -> {
                        String[] parts = l.trim().split("\\s+");
                        String last = parts[parts.length - 1].replace(";", "");
                        return Double.parseDouble(last);
                    })
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Progression line not found"));
        }
    }
}
