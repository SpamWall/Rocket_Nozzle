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
import com.nozzle.moc.AltitudePerformance;
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
}
