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

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("RaspMotorData Tests")
class RaspMotorData_UT {

    // A simple rectangular-profile motor: constant 50 N for 1 s
    // Total impulse = 50 N·s, prop mass = 0.025 kg → Isp = 50/(0.025*9.80665) ≈ 203.9 s
    private static final String RECT_ENG = """
            G50-7 29 100 7 0.025 0.060 TEST
               0.000  50.000
               0.500  50.000
               1.000  50.000
               1.001   0.000
            ;
            """;

    private RaspMotorData motor;

    @BeforeEach
    void setUp() {
        motor = RaspImporter.parse(RECT_ENG);
    }

    // -----------------------------------------------------------------------
    // Derived performance quantities
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Derived Performance Quantities")
    class DerivedPerformance {

        @Test
        @DisplayName("totalImpulseNs: trapezoidal integral of constant 50 N over 1 s = 50 N·s")
        void totalImpulse() {
            // Exact: trapezoid of 50 N from t=0 to t=1.0, then 50→0 from 1.0 to 1.001
            // ≈ 50.0*1.0 + 0.5*(50+0)*0.001 = 50.025
            assertThat(motor.totalImpulseNs()).isCloseTo(50.025, within(1e-9));
        }

        @Test
        @DisplayName("burnTime: last time with positive thrust")
        void burnTime() {
            assertThat(motor.burnTime()).isCloseTo(1.000, within(1e-9));
        }

        @Test
        @DisplayName("maxThrustN: maximum from the data array")
        void maxThrust() {
            assertThat(motor.maxThrustN()).isCloseTo(50.0, within(1e-9));
        }

        @Test
        @DisplayName("averageThrustN = totalImpulse / burnTime")
        void averageThrust() {
            double expected = motor.totalImpulseNs() / motor.burnTime();
            assertThat(motor.averageThrustN()).isCloseTo(expected, within(1e-9));
        }

        @Test
        @DisplayName("specificImpulseSeconds = totalImpulse / (propMass * g0)")
        void specificImpulse() {
            double expected = motor.totalImpulseNs() / (0.025 * 9.80665);
            assertThat(motor.specificImpulseSeconds()).isCloseTo(expected, within(1e-6));
        }

        @Test
        @DisplayName("averageMassFlowRateKgPerS = propMass / burnTime")
        void averageMassFlowRate() {
            double expected = 0.025 / motor.burnTime();
            assertThat(motor.averageMassFlowRateKgPerS()).isCloseTo(expected, within(1e-9));
        }
    }

    // -----------------------------------------------------------------------
    // Motor classification
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Motor Classification")
    class MotorClassification {

        @ParameterizedTest(name = "impulse={0} N·s → class {1}")
        @CsvSource({
                "1.0,  A",
                "2.5,  A",
                "2.6,  B",
                "5.0,  B",
                "10.0, C",
                "40.0, E",
                "80.0, F",
                "160.0, G",
                "320.0, H",
                "640.0, I",
                "1280.0, J",
                "2560.0, K",
                "5120.0, L",
                "10240.0, M",
                "20480.0, N",
                "20481.0, O+"
        })
        @DisplayName("motorClass() returns correct letter for given total impulse")
        void motorClassCorrect(double impulseNs, String expectedClass) {
            // Build a synthetic motor with the target total impulse:
            //   two-point thrust curve: F = impulseNs / 1.0 s (constant)
            String eng = String.format(
                    "X 29 100 0 0.100 0.200 TEST\n 0.000 %f\n 1.000 %f\n;\n",
                    impulseNs, impulseNs);
            RaspMotorData m = RaspImporter.parse(eng);
            assertThat(m.motorClass()).isEqualTo(expectedClass);
        }

        @Test
        @DisplayName("50 N·s total impulse is class F (40–80 N·s range)")
        void fiftyNsIsClassF() {
            assertThat(motor.motorClass()).isEqualTo("F");
        }
    }

    // -----------------------------------------------------------------------
    // Pipeline bridge — toNozzleParameters
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Pipeline Bridge")
    class PipelineBridge {

        private NozzleDesignParameters template;

