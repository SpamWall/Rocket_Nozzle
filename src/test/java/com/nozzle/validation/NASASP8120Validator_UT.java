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

package com.nozzle.validation;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.moc.CharacteristicNet;
import com.nozzle.moc.CharacteristicPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("NASASP8120Validator Tests")
class NASASP8120Validator_UT {
    
    private NozzleDesignParameters params;
    private NASASP8120Validator validator;
    
    @BeforeEach
    void setUp() {
        params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();
        
        validator = new NASASP8120Validator();
    }
    
    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {
        
        @Test
        @DisplayName("Should create validator")
        void shouldCreateValidator() {
            assertThat(new NASASP8120Validator()).isNotNull();
        }
    }
    
    @Nested
    @DisplayName("Parameter Validation Tests")
    class ParameterValidationTests {
        
        @Test
        @DisplayName("Should validate design parameters")
        void shouldValidateDesignParameters() {
            NASASP8120Validator.ValidationResult result = validator.validate(params);
            
            assertThat(result).isNotNull();
            assertThat(result.metrics()).isNotEmpty();
        }
        
        @Test
        @DisplayName("Valid design should pass validation")
        void validDesignShouldPassValidation() {
            NASASP8120Validator.ValidationResult result = validator.validate(params);
            
            assertThat(result.isValid()).isTrue();
        }
        
        @Test
        @DisplayName("Should compute area ratio error")
        void shouldComputeAreaRatioError() {
            NASASP8120Validator.ValidationResult result = validator.validate(params);
            
            assertThat(result.metrics()).containsKey("area_ratio_error_percent");
            double error = result.metrics().get("area_ratio_error_percent");
            assertThat(error).isLessThan(5.0);
        }
        
        @Test
        @DisplayName("Should compute thrust coefficient error")
        void shouldComputeThrustCoefficientError() {
            NASASP8120Validator.ValidationResult result = validator.validate(params);
            
            assertThat(result.metrics()).containsKey("thrust_coeff_error_percent");
        }
    }
    
    @Nested
    @DisplayName("Net Validation Tests")
    class NetValidationTests {
        
        @Test
        @DisplayName("Should validate characteristic net")
        void shouldValidateCharacteristicNet() {
            CharacteristicNet net = new CharacteristicNet(params).generate();
            
            NASASP8120Validator.ValidationResult result = validator.validate(net);
            
            assertThat(result).isNotNull();
            assertThat(result.metrics()).containsKey("exit_mach_error_percent");
        }
    }
    
    @Nested
    @DisplayName("Warning Tests")
    class WarningTests {
        
        @Test
        @DisplayName("Should warn for low exit Mach")
        void shouldWarnForLowExitMach() {
            NozzleDesignParameters lowMach = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(1.3)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.AIR)
                    .wallAngleInitialDegrees(30)
                    .lengthFraction(0.8)
                    .build();
            
            NASASP8120Validator.ValidationResult result = validator.validate(lowMach);
            
            assertThat(result.warnings()).isNotEmpty();
        }
        
        @Test
        @DisplayName("Should warn for high wall angle")
        void shouldWarnForHighWallAngle() {
            NozzleDesignParameters highAngle = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(3.0)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.AIR)
                    .wallAngleInitialDegrees(40)
                    .lengthFraction(0.8)
                    .build();
            
            NASASP8120Validator.ValidationResult result = validator.validate(highAngle);
            
