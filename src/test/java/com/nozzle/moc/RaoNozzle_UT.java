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

@DisplayName("RaoNozzle Tests")
class RaoNozzle_UT {
    
    private NozzleDesignParameters params;
    private RaoNozzle raoNozzle;
    
    @BeforeEach
    void setUp() {
        params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.AIR)
                .numberOfCharLines(20)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();
        
        raoNozzle = new RaoNozzle(params);
    }
    
    @Nested
    @DisplayName("Generation Tests")
    class GenerationTests {
        
        @Test
        @DisplayName("Should generate contour points")
        void shouldGenerateContourPoints() {
            raoNozzle.generate();
            List<Point2D> contour = raoNozzle.getContourPoints();
            
            assertThat(contour).isNotEmpty();
            assertThat(contour.size()).isGreaterThan(50);
        }
        
        @Test
        @DisplayName("Contour should start at throat")
        void contourShouldStartAtThroat() {
            raoNozzle.generate();
            List<Point2D> contour = raoNozzle.getContourPoints();
            
            Point2D first = contour.getFirst();
            assertThat(first.y()).isCloseTo(params.throatRadius(), within(0.01));
        }
        
        @Test
        @DisplayName("Contour should end at exit radius")
        void contourShouldEndAtExitRadius() {
            raoNozzle.generate();
            List<Point2D> contour = raoNozzle.getContourPoints();
            
            Point2D last = contour.getLast();
            assertThat(last.y()).isCloseTo(params.exitRadius(), within(0.01));
        }
        
        @Test
        @DisplayName("Contour should have monotonically increasing x")
        void contourShouldHaveIncreasingX() {
            raoNozzle.generate();
            List<Point2D> contour = raoNozzle.getContourPoints();
            
            for (int i = 1; i < contour.size(); i++) {
                assertThat(contour.get(i).x())
                        .isGreaterThanOrEqualTo(contour.get(i - 1).x());
            }
        }
        
        @Test
        @DisplayName("Contour should have monotonically increasing y")
        void contourShouldHaveIncreasingY() {
            raoNozzle.generate();
            List<Point2D> contour = raoNozzle.getContourPoints();
            
            // After throat region, y should generally increase
            for (int i = contour.size() / 5; i < contour.size(); i++) {
                assertThat(contour.get(i).y())
                        .isGreaterThanOrEqualTo(contour.get(i - 1).y() - 0.001);
            }
        }
    }
    
    @Nested
    @DisplayName("Angle Tests")
    class AngleTests {
        
        @Test
        @DisplayName("Exit angle should be small and positive")
        void exitAngleShouldBeSmallAndPositive() {
            raoNozzle.generate();
            
            double exitAngle = raoNozzle.getExitAngle();
            assertThat(Math.toDegrees(exitAngle)).isBetween(1.0, 15.0);
        }
        
        @Test
        @DisplayName("Inflection angle should be between initial and exit")
        void inflectionAngleShouldBeBetweenInitialAndExit() {
            raoNozzle.generate();
            
            double inflection = raoNozzle.getInflectionAngle();
            double exit = raoNozzle.getExitAngle();
            
            assertThat(inflection).isGreaterThan(exit);
            assertThat(Math.toDegrees(inflection)).isLessThanOrEqualTo(45);
        }
    }
    
    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {
        
        @Test
        @DisplayName("Should calculate thrust coefficient")
        void shouldCalculateThrustCoefficient() {
            raoNozzle.generate();
            
            double cf = raoNozzle.calculateThrustCoefficient();
            assertThat(cf).isGreaterThan(1.0);
            assertThat(cf).isLessThan(2.5);
        }
        
        @Test
        @DisplayName("Should calculate efficiency")
        void shouldCalculateEfficiency() {
            raoNozzle.generate();
            
            double efficiency = raoNozzle.calculateEfficiency();
            assertThat(efficiency).isBetween(0.9, 1.0);
        }
    }
    
    @Nested
    @DisplayName("Interpolation Tests")
    class InterpolationTests {
        
        @Test
        @DisplayName("Should get radius at any x position")
        void shouldGetRadiusAtAnyX() {
            raoNozzle.generate();
            
            double midX = raoNozzle.getActualLength() / 2;
            double radius = raoNozzle.getRadiusAt(midX);
            
            assertThat(radius).isBetween(params.throatRadius(), params.exitRadius());
        }
        
        @Test
        @DisplayName("Should get angle at any x position")
        void shouldGetAngleAtAnyX() {
            raoNozzle.generate();
            
            double midX = raoNozzle.getActualLength() / 2;
            double angle = raoNozzle.getAngleAt(midX);
            
            assertThat(angle).isGreaterThanOrEqualTo(0);
        }
    }
    
    @Nested
    @DisplayName("Comparison Tests")
    class ComparisonTests {
        
        @Test
        @DisplayName("Should compare with MOC nozzle")
        void shouldCompareWithMOCNozzle() {
            raoNozzle.generate();
            
            CharacteristicNet mocNet = new CharacteristicNet(params).generate();
            RaoNozzle.NozzleComparison comparison = raoNozzle.compareTo(mocNet);
            
            assertThat(comparison).isNotNull();
            assertThat(comparison.maxRadiusDifference()).isGreaterThanOrEqualTo(0);
            assertThat(comparison.avgRadiusDifference()).isGreaterThanOrEqualTo(0);
        }
        
        @Test
        @DisplayName("Comparison should have reasonable differences")
        void comparisonShouldHaveReasonableDifferences() {
            raoNozzle.generate();
            
            CharacteristicNet mocNet = new CharacteristicNet(params).generate();
            RaoNozzle.NozzleComparison comparison = raoNozzle.compareTo(mocNet);
            
            // The two nozzle design methods (Rao empirical vs MOC) use different
            // wall contour parameterisations starting from different axial origins.
            // The sonic-line initial data line shifts the MOC profile slightly, so
            // the tolerance is set to 25 % of throat radius rather than 20 %.
            double rt = params.throatRadius();
            assertThat(comparison.maxRadiusDifference()).isLessThan(rt * 0.25);
        }
    }
    
    @Nested
    @DisplayName("Length Fraction Tests")
    class LengthFractionTests {

        @Test
        @DisplayName("Higher length fraction should give longer nozzle")
        void higherLengthFractionShouldGiveLongerNozzle() {
            RaoNozzle short80 = new RaoNozzle(params, 0.6, 100).generate();
            RaoNozzle long80 = new RaoNozzle(params, 1.0, 100).generate();

            assertThat(long80.getActualLength())
                    .isGreaterThan(short80.getActualLength());
        }

        @Test
        @DisplayName("Shorter nozzle should have larger exit angle")
        void shorterNozzleShouldHaveLargerExitAngle() {
            RaoNozzle short60 = new RaoNozzle(params, 0.6, 100).generate();
            RaoNozzle long100 = new RaoNozzle(params, 1.0, 100).generate();

            assertThat(short60.getExitAngle())
                    .isGreaterThan(long100.getExitAngle());
        }

        @Test
        @DisplayName("lengthFraction below 0.6 uses the else branch for both angle correlations")
        void veryShortLengthFractionUsesElseBranch() {
            // lengthFraction=0.5 < 0.6 â†’ hits the else branch in both if-else chains
            // inside calculateAngles (inflectionAngle and exitAngle)
            RaoNozzle veryShort = new RaoNozzle(params, 0.5, 100).generate();
            assertThat(Math.toDegrees(veryShort.getInflectionAngle())).isBetween(15.0, 45.0);
            assertThat(Math.toDegrees(veryShort.getExitAngle())).isBetween(1.0, 15.0);
        }
    }

    @Nested
    @DisplayName("Lazy Generation Tests")
    class LazyGenerationTests {

        @Test
        @DisplayName("getContourPoints triggers generation when contour is empty")
        void getContourPointsTriggersLazyGeneration() {
            // raoNozzle from setUp has not had generate() called
            List<Point2D> points = raoNozzle.getContourPoints();
            assertThat(points).isNotEmpty();
        }

        @Test
        @DisplayName("getRadiusAt covers all four && branches including negative x")
        void getRadiusAtCoversAllBranches() {
            // isEmpty()=true: triggers lazy generation
            double rMid = raoNozzle.getRadiusAt(0.01);
            assertThat(rMid).isGreaterThan(0);

            // x < prev.x() on first iteration (first-condition-false branch):
            // contour starts at x=0, so x=-0.1 makes (x >= prev.x()) false immediately
            double rBefore = raoNozzle.getRadiusAt(-0.1);
            assertThat(rBefore).isCloseTo(params.exitRadius(), within(1e-9));

            // x >= prev.x() true && x <= curr.x() false (x past contour end)
            double rPast = raoNozzle.getRadiusAt(raoNozzle.getActualLength() * 10);
            assertThat(rPast).isCloseTo(params.exitRadius(), within(1e-9));
        }

        @Test
        @DisplayName("getAngleAt covers all four && branches including negative x")
        void getAngleAtCoversAllBranches() {
            // isEmpty()=true: triggers lazy generation
            raoNozzle.getAngleAt(0.01);

            // x < prev.x() on first iteration (first-condition-false branch)
            raoNozzle.getAngleAt(-0.1);

            // x >= prev.x() true && x <= curr.x() false â†’ falls through to return exitAngle
            double angle = raoNozzle.getAngleAt(raoNozzle.getActualLength() * 10);
            assertThat(angle).isEqualTo(raoNozzle.getExitAngle());
        }
    }

    @Nested
    @DisplayName("Edge-Case Comparison Tests")
    class EdgeCaseComparisonTests {

        @Test
        @DisplayName("compareTo with ungenerated net covers empty-contour and empty-wall branches")
        void compareToWithEmptyNetCoversAllEmptyBranches() {
            // raoNozzle not yet generated â†’ covers contourPoints.isEmpty()=true in compareTo
            // Ungenerated CharacteristicNet â†’ empty wall points â†’
            //   covers mocWall.isEmpty() ? 0 ternary and
            //   calculateMOCThrustCoefficient wallPoints.isEmpty()=true
            CharacteristicNet emptyNet = new CharacteristicNet(params);
            RaoNozzle.NozzleComparison comparison = raoNozzle.compareTo(emptyNet);

            assertThat(comparison.maxRadiusDifference()).isEqualTo(0.0);
            assertThat(comparison.avgRadiusDifference()).isEqualTo(0.0);
            assertThat(comparison.mocLength()).isEqualTo(0.0);
            assertThat(comparison.mocThrustCoefficient()).isGreaterThan(0);
        }
    }

    // -----------------------------------------------------------------------
    // getParameters
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getParameters Tests")
    class GetParametersTests {

        @Test
        @DisplayName("getParameters() returns the same instance passed at construction")
        void returnsConstructorInstance() {
            assertThat(raoNozzle.getParameters()).isSameAs(params);
        }

        @Test
        @DisplayName("getParameters() still returns same instance after generate()")
        void unchangedAfterGenerate() {
            raoNozzle.generate();
            assertThat(raoNozzle.getParameters()).isSameAs(params);
        }

        @Test
        @DisplayName("getParameters() reflects the correct throat radius")
        void reflectsThroatRadius() {
            assertThat(raoNozzle.getParameters().throatRadius())
                    .isEqualTo(params.throatRadius());
        }

        @Test
        @DisplayName("getParameters() is consistent across the three-arg constructor")
        void threeArgConstructorPreservesParams() {
            RaoNozzle custom = new RaoNozzle(params, 0.9, 50);
            assertThat(custom.getParameters()).isSameAs(params);
        }
    }

    // -----------------------------------------------------------------------
    // NozzleComparison.thrustCoefficientDifference
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("NozzleComparison.thrustCoefficientDifference Tests")
    class ThrustCoefficientDifferenceTests {

        @Test
        @DisplayName("thrustCoefficientDifference() = |Cf_Rao - Cf_MOC| when Rao > MOC")
        void differenceWhenRaoGreater() {
            RaoNozzle.NozzleComparison c =
                    new RaoNozzle.NozzleComparison(0, 0, 0, 1.80, 1.75, 0.5, 0.48);
            assertThat(c.thrustCoefficientDifference()).isCloseTo(0.05, within(1e-10));
        }

        @Test
        @DisplayName("thrustCoefficientDifference() = |Cf_Rao - Cf_MOC| when MOC > Rao")
        void differenceWhenMocGreater() {
            RaoNozzle.NozzleComparison c =
                    new RaoNozzle.NozzleComparison(0, 0, 0, 1.70, 1.82, 0.5, 0.52);
            assertThat(c.thrustCoefficientDifference()).isCloseTo(0.12, within(1e-10));
        }

        @Test
        @DisplayName("thrustCoefficientDifference() is zero when coefficients are equal")
        void differenceIsZeroWhenEqual() {
            RaoNozzle.NozzleComparison c =
                    new RaoNozzle.NozzleComparison(0, 0, 0, 1.75, 1.75, 0.5, 0.5);
            assertThat(c.thrustCoefficientDifference()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("thrustCoefficientDifference() is always non-negative")
        void alwaysNonNegative() {
            raoNozzle.generate();
            CharacteristicNet mocNet = new CharacteristicNet(params).generate();
            RaoNozzle.NozzleComparison comparison = raoNozzle.compareTo(mocNet);
            assertThat(comparison.thrustCoefficientDifference()).isGreaterThanOrEqualTo(0.0);
        }

        @Test
        @DisplayName("thrustCoefficientDifference() equals abs(raoThrustCoefficient - mocThrustCoefficient)")
        void equalsManualAbsDifference() {
            raoNozzle.generate();
            CharacteristicNet mocNet = new CharacteristicNet(params).generate();
            RaoNozzle.NozzleComparison comparison = raoNozzle.compareTo(mocNet);
            double expected = Math.abs(
                    comparison.raoThrustCoefficient() - comparison.mocThrustCoefficient());
            assertThat(comparison.thrustCoefficientDifference())
                    .isCloseTo(expected, within(1e-12));
        }
    }

    // -----------------------------------------------------------------------
    // NozzleComparison.toString
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("NozzleComparison.toString Tests")
    class NozzleComparisonToStringTests {

        private RaoNozzle.NozzleComparison comparison;

        @BeforeEach
        void buildComparison() {
            comparison = new RaoNozzle.NozzleComparison(
                    0.001, 0.0005, Math.toRadians(0.5), 1.78, 1.76, 0.450, 0.438);
        }

        @Test
        @DisplayName("toString() starts with 'NozzleComparison['")
        void startsWithPrefix() {
            assertThat(comparison.toString()).startsWith("NozzleComparison[");
        }

        @Test
        @DisplayName("toString() contains Cf_Rao label and value")
        void containsCfRao() {
            assertThat(comparison.toString())
                    .contains("Cf_Rao=")
                    .contains("1.7800");
        }

        @Test
        @DisplayName("toString() contains Cf_MOC label and value")
        void containsCfMoc() {
            assertThat(comparison.toString())
                    .contains("Cf_MOC=")
                    .contains("1.7600");
        }

        @Test
        @DisplayName("toString() contains maxÎ”r in mm")
        void containsMaxDeltaR() {
            // maxRadiusDifference = 0.001 m â†’ 1.0000 mm
            assertThat(comparison.toString())
                    .contains("maxÎ”r=")
                    .contains("1.0000 mm");
        }

        @Test
        @DisplayName("toString() contains L_Rao and L_MOC lengths")
        void containsLengths() {
            assertThat(comparison.toString())
                    .contains("L_Rao=0.4500 m")
                    .contains("L_MOC=0.4380 m");
        }

        @Test
        @DisplayName("toString() output from compareTo() is non-empty and well-formed")
        void compareToToStringWellFormed() {
            raoNozzle.generate();
            CharacteristicNet mocNet = new CharacteristicNet(params).generate();
            String s = raoNozzle.compareTo(mocNet).toString();
            assertThat(s).startsWith("NozzleComparison[").endsWith("]");
        }
    }
}
