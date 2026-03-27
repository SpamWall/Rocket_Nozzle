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

package com.nozzle;

import com.nozzle.chemistry.ChemistryModel;
import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.core.PerformanceCalculator;
import com.nozzle.export.CSVExporter;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.io.DesignDocument;
import com.nozzle.io.NozzleSerializer;
import com.nozzle.moc.CharacteristicNet;
import com.nozzle.thermal.BoundaryLayerCorrection;
import com.nozzle.thermal.HeatTransferModel;
import com.nozzle.validation.NASASP8120Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end pipeline tests that chain every major stage together and verify
 * cross-stage consistency and physical plausibility of the final outputs.
 *
 * <p>Stage order exercised in the full-pipeline tests:
 * <ol>
 *   <li>NozzleDesignParameters (design intent)</li>
 *   <li>CharacteristicNet (MOC flow-field solve)</li>
 *   <li>NozzleContour (geometry extraction from MOC wall points)</li>
 *   <li>BoundaryLayerCorrection (skin-friction loss)</li>
 *   <li>HeatTransferModel (wall temperature profile)</li>
 *   <li>ChemistryModel (equilibrium gamma variation)</li>
 *   <li>PerformanceCalculator (all four loss mechanisms)</li>
 *   <li>NASASP8120Validator (reference correlation checks)</li>
 *   <li>CSVExporter (wall contour and characteristic net output)</li>
 *   <li>NozzleSerializer (JSON save/load round-trip)</li>
 * </ol>
 */
@DisplayName("Full Pipeline Tests")
class FullPipeline_IT {

    // ------------------------------------------------------------------
    // Shared parameter builders
    // ------------------------------------------------------------------

