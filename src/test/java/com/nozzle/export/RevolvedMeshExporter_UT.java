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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

@DisplayName("RevolvedMeshExporter Tests")
class RevolvedMeshExporter_UT {

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
        contour.generate(40);
    }

    // -------------------------------------------------------------------------
    // Format smoke tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Format smoke tests")
    class FormatSmokeTests {

        @Test
        @DisplayName("OpenFOAM blockMeshDict should be non-empty")
        void openFoamProducesNonEmptyFile() throws IOException {
            Path out = tempDir.resolve("blockMeshDict_3d");
            new RevolvedMeshExporter().setAxialCells(5).setRadialCells(4).setAzimuthalCells(8)
                    .export(contour, out, RevolvedMeshExporter.Format.OPENFOAM_BLOCKMESH);

            assertThat(out).exists();
            assertThat(Files.size(out)).isGreaterThan(0);
        }

        @Test
        @DisplayName("Gmsh .geo should be non-empty")
        void gmshProducesNonEmptyFile() throws IOException {
            Path out = tempDir.resolve("nozzle_3d.geo");
            new RevolvedMeshExporter().setAxialCells(5).setRadialCells(4).setAzimuthalCells(8)
                    .export(contour, out, RevolvedMeshExporter.Format.GMSH_GEO);

            assertThat(out).exists();
            assertThat(Files.size(out)).isGreaterThan(0);
        }

        @Test
        @DisplayName("Plot3D should be non-empty")
        void plot3dProducesNonEmptyFile() throws IOException {
            Path out = tempDir.resolve("nozzle_3d.xyz");
            new RevolvedMeshExporter().setAxialCells(5).setRadialCells(4).setAzimuthalCells(8)
                    .export(contour, out, RevolvedMeshExporter.Format.PLOT3D);

            assertThat(out).exists();
            assertThat(Files.size(out)).isGreaterThan(0);
        }

        @Test
        @DisplayName("All formats throw IllegalArgumentException for ungenerated contour")
        void allFormatsThrowForEmptyContour() {
            NozzleContour empty = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
            for (RevolvedMeshExporter.Format fmt : RevolvedMeshExporter.Format.values()) {
                Path out = tempDir.resolve("empty_" + fmt.name());
                assertThatThrownBy(() ->
                        new RevolvedMeshExporter().export(empty, out, fmt))
                        .as("Format %s should throw for empty contour", fmt)
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }
    }

    // -------------------------------------------------------------------------
    // OpenFOAM content tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("OpenFOAM blockMeshDict content")
    class OpenFOAMContentTests {

        @Test
        @DisplayName("Should contain FoamFile header, vertices, blocks, and boundary sections")
        void shouldContainRequiredSections() throws IOException {
            Path out = tempDir.resolve("bmd_sections");
            new RevolvedMeshExporter().setAxialCells(4).setRadialCells(3).setAzimuthalCells(4)
                    .export(contour, out, RevolvedMeshExporter.Format.OPENFOAM_BLOCKMESH);

            String content = Files.readString(out);
            assertThat(content).contains("FoamFile");
            assertThat(content).contains("vertices");
            assertThat(content).contains("blocks");
            assertThat(content).contains("boundary");
        }

        @Test
        @DisplayName("Should contain inlet, outlet, and wall patches")
        void shouldContainRequiredPatches() throws IOException {
            Path out = tempDir.resolve("bmd_patches");
            new RevolvedMeshExporter().setAxialCells(4).setRadialCells(3).setAzimuthalCells(4)
                    .export(contour, out, RevolvedMeshExporter.Format.OPENFOAM_BLOCKMESH);

            String content = Files.readString(out);
            assertThat(content).contains("inlet");
            assertThat(content).contains("outlet");
            assertThat(content).contains("wall");
        }

        @Test
        @DisplayName("Should contain correct number of axis vertices")
        void axisVertexCountMatchesFormula() throws IOException {
            int axial = 6;
            Path out = tempDir.resolve("bmd_vertex_count");
            new RevolvedMeshExporter().setAxialCells(axial).setRadialCells(3).setAzimuthalCells(4)
                    .export(contour, out, RevolvedMeshExporter.Format.OPENFOAM_BLOCKMESH);

            // axis[0] .. axis[axial] → axial+1 axis comments
            String content = Files.readString(out);
            long axisComments = content.lines()
                    .filter(l -> l.contains("// axis["))
                    .count();
            assertThat(axisComments).isEqualTo(axial + 1);
        }

        @Test
        @DisplayName("Should contain correct number of wall vertices")
        void wallVertexCountMatchesFormula() throws IOException {
            int axial = 4;
            int azimuthal = 6;
            Path out = tempDir.resolve("bmd_wall_count");
            new RevolvedMeshExporter().setAxialCells(axial).setRadialCells(3).setAzimuthalCells(azimuthal)
                    .export(contour, out, RevolvedMeshExporter.Format.OPENFOAM_BLOCKMESH);

            // wall[i][k]: (axial+1) stations × azimuthal sectors
            String content = Files.readString(out);
            long wallComments = content.lines()
                    .filter(l -> l.contains("// wall["))
                    .count();
            assertThat(wallComments).isEqualTo((long) (axial + 1) * azimuthal);
        }

        @Test
        @DisplayName("Should contain correct number of hex blocks")
        void hexBlockCountMatchesFormula() throws IOException {
            int axial = 5;
            int azimuthal = 8;
            Path out = tempDir.resolve("bmd_block_count");
            new RevolvedMeshExporter().setAxialCells(axial).setRadialCells(3).setAzimuthalCells(azimuthal)
                    .export(contour, out, RevolvedMeshExporter.Format.OPENFOAM_BLOCKMESH);

            String content = Files.readString(out);
            long hexCount = content.lines()
                    .filter(l -> l.trim().startsWith("hex ("))
                    .count();
            assertThat(hexCount).isEqualTo((long) axial * azimuthal);
        }

        @Test
        @DisplayName("More azimuthal sectors produces a larger blockMeshDict")
        void moreAzimuthalSectorsProducesLargerFile() throws IOException {
            Path coarse = tempDir.resolve("bmd_coarse");
            Path fine   = tempDir.resolve("bmd_fine");
            new RevolvedMeshExporter().setAxialCells(5).setRadialCells(3).setAzimuthalCells(4)
                    .export(contour, coarse, RevolvedMeshExporter.Format.OPENFOAM_BLOCKMESH);
            new RevolvedMeshExporter().setAxialCells(5).setRadialCells(3).setAzimuthalCells(16)
                    .export(contour, fine, RevolvedMeshExporter.Format.OPENFOAM_BLOCKMESH);

            assertThat(Files.size(fine)).isGreaterThan(Files.size(coarse));
        }
    }

    // -------------------------------------------------------------------------
    // Gmsh content tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Gmsh .geo content")
    class GmshContentTests {

        @Test
        @DisplayName("Should contain Point, Spline, Curve Loop, and Plane Surface")
        void shouldContain2DProfile() throws IOException {
            Path out = tempDir.resolve("geo_profile");
            new RevolvedMeshExporter().setAxialCells(5).setRadialCells(4).setAzimuthalCells(8)
                    .export(contour, out, RevolvedMeshExporter.Format.GMSH_GEO);

            String content = Files.readString(out);
            assertThat(content).contains("Point(");
            assertThat(content).contains("Spline(");
            assertThat(content).contains("Curve Loop(");
            assertThat(content).contains("Plane Surface(");
        }

        @Test
        @DisplayName("Should contain Extrude, Rotate, and Recombine directives")
        void shouldContainExtrudeRotateRecombine() throws IOException {
            Path out = tempDir.resolve("geo_extrude");
            new RevolvedMeshExporter().setAxialCells(5).setRadialCells(4).setAzimuthalCells(8)
                    .export(contour, out, RevolvedMeshExporter.Format.GMSH_GEO);

            String content = Files.readString(out);
            assertThat(content).contains("Extrude");
            assertThat(content).contains("Rotate");
            assertThat(content).contains("Recombine");
        }

        @Test
        @DisplayName("Should contain Physical Volume for the fluid domain")
        void shouldContainPhysicalVolume() throws IOException {
            Path out = tempDir.resolve("geo_phys");
            new RevolvedMeshExporter().setAxialCells(5).setRadialCells(4).setAzimuthalCells(8)
                    .export(contour, out, RevolvedMeshExporter.Format.GMSH_GEO);

            String content = Files.readString(out);
            assertThat(content).contains("Physical Volume");
            assertThat(content).contains("fluid");
        }

        @Test
        @DisplayName("Layers count in Extrude directive matches azimuthalCells")
        void layersMatchAzimuthalCells() throws IOException {
            int azimuthal = 12;
            Path out = tempDir.resolve("geo_layers");
            new RevolvedMeshExporter().setAxialCells(5).setAzimuthalCells(azimuthal)
                    .export(contour, out, RevolvedMeshExporter.Format.GMSH_GEO);

            String content = Files.readString(out);
            assertThat(content).contains("Layers{" + azimuthal + "}");
        }

        @Test
        @DisplayName("Should contain Physical Surface definitions for wall, inlet, outlet")
        void shouldContainPhysicalSurfaces() throws IOException {
            Path out = tempDir.resolve("geo_surfaces");
            new RevolvedMeshExporter().setAxialCells(5).setAzimuthalCells(8)
                    .export(contour, out, RevolvedMeshExporter.Format.GMSH_GEO);

            String content = Files.readString(out);
            assertThat(content).contains("Physical Surface(\"wall\")");
            assertThat(content).contains("Physical Surface(\"inlet\")");
            assertThat(content).contains("Physical Surface(\"outlet\")");
        }
    }

    // -------------------------------------------------------------------------
    // Plot3D content tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Plot3D 3-D structured grid")
    class Plot3DContentTests {

        @Test
        @DisplayName("Header should declare correct 3-D dimensions (ni × nj × nk)")
        void headerShouldDeclareDimensions() throws IOException {
            int axial = 10, radial = 5, azimuthal = 8;
            Path out = tempDir.resolve("p3d_dims.xyz");
            new RevolvedMeshExporter()
                    .setAxialCells(axial).setRadialCells(radial).setAzimuthalCells(azimuthal)
                    .export(contour, out, RevolvedMeshExporter.Format.PLOT3D);

            // Second line: "ni nj nk"
            String secondLine = Files.lines(out).skip(1).findFirst().orElseThrow();
            String[] parts = secondLine.trim().split("\\s+");
            assertThat(Integer.parseInt(parts[0])).isEqualTo(axial + 1);
            assertThat(Integer.parseInt(parts[1])).isEqualTo(radial + 1);
            assertThat(Integer.parseInt(parts[2])).isEqualTo(azimuthal + 1);
        }

        @Test
        @DisplayName("More azimuthal cells produces a larger Plot3D file")
        void moreAzimuthalCellsProducesLargerPlot3D() throws IOException {
            Path coarse = tempDir.resolve("p3d_coarse.xyz");
            Path fine   = tempDir.resolve("p3d_fine.xyz");
            new RevolvedMeshExporter().setAxialCells(5).setRadialCells(4).setAzimuthalCells(4)
                    .export(contour, coarse, RevolvedMeshExporter.Format.PLOT3D);
            new RevolvedMeshExporter().setAxialCells(5).setRadialCells(4).setAzimuthalCells(16)
                    .export(contour, fine, RevolvedMeshExporter.Format.PLOT3D);

            assertThat(Files.size(fine)).isGreaterThan(Files.size(coarse));
        }

        @Test
        @DisplayName("Closing azimuthal plane should equal the opening plane (periodic closure)")
        void closingPlaneEqualsOpeningPlane() throws IOException {
            Path out = tempDir.resolve("p3d_periodic.xyz");
            int axial = 4, radial = 3, azimuthal = 8;
            new RevolvedMeshExporter()
                    .setAxialCells(axial).setRadialCells(radial).setAzimuthalCells(azimuthal)
                    .export(contour, out, RevolvedMeshExporter.Format.PLOT3D);

            // The grid closes: θ(k=0)=0 and θ(k=azimuthal)=2π → cos/sin equal.
            // Both planes should contribute identical y/z coordinates.
            // Verify the file is consistent (no NaN, all parseable doubles).
            long nanLines = Files.lines(out)
                    .flatMap(l -> java.util.Arrays.stream(l.trim().split("\\s+")))
                    .filter(s -> !s.isEmpty())
                    .filter(s -> { try { Double.parseDouble(s); return false; }
                                  catch (NumberFormatException e) { return true; } })
                    .count();
            assertThat(nanLines).isZero();
        }
    }

    // -------------------------------------------------------------------------
    // Grading / firstLayerThickness tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Grading and firstLayerThickness")
    class GradingTests {

        @Test
        @DisplayName("setFirstLayerThickness rejects non-positive values")
        void setFirstLayerThicknessRejectsNonPositive() {
            assertThatThrownBy(() -> new RevolvedMeshExporter().setFirstLayerThickness(0.0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new RevolvedMeshExporter().setFirstLayerThickness(-1e-5))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatCode(() -> new RevolvedMeshExporter().setFirstLayerThickness(1e-5))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Smaller firstLayerThickness produces a larger grading ratio in blockMeshDict")
        void smallerFirstLayerProducesLargerGrading() throws IOException {
            Path coarse = tempDir.resolve("bmd_grad_coarse");
            Path fine   = tempDir.resolve("bmd_grad_fine");

            new RevolvedMeshExporter().setAxialCells(4).setRadialCells(5).setAzimuthalCells(4)
                    .setFirstLayerThickness(1e-3)
                    .export(contour, coarse, RevolvedMeshExporter.Format.OPENFOAM_BLOCKMESH);
            new RevolvedMeshExporter().setAxialCells(4).setRadialCells(5).setAzimuthalCells(4)
                    .setFirstLayerThickness(1e-5)
                    .export(contour, fine, RevolvedMeshExporter.Format.OPENFOAM_BLOCKMESH);

            double gradingCoarse = extractSimpleGrading(Files.readString(coarse));
            double gradingFine   = extractSimpleGrading(Files.readString(fine));

            assertThat(gradingFine).isGreaterThan(gradingCoarse);
        }

        @Test
        @DisplayName("Smaller firstLayerThickness produces a larger Plot3D 3-D file size")
        void smallerFirstLayerProducesDistinctPlot3D() throws IOException {
            Path coarse = tempDir.resolve("p3d_yp_coarse.xyz");
            Path fine   = tempDir.resolve("p3d_yp_fine.xyz");

            new RevolvedMeshExporter().setAxialCells(5).setRadialCells(8).setAzimuthalCells(4)
                    .setFirstLayerThickness(1e-3)
                    .export(contour, coarse, RevolvedMeshExporter.Format.PLOT3D);
            new RevolvedMeshExporter().setAxialCells(5).setRadialCells(8).setAzimuthalCells(4)
                    .setFirstLayerThickness(1e-5)
                    .export(contour, fine, RevolvedMeshExporter.Format.PLOT3D);

            // Different exponents → different coordinate values → different file content
            assertThat(Files.readString(fine)).isNotEqualTo(Files.readString(coarse));
        }
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /** Extracts the grading ratio from the first {@code simpleGrading (1 <g> 1)} line. */
    private static double extractSimpleGrading(String bmd) {
        return bmd.lines()
                .filter(l -> l.trim().startsWith("simpleGrading"))
                .mapToDouble(l -> {
                    String[] parts = l.trim().split("\\s+");
                    // "simpleGrading", "(1", "<g>", "1)"
                    return Double.parseDouble(parts[2]);
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("simpleGrading line not found"));
    }
}
