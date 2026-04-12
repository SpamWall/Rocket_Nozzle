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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the full-nozzle DXF export methods added to {@link DXFExporter}:
 * <ul>
 *   <li>{@link DXFExporter#exportFullNozzleProfile}</li>
 *   <li>{@link DXFExporter#exportFullNozzleRevolutionProfile}</li>
 * </ul>
 */
class DXFExporterFullNozzle_UT {

    @TempDir
    Path tempDir;

    private NozzleDesignParameters params;
    private FullNozzleGeometry fullGeom;
    private DXFExporter exporter;

    @BeforeEach
    void setUp() {
        params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500.0)
                .ambientPressure(101325.0)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(15)
                .wallAngleInitialDegrees(30.0)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .contractionRatio(4.0)
                .build();

        fullGeom = new FullNozzleGeometry(params).generate(30, 60);
        exporter = new DXFExporter();
    }

    // ── exportFullNozzleProfile ───────────────────────────────────────────────

    @Test
    void exportFullNozzleProfile_createsFile() throws IOException {
        Path out = tempDir.resolve("full_nozzle.dxf");
        exporter.exportFullNozzleProfile(fullGeom, out);
        assertThat(Files.exists(out)).isTrue();
    }

    @Test
    void exportFullNozzleProfile_fileIsNonEmpty() throws IOException {
        Path out = tempDir.resolve("full_nozzle.dxf");
        exporter.exportFullNozzleProfile(fullGeom, out);
        assertThat(Files.size(out)).isPositive();
    }

    @Test
    void exportFullNozzleProfile_containsDxfHeader() throws IOException {
        Path out = tempDir.resolve("full_nozzle.dxf");
        exporter.exportFullNozzleProfile(fullGeom, out);
        String content = Files.readString(out);
        assertThat(content).contains("SECTION").contains("ENTITIES").contains("ENDSEC");
    }

    @Test
    void exportFullNozzleProfile_containsWallLayer() throws IOException {
        Path out = tempDir.resolve("full_nozzle.dxf");
        exporter.exportFullNozzleProfile(fullGeom, out);
        String content = Files.readString(out);
        assertThat(content).contains("WALL");
    }

    @Test
    void exportFullNozzleProfile_containsAxisLayer() throws IOException {
        Path out = tempDir.resolve("full_nozzle.dxf");
        exporter.exportFullNozzleProfile(fullGeom, out);
        String content = Files.readString(out);
        assertThat(content).contains("AXIS");
    }

    @Test
    void exportFullNozzleProfile_containsThroatLayer() throws IOException {
        Path out = tempDir.resolve("full_nozzle.dxf");
        exporter.exportFullNozzleProfile(fullGeom, out);
        String content = Files.readString(out);
        assertThat(content).contains("THROAT");
    }

    @Test
    void exportFullNozzleProfile_containsPolyline() throws IOException {
        Path out = tempDir.resolve("full_nozzle.dxf");
        exporter.exportFullNozzleProfile(fullGeom, out);
        String content = Files.readString(out);
        assertThat(content).contains("POLYLINE");
    }

    @Test
    void exportFullNozzleProfile_containsVertices() throws IOException {
        Path out = tempDir.resolve("full_nozzle.dxf");
        exporter.exportFullNozzleProfile(fullGeom, out);
        List<String> lines = Files.readAllLines(out);
        long vertexCount = lines.stream().filter("VERTEX"::equals).count();
        // Expect at least as many vertices as wall points
        assertThat(vertexCount).isGreaterThanOrEqualTo(fullGeom.getWallPoints().size());
    }

    @Test
    void exportFullNozzleProfile_throwsForEmptyGeometry() {
        FullNozzleGeometry empty = new FullNozzleGeometry(params); // not generated
        Path out = tempDir.resolve("empty.dxf");
        assertThatThrownBy(() -> exporter.exportFullNozzleProfile(empty, out))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wall points");
    }

    // ── exportFullNozzleRevolutionProfile ────────────────────────────────────

    @Test
    void exportRevolution_createsFile() throws IOException {
        Path out = tempDir.resolve("revolution.dxf");
        exporter.exportFullNozzleRevolutionProfile(fullGeom, out);
        assertThat(Files.exists(out)).isTrue();
    }

    @Test
    void exportRevolution_fileIsNonEmpty() throws IOException {
        Path out = tempDir.resolve("revolution.dxf");
        exporter.exportFullNozzleRevolutionProfile(fullGeom, out);
        assertThat(Files.size(out)).isPositive();
    }

    @Test
    void exportRevolution_containsAllRequiredLayers() throws IOException {
        Path out = tempDir.resolve("revolution.dxf");
        exporter.exportFullNozzleRevolutionProfile(fullGeom, out);
        String content = Files.readString(out);
        assertThat(content).contains("WALL");
        assertThat(content).contains("AXIS");
        assertThat(content).contains("INLET");
        assertThat(content).contains("OUTLET");
        assertThat(content).contains("THROAT");
    }

    @Test
    void exportRevolution_containsThreeLineEntities() throws IOException {
        Path out = tempDir.resolve("revolution.dxf");
        exporter.exportFullNozzleRevolutionProfile(fullGeom, out);
        List<String> lines = Files.readAllLines(out);
        // AXIS + INLET + OUTLET + THROAT = 4 LINE entities (THROAT is a LINE)
        long lineCount = lines.stream().filter("LINE"::equals).count();
        assertThat(lineCount).isGreaterThanOrEqualTo(4);
    }

    @Test
    void exportRevolution_throwsForEmptyGeometry() {
        FullNozzleGeometry empty = new FullNozzleGeometry(params);
        Path out = tempDir.resolve("empty_rev.dxf");
        assertThatThrownBy(() -> exporter.exportFullNozzleRevolutionProfile(empty, out))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wall points");
    }

    // ── Scale factor is applied ───────────────────────────────────────────────

    @Test
    void customScaleFactorAffectsOutput() throws IOException {
        // Export with scale=1 (metre-native) vs scale=1000 (mm)
        Path outMm = tempDir.resolve("scale_mm.dxf");
        Path outM  = tempDir.resolve("scale_m.dxf");

        exporter.setScaleFactor(1000.0).exportFullNozzleProfile(fullGeom, outMm);
        exporter.setScaleFactor(1.0)   .exportFullNozzleProfile(fullGeom, outM);

        // The two files should have different coordinate strings
        assertThat(Files.readString(outMm)).isNotEqualTo(Files.readString(outM));
    }

    // ── Negative-coordinate wall points appear in file ───────────────────────

    @Test
    void exportContainsNegativeXCoordinate() throws IOException {
        // The convergent section wall points have x < 0; those coordinates must
        // appear in the exported DXF.
        Path out = tempDir.resolve("full_nozzle_neg.dxf");
        exporter.setScaleFactor(1.0).exportFullNozzleProfile(fullGeom, out);
        String content = Files.readString(out);
        // At least one vertex should have a negative x (10\n-...)
        assertThat(content).containsPattern("10\\n-");
    }
}
