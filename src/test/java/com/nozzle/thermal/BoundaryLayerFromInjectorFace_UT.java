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

package com.nozzle.thermal;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.geometry.FullNozzleGeometry;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.thermal.BoundaryLayerPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the injector-face boundary-layer integration methods added to
 * {@link BoundaryLayerCorrection}:
 * <ul>
 *   <li>{@link BoundaryLayerCorrection#calculateFromInjectorFace}</li>
 *   <li>{@link BoundaryLayerCorrection#getThroatDisplacementThickness}</li>
 *   <li>{@link BoundaryLayerCorrection#getBoundaryLayerCdCorrection}</li>
 *   <li>{@link BoundaryLayerCorrection#getCombinedCd}</li>
 * </ul>
 */
class BoundaryLayerFromInjectorFace_UT {

    private NozzleDesignParameters params;
    private FullNozzleGeometry fullGeom;

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
    }

    // ── IllegalStateException guard ──────────────────────────────────────────

    @Test
    void calculateFromInjectorFace_throwsIfNotGenerated() {
        FullNozzleGeometry notGenerated = new FullNozzleGeometry(params);
        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        contour.generate(50);
        BoundaryLayerCorrection blc = new BoundaryLayerCorrection(params, contour);

        assertThatThrownBy(() -> blc.calculateFromInjectorFace(notGenerated, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("generate");
    }

    // ── Profile is non-empty after calculation ───────────────────────────────

    @Test
    void profileNonEmptyAfterCalculation() {
        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        BoundaryLayerCorrection blc = new BoundaryLayerCorrection(params, contour)
                .calculateFromInjectorFace(fullGeom, null);

        assertThat(blc.getBoundaryLayerProfile()).isNotEmpty();
    }

    @Test
    void profileSizeMatchesWallPoints() {
        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        BoundaryLayerCorrection blc = new BoundaryLayerCorrection(params, contour)
                .calculateFromInjectorFace(fullGeom, Collections.emptyList());

        assertThat(blc.getBoundaryLayerProfile()).hasSize(fullGeom.getWallPoints().size());
    }

    // ── Running-length starts at zero ────────────────────────────────────────

    @Test
    void firstProfilePointHasZeroRunningLength() {
        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        BoundaryLayerCorrection blc = new BoundaryLayerCorrection(params, contour)
                .calculateFromInjectorFace(fullGeom, null);

        double s0 = blc.getBoundaryLayerProfile().getFirst().runningLength();
        assertThat(s0).isEqualTo(0.0);
    }

    @Test
    void runningLengthMonotonicallyIncreases() {
        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        var profile = new BoundaryLayerCorrection(params, contour)
                .calculateFromInjectorFace(fullGeom, null)
                .getBoundaryLayerProfile();

        for (int i = 1; i < profile.size(); i++) {
            assertThat(profile.get(i).runningLength())
                    .isGreaterThanOrEqualTo(profile.get(i - 1).runningLength());
        }
    }

    // ── BL starts further upstream than throat-only calculation ──────────────

    @Test
    void injectorFaceRunningLengthIsLongerThanThroatOnly() {
        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        contour.generate(60);

        BoundaryLayerCorrection blcFull   = new BoundaryLayerCorrection(params, contour)
                .calculateFromInjectorFace(fullGeom, null);
        BoundaryLayerCorrection blcThroat = new BoundaryLayerCorrection(params, contour)
                .calculate(null);

        // The full running length at the exit must be larger because it includes
        // the convergent section arc length.
        double sExitFull   = blcFull  .getBoundaryLayerProfile().getLast().runningLength();
        double sExitThroat = blcThroat.getBoundaryLayerProfile().isEmpty() ? 0
                : blcThroat.getBoundaryLayerProfile().getLast().runningLength();

        assertThat(sExitFull).isGreaterThan(sExitThroat);
    }

    // ── Throat displacement thickness is a small positive fraction of r_t ───

    @Test
    void throatDisplacementThicknessIsPositive() {
        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        double deltaStar = new BoundaryLayerCorrection(params, contour)
                .calculateFromInjectorFace(fullGeom, null)
                .getThroatDisplacementThickness();

        assertThat(deltaStar).isPositive();
    }

    @Test
    void throatDisplacementThicknessIsSmallFractionOfThroatRadius() {
        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        double deltaStar = new BoundaryLayerCorrection(params, contour)
                .calculateFromInjectorFace(fullGeom, null)
                .getThroatDisplacementThickness();

        double rt = params.throatRadius();
        // Typically δ*/r_t < 5% for standard rocket conditions
        assertThat(deltaStar / rt).isLessThan(0.15);
    }

    @Test
    void throatDisplacementThicknessZeroBeforeCalculation() {
        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        double deltaStar = new BoundaryLayerCorrection(params, contour)
                .getThroatDisplacementThickness();

        assertThat(deltaStar).isEqualTo(0.0);
    }

    // ── Cd corrections ───────────────────────────────────────────────────────

    @Test
    void blCdCorrectionBetweenZeroAndOne() {
        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        double cdBl = new BoundaryLayerCorrection(params, contour)
                .calculateFromInjectorFace(fullGeom, null)
                .getBoundaryLayerCdCorrection();

        assertThat(cdBl).isBetween(0.0, 1.0);
    }

    @Test
    void blCdCorrectionIsOneWhenNoCalculation() {
        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        double cdBl = new BoundaryLayerCorrection(params, contour)
                .getBoundaryLayerCdCorrection();

        assertThat(cdBl).isEqualTo(1.0);
    }

    @Test
    void combinedCdIsLessOrEqualToGeometricCd() {
        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        BoundaryLayerCorrection blc = new BoundaryLayerCorrection(params, contour)
                .calculateFromInjectorFace(fullGeom, null);

        double cdGeom     = params.dischargeCoefficient();
        double cdCombined = blc.getCombinedCd();

        // BL correction can only reduce Cd
        assertThat(cdCombined).isLessThanOrEqualTo(cdGeom + 1e-12);
    }

    @Test
    void combinedCdIsPhysicallyReasonable() {
        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        double cd = new BoundaryLayerCorrection(params, contour)
                .calculateFromInjectorFace(fullGeom, null)
                .getCombinedCd();

        // Typical combined Cd for a bell nozzle: 0.97 – 1.0
        assertThat(cd).isBetween(0.80, 1.0);
    }

    // ── Turbulent flag ────────────────────────────────────────────────────────

    @Test
    void forceTurbulentProfileHasAllTurbulentPoints() {
        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        var profile = new BoundaryLayerCorrection(params, contour)
                .setForceTurbulent(true)
                .calculateFromInjectorFace(fullGeom, null)
                .getBoundaryLayerProfile();

        // Every point with non-zero running length should be marked turbulent
        profile.stream()
               .filter(p -> p.runningLength() > 0)
               .forEach(p -> assertThat(p.isTurbulent())
                       .as("Point at x=%.4f should be turbulent", p.x())
                       .isTrue());
    }

    // ── Displacement thickness increases through nozzle ───────────────────────

    @Test
    void displacementThicknessGrowsAlongNozzle() {
        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        var profile = new BoundaryLayerCorrection(params, contour)
                .calculateFromInjectorFace(fullGeom, null)
                .getBoundaryLayerProfile();

        // Overall trend: δ* at exit > δ* at first point
        double deltaStarFirst = profile.getFirst().displacementThickness();
        double deltaStarLast  = profile.getLast().displacementThickness();
        assertThat(deltaStarLast).isGreaterThan(deltaStarFirst);
    }

    // ── Non-null x/y in profile ───────────────────────────────────────────────

    @Test
    void allProfilePointsHaveFiniteValues() {
        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        var profile = new BoundaryLayerCorrection(params, contour)
                .calculateFromInjectorFace(fullGeom, null)
                .getBoundaryLayerProfile();

        for (var p : profile) {
            assertThat(Double.isFinite(p.x())).isTrue();
            assertThat(Double.isFinite(p.y())).isTrue();
            assertThat(Double.isFinite(p.displacementThickness())).isTrue();
            assertThat(Double.isFinite(p.thickness())).isTrue();
        }
    }

    // ── x-coordinate coverage ────────────────────────────────────────────────

    @Test
    void profileSpansConvergentAndDivergentSections() {
        NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        var profile = new BoundaryLayerCorrection(params, contour)
                .calculateFromInjectorFace(fullGeom, null)
                .getBoundaryLayerProfile();

        double xMin = profile.stream().mapToDouble(BoundaryLayerPoint::x).min().orElse(0);
        double xMax = profile.stream().mapToDouble(BoundaryLayerPoint::x).max().orElse(0);

        assertThat(xMin).isNegative();   // convergent section covered
        assertThat(xMax).isPositive();   // divergent section covered
    }
}
