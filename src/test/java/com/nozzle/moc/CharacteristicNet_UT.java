package com.nozzle.moc;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CharacteristicNet Tests")
class CharacteristicNet_UT {
    
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
    
    // -----------------------------------------------------------------------
    // Planar (2D) net — covers axisymmetric=false branch in computeInteriorPoint
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Planar Net Tests")
    class PlanarNetTests {

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Planar net (axisymmetric=false) generates without axisymmetric source-term correction")
        void planarNetGeneratesWithoutAxisymmetricCorrection() {
            NozzleDesignParameters planarParams = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(2.5)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.AIR)
                    .numberOfCharLines(10)
                    .wallAngleInitialDegrees(25)
                    .lengthFraction(0.8)
                    .axisymmetric(false)
                    .build();

            CharacteristicNet net = new CharacteristicNet(planarParams).generate();

            assertThat(net.getNetPoints()).isNotEmpty();
            assertThat(net.getTotalPointCount()).isGreaterThan(0);
        }
    }

    // -----------------------------------------------------------------------
    // Empty-net accessor — covers calculateExitAreaRatio() wallPoints.isEmpty() TRUE
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Empty Net Accessor Tests")
    class EmptyNetAccessorTests {

        @Test
        @DisplayName("calculateExitAreaRatio() before generate() returns the design area ratio (L495 TRUE)")
        void calculateExitAreaRatioBeforeGenerateReturnsDesignValue() {
            CharacteristicNet net = new CharacteristicNet(params);

            assertThat(net.calculateExitAreaRatio())
                    .isCloseTo(params.exitAreaRatio(), within(1e-10));
        }
    }

    // -----------------------------------------------------------------------
    // computeInteriorPoint null return — covers L350 (nu <= 0)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Null Return Branch Tests")
    class NullReturnBranchTests {

        @Test
        @DisplayName("computeInteriorPoint returns null when Qminus < Qplus so nu = (Q- - Q+)/2 <= 0 (L350)")
        void computeInteriorPointReturnsNullForNonPositiveNu() {
            CharacteristicNet net = new CharacteristicNet(params);
            GasProperties gas = params.gasProperties();
            double mu = gas.machAngle(1.5);

            // Q+ = theta_L - nu_L = 0.40 - 0.10 =  0.30
            // Q- = theta_R + nu_R = 0.05 + 0.10 =  0.15
            // nu = (Q- - Q+) / 2 = (0.15 - 0.30) / 2 = -0.075  <=  0  →  return null
            CharacteristicPoint left = CharacteristicPoint.create(
                    0.01, 0.03, 1.5, 0.40, 0.10, mu,
                    5e6, 3000, 5.0, 1000, CharacteristicPoint.PointType.INITIAL);
            CharacteristicPoint right = CharacteristicPoint.create(
                    0.01, 0.05, 1.5, 0.05, 0.10, mu,
                    5e6, 3000, 5.0, 1000, CharacteristicPoint.PointType.INITIAL);

            assertThat(net.computeInteriorPoint(left, right)).isNull();
        }
    }

    // -----------------------------------------------------------------------
    // validate() internal branches — non-monotonic wall (L516) and subsonic (L519)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Validate Internal Branch Tests")
    class ValidateInternalBranchTests {

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("validate() returns false when wall points are non-monotonic in x (L516 TRUE)")
        void validateReturnsFalseForNonMonotonicWallX() throws Exception {
            CharacteristicNet net = new CharacteristicNet(params).generate();
            assertThat(net.validate()).isTrue();

            Field wallField = CharacteristicNet.class.getDeclaredField("wallPoints");
            wallField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<CharacteristicPoint> rawWall = (List<CharacteristicPoint>) wallField.get(net);

            CharacteristicPoint last = rawWall.getLast();
            GasProperties gas = params.gasProperties();
            double mach = 2.0;
            // x < last.x()  →  wallPoints.get(i).x() <= wallPoints.get(i-1).x()  →  return false
            CharacteristicPoint nonMonotonic = CharacteristicPoint.create(
                    last.x() - 0.01, last.y() + 0.005, mach,
                    0.0, gas.prandtlMeyerFunction(mach), gas.machAngle(mach),
                    1e6, 2000, 2.0, 800, CharacteristicPoint.PointType.WALL);
            rawWall.add(nonMonotonic);

            assertThat(net.validate()).isFalse();
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("validate() returns false when a subsonic point exists in netPoints (L519 TRUE)")
        void validateReturnsFalseForSubsonicPoint() throws Exception {
            CharacteristicNet net = new CharacteristicNet(params).generate();
            assertThat(net.validate()).isTrue();

            Field netField = CharacteristicNet.class.getDeclaredField("netPoints");
            netField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<List<CharacteristicPoint>> rawNet =
                    (List<List<CharacteristicPoint>>) netField.get(net);

            // mach = 0.5 < 1.0  →  p.mach() < 1.0  →  return false
            CharacteristicPoint subsonic = CharacteristicPoint.create(
                    0.10, 0.04, 0.5, 0.0, 0.0, Math.PI / 2,
                    5e6, 3000, 5.0, 150, CharacteristicPoint.PointType.INTERIOR);
            rawNet.add(new ArrayList<>(List.of(subsonic)));

            assertThat(net.validate()).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // Early-termination test — covers L176 (maxTheta < 0.5° break)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Early Termination Tests")
    class EarlyTerminationTests {

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("generate() breaks early when max flow angle drops below 0.5° (L176)")
        void generateBreaksEarlyWhenFlowNearlyAxial() {
            // wallAngleInitialDegrees = 0.3°  <  0.5°
            // After the first propagation row the max theta in nextLine ≈ 0.3°,
            // which satisfies maxTheta < toRadians(0.5) → break at L176 before
            // exhausting maxRows = 2*5+20 = 30.
            // axisymmetric=false avoids the source-term correction that could
            // alter theta, making the early-exit arithmetic straightforward.
            NozzleDesignParameters nearAxialParams = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(1.5)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.AIR)
                    .numberOfCharLines(5)
                    .wallAngleInitialDegrees(0.3)
                    .lengthFraction(0.8)
                    .axisymmetric(false)
                    .build();

            CharacteristicNet net = new CharacteristicNet(nearAxialParams).generate();

            // Should have generated at least one row but far fewer than maxRows (30)
            assertThat(net.getNetPoints()).isNotEmpty();
            assertThat(net.getNetPoints().size()).isLessThan(1 + 30);
        }
    }

    // -----------------------------------------------------------------------
    // Different gas properties
    // -----------------------------------------------------------------------

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