            assertThat(result.warnings()).anyMatch(w -> w.contains("wall angle"));
        }
    }
    
    @Nested
    @DisplayName("Result Format Tests")
    class ResultFormatTests {

        @Test
        @DisplayName("Result should have meaningful toString")
        void resultShouldHaveMeaningfulToString() {
            NASASP8120Validator.ValidationResult result = validator.validate(params);

            String str = result.toString();
            assertThat(str).contains("Validation Result");
            assertThat(str).contains("Metrics");
        }
    }

    @Nested
    @DisplayName("ISP Error Branch Tests")
    class IspErrorBranchTests {

        @Test
        @DisplayName("ispError ≤ 2% (L69 FALSE) — perfectly-expanded design skips Isp warning")
        void ispErrorWithinToleranceSkipsWarning() {
            // Set pa = pe for AIR M=2 → pressure thrust = 0 → Isp_params ≈ Isp_validator → ispError ≈ 0
            double gamma = GasProperties.AIR.gamma();
            double M = 2.0;
            double pc = 7e6;
            double pe = pc * Math.pow(1.0 + (gamma - 1.0) / 2.0 * M * M, -gamma / (gamma - 1.0));

            NozzleDesignParameters perfExp = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(M)
                    .chamberPressure(pc)
                    .chamberTemperature(3500)
                    .ambientPressure(pe)
                    .gasProperties(GasProperties.AIR)
                    .wallAngleInitialDegrees(30)
                    .lengthFraction(0.8)
                    .build();

            NASASP8120Validator.ValidationResult result = validator.validate(perfExp);

            double ispError = result.metrics().get("isp_error_percent");
            assertThat(ispError).isLessThan(2.0);
            assertThat(result.warnings()).noneMatch(w -> w.contains("Specific impulse"));
        }
    }

    @Nested
    @DisplayName("High Mach Design Tests")
    class HighMachDesignTests {

        @Test
        @DisplayName("exitMach > 6.0 (L77 TRUE) — warns about real-gas corrections")
        void highMachWarnsAboutRealGas() {
            NozzleDesignParameters highMach = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(7.0)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.AIR)
                    .wallAngleInitialDegrees(30)
                    .lengthFraction(0.8)
                    .build();

            NASASP8120Validator.ValidationResult result = validator.validate(highMach);

            assertThat(result.warnings()).anyMatch(w -> w.contains("real-gas"));
        }

        @Test
        @DisplayName("areaRatio > 400 (L80 TRUE) — warns about viscous losses (AIR M=10 → AR≈536)")
        void veryHighMachWarnsAboutViscousLosses() {
            NozzleDesignParameters veryHighMach = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(10.0)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.AIR)
                    .wallAngleInitialDegrees(30)
                    .lengthFraction(0.8)
                    .build();

            NASASP8120Validator.ValidationResult result = validator.validate(veryHighMach);

            assertThat(result.warnings()).anyMatch(w -> w.contains("viscous losses"));
        }
    }

    @Nested
    @DisplayName("Ungenerated Net Tests")
    class UngeneratedNetTests {

        @Test
        @DisplayName("Ungenerated net (L117 TRUE) adds error; empty wallPoints (L123 FALSE) skips exit-Mach block")
        void ungeneratedNetFailsValidationAndSkipsExitMachBlock() {
            // CharacteristicNet without .generate() → empty netPoints → validate() returns false
            CharacteristicNet net = new CharacteristicNet(params);

            NASASP8120Validator.ValidationResult result = validator.validate(net);

            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("Characteristic net failed"));
            assertThat(result.metrics()).doesNotContainKey("exit_mach_error_percent");
        }
    }

    @Nested
    @DisplayName("Divergence Efficiency Interpolation Tests")
    class DivergenceEfficiencyTests {

        @Test
        @DisplayName("lengthFraction = 0.6 (L241 TRUE) — clamps to lower-bound efficiency 0.92")
        void lengthFractionAtLowerBoundReturnsLowerEfficiency() {
            NozzleDesignParameters lowFrac = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(3.0)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.AIR)
                    .wallAngleInitialDegrees(30)
                    .lengthFraction(0.6)
                    .build();

            NASASP8120Validator.ValidationResult result = validator.validate(lowFrac);

            assertThat(result.metrics().get("estimated_divergence_efficiency"))
                    .isCloseTo(0.92, within(1e-9));
        }

        @Test
        @DisplayName("lengthFraction = 1.0 (L242 TRUE) — clamps to upper-bound efficiency 0.99")
        void lengthFractionAtUpperBoundReturnsUpperEfficiency() {
            NozzleDesignParameters fullFrac = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(3.0)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.AIR)
                    .wallAngleInitialDegrees(30)
                    .lengthFraction(1.0)
                    .build();

            NASASP8120Validator.ValidationResult result = validator.validate(fullFrac);

            assertThat(result.metrics().get("estimated_divergence_efficiency"))
                    .isCloseTo(0.99, within(1e-9));
        }
    }

    @Nested
    @DisplayName("Area Ratio and Thrust Coefficient Dead Branch Tests")
    class DeadCodeBranchTests {

        /** Subclass that replaces the two package-private calculation methods with fixed returns. */
        private class TestableValidator extends NASASP8120Validator {
            private final double arOverride;
            private final double cfOverride;

            TestableValidator(double arOverride, double cfOverride) {
                this.arOverride = arOverride;
                this.cfOverride = cfOverride;
            }

            @Override
            double calculateAreaRatio(double mach, double gamma) {
                return arOverride >= 0 ? arOverride : super.calculateAreaRatio(mach, gamma);
            }

            @Override
            double calculateIdealThrustCoefficient(double mach, double gamma, double pressureRatio) {
                return cfOverride >= 0 ? cfOverride : super.calculateIdealThrustCoefficient(mach, gamma, pressureRatio);
            }
        }

        @Test
        @DisplayName("arError > 1% (L62 TRUE) and > 5% (L65 TRUE) — warning and hard error added")
        void areaRatioErrorAbove5PercentAddsErrorAndWarning() {
            // Return near-zero AR so arError >> 5% compared to the real isentropic value
            NASASP8120Validator.ValidationResult result =
                    new TestableValidator(0.001, -1).validate(params);

            assertThat(result.warnings()).anyMatch(w -> w.contains("Area ratio deviation"));
            assertThat(result.errors()).anyMatch(e -> e.contains("Area ratio error"));
            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("cfError > 1% (L77 TRUE) — thrust coefficient warning added")
        void thrustCoefficientErrorAbove1PercentAddsWarning() {
            // Return near-zero Cf so cfError >> 1% compared to the design value
            NASASP8120Validator.ValidationResult result =
                    new TestableValidator(-1, 0.001).validate(params);

            assertThat(result.warnings()).anyMatch(w -> w.contains("Thrust coefficient deviation"));
        }
    }

    @Nested
    @DisplayName("Net Exit Condition Branch Tests")
    class NetBranchTests {

        /** CharacteristicNet subclass that injects custom wall points, AR, and validity. */
        private class TestableNet extends CharacteristicNet {
            private final List<CharacteristicPoint> injectedWallPoints;
            private final double injectedAR;

            TestableNet(NozzleDesignParameters p,
                        List<CharacteristicPoint> wallPoints,
                        double ar) {
                super(p);
                this.injectedWallPoints = wallPoints;
                this.injectedAR = ar;
            }

            @Override
            public List<CharacteristicPoint> getWallPoints() {
                return injectedWallPoints;
            }

            @Override
            public double calculateExitAreaRatio() {
                return injectedAR;
            }

            @Override
            public boolean validate() {
                return true;
            }
        }

        @Test
        @DisplayName("machError > 2% (L148 TRUE) and > 5% (L151 TRUE) — Mach warning and error added")
        void machErrorAbove5PercentAddsWarningAndError() {
            double designMach = params.exitMach();
            // 10% higher → machError ≈ 10%
            CharacteristicPoint exitPt = CharacteristicPoint.of(1.0, 0.05, designMach * 1.10, 0.0, 0, 0);
            TestableNet net = new TestableNet(params, List.of(exitPt), params.exitAreaRatio());

            NASASP8120Validator.ValidationResult result = validator.validate(net);

            assertThat(result.warnings()).anyMatch(w -> w.contains("Exit Mach deviation"));
            assertThat(result.errors()).anyMatch(e -> e.contains("Exit Mach error"));
        }

        @Test
        @DisplayName("exitAngleDeg > 10° (L159 TRUE) — exit flow angle warning added")
        void exitFlowAngleAbove10DegreesAddsWarning() {
            double designMach = params.exitMach();
            double theta = Math.toRadians(15.0); // 15° > 10°
            CharacteristicPoint exitPt = CharacteristicPoint.of(1.0, 0.05, designMach, theta, 0, 0);
            TestableNet net = new TestableNet(params, List.of(exitPt), params.exitAreaRatio());

            NASASP8120Validator.ValidationResult result = validator.validate(net);

            assertThat(result.warnings()).anyMatch(w -> w.contains("Exit flow angle"));
        }

        @Test
        @DisplayName("arDiff > 3% (L170 TRUE) — computed vs design area ratio warning added")
        void computedAreaRatioDifferenceAbove3PercentAddsWarning() {
            double designMach = params.exitMach();
            CharacteristicPoint exitPt = CharacteristicPoint.of(1.0, 0.05, designMach, 0.0, 0, 0);
            double wrongAR = params.exitAreaRatio() * 1.10; // 10% off → arDiff > 3%
            TestableNet net = new TestableNet(params, List.of(exitPt), wrongAR);

            NASASP8120Validator.ValidationResult result = validator.validate(net);

            assertThat(result.warnings()).anyMatch(w -> w.contains("Computed area ratio differs"));
        }
    }

    @Nested
    @DisplayName("ValidationResult toString Branch Tests")
    class ValidationResultToStringTests {

        @Test
        @DisplayName("toString with errors, no warnings, no metrics — errors block TRUE, warnings FALSE, metrics FALSE")
        void toStringWithErrorsAndNoWarningsNoMetrics() {
            NASASP8120Validator.ValidationResult result = new NASASP8120Validator.ValidationResult(
                    false, List.of("Critical error"), List.of(), Map.of());

            String str = result.toString();
            assertThat(str).contains("FAILED");
            assertThat(str).contains("Critical error");
            assertThat(str).doesNotContain("Warnings:");
            assertThat(str).doesNotContain("Metrics:");
        }

        @Test
        @DisplayName("toString with warnings and metrics, no errors — errors FALSE, warnings TRUE, metrics TRUE")
        void toStringWithWarningsAndMetrics() {
            NASASP8120Validator.ValidationResult result = new NASASP8120Validator.ValidationResult(
                    true, List.of(), List.of("Advisory warning"), Map.of("key", 1.2345));

            String str = result.toString();
            assertThat(str).contains("PASSED");
            assertThat(str).doesNotContain("Errors:");
            assertThat(str).contains("Advisory warning");
            assertThat(str).contains("key");
        }
    }
}
