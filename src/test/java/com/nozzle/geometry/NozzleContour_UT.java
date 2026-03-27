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
import com.nozzle.moc.CharacteristicPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("NozzleContour Tests")
class NozzleContour_UT {
    
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
    
    @Nested
    @DisplayName("Conical Contour Tests")
    class ConicalContourTests {
        
        @Test
        @DisplayName("Should generate conical contour")
        void shouldGenerateConicalContour() {
            NozzleContour contour = new NozzleContour(NozzleContour.ContourType.CONICAL, params);
            contour.generate(100);
            
            assertThat(contour.getContourPoints()).hasSize(100);
            assertThat(contour.getType()).isEqualTo(NozzleContour.ContourType.CONICAL);
        }
        
        @Test
        @DisplayName("Conical contour should be monotonic")
        void conicalContourShouldBeMonotonic() {
            NozzleContour contour = new NozzleContour(NozzleContour.ContourType.CONICAL, params);
            contour.generate(100);
            
            List<Point2D> points = contour.getContourPoints();
            for (int i = 1; i < points.size(); i++) {
                assertThat(points.get(i).x()).isGreaterThanOrEqualTo(points.get(i-1).x());
            }
        }
    }
    
    @Nested
    @DisplayName("Bell Contour Tests")
    class BellContourTests {
        
        @Test
        @DisplayName("Should generate bell contour")
        void shouldGenerateBellContour() {
            NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
            contour.generate(100);
            
            assertThat(contour.getContourPoints()).hasSize(100);
        }
        
        @Test
        @DisplayName("Bell should start at throat radius")
        void bellShouldStartAtThroatRadius() {
            NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
            contour.generate(100);
            
            Point2D first = contour.getContourPoints().getFirst();
            assertThat(first.y()).isCloseTo(params.throatRadius(), within(0.01));
        }
    }
    
    @Nested
    @DisplayName("Interpolation Tests")
    class InterpolationTests {
        
        @Test
        @DisplayName("Should get radius at position")
        void shouldGetRadiusAtPosition() {
            NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
            contour.generate(100);
            
            double midX = contour.getLength() / 2;
            double radius = contour.getRadiusAt(midX);
            
            assertThat(radius).isBetween(params.throatRadius(), params.exitRadius() * 1.1);
        }
        
        @Test
        @DisplayName("Should get slope at position")
        void shouldGetSlopeAtPosition() {
            NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
            contour.generate(100);
            
            double midX = contour.getLength() / 2;
            double slope = contour.getSlopeAt(midX);
            
            assertThat(slope).isGreaterThanOrEqualTo(0); // Divergent section
        }
        
        @Test
        @DisplayName("Should get angle at position")
        void shouldGetAngleAtPosition() {
            NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
            contour.generate(100);
            
            double midX = contour.getLength() / 2;
            double angle = contour.getAngleAt(midX);
            
            assertThat(Math.toDegrees(angle)).isBetween(-5.0, 45.0);
        }
    }
    
    @Nested
    @DisplayName("Surface Area Tests")
    class SurfaceAreaTests {
        
        @Test
        @DisplayName("Should calculate surface area")
        void shouldCalculateSurfaceArea() {
            NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
            contour.generate(100);
            
            double area = contour.calculateSurfaceArea();
            assertThat(area).isGreaterThan(0);
        }
    }
    
    @Nested
    @DisplayName("Length Tests")
    class LengthTests {

