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

package com.nozzle.geometry;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.core.Point2D;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ConvergentSection Tests")
class ConvergentSection_UT {

    private NozzleDesignParameters defaultParams;

    @BeforeEach
    void setUp() {
        defaultParams = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .upstreamCurvatureRatio(1.5)
                .convergentHalfAngleDegrees(30)
                .contractionRatio(4.0)
                .build();
    }

    // =========================================================================
    // Construction validation
    // =========================================================================

    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {

        @Test
        @DisplayName("Should construct with valid default parameters")
        void shouldConstructWithDefaultParameters() {
            assertThatCode(() -> new ConvergentSection(defaultParams))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should reject geometry where arc exceeds chamber radius")
        void shouldRejectArcExceedingChamberRadius() {
            // upstreamCurvatureRatio=3.0, theta_c=60 deg, contractionRatio=1.5 → arc overshoots
            NozzleDesignParameters bad = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(2.0)
                    .chamberPressure(7e6).chamberTemperature(3000)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.AIR)
                    .upstreamCurvatureRatio(3.0)
                    .convergentHalfAngleDegrees(60)
                    .contractionRatio(1.5)
                    .build();
            assertThatThrownBy(() -> new ConvergentSection(bad))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("chamber radius");
        }
    }

    // =========================================================================
    // Geometry correctness
    // =========================================================================

    @Nested
    @DisplayName("Geometry Tests")
    class GeometryTests {

        @Test
        @DisplayName("Generated points should be ordered from upstream to throat")
        void shouldOrderPointsUpstreamToThroat() {
            ConvergentSection cs = new ConvergentSection(defaultParams).generate(60);
            List<Point2D> pts = cs.getContourPoints();
            assertThat(pts).hasSizeGreaterThanOrEqualTo(10);

            // x coordinates must be strictly increasing (going downstream)
            for (int i = 1; i < pts.size(); i++) {
                assertThat(pts.get(i).x()).isGreaterThan(pts.get(i - 1).x());
            }
        }

        @Test
        @DisplayName("All x coordinates must be negative (upstream of throat)")
        void allXCoordsMustBeNegative() {
            ConvergentSection cs = new ConvergentSection(defaultParams).generate(60);
            cs.getContourPoints().forEach(p ->
                    assertThat(p.x()).isNegative());
        }

        @Test
        @DisplayName("First point should be at chamber radius")
        void firstPointShouldBeAtChamberRadius() {
            ConvergentSection cs = new ConvergentSection(defaultParams).generate(60);
            double rc = defaultParams.chamberRadius();
            assertThat(cs.getContourPoints().getFirst().y())
                    .isCloseTo(rc, within(1e-6));
        }

        @Test
        @DisplayName("Last point should be close to throat radius")
        void lastPointShouldBeCloseToThroatRadius() {
            double rt = defaultParams.throatRadius();
            ConvergentSection cs = new ConvergentSection(defaultParams).generate(60);
            // Last point is just upstream of x=0; radius should be near rt
            assertThat(cs.getContourPoints().getLast().y())
                    .isCloseTo(rt, within(rt * 0.02));
        }

        @Test
        @DisplayName("Chamber radius should equal rt × sqrt(contractionRatio)")
        void chamberRadiusShouldMatchFormula() {
            ConvergentSection cs = new ConvergentSection(defaultParams).generate(40);
            double expected = defaultParams.throatRadius()
                            * Math.sqrt(defaultParams.contractionRatio());
            assertThat(cs.getChamberRadius()).isCloseTo(expected, within(1e-9));
        }

        @Test
        @DisplayName("Length should be positive and consistent with geometry")
        void lengthShouldBePositive() {
            ConvergentSection cs = new ConvergentSection(defaultParams).generate(60);
            assertThat(cs.getLength()).isPositive();
            // Upstream x of first point equals -getLength()
            assertThat(-cs.getContourPoints().getFirst().x())
                    .isCloseTo(cs.getLength(), within(1e-9));
        }

        @Test
        @DisplayName("Arc end radius should be less than chamber radius")
        void arcEndRadiusShouldBeBelowChamberRadius() {
            ConvergentSection cs = new ConvergentSection(defaultParams).generate(60);
            assertThat(cs.getArcEndY()).isLessThan(cs.getChamberRadius());
            assertThat(cs.getArcEndY()).isGreaterThan(defaultParams.throatRadius());
        }

