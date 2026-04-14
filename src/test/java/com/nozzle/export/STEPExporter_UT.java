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
    @DisplayName("exportRevolvedSolid Tests")
    class RevolvedSolidExportTests {

        // ---- NozzleContour overload ----

        @Test
        @DisplayName("exportRevolvedSolid(NozzleContour) writes a complete ISO-10303-21 file")
        void contourOverloadWritesCompleteStepFile() throws IOException {
            Path out = tempDir.resolve("revsolid_complete.step");
            new STEPExporter().exportRevolvedSolid(contour, out);

            String content = Files.readString(out);
            assertThat(content).contains("ISO-10303-21");
            assertThat(content).contains("HEADER");
            assertThat(content).contains("FILE_SCHEMA");
            assertThat(content).contains("AUTOMOTIVE_DESIGN");
            assertThat(content).contains("DATA;");
            assertThat(content).contains("ENDSEC;");
            assertThat(content).contains("END-ISO-10303-21");
        }

        @Test
        @DisplayName("exportRevolvedSolid(NozzleContour) writes a SURFACE_OF_REVOLUTION entity")
        void contourOverloadWritesSurfaceOfRevolution() throws IOException {
            Path out = tempDir.resolve("revsolid_sor.step");
            new STEPExporter().exportRevolvedSolid(contour, out);

            assertThat(Files.readString(out)).contains("SURFACE_OF_REVOLUTION");
        }

        @Test
        @DisplayName("exportRevolvedSolid(NozzleContour) writes n+2 CARTESIAN_POINT entities")
        void contourOverloadCartesianPointCountIsNPlusTwo() throws IOException {
            Path out = tempDir.resolve("revsolid_pts.step");
            new STEPExporter().exportRevolvedSolid(contour, out);

            // n profile points + 2 axis-closing points (exit-axis + throat-axis)
            long count = Files.readString(out).lines()
                    .filter(l -> l.contains("CARTESIAN_POINT"))
                    .count();
            // writeData also writes an origin point, so total is n + 2 profile closes + 1 origin
            assertThat(count).isGreaterThanOrEqualTo(contour.getContourPoints().size() + 2);
        }

        @Test
        @DisplayName("exportRevolvedSolid(NozzleContour) writes POLYLINE and EDGE_CURVE entities")
        void contourOverloadWritesPolylineAndEdgeCurve() throws IOException {
            Path out = tempDir.resolve("revsolid_poly.step");
            new STEPExporter().exportRevolvedSolid(contour, out);

            String content = Files.readString(out);
            assertThat(content).contains("POLYLINE");
            assertThat(content).contains("EDGE_CURVE");
        }

        @Test
        @DisplayName("exportRevolvedSolid(NozzleContour) writes the product-definition chain")
        void contourOverloadWritesProductDefinitionChain() throws IOException {
            Path out = tempDir.resolve("revsolid_prod.step");
            new STEPExporter().exportRevolvedSolid(contour, out);

            String content = Files.readString(out);
            assertThat(content).contains("PRODUCT(");
            assertThat(content).contains("PRODUCT_DEFINITION(");
            assertThat(content).contains("SHAPE_REPRESENTATION(");
            assertThat(content).contains("SHAPE_DEFINITION_REPRESENTATION(");
        }

        @Test
        @DisplayName("exportRevolvedSolid(NozzleContour) setScaleFactor changes coordinate values")
        void contourOverloadScaleFactorChangesCoordinates() throws IOException {
            Path defaultOut = tempDir.resolve("revsolid_scale_default.step");
            Path scaledOut  = tempDir.resolve("revsolid_scale_1.step");

            new STEPExporter()                    .exportRevolvedSolid(contour, defaultOut);
            new STEPExporter().setScaleFactor(1.0).exportRevolvedSolid(contour, scaledOut);

            assertThat(Files.readString(defaultOut))
                    .isNotEqualTo(Files.readString(scaledOut));
        }

        // ---- FullNozzleGeometry overload ----

        @Test
        @DisplayName("exportRevolvedSolid(FullNozzleGeometry) writes a complete STEP file")
        void fullGeometryOverloadWritesCompleteStepFile() throws IOException {
            FullNozzleGeometry geom = new FullNozzleGeometry(params).generate();
            Path out = tempDir.resolve("revsolid_full.step");
            new STEPExporter().exportRevolvedSolid(geom, out);

            String content = Files.readString(out);
            assertThat(content).contains("ISO-10303-21");
            assertThat(content).contains("SURFACE_OF_REVOLUTION");
            assertThat(content).contains("END-ISO-10303-21");
        }

        @Test
        @DisplayName("exportRevolvedSolid(FullNozzleGeometry) throws IllegalStateException when not generated")
        void fullGeometryOverloadThrowsWhenNotGenerated() {
            FullNozzleGeometry ungenerated = new FullNozzleGeometry(params);
            Path out = tempDir.resolve("revsolid_empty_full.step");
            assertThatThrownBy(() -> new STEPExporter().exportRevolvedSolid(ungenerated, out))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("exportRevolvedSolid(FullNozzleGeometry) output contains negative x coordinates")
        void fullGeometryOverloadContainsNegativeXCoordinates() throws IOException {
            FullNozzleGeometry geom = new FullNozzleGeometry(params).generate();
            Path out = tempDir.resolve("revsolid_full_neg.step");
            new STEPExporter().setScaleFactor(1.0).exportRevolvedSolid(geom, out);

            // Convergent section starts at negative x (chamber face); those
            // CARTESIAN_POINT entries must appear in the file
            assertThat(Files.readString(out)).containsPattern("CARTESIAN_POINT.*-\\d");
        }

        @Test
        @DisplayName("exportRevolvedSolid(FullNozzleGeometry) has more CARTESIAN_POINTs than divergent-only")
        void fullGeometryOverloadHasMorePointsThanDivergentOnly() throws IOException {
            FullNozzleGeometry geom = new FullNozzleGeometry(params).generate();
            Path fullOut      = tempDir.resolve("revsolid_full_count.step");
            Path divergentOut = tempDir.resolve("revsolid_div_count.step");

            new STEPExporter().exportRevolvedSolid(geom, fullOut);
            new STEPExporter().exportRevolvedSolid(contour, divergentOut);

            long fullCount = Files.readString(fullOut).lines()
                    .filter(l -> l.contains("CARTESIAN_POINT")).count();
            long divCount  = Files.readString(divergentOut).lines()
                    .filter(l -> l.contains("CARTESIAN_POINT")).count();

            assertThat(fullCount).isGreaterThan(divCount);
        }

        // ---- DualBellNozzle overload ----

        @Test
        @DisplayName("exportRevolvedSolid(DualBellNozzle) writes a complete STEP file")
        void dualBellOverloadWritesCompleteStepFile() throws IOException {
            DualBellNozzle nozzle = new DualBellNozzle(params, 2.0).generate();
            Path out = tempDir.resolve("revsolid_dualbell.step");
            new STEPExporter().exportRevolvedSolid(nozzle, out);

            String content = Files.readString(out);
            assertThat(content).contains("ISO-10303-21");
            assertThat(content).contains("SURFACE_OF_REVOLUTION");
            assertThat(content).contains("END-ISO-10303-21");
        }

        @Test
        @DisplayName("exportRevolvedSolid(DualBellNozzle) writes n+2 CARTESIAN_POINT entities")
        void dualBellOverloadCartesianPointCountIsNPlusTwo() throws IOException {
            DualBellNozzle nozzle = new DualBellNozzle(params, 2.0).generate();
            Path out = tempDir.resolve("revsolid_dualbell_pts.step");
            new STEPExporter().exportRevolvedSolid(nozzle, out);

            long count = Files.readString(out).lines()
                    .filter(l -> l.contains("CARTESIAN_POINT")).count();
            assertThat(count).isGreaterThanOrEqualTo(nozzle.getContourPoints().size() + 2);
        }

        @Test
        @DisplayName("exportRevolvedSolid(DualBellNozzle) produces identical output to NozzleContour overload for same points")
        void dualBellAndContourOverloadsProduceSameOutput() throws IOException {
            DualBellNozzle nozzle = new DualBellNozzle(params, 2.0).generate();
            NozzleContour fromNozzle = NozzleContour.fromPoints(
                    nozzle.getParameters(), nozzle.getContourPoints());

            Path dualBellOut = tempDir.resolve("revsolid_db_equiv_db.step");
            Path contourOut  = tempDir.resolve("revsolid_db_equiv_c.step");

            new STEPExporter().exportRevolvedSolid(nozzle, dualBellOut);
            new STEPExporter().exportRevolvedSolid(fromNozzle, contourOut);

            // Both paths call writeData with the same points so DATA sections must match
            assertThat(Files.readString(dualBellOut)).contains("SURFACE_OF_REVOLUTION");
            assertThat(Files.readString(contourOut)).contains("SURFACE_OF_REVOLUTION");
            long dbCount = Files.readString(dualBellOut).lines()
                    .filter(l -> l.contains("CARTESIAN_POINT")).count();
            long cCount  = Files.readString(contourOut).lines()
                    .filter(l -> l.contains("CARTESIAN_POINT")).count();
            assertThat(dbCount).isEqualTo(cCount);
        }
    }

    @Nested
    @DisplayName("exportProfileCurve Tests")
    class ProfileCurveExportTests {

        // ---- NozzleContour overload ----

        @Test
        @DisplayName("exportProfileCurve(NozzleContour) writes a valid ISO-10303-21 file")
        void contourOverloadWritesValidStepFile() throws IOException {
            Path out = tempDir.resolve("profile_valid.step");
            new STEPExporter().exportProfileCurve(contour, out);

            String content = Files.readString(out);
            assertThat(content).contains("ISO-10303-21");
            assertThat(content).contains("HEADER");
            assertThat(content).contains("DATA;");
            assertThat(content).contains("END-ISO-10303-21");
        }

        @Test
        @DisplayName("exportProfileCurve(NozzleContour) writes one CARTESIAN_POINT per contour point")
        void cartesianPointCountMatchesContourPoints() throws IOException {
            Path out = tempDir.resolve("profile_pts.step");
            new STEPExporter().exportProfileCurve(contour, out);

            String content = Files.readString(out);
            long count = content.lines()
                    .filter(l -> l.contains("CARTESIAN_POINT"))
                    .count();
            assertThat(count).isEqualTo(contour.getContourPoints().size());
        }

        @Test
        @DisplayName("exportProfileCurve(NozzleContour) writes a B_SPLINE_CURVE_WITH_KNOTS named 'Profile'")
        void bsplineEntityNamedProfile() throws IOException {
            Path out = tempDir.resolve("profile_bspline.step");
            new STEPExporter().exportProfileCurve(contour, out);

            assertThat(Files.readString(out)).contains("B_SPLINE_CURVE_WITH_KNOTS('Profile'");
        }

        @Test
        @DisplayName("exportProfileCurve(NozzleContour) uses degree 3 for a standard contour")
        void bsplineDegreeIsThreeForStandardContour() throws IOException {
            // 50 points → degree = min(3, 49) = 3
            Path out = tempDir.resolve("profile_deg.step");
            new STEPExporter().exportProfileCurve(contour, out);

            assertThat(Files.readString(out)).contains("B_SPLINE_CURVE_WITH_KNOTS('Profile',3,");
        }

        @Test
        @DisplayName("exportProfileCurve(NozzleContour) uses degree 1 for a two-point contour")
        void bsplineDegreeIsOneForTwoPointContour() throws IOException {
            NozzleContour tiny = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
            tiny.generate(2);
            Path out = tempDir.resolve("profile_deg1.step");
            new STEPExporter().exportProfileCurve(tiny, out);

            int degree = Math.min(3, tiny.getContourPoints().size() - 1);
            assertThat(Files.readString(out))
                    .contains("B_SPLINE_CURVE_WITH_KNOTS('Profile'," + degree + ",");
        }

        @Test
        @DisplayName("setScaleFactor changes the coordinate values in exportProfileCurve output")
        void setScaleFactorChangesCoordinates() throws IOException {
            Path defaultOut = tempDir.resolve("profile_scale_default.step");
            Path scaledOut  = tempDir.resolve("profile_scale_1.step");

            new STEPExporter()              .exportProfileCurve(contour, defaultOut);
            new STEPExporter().setScaleFactor(1.0).exportProfileCurve(contour, scaledOut);

            assertThat(Files.readString(defaultOut))
                    .isNotEqualTo(Files.readString(scaledOut));
        }

        @Test
        @DisplayName("setAuthor and setOrganization appear in exportProfileCurve output")
        void authorAndOrganizationAppearsInProfileCurveHeader() throws IOException {
            Path out = tempDir.resolve("profile_author.step");
            new STEPExporter()
                    .setAuthor("Jane Doe")
                    .setOrganization("Propulsion Lab")
                    .exportProfileCurve(contour, out);

            String content = Files.readString(out);
            assertThat(content).contains("Jane Doe");
            assertThat(content).contains("Propulsion Lab");
        }

        // ---- DualBellNozzle overload ----

        @Test
        @DisplayName("exportProfileCurve(DualBellNozzle) writes a valid STEP file")
        void dualBellOverloadWritesValidStepFile() throws IOException {
            DualBellNozzle nozzle = new DualBellNozzle(params, 2.0).generate();
            Path out = tempDir.resolve("profile_dualbell.step");
            new STEPExporter().exportProfileCurve(nozzle, out);

            String content = Files.readString(out);
            assertThat(content).contains("ISO-10303-21");
            assertThat(content).contains("CARTESIAN_POINT");
            assertThat(content).contains("B_SPLINE_CURVE_WITH_KNOTS('Profile'");
            assertThat(content).contains("END-ISO-10303-21");
        }

        @Test
        @DisplayName("exportProfileCurve(DualBellNozzle) writes one CARTESIAN_POINT per contour point")
        void dualBellCartesianPointCountMatchesNozzlePoints() throws IOException {
            DualBellNozzle nozzle = new DualBellNozzle(params, 2.0).generate();
            Path out = tempDir.resolve("profile_dualbell_pts.step");
            new STEPExporter().exportProfileCurve(nozzle, out);

            long count = Files.readString(out).lines()
                    .filter(l -> l.contains("CARTESIAN_POINT"))
                    .count();
            assertThat(count).isEqualTo(nozzle.getContourPoints().size());
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
