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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the heat-flux peak-location feature added to {@link HeatTransferModel}.
 *
 * <p>The Bartz curvature correction {@code (D_t/r_c)^0.1} uses the parametric
 * throat-arc radii ({@code r_cd = throatCurvatureRatio × r_t} on the divergent side,
 * {@code r_cu = upstreamCurvatureRatio × r_t} on the convergent side) instead of
 * noisy finite differences in the arc zone.  This shifts the predicted peak location
 * correctly: when {@code r_cu > r_cd} (standard Rao values) the divergent side is more
 * curved and the peak is downstream of the throat; when {@code r_cu < r_cd} the
 * upstream arc is tighter and the peak can sit on the convergent side.
 */
@DisplayName("HeatTransferModel — peak heat-flux location")
class HeatTransferPeakLocation_UT {

    // Standard Rao: r_cd = 0.382·r_t, r_cu = 1.5·r_t → divergent side more curved → peak downstream
    private NozzleDesignParameters standardParams;

    // Tight upstream: r_cu = 0.2·r_t < r_cd = 0.382·r_t → convergent side more curved → peak upstream
    private NozzleDesignParameters tightUpstreamParams;

    private NozzleContour standardContour;
    private NozzleContour tightUpstreamContour;

    @BeforeEach
    void setUp() {
        standardParams = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500.0)
                .ambientPressure(101325.0)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(15)
                .wallAngleInitialDegrees(25.0)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .throatCurvatureRatio(0.382)    // r_cd = 0.382·r_t (Rao default)
                .upstreamCurvatureRatio(1.5)    // r_cu = 1.5·r_t (Rao default)
                .contractionRatio(4.0)
                .build();

