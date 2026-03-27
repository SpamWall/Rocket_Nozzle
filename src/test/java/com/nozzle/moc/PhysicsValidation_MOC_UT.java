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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Validates the Method of Characteristics implementation against
 * known analytical results.
 *
 * <h2>Validation targets</h2>
 * <ol>
 *   <li><b>Riemann invariant conservation (planar, non-axisymmetric):</b>
 *       In 2-D planar flow the characteristics Q+ = θ − ν and Q− = θ + ν are
 *       analytically constant along their respective characteristics.
 *       {@code computeInteriorPoint} must preserve them to machine precision
 *       (no axisymmetric correction applied in planar mode).</li>
 *   <li><b>Isentropic thermodynamic consistency:</b>
 *       Every point in the net must have T, P, ρ, V consistent with the
 *       local Mach number via isentropic relations from the known chamber
 *       stagnation conditions, to within 0.1 %.</li>
 *   <li><b>Mach angle identity:</b>
 *       Each point's stored Mach angle μ must satisfy sin(μ) = 1/M exactly.</li>
 *   <li><b>ν = ν(M) self-consistency:</b>
 *       Stored Prandtl-Meyer angle must equal the function evaluated at the
 *       stored Mach number.</li>
 *   <li><b>Net monotonicity laws:</b>
 *       Mach increases downstream; wall radius grows; axis of symmetry holds.</li>
 * </ol>
 */
@DisplayName("Physics Validation — Method of Characteristics")
class PhysicsValidation_MOC_UT {

    // -----------------------------------------------------------------------
    // Riemann invariant conservation in planar (non-axisymmetric) MOC
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Riemann invariant conservation (planar MOC)")
    class RiemannInvariantTests {

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Q+ = θ − ν is preserved by computeInteriorPoint to < 1e-10 rad")
        void qPlusPreservedExactly() {
            GasProperties gas = GasProperties.AIR;
            NozzleDesignParameters params = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0)
                    .chamberPressure(7e6).chamberTemperature(3500.0).ambientPressure(101325.0)
                    .gasProperties(gas).numberOfCharLines(10)
                    .wallAngleInitialDegrees(25).lengthFraction(0.8)
                    .axisymmetric(false).build();

            CharacteristicNet net = new CharacteristicNet(params);

            // Construct two points with known, distinct Riemann invariants
            double m1 = 1.8, m2 = 2.2;
            double nu1 = gas.prandtlMeyerFunction(m1);
            double nu2 = gas.prandtlMeyerFunction(m2);
            double theta1 = Math.toRadians(8.0);
            double theta2 = Math.toRadians(14.0);

            CharacteristicPoint left = CharacteristicPoint.create(
                    0.02, 0.03, m1, theta1, nu1, gas.machAngle(m1),
                    6e6, 3000, 5.0, 900, CharacteristicPoint.PointType.INITIAL);
            CharacteristicPoint right = CharacteristicPoint.create(
                    0.02, 0.05, m2, theta2, nu2, gas.machAngle(m2),
                    5e6, 2800, 4.5, 1000, CharacteristicPoint.PointType.INITIAL);

            double qPlusLeft   = left.rightRiemannInvariant();    // θ - ν  (C+)
            double qMinusRight = right.leftRiemannInvariant();    // θ + ν  (C-)

            CharacteristicPoint interior = net.computeInteriorPoint(left, right);

            assertThat(interior).as("Interior point must not be null").isNotNull();

            // Verify Q+ preserved along C+ from left
            double qPlusInterior = interior.rightRiemannInvariant();
            assertThat(qPlusInterior)
                    .as("Q+ = θ−ν at interior must equal Q+ from left point")
                    .isCloseTo(qPlusLeft, within(1e-10));

            // Verify Q- preserved along C- from right
            double qMinusInterior = interior.leftRiemannInvariant();
            assertThat(qMinusInterior)
                    .as("Q- = θ+ν at interior must equal Q- from right point")
                    .isCloseTo(qMinusRight, within(1e-10));
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Interior Mach number equals machFromPrandtlMeyer(ν_interior) to 1e-8")
        void interiorMachConsistentWithNu() {
            GasProperties gas = GasProperties.AIR;
            NozzleDesignParameters params = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0)
                    .chamberPressure(7e6).chamberTemperature(3500.0).ambientPressure(101325.0)
                    .gasProperties(gas).numberOfCharLines(10)
                    .wallAngleInitialDegrees(25).lengthFraction(0.8)
                    .axisymmetric(false).build();

            CharacteristicNet net = new CharacteristicNet(params);

