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
import com.nozzle.moc.CharacteristicNet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link FullNozzleGeometry}.
 *
 * <p>Covers: generation, wall-point ordering, geometry accessors, MOC factory,
 * total-length computation, and the isGenerated state flag.
 */
class FullNozzleGeometry_UT {

    private NozzleDesignParameters params;

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
    }

    // ── isGenerated state ────────────────────────────────────────────────────

    @Test
    void notGeneratedByDefault() {
        FullNozzleGeometry geom = new FullNozzleGeometry(params);
        assertThat(geom.isGenerated()).isFalse();
        assertThat(geom.getWallPoints()).isEmpty();
    }

    @Test
    void generatedAfterGenerate() {
        FullNozzleGeometry geom = new FullNozzleGeometry(params).generate();
        assertThat(geom.isGenerated()).isTrue();
    }

    // ── Wall point count and ordering ────────────────────────────────────────

    @Test
    void wallPointsNonEmptyAfterGenerate() {
        List<Point2D> wall = new FullNozzleGeometry(params).generate().getWallPoints();
        assertThat(wall).isNotEmpty();
    }

    @Test
    void wallPointsStrictlyMonotonicInX() {
        List<Point2D> wall = new FullNozzleGeometry(params).generate(30, 60).getWallPoints();
        for (int i = 1; i < wall.size(); i++) {
            assertThat(wall.get(i).x())
                    .as("Wall x must increase monotonically at index %d", i)
                    .isGreaterThan(wall.get(i - 1).x());
        }
    }

    @Test
    void firstWallPointIsUpstreamOfThroat() {
        FullNozzleGeometry geom = new FullNozzleGeometry(params).generate();
        assertThat(geom.getWallPoints().getFirst().x()).isNegative();
    }

    @Test
    void lastWallPointIsDownstreamOfThroat() {
        FullNozzleGeometry geom = new FullNozzleGeometry(params).generate();
        assertThat(geom.getWallPoints().getLast().x()).isPositive();
    }

    @Test
    void wallPointsContainThroatRegion() {
        // Some wall point should be near x=0 (throat boundary)
        List<Point2D> wall = new FullNozzleGeometry(params).generate(30, 60).getWallPoints();
        double minAbsX = wall.stream().mapToDouble(p -> Math.abs(p.x())).min().orElse(Double.MAX_VALUE);
        // Closest point to x=0 should be within one throat radius of the throat
        assertThat(minAbsX).isLessThan(params.throatRadius());
    }

    // ── Radius bounds ────────────────────────────────────────────────────────

    @Test
    void firstPointRadiusEqualsChamberRadius() {
        FullNozzleGeometry geom = new FullNozzleGeometry(params).generate();
        double rc = params.chamberRadius();
        // Chamber face radius should match r_c = r_t * sqrt(contractionRatio)
        assertThat(geom.getWallPoints().getFirst().y()).isCloseTo(rc, within(rc * 0.02));
    }

    @Test
    void lastPointRadiusIsNearExitRadius() {
        FullNozzleGeometry geom = new FullNozzleGeometry(params).generate();
        double re = params.exitRadius();
        assertThat(geom.getWallPoints().getLast().y()).isCloseTo(re, within(re * 0.05));
    }

    @Test
    void minimumRadiusIsNearThroatRadius() {
        FullNozzleGeometry geom = new FullNozzleGeometry(params).generate();
        double rt = params.throatRadius();
        double minR = geom.getWallPoints().stream().mapToDouble(Point2D::y).min().orElse(Double.MAX_VALUE);
        assertThat(minR).isCloseTo(rt, within(rt * 0.1));
    }

    // ── Scalar geometry accessors ────────────────────────────────────────────

    @Test
    void getTotalLengthIsPositive() {
        FullNozzleGeometry geom = new FullNozzleGeometry(params).generate();
        assertThat(geom.getTotalLength()).isPositive();
    }

    @Test
    void totalLengthEqualsSumOfSections() {
        FullNozzleGeometry geom = new FullNozzleGeometry(params).generate();
        double expected = geom.getConvergentLength() + geom.getDivergentLength();
        assertThat(geom.getTotalLength()).isCloseTo(expected, within(geom.getTotalLength() * 1e-6));
    }

    @Test
    void convergentLengthIsPositive() {
        FullNozzleGeometry geom = new FullNozzleGeometry(params).generate();
        assertThat(geom.getConvergentLength()).isPositive();
    }

    @Test
    void divergentLengthIsPositive() {
        FullNozzleGeometry geom = new FullNozzleGeometry(params).generate();
        assertThat(geom.getDivergentLength()).isPositive();
    }

    @Test
    void chamberFaceXIsNegative() {
        FullNozzleGeometry geom = new FullNozzleGeometry(params).generate();
        assertThat(geom.getChamberFaceX()).isNegative();
    }

    @Test
    void exitXIsPositive() {
        FullNozzleGeometry geom = new FullNozzleGeometry(params).generate();
        assertThat(geom.getExitX()).isPositive();
    }

    @Test
    void throatRadiusMatchesParameters() {
        FullNozzleGeometry geom = new FullNozzleGeometry(params);
        assertThat(geom.getThroatRadius()).isEqualTo(params.throatRadius());
    }

    @Test
    void chamberRadiusMatchesParameters() {
        FullNozzleGeometry geom = new FullNozzleGeometry(params);
        assertThat(geom.getChamberRadius()).isEqualTo(params.chamberRadius());
    }

    @Test
    void exitRadiusMatchesParameters() {
        FullNozzleGeometry geom = new FullNozzleGeometry(params);
        assertThat(geom.getExitRadius()).isEqualTo(params.exitRadius());
    }

    @Test
    void sonicLineCdInRange() {
        FullNozzleGeometry geom = new FullNozzleGeometry(params);
        double cd = geom.getSonicLineCd();
        assertThat(cd).isBetween(0.98, 1.0);
    }

    // ── Zero-points guard before generate ───────────────────────────────────

    @Test
    void totalLengthZeroBeforeGenerate() {
        assertThat(new FullNozzleGeometry(params).getTotalLength()).isEqualTo(0.0);
    }

    @Test
    void chamberFaceXZeroBeforeGenerate() {
        assertThat(new FullNozzleGeometry(params).getChamberFaceX()).isEqualTo(0.0);
    }

    // ── toString ─────────────────────────────────────────────────────────────

    @Test
    void toStringBeforeGenerate() {
        assertThat(new FullNozzleGeometry(params).toString()).contains("not generated");
    }

    @Test
    void toStringAfterGenerate() {
        String s = new FullNozzleGeometry(params).generate().toString();
        assertThat(s).contains("r_c=").contains("r_t=").contains("r_e=");
    }

    // ── MOC factory ──────────────────────────────────────────────────────────

    @Test
    void fromMOCFactoryCreatesNonEmptyGeometry() {
        CharacteristicNet net = new CharacteristicNet(params).generate();
        FullNozzleGeometry geom = FullNozzleGeometry.fromMOC(params, net).generate(30, 0);
        assertThat(geom.isGenerated()).isTrue();
        assertThat(geom.getWallPoints()).isNotEmpty();
    }

    @Test
    void fromMOCWallPointsMonotonic() {
        CharacteristicNet net = new CharacteristicNet(params).generate();
        FullNozzleGeometry geom = FullNozzleGeometry.fromMOC(params, net).generate(30, 0);
        List<Point2D> wall = geom.getWallPoints();
        for (int i = 1; i < wall.size(); i++) {
            assertThat(wall.get(i).x()).isGreaterThan(wall.get(i - 1).x());
        }
    }

    // ── Custom contour type ───────────────────────────────────────────────────

    @Test
    void conicalDivergentContourWorks() {
        NozzleContour conical = new NozzleContour(NozzleContour.ContourType.CONICAL, params);
        ConvergentSection conv = new ConvergentSection(params);
        FullNozzleGeometry geom = new FullNozzleGeometry(params, conv, conical).generate(25, 50);
        assertThat(geom.isGenerated()).isTrue();
        assertThat(geom.getDivergentLength()).isPositive();
    }

    @Test
    void truncatedIdealDivergentContourWorks() {
        NozzleContour tic = new NozzleContour(NozzleContour.ContourType.TRUNCATED_IDEAL, params);
        ConvergentSection conv = new ConvergentSection(params);
        FullNozzleGeometry geom = new FullNozzleGeometry(params, conv, tic).generate(25, 50);
        assertThat(geom.isGenerated()).isTrue();
        assertThat(geom.getWallPoints()).hasSizeGreaterThan(10);
    }

    // ── Component accessors ──────────────────────────────────────────────────

    @Test
    void getConvergentSectionIsNotNull() {
        assertThat(new FullNozzleGeometry(params).getConvergentSection()).isNotNull();
    }

    @Test
    void getDivergentContourIsNotNull() {
        assertThat(new FullNozzleGeometry(params).getDivergentContour()).isNotNull();
    }

    @Test
    void getParametersIsNotNull() {
        assertThat(new FullNozzleGeometry(params).getParameters()).isSameAs(params);
    }
}
