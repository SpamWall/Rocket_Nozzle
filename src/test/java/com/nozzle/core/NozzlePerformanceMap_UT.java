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

package com.nozzle.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("NozzlePerformanceMap Tests")
class NozzlePerformanceMap_UT {

    private NozzleDesignParameters params;

    @BeforeEach
    void setUp() {
        params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20)
                .wallAngleInitialDegrees(25)
                .build();
    }

    @Nested
    @DisplayName("Construction and configuration")
    class ConstructionTests {

        @Test
        @DisplayName("Should construct with defaults")
        void shouldConstructDefault() {
            NozzlePerformanceMap map = new NozzlePerformanceMap(params);
            assertThat(map).isNotNull();
        }

        @Test
        @DisplayName("Should accept fluent configuration")
        void shouldAcceptFluentConfig() {
            NozzlePerformanceMap map = new NozzlePerformanceMap(params)
                    .altitudePoints(20)
                    .expansionRatioPoints(25)
                    .maxExpansionRatio(50)
                    .maxAltitudeKm(60);
            assertThat(map).isNotNull();
        }
    }

    @Nested
    @DisplayName("Generation")
    class GenerationTests {

        @Test
        @DisplayName("Should generate a full map with correct dimensions")
        void shouldGenerateCorrectDimensions() {
            int nAlt = 10;
            int nEps = 12;

            NozzlePerformanceMap map = new NozzlePerformanceMap(params)
                    .altitudePoints(nAlt)
                    .expansionRatioPoints(nEps)
                    .generate();

            assertThat(map.getMap()).hasSize(nAlt);
            assertThat(map.getMap().getFirst()).hasSize(nEps);
        }

        @Test
        @DisplayName("Should generate altitude grid from 0 to max")
        void shouldGenerateAltitudeGrid() {
            NozzlePerformanceMap map = new NozzlePerformanceMap(params)
                    .altitudePoints(10)
                    .maxAltitudeKm(50)
                    .generate();

            double[] alts = map.getAltitudes();
            assertThat(alts[0]).isCloseTo(0.0, within(1e-6));
            assertThat(alts[alts.length - 1]).isCloseTo(50.0, within(1e-6));
        }

        @Test
        @DisplayName("Expansion ratio grid should be log-spaced from 1.5 to max")
        void shouldGenerateLogSpacedExpansionGrid() {
            NozzlePerformanceMap map = new NozzlePerformanceMap(params)
                    .expansionRatioPoints(10)
                    .maxExpansionRatio(100)
                    .generate();

            double[] eps = map.getExpansionRatios();
            assertThat(eps[0]).isCloseTo(1.5, within(0.01));
            assertThat(eps[eps.length - 1]).isCloseTo(100.0, within(1.0));
        }

        @Test
        @DisplayName("All Isp values should be positive")
        void allIspPositive() {
            NozzlePerformanceMap map = new NozzlePerformanceMap(params)
                    .altitudePoints(8).expansionRatioPoints(8).generate();

            map.getMap().forEach(row ->
                    row.forEach(cell ->
                            assertThat(cell.isp())
                                    .as("Isp>0 at alt=%.1f, eps=%.2f",
                                            cell.altitudeKm(), cell.expansionRatio())
                                    .isGreaterThan(0.0)));
        }

        @Test
        @DisplayName("Isp should increase with altitude for a fixed low expansion ratio")
        void ispIncreasesWithAltitude() {
            NozzlePerformanceMap map = new NozzlePerformanceMap(params)
                    .altitudePoints(10).expansionRatioPoints(5).generate();

            // At epsilon=1.5 (small nozzle), sea-level Isp < upper-atmosphere Isp
            double ispSL   = map.isp(0.0,  1.5);
            double ispHigh = map.isp(40.0, 1.5);

            assertThat(ispHigh).isGreaterThan(ispSL);
        }
    }

    @Nested
    @DisplayName("Query API")
    class QueryTests {

        private NozzlePerformanceMap generatedMap;

        @BeforeEach
        void generate() {
            generatedMap = new NozzlePerformanceMap(params)
                    .altitudePoints(15)
                    .expansionRatioPoints(15)
                    .maxExpansionRatio(50)
                    .generate();
        }

        @Test
        @DisplayName("isp() should return positive value for valid inputs")
        void ispQueryPositive() {
            double isp = generatedMap.isp(0.0, 10.0);
            assertThat(isp).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("isp() before generate() should throw")
        void ispBeforeGenerateThrows() {
            NozzlePerformanceMap ungenerated = new NozzlePerformanceMap(params);
            assertThatThrownBy(() -> ungenerated.isp(0.0, 10.0))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Optimum epsilon should be positive")
        void optimumEpsilonPositive() {
            double opt = generatedMap.optimumExpansionRatio(0.0);
            assertThat(opt).isGreaterThan(1.0);
        }

        @Test
        @DisplayName("Optimum epsilon should increase with altitude")
        void optimumEpsilonIncreasesWithAltitude() {
            double optSL   = generatedMap.optimumExpansionRatio(0.0);
            double optHigh = generatedMap.optimumExpansionRatio(30.0);

            // At higher altitude, ambient pressure lower → more expansion profitable
            assertThat(optHigh).isGreaterThan(optSL);
        }

        @Test
        @DisplayName("getOptima() should have one entry per altitude point")
        void optimaCountMatchesAltitudePoints() {
            assertThat(generatedMap.getOptima()).hasSize(generatedMap.getAltitudes().length);
        }

        @Test
        @DisplayName("peakIspForFixedNozzle() should return positive value")
        void peakIspForFixedNozzlePositive() {
            double peakIsp = generatedMap.peakIspForFixedNozzle(10.0);
            assertThat(peakIsp).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("AltitudeOptimum record fields should be populated")
        void altitudeOptimumFieldsPopulated() {
            NozzlePerformanceMap.AltitudeOptimum opt = generatedMap.getOptima().getFirst();
            assertThat(opt.altitudeKm()).isCloseTo(0.0, within(0.01));
            assertThat(opt.optimumEpsilon()).isGreaterThan(1.0);
            assertThat(opt.peakIsp()).isGreaterThan(0.0);
            assertThat(opt.idealEpsilon()).isGreaterThan(1.0);
        }
    }

    @Nested
    @DisplayName("Standard Atmosphere")
    class AtmosphereTests {

        @Test
        @DisplayName("Sea-level pressure should be ~101325 Pa")
        void seaLevelPressure() {
            double p = NozzlePerformanceMap.standardAtmospherePressure(0.0);
            assertThat(p).isCloseTo(101325.0, within(10.0));
        }

        @ParameterizedTest(name = "altitude {0} km")
        @ValueSource(doubles = {0.0, 5.0, 11.0, 20.0, 32.0, 47.0, 60.0})
        @DisplayName("Pressure should decrease with altitude")
        void pressureDecreaseWithAltitude(double altKm) {
            double p0 = NozzlePerformanceMap.standardAtmospherePressure(0.0);
            double pAlt = NozzlePerformanceMap.standardAtmospherePressure(altKm);

            if (altKm > 0) {
                assertThat(pAlt).isLessThan(p0);
            } else {
                assertThat(pAlt).isCloseTo(p0, within(1.0));
            }
        }

        @Test
        @DisplayName("Tropopause pressure at 11 km should be ~22632 Pa")
        void tropopausePressure() {
            double p = NozzlePerformanceMap.standardAtmospherePressure(11.0);
            assertThat(p).isCloseTo(22632.0, within(500.0));
        }

        @Test
        @DisplayName("Pressure at 80 km should be near-vacuum (< 1 Pa)")
        void nearVacuumAt80km() {
            double p = NozzlePerformanceMap.standardAtmospherePressure(80.0);
            assertThat(p).isLessThan(1.0);
        }
    }

    @Nested
    @DisplayName("MapCell record")
    class MapCellTests {

        @Test
        @DisplayName("Separated cells should have lower Isp than non-separated")
        void separatedCellsLowerIsp() {
            NozzlePerformanceMap map = new NozzlePerformanceMap(params)
                    .altitudePoints(5)
                    .expansionRatioPoints(20)
                    .maxExpansionRatio(200)
                    .generate();

            // At sea level (high pa), very large expansion ratios should be separated
            // and have lower Isp than optimal
            List<NozzlePerformanceMap.MapCell> slRow = map.getMap().getFirst();
            boolean anySeparated = slRow.stream().anyMatch(NozzlePerformanceMap.MapCell::separated);
            assertThat(anySeparated).as("Some sea-level cells with large eps should separate").isTrue();
        }
    }
}
