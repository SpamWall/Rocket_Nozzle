package com.nozzle.geometry;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
}
