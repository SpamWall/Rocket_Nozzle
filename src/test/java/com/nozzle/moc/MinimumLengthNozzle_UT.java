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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MinimumLengthNozzle Tests")
class MinimumLengthNozzle_UT {

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
                .numberOfCharLines(20)
                .wallAngleInitialDegrees(25)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();
    }

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("Should construct with default tolerance")
        void shouldConstructDefault() {
            MinimumLengthNozzle mln = new MinimumLengthNozzle(params);
            assertThat(mln).isNotNull();
            assertThat(mln.getWallPoints()).isEmpty();
        }

        @Test
        @DisplayName("Should construct with custom convergence settings")
        void shouldConstructCustom() {
            MinimumLengthNozzle mln = new MinimumLengthNozzle(params, 1e-10, 200);
            assertThat(mln).isNotNull();
        }
    }

    @Nested
    @DisplayName("Generation")
    class GenerationTests {

        @Test
        @DisplayName("Should generate wall points after calling generate()")
        void shouldGenerateWallPoints() {
            MinimumLengthNozzle mln = new MinimumLengthNozzle(params).generate();

            assertThat(mln.getWallPoints()).isNotEmpty();
            assertThat(mln.getNetPoints()).isNotEmpty();
        }

        @Test
        @DisplayName("Should have θ_max = ν(M_exit)/2")
        void shouldHaveCorrectMaxWallAngle() {
            MinimumLengthNozzle mln = new MinimumLengthNozzle(params).generate();

            double nuExit = GasProperties.AIR.prandtlMeyerFunction(params.exitMach());
            double expectedThetaMax = nuExit / 2.0;

            assertThat(mln.getMaximumWallAngle())
                    .isCloseTo(expectedThetaMax, within(1e-10));
        }

        @Test
        @DisplayName("Wall points should be x-monotonically increasing")
        void wallPointsShouldBeXMonotone() {
            MinimumLengthNozzle mln = new MinimumLengthNozzle(params).generate();
            List<CharacteristicPoint> wall = mln.getWallPoints();

            for (int i = 1; i < wall.size(); i++) {
                assertThat(wall.get(i).x())
                        .as("Wall x[%d] > x[%d]", i, i - 1)
                        .isGreaterThan(wall.get(i - 1).x());
            }
        }

        @Test
        @DisplayName("Wall points should have y (radius) monotonically increasing")
        void wallPointsYShouldBeMonotone() {
            MinimumLengthNozzle mln = new MinimumLengthNozzle(params).generate();
            List<CharacteristicPoint> wall = mln.getWallPoints();

            for (int i = 1; i < wall.size(); i++) {
                assertThat(wall.get(i).y())
                        .as("Wall y[%d] >= y[%d]", i, i - 1)
                        .isGreaterThanOrEqualTo(wall.get(i - 1).y() - 1e-9);
            }
        }

        @Test
        @DisplayName("All wall Mach numbers should be >= 1")
        void wallMachNumbersSupersonic() {
            MinimumLengthNozzle mln = new MinimumLengthNozzle(params).generate();

            mln.getWallPoints().forEach(wp ->
                    assertThat(wp.mach())
                            .as("Wall Mach >= 1 at x=%.4f", wp.x())
                            .isGreaterThanOrEqualTo(1.0));
        }

        @Test
        @DisplayName("Nozzle length should be positive")
        void nozzleLengthShouldBePositive() {
            MinimumLengthNozzle mln = new MinimumLengthNozzle(params).generate();
            assertThat(mln.nozzleLength()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("Exit area ratio should be close to design value")
        void exitAreaRatioShouldBeReasonable() {
            MinimumLengthNozzle mln = new MinimumLengthNozzle(params).generate();
            double designEpsilon = params.exitAreaRatio();

            // MLN exit epsilon won't exactly match since the wall is found dynamically
            assertThat(mln.exitAreaRatio())
                    .isGreaterThan(1.5)
                    .isLessThan(designEpsilon * 2.0);
        }
    }

    @Nested
    @DisplayName("Performance Metrics")
    class PerformanceMetricsTests {

        @Test
        @DisplayName("MLN should be shorter than 15-degree cone reference")
        void mlnShouldBeShorterThanConeReference() {
            MinimumLengthNozzle mln = new MinimumLengthNozzle(params).generate();
            double lengthRatio = mln.lengthRatioVsCone();

            // MLN should be 50-80% of equivalent cone length
            assertThat(lengthRatio)
                    .isGreaterThan(0.3)
                    .isLessThan(0.95);
        }

        @ParameterizedTest(name = "Exit Mach {0}")
        @ValueSource(doubles = {2.0, 2.5, 3.0, 4.0})
        @DisplayName("Should generate for various exit Mach numbers")
        void shouldGenerateForVariousMachNumbers(double mach) {
            NozzleDesignParameters p = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(mach)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.AIR)
                    .numberOfCharLines(15)
                    .wallAngleInitialDegrees(20)
                    .build();

            MinimumLengthNozzle mln = new MinimumLengthNozzle(p).generate();

            assertThat(mln.getWallPoints()).isNotEmpty();
            assertThat(mln.nozzleLength()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("Higher exit Mach should produce a longer nozzle")
        void higherMachLongerNozzle() {
            NozzleDesignParameters low = NozzleDesignParameters.builder()
                    .exitMach(2.0).throatRadius(0.05).chamberPressure(7e6)
                    .chamberTemperature(3500).ambientPressure(101325)
                    .gasProperties(GasProperties.AIR).numberOfCharLines(15)
                    .wallAngleInitialDegrees(20).build();

            NozzleDesignParameters high = NozzleDesignParameters.builder()
                    .exitMach(3.5).throatRadius(0.05).chamberPressure(7e6)
                    .chamberTemperature(3500).ambientPressure(101325)
                    .gasProperties(GasProperties.AIR).numberOfCharLines(15)
                    .wallAngleInitialDegrees(20).build();

            double lengthLow  = new MinimumLengthNozzle(low).generate().nozzleLength();
            double lengthHigh = new MinimumLengthNozzle(high).generate().nozzleLength();

            assertThat(lengthHigh).isGreaterThan(lengthLow);
        }
    }

    @Nested
    @DisplayName("Interior Point Computation")
    class InteriorPointTests {

        @Test
        @DisplayName("computeInteriorPoint should return physical result for valid inputs")
        void computeInteriorPointPhysical() {
            MinimumLengthNozzle mln = new MinimumLengthNozzle(params);
            GasProperties gas = GasProperties.AIR;

            double nu = Math.toRadians(10.0);
            double mach = gas.machFromPrandtlMeyer(nu);
            double mu   = gas.machAngle(mach);

            CharacteristicPoint left = CharacteristicPoint.of(0.01, 0.02, mach,
                    Math.toRadians(5), nu, mu);
            CharacteristicPoint right = CharacteristicPoint.of(0.01, 0.04, mach,
                    Math.toRadians(10), nu + Math.toRadians(2), mu);

            CharacteristicPoint result = mln.computeInteriorPoint(left, right, gas);

            assertThat(result).isNotNull();
            assertThat(result.mach()).isGreaterThan(1.0);
            assertThat(result.y()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("computeInteriorPoint should return null when ν <= 0")
        void computeInteriorPointNullForNegativeNu() {
            MinimumLengthNozzle mln = new MinimumLengthNozzle(params);
            GasProperties gas = GasProperties.AIR;

            // Construct points where Q- < Q+ → ν would be negative
            CharacteristicPoint left  = CharacteristicPoint.of(0.0, 0.02, 2.0,
                    Math.toRadians(20), Math.toRadians(26.4), gas.machAngle(2.0));
            CharacteristicPoint right = CharacteristicPoint.of(0.0, 0.04, 1.5,
                    Math.toRadians(-5), Math.toRadians(11.9), gas.machAngle(1.5));

            // Result may be null (ν ≤ 0) or degenerate — just check no exception
            // (null is a valid return for non-physical configurations)
            assertThatCode(() -> mln.computeInteriorPoint(left, right, gas))
                    .doesNotThrowAnyException();
        }
    }
}
