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

package com.nozzle.io;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.core.Point2D;
import com.nozzle.moc.CharacteristicNet;
import com.nozzle.moc.CharacteristicPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("NozzleSerializer Tests")
class NozzleSerializer_UT {

    private static final double DELTA = 1e-12;

    private static NozzleDesignParameters baseParams() {
        return NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500.0)
                .ambientPressure(101325.0)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(10)
                .wallAngleInitialDegrees(30.0)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();
    }

    // -----------------------------------------------------------------------
    // Round-trip: parameters only
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Parameters-Only Round-Trip Tests")
    class ParametersRoundTripTests {

        @Test
        @DisplayName("All numeric fields survive JSON round-trip without precision loss")
        void allNumericFieldsSurviveRoundTrip() {
            NozzleDesignParameters original = baseParams();

            String json = NozzleSerializer.toJson(NozzleSerializer.document(original));
            DesignDocument restored = NozzleSerializer.fromJson(json);
            NozzleDesignParameters p = restored.parameters();

            assertThat(p.throatRadius())       .isCloseTo(original.throatRadius(),       within(DELTA));
            assertThat(p.exitMach())           .isCloseTo(original.exitMach(),            within(DELTA));
            assertThat(p.chamberPressure())    .isCloseTo(original.chamberPressure(),     within(DELTA));
            assertThat(p.chamberTemperature()) .isCloseTo(original.chamberTemperature(),  within(DELTA));
            assertThat(p.ambientPressure())    .isCloseTo(original.ambientPressure(),     within(DELTA));
            assertThat(p.numberOfCharLines())  .isEqualTo(original.numberOfCharLines());
            assertThat(p.wallAngleInitial())   .isCloseTo(original.wallAngleInitial(),    within(DELTA));
            assertThat(p.lengthFraction())     .isCloseTo(original.lengthFraction(),      within(DELTA));
            assertThat(p.axisymmetric())       .isEqualTo(original.axisymmetric());
        }

        @Test
        @DisplayName("GasProperties fields survive JSON round-trip without precision loss")
        void gasPropertiesSurviveRoundTrip() {
            NozzleDesignParameters original = baseParams();
            GasProperties g = original.gasProperties();

            String json = NozzleSerializer.toJson(NozzleSerializer.document(original));
            GasProperties r = NozzleSerializer.fromJson(json).parameters().gasProperties();

            assertThat(r.gamma())           .isCloseTo(g.gamma(),           within(DELTA));
            assertThat(r.molecularWeight()) .isCloseTo(g.molecularWeight(), within(DELTA));
            assertThat(r.gasConstant())     .isCloseTo(g.gasConstant(),     within(DELTA));
            assertThat(r.viscosityRef())    .isCloseTo(g.viscosityRef(),     within(DELTA));
            assertThat(r.tempRef())         .isCloseTo(g.tempRef(),         within(DELTA));
            assertThat(r.sutherlandConst()) .isCloseTo(g.sutherlandConst(), within(DELTA));
        }

        @Test
        @DisplayName("Axisymmetric flag round-trips correctly for both true and false")
        void axisymmetricFlagRoundTrips() {
            NozzleDesignParameters axi   = baseParams();
            NozzleDesignParameters planar = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(2.0).chamberPressure(5e6)
                    .chamberTemperature(3000).ambientPressure(101325)
                    .gasProperties(GasProperties.AIR).numberOfCharLines(10)
                    .wallAngleInitialDegrees(25).lengthFraction(0.7).planar().build();

            String axiJson   = NozzleSerializer.toJson(NozzleSerializer.document(axi));
            String planarJson = NozzleSerializer.toJson(NozzleSerializer.document(planar));

            assertThat(NozzleSerializer.fromJson(axiJson).parameters().axisymmetric()).isTrue();
            assertThat(NozzleSerializer.fromJson(planarJson).parameters().axisymmetric()).isFalse();
        }

        @Test
        @DisplayName("Computed derived properties match original after round-trip")
        void derivedPropertiesMatchAfterRoundTrip() {
            NozzleDesignParameters original = baseParams();
            String json = NozzleSerializer.toJson(NozzleSerializer.document(original));
            NozzleDesignParameters restored = NozzleSerializer.fromJson(json).parameters();

            assertThat(restored.exitRadius())            .isCloseTo(original.exitRadius(),            within(1e-10));
            assertThat(restored.characteristicVelocity()).isCloseTo(original.characteristicVelocity(), within(1e-6));
            assertThat(restored.idealSpecificImpulse())  .isCloseTo(original.idealSpecificImpulse(),  within(1e-6));
        }

        @Test
        @DisplayName("Different gas presets all survive round-trip")
        void allGasPresetsSurviveRoundTrip() {
            for (GasProperties gas : List.of(
                    GasProperties.AIR,
                    GasProperties.HYDROGEN,
                    GasProperties.NITROGEN,
                    GasProperties.LOX_LH2_PRODUCTS,
                    GasProperties.LOX_RP1_PRODUCTS,
                    GasProperties.LOX_CH4_PRODUCTS)) {

                NozzleDesignParameters params = NozzleDesignParameters.builder()
                        .throatRadius(0.05).exitMach(3.0).chamberPressure(7e6)
                        .chamberTemperature(3500).ambientPressure(101325)
                        .gasProperties(gas).numberOfCharLines(10)
                        .wallAngleInitialDegrees(30).lengthFraction(0.8).build();

                String json = NozzleSerializer.toJson(NozzleSerializer.document(params));
                GasProperties r = NozzleSerializer.fromJson(json).parameters().gasProperties();

                assertThat(r.gamma()).as("gamma for %s", gas.gasConstant())
                        .isCloseTo(gas.gamma(), within(DELTA));
            }
        }
    }

    // -----------------------------------------------------------------------
    // Round-trip: full design with MOC and contour
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Full Design Round-Trip Tests")
    class FullDesignRoundTripTests {

        @Test
        @DisplayName("Wall-point count and coordinates survive round-trip")
        void wallPointsRoundTrip() {
            NozzleDesignParameters params = baseParams();
            CharacteristicNet net = new CharacteristicNet(params).generate();

            String json = NozzleSerializer.toJson(
                    NozzleSerializer.document(params, net,
                            new NozzleContour(NozzleContour.ContourType.RAO_BELL, params)));

            DesignDocument doc = NozzleSerializer.fromJson(json);

            assertThat(doc.wallPoints()).hasSize(net.getWallPoints().size());
            CharacteristicPoint orig = net.getWallPoints().getFirst();
            CharacteristicPoint rest = doc.wallPoints().getFirst();
            assertThat(rest.x())    .isCloseTo(orig.x(),    within(DELTA));
            assertThat(rest.y())    .isCloseTo(orig.y(),    within(DELTA));
            assertThat(rest.mach()) .isCloseTo(orig.mach(), within(DELTA));
        }

        @Test
        @DisplayName("CharacteristicPoint type enum survives round-trip")
        void characteristicPointTypeRoundTrips() {
            NozzleDesignParameters params = baseParams();
            CharacteristicNet net = new CharacteristicNet(params).generate();

            String json = NozzleSerializer.toJson(
                    NozzleSerializer.document(params, net,
                            new NozzleContour(NozzleContour.ContourType.RAO_BELL, params)));

            List<CharacteristicPoint> wall = NozzleSerializer.fromJson(json).wallPoints();
            assertThat(wall).allSatisfy(
                    pt -> assertThat(pt.pointType()).isNotNull());
        }

        @Test
        @DisplayName("MOC net row count and inner list sizes survive round-trip")
        void netPointsRoundTrip() {
            NozzleDesignParameters params = baseParams();
            CharacteristicNet net = new CharacteristicNet(params).generate();

            String json = NozzleSerializer.toJson(
                    NozzleSerializer.document(params, net,
                            new NozzleContour(NozzleContour.ContourType.RAO_BELL, params)));

            List<List<CharacteristicPoint>> restored = NozzleSerializer.fromJson(json).netPoints();

            assertThat(restored).hasSize(net.getNetPoints().size());
            for (int i = 0; i < restored.size(); i++) {
                assertThat(restored.get(i)).hasSize(net.getNetPoints().get(i).size());
            }
        }

        @Test
        @DisplayName("Contour-point count and coordinates survive round-trip")
        void contourPointsRoundTrip() {
            NozzleDesignParameters params = baseParams();
            NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
            contour.generate(50);

            String json = NozzleSerializer.toJson(
                    NozzleSerializer.document(params, new CharacteristicNet(params).generate(), contour));

            List<Point2D> restored = NozzleSerializer.fromJson(json).contourPoints();

            assertThat(restored).hasSize(contour.getContourPoints().size());
            Point2D first = contour.getContourPoints().getFirst();
            assertThat(restored.getFirst().x()).isCloseTo(first.x(), within(DELTA));
            assertThat(restored.getFirst().y()).isCloseTo(first.y(), within(DELTA));
        }
    }

    // -----------------------------------------------------------------------
    // File I/O
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("File I/O Tests")
    class FileIOTests {

        @Test
        @DisplayName("save() writes a non-empty file; load() restores the document")
        void saveAndLoadRoundTrip(@TempDir Path tempDir) {
            Path file = tempDir.resolve("design.json");
            NozzleDesignParameters original = baseParams();

            NozzleSerializer.save(NozzleSerializer.document(original), file);

            assertThat(Files.exists(file)).isTrue();
            assertThat(file.toFile().length()).isGreaterThan(0L);

            DesignDocument restored = NozzleSerializer.load(file);
            assertThat(restored.parameters().throatRadius())
                    .isCloseTo(original.throatRadius(), within(DELTA));
        }

        @Test
        @DisplayName("Saved JSON is human-readable (pretty-printed, contains field names)")
        void savedJsonIsPrettyPrinted(@TempDir Path tempDir) throws Exception {
            Path file = tempDir.resolve("pretty.json");
            NozzleSerializer.save(NozzleSerializer.document(baseParams()), file);

            String content = Files.readString(file);
            assertThat(content).contains("throatRadius");
            assertThat(content).contains("gasProperties");
            assertThat(content).contains("\n");   // pretty-printed, not a single line
        }

        @Test
        @DisplayName("load() throws UncheckedIOException for a missing file")
        void loadThrowsForMissingFile(@TempDir Path tempDir) {
            Path missing = tempDir.resolve("does_not_exist.json");
            assertThatThrownBy(() -> NozzleSerializer.load(missing))
                    .isInstanceOf(UncheckedIOException.class);
        }

        @Test
        @DisplayName("fromJson() throws UncheckedIOException for malformed JSON")
        void fromJsonThrowsForMalformedInput() {
            assertThatThrownBy(() -> NozzleSerializer.fromJson("{not valid json"))
                    .isInstanceOf(UncheckedIOException.class);
        }
    }

    // -----------------------------------------------------------------------
    // DesignDocument metadata
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("DesignDocument Metadata Tests")
    class MetadataTests {

        @Test
        @DisplayName("document() sets schema version to NozzleSerializer.SCHEMA_VERSION")
        void documentHasCurrentSchemaVersion() {
            DesignDocument doc = NozzleSerializer.document(baseParams());
            assertThat(doc.version()).isEqualTo(NozzleSerializer.SCHEMA_VERSION);
        }

        @Test
        @DisplayName("document() populates createdAt with a non-blank ISO-8601 timestamp")
        void documentHasNonBlankTimestamp() {
            DesignDocument doc = NozzleSerializer.document(baseParams());
            assertThat(doc.createdAt()).isNotBlank();
            // Verify it parses as an Instant (throws if not ISO-8601)
            assertThatCode(() -> java.time.Instant.parse(doc.createdAt())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("parameters-only document has empty computed lists")
        void parametersOnlyDocumentHasEmptyLists() {
            DesignDocument doc = NozzleSerializer.document(baseParams());
            assertThat(doc.wallPoints())    .isEmpty();
            assertThat(doc.netPoints())     .isEmpty();
            assertThat(doc.contourPoints()) .isEmpty();
        }

        @Test
        @DisplayName("Version field survives round-trip")
        void versionSurvivesRoundTrip() {
            String json = NozzleSerializer.toJson(NozzleSerializer.document(baseParams()));
            assertThat(NozzleSerializer.fromJson(json).version())
                    .isEqualTo(NozzleSerializer.SCHEMA_VERSION);
        }
    }
}
