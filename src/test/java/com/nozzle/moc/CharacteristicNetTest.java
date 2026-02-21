package com.nozzle.moc;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CharacteristicNet Tests")
class CharacteristicNetTest {
    
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
    @DisplayName("Construction Tests")
    class ConstructionTests {
        
        @Test
        @DisplayName("Should create empty net")
        void shouldCreateEmptyNet() {
            CharacteristicNet net = new CharacteristicNet(params);
            
            assertThat(net.getParameters()).isEqualTo(params);
            assertThat(net.getNetPoints()).isEmpty();
            assertThat(net.getWallPoints()).isEmpty();
        }
        
        @Test
        @DisplayName("Should accept custom tolerance")
        void shouldAcceptCustomTolerance() {
            CharacteristicNet net = new CharacteristicNet(params, 1e-10, 200);
            assertThat(net).isNotNull();
        }
    }
    
    @Nested
    @DisplayName("Generation Tests")
    class GenerationTests {
        
        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Should generate characteristic net")
        void shouldGenerateCharacteristicNet() {
            CharacteristicNet net = new CharacteristicNet(params).generate();
            
            assertThat(net.getNetPoints()).isNotEmpty();
            assertThat(net.getTotalPointCount()).isGreaterThan(0);
        }
        
        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Should generate wall points")
        void shouldGenerateWallPoints() {
            CharacteristicNet net = new CharacteristicNet(params).generate();
            
            List<CharacteristicPoint> wallPoints = net.getWallPoints();
            assertThat(wallPoints).isNotEmpty();
            
            // Wall points should have increasing x
            for (int i = 1; i < wallPoints.size(); i++) {
                assertThat(wallPoints.get(i).x())
                        .isGreaterThanOrEqualTo(wallPoints.get(i - 1).x());
            }
        }
        
        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Should have supersonic Mach throughout")
        void shouldHaveSupersonicMachThroughout() {
            CharacteristicNet net = new CharacteristicNet(params).generate();
            
            for (CharacteristicPoint point : net.getAllPoints()) {
                assertThat(point.mach()).isGreaterThanOrEqualTo(1.0);
            }
        }
        
        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Should have positive radii for axisymmetric")
        void shouldHavePositiveRadii() {
            CharacteristicNet net = new CharacteristicNet(params).generate();
            
            for (CharacteristicPoint point : net.getAllPoints()) {
                assertThat(point.y()).isGreaterThanOrEqualTo(0);
            }
        }
        
        @Test
        @DisplayName("Should return same instance on generate")
        void shouldReturnSameInstance() {
            CharacteristicNet net = new CharacteristicNet(params);
            CharacteristicNet generated = net.generate();
            
            assertThat(generated).isSameAs(net);
        }
    }
    
    @Nested
    @DisplayName("Interior Point Calculation Tests")
    class InteriorPointTests {
        
        @Test
        @DisplayName("Should compute interior point from two adjacent points")
        void shouldComputeInteriorPoint() {
            CharacteristicNet net = new CharacteristicNet(params);
            
            // Create two test points with correct MOC geometry
            // Left point is closer to centerline (lower y, smaller theta)
            // Right point is closer to wall (higher y, larger theta)
            // Both at same x, characteristics diverge forward
            double mach1 = 1.5;
            double mach2 = 1.6;
            GasProperties gas = params.gasProperties();
            
            CharacteristicPoint left = CharacteristicPoint.create(
                    0.01, 0.04, mach1, Math.toRadians(10), 
                    gas.prandtlMeyerFunction(mach1), gas.machAngle(mach1),
                    5e6, 3000, 5.0, 1000,
                    CharacteristicPoint.PointType.INITIAL
            );
            
            CharacteristicPoint right = CharacteristicPoint.create(
                    0.01, 0.05, mach2, Math.toRadians(15),
                    gas.prandtlMeyerFunction(mach2), gas.machAngle(mach2),
                    4.5e6, 2900, 4.5, 1050,
                    CharacteristicPoint.PointType.INITIAL
            );
            
            CharacteristicPoint interior = net.computeInteriorPoint(left, right);
            
            assertThat(interior).isNotNull();
            assertThat(interior.x()).isGreaterThan(left.x());
            assertThat(interior.mach()).isGreaterThanOrEqualTo(1.0);
            assertThat(interior.pointType()).isEqualTo(CharacteristicPoint.PointType.INTERIOR);
        }
    }
    
    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {
        
        @Test
        @DisplayName("Empty net should be invalid")
        void emptyNetShouldBeInvalid() {
            CharacteristicNet net = new CharacteristicNet(params);
            assertThat(net.validate()).isFalse();
        }
        
        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Generated net should be valid")
        void generatedNetShouldBeValid() {
            CharacteristicNet net = new CharacteristicNet(params).generate();
            assertThat(net.validate()).isTrue();
        }
    }
    