            double m1 = 1.5, m2 = 2.0;
            CharacteristicPoint left = CharacteristicPoint.create(
                    0.01, 0.03, m1, Math.toRadians(6), gas.prandtlMeyerFunction(m1),
                    gas.machAngle(m1), 6e6, 3000, 5.0, 800, CharacteristicPoint.PointType.INITIAL);
            CharacteristicPoint right = CharacteristicPoint.create(
                    0.01, 0.05, m2, Math.toRadians(12), gas.prandtlMeyerFunction(m2),
                    gas.machAngle(m2), 5e6, 2800, 4.5, 1000, CharacteristicPoint.PointType.INITIAL);

            CharacteristicPoint interior = net.computeInteriorPoint(left, right);
            assertThat(interior).isNotNull();

            double expectedMach = gas.machFromPrandtlMeyer(interior.nu());
            assertThat(interior.mach())
                    .as("M at interior consistent with stored ν")
                    .isCloseTo(expectedMach, within(1e-8));
        }
    }

    // -----------------------------------------------------------------------
    // Isentropic thermodynamic consistency of all net points
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Isentropic consistency of net points (T, P, ρ, V vs M)")
    class IsentropicConsistencyTests {

        private static final double TOLERANCE_PCT = 0.001;  // 0.1 %

        private NozzleDesignParameters standardParams() {
            return NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0)
                    .chamberPressure(7e6).chamberTemperature(3500.0).ambientPressure(101325.0)
                    .gasProperties(GasProperties.AIR)
                    .numberOfCharLines(12)
                    .wallAngleInitialDegrees(25).lengthFraction(0.8)
                    .axisymmetric(true).build();
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("T at every net point = Tc · (T/T0)(M) to 0.1%")
        void temperatureConsistentWithMach() {
            NozzleDesignParameters params = standardParams();
            GasProperties gas = params.gasProperties();
            CharacteristicNet net = new CharacteristicNet(params).generate();

            for (CharacteristicPoint p : net.getAllPoints()) {
                double expected = params.chamberTemperature() * gas.isentropicTemperatureRatio(p.mach());
                double tol = expected * TOLERANCE_PCT;
                assertThat(p.temperature())
                        .as("T at M=%.4f (x=%.4f, y=%.4f)", p.mach(), p.x(), p.y())
                        .isCloseTo(expected, within(tol));
            }
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("P at every net point = Pc · (P/P0)(M) to 0.1%")
        void pressureConsistentWithMach() {
            NozzleDesignParameters params = standardParams();
            GasProperties gas = params.gasProperties();
            CharacteristicNet net = new CharacteristicNet(params).generate();

            for (CharacteristicPoint p : net.getAllPoints()) {
                double expected = params.chamberPressure() * gas.isentropicPressureRatio(p.mach());
                double tol = expected * TOLERANCE_PCT;
                assertThat(p.pressure())
                        .as("P at M=%.4f", p.mach())
                        .isCloseTo(expected, within(tol));
            }
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("ρ at every net point = P/(RT) (ideal gas law) to 0.1%")
        void densityConsistentWithPressureAndTemperature() {
            NozzleDesignParameters params = standardParams();
            GasProperties gas = params.gasProperties();
            CharacteristicNet net = new CharacteristicNet(params).generate();

            for (CharacteristicPoint p : net.getAllPoints()) {
                double expected = p.pressure() / (gas.gasConstant() * p.temperature());
                double tol = expected * TOLERANCE_PCT;
                assertThat(p.density())
                        .as("ρ = P/RT at M=%.4f", p.mach())
                        .isCloseTo(expected, within(tol));
            }
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("V at every net point = M · √(γRT) (speed of sound × Mach) to 0.1%")
        void velocityConsistentWithMachAndTemperature() {
            NozzleDesignParameters params = standardParams();
            GasProperties gas = params.gasProperties();
            CharacteristicNet net = new CharacteristicNet(params).generate();

            for (CharacteristicPoint p : net.getAllPoints()) {
                double a = gas.speedOfSound(p.temperature());
                double expected = p.mach() * a;
                double tol = expected * TOLERANCE_PCT;
                assertThat(p.velocity())
                        .as("V = M·a at M=%.4f", p.mach())
                        .isCloseTo(expected, within(tol));
            }
        }
    }

    // -----------------------------------------------------------------------
    // Mach angle and Prandtl-Meyer self-consistency at each stored point
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Point-level angle self-consistency")
    class AngleSelfConsistencyTests {

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("sin(μ) = 1/M at every net point to 1e-8 (exact Mach-cone relation)")
        void machAngleSatisfiesDefinition() {
            NozzleDesignParameters params = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0)
                    .chamberPressure(7e6).chamberTemperature(3500.0).ambientPressure(101325.0)
                    .gasProperties(GasProperties.AIR)
                    .numberOfCharLines(12).wallAngleInitialDegrees(25).lengthFraction(0.8)
                    .axisymmetric(true).build();

            CharacteristicNet net = new CharacteristicNet(params).generate();

            for (CharacteristicPoint p : net.getAllPoints()) {
                assertThat(Math.sin(p.mu()))
                        .as("sin(μ) at M=%.4f", p.mach())
                        .isCloseTo(1.0 / p.mach(), within(1e-8));
            }
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("ν stored = prandtlMeyerFunction(M stored) at every net point to 1e-6 rad")
        void nuConsistentWithMach() {
            NozzleDesignParameters params = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0)
                    .chamberPressure(7e6).chamberTemperature(3500.0).ambientPressure(101325.0)
                    .gasProperties(GasProperties.AIR)
                    .numberOfCharLines(12).wallAngleInitialDegrees(25).lengthFraction(0.8)
                    .axisymmetric(true).build();

            CharacteristicNet net = new CharacteristicNet(params).generate();
            GasProperties gas = params.gasProperties();

            for (CharacteristicPoint p : net.getAllPoints()) {
                double expectedNu = gas.prandtlMeyerFunction(p.mach());
                assertThat(p.nu())
                        .as("ν at M=%.4f", p.mach())
                        .isCloseTo(expectedNu, within(1e-6));
            }
        }
    }

    // -----------------------------------------------------------------------
    // Wall-point physics: isentropic expansion along the wall
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Wall point physics")
    class WallPointTests {

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Wall Mach increases monotonically downstream (flow accelerates)")
        void wallMachIncreasesDownstream() {
            NozzleDesignParameters params = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0)
                    .chamberPressure(7e6).chamberTemperature(3500.0).ambientPressure(101325.0)
                    .gasProperties(GasProperties.AIR)
                    .numberOfCharLines(15).wallAngleInitialDegrees(25).lengthFraction(0.8)
                    .axisymmetric(true).build();

            List<CharacteristicPoint> wallPts = new CharacteristicNet(params).generate().getWallPoints();
            assertThat(wallPts).hasSizeGreaterThan(1);

            for (int i = 1; i < wallPts.size(); i++) {
                assertThat(wallPts.get(i).mach())
                        .as("M at wall point %d > M at wall point %d", i, i - 1)
                        .isGreaterThan(wallPts.get(i - 1).mach() - 1e-6);
            }
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Wall pressure decreases monotonically (isentropic expansion)")
        void wallPressureDecreasesDownstream() {
            NozzleDesignParameters params = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0)
                    .chamberPressure(7e6).chamberTemperature(3500.0).ambientPressure(101325.0)
                    .gasProperties(GasProperties.AIR)
                    .numberOfCharLines(15).wallAngleInitialDegrees(25).lengthFraction(0.8)
                    .axisymmetric(true).build();

            List<CharacteristicPoint> wallPts = new CharacteristicNet(params).generate().getWallPoints();

            for (int i = 1; i < wallPts.size(); i++) {
                assertThat(wallPts.get(i).pressure())
                        .as("P at wall point %d < P at wall point %d", i, i - 1)
                        .isLessThan(wallPts.get(i - 1).pressure() + 1.0); // +1 Pa numerical tolerance
            }
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("First wall point Mach is supersonic (≥ 1) and less than design exit Mach")
        void firstWallPointNearThroat() {
            NozzleDesignParameters params = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0)
                    .chamberPressure(7e6).chamberTemperature(3500.0).ambientPressure(101325.0)
                    .gasProperties(GasProperties.AIR)
                    .numberOfCharLines(15).wallAngleInitialDegrees(25).lengthFraction(0.8)
                    .axisymmetric(true).build();

            CharacteristicPoint first = new CharacteristicNet(params).generate().getWallPoints().getFirst();
            // The first wall point originates from the initial-data line near the throat.
            // Its Mach is supersonic but below the design exit Mach.
            assertThat(first.mach()).isBetween(1.0, params.exitMach());
            // And it should be located at (or very near) the throat radius
            assertThat(first.y()).isCloseTo(params.throatRadius(), within(params.throatRadius() * 0.20));
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Every wall point: T = Tc·(T/T0)(M) to 0.1%  (isentropic wall)")
        void wallPointsAreIsentropic() {
            NozzleDesignParameters params = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0)
                    .chamberPressure(7e6).chamberTemperature(3500.0).ambientPressure(101325.0)
                    .gasProperties(GasProperties.AIR)
                    .numberOfCharLines(15).wallAngleInitialDegrees(25).lengthFraction(0.8)
                    .axisymmetric(true).build();

            GasProperties gas = params.gasProperties();
            List<CharacteristicPoint> wallPts = new CharacteristicNet(params).generate().getWallPoints();

            for (CharacteristicPoint p : wallPts) {
                double expected = params.chamberTemperature() * gas.isentropicTemperatureRatio(p.mach());
                assertThat(p.temperature())
                        .as("T at wall M=%.4f", p.mach())
                        .isCloseTo(expected, within(expected * 0.001));
            }
        }
    }
}
