package com.nozzle.validation;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.moc.CharacteristicNet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("NASASP8120Validator Tests")
class NASASP8120ValidatorTest {
    
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
        
        validator = new NASASP8120Validator(params.gasProperties().gamma());
    }
    
    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {
        
        @Test
        @DisplayName("Should create validator with gamma")
        void shouldCreateValidatorWithGamma() {
            NASASP8120Validator v = new NASASP8120Validator(1.2);
            assertThat(v).isNotNull();
        }
        
        @Test
        @DisplayName("Should create validator with default gamma")
        void shouldCreateValidatorWithDefaultGamma() {
            NASASP8120Validator v = new NASASP8120Validator();
            assertThat(v).isNotNull();
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
}