        @Test
        @DisplayName("Should calculate length")
        void shouldCalculateLength() {
            NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
            contour.generate(100);

            double length = contour.getLength();
            assertThat(length).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Spline Contour Tests")
    class SplineContourTests {

        /** Minimal wall point helper — only x and y matter for contour building. */
        private CharacteristicPoint wallPoint(double x, double y) {
            return new CharacteristicPoint(x, y, 2.0, 0.1, 0.5, 0.3,
                    50000, 2000, 0.1, 1500, 0, 0,
                    CharacteristicPoint.PointType.WALL);
        }

        @Test
        @DisplayName("fromMOCWallPoints filters closely-spaced points (FALSE spacing branch)")
        void fromMOCWallPointsFiltersCloselySpacedPoints() {
            // minSpacing = 0.05 * throatRadius(0.05) = 0.0025 m
            // Third point is only 0.0001 m from second → filtered out
            List<CharacteristicPoint> wallPoints = List.of(
                    wallPoint(0.01, 0.060),
                    wallPoint(0.02, 0.070),
                    wallPoint(0.0201, 0.0701),  // gap < minSpacing → skipped
                    wallPoint(0.03, 0.080),
                    wallPoint(0.04, 0.090)
            );

            NozzleContour contour = NozzleContour.fromMOCWallPoints(params, wallPoints);
            contour.generate(50);

            assertThat(contour.getType()).isEqualTo(NozzleContour.ContourType.MOC_GENERATED);
            assertThat(contour.getContourPoints()).hasSize(50);
        }

        @Test
        @DisplayName("fromMOCWallPoints with empty list triggers generateSpline early return")
        void fromMOCWallPointsEmptyListTriggersSplineEarlyReturn() {
            // generateSpline() called with 0 control points → if (size < 2) return
            NozzleContour contour = NozzleContour.fromMOCWallPoints(params, List.of());

            assertThat(contour.getType()).isEqualTo(NozzleContour.ContourType.MOC_GENERATED);
            assertThat(contour.getContourPoints()).isEmpty();
        }

        @Test
        @DisplayName("generate on CUSTOM_SPLINE with no control points hits early return in generateSplineContour")
        void generateCustomSplineWithNoControlPointsHitsEarlyReturn() {
            // Covers CUSTOM_SPLINE switch arm + generateSplineContour size < 2 early return
            NozzleContour contour = new NozzleContour(NozzleContour.ContourType.CUSTOM_SPLINE, params);
            contour.generate(100);

            assertThat(contour.getContourPoints()).isEmpty();
        }

        @Test
        @DisplayName("generate on MOC_GENERATED after fromMOCWallPoints hits FALSE branch of lazy spline check")
        void generateMOCGeneratedSecondCallSkipsLazyGenerateSpline() {
            // fromMOCWallPoints pre-computes spline → splineCoeffA != null
            // Second generate() call → splineCoeffA == null is FALSE → spline reused
            List<CharacteristicPoint> wallPoints = List.of(
                    wallPoint(0.01, 0.060),
                    wallPoint(0.02, 0.070),
                    wallPoint(0.03, 0.080),
                    wallPoint(0.04, 0.090)
            );

            NozzleContour contour = NozzleContour.fromMOCWallPoints(params, wallPoints);
            contour.generate(50);

            assertThat(contour.getContourPoints()).hasSize(50);
        }

        @Test
        @DisplayName("CUSTOM_SPLINE with injected control points triggers lazy generateSpline (TRUE branch)")
        void customSplineWithInjectedControlPointsTriggersLazyGenerateSpline() throws Exception {
            // Public API provides no way to add CUSTOM_SPLINE control points, so use reflection
            // to reach: controlPoints.size() >= 2 AND splineCoeffA == null
            NozzleContour contour = new NozzleContour(NozzleContour.ContourType.CUSTOM_SPLINE, params);

            Field cpField = NozzleContour.class.getDeclaredField("controlPoints");
            cpField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Point2D> cp = (List<Point2D>) cpField.get(contour);
            cp.add(new Point2D(0.01, 0.060));
            cp.add(new Point2D(0.02, 0.070));
            cp.add(new Point2D(0.03, 0.080));

            // splineCoeffA is still null → lazy generateSpline() called inside generateSplineContour
            contour.generate(30);

            assertThat(contour.getContourPoints()).hasSize(30);
        }

        @Test
        @DisplayName("getRadiusAt on CUSTOM_SPLINE triggers auto-generate and evaluateSpline null path")
        void getRadiusAtCustomSplineTriggersAutoGenerateAndEvaluateSplineNull() {
            // contourPoints.isEmpty() → TRUE → generate(100) called (auto-generate branch)
            // CUSTOM_SPLINE with no control points → generateSplineContour early return → still empty
            // type == CUSTOM_SPLINE → TRUE → evaluateSpline with splineX == null → returns 0
            NozzleContour contour = new NozzleContour(NozzleContour.ContourType.CUSTOM_SPLINE, params);

            double r = contour.getRadiusAt(0.02);

            assertThat(r).isEqualTo(0.0);
        }

        @Test
        @DisplayName("getRadiusAt on MOC_GENERATED uses spline evaluation path")
        void getRadiusAtMOCGeneratedUsesSplineEvaluationPath() {
            // type == MOC_GENERATED → evaluateSpline() path (not linear interpolation)
            List<CharacteristicPoint> wallPoints = List.of(
                    wallPoint(0.01, 0.060),
                    wallPoint(0.02, 0.070),
                    wallPoint(0.03, 0.080),
                    wallPoint(0.04, 0.090)
            );

            NozzleContour contour = NozzleContour.fromMOCWallPoints(params, wallPoints);
            contour.generate(50);
            double r = contour.getRadiusAt(0.025);

            assertThat(r).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("getRadiusAt before first spline knot covers x >= splineX[j] FALSE branch")
        void getRadiusAtBeforeFirstKnotCoversLeftOperandFalseBranch() {
            // x < splineX[0]: every loop iteration hits x >= splineX[j] == FALSE (short circuit)
            List<CharacteristicPoint> wallPoints = List.of(
                    wallPoint(0.01, 0.060),
                    wallPoint(0.02, 0.070),
                    wallPoint(0.03, 0.080),
                    wallPoint(0.04, 0.090)
            );

            NozzleContour contour = NozzleContour.fromMOCWallPoints(params, wallPoints);
            contour.generate(50);
            // x = 0.005 < splineX[0] = 0.01 → all j iterations: a=false → short-circuit
            double r = contour.getRadiusAt(0.005);

            // Extrapolation result: the spline evaluates with dx < 0, a finite value is returned
            assertThat(r).isNotNaN();
        }
    }

    @Nested
    @DisplayName("Short-circuit Branch Tests")
    class ShortCircuitBranchTests {

        @Test
        @DisplayName("getRadiusAt beyond last contour point returns exitRadius")
        void getRadiusAtBeyondLastPointReturnsExitRadius() {
            // Falls through the entire interpolation loop → return parameters.exitRadius()
            NozzleContour contour = new NozzleContour(NozzleContour.ContourType.CONICAL, params);
            contour.generate(100);

            double r = contour.getRadiusAt(1000.0);

            assertThat(r).isCloseTo(params.exitRadius(), within(1e-10));
        }

        @Test
        @DisplayName("getLength returns 0 when no contour points have been generated")
        void getLengthReturnsZeroWhenNoPointsGenerated() {
            NozzleContour contour = new NozzleContour(NozzleContour.ContourType.CONICAL, params);

            assertThat(contour.getLength()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("calculateSurfaceArea returns 0 when fewer than 2 contour points")
        void calculateSurfaceAreaReturnsZeroWhenFewerThanTwoPoints() {
            NozzleContour contour = new NozzleContour(NozzleContour.ContourType.CONICAL, params);

            assertThat(contour.calculateSurfaceArea()).isEqualTo(0.0);
        }
    }
}
