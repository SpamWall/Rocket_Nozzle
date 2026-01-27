package com.nozzle.moc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CharacteristicPoint Tests")
class CharacteristicPointTest {
    
    private CharacteristicPoint point;
    
    @BeforeEach
    void setUp() {
        point = CharacteristicPoint.create(
                0.1, 0.05, 2.0, Math.toRadians(10), Math.toRadians(26.38), Math.toRadians(30),
                100000, 2000, 0.17, 1200,
                CharacteristicPoint.PointType.INTERIOR
        );
    }
    
    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {
        
        @Test
        @DisplayName("Should create point with basic properties")
        void shouldCreatePointWithBasicProperties() {
            CharacteristicPoint p = CharacteristicPoint.of(0.1, 0.05, 2.0, 
                    Math.toRadians(10), Math.toRadians(26.38), Math.toRadians(30));
            
            assertThat(p.x()).isEqualTo(0.1);
            assertThat(p.y()).isEqualTo(0.05);
            assertThat(p.mach()).isEqualTo(2.0);
            assertThat(p.pointType()).isEqualTo(CharacteristicPoint.PointType.INTERIOR);
        }
        
        @Test
        @DisplayName("Should create point with full properties")
        void shouldCreatePointWithFullProperties() {
            assertThat(point.x()).isEqualTo(0.1);
            assertThat(point.y()).isEqualTo(0.05);
            assertThat(point.mach()).isEqualTo(2.0);
            assertThat(point.pressure()).isEqualTo(100000);
            assertThat(point.temperature()).isEqualTo(2000);
            assertThat(point.density()).isEqualTo(0.17);
            assertThat(point.velocity()).isEqualTo(1200);
        }
    }
    
    @Nested
    @DisplayName("Angle Conversions")
    class AngleConversionTests {
        
        @Test
        @DisplayName("Should convert theta to degrees")
        void shouldConvertThetaToDegrees() {
            assertThat(point.thetaDegrees()).isCloseTo(10.0, within(0.01));
        }
        
        @Test
        @DisplayName("Should convert mu to degrees")
        void shouldConvertMuToDegrees() {
            assertThat(point.muDegrees()).isCloseTo(30.0, within(0.01));
        }
        
        @Test
        @DisplayName("Should convert nu to degrees")
        void shouldConvertNuToDegrees() {
            assertThat(point.nuDegrees()).isCloseTo(26.38, within(0.01));
        }
    }
    
    @Nested
    @DisplayName("Characteristic Calculations")
    class CharacteristicCalculationsTests {
        
        @Test
        @DisplayName("Should calculate left characteristic slope")
        void shouldCalculateLeftCharacteristicSlope() {
            // theta - mu = 10 - 30 = -20 degrees
            double slope = point.leftCharacteristicSlope();
            assertThat(slope).isCloseTo(Math.tan(Math.toRadians(-20)), within(0.001));
        }
        
        @Test
        @DisplayName("Should calculate right characteristic slope")
        void shouldCalculateRightCharacteristicSlope() {
            // theta + mu = 10 + 30 = 40 degrees
            double slope = point.rightCharacteristicSlope();
            assertThat(slope).isCloseTo(Math.tan(Math.toRadians(40)), within(0.001));
        }
        
        @Test
        @DisplayName("Should calculate left Riemann invariant")
        void shouldCalculateLeftRiemannInvariant() {
            double qMinus = point.leftRiemannInvariant();
            // theta + nu
            assertThat(qMinus).isCloseTo(Math.toRadians(10 + 26.38), within(0.001));
        }
        
        @Test
        @DisplayName("Should calculate right Riemann invariant")
        void shouldCalculateRightRiemannInvariant() {
            double qPlus = point.rightRiemannInvariant();
            // theta - nu
            assertThat(qPlus).isCloseTo(Math.toRadians(10 - 26.38), within(0.001));
        }
    }
    
    @Nested
    @DisplayName("Geometry Methods")
    class GeometryMethodsTests {
        
