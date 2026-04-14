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
import com.nozzle.moc.AltitudePerformance;
import com.nozzle.moc.DualBellNozzle;
import com.nozzle.moc.CharacteristicNet;
import com.nozzle.thermal.BoundaryLayerCorrection;
import com.nozzle.thermal.HeatTransferModel;
import com.nozzle.thermal.ThermalStressAnalysis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CSVExporter Tests")
class CSVExporter_UT {

    private NozzleDesignParameters params;
    private NozzleContour contour;
    private CharacteristicNet net;

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

        net = new CharacteristicNet(params).generate();
    }

    @Nested
    @DisplayName("Bell Nozzle Export Tests")
    class BellNozzleExportTests {

        @Test
        @DisplayName("Should export wall contour to CSV")
        void shouldExportWallContourToCSV() throws IOException {
            Path out = tempDir.resolve("wall_contour.csv");
            new CSVExporter().exportWallContour(net, out);

            assertThat(out).exists();
            assertThat(Files.readString(out)).contains("x,y,mach");
        }

        @Test
        @DisplayName("Should export design parameters to CSV")
        void shouldExportDesignParametersToCSV() throws IOException {
            Path out = tempDir.resolve("params.csv");
            new CSVExporter().exportDesignParameters(params, out);

            assertThat(out).exists();
            String content = Files.readString(out);
            assertThat(content).contains("parameter,value,unit");
            assertThat(content).contains("throat_radius");
        }

        @Test
        @DisplayName("Should export contour to CSV")
        void shouldExportContourToCSV() throws IOException {
            Path out = tempDir.resolve("contour.csv");
            new CSVExporter().exportContour(contour, out);

            assertThat(out).exists();
        }

        @Test
        @DisplayName("exportDesignParameters should write 0 for non-axisymmetric params")
        void exportDesignParametersNonAxisymmetric() throws IOException {
            NozzleDesignParameters planar = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(2.5)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.AIR)
                    .numberOfCharLines(10)
                    .wallAngleInitialDegrees(25)
                    .lengthFraction(0.8)
                    .axisymmetric(false)
                    .build();
            Path out = tempDir.resolve("params_planar.csv");
            new CSVExporter().exportDesignParameters(planar, out);

            assertThat(out).exists();
            assertThat(Files.readString(out)).contains("axisymmetric,0.0000000");
        }
    }

    @Nested
    @DisplayName("exportContour Tests")
    class ContourExportTests {

        // ---- NozzleContour overload ----

        @Test
        @DisplayName("exportContour(NozzleContour) writes the correct header")
        void nozzleContourWritesCorrectHeader() throws IOException {
            Path out = tempDir.resolve("contour_header.csv");
            new CSVExporter().exportContour(contour, out);

            String firstLine = Files.readString(out).lines().findFirst().orElseThrow();
            assertThat(firstLine).isEqualTo("x,y,angle_deg");
        }

        @Test
        @DisplayName("exportContour(NozzleContour) writes one data row per contour point")
        void nozzleContourRowCountMatchesContourPoints() throws IOException {
            Path out = tempDir.resolve("contour_rows.csv");
            new CSVExporter().exportContour(contour, out);

            long dataRows = Files.readString(out).lines().skip(1)
                    .filter(l -> !l.isBlank()).count();
            assertThat(dataRows).isEqualTo(contour.getContourPoints().size());
        }

        @Test
        @DisplayName("exportContour(NozzleContour) x values are monotonically non-decreasing")
        void nozzleContourXValuesMonotonic() throws IOException {
            Path out = tempDir.resolve("contour_mono.csv");
            new CSVExporter().exportContour(contour, out);

            List<Double> xs = Files.readString(out).lines().skip(1)
                    .filter(l -> !l.isBlank())
                    .map(l -> Double.parseDouble(l.split(",")[0]))
                    .toList();
            for (int i = 1; i < xs.size(); i++) {
                assertThat(xs.get(i)).isGreaterThanOrEqualTo(xs.get(i - 1));
            }
        }

        @Test
        @DisplayName("exportContour(NozzleContour) y values are positive (wall radius > 0)")
        void nozzleContourYValuesPositive() throws IOException {
            Path out = tempDir.resolve("contour_y.csv");
            new CSVExporter().exportContour(contour, out);

            Files.readString(out).lines().skip(1).filter(l -> !l.isBlank()).forEach(line -> {
                double y = Double.parseDouble(line.split(",")[1]);
                assertThat(y).isGreaterThan(0.0);
            });
        }

        // ---- FullNozzleGeometry overload ----

        @Test
        @DisplayName("exportContour(FullNozzleGeometry) writes the correct header")
        void fullGeometryWritesCorrectHeader() throws IOException {
            FullNozzleGeometry geom = new FullNozzleGeometry(params).generate();
            Path out = tempDir.resolve("full_contour_header.csv");
            new CSVExporter().exportContour(geom, out);

            String firstLine = Files.readString(out).lines().findFirst().orElseThrow();
            assertThat(firstLine).isEqualTo("x,y,angle_deg,section");
        }

        @Test
        @DisplayName("exportContour(FullNozzleGeometry) writes one data row per wall point")
        void fullGeometryRowCountMatchesWallPoints() throws IOException {
            FullNozzleGeometry geom = new FullNozzleGeometry(params).generate();
            Path out = tempDir.resolve("full_contour_rows.csv");
            new CSVExporter().exportContour(geom, out);

            long dataRows = Files.readString(out).lines().skip(1)
                    .filter(l -> !l.isBlank()).count();
            assertThat(dataRows).isEqualTo(geom.getWallPoints().size());
        }

        @Test
        @DisplayName("exportContour(FullNozzleGeometry) labels negative-x rows as 'convergent'")
        void fullGeometryConvergentRowsLabelled() throws IOException {
            FullNozzleGeometry geom = new FullNozzleGeometry(params).generate();
            Path out = tempDir.resolve("full_contour_conv.csv");
            new CSVExporter().exportContour(geom, out);

            Files.readString(out).lines().skip(1).filter(l -> !l.isBlank()).forEach(line -> {
                String[] parts = line.split(",");
                double x = Double.parseDouble(parts[0]);
                if (x < 0.0) assertThat(parts[3]).isEqualTo("convergent");
            });
        }

        @Test
        @DisplayName("exportContour(FullNozzleGeometry) labels positive-x rows as 'divergent'")
        void fullGeometryDivergentRowsLabelled() throws IOException {
            FullNozzleGeometry geom = new FullNozzleGeometry(params).generate();
            Path out = tempDir.resolve("full_contour_div.csv");
            new CSVExporter().exportContour(geom, out);

            Files.readString(out).lines().skip(1).filter(l -> !l.isBlank()).forEach(line -> {
                String[] parts = line.split(",");
                double x = Double.parseDouble(parts[0]);
                if (x > 0.0) assertThat(parts[3]).isEqualTo("divergent");
            });
        }

        @Test
        @DisplayName("exportContour(FullNozzleGeometry) output contains both convergent and divergent rows")
        void fullGeometryContainsBothSections() throws IOException {
            FullNozzleGeometry geom = new FullNozzleGeometry(params).generate();
            Path out = tempDir.resolve("full_contour_both.csv");
            new CSVExporter().exportContour(geom, out);

            String content = Files.readString(out);
            assertThat(content).contains("convergent");
            assertThat(content).contains("divergent");
        }

        @Test
        @DisplayName("exportContour(FullNozzleGeometry) throws IllegalStateException when not generated")
        void fullGeometryThrowsWhenNotGenerated() {
            FullNozzleGeometry ungenerated = new FullNozzleGeometry(params);
            Path out = tempDir.resolve("full_contour_empty.csv");
            assertThatThrownBy(() -> new CSVExporter().exportContour(ungenerated, out))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("Thermal Export Tests")
    class ThermalExportTests {

        private HeatTransferModel heat;
        private BoundaryLayerCorrection bl;

        @BeforeEach
        void setUpThermal() {
            heat = new HeatTransferModel(params, contour).calculate(List.of());
            bl   = new BoundaryLayerCorrection(params, contour).calculate(List.of());
        }

        @Test
        @DisplayName("Should export characteristic net to CSV")
        void shouldExportCharacteristicNetToCSV() throws IOException {
            Path out = tempDir.resolve("char_net.csv");
            new CSVExporter().exportCharacteristicNet(net, out);

            assertThat(out).exists();
            String content = Files.readString(out);
            assertThat(content).contains("row,index,x,y,mach");
        }

        @Test
        @DisplayName("Should export thermal profile to CSV")
        void shouldExportThermalProfileToCSV() throws IOException {
            Path out = tempDir.resolve("thermal.csv");
            new CSVExporter().exportThermalProfile(heat, out);

            assertThat(out).exists();
            String content = Files.readString(out);
            assertThat(content).contains("wall_temp_K");
            assertThat(content).contains("total_heat_flux_W_m2");
        }

        @Test
        @DisplayName("exportBoundaryLayerProfile should write 'true' for turbulent points")
        void exportBoundaryLayerProfileTurbulent() throws IOException {
            // forceTurbulent = true by default
            Path out = tempDir.resolve("bl_turbulent.csv");
            new CSVExporter().exportBoundaryLayerProfile(bl, out);

            assertThat(out).exists();
            String content = Files.readString(out);
            assertThat(content).contains("running_length,Reynolds");
            assertThat(content).contains("true");
        }

        @Test
        @DisplayName("exportBoundaryLayerProfile should write 'false' for laminar points")
        void exportBoundaryLayerProfileLaminar() throws IOException {
            // forceTurbulent=false + very high transition Re keeps all points laminar
            BoundaryLayerCorrection laminar = new BoundaryLayerCorrection(params, contour)
                    .setForceTurbulent(false)
                    .setTransitionReynolds(Double.MAX_VALUE)
                    .calculate(List.of());
            Path out = tempDir.resolve("bl_laminar.csv");
            new CSVExporter().exportBoundaryLayerProfile(laminar, out);

            assertThat(out).exists();
            assertThat(Files.readString(out)).contains("false");
        }

        @Test
        @DisplayName("exportCompleteReport with all optional args null should skip thermal files")
        void exportCompleteReportAllNulls() throws IOException {
            Path dir = tempDir.resolve("report_null");
            new CSVExporter().exportCompleteReport(net, null, null, null, dir);

            assertThat(dir.resolve("design_parameters.csv")).exists();
            assertThat(dir.resolve("characteristic_net.csv")).exists();
            assertThat(dir.resolve("wall_contour_moc.csv")).exists();
            assertThat(dir.resolve("wall_contour_design.csv")).doesNotExist();
            assertThat(dir.resolve("thermal_profile.csv")).doesNotExist();
            assertThat(dir.resolve("boundary_layer.csv")).doesNotExist();
        }

        @Test
        @DisplayName("exportCompleteReport with all optional args non-null should write all files")
        void exportCompleteReportAllNonNull() throws IOException {
            Path dir = tempDir.resolve("report_full");
            new CSVExporter().exportCompleteReport(net, contour, heat, bl, dir);

            assertThat(dir.resolve("design_parameters.csv")).exists();
            assertThat(dir.resolve("characteristic_net.csv")).exists();
            assertThat(dir.resolve("wall_contour_moc.csv")).exists();
            assertThat(dir.resolve("wall_contour_design.csv")).exists();
            assertThat(dir.resolve("thermal_profile.csv")).exists();
            assertThat(dir.resolve("boundary_layer.csv")).exists();
        }

        @Test
        @DisplayName("exportStressProfile should write 'Inf' for sub-endurance-limit points and numeric values for high-stress points")
        void exportStressProfileBothCycleBranches() throws IOException {
            // High heat flux → large ΔT → sigVM >> endurance limit → finite cycles
            HeatTransferModel.WallThermalPoint highFlux = new HeatTransferModel.WallThermalPoint(
                    0.0, 0.05, 1500.0, 50e6, 50e6, 0.0, 1e5, 3000.0);
            // Zero heat flux → sigVM driven by pressure only (~100 MPa) < Inconel endurance limit (690 MPa) → Inf
            HeatTransferModel.WallThermalPoint zeroFlux = new HeatTransferModel.WallThermalPoint(
                    0.1, 0.05, 800.0, 0.0, 0.0, 0.0, 1e4, 1000.0);

            ThermalStressAnalysis analysis = new ThermalStressAnalysis(
                    params, List.of(highFlux, zeroFlux),
                    ThermalStressAnalysis.Material.INCONEL_718, 0.003, 20.0)
                    .calculate();

            Path out = tempDir.resolve("stress.csv");
            new CSVExporter().exportStressProfile(analysis, out);

            assertThat(out).exists();
            String content = Files.readString(out);
            assertThat(content).contains("sigma_vm_MPa");
            assertThat(content).contains("Inf");   // infinite-life branch
            // numeric cycle count present (finite-life branch produced a decimal value)
            assertThat(content.lines()
                    .skip(1) // skip header
                    .anyMatch(line -> !line.endsWith(",Inf")))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Aerospike Export Tests")
    class AerospikeExportTests {

        private AerospikeNozzle nozzle;
        private double[] altitudePressures;

        @BeforeEach
        void setUpAerospike() {
            nozzle = new AerospikeNozzle(params).generate();
            altitudePressures = new double[]{101325, 50000, 20000, 5000, 1000};
        }

        @Test
        @DisplayName("exportSpikeContour should write full and truncated rows")
        void exportSpikeContourWritesFullAndTruncated() throws IOException {
            Path out = tempDir.resolve("spike_contour.csv");
            new CSVExporter().exportSpikeContour(nozzle, out);

            assertThat(out).exists();
            String content = Files.readString(out);
            assertThat(content).contains("contour,x_m,y_m");
            assertThat(content).contains("full,");
            assertThat(content).contains("truncated,");
        }

        @Test
        @DisplayName("exportAltitudePerformance should write all pressure levels")
        void exportAltitudePerformanceWritesAllLevels() throws IOException {
            AltitudePerformance perf = nozzle.calculateAltitudePerformance(altitudePressures);
            Path out = tempDir.resolve("altitude_perf.csv");
            new CSVExporter().exportAltitudePerformance(perf, out);

            assertThat(out).exists();
            String content = Files.readString(out);
            assertThat(content).contains("ambient_pressure_Pa");
            assertThat(content).contains("aerospike_cf");
            assertThat(content).contains("bell_nozzle_cf");
            assertThat(content.lines().count()).isEqualTo(6); // header + 5 data rows
        }

        @Test
        @DisplayName("exportAerospikeReport should create three CSV files in output directory")
        void exportAerospikeReportCreatesThreeFiles() throws IOException {
            Path reportDir = tempDir.resolve("aerospike_report");
            new CSVExporter().exportAerospikeReport(nozzle, altitudePressures, reportDir);

            assertThat(reportDir.resolve("aerospike_design_parameters.csv")).exists();
            assertThat(reportDir.resolve("aerospike_spike_contour.csv")).exists();
            assertThat(reportDir.resolve("aerospike_altitude_performance.csv")).exists();
        }
    }

    @Nested
    @DisplayName("DualBell Report Export Tests")
    class DualBellReportExportTests {

        private DualBellNozzle nozzle;

        @BeforeEach
        void setUpDualBell() {
            nozzle = new DualBellNozzle(params, 2.0).generate();
        }

        @Test
        @DisplayName("exportDualBellReport creates all three expected files")
        void createsAllThreeFiles() throws IOException {
            Path reportDir = tempDir.resolve("dualbell_report");
            new CSVExporter().exportDualBellReport(nozzle, reportDir);

            assertThat(reportDir.resolve("dual_bell_design_parameters.csv")).exists();
            assertThat(reportDir.resolve("dual_bell_contour.csv")).exists();
            assertThat(reportDir.resolve("dual_bell_performance.csv")).exists();
        }

        @Test
        @DisplayName("exportDualBellReport creates the output directory if absent")
        void createsOutputDirectoryIfAbsent() throws IOException {
            Path reportDir = tempDir.resolve("nested/dualbell_report");
            assertThat(reportDir).doesNotExist();
            new CSVExporter().exportDualBellReport(nozzle, reportDir);
            assertThat(reportDir).isDirectory();
        }

        @Test
        @DisplayName("dual_bell_design_parameters.csv contains the standard header and throat_radius row")
        void designParametersContainsExpectedContent() throws IOException {
            Path reportDir = tempDir.resolve("dualbell_report_params");
            new CSVExporter().exportDualBellReport(nozzle, reportDir);

            String content = Files.readString(reportDir.resolve("dual_bell_design_parameters.csv"));
            assertThat(content).contains("parameter,value,unit");
            assertThat(content).contains("throat_radius");
        }

        @Test
        @DisplayName("dual_bell_contour.csv contains BASE and EXTENSION section labels")
        void contourContainsBothSectionLabels() throws IOException {
            Path reportDir = tempDir.resolve("dualbell_report_contour");
            new CSVExporter().exportDualBellReport(nozzle, reportDir);

            String content = Files.readString(reportDir.resolve("dual_bell_contour.csv"));
            assertThat(content).contains("BASE");
            assertThat(content).contains("EXTENSION");
        }

        @Test
        @DisplayName("dual_bell_performance.csv contains the standard header")
        void performanceContainsHeader() throws IOException {
            Path reportDir = tempDir.resolve("dualbell_report_perf_header");
            new CSVExporter().exportDualBellReport(nozzle, reportDir);

            assertThat(Files.readString(reportDir.resolve("dual_bell_performance.csv")))
                    .startsWith("parameter,value,unit");
        }

        @Test
        @DisplayName("dual_bell_performance.csv contains all twelve expected parameter rows")
        void performanceContainsAllParameters() throws IOException {
            Path reportDir = tempDir.resolve("dualbell_report_perf_rows");
            new CSVExporter().exportDualBellReport(nozzle, reportDir);

            String content = Files.readString(reportDir.resolve("dual_bell_performance.csv"));
            assertThat(content).contains("base_length");
            assertThat(content).contains("total_length");
            assertThat(content).contains("transition_radius");
            assertThat(content).contains("transition_mach");
            assertThat(content).contains("inflection_angle");
            assertThat(content).contains("base_exit_angle");
            assertThat(content).contains("extension_exit_angle");
            assertThat(content).contains("transition_pressure");
            assertThat(content).contains("sea_level_cf");
            assertThat(content).contains("high_altitude_cf");
            assertThat(content).contains("sea_level_isp");
            assertThat(content).contains("high_altitude_isp");
        }

        @Test
        @DisplayName("dual_bell_performance.csv performance values are physically plausible")
        void performanceValuesArePlausible() throws IOException {
            Path reportDir = tempDir.resolve("dualbell_report_perf_values");
            new CSVExporter().exportDualBellReport(nozzle, reportDir);

            // Parse all rows into a name→value map for spot-checking
            java.util.Map<String, Double> values = new java.util.HashMap<>();
            Files.readString(reportDir.resolve("dual_bell_performance.csv"))
                    .lines().skip(1).filter(l -> !l.isBlank()).forEach(line -> {
                        String[] parts = line.split(",");
                        if (parts.length >= 2) values.put(parts[0], Double.parseDouble(parts[1]));
                    });

            assertThat(values.get("base_length")).isGreaterThan(0.0);
            assertThat(values.get("total_length")).isGreaterThanOrEqualTo(values.get("base_length"));
            assertThat(values.get("transition_pressure")).isGreaterThan(0.0)
                    .isLessThan(params.chamberPressure());
            assertThat(values.get("sea_level_cf")).isGreaterThan(0.0).isLessThan(2.5);
            assertThat(values.get("high_altitude_cf")).isGreaterThan(values.get("sea_level_cf"));
            assertThat(values.get("high_altitude_isp")).isGreaterThan(values.get("sea_level_isp"));
        }
    }

    @Nested
    @DisplayName("DualBell Contour Export Tests")
    class DualBellContourExportTests {

        private DualBellNozzle nozzle;

        @BeforeEach
        void setUpDualBell() {
            nozzle = new DualBellNozzle(params, 2.0).generate();
        }

        @Test
        @DisplayName("exportDualBellContour creates the output file")
        void createsOutputFile() throws IOException {
            Path out = tempDir.resolve("dual_bell.csv");
            new CSVExporter().exportDualBellContour(nozzle, out);

            assertThat(out).exists();
            assertThat(out.toFile().length()).isGreaterThan(0L);
        }

        @Test
        @DisplayName("exportDualBellContour writes the correct header")
        void writesCorrectHeader() throws IOException {
            Path out = tempDir.resolve("dual_bell_header.csv");
            new CSVExporter().exportDualBellContour(nozzle, out);

            String firstLine = Files.readString(out).lines().findFirst().orElseThrow();
            assertThat(firstLine).isEqualTo("x_m,y_m,section");
        }

        @Test
        @DisplayName("exportDualBellContour writes one data row per contour point")
        void rowCountMatchesContourPoints() throws IOException {
            Path out = tempDir.resolve("dual_bell_rows.csv");
            new CSVExporter().exportDualBellContour(nozzle, out);

            long dataRows = Files.readString(out).lines().skip(1)
                    .filter(l -> !l.isBlank()).count();
            assertThat(dataRows).isEqualTo(nozzle.getContourPoints().size());
        }

        @Test
        @DisplayName("exportDualBellContour labels pre-kink rows as BASE")
        void preKinkRowsAreBase() throws IOException {
            Path out = tempDir.resolve("dual_bell_base.csv");
            new CSVExporter().exportDualBellContour(nozzle, out);

            // rows 1..kinkIdx+1 (1-based, skipping header) must all be BASE
            List<String> dataRows = Files.readString(out).lines().skip(1)
                    .filter(l -> !l.isBlank()).toList();
            int kinkIdx = nozzle.getKinkIndex();
            for (int i = 0; i <= kinkIdx; i++) {
                assertThat(dataRows.get(i)).as("row %d (kinkIdx=%d)", i, kinkIdx)
                        .endsWith(",BASE");
            }
        }

        @Test
        @DisplayName("exportDualBellContour labels post-kink rows as EXTENSION")
        void postKinkRowsAreExtension() throws IOException {
            Path out = tempDir.resolve("dual_bell_ext.csv");
            new CSVExporter().exportDualBellContour(nozzle, out);

            List<String> dataRows = Files.readString(out).lines().skip(1)
                    .filter(l -> !l.isBlank()).toList();
            int kinkIdx = nozzle.getKinkIndex();
            for (int i = kinkIdx + 1; i < dataRows.size(); i++) {
                assertThat(dataRows.get(i)).as("row %d (kinkIdx=%d)", i, kinkIdx)
                        .endsWith(",EXTENSION");
            }
        }

        @Test
        @DisplayName("exportDualBellContour labels the kink point itself as BASE")
        void kinkPointIsBASE() throws IOException {
            Path out = tempDir.resolve("dual_bell_kink.csv");
            new CSVExporter().exportDualBellContour(nozzle, out);

            List<String> dataRows = Files.readString(out).lines().skip(1)
                    .filter(l -> !l.isBlank()).toList();
            assertThat(dataRows.get(nozzle.getKinkIndex())).endsWith(",BASE");
        }

        @Test
        @DisplayName("exportDualBellContour x values are monotonically non-decreasing")
        void xValuesAreMonotonicallyNonDecreasing() throws IOException {
            Path out = tempDir.resolve("dual_bell_mono.csv");
            new CSVExporter().exportDualBellContour(nozzle, out);

            List<Double> xValues = Files.readString(out).lines().skip(1)
                    .filter(l -> !l.isBlank())
                    .map(l -> Double.parseDouble(l.split(",")[0]))
                    .toList();
            for (int i = 1; i < xValues.size(); i++) {
                assertThat(xValues.get(i)).isGreaterThanOrEqualTo(xValues.get(i - 1));
            }
        }
    }
}
