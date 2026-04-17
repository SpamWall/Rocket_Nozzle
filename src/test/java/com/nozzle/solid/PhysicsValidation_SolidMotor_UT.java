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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Physics-level validation tests for the solid motor model.
 *
 * <p>These tests verify that the classes produce results consistent with
 * published ballistic theory and well-known closed-form results, rather than
 * merely exercising the code paths.  Each test cites the physical principle
 * or reference value it is checking.
 */
@DisplayName("Physics Validation — Solid Motor")
class PhysicsValidation_SolidMotor_UT {

    // -----------------------------------------------------------------------
    // Vieille's law (burn-rate pressure dependence)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Vieille's Law")
    class VieilleLaw {

        @Test
        @DisplayName("Pressure exponent: r(2P)/r(P) = 2^n (Vieille's law identity)")
        void pressureExponentIdentity() {
            SolidPropellant p = SolidPropellant.APCP_HTPB();
            double n  = p.burnRateExponent();
            double pc = 5.0e6;
            double ratio = p.burnRate(2 * pc) / p.burnRate(pc);
            assertThat(ratio).isCloseTo(Math.pow(2.0, n), within(1e-10));
        }

        @Test
        @DisplayName("Temperature sensitivity: r(T)/r(Tref) = exp(sigma_p * deltaT)")
        void temperatureSensitivityExponential() {
            SolidPropellant p = SolidPropellant.APCP_HTPB();
            double deltaT = 50.0;  // K above reference
            double pc     = 7.0e6;
            double ratio  = p.burnRate(pc, p.referenceTemperature() + deltaT)
                            / p.burnRate(pc);
            double expected = Math.exp(p.temperatureSensitivity() * deltaT);
            assertThat(ratio).isCloseTo(expected, within(1e-10));
        }

        @Test
        @DisplayName("Zero temperature offset: burnRate(P, Tref) == burnRate(P)")
        void zeroTempOffsetIsIdentity() {
            SolidPropellant p = SolidPropellant.KNSU();
            double pc = 1.5e6;
            assertThat(p.burnRate(pc, p.referenceTemperature())).isEqualTo(p.burnRate(pc));
        }
    }

    // -----------------------------------------------------------------------
    // Kn equation (quasi-steady equilibrium pressure)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Kn Equation (Equilibrium Pressure)")
    class KnEquation {

        @Test
        @DisplayName("P = (rho_p * a * Kn * c*)^(1/(1-n)) matches direct formula")
        void equilibriumPressureMatchesFormula() {
            SolidPropellant p  = SolidPropellant.APCP_HTPB();
            double ab = 0.020, at = 2e-4;
            double kn = ab / at;
            double expected = Math.pow(
                    p.density() * p.burnRateCoefficient() * kn * p.characteristicVelocity(),
                    1.0 / (1.0 - p.burnRateExponent()));
            assertThat(p.equilibriumPressure(ab, at)).isCloseTo(expected, within(1.0));
        }

        @Test
        @DisplayName("At equilibrium: rho_p * Ab * r(Pc) == Pc * At / c*  (mass balance)")
        void equilibriumMassBalance() {
            SolidPropellant p  = SolidPropellant.APCP_HTPB();
            double ab = 0.020, at = 2e-4;
            double pc = p.equilibriumPressure(ab, at);
            double mGenerated = p.density() * ab * p.burnRate(pc);
            double mExhausted = pc * at / p.characteristicVelocity();
            assertThat(mGenerated).isCloseTo(mExhausted, withinPercentage(0.01));
        }

        @Test
        @DisplayName("Pressure stability: doubling Ab doubles Kn and raises P by 2^(1/(1-n))")
        void pressureRaisesWithKn() {
            SolidPropellant p  = SolidPropellant.APCP_HTPB();
            double at = 1e-4;
            double ab1 = 0.01, ab2 = 0.02;
            double pc1 = p.equilibriumPressure(ab1, at);
            double pc2 = p.equilibriumPressure(ab2, at);
            double n   = p.burnRateExponent();
            assertThat(pc2 / pc1).isCloseTo(Math.pow(2.0, 1.0 / (1.0 - n)), within(1e-6));
        }
    }

    // -----------------------------------------------------------------------
    // BATES grain — analytical area formula
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("BATES Grain Analytical Check")
    class BatesAnalytical {

        @Test
        @DisplayName("burningArea(0) matches closed-form: N*(pi*Dp*L + pi/2*(Do^2-Dp^2))")
        void initialAreaClosedForm() {
            double do_ = 0.100, dp = 0.050, l = 0.075;
            BatesGrain g = new BatesGrain(do_, dp, l, 3);
            double expected = 3 * (Math.PI * dp * l + 0.5 * Math.PI * (do_*do_ - dp*dp));
            assertThat(g.burningArea(0.0)).isCloseTo(expected, within(1e-12));
        }

        @Test
        @DisplayName("Neutral-length condition: Ab(0) == Ab(web) when L = (3*Do+Dp)/2")
        void neutralLengthEqualArea() {
            double do_ = 0.120, dp = 0.060;
            double lNeutral = (3.0 * do_ + dp) / 2.0;  // = 0.210 m
            BatesGrain g = new BatesGrain(do_, dp, lNeutral, 1);
            assertThat(g.burningArea(0.0))
                    .isCloseTo(g.burningArea(g.webThickness()), within(1e-9));
        }

        @Test
        @DisplayName("propellantVolume = N * pi/4 * (Do^2 - Dp^2) * L")
        void propellantVolumeClosedForm() {
            double do_ = 0.100, dp = 0.050, l = 0.080;
            BatesGrain g = new BatesGrain(do_, dp, l, 2);
            double expected = 2 * Math.PI / 4 * (do_*do_ - dp*dp) * l;
            assertThat(g.propellantVolume()).isCloseTo(expected, within(1e-12));
        }
    }