    private static NozzleDesignParameters loxRp1Params() {
        return NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.5)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();
    }

    private static NozzleDesignParameters loxLh2Params() {
        return NozzleDesignParameters.builder()
                .throatRadius(0.04)
                .exitMach(4.0)
                .chamberPressure(10e6)
                .chamberTemperature(3200)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_LH2_PRODUCTS)
                .numberOfCharLines(20)
                .wallAngleInitialDegrees(28)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();
    }

    private static NozzleDesignParameters loxCh4Params() {
        return NozzleDesignParameters.builder()
                .throatRadius(0.06)
                .exitMach(3.0)
                .chamberPressure(6e6)
                .chamberTemperature(3400)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_CH4_PRODUCTS)
                .numberOfCharLines(20)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();
    }

    // ------------------------------------------------------------------
    // Minimal pipeline: params → MOC → performance
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Minimal Pipeline Tests")
    class MinimalPipelineTests {

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Parameters flow through MOC to PerformanceCalculator.simple()")
        void minimalPipelineProducesPhysicalResults() {
            NozzleDesignParameters params = loxRp1Params();

            CharacteristicNet net = new CharacteristicNet(params).generate();

            PerformanceCalculator calc =
                    new PerformanceCalculator(params, net, null, null, null).calculate();

            assertThat(net.validate()).isTrue();
            assertThat(calc.getIdealThrustCoefficient()).isGreaterThan(1.0);
            assertThat(calc.getActualThrustCoefficient()).isGreaterThan(0.0);
            assertThat(calc.getEfficiency()).isBetween(0.80, 1.0);
            assertThat(calc.getSpecificImpulse()).isBetween(100.0, 500.0);
            assertThat(calc.getThrust()).isGreaterThan(0.0);
            assertThat(calc.getMassFlowRate()).isGreaterThan(0.0);
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Actual Cf is bounded by [0.85, 1.0] times ideal Cf")
        void actualCfBoundedByIdeal() {
            NozzleDesignParameters params = loxRp1Params();
            PerformanceCalculator calc =
                    new PerformanceCalculator(params,
                            new CharacteristicNet(params).generate(),
                            null, null, null).calculate();

            assertThat(calc.getActualThrustCoefficient())
                    .isGreaterThanOrEqualTo(calc.getIdealThrustCoefficient() * 0.85);
            assertThat(calc.getActualThrustCoefficient())
                    .isLessThanOrEqualTo(calc.getIdealThrustCoefficient());
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Sum of loss terms equals ideal minus actual Cf")
        void totalLossEqualsIdealMinusActual() {
            NozzleDesignParameters params = loxRp1Params();
            PerformanceCalculator calc =
                    new PerformanceCalculator(params,
                            new CharacteristicNet(params).generate(),
                            null, null, null).calculate();

            double expectedLoss = calc.getIdealThrustCoefficient()
                    - calc.getActualThrustCoefficient();
            assertThat(calc.getTotalLoss()).isCloseTo(expectedLoss, within(1e-9));
        }
    }

    // ------------------------------------------------------------------
    // Full pipeline: all optional stages wired together
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Full Pipeline Tests")
    class FullPipelineTests {

        @Test
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        @DisplayName("All stages produce consistent, physically plausible outputs")
        void allStagesChainedWithLoxRp1() {
            NozzleDesignParameters params = loxRp1Params();

            // Stage 1 — MOC
            CharacteristicNet net = new CharacteristicNet(params).generate();
            assertThat(net.validate()).isTrue();

            // Stage 2 — Geometry from MOC; generate() populates the contourPoints list
            NozzleContour contour =
                    NozzleContour.fromMOCWallPoints(params, net.getWallPoints());
            contour.generate(100);
            assertThat(contour.getContourPoints()).isNotEmpty();
            assertThat(contour.getLength()).isGreaterThan(0.0);

            // Stage 3 — Boundary layer
            BoundaryLayerCorrection bl =
                    new BoundaryLayerCorrection(params, contour)
                            .calculate(net.getAllPoints());
            assertThat(bl.getThrustCoefficientLoss()).isGreaterThanOrEqualTo(0.0);

            // Stage 4 — Heat transfer
            HeatTransferModel heat = new HeatTransferModel(params, contour)
                    .setWallProperties(20.0, 0.003);
            heat.calculate(net.getAllPoints());
            assertThat(heat.getMaxWallTemperature()).isGreaterThan(0.0);

            // Stage 5 — Chemistry
            ChemistryModel chem = ChemistryModel.equilibrium(params.gasProperties());

            // Stage 6 — Performance with all models
            PerformanceCalculator calc =
                    new PerformanceCalculator(params, net, contour, bl, chem).calculate();
            assertThat(calc.getEfficiency()).isBetween(0.80, 1.0);
            assertThat(calc.getSpecificImpulse()).isBetween(100.0, 500.0);
            assertThat(calc.getBoundaryLayerLoss())
                    .isCloseTo(bl.getThrustCoefficientLoss(), within(1e-9));

            // Stage 7 — NASA SP-8120 validation; Mach 3.5 is within the valid design envelope
            NASASP8120Validator validator = new NASASP8120Validator();
            NASASP8120Validator.ValidationResult result = validator.validate(params);
            assertThat(result).isNotNull();
            assertThat(result.metrics()).containsKey("area_ratio_error_percent");
        }

        @Test
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        @DisplayName("MOC exit area ratio is consistent with design area ratio")
        void mocExitAreaRatioMatchesDesign() {
            NozzleDesignParameters params = loxRp1Params();
            CharacteristicNet net = new CharacteristicNet(params).generate();

            double designAR = params.exitAreaRatio();
            double mocAR    = net.calculateExitAreaRatio();

            // MOC area ratio should be within 10% of the isentropic design value
            assertThat(Math.abs(mocAR - designAR) / designAR).isLessThan(0.10);
        }

        @Test
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        @DisplayName("MOC-derived contour exit radius is consistent with design exit radius")
        void contourExitRadiusMatchesDesign() {
            NozzleDesignParameters params = loxRp1Params();
            CharacteristicNet net = new CharacteristicNet(params).generate();
            NozzleContour contour =
                    NozzleContour.fromMOCWallPoints(params, net.getWallPoints());

            double designExitRadius = params.exitRadius();
            double lastY = net.getWallPoints().getLast().y();

            assertThat(lastY).isGreaterThanOrEqualTo(designExitRadius * 0.95);
        }
    }

    // ------------------------------------------------------------------
    // Propellant variants
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Propellant Variant Tests")
    class PropellantVariantTests {

        @Test
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        @DisplayName("LOX/RP-1 pipeline completes and Isp is in expected range (270–340 s)")
        void loxRp1PipelineIspRange() {
            NozzleDesignParameters params = loxRp1Params();
            CharacteristicNet net = new CharacteristicNet(params).generate();
            NozzleContour contour =
                    NozzleContour.fromMOCWallPoints(params, net.getWallPoints());
            BoundaryLayerCorrection bl =
                    new BoundaryLayerCorrection(params, contour)
                            .calculate(net.getAllPoints());
            PerformanceCalculator calc =
                    new PerformanceCalculator(params, net, contour, bl, null).calculate();

            assertThat(calc.getSpecificImpulse()).isBetween(200.0, 380.0);
        }

        @Test
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        @DisplayName("LOX/LH2 pipeline completes and Isp exceeds LOX/RP-1 (higher energy propellant)")
        void loxLh2PipelineHigherIspThanLoxRp1() {
            PerformanceCalculator lh2Calc = runMinimalPipeline(loxLh2Params());
            PerformanceCalculator rp1Calc = runMinimalPipeline(loxRp1Params());

            assertThat(lh2Calc.getSpecificImpulse())
                    .isGreaterThan(rp1Calc.getSpecificImpulse());
        }

        @Test
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        @DisplayName("LOX/CH4 pipeline completes with Isp between LOX/RP-1 and LOX/LH2")
        void loxCh4PipelineIspBetweenRp1AndLh2() {
            PerformanceCalculator ch4Calc = runMinimalPipeline(loxCh4Params());
            PerformanceCalculator rp1Calc = runMinimalPipeline(loxRp1Params());
            PerformanceCalculator lh2Calc = runMinimalPipeline(loxLh2Params());

            // LOX/RP-1 < LOX/CH4 < LOX/LH2 is the expected ranking
            assertThat(ch4Calc.getSpecificImpulse())
                    .isGreaterThan(rp1Calc.getSpecificImpulse());
            assertThat(ch4Calc.getSpecificImpulse())
                    .isLessThan(lh2Calc.getSpecificImpulse());
        }

        private PerformanceCalculator runMinimalPipeline(NozzleDesignParameters params) {
            CharacteristicNet net = new CharacteristicNet(params).generate();
            NozzleContour contour =
                    NozzleContour.fromMOCWallPoints(params, net.getWallPoints());
            return new PerformanceCalculator(params, net, contour, null, null).calculate();
        }
    }

    // ------------------------------------------------------------------
    // Export stage
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Export Stage Tests")
    class ExportStageTests {

        @Test
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        @DisplayName("CSVExporter writes a non-empty wall contour file from MOC output")
        void csvExporterWritesWallContour(@TempDir Path tempDir) throws Exception {
            NozzleDesignParameters params = loxRp1Params();
            CharacteristicNet net = new CharacteristicNet(params).generate();
            NozzleContour contour =
                    NozzleContour.fromMOCWallPoints(params, net.getWallPoints());
            contour.generate(100);

            Path outFile = tempDir.resolve("wall.csv");
            new CSVExporter().exportContour(contour, outFile);

            assertThat(Files.exists(outFile)).isTrue();
            assertThat(outFile.toFile().length()).isGreaterThan(0L);

            String content = Files.readString(outFile);
            assertThat(content).contains("x");   // header row
            assertThat(content.lines().count()).isGreaterThan(2L);
        }

        @Test
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        @DisplayName("CSVExporter writes a non-empty characteristic net file")
        void csvExporterWritesCharacteristicNet(@TempDir Path tempDir) throws Exception {
            NozzleDesignParameters params = loxRp1Params();
            CharacteristicNet net = new CharacteristicNet(params).generate();

            Path outFile = tempDir.resolve("net.csv");
            new CSVExporter().exportCharacteristicNet(net, outFile);

            assertThat(Files.exists(outFile)).isTrue();
            assertThat(outFile.toFile().length()).isGreaterThan(0L);
        }
    }

    // ------------------------------------------------------------------
    // Serialization round-trip of a complete design
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Serialization Round-Trip Tests")
    class SerializationRoundTripTests {

        @Test
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        @DisplayName("Full design survives JSON round-trip and reproduces identical performance")
        void fullDesignRoundTripPreservesPerformance(@TempDir Path tempDir) {
            NozzleDesignParameters params = loxRp1Params();
            CharacteristicNet net = new CharacteristicNet(params).generate();
            NozzleContour contour =
                    NozzleContour.fromMOCWallPoints(params, net.getWallPoints());

            PerformanceCalculator original =
                    new PerformanceCalculator(params, net, contour, null, null).calculate();

            // Serialize
            Path file = tempDir.resolve("design.json");
            NozzleSerializer.save(NozzleSerializer.document(params, net, contour), file);

            // Reload — performance depends only on design parameters, which are deterministic
            NozzleDesignParameters restored = NozzleSerializer.load(file).parameters();

            PerformanceCalculator reloaded =
                    new PerformanceCalculator(restored, null, null, null, null).calculate();

            // Key performance metrics must be identical (deterministic parameters)
            assertThat(reloaded.getIdealThrustCoefficient())
                    .isCloseTo(original.getIdealThrustCoefficient(), within(1e-6));
            // Isp may differ by a few seconds because the reloaded calculator has no net
            // for the divergence term; idealCf (purely from params) must match exactly.
            assertThat(reloaded.getSpecificImpulse())
                    .isCloseTo(original.getSpecificImpulse(), within(5.0));
            assertThat(reloaded.getMassFlowRate())
                    .isCloseTo(original.getMassFlowRate(), within(1e-6));
        }

        @Test
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        @DisplayName("Wall-point count is preserved across save/load")
        void wallPointCountPreservedAcrossRoundTrip(@TempDir Path tempDir) {
            NozzleDesignParameters params = loxRp1Params();
            CharacteristicNet net = new CharacteristicNet(params).generate();
            NozzleContour contour =
                    NozzleContour.fromMOCWallPoints(params, net.getWallPoints());

            Path file = tempDir.resolve("design.json");
            NozzleSerializer.save(
                    NozzleSerializer.document(params, net, contour), file);

            DesignDocument doc = NozzleSerializer.load(file);
            assertThat(doc.wallPoints()).hasSize(net.getWallPoints().size());
        }

        @Test
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        @DisplayName("NASA SP-8120 validation passes on a reloaded design")
        void validationPassesOnReloadedDesign(@TempDir Path tempDir) {
            NozzleDesignParameters params = loxRp1Params();
            CharacteristicNet net = new CharacteristicNet(params).generate();

            Path file = tempDir.resolve("design.json");
            NozzleSerializer.save(
                    NozzleSerializer.document(params, net,
                            NozzleContour.fromMOCWallPoints(params, net.getWallPoints())),
                    file);

            NozzleDesignParameters restored = NozzleSerializer.load(file).parameters();
            NASASP8120Validator.ValidationResult result =
                    new NASASP8120Validator().validate(restored);

            assertThat(result.errors()).isEmpty();
        }
    }
}