        tightUpstreamParams = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500.0)
                .ambientPressure(101325.0)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(15)
                .wallAngleInitialDegrees(25.0)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .throatCurvatureRatio(0.382)    // r_cd = 0.382·r_t (same as standard)
                .upstreamCurvatureRatio(0.2)    // r_cu = 0.2·r_t < r_cd → tighter upstream arc
                .contractionRatio(4.0)
                .build();

        standardContour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, standardParams);
        standardContour.generate(60);

        tightUpstreamContour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, tightUpstreamParams);
        tightUpstreamContour.generate(60);
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Before calculation")
    class BeforeCalculation {

        @Test
        @DisplayName("getPeakFluxPoint() returns null before any calculation")
        void peakFluxPoint_isNull() {
            HeatTransferModel model = new HeatTransferModel(standardParams, standardContour);
            assertThat(model.getPeakFluxPoint()).isNull();
        }

        @Test
        @DisplayName("getPeakFluxX() returns NaN before any calculation")
        void peakFluxX_isNaN() {
            HeatTransferModel model = new HeatTransferModel(standardParams, standardContour);
            assertThat(model.getPeakFluxX()).isNaN();
        }
    }

    // ── calculate() — divergent section only ─────────────────────────────────

    @Nested
    @DisplayName("calculate() — divergent section")
    class DivergentCalculation {

        @Test
        @DisplayName("peak is non-null after calculate()")
        void peakIsNotNull_afterCalculation() {
            HeatTransferModel model = new HeatTransferModel(standardParams, standardContour)
                    .calculate(null);
            assertThat(model.getPeakFluxPoint()).isNotNull();
        }

        @Test
        @DisplayName("peak x-position is finite after calculate()")
        void peakFluxX_isFinite_afterCalculation() {
            HeatTransferModel model = new HeatTransferModel(standardParams, standardContour)
                    .calculate(null);
            assertThat(model.getPeakFluxX()).isFinite();
        }

        @Test
        @DisplayName("standard Rao params: peak is downstream of throat (x > 0)")
        void standardRaoParams_peakIsDownstreamOfThroat() {
            // r_cu = 1.5·r_t  >  r_cd = 0.382·r_t
            // Bartz correction (D_t/r_c)^0.1:
            //   divergent side: (2/0.382)^0.1 ≈ 1.18  (larger correction)
            //   convergent side: (2/1.5)^0.1  ≈ 1.03
            // → heat transfer coefficient peaks on the divergent side
            HeatTransferModel model = new HeatTransferModel(standardParams, standardContour)
                    .calculate(null);

            assertThat(model.getPeakFluxX())
                    .as("standard Rao params: peak should be downstream of throat")
                    .isGreaterThan(0.0);
        }

        @Test
        @DisplayName("peak x matches the x-coordinate of the peak WallThermalPoint")
        void peakFluxX_matchesPeakPoint() {
            HeatTransferModel model = new HeatTransferModel(standardParams, standardContour)
                    .calculate(null);

            assertThat(model.getPeakFluxX())
                    .isEqualTo(model.getPeakFluxPoint().x());
        }

        @Test
        @DisplayName("peak total heat flux matches getMaxHeatFlux()")
        void peakTotalFlux_matchesGetMaxHeatFlux() {
            HeatTransferModel model = new HeatTransferModel(standardParams, standardContour)
                    .calculate(null);

            assertThat(model.getPeakFluxPoint().totalHeatFlux())
                    .isEqualTo(model.getMaxHeatFlux());
        }

        @Test
        @DisplayName("peak is cleared and reset by a second calculate() call")
        void peakReset_onRecalculation() {
            HeatTransferModel model = new HeatTransferModel(standardParams, standardContour)
                    .calculate(null);
            double xFirst = model.getPeakFluxX();

            model.calculate(null);
            double xSecond = model.getPeakFluxX();

            // Re-running with identical inputs gives the same answer; the important
            // thing is that the peak is refreshed, not stale from the prior call.
            assertThat(xSecond).isEqualTo(xFirst);
        }
    }

    // ── calculateFullProfile() — full wall ───────────────────────────────────

    @Nested
    @DisplayName("calculateFullProfile() — full wall (convergent + divergent)")
    class FullProfileCalculation {

        @Test
        @DisplayName("peak is non-null after calculateFullProfile()")
        void peakIsNotNull_afterFullProfile() {
            FullNozzleGeometry fullGeom = new FullNozzleGeometry(standardParams).generate(30, 60);
            HeatTransferModel model = new HeatTransferModel(standardParams, standardContour)
                    .calculateFullProfile(fullGeom, null);

            assertThat(model.getPeakFluxPoint()).isNotNull();
        }

        @Test
        @DisplayName("thermal profile includes convergent-section points (x < 0)")
        void profileIncludesConvergentSection() {
            FullNozzleGeometry fullGeom = new FullNozzleGeometry(standardParams).generate(30, 60);
            HeatTransferModel model = new HeatTransferModel(standardParams, standardContour)
                    .calculateFullProfile(fullGeom, null);

            boolean hasConvergentPoints = model.getWallThermalProfile()
                    .stream()
                    .anyMatch(p -> p.x() < 0.0);

            assertThat(hasConvergentPoints)
                    .as("full-profile calculation must cover convergent section (x < 0)")
                    .isTrue();
        }

        @Test
        @DisplayName("standard Rao params: full-profile peak is downstream of throat (x > 0)")
        void standardParams_fullProfile_peakDownstreamOfThroat() {
            FullNozzleGeometry fullGeom = new FullNozzleGeometry(standardParams).generate(30, 60);
            HeatTransferModel model = new HeatTransferModel(standardParams, standardContour)
                    .calculateFullProfile(fullGeom, null);

            assertThat(model.getPeakFluxX())
                    .as("standard Rao params: full-profile peak should be downstream of throat")
                    .isGreaterThan(0.0);
        }

        @Test
        @DisplayName("tight upstream curvature (r_cu < r_cd): full-profile peak shifts to convergent side (x < 0)")
        void tightUpstreamCurvature_peakOnConvergentSide() {
            // r_cu = 0.2·r_t  <  r_cd = 0.382·r_t
            // Bartz correction (D_t/r_c)^0.1:
            //   convergent side: (2/0.2)^0.1  = 10^0.1 ≈ 1.26  (larger correction)
            //   divergent side:  (2/0.382)^0.1 ≈ 1.18
            // Both the higher h and the higher gas temperature on the convergent side
            // drive the peak to x < 0.
            FullNozzleGeometry tightFullGeom = new FullNozzleGeometry(tightUpstreamParams)
                    .generate(30, 60);
            HeatTransferModel model = new HeatTransferModel(tightUpstreamParams, tightUpstreamContour)
                    .calculateFullProfile(tightFullGeom, null);

            assertThat(model.getPeakFluxX())
                    .as("tight upstream arc (r_cu=0.2·r_t < r_cd=0.382·r_t): " +
                        "peak should be on the convergent side (x < 0)")
                    .isLessThan(0.0);
        }

        @Test
        @DisplayName("full-profile peak x matches the x-coordinate of the peak WallThermalPoint")
        void peakFluxX_matchesPeakPoint() {
            FullNozzleGeometry fullGeom = new FullNozzleGeometry(standardParams).generate(30, 60);
            HeatTransferModel model = new HeatTransferModel(standardParams, standardContour)
                    .calculateFullProfile(fullGeom, null);

            assertThat(model.getPeakFluxX())
                    .isEqualTo(model.getPeakFluxPoint().x());
        }

        @Test
        @DisplayName("full-profile peak total heat flux matches getMaxHeatFlux()")
        void peakTotalFlux_matchesGetMaxHeatFlux() {
            FullNozzleGeometry fullGeom = new FullNozzleGeometry(standardParams).generate(30, 60);
            HeatTransferModel model = new HeatTransferModel(standardParams, standardContour)
                    .calculateFullProfile(fullGeom, null);

            assertThat(model.getPeakFluxPoint().totalHeatFlux())
                    .isEqualTo(model.getMaxHeatFlux());
        }

        @Test
        @DisplayName("calculateFullProfile() replaces an earlier calculate() profile")
        void fullProfile_replacesEarlierDivergentProfile() {
            FullNozzleGeometry fullGeom = new FullNozzleGeometry(standardParams).generate(30, 60);
            HeatTransferModel model = new HeatTransferModel(standardParams, standardContour);

            model.calculate(null);
            int sizeAfterCalculate = model.getWallThermalProfile().size();

            model.calculateFullProfile(fullGeom, null);
            int sizeAfterFullProfile = model.getWallThermalProfile().size();

            // The full profile covers convergent + divergent, so it has more points
            // than the divergent-only calculate().
            assertThat(sizeAfterFullProfile)
                    .as("calculateFullProfile() must replace the divergent-only profile")
                    .isGreaterThan(sizeAfterCalculate);
        }

        @Test
        @DisplayName("no-op when FullNozzleGeometry has not been generated")
        void noOp_whenNotGenerated() {
            FullNozzleGeometry notGenerated = new FullNozzleGeometry(standardParams);
            HeatTransferModel model = new HeatTransferModel(standardParams, standardContour)
                    .calculateFullProfile(notGenerated, null);

            assertThat(model.getWallThermalProfile()).isEmpty();
            assertThat(model.getPeakFluxPoint()).isNull();
        }
    }
}
