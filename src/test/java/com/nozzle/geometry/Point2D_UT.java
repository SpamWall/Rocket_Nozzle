package com.nozzle.geometry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Point2D Tests")
class Point2D_UT {
    
    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {
        
        @Test
        @DisplayName("Should create point with coordinates")
        void shouldCreatePointWithCoordinates() {
            Point2D p = new Point2D(3.0, 4.0);
            assertThat(p.x()).isEqualTo(3.0);
            assertThat(p.y()).isEqualTo(4.0);
        }
        
        @Test
        @DisplayName("Should have origin constant")
        void shouldHaveOriginConstant() {
            assertThat(Point2D.ORIGIN.x()).isEqualTo(0);
            assertThat(Point2D.ORIGIN.y()).isEqualTo(0);
        }
        
        @Test
        @DisplayName("Should create from polar coordinates")
        void shouldCreateFromPolar() {
            Point2D p = Point2D.fromPolar(5.0, 0);
            assertThat(p.x()).isCloseTo(5.0, within(1e-10));
            assertThat(p.y()).isCloseTo(0.0, within(1e-10));
            
            Point2D p2 = Point2D.fromPolar(1.0, Math.PI / 2);
            assertThat(p2.x()).isCloseTo(0.0, within(1e-10));
            assertThat(p2.y()).isCloseTo(1.0, within(1e-10));
        }
    }
    
    @Nested
    @DisplayName("Geometry Tests")
    class GeometryTests {
        
        @Test
        @DisplayName("Should calculate distance")
        void shouldCalculateDistance() {
            Point2D p1 = new Point2D(0, 0);
            Point2D p2 = new Point2D(3, 4);
            assertThat(p1.distanceTo(p2)).isEqualTo(5.0);
        }
        
        @Test
        @DisplayName("Should calculate magnitude")
        void shouldCalculateMagnitude() {
            Point2D p = new Point2D(3, 4);
            assertThat(p.magnitude()).isEqualTo(5.0);
        }
        
        @Test
        @DisplayName("Should calculate angle")
        void shouldCalculateAngle() {
            Point2D p = new Point2D(1, 1);
            assertThat(p.angle()).isCloseTo(Math.PI / 4, within(1e-10));
        }
    }
    
    @Nested
    @DisplayName("Arithmetic Tests")
    class ArithmeticTests {
        
        @Test
        @DisplayName("Should add points")
        void shouldAddPoints() {
            Point2D p1 = new Point2D(1, 2);
            Point2D p2 = new Point2D(3, 4);
            Point2D sum = p1.add(p2);
            assertThat(sum.x()).isEqualTo(4);
            assertThat(sum.y()).isEqualTo(6);
        }
        
        @Test
        @DisplayName("Should subtract points")
        void shouldSubtractPoints() {
            Point2D p1 = new Point2D(5, 7);
            Point2D p2 = new Point2D(2, 3);
            Point2D diff = p1.subtract(p2);
            assertThat(diff.x()).isEqualTo(3);
            assertThat(diff.y()).isEqualTo(4);
        }
        
        @Test
        @DisplayName("Should scale point")
        void shouldScalePoint() {
            Point2D p = new Point2D(2, 3);
            Point2D scaled = p.scale(2.5);
            assertThat(scaled.x()).isEqualTo(5);
            assertThat(scaled.y()).isEqualTo(7.5);
        }
        
        @Test
        @DisplayName("Should normalize point")
        void shouldNormalizePoint() {
            Point2D p = new Point2D(3, 4);
            Point2D norm = p.normalize();
            assertThat(norm.magnitude()).isCloseTo(1.0, within(1e-10));
        }
        
        @Test
        @DisplayName("Should calculate dot product")
        void shouldCalculateDotProduct() {
            Point2D p1 = new Point2D(1, 2);
            Point2D p2 = new Point2D(3, 4);
            assertThat(p1.dot(p2)).isEqualTo(11);
        }
        
        @Test
        @DisplayName("Should calculate cross product")
        void shouldCalculateCrossProduct() {
            Point2D p1 = new Point2D(1, 0);
            Point2D p2 = new Point2D(0, 1);
            assertThat(p1.cross(p2)).isEqualTo(1);
        }
    }
    
    @Nested
    @DisplayName("Transformation Tests")
    class TransformationTests {
        
        @Test
        @DisplayName("Should rotate point")
        void shouldRotatePoint() {
            Point2D p = new Point2D(1, 0);
            Point2D rotated = p.rotate(Math.PI / 2);
            assertThat(rotated.x()).isCloseTo(0, within(1e-10));
            assertThat(rotated.y()).isCloseTo(1, within(1e-10));
        }
        
        @Test
        @DisplayName("Should reflect across x-axis")
        void shouldReflectAcrossXAxis() {
            Point2D p = new Point2D(3, 4);
            Point2D reflected = p.reflectX();
            assertThat(reflected.x()).isEqualTo(3);
            assertThat(reflected.y()).isEqualTo(-4);
        }
        
        @Test
        @DisplayName("Should reflect across y-axis")
        void shouldReflectAcrossYAxis() {
            Point2D p = new Point2D(3, 4);
            Point2D reflected = p.reflectY();
            assertThat(reflected.x()).isEqualTo(-3);
            assertThat(reflected.y()).isEqualTo(4);
        }
    }
    
    @Nested
    @DisplayName("Interpolation Tests")
    class InterpolationTests {
        
        @Test
        @DisplayName("Should interpolate linearly")
        void shouldInterpolateLinearly() {
            Point2D p1 = new Point2D(0, 0);
            Point2D p2 = new Point2D(10, 10);
            
            Point2D mid = p1.linearInterpolate(p2, 0.5);
            assertThat(mid.x()).isEqualTo(5);
            assertThat(mid.y()).isEqualTo(5);
        }
        
        @Test
        @DisplayName("Should calculate midpoint")
        void shouldCalculateMidpoint() {
            Point2D p1 = new Point2D(0, 0);
            Point2D p2 = new Point2D(4, 6);
            
            Point2D mid = p1.midpoint(p2);
            assertThat(mid.x()).isEqualTo(2);
            assertThat(mid.y()).isEqualTo(3);
        }
    }
}
