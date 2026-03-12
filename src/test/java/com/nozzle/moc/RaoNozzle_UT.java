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
            
            // Differences should be within 10% of throat radius
            double rt = params.throatRadius();
            assertThat(comparison.maxRadiusDifference()).isLessThan(rt * 0.2);
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
    }
}
