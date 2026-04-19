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

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DualBellNozzle}.
 *
 * <p>Tests verify constructor contracts, geometric invariants, kink detectability,
 * and the key physics requirement that high-altitude Isp exceeds sea-level Isp.
 * Expected numeric ranges are derived from published dual-bell literature
 * (Hagemann et al., AIAA-96-2958; Immich &amp; Caporicci, AIAA-96-3008) and
 * are intentionally wide to accommodate the parabolic BÃ©zier approximation.
 *
 * <p>Section 7 adds a constant-Î³ analytical verification that cross-checks the
 * thrust-coefficient formula against a hand-computable isentropic result.  The
 * geometry mirrors the DLR subscale nozzle characterized by GÃ©nin &amp; Stark
 * (<em>Shock Waves</em> 19, 265â€“270, 2009): R_th = 9 mm, Îµ_b = 3.9, Îµ_e â‰ˆ 7.1,
 * kink angle = 15Â°, cold-flow Nâ‚‚ (Î³ = 1.4).
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
            // Use an explicit kink angle of 5Â° to make the dip clearly detectable.
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
        @DisplayName("Sea-level Isp is in a physically plausible range (100â€“500 s)")
        void seaLevelIspPlausible() {
            DualBellNozzle nozzle = new DualBellNozzle(params, 4.0).generate();
            assertThat(nozzle.getSeaLevelIsp()).isBetween(100.0, 500.0);
        }

        @Test
        @DisplayName("High-altitude Isp is in a physically plausible range (100â€“600 s)")
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
     * bell has Isp â‰ˆ 136 s at sea level; the dual-bell avoids this by separating
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

    // =========================================================================
    //  7. calculateBaseAngles â€” branch coverage for lf âˆˆ [0.6, 0.8) and lf < 0.6
    // =========================================================================

    /**
     * calculateBaseAngles() is private but fully observable through
     * getInflectionAngle() and getBaseExitAngle().  The method has three
     * branches keyed on baseLengthFraction (lf):
     * <ul>
     *   <li>lf â‰¥ 0.8  â€” inflection = 21 + 3(Mâˆ’2)Â°,  exit = 8 âˆ’ 0.5Â·ln(AR)</li>
     *   <li>lf â‰¥ 0.6  â€” inflection = 25 + 4(Mâˆ’2)Â°,  exit = 11 âˆ’ 0.7Â·ln(AR)</li>
     *   <li>default   â€” inflection = 30 + 5(Mâˆ’2)Â°,  exit = 14 âˆ’ 0.9Â·ln(AR)</li>
     * </ul>
     * Both outputs are clamped: inflectionAngle âˆˆ [15Â°, 45Â°],
     * baseExitAngle â‰¥ 1Â°.  The existing tests only exercise the lf â‰¥ 0.8
     * branch; this class covers the remaining two.
     */
    @Nested
    @DisplayName("calculateBaseAngles â€” lf âˆˆ [0.6, 0.8) and lf < 0.6 branches")
    class CalculateBaseAnglesTests {

        /** transitionAreaRatio used across all branch tests. */
        private static final double AR = 4.0;

        // ---- helpers --------------------------------------------------------

        /** Mach number at the kink (same formula the nozzle uses internally). */
        private double transitionMach() {
            return params.gasProperties().machFromAreaRatio(AR);
        }

        private DualBellNozzle buildWith(double baseLf) {
            return new DualBellNozzle(
                    params, AR, baseLf, 0.8, Math.toRadians(3.0), 200).generate();
        }

        // ---- lf âˆˆ [0.6, 0.8) branch ----------------------------------------

        @Test
        @DisplayName("lf=0.7: inflectionAngle matches 25 + 4*(Mâˆ’2)Â° formula")
        void lf07InflectionAngleMatchesFormula() {
            DualBellNozzle nozzle = buildWith(0.7);
            double mach     = transitionMach();
            double expected = Math.clamp(
                    Math.toRadians(25.0 + 4.0 * (mach - 2.0)),
                    Math.toRadians(15.0), Math.toRadians(45.0));
            assertThat(nozzle.getInflectionAngle()).isCloseTo(expected, within(1e-9));
        }

        @Test
        @DisplayName("lf=0.7: baseExitAngle matches 11 âˆ’ 0.7Â·ln(AR) formula")
        void lf07BaseExitAngleMatchesFormula() {
            DualBellNozzle nozzle = buildWith(0.7);
            double expected = Math.max(
                    Math.toRadians(11.0 - 0.7 * Math.log(AR)),
                    Math.toRadians(1.0));
            assertThat(nozzle.getBaseExitAngle()).isCloseTo(expected, within(1e-9));
        }

        @Test
        @DisplayName("lf=0.7: inflectionAngle is larger than lf=0.8 value (shorter bell â†’ steeper initial angle)")
        void lf07InflectionLargerThanLf08() {
            assertThat(buildWith(0.7).getInflectionAngle())
                    .isGreaterThan(buildWith(0.8).getInflectionAngle());
        }

        @Test
        @DisplayName("lf=0.7: baseExitAngle is larger than lf=0.8 value (wider exit for shorter base bell)")
        void lf07BaseExitAngleLargerThanLf08() {
            assertThat(buildWith(0.7).getBaseExitAngle())
                    .isGreaterThan(buildWith(0.8).getBaseExitAngle());
        }

        @Test
        @DisplayName("lf=0.7: nozzle generates a valid contour (Isp physics intact)")
        void lf07GeneratesValidContour() {
            DualBellNozzle nozzle = buildWith(0.7);
            assertThat(nozzle.getContourPoints()).hasSizeGreaterThan(10);
            assertThat(nozzle.getHighAltitudeIsp()).isGreaterThan(nozzle.getSeaLevelIsp());
        }

        // ---- lf < 0.6 (default) branch -------------------------------------

        @Test
        @DisplayName("lf=0.4: inflectionAngle matches 30 + 5*(Mâˆ’2)Â° formula")
        void lf04InflectionAngleMatchesFormula() {
            DualBellNozzle nozzle = buildWith(0.4);
            double mach     = transitionMach();
            double expected = Math.clamp(
                    Math.toRadians(30.0 + 5.0 * (mach - 2.0)),
                    Math.toRadians(15.0), Math.toRadians(45.0));
            assertThat(nozzle.getInflectionAngle()).isCloseTo(expected, within(1e-9));
        }

        @Test
        @DisplayName("lf=0.4: baseExitAngle matches 14 âˆ’ 0.9Â·ln(AR) formula")
        void lf04BaseExitAngleMatchesFormula() {
            DualBellNozzle nozzle = buildWith(0.4);
            double expected = Math.max(
                    Math.toRadians(14.0 - 0.9 * Math.log(AR)),
                    Math.toRadians(1.0));
            assertThat(nozzle.getBaseExitAngle()).isCloseTo(expected, within(1e-9));
        }

        @Test
        @DisplayName("lf=0.4: inflectionAngle is larger than lf=0.7 value (steeper still)")
        void lf04InflectionLargerThanLf07() {
            assertThat(buildWith(0.4).getInflectionAngle())
                    .isGreaterThan(buildWith(0.7).getInflectionAngle());
        }

        @Test
        @DisplayName("lf=0.4: baseExitAngle is larger than lf=0.7 value (wider exit)")
        void lf04BaseExitAngleLargerThanLf07() {
            assertThat(buildWith(0.4).getBaseExitAngle())
                    .isGreaterThan(buildWith(0.7).getBaseExitAngle());
        }

        @Test
        @DisplayName("lf=0.4: nozzle generates a valid contour (Isp physics intact)")
        void lf04GeneratesValidContour() {
            DualBellNozzle nozzle = buildWith(0.4);
            assertThat(nozzle.getContourPoints()).hasSizeGreaterThan(10);
            assertThat(nozzle.getHighAltitudeIsp()).isGreaterThan(nozzle.getSeaLevelIsp());
        }

        // ---- three-way monotonicity -----------------------------------------

        @Test
        @DisplayName("inflectionAngle is strictly ordered: lf=0.4 > lf=0.7 > lf=0.85")
        void inflectionAngleMonotonicWithLf() {
            double a04 = buildWith(0.4).getInflectionAngle();
            double a07 = buildWith(0.7).getInflectionAngle();
            double a85 = buildWith(0.85).getInflectionAngle();
            assertThat(a04).isGreaterThan(a07);
            assertThat(a07).isGreaterThan(a85);
        }

        @Test
        @DisplayName("baseExitAngle is strictly ordered: lf=0.4 > lf=0.7 > lf=0.85")
        void baseExitAngleMonotonicWithLf() {
            double e04 = buildWith(0.4).getBaseExitAngle();
            double e07 = buildWith(0.7).getBaseExitAngle();
            double e85 = buildWith(0.85).getBaseExitAngle();
            assertThat(e04).isGreaterThan(e07);
            assertThat(e07).isGreaterThan(e85);
        }
    }

    // =========================================================================
    //  8. calculateExtensionAngles â€” branch coverage for lf âˆˆ [0.6, 0.8) and lf < 0.6
    // =========================================================================

    /**
     * calculateExtensionAngles() is private but fully observable through
     * getExtensionExitAngle().  It mirrors calculateBaseAngles() but uses
     * extensionLengthFraction and exitAreaRatio:
     * <ul>
     *   <li>lf â‰¥ 0.8  â€” exit =  8 âˆ’ 0.5Â·ln(exitAR)</li>
     *   <li>lf â‰¥ 0.6  â€” exit = 11 âˆ’ 0.7Â·ln(exitAR)</li>
     *   <li>default   â€” exit = 14 âˆ’ 0.9Â·ln(exitAR)</li>
     * </ul>
     * extensionExitAngle is clamped to â‰¥ 1Â°.  All existing tests pass
     * extensionLengthFraction = 0.8 and therefore only exercise the first branch.
     */
    @Nested
    @DisplayName("calculateExtensionAngles â€” lf âˆˆ [0.6, 0.8) and lf < 0.6 branches")
    class CalculateExtensionAnglesTests {

        private static final double AR_TRANS = 4.0;

        private DualBellNozzle buildWith(double extLf) {
            return new DualBellNozzle(
                    params, AR_TRANS, 0.8, extLf, Math.toRadians(3.0), 200).generate();
        }

        private double expectedAngle(double coefficient, double constant) {
            double ln = Math.log(params.exitAreaRatio());
            return Math.max(Math.toRadians(constant - coefficient * ln), Math.toRadians(1.0));
        }

        // ---- lf âˆˆ [0.6, 0.8) branch ----------------------------------------

        @Test
        @DisplayName("lf=0.7: extensionExitAngle matches 11 âˆ’ 0.7Â·ln(exitAR) formula")
        void lf07MatchesFormula() {
            assertThat(buildWith(0.7).getExtensionExitAngle())
                    .isCloseTo(expectedAngle(0.7, 11.0), within(1e-9));
        }

        @Test
        @DisplayName("lf=0.7: extensionExitAngle is larger than lf=0.8 value")
        void lf07LargerThanLf08() {
            assertThat(buildWith(0.7).getExtensionExitAngle())
                    .isGreaterThan(buildWith(0.8).getExtensionExitAngle());
        }

        @Test
        @DisplayName("lf=0.7: nozzle generates a valid contour (Isp physics intact)")
        void lf07GeneratesValidContour() {
            DualBellNozzle nozzle = buildWith(0.7);
            assertThat(nozzle.getContourPoints()).hasSizeGreaterThan(10);
            assertThat(nozzle.getHighAltitudeIsp()).isGreaterThan(nozzle.getSeaLevelIsp());
        }

        // ---- lf < 0.6 (default) branch -------------------------------------

        @Test
        @DisplayName("lf=0.4: extensionExitAngle matches 14 âˆ’ 0.9Â·ln(exitAR) formula")
        void lf04MatchesFormula() {
            assertThat(buildWith(0.4).getExtensionExitAngle())
                    .isCloseTo(expectedAngle(0.9, 14.0), within(1e-9));
        }

        @Test
        @DisplayName("lf=0.4: extensionExitAngle is larger than lf=0.7 value")
        void lf04LargerThanLf07() {
            assertThat(buildWith(0.4).getExtensionExitAngle())
                    .isGreaterThan(buildWith(0.7).getExtensionExitAngle());
        }

        @Test
        @DisplayName("lf=0.4: nozzle generates a valid contour (Isp physics intact)")
        void lf04GeneratesValidContour() {
            DualBellNozzle nozzle = buildWith(0.4);
            assertThat(nozzle.getContourPoints()).hasSizeGreaterThan(10);
            assertThat(nozzle.getHighAltitudeIsp()).isGreaterThan(nozzle.getSeaLevelIsp());
        }

        // ---- three-way monotonicity ----------------------------------------

        @Test
        @DisplayName("extensionExitAngle is strictly ordered: lf=0.4 > lf=0.7 > lf=0.85")
        void extensionExitAngleMonotonicWithLf() {
            double e04 = buildWith(0.4).getExtensionExitAngle();
            double e07 = buildWith(0.7).getExtensionExitAngle();
            double e85 = buildWith(0.85).getExtensionExitAngle();
            assertThat(e04).isGreaterThan(e07);
            assertThat(e07).isGreaterThan(e85);
        }
    }

    // =========================================================================
    //  9. Constant-Î³ analytical verification (formerly section 7)
    //     Geometry: DLR subscale nozzle (GÃ©nin & Stark, Shock Waves 2009)
    //       R_th = 9 mm  |  Îµ_b = 3.9  |  Îµ_e â‰ˆ 7.1  |  kink 15Â°  |  Î³ = 1.4
    //
    //  Cross-checks DualBellNozzle.computePerformance() against a hand-derived
    //  isentropic Cf formula using only Î³ and the area-ratio Mach numbers.
    //  The formula is evaluated independently in static helper methods below
    //  and compared against the model output to within 0.5 %.
    // =========================================================================

    @Nested
    @DisplayName("Constant-Î³ Cf analytical verification (DLR subscale geometry, Î³=1.4)")
    class ConstantGammaCfVerification {

        // --- DLR subscale geometry ------------------------------------------
        // GÃ©nin & Stark, Shock Waves 19, 265â€“270 (2009), Table 1
        //   R_th = 9 mm,  Îµ_b = 3.9,  Îµ_e â‰ˆ 7.1  (M_exit â‰ˆ 3.5504 for Î³=1.4)
        //   kink angle Î± = 15Â°,  cold-flow Nâ‚‚  (Î³ = 1.4)
        // --------------------------------------------------------------------
        private static final double EPS_B  = 3.9;      // base area ratio (kink)
        private static final double M_EXIT = 3.5504;   // gives Îµ_e â‰ˆ 7.104 for Î³=1.4
        private static final double PC     = 1_000_000.0; // 1 MPa (Cf is pc-independent)
        private static final double PA     =    10_000.0; // 0.1 atm ambient

        private DualBellNozzle nozzle;

        @BeforeEach
        void buildNozzle() {
            NozzleDesignParameters p = NozzleDesignParameters.builder()
                    .throatRadius(0.009)
                    .exitMach(M_EXIT)
                    .chamberPressure(PC)
                    .chamberTemperature(293.0)
                    .ambientPressure(PA)
                    .gasProperties(GasProperties.NITROGEN)
                    .numberOfCharLines(20)
                    .wallAngleInitialDegrees(30.0)
                    .lengthFraction(0.8)
                    .axisymmetric(false)
                    .build();

            nozzle = new DualBellNozzle(p, EPS_B,
                    0.8, 0.8, Math.toRadians(15.0), 200).generate();
        }

        // --- Independent formula helpers (Î³ = 1.4 only) ---------------------

        /**
         * Isentropic total-to-static pressure ratio p/pâ‚€ for Î³ = 1.4:
         * {@code p/pâ‚€ = (1 + 0.2 MÂ²)^(âˆ’3.5)}.
         */
        private static double pressureRatio14(double mach) {
            return Math.pow(1.0 + 0.2 * mach * mach, -3.5);
        }

        /**
         * Rocket momentum Cf term (before divergence factor) for Î³ = 1.4:
         * {@code cfMom = sqrt(term1 Ã— (1 âˆ’ (p/pâ‚€)^(2/7)))}
         * where {@code term1 = 2Î³Â²/(Î³âˆ’1) Ã— (2/(Î³+1))^((Î³+1)/(Î³âˆ’1)) = 9.8 Ã— (5/6)^6}.
         */
        private static double cfMomentum14(double pRatio) {
            // For Î³ = 1.4:
            //   2Î³Â²/(Î³âˆ’1) = 2Ã—1.96/0.4 = 9.8
            //   (2/(Î³+1))^((Î³+1)/(Î³âˆ’1)) = (2/2.4)^6 = (5/6)^6
            final double TERM1 = 9.8 * Math.pow(5.0 / 6.0, 6.0);
            return Math.sqrt(TERM1 * (1.0 - Math.pow(pRatio, 2.0 / 7.0)));
        }

        // --- Tests ----------------------------------------------------------

        @Test
        @DisplayName("Transition Mach matches machFromAreaRatio(Îµ_b) for Î³=1.4")
        void transitionMachMatchesIsentropic() {
            double expected = GasProperties.NITROGEN.machFromAreaRatio(EPS_B);
            assertThat(nozzle.getTransitionMach()).isCloseTo(expected, within(1e-6));
        }

        @Test
        @DisplayName("Sea-level Cf matches constant-Î³ isentropic formula (< 0.5 %)")
        void seaLevelCfMatchesFormula() {
            double mKink   = nozzle.getTransitionMach();
            double pKink   = pressureRatio14(mKink);

            double cfMom   = cfMomentum14(pKink);
            double lambda  = (1.0 + Math.cos(nozzle.getBaseExitAngle())) / 2.0;
            double cfPres  = (pKink - PA / PC) * EPS_B;
            double expected = lambda * cfMom + cfPres;

            assertThat(nozzle.getSeaLevelCf())
                    .isCloseTo(expected, within(expected * 0.005));
        }

        @Test
        @DisplayName("High-altitude Cf matches constant-Î³ isentropic formula (< 0.5 %)")
        void highAltCfMatchesFormula() {
            double mExit  = M_EXIT;
            double pExit  = pressureRatio14(mExit);
            double exitAR = GasProperties.NITROGEN.areaRatio(mExit);

            double cfMom  = cfMomentum14(pExit);
            double lambda = (1.0 + Math.cos(nozzle.getExtensionExitAngle())) / 2.0;
            double cfPres = pExit * exitAR;   // pa = 0 in altitude mode
            double expected = lambda * cfMom + cfPres;

            assertThat(nozzle.getHighAltitudeCf())
                    .isCloseTo(expected, within(expected * 0.005));
        }

        @Test
        @DisplayName("Sea-level Cf < high-altitude Cf for DLR subscale geometry")
        void slCfLessThanAltCf() {
            assertThat(nozzle.getSeaLevelCf()).isLessThan(nozzle.getHighAltitudeCf());
        }
    }
}