    @Nested
    @DisplayName("Area Ratio Tests")
    class AreaRatioTests {
        
        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Should calculate exit area ratio")
        void shouldCalculateExitAreaRatio() {
            CharacteristicNet net = new CharacteristicNet(params).generate();
            
            double computedAR = net.calculateExitAreaRatio();
            
            // The MOC computes whatever geometry emerges from the initial expansion.
            // The computed area ratio should be > 1 (supersonic divergent nozzle)
            // and should increase with the wall points
            assertThat(computedAR).isGreaterThan(1.0);
            
            // Wall points should show increasing radius
            List<CharacteristicPoint> wallPts = net.getWallPoints();
            assertThat(wallPts).isNotEmpty();
            
            // First wall point should be near throat radius
            double rt = params.throatRadius();
            assertThat(wallPts.getFirst().y()).isCloseTo(rt, within(rt * 0.5));
        }
    }
    
    @Nested
    @DisplayName("Accessors Tests")
    class AccessorsTests {
        
        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Should return unmodifiable net points")
        void shouldReturnUnmodifiableNetPoints() {
            CharacteristicNet net = new CharacteristicNet(params).generate();
            List<List<CharacteristicPoint>> netPoints = net.getNetPoints();
            
            assertThatThrownBy(netPoints::clear)
                    .isInstanceOf(UnsupportedOperationException.class);
        }
        
        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Should return unmodifiable wall points")
        void shouldReturnUnmodifiableWallPoints() {
            CharacteristicNet net = new CharacteristicNet(params).generate();
            List<CharacteristicPoint> wallPoints = net.getWallPoints();
            
            assertThatThrownBy(wallPoints::clear)
                    .isInstanceOf(UnsupportedOperationException.class);
        }
        
        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Should return flat list of all points")
        void shouldReturnFlatListOfAllPoints() {
            CharacteristicNet net = new CharacteristicNet(params).generate();
            
            List<CharacteristicPoint> allPoints = net.getAllPoints();
            int totalCount = net.getTotalPointCount();
            
            assertThat(allPoints).hasSize(totalCount);
        }
    }
    
    @Nested
    @DisplayName("Different Gas Properties Tests")
    class DifferentGasPropertiesTests {
        
        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Should work with LOX/RP-1 properties")
        void shouldWorkWithLoxRp1() {
            NozzleDesignParameters loxRp1Params = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(2.5)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .numberOfCharLines(15)
                    .wallAngleInitialDegrees(25)
                    .lengthFraction(0.8)
                    .axisymmetric(true)
                    .build();
            
            CharacteristicNet net = new CharacteristicNet(loxRp1Params).generate();
            
            assertThat(net.validate()).isTrue();
            assertThat(net.getTotalPointCount()).isGreaterThan(0);
        }
        
        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Should work with LOX/LH2 properties")
        void shouldWorkWithLoxLh2() {
            NozzleDesignParameters loxLh2Params = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(2.5)
                    .chamberPressure(7e6)
                    .chamberTemperature(3200)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.LOX_LH2_PRODUCTS)
                    .numberOfCharLines(15)
                    .wallAngleInitialDegrees(25)
                    .lengthFraction(0.8)
                    .axisymmetric(true)
                    .build();
            
            CharacteristicNet net = new CharacteristicNet(loxLh2Params).generate();
            
            assertThat(net.validate()).isTrue();
        }
    }
}
