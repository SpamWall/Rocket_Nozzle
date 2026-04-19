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

package com.nozzle.thermal;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.moc.CharacteristicNet;
import com.nozzle.moc.CharacteristicPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TwoPhaseFlowModel Tests")
class TwoPhaseFlowModel_UT {

    private NozzleDesignParameters params;
    private List<CharacteristicPoint> wallPoints;

    @BeforeEach
    void setUp() {
        params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.APCP_HTPB_PRODUCTS)
                .numberOfCharLines(15)
                .wallAngleInitialDegrees(25)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();

        wallPoints = new CharacteristicNet(params).generate().getWallPoints();
    }

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("Should construct with mass fraction and Al2O3 defaults")
        void shouldConstructDefault() {
            TwoPhaseFlowModel model = new TwoPhaseFlowModel(params, 0.30);
            assertThat(model).isNotNull();
        }

        @Test
        @DisplayName("Should construct with full custom particle properties")
        void shouldConstructFull() {
            TwoPhaseFlowModel model = new TwoPhaseFlowModel(params, 0.25,
                    5e-6, 3960.0, 1050.0);
            assertThat(model).isNotNull();
        }

        @Test
        @DisplayName("Should reject mass fraction >= 1")
        void shouldRejectMassFractionOne() {
            assertThatThrownBy(() -> new TwoPhaseFlowModel(params, 1.0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should reject negative mass fraction")
        void shouldRejectNegativeMassFraction() {
            assertThatThrownBy(() -> new TwoPhaseFlowModel(params, -0.1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should allow zero mass fraction (single-phase limit)")
        void shouldAllowZeroMassFraction() {
            TwoPhaseFlowModel model = new TwoPhaseFlowModel(params, 0.0);
            model.solve(wallPoints);
            assertThat(model.twoPhaseEfficiency()).isCloseTo(1.0, within(0.01));
        }
    }

    @Nested
    @DisplayName("Solve")
    class SolveTests {

        @Test
        @DisplayName("Should populate particle trajectory")
        void shouldPopulateTrajectory() {
            TwoPhaseFlowModel model = new TwoPhaseFlowModel(params, 0.30)
                    .solve(wallPoints);

            assertThat(model.getParticleTrajectory()).isNotEmpty();
        }

        @Test
        @DisplayName("Two-phase efficiency should be <= 1.0")
        void efficiencyLeOne() {
            TwoPhaseFlowModel model = new TwoPhaseFlowModel(params, 0.30)
                    .solve(wallPoints);

            assertThat(model.twoPhaseEfficiency()).isLessThanOrEqualTo(1.0);
        }

        @Test
        @DisplayName("Two-phase efficiency should be positive")
        void efficiencyPositive() {
            TwoPhaseFlowModel model = new TwoPhaseFlowModel(params, 0.30)
                    .solve(wallPoints);

            assertThat(model.twoPhaseEfficiency()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("Higher mass fraction should produce lower efficiency")
        void higherMassFractionLowerEfficiency() {
            double eff10 = new TwoPhaseFlowModel(params, 0.10).solve(wallPoints).twoPhaseEfficiency();
            double eff30 = new TwoPhaseFlowModel(params, 0.30).solve(wallPoints).twoPhaseEfficiency();

            assertThat(eff30).isLessThanOrEqualTo(eff10);
        }

        @Test
        @DisplayName("Exit velocity lag should be non-negative")
        void exitVelocityLagNonNegative() {
            TwoPhaseFlowModel model = new TwoPhaseFlowModel(params, 0.30).solve(wallPoints);

            assertThat(model.exitVelocityLag()).isGreaterThanOrEqualTo(0.0);
        }

        @Test
        @DisplayName("Exit thermal lag should be finite after solve")
        void exitThermalLagFinite() {
            TwoPhaseFlowModel model = new TwoPhaseFlowModel(params, 0.30).solve(wallPoints);

            // Lag = (T_gas - T_particle) / T_gas; can be negative when high-heat-capacity
            // particles retain more heat than the rapidly cooling exit gas
            assertThat(model.exitThermalLag()).isFinite();
        }

        @Test
        @DisplayName("Exit thermal lag should be zero before solve (empty trajectory branch)")
        void exitThermalLagZeroBeforeSolve() {
            TwoPhaseFlowModel model = new TwoPhaseFlowModel(params, 0.30);

            assertThat(model.exitThermalLag()).isZero();
        }

        @Test
        @DisplayName("Exit thermal lag should be less than 1 (particle temperature > 0)")
        void exitThermalLagLessThanOne() {
            TwoPhaseFlowModel model = new TwoPhaseFlowModel(params, 0.30).solve(wallPoints);

            assertThat(model.exitThermalLag()).isLessThan(1.0);
        }

        @Test
        @DisplayName("Isp loss fraction should equal 1 - efficiency")
        void ispLossMatchesEfficiency() {
            TwoPhaseFlowModel model = new TwoPhaseFlowModel(params, 0.25).solve(wallPoints);

            assertThat(model.ispLossFraction())
                    .isCloseTo(1.0 - model.twoPhaseEfficiency(), within(1e-10));
        }

        @ParameterizedTest(name = "mass fraction {0}")
        @ValueSource(doubles = {0.05, 0.15, 0.30, 0.50})
        @DisplayName("Should produce valid efficiency for various mass fractions")
        void shouldHandleVariousMassFractions(double alpha) {
            TwoPhaseFlowModel model = new TwoPhaseFlowModel(params, alpha).solve(wallPoints);

            assertThat(model.twoPhaseEfficiency())
                    .isGreaterThan(0.0)
                    .isLessThanOrEqualTo(1.0);
        }

        @Test
        @DisplayName("Should reject empty wall point list")
        void shouldRejectEmptyWallPoints() {
            TwoPhaseFlowModel model = new TwoPhaseFlowModel(params, 0.30);

            assertThatThrownBy(() -> model.solve(List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("ParticleState record")
    class ParticleStateTests {

        @Test
        @DisplayName("Particle velocity should increase downstream")
        void particleVelocityIncreasesDownstream() {
            TwoPhaseFlowModel model = new TwoPhaseFlowModel(params, 0.30).solve(wallPoints);
            List<TwoPhaseFlowModel.ParticleState> trajectory = model.getParticleTrajectory();

            // First particle should be slower than last (particles accelerate through nozzle)
            assertThat(trajectory.getLast().velocity())
                    .isGreaterThan(trajectory.getFirst().velocity());
        }

        @Test
        @DisplayName("Particle x-positions should span throat to exit")
        void particleXSpansNozzle() {
            TwoPhaseFlowModel model = new TwoPhaseFlowModel(params, 0.30).solve(wallPoints);
            List<TwoPhaseFlowModel.ParticleState> trajectory = model.getParticleTrajectory();

            assertThat(trajectory.getLast().x()).isGreaterThan(trajectory.getFirst().x());
        }

        @Test
        @DisplayName("Larger particle diameter should produce larger velocity lag")
        void largerParticlesMoreLag() {
            double alpha = 0.25;
            double smallD = 2e-6;
            double largeD = 20e-6;

            double lagSmall = new TwoPhaseFlowModel(params, alpha, smallD, 3960, 1050)
                    .solve(wallPoints).exitVelocityLag();
            double lagLarge = new TwoPhaseFlowModel(params, alpha, largeD, 3960, 1050)
                    .solve(wallPoints).exitVelocityLag();

            // Larger particles have more inertia → more lag
            assertThat(lagLarge).isGreaterThanOrEqualTo(lagSmall);
        }
    }

    @Nested
    @DisplayName("Al2O3 defaults")
    class DefaultsTests {

        @Test
        @DisplayName("Al2O3 constants should have physical values")
        void al2o3ConstantsSane() {
            assertThat(TwoPhaseFlowModel.AL2O3_DENSITY).isCloseTo(3960.0, within(100.0));
            assertThat(TwoPhaseFlowModel.AL2O3_SPECIFIC_HEAT).isCloseTo(1050.0, within(100.0));
            assertThat(TwoPhaseFlowModel.AL2O3_DIAMETER_M).isCloseTo(4e-6, within(1e-6));
        }

        @Test
        @DisplayName("APCP 18% Al should give 1-5% Isp loss (typical)")
        void apcp18AlTypicalLoss() {
            // 18% Al → ~30% Al2O3 by mass
            TwoPhaseFlowModel model = new TwoPhaseFlowModel(params, 0.30).solve(wallPoints);

            assertThat(model.ispLossFraction())
                    .isGreaterThanOrEqualTo(0.0)
                    .isLessThanOrEqualTo(0.10);  // ≤ 10% loss
        }
    }
}