        @Test
        @DisplayName("Arc end-point should satisfy upstream arc formula")
        void arcEndShouldSatisfyArcFormula() {
            double rt  = defaultParams.throatRadius();
            double rcu = defaultParams.upstreamCurvatureRatio() * rt;
            double tc  = defaultParams.convergentHalfAngle();

            ConvergentSection cs = new ConvergentSection(defaultParams).generate(40);

            double expectedX = -rcu * Math.sin(tc);
            double expectedY = rt + rcu * (1.0 - Math.cos(tc));

            assertThat(cs.getArcEndX()).isCloseTo(expectedX, within(1e-9));
            assertThat(cs.getArcEndY()).isCloseTo(expectedY, within(1e-9));
        }

        @ParameterizedTest(name = "upstreamRatio={0}, halfAngleDeg={1}, contractionRatio={2}")
        @CsvSource({
                "0.8,  20, 3.0",
                "1.5,  30, 4.0",
                "2.0,  25, 6.0",
                "1.0,  45, 5.0"
        })
        @DisplayName("Geometry should be valid across a parameter sweep")
        void geometryShouldBeValidAcrossParameterSweep(
                double uRatio, double halfDeg, double cr) {
            NozzleDesignParameters p = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0)
                    .chamberPressure(7e6).chamberTemperature(3500)
                    .ambientPressure(101325).gasProperties(GasProperties.AIR)
                    .upstreamCurvatureRatio(uRatio)
                    .convergentHalfAngleDegrees(halfDeg)
                    .contractionRatio(cr)
                    .build();
            ConvergentSection cs = new ConvergentSection(p).generate(50);
            assertThat(cs.getContourPoints()).hasSizeGreaterThanOrEqualTo(10);
            assertThat(cs.getLength()).isPositive();
        }
    }

    // =========================================================================
    // getLength
    // =========================================================================

    @Nested
    @DisplayName("getLength Tests")
    class GetLengthTests {

        @Test
        @DisplayName("Returns 0 before generate is called")
        void zeroBeforeGenerate() {
            assertThat(new ConvergentSection(defaultParams).getLength()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Equals the magnitude of the first contour point's x-coordinate")
        void equalsNegatedFirstPointX() {
            ConvergentSection cs = new ConvergentSection(defaultParams).generate(50);
            assertThat(cs.getLength())
                    .isCloseTo(-cs.getContourPoints().getFirst().x(), within(1e-9));
        }

        @Test
        @DisplayName("Equals -getChamberFaceX()")
        void equalsNegatedChamberFaceX() {
            ConvergentSection cs = new ConvergentSection(defaultParams).generate(50);
            assertThat(cs.getLength()).isCloseTo(-cs.getChamberFaceX(), within(1e-9));
        }

        @Test
        @DisplayName("Grows with larger contraction ratio (more radial distance to cover)")
        void growsWithContractionRatio() {
            double len4 = lengthFor(4.0, 30, 1.5);
            double len8 = lengthFor(8.0, 30, 1.5);
            assertThat(len8).isGreaterThan(len4);
        }

        @Test
        @DisplayName("Grows with smaller half-angle (more gradual cone → longer axial run)")
        void growsWithSmallerHalfAngle() {
            double len40 = lengthFor(4.0, 40, 1.5);
            double len20 = lengthFor(4.0, 20, 1.5);
            assertThat(len20).isGreaterThan(len40);
        }

        @Test
        @DisplayName("Matches the analytical chamber-face formula")
        void matchesAnalyticalFormula() {
            double rt  = defaultParams.throatRadius();
            double rcu = defaultParams.upstreamCurvatureRatio() * rt;
            double tc  = defaultParams.convergentHalfAngle();
            double rc  = defaultParams.chamberRadius();

            double arcEndX = -rcu * Math.sin(tc);
            double arcEndY = rt + rcu * (1.0 - Math.cos(tc));
            double expectedChamberFaceX = arcEndX - (rc - arcEndY) / Math.tan(tc);
            double expectedLength = -expectedChamberFaceX;

            ConvergentSection cs = new ConvergentSection(defaultParams).generate(50);
            assertThat(cs.getLength()).isCloseTo(expectedLength, within(1e-9));
        }

        private double lengthFor(double cr, double halfDeg, double uRatio) {
            NozzleDesignParameters p = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0)
                    .chamberPressure(7e6).chamberTemperature(3500)
                    .ambientPressure(101325).gasProperties(GasProperties.AIR)
                    .contractionRatio(cr)
                    .convergentHalfAngleDegrees(halfDeg)
                    .upstreamCurvatureRatio(uRatio)
                    .build();
            return new ConvergentSection(p).generate(50).getLength();
        }
    }

    // =========================================================================
    // getChamberFaceX
    // =========================================================================

    @Nested
    @DisplayName("getChamberFaceX Tests")
    class GetChamberFaceXTests {

        @Test
        @DisplayName("Returns 0 before generate is called (field default)")
        void zeroBeforeGenerate() {
            assertThat(new ConvergentSection(defaultParams).getChamberFaceX()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Returns a negative value after generate (upstream of throat)")
        void negativeAfterGenerate() {
            assertThat(new ConvergentSection(defaultParams).generate(50).getChamberFaceX())
                    .isNegative();
        }

        @Test
        @DisplayName("Equals the x-coordinate of the first contour point")
        void equalsFirstContourPointX() {
            ConvergentSection cs = new ConvergentSection(defaultParams).generate(50);
            assertThat(cs.getChamberFaceX())
                    .isCloseTo(cs.getContourPoints().getFirst().x(), within(1e-9));
        }

        @Test
        @DisplayName("Equals -getLength()")
        void equalsNegatedLength() {
            ConvergentSection cs = new ConvergentSection(defaultParams).generate(50);
            assertThat(cs.getChamberFaceX()).isCloseTo(-cs.getLength(), within(1e-9));
        }

        @Test
        @DisplayName("Matches the analytical formula: arcEndX - (r_c - arcEndY) / tan(θ_c)")
        void matchesAnalyticalFormula() {
            double rt  = defaultParams.throatRadius();
            double rcu = defaultParams.upstreamCurvatureRatio() * rt;
            double tc  = defaultParams.convergentHalfAngle();
            double rc  = defaultParams.chamberRadius();

            double arcEndX = -rcu * Math.sin(tc);
            double arcEndY = rt + rcu * (1.0 - Math.cos(tc));
            double expected = arcEndX - (rc - arcEndY) / Math.tan(tc);

            ConvergentSection cs = new ConvergentSection(defaultParams).generate(50);
            assertThat(cs.getChamberFaceX()).isCloseTo(expected, within(1e-9));
        }

        @Test
        @DisplayName("Becomes more negative with larger contraction ratio")
        void moreNegativeWithLargerContractionRatio() {
            double x4 = chamberFaceXFor(4.0, 30, 1.5);
            double x8 = chamberFaceXFor(8.0, 30, 1.5);
            assertThat(x8).isLessThan(x4);
        }

        @Test
        @DisplayName("Becomes more negative with smaller half-angle")
        void moreNegativeWithSmallerHalfAngle() {
            double x40 = chamberFaceXFor(4.0, 40, 1.5);
            double x20 = chamberFaceXFor(4.0, 20, 1.5);
            assertThat(x20).isLessThan(x40);
        }

        private double chamberFaceXFor(double cr, double halfDeg, double uRatio) {
            NozzleDesignParameters p = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0)
                    .chamberPressure(7e6).chamberTemperature(3500)
                    .ambientPressure(101325).gasProperties(GasProperties.AIR)
                    .contractionRatio(cr)
                    .convergentHalfAngleDegrees(halfDeg)
                    .upstreamCurvatureRatio(uRatio)
                    .build();
            return new ConvergentSection(p).generate(50).getChamberFaceX();
        }
    }

    // =========================================================================
    // Sonic-line Cd correction
    // =========================================================================

    @Nested
    @DisplayName("Sonic Line Cd Correction Tests")
    class SonicLineCdTests {

        @Test
        @DisplayName("Cd correction should be in [0.98, 1.0] for default parameters")
        void cdCorrectionShouldBeInValidRange() {
            ConvergentSection cs = new ConvergentSection(defaultParams).generate(60);
            assertThat(cs.getSonicLineCdCorrection())
                    .isGreaterThanOrEqualTo(0.98)
                    .isLessThanOrEqualTo(1.0);
        }

        @Test
        @DisplayName("Cd correction should be less than 1.0 (sonic line is curved)")
        void cdCorrectionShouldBeLessThanOne() {
            ConvergentSection cs = new ConvergentSection(defaultParams).generate(60);
            assertThat(cs.getSonicLineCdCorrection()).isLessThan(1.0);
        }

        @Test
        @DisplayName("Cd should increase as upstream curvature ratio increases")
        void cdShouldIncreaseWithUpstreamCurvatureRatio() {
            // Larger r_cu → gentler upstream arc → flatter sonic line → higher Cd
            double cd1 = cdFor(0.5);
            double cd2 = cdFor(1.5);
            double cd3 = cdFor(2.5);
            assertThat(cd1).isLessThan(cd2);
            assertThat(cd2).isLessThan(cd3);
        }

        @Test
        @DisplayName("Cd should increase as downstream curvature ratio increases")
        void cdShouldIncreaseWithDownstreamCurvatureRatio() {
            // Larger r_cd → gentler downstream arc → flatter sonic line → higher Cd
            double cdSmall = cdForDownstream(0.3);
            double cdMed   = cdForDownstream(0.6);
            double cdLarge = cdForDownstream(1.0);
            assertThat(cdSmall).isLessThan(cdMed);
            assertThat(cdMed).isLessThan(cdLarge);
        }

        @Test
        @DisplayName("Default parameters should give ~0.996 Cd correction")
        void defaultShouldGiveExpectedCd() {
            // r_cu=1.5*rt, r_cd=0.382*rt, γ≈1.2 (LOX/RP-1) → ~0.995-0.998
            ConvergentSection cs = new ConvergentSection(defaultParams).generate(60);
            assertThat(cs.getSonicLineCdCorrection())
                    .isGreaterThan(0.990)
                    .isLessThan(1.000);
        }

        // ---- helpers ----

        private double cdFor(double upstreamRatio) {
            NozzleDesignParameters p = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0)
                    .chamberPressure(7e6).chamberTemperature(3500)
                    .ambientPressure(101325).gasProperties(GasProperties.AIR)
                    .upstreamCurvatureRatio(upstreamRatio)
                    .convergentHalfAngleDegrees(30).contractionRatio(4.0)
                    .build();
            return new ConvergentSection(p).generate(40).getSonicLineCdCorrection();
        }

        private double cdForDownstream(double downstreamRatio) {
            NozzleDesignParameters p = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0)
                    .chamberPressure(7e6).chamberTemperature(3500)
                    .ambientPressure(101325).gasProperties(GasProperties.AIR)
                    .throatCurvatureRatio(downstreamRatio)
                    .upstreamCurvatureRatio(1.5)
                    .convergentHalfAngleDegrees(30).contractionRatio(4.0)
                    .build();
            return new ConvergentSection(p).generate(40).getSonicLineCdCorrection();
        }
    }

    // =========================================================================
    // NozzleContour integration
    // =========================================================================

    @Nested
    @DisplayName("NozzleContour Integration Tests")
    class NozzleContourIntegrationTests {

        @Test
        @DisplayName("withConvergentSection should produce points with negative x")
        void fullContourShouldHaveNegativeXPoints() {
            NozzleContour divergent = new NozzleContour(
                    NozzleContour.ContourType.RAO_BELL, defaultParams);
            divergent.generate(80);

            ConvergentSection cs = new ConvergentSection(defaultParams).generate(50);
            NozzleContour full = divergent.withConvergentSection(cs);

            boolean hasNegativeX = full.getContourPoints().stream()
                    .anyMatch(p -> p.x() < 0);
            assertThat(hasNegativeX).isTrue();
        }

        @Test
        @DisplayName("Full contour length should exceed divergent-only length")
        void fullContourShouldBeLongerThanDivergentOnly() {
            NozzleContour divergent = new NozzleContour(
                    NozzleContour.ContourType.RAO_BELL, defaultParams);
            divergent.generate(80);

            ConvergentSection cs = new ConvergentSection(defaultParams).generate(50);
            NozzleContour full = divergent.withConvergentSection(cs);

            assertThat(full.getLength()).isGreaterThan(divergent.getLength());
        }

        @Test
        @DisplayName("withConvergentSection should throw if ConvergentSection not generated")
        void shouldThrowIfConvergentSectionNotGenerated() {
            NozzleContour divergent = new NozzleContour(
                    NozzleContour.ContourType.CONICAL, defaultParams);
            divergent.generate(40);

            ConvergentSection cs = new ConvergentSection(defaultParams); // not generated
            assertThatThrownBy(() -> divergent.withConvergentSection(cs))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