        @Test
        @DisplayName("Should calculate distance to another point")
        void shouldCalculateDistanceToAnotherPoint() {
            CharacteristicPoint other = CharacteristicPoint.of(0.2, 0.08, 2.5, 0, 0, 0);
            double dist = point.distanceTo(other);
            
            double expected = Math.sqrt(Math.pow(0.1, 2) + Math.pow(0.03, 2));
            assertThat(dist).isCloseTo(expected, within(1e-6));
        }
        
        @Test
        @DisplayName("Should check if on axis")
        void shouldCheckIfOnAxis() {
            assertThat(point.isOnAxis(0.001)).isFalse();
            
            CharacteristicPoint axisPoint = CharacteristicPoint.of(0.1, 0.0001, 2.0, 0, 0, 0);
            assertThat(axisPoint.isOnAxis(0.001)).isTrue();
        }
        
        @Test
        @DisplayName("Should calculate local area ratio")
        void shouldCalculateLocalAreaRatio() {
            double ar = point.localAreaRatio(0.04); // throat radius = 0.04
            assertThat(ar).isCloseTo((0.05 * 0.05) / (0.04 * 0.04), within(0.001));
        }
    }
    
    @Nested
    @DisplayName("Immutability Tests")
    class ImmutabilityTests {
        
        @Test
        @DisplayName("Should return new point with updated indices")
        void shouldReturnNewPointWithUpdatedIndices() {
            CharacteristicPoint updated = point.withIndices(5, 10);
            
            assertThat(updated).isNotSameAs(point);
            assertThat(updated.leftIndex()).isEqualTo(5);
            assertThat(updated.rightIndex()).isEqualTo(10);
            assertThat(updated.x()).isEqualTo(point.x());
            assertThat(updated.mach()).isEqualTo(point.mach());
        }
        
        @Test
        @DisplayName("Should return new point with updated type")
        void shouldReturnNewPointWithUpdatedType() {
            CharacteristicPoint updated = point.withType(CharacteristicPoint.PointType.WALL);
            
            assertThat(updated).isNotSameAs(point);
            assertThat(updated.pointType()).isEqualTo(CharacteristicPoint.PointType.WALL);
            assertThat(updated.x()).isEqualTo(point.x());
        }
        
        @Test
        @DisplayName("Should return new point with updated thermodynamics")
        void shouldReturnNewPointWithUpdatedThermodynamics() {
            CharacteristicPoint updated = point.withThermodynamics(200000, 2500, 0.3, 1500);
            
            assertThat(updated).isNotSameAs(point);
            assertThat(updated.pressure()).isEqualTo(200000);
            assertThat(updated.temperature()).isEqualTo(2500);
            assertThat(updated.density()).isEqualTo(0.3);
            assertThat(updated.velocity()).isEqualTo(1500);
        }
    }
    
    @Nested
    @DisplayName("Point Type Tests")
    class PointTypeTests {
        
        @Test
        @DisplayName("Should have all point types")
        void shouldHaveAllPointTypes() {
            CharacteristicPoint.PointType[] types = CharacteristicPoint.PointType.values();
            
            assertThat(types).contains(
                    CharacteristicPoint.PointType.INITIAL,
                    CharacteristicPoint.PointType.INTERIOR,
                    CharacteristicPoint.PointType.WALL,
                    CharacteristicPoint.PointType.CENTERLINE,
                    CharacteristicPoint.PointType.EXIT
            );
        }
    }
    
    @Test
    @DisplayName("Should have meaningful toString")
    void shouldHaveMeaningfulToString() {
        String str = point.toString();
        
        assertThat(str).contains("Point");
        assertThat(str).contains("M=2.0");
        assertThat(str).contains("INTERIOR");
    }
    
    @Test
    @DisplayName("Should implement equals correctly")
    void shouldImplementEqualsCorrectly() {
        CharacteristicPoint same = CharacteristicPoint.create(
                0.1, 0.05, 2.0, Math.toRadians(10), Math.toRadians(26.38), Math.toRadians(30),
                100000, 2000, 0.17, 1200,
                CharacteristicPoint.PointType.INTERIOR
        );
        
        CharacteristicPoint different = CharacteristicPoint.of(0.2, 0.05, 2.0, 
                Math.toRadians(10), Math.toRadians(26.38), Math.toRadians(30));
        
        assertThat(point).isEqualTo(same);
        assertThat(point).isNotEqualTo(different);
    }
}