        @BeforeEach
        void buildTemplate() {
            template = NozzleDesignParameters.builder()
                    .throatRadius(0.010)
                    .exitMach(3.0)
                    .ambientPressure(101325)
                    .chamberPressure(5.0e6)   // placeholder — will be overridden
                    .chamberTemperature(3200)
                    .gasProperties(GasProperties.APCP_HTPB_PRODUCTS)
                    .axisymmetric(true)
                    .lengthFraction(0.8)
                    .build();
        }

        @Test
        @DisplayName("toNozzleParameters() returns non-null")
        void notNull() {
            assertThat(motor.toNozzleParameters(template)).isNotNull();
        }

        @Test
        @DisplayName("toNozzleParameters() overrides chamberPressure with Pc derived from avgThrust")
        void overridesChamberPressure() {
            NozzleDesignParameters params = motor.toNozzleParameters(template);
            // Pc must differ from the template placeholder of 5 MPa
            assertThat(params.chamberPressure())
                    .isNotCloseTo(5.0e6, within(1000.0));
        }

        @Test
        @DisplayName("toNozzleParameters() preserves all non-pressure template fields")
        void preservesTemplateFields() {
            NozzleDesignParameters params = motor.toNozzleParameters(template);
            assertThat(params.throatRadius()).isEqualTo(template.throatRadius());
            assertThat(params.exitMach()).isEqualTo(template.exitMach());
            assertThat(params.ambientPressure()).isEqualTo(template.ambientPressure());
            assertThat(params.chamberTemperature()).isEqualTo(template.chamberTemperature());
            assertThat(params.gasProperties()).isSameAs(template.gasProperties());
            assertThat(params.lengthFraction()).isEqualTo(template.lengthFraction());
        }

        @Test
        @DisplayName("toNozzleParametersAtMaxPressure() Pc > toNozzleParameters() Pc for a peaked thrust trace")
        void maxPressureGtAvgPressure() {
            // Peaked motor: thrust spikes to 200 N then falls; max > average by design
            RaspMotorData peaked = RaspImporter.parse("""
                    H100-7 38 193 7 0.100 0.200 TEST
                       0.000    0.000
                       0.100   80.000
                       0.300  200.000
                       0.500   80.000
                       0.600    0.000
                    ;
                    """);
            double avgPc = peaked.toNozzleParameters(template).chamberPressure();
            double maxPc = peaked.toNozzleParametersAtMaxPressure(template).chamberPressure();
            assertThat(maxPc).isGreaterThan(avgPc);
        }

        @Test
        @DisplayName("Pc from thrust is positive")
        void pcIsPositive() {
            assertThat(motor.toNozzleParameters(template).chamberPressure())
                    .isGreaterThan(0.0);
        }

        @Test
        @DisplayName("Pressure inversion round-trip: F(Pc) → Pc → F(Pc) consistent within 0.1%")
        void pressureInversionRoundTrip() {
            // Recover average Pc from average thrust
            double avgPc     = motor.toNozzleParameters(template).chamberPressure();
            double gamma     = template.gasProperties().gamma();
            double me        = template.exitMach();
            double at        = Math.PI * template.throatRadius() * template.throatRadius();
            double cf = getCf(gamma, me, avgPc);
            double reconstructedF = cf * avgPc * at;

            assertThat(reconstructedF)
                    .isCloseTo(motor.averageThrustN(), withinPercentage(0.1));
        }

        private double getCf(double gamma, double me, double avgPc) {
            double pa        = template.ambientPressure();

            double tt       = 1.0 + 0.5 * (gamma - 1.0) * me * me;
            double peOverPc = Math.pow(tt, -gamma / (gamma - 1.0));
            double aeOverAt = (1.0 / me) * Math.pow(
                    (2.0 / (gamma + 1.0)) * tt,
                    (gamma + 1.0) / (2.0 * (gamma - 1.0)));
            double sqrtTerm = Math.sqrt(
                    2.0 * gamma * gamma / (gamma - 1.0)
                    * Math.pow(2.0 / (gamma + 1.0), (gamma + 1.0) / (gamma - 1.0))
                    * (1.0 - Math.pow(peOverPc, (gamma - 1.0) / gamma)));

            // Reconstruct thrust from Pc
           return sqrtTerm + (peOverPc - pa / avgPc) * aeOverAt;  // Cf
        }
    }
}