    // -----------------------------------------------------------------------
    // End-burning grain — perfectly neutral
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("End-Burning Grain Analytical Check")
    class EndBurningAnalytical {

        @Test
        @DisplayName("Ab is constant to machine precision across full web (perfectly neutral)")
        void perfectlyNeutral() {
            EndBurningGrain g = new EndBurningGrain(0.075, 0.250);
            double ab0 = g.burningArea(0.0);
            for (int i = 1; i <= 50; i++) {
                double y = g.webThickness() * i / 50.0;
                assertThat(g.burningArea(y)).isEqualTo(ab0);
            }
        }

        @Test
        @DisplayName("Ab equals pi/4 * D^2")
        void areaEqualsCircleArea() {
            double d = 0.080;
            EndBurningGrain g = new EndBurningGrain(d, 0.200);
            assertThat(g.burningArea(0.0))
                    .isCloseTo(Math.PI / 4 * d * d, within(1e-12));
        }
    }

    // -----------------------------------------------------------------------
    // Mass conservation through burn trajectory
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Mass Conservation")
    class MassConservation {

        @Test
        @DisplayName("Integrated mass flow rate over burn ≈ propellant mass (within 1%)")
        void massConservation() {
            SolidPropellant propellant = SolidPropellant.APCP_HTPB();
            GrainGeometry   grain      = new BatesGrain(0.100, 0.050, 0.075, 4);
            double at = Math.PI * 0.010 * 0.010;
            SolidMotorChamber chamber  = new SolidMotorChamber(propellant, grain, at);

            BurnTrajectory traj = chamber.computeBurnTrajectory(294.0, 0.001);

            double integratedMass = 0.0;
            for (int i = 1; i < traj.size(); i++) {
                double dt   = traj.timeAt(i) - traj.timeAt(i - 1);
                double mdot = 0.5 * (traj.massFlowRateAt(i) + traj.massFlowRateAt(i - 1));
                integratedMass += mdot * dt;
            }

            double initialMass = traj.propellantMass();
            assertThat(integratedMass).isCloseTo(initialMass, withinPercentage(1.0));
        }

        @Test
        @DisplayName("End-burning grain mass conservation (perfectly flat P trace)")
        void endBurningMassConservation() {
            SolidPropellant propellant = SolidPropellant.KNSU();
            EndBurningGrain grain      = new EndBurningGrain(0.075, 0.200);
            double at = Math.PI * 0.006 * 0.006;
            SolidMotorChamber chamber  = new SolidMotorChamber(propellant, grain, at);

            BurnTrajectory traj = chamber.computeBurnTrajectory(294.0, 0.001);

            double integratedMass = 0.0;
            for (int i = 1; i < traj.size(); i++) {
                double dt   = traj.timeAt(i) - traj.timeAt(i - 1);
                double mdot = 0.5 * (traj.massFlowRateAt(i) + traj.massFlowRateAt(i - 1));
                integratedMass += mdot * dt;
            }
            assertThat(integratedMass).isCloseTo(traj.propellantMass(), withinPercentage(1.0));
        }
    }

    // -----------------------------------------------------------------------
    // End-burning motor: constant pressure trace
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("End-Burning Motor Pressure Trace")
    class EndBurningPressureTrace {

        @Test
        @DisplayName("End-burning motor has essentially constant chamber pressure (within 0.1%)")
        void pressureConstant() {
            SolidPropellant propellant = SolidPropellant.KNSU();
            EndBurningGrain grain      = new EndBurningGrain(0.075, 0.200);
            double at = Math.PI * 0.006 * 0.006;
            SolidMotorChamber chamber  = new SolidMotorChamber(propellant, grain, at);

            BurnTrajectory traj = chamber.computeBurnTrajectory(294.0, 0.001);

            double p0 = traj.chamberPressureAt(0);
            for (int i = 1; i < traj.size() - 1; i++) {
                assertThat(traj.chamberPressureAt(i))
                        .as("Pressure at step %d", i)
                        .isCloseTo(p0, withinPercentage(0.1));
            }
        }
    }

    // -----------------------------------------------------------------------
    // Temperature sensitivity on burn time
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Temperature Sensitivity")
    class TemperatureSensitivity {

        @Test
        @DisplayName("Hot propellant burns faster → shorter burn time than cold")
        void hotPropellantBurnsFaster() {
            SolidPropellant propellant = SolidPropellant.APCP_HTPB();
            GrainGeometry   grain      = new BatesGrain(0.100, 0.050, 0.075, 2);
            double at = Math.PI * 0.010 * 0.010;
            SolidMotorChamber chamber  = new SolidMotorChamber(propellant, grain, at);

            BurnTrajectory cold = chamber.computeBurnTrajectory(233.0);  // -40 °C
            BurnTrajectory hot  = chamber.computeBurnTrajectory(344.0);  // +71 °C

            assertThat(hot.burnTime()).isLessThan(cold.burnTime());
        }

        @Test
        @DisplayName("Hot propellant produces higher average chamber pressure")
        void hotPropellantHigherPressure() {
            SolidPropellant propellant = SolidPropellant.APCP_HTPB();
            GrainGeometry   grain      = new BatesGrain(0.100, 0.050, 0.075, 2);
            double at = Math.PI * 0.010 * 0.010;
            SolidMotorChamber chamber  = new SolidMotorChamber(propellant, grain, at);

            BurnTrajectory cold = chamber.computeBurnTrajectory(233.0);
            BurnTrajectory hot  = chamber.computeBurnTrajectory(344.0);

            assertThat(hot.averagePressure()).isGreaterThan(cold.averagePressure());
        }
    }
}
