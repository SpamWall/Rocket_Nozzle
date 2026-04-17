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

package com.nozzle.solid;

import com.nozzle.core.NozzleDesignParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SolidMotorChamber Tests")
class SolidMotorChamber_UT {

    // Small APCP motor: 4-segment BATES grain
    // Do=100mm, Dp=50mm, L=75mm  → web = 25mm
    // Throat radius = 10mm → At = pi*(0.01)^2 ≈ 3.142e-4 m²
    private static final double THROAT_RADIUS = 0.010;
    private static final double THROAT_AREA   = Math.PI * THROAT_RADIUS * THROAT_RADIUS;

    private SolidPropellant     propellant;
    private GrainGeometry       grain;
    private SolidMotorChamber   chamber;

    @BeforeEach
    void setUp() {
        propellant = SolidPropellant.APCP_HTPB();
        grain      = new BatesGrain(0.100, 0.050, 0.075, 4);
        chamber    = new SolidMotorChamber(propellant, grain, THROAT_AREA);
    }

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {

        @Test
        @DisplayName("Valid parameters construct without exception")
        void validParametersConstruct() {
            assertThatCode(() -> new SolidMotorChamber(propellant, grain, THROAT_AREA))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("null propellant throws IllegalArgumentException")
        void nullPropellantThrows() {
            assertThatThrownBy(() -> new SolidMotorChamber(null, grain, THROAT_AREA))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null grain throws IllegalArgumentException")
        void nullGrainThrows() {
            assertThatThrownBy(() -> new SolidMotorChamber(propellant, null, THROAT_AREA))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throatArea <= 0 throws IllegalArgumentException")
        void nonPositiveThroatAreaThrows() {
            assertThatThrownBy(() -> new SolidMotorChamber(propellant, grain, 0.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Throat area");
        }

        @Test
        @DisplayName("Accessors return constructed values")
        void accessorsReturnConstructedValues() {
            assertThat(chamber.propellant()).isSameAs(propellant);
            assertThat(chamber.grain()).isSameAs(grain);
            assertThat(chamber.throatArea()).isEqualTo(THROAT_AREA);
        }
    }

    // -----------------------------------------------------------------------
    // Point calculations
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Point Calculation Tests")
    class PointCalculationTests {

        @Test
        @DisplayName("klemmung(y) = burningArea(y) / throatArea")
        void klemmungEqualsRatio() {
            for (double y : new double[]{0.0, 0.010, 0.020, grain.webThickness()}) {
                double expected = grain.burningArea(y) / THROAT_AREA;
                assertThat(chamber.klemmung(y)).isCloseTo(expected, within(1e-10));
            }
        }

        @Test
        @DisplayName("chamberPressure(y) is positive throughout the burn")
        void chamberPressurePositive() {
            double w = grain.webThickness();
            for (int i = 0; i <= 10; i++) {
                assertThat(chamber.chamberPressure(w * i / 10.0)).isGreaterThan(0.0);
            }
        }

        @Test
        @DisplayName("chamberPressure(y, T_ref) matches single-arg overload")
        void chamberPressureRefTempMatchesSingleArg() {
            assertThat(chamber.chamberPressure(0.0, propellant.referenceTemperature()))
                    .isCloseTo(chamber.chamberPressure(0.0), within(1e-6));
        }

        @Test
        @DisplayName("burnRate(y) is positive throughout the burn")
        void burnRatePositive() {
            double w = grain.webThickness();
            for (int i = 0; i <= 10; i++) {
                assertThat(chamber.burnRate(w * i / 10.0)).isGreaterThan(0.0);
            }
        }

        @Test
        @DisplayName("estimateBurnTime() is positive and within plausible range (0.1 s – 300 s)")
        void estimateBurnTimePlausible() {
            double est = chamber.estimateBurnTime(294.0);
            assertThat(est).isGreaterThan(0.1).isLessThan(300.0);
        }
    }

    // -----------------------------------------------------------------------
    // Trajectory
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Trajectory Tests")
    class TrajectoryTests {

        @Test
        @DisplayName("computeBurnTrajectory(T) returns non-null trajectory with data")
        void trajectoryNonNull() {
            BurnTrajectory traj = chamber.computeBurnTrajectory(294.0);
            assertThat(traj).isNotNull();
            assertThat(traj.size()).isGreaterThan(1);
        }

        @Test
        @DisplayName("trajectory burnTime() is positive")
        void trajectoryBurnTimePositive() {
            assertThat(chamber.computeBurnTrajectory(294.0).burnTime()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("trajectory first time is 0 and last time equals burnTime()")
        void trajectoryTimeArrayBounds() {
            BurnTrajectory traj = chamber.computeBurnTrajectory(294.0);
            assertThat(traj.timeAt(0)).isEqualTo(0.0);
            assertThat(traj.timeAt(traj.size() - 1)).isCloseTo(traj.burnTime(), within(1e-12));
        }

        @Test
        @DisplayName("trajectory time is monotonically increasing")
        void trajectoryTimeMonotonic() {
            BurnTrajectory traj = chamber.computeBurnTrajectory(294.0);
            for (int i = 1; i < traj.size(); i++) {
                assertThat(traj.timeAt(i)).isGreaterThan(traj.timeAt(i - 1));
            }
        }

        @Test
        @DisplayName("trajectory webBurned is monotonically non-decreasing")
        void trajectoryWebMonotonic() {
            BurnTrajectory traj = chamber.computeBurnTrajectory(294.0);
            for (int i = 1; i < traj.size(); i++) {
                assertThat(traj.webBurnedAt(i)).isGreaterThanOrEqualTo(traj.webBurnedAt(i - 1));
            }
        }

        @Test
        @DisplayName("trajectory final web burned equals grain web thickness")
        void trajectoryFinalWebAtBurnout() {
            BurnTrajectory traj = chamber.computeBurnTrajectory(294.0);
            assertThat(traj.webBurnedAt(traj.size() - 1))
                    .isCloseTo(grain.webThickness(), within(1e-9));
        }

        @Test
        @DisplayName("trajectory all chamberPressure values are positive")
        void trajectoryPressurePositive() {
            BurnTrajectory traj = chamber.computeBurnTrajectory(294.0);
            for (int i = 0; i < traj.size(); i++) {
                assertThat(traj.chamberPressureAt(i)).isGreaterThan(0.0);
            }
        }

        @Test
        @DisplayName("trajectory all massFlowRate values are positive")
        void trajectoryMassFlowPositive() {
            BurnTrajectory traj = chamber.computeBurnTrajectory(294.0);
            for (int i = 0; i < traj.size(); i++) {
                assertThat(traj.massFlowRateAt(i)).isGreaterThan(0.0);
            }
        }

        @Test
        @DisplayName("averagePressure() is between initial and max pressure")
        void averagePressureBounded() {
            BurnTrajectory traj = chamber.computeBurnTrajectory(294.0);
            assertThat(traj.averagePressure())
                    .isGreaterThan(0.0)
                    .isLessThanOrEqualTo(traj.maxPressure());
        }

        @Test
        @DisplayName("maxPressure() >= all individual pressures")
        void maxPressureIsMax() {
            BurnTrajectory traj = chamber.computeBurnTrajectory(294.0);
            for (int i = 0; i < traj.size(); i++) {
                assertThat(traj.maxPressure()).isGreaterThanOrEqualTo(traj.chamberPressureAt(i));
            }
        }

        @Test
        @DisplayName("non-positive timeStep throws IllegalArgumentException")
        void nonPositiveTimeStepThrows() {
            assertThatThrownBy(() -> chamber.computeBurnTrajectory(294.0, 0.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Time step");
        }

        @Test
        @DisplayName("Finer time step does not change burnTime by more than 1%")
        void finerTimestepConverges() {
            BurnTrajectory coarse = chamber.computeBurnTrajectory(294.0, 0.01);
            BurnTrajectory fine   = chamber.computeBurnTrajectory(294.0, 0.001);
            assertThat(fine.burnTime())
                    .isCloseTo(coarse.burnTime(), withinPercentage(1.0));
        }
    }

    // -----------------------------------------------------------------------
    // Pipeline bridge
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("toNozzleParameters Bridge Tests")
    class BridgeTests {

        @Test
        @DisplayName("toNozzleParameters() produces a non-null NozzleDesignParameters")
        void toNozzleParamsNotNull() {
            BurnTrajectory traj = chamber.computeBurnTrajectory(294.0);
            NozzleDesignParameters template = NozzleDesignParameters.builder()
                    .throatRadius(THROAT_RADIUS)
                    .exitMach(3.0)
                    .ambientPressure(101325)
                    .build();
            assertThat(traj.toNozzleParameters(template)).isNotNull();
        }

        @Test
        @DisplayName("toNozzleParameters() uses trajectory averagePressure as chamberPressure")
        void toNozzleParamsUsesAveragePressure() {
            BurnTrajectory traj = chamber.computeBurnTrajectory(294.0);
            NozzleDesignParameters template = NozzleDesignParameters.builder()
                    .throatRadius(THROAT_RADIUS)
                    .exitMach(3.0)
                    .ambientPressure(101325)
                    .build();
            NozzleDesignParameters params = traj.toNozzleParameters(template);
            assertThat(params.chamberPressure()).isCloseTo(traj.averagePressure(), within(1.0));
        }

        @Test
        @DisplayName("toNozzleParametersAtMaxPressure() chamberPressure equals maxPressure")
        void toNozzleParamsMaxPressure() {
            BurnTrajectory traj = chamber.computeBurnTrajectory(294.0);
            NozzleDesignParameters template = NozzleDesignParameters.builder()
                    .throatRadius(THROAT_RADIUS)
                    .exitMach(3.0)
                    .ambientPressure(101325)
                    .build();
            NozzleDesignParameters params = traj.toNozzleParametersAtMaxPressure(template);
            assertThat(params.chamberPressure()).isCloseTo(traj.maxPressure(), within(1.0));
        }

        @Test
        @DisplayName("Returned NozzleDesignParameters preserves template throatRadius")
        void toNozzleParamsPreservesThroatRadius() {
            BurnTrajectory traj = chamber.computeBurnTrajectory(294.0);
            NozzleDesignParameters template = NozzleDesignParameters.builder()
                    .throatRadius(THROAT_RADIUS)
                    .exitMach(3.0)
                    .ambientPressure(101325)
                    .build();
            assertThat(traj.toNozzleParameters(template).throatRadius())
                    .isEqualTo(THROAT_RADIUS);
        }
    }
}
