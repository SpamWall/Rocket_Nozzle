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
import com.nozzle.geometry.Point2D;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DualBellNozzle}.
 *
 * <p>Tests verify constructor contracts, geometric invariants, kink detectability,
 * and the key physics requirement that high-altitude Isp exceeds sea-level Isp.
 * Expected numeric ranges are derived from published dual-bell literature
 * (Hagemann et al., AIAA-96-2958; Immich &amp; Caporicci, AIAA-96-3008) and
 * are intentionally wide to accommodate the parabolic Bézier approximation.
 *
 * <h2>Shared design conditions</h2>
 * <pre>
 *   r_throat = 50 mm,  Me = 5.0,  Pc = 7 MPa,  Tc = 3500 K,  Pa = 101 325 Pa
 *   transitionAreaRatio = 4.0  (kink at A/A* = 4)
 * </pre>
 */
@DisplayName("DualBellNozzle")
class DualBellNozzle_UT {

    /** Baseline LOX/RP-1 design with exit Mach 5 to give meaningful extension. */
    private NozzleDesignParameters params;

    @BeforeEach
    void setUp() {
        params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(5.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101_325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();
    }

    // =========================================================================
    //  1. Constructor validation
    // =========================================================================

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("transitionAreaRatio below minimum throws")
        void belowMinimumThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new DualBellNozzle(params, 1.0));
        }

        @Test
        @DisplayName("transitionAreaRatio >= exitAreaRatio throws")
        void aboveExitArThrows() {
            double exitAR = params.exitAreaRatio();
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new DualBellNozzle(params, exitAR));
        }

        @Test
        @DisplayName("negative kinkAngle throws")
        void negativeKinkAngleThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new DualBellNozzle(
                            params, 4.0, 0.8, 0.8, -0.01, 200));
        }

        @Test
        @DisplayName("zero baseLengthFraction throws")
        void zeroBaseLengthFractionThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new DualBellNozzle(
                            params, 4.0, 0.0, 0.8, Math.toRadians(3), 200));
        }

        @Test
        @DisplayName("valid construction does not throw")
        void validConstructionSucceeds() {
            assertThatNoException()
                    .isThrownBy(() -> new DualBellNozzle(params, 4.0));
        }
    }

    // =========================================================================
    //  2. Contour geometry
    // =========================================================================

    @Nested
    @DisplayName("Contour geometry")
    class ContourGeometry {

        @Test
        @DisplayName("generate() produces a non-empty contour")
        void contourIsNotEmpty() {
            List<Point2D> pts = new DualBellNozzle(params, 4.0).generate().getContourPoints();
            assertThat(pts).hasSizeGreaterThan(50);
        }

        @Test
        @DisplayName("First point is at the throat radius")
        void firstPointAtThroatRadius() {
            List<Point2D> pts = new DualBellNozzle(params, 4.0).generate().getContourPoints();
            assertThat(pts.getFirst().y()).isCloseTo(params.throatRadius(), within(1e-3));
        }

        @Test
        @DisplayName("Last point is at the exit radius")
        void lastPointAtExitRadius() {
            List<Point2D> pts = new DualBellNozzle(params, 4.0).generate().getContourPoints();
            assertThat(pts.getLast().y()).isCloseTo(params.exitRadius(), within(1e-3));
        }

        @Test
        @DisplayName("First point x-coordinate is near zero (throat)")
        void firstPointNearThroat() {
            List<Point2D> pts = new DualBellNozzle(params, 4.0).generate().getContourPoints();
            assertThat(pts.getFirst().x()).isCloseTo(0.0, within(0.01));
        }

        @Test
        @DisplayName("All x-coordinates are monotonically non-decreasing")
        void xCoordinatesMonotonicallyNonDecreasing() {
            List<Point2D> pts = new DualBellNozzle(params, 4.0).generate().getContourPoints();
            for (int i = 1; i < pts.size(); i++) {
                assertThat(pts.get(i).x())
                        .as("x at index %d must be >= x at %d", i, i - 1)
                        .isGreaterThanOrEqualTo(pts.get(i - 1).x());
            }
        }

        @Test
        @DisplayName("All y-coordinates (radii) are positive")
        void allRadiiPositive() {
            List<Point2D> pts = new DualBellNozzle(params, 4.0).generate().getContourPoints();
            for (Point2D pt : pts) {
                assertThat(pt.y()).as("radius at x=%.4f must be positive", pt.x()).isPositive();
            }
        }

        @Test
        @DisplayName("baseLength < totalLength")
        void baseLengthSmallerThanTotalLength() {
            DualBellNozzle nozzle = new DualBellNozzle(params, 4.0).generate();
            assertThat(nozzle.getBaseLength()).isLessThan(nozzle.getTotalLength());
        }

        @Test
        @DisplayName("transitionRadius is between throat radius and exit radius")
        void transitionRadiusBetweenThroatAndExit() {
            DualBellNozzle nozzle = new DualBellNozzle(params, 4.0).generate();
            assertThat(nozzle.getTransitionRadius())
                    .isGreaterThan(params.throatRadius())
                    .isLessThan(params.exitRadius());
        }

        @Test
        @DisplayName("kinkIndex is interior to the contour")
        void kinkIndexIsInterior() {
            DualBellNozzle nozzle = new DualBellNozzle(params, 4.0).generate();
            int ki = nozzle.getKinkIndex();
            assertThat(ki).isGreaterThan(0)
                          .isLessThan(nozzle.getContourPoints().size() - 1);
        }

        @Test
        @DisplayName("Point at kinkIndex is at transitionRadius")
        void kinkPointAtTransitionRadius() {
            DualBellNozzle nozzle = new DualBellNozzle(params, 4.0).generate();
            Point2D kinkPt = nozzle.getContourPoints().get(nozzle.getKinkIndex());
            assertThat(kinkPt.y())
                    .isCloseTo(nozzle.getTransitionRadius(), within(1e-4));
        }

        @Test
        @DisplayName("transitionMach is between 1.0 and exit Mach")
        void transitionMachInRange() {
            DualBellNozzle nozzle = new DualBellNozzle(params, 4.0).generate();
            assertThat(nozzle.getTransitionMach())
                    .isGreaterThan(1.0)
                    .isLessThan(params.exitMach());
        }
    }

    // =========================================================================
    //  3. Kink detectability
    // =========================================================================

    /**
     * The kink is the defining geometric feature of a dual-bell nozzle.
     * At the kink the wall slope changes sign (base bell flares outward,
     * extension briefly turns inward).  This is verified by checking that
     * the radius briefly decreases just past the kink point.
     */
    @Nested
    @DisplayName("Kink geometry")
    class KinkGeometry {

        @Test
        @DisplayName("Wall radius decreases briefly after the kink (inward re-inflection)")
        void radiusDecreasesPastKink() {
            // Use an explicit kink angle of 5° to make the dip clearly detectable.
            DualBellNozzle nozzle = new DualBellNozzle(
                    params, 4.0, 0.8, 0.8, Math.toRadians(5.0), 200).generate();
            List<Point2D> pts = nozzle.getContourPoints();
            int ki = nozzle.getKinkIndex();

            // Find the minimum radius in the extension section
            double minRadiusAfterKink = pts.get(ki).y();
            for (int i = ki + 1; i < pts.size(); i++) {
                minRadiusAfterKink = Math.min(minRadiusAfterKink, pts.get(i).y());
            }
            // The minimum must be strictly less than the kink radius
            assertThat(minRadiusAfterKink).isLessThan(nozzle.getTransitionRadius());
        }

        @Test
        @DisplayName("Zero kink angle produces no inward dip (wall is monotonically non-decreasing)")
        void zeroKinkAngleNoInwardDip() {
            DualBellNozzle nozzle = new DualBellNozzle(
                    params, 4.0, 0.8, 0.8, 0.0, 200).generate();
            List<Point2D> pts = nozzle.getContourPoints();
            int ki = nozzle.getKinkIndex();

            double kinkR = pts.get(ki).y();
            for (int i = ki + 1; i < pts.size(); i++) {
                assertThat(pts.get(i).y())
                        .as("radius at index %d must be >= kink radius for zero kink angle", i)
                        .isGreaterThanOrEqualTo(kinkR - 1e-9);
            }
        }
    }

    // =========================================================================
    //  4. Performance physics
    // =========================================================================

    @Nested
    @DisplayName("Performance physics")
    class PerformancePhysics {

        @Test
        @DisplayName("High-altitude Isp exceeds sea-level Isp (key physics)")
        void highAltIspExceedsSeaLevelIsp() {
            DualBellNozzle nozzle = new DualBellNozzle(params, 4.0).generate();
            assertThat(nozzle.getHighAltitudeIsp()).isGreaterThan(nozzle.getSeaLevelIsp());
        }

        @Test
        @DisplayName("PerformanceSummary.ispGain() is positive")
        void ispGainIsPositive() {
            DualBellNozzle.PerformanceSummary s =
                    new DualBellNozzle(params, 4.0).generate().getPerformanceSummary();
            assertThat(s.ispGain()).isPositive();
        }

        @Test
        @DisplayName("Both Cf values are positive")
        void bothCfPositive() {
            DualBellNozzle nozzle = new DualBellNozzle(params, 4.0).generate();
            assertThat(nozzle.getSeaLevelCf()).isPositive();
            assertThat(nozzle.getHighAltitudeCf()).isPositive();
        }

        @Test
        @DisplayName("Sea-level Isp is in a physically plausible range (100–500 s)")
        void seaLevelIspPlausible() {
            DualBellNozzle nozzle = new DualBellNozzle(params, 4.0).generate();
            assertThat(nozzle.getSeaLevelIsp()).isBetween(100.0, 500.0);
        }

        @Test
        @DisplayName("High-altitude Isp is in a physically plausible range (100–600 s)")
        void highAltIspPlausible() {
            DualBellNozzle nozzle = new DualBellNozzle(params, 4.0).generate();
            assertThat(nozzle.getHighAltitudeIsp()).isBetween(100.0, 600.0);
        }

        @Test
        @DisplayName("Transition pressure is between 0 and sea-level ambient")
        void transitionPressureBelowSeaLevel() {
            DualBellNozzle nozzle = new DualBellNozzle(params, 4.0).generate();
            assertThat(nozzle.getTransitionPressure())
                    .isGreaterThan(0.0)
                    .isLessThan(params.ambientPressure());
        }

        @Test
        @DisplayName("Higher transitionAreaRatio gives better sea-level Isp (more expansion at SL)")
        void higherTransitionArImprovesSLIsp() {
            double exitAR = params.exitAreaRatio();
            // Use transitionAR = 3 vs 6 (both well below exitAR)
            DualBellNozzle low  = new DualBellNozzle(params, 3.0).generate();
            DualBellNozzle high = new DualBellNozzle(params, Math.min(6.0, exitAR - 1.0)).generate();
            assertThat(high.getSeaLevelIsp()).isGreaterThan(low.getSeaLevelIsp());
        }
    }

    // =========================================================================
    //  5. Comparison with single Rao bell
    // =========================================================================

    /**
     * The dual-bell altitude-compensation benefit is most visible at high exit
     * Mach (large area ratio) where a single full Rao bell is badly over-expanded
     * at sea level.  At Me = 5 the LOX/RP-1 area ratio is ~76 and the full Rao
     * bell has Isp ≈ 136 s at sea level; the dual-bell avoids this by separating
     * at the kink (AR = 4) and achieves far better sea-level performance.
     */
    @Nested
    @DisplayName("Comparison with RaoNozzle")
    class RaoComparison {

        @Test
        @DisplayName("Dual-bell sea-level Isp exceeds full Rao bell sea-level Isp (altitude compensation benefit)")
        void seaLevelIspExceedsFullRaoAtSeaLevel() {
            DualBellNozzle dual = new DualBellNozzle(params, 4.0).generate();
            RaoNozzle rao = new RaoNozzle(params).generate();
            // Full Rao at sea level is massively over-expanded; dual-bell separates at the kink.
            double raoIsp = rao.calculateThrustCoefficient()
                            * params.characteristicVelocity() / 9.80665;
            assertThat(dual.getSeaLevelIsp()).isGreaterThan(raoIsp);
        }

        @Test
        @DisplayName("Dual-bell high-altitude Isp exceeds full Rao bell sea-level Isp")
        void highAltIspExceedsFullRaoSeaLevelIsp() {
            DualBellNozzle dual = new DualBellNozzle(params, 4.0).generate();
            RaoNozzle rao = new RaoNozzle(params).generate();
            double raoIsp = rao.calculateThrustCoefficient()
                            * params.characteristicVelocity() / 9.80665;
            assertThat(dual.getHighAltitudeIsp()).isGreaterThan(raoIsp);
        }
    }

    // =========================================================================
    //  6. Default constructor behaviour
    // =========================================================================

    @Nested
    @DisplayName("Default constructor")
    class DefaultConstructor {

        @Test
        @DisplayName("Default transitionAreaRatio is sqrt(exitAreaRatio)")
        void defaultTransitionArIsGeometricMean() {
            double exitAR     = params.exitAreaRatio();
            double expectedAR = Math.sqrt(exitAR);
            DualBellNozzle nozzle = new DualBellNozzle(params).generate();

            // Verify the transition radius corresponds to sqrt(exitAR)
            double expectedR = params.throatRadius() * Math.sqrt(expectedAR);
            assertThat(nozzle.getTransitionRadius())
                    .isCloseTo(expectedR, within(1e-6));
        }

        @Test
        @DisplayName("Default constructor generates valid contour")
        void defaultConstructorGeneratesValidContour() {
            DualBellNozzle nozzle = new DualBellNozzle(params).generate();
            assertThat(nozzle.getContourPoints()).hasSizeGreaterThan(10);
            assertThat(nozzle.getHighAltitudeIsp()).isGreaterThan(nozzle.getSeaLevelIsp());
        }
    }
}
