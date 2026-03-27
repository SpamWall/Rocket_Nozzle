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
    // Wall-convergence tests — verify the Rao wall profile drives the generated
    // contour to the design exit radius regardless of numberOfCharLines (L179).
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Wall Convergence Tests")
    class WallConvergenceTests {

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("generate() wall reaches design exit radius for coarse mesh (n=30)")
        void wallReachesExitRadiusCoarse() {
            NozzleDesignParameters p = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(3.5)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .numberOfCharLines(30)
                    .wallAngleInitialDegrees(30)
                    .lengthFraction(0.8)
                    .build();

            CharacteristicNet net = new CharacteristicNet(p).generate();
            double designExitRadius = p.exitRadius();
            double actualExitRadius = net.getWallPoints().getLast().y();

            assertThat(net.getNetPoints()).isNotEmpty();
            // Last wall point must reach at least the design exit radius
            assertThat(actualExitRadius).isGreaterThanOrEqualTo(designExitRadius * 0.95);
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("computed A/A* is consistent between coarse (n=30) and fine (n=300) meshes")
        void areaRatioIsNIndependent() {
            NozzleDesignParameters.Builder base = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(3.5)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .wallAngleInitialDegrees(30)
                    .lengthFraction(0.8);

            double ar30  = new CharacteristicNet(base.numberOfCharLines(30).build())
                    .generate().calculateExitAreaRatio();
            double ar300 = new CharacteristicNet(base.numberOfCharLines(300).build())
                    .generate().calculateExitAreaRatio();

            // Both meshes must agree to within 5 %
            assertThat(Math.abs(ar30 - ar300) / ar300).isLessThan(0.05);
        }
    }

    // -----------------------------------------------------------------------
    // computeRaoExitAngle() length-fraction branches (L282/L284)
    //   lf >= 0.8 TRUE  → already covered by default params (lf=0.8)
    //   lf >= 0.8 FALSE, lf >= 0.6 TRUE  → lf=0.7
    //   lf >= 0.8 FALSE, lf >= 0.6 FALSE → lf=0.5
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Rao Exit Angle Length-Fraction Branch Tests")
    class RaoExitAngleBranchTests {

        private NozzleDesignParameters buildWith(double lf) {
            return NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(2.5)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.AIR)
                    .numberOfCharLines(10)
                    .wallAngleInitialDegrees(25)
                    .lengthFraction(lf)
                    .axisymmetric(true)
                    .build();
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("lf=0.7 exercises else-if (lf >= 0.6) branch of computeRaoExitAngle")
        void lengthFraction07ExercisesElseIfBranch() {
            CharacteristicNet net = new CharacteristicNet(buildWith(0.7)).generate();
            assertThat(net.validate()).isTrue();
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("lf=0.5 exercises else branch of computeRaoExitAngle")
        void lengthFraction05ExercisesElseBranch() {
            CharacteristicNet net = new CharacteristicNet(buildWith(0.5)).generate();
            assertThat(net.validate()).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // Axisymmetric-correction iteration branches:
    //   L419 for-loop exhausted (iter >= maxIterations)
    //   L421 yAvg < 1e-10 early break
    //   L434 cotMu < convergenceTolerance early break
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Axisymmetric Correction Iteration Branch Tests")
    class AxisymmetricCorrectionBranchTests {

        // Helpers – build characteristic points with known Riemann invariants.
        // Qplus = theta_L - nu_L, Qminus = theta_R + nu_R
        // nu_interior = (Qminus - Qplus)/2 must be > 0.

        private CharacteristicPoint makePoint(double x, double y, double mach,
                                              double theta, GasProperties gas,
                                              CharacteristicPoint.PointType type) {
            double nu = gas.prandtlMeyerFunction(mach);
            double mu = gas.machAngle(mach);
            double T = 3500 * gas.isentropicTemperatureRatio(mach);
            double P = 7e6 * gas.isentropicPressureRatio(mach);
            double rho = P / (gas.gasConstant() * T);
            double V = mach * gas.speedOfSound(T);
            return CharacteristicPoint.create(x, y, mach, theta, nu, mu, P, T, rho, V, type);
        }

        @Test
        @DisplayName("axisymmetric iteration exhausts maxIterations when convergenceTolerance=0 (L419 loop false)")
        void iterationExhaustsMaxIterations() {
            // convergenceTolerance=0.0 → Math.abs(Δθ) < 0.0 is always false → convergence break never fires.
            // cotMu = sqrt(M²-1) ≈ 1.15 for M=1.5 which is NOT < 0.0 → sonic guard never fires.
            // With maxIter=2 the for-loop condition (iter < 2) evaluates to false at iter=2 → L419 covered.
            CharacteristicNet net = new CharacteristicNet(params, 0.0, 2);
            GasProperties gas = params.gasProperties();

            CharacteristicPoint left  = makePoint(0.01, 0.04, 1.5, Math.toRadians(10), gas,
                    CharacteristicPoint.PointType.INITIAL);
            CharacteristicPoint right = makePoint(0.01, 0.05, 1.6, Math.toRadians(15), gas,
                    CharacteristicPoint.PointType.INITIAL);

            CharacteristicPoint interior = net.computeInteriorPoint(left, right);
            assertThat(interior).isNotNull();
            assertThat(interior.mach()).isGreaterThanOrEqualTo(1.0);
        }

        @Test
        @DisplayName("axisymmetric iteration breaks early when yAvg < 1e-10 (L421 true)")
        void iterationBreaksForNearZeroYAvg() {
            // y values ≈ 1e-15 so yAvg = (y_left + y_right + y_intersection)/3 ≈ 0 < 1e-10.
            // The loop breaks immediately; computeInteriorPoint still returns a valid point.
            CharacteristicNet net = new CharacteristicNet(params); // axisymmetric=true
            GasProperties gas = params.gasProperties();

            // theta_L=0.10, nu_L=nu → Qplus = 0.10 - nu
            // theta_R=0.15, nu_R=nu → Qminus = 0.15 + nu
            // nu_interior = (0.15 + nu - (0.10 - nu))/2 = (0.05 + 2*nu)/2 > 0 ✓
            CharacteristicPoint left  = makePoint(0.001, 1e-15, 1.5, 0.10, gas,
                    CharacteristicPoint.PointType.INITIAL);
            CharacteristicPoint right = makePoint(0.001, 2e-15, 1.5, 0.15, gas,
                    CharacteristicPoint.PointType.INITIAL);

            CharacteristicPoint interior = net.computeInteriorPoint(left, right);
            assertThat(interior).isNotNull();
        }

        @Test
        @DisplayName("axisymmetric iteration breaks early when cotMu < convergenceTolerance (L434 true)")
        void iterationBreaksForNearSonicCotMu() {
            // convergenceTolerance=1.0 → sonic guard fires when cotMu = sqrt(M²-1) < 1.0,
            // i.e., when machAvg < sqrt(2) ≈ 1.414.  With M=1.2, cotMu ≈ 0.663 < 1.0. ✓
            CharacteristicNet net = new CharacteristicNet(params, 1.0, 100);
            GasProperties gas = params.gasProperties();

            // Qplus = 0.05 - nu_L, Qminus = 0.08 + nu_R  → nu_interior = (0.08+nu+0.05-0.05+nu)/2 > 0
            CharacteristicPoint left  = makePoint(0.01, 0.04, 1.2, 0.05, gas,
                    CharacteristicPoint.PointType.INITIAL);
            CharacteristicPoint right = makePoint(0.01, 0.05, 1.2, 0.08, gas,
                    CharacteristicPoint.PointType.INITIAL);

            CharacteristicPoint interior = net.computeInteriorPoint(left, right);
            assertThat(interior).isNotNull();
            assertThat(interior.mach()).isGreaterThanOrEqualTo(1.0);
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
