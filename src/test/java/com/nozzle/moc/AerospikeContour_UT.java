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

package com.nozzle.moc;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.core.Point2D;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the spike contour algorithm implemented in {@link AerospikeContour}
 * and exercised through the public {@link AerospikeNozzle} API.
 */
@DisplayName("AerospikeContour Tests")
class AerospikeContour_UT {

    private NozzleDesignParameters params;

    @BeforeEach
    void setUp() {
        params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(2.5)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.AIR)
                .numberOfCharLines(15)
                .wallAngleInitialDegrees(25)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();
    }

    // -----------------------------------------------------------------------
    // Geometry
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Spike Contour Geometry Tests")
    class SpikeContourGeometryTests {

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Full spike contour starts at throat inner radius")
        void spikeStartsAtInnerThroatRadius() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params).generate();
            List<Point2D> contour = nozzle.getFullSpikeContour();

            double ri = params.throatRadius() * AerospikeNozzle.DEFAULT_SPIKE_RADIUS_RATIO;
            assertThat(contour.getFirst().x()).isCloseTo(0.0, within(1e-12));
            assertThat(contour.getFirst().y()).isCloseTo(ri, within(1e-12));
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Spike tip (last point) has smaller radius than throat inner radius")
        void spikeTipRadiusSmallerThanThroatInner() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params).generate();
            List<Point2D> contour = nozzle.getFullSpikeContour();

            double ri = params.throatRadius() * AerospikeNozzle.DEFAULT_SPIKE_RADIUS_RATIO;
            assertThat(contour.getLast().y()).isLessThan(ri);
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Spike contour x-coordinates are monotonically non-decreasing")
        void spikeXMonotonicallyIncreasing() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params).generate();
            List<Point2D> contour = nozzle.getFullSpikeContour();

            for (int i = 1; i < contour.size(); i++) {
                assertThat(contour.get(i).x())
                        .as("x[%d] >= x[%d]", i, i - 1)
                        .isGreaterThanOrEqualTo(contour.get(i - 1).x());
            }
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Spike radius decreases monotonically from throat to tip")
        void spikeRadiusMonotonicallyDecreasing() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params).generate();
            List<Point2D> contour = nozzle.getFullSpikeContour();

            for (int i = 1; i < contour.size(); i++) {
                assertThat(contour.get(i).y())
                        .as("r[%d] <= r[%d]", i, i - 1)
                        .isLessThanOrEqualTo(contour.get(i - 1).y());
            }
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Full spike contour has numSpikePoints + 1 points (including origin)")
        void spikeContourPointCount() {
            int n = 50;
            AerospikeNozzle nozzle = new AerospikeNozzle(params, 0.6, 0.8, n).generate();
            assertThat(nozzle.getFullSpikeContour()).hasSize(n + 1);
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("getFullSpikeContour() is unmodifiable")
        void fullSpikeContourIsUnmodifiable() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params).generate();
            List<Point2D> contour = nozzle.getFullSpikeContour();
            assertThatThrownBy(contour::clear)
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("getTruncatedSpikeContour() is unmodifiable")
        void truncatedSpikeContourIsUnmodifiable() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params).generate();
            List<Point2D> truncated = nozzle.getTruncatedSpikeContour();
            assertThatThrownBy(truncated::clear)
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Truncated spike is shorter than full spike")
        void truncatedSpikeIsShorterThanFull() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params, 0.6, 0.7, 100).generate();

            assertThat(nozzle.getTruncatedLength())
                    .isLessThan(nozzle.getFullSpikeLength());
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Full spike (truncationFraction=1) truncated length equals full length")
        void fullSpikeNotTruncated() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params, 0.6, 1.0, 100).generate();

            assertThat(nozzle.getTruncatedLength())
                    .isCloseTo(nozzle.getFullSpikeLength(), within(1e-12));
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Truncated base radius is less than inner throat radius")
        void truncatedBaseRadiusBelowInnerThroat() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params, 0.6, 0.5, 100).generate();
            double ri = params.throatRadius() * 0.6;
            assertThat(nozzle.getTruncatedBaseRadius()).isLessThanOrEqualTo(ri);
        }
    }

    // -----------------------------------------------------------------------
    // Branch coverage
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Branch Coverage Tests")
    class BranchCoverageTests {

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Truncated contour is non-empty even when truncation fraction is very small")
        void truncatedContourNonEmptyForSmallFraction() {
            // The first point is always at x=0 â‰¤ cutX â‰¥ 0, so truncated is never empty.
            AerospikeNozzle nozzle = new AerospikeNozzle(params, 0.6, 0.01, 100).generate();
            assertThat(nozzle.getTruncatedSpikeContour()).isNotEmpty();
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Very fine mesh (numSpikePoints=500) converges without error")
        void fineMeshGeneratesSuccessfully() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params, 0.6, 0.8, 500).generate();
            assertThat(nozzle.getFullSpikeContour()).hasSize(501);
            assertThat(nozzle.getFullSpikeLength()).isGreaterThan(0.0);
        }
    }

    // -----------------------------------------------------------------------
    // Direct AerospikeContour tests (package-private access)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Direct AerospikeContour Tests")
    class DirectAerospikeContourTests {

        @Test
        @DisplayName("getFullSpikeLength() returns 0 before generate() is called")
        void fullSpikeLengthZeroBeforeGenerate() {
            AerospikeContour contour = new AerospikeContour(params, 0.6, 0.8, 10);
            assertThat(contour.getFullSpikeLength()).isCloseTo(0.0, within(1e-12));
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("getFullSpikeContour() triggers lazy generation when contour is empty")
        void lazyGenerationOnGetFullSpikeContour() {
            AerospikeContour contour = new AerospikeContour(params, 0.6, 0.8, 10);
            List<Point2D> result = contour.getFullSpikeContour();
            assertThat(result).hasSize(11); // numSpikePoints + 1
            assertThat(contour.getFullSpikeLength()).isGreaterThan(0.0);
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("generate() clears and rebuilds the contour on a second call")
        void generateTwiceProducesSameContour() {
            AerospikeContour contour = new AerospikeContour(params, 0.6, 0.8, 10);
            contour.generate();
            double firstLength = contour.getFullSpikeLength();
            int firstSize = contour.getFullSpikeContour().size();

            contour.generate();

            assertThat(contour.getFullSpikeContour()).hasSize(firstSize);
            assertThat(contour.getFullSpikeLength()).isCloseTo(firstLength, within(1e-12));
        }

        @Test
        @DisplayName("getAnnularThroatArea() equals Ï€(rtÂ² âˆ’ riÂ²)")
        void annularThroatAreaFormula() {
            double rt = params.throatRadius();
            double ri = rt * 0.6;
            AerospikeContour contour = new AerospikeContour(params, 0.6, 0.8, 10);
            assertThat(contour.getAnnularThroatArea())
                    .isCloseTo(Math.PI * (rt * rt - ri * ri), within(1e-12));
        }

        @Test
        @DisplayName("getAnnularExitArea() equals annular throat area Ã— design area ratio")
        void annularExitAreaEqualsThroatTimesAreaRatio() {
            AerospikeContour contour = new AerospikeContour(params, 0.6, 0.8, 10);
            double expected = contour.getAnnularThroatArea() * params.exitAreaRatio();
            assertThat(contour.getAnnularExitArea()).isCloseTo(expected, within(1e-12));
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("getTruncatedSpikeContour() with truncationFraction=1 includes all points")
        void truncationFractionOneIncludesAllPoints() {
            AerospikeContour contour = new AerospikeContour(params, 0.6, 1.0, 20).generate();
            assertThat(contour.getTruncatedSpikeContour())
                    .hasSize(contour.getFullSpikeContour().size());
        }

        @Test
        @DisplayName("getParameters() returns the parameters supplied at construction")
        void getParametersReturnsSame() {
            AerospikeContour contour = new AerospikeContour(params, 0.6, 0.8, 10);
            assertThat(contour.getParameters()).isSameAs(params);
        }

        @Test
        @DisplayName("getSpikeRadiusRatio() returns the value supplied at construction")
        void getSpikeRadiusRatioReturnsSame() {
            AerospikeContour contour = new AerospikeContour(params, 0.55, 0.8, 10);
            assertThat(contour.getSpikeRadiusRatio()).isCloseTo(0.55, within(1e-12));
        }

        @Test
        @DisplayName("getTruncationFraction() returns the value supplied at construction")
        void getTruncationFractionReturnsSame() {
            AerospikeContour contour = new AerospikeContour(params, 0.6, 0.75, 10);
            assertThat(contour.getTruncationFraction()).isCloseTo(0.75, within(1e-12));
        }
    }
}
