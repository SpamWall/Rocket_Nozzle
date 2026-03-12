package com.nozzle.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("NozzleDesignParameters Tests")
class NozzleDesignParameters_UT {
    
    private NozzleDesignParameters params;
    
    @BeforeEach
    void setUp() {
        params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.AIR)
                .numberOfCharLines(30)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();
    }
    
    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {
        
        @Test
        @DisplayName("Should create valid parameters")
        void shouldCreateValidParameters() {
            assertThat(params.throatRadius()).isEqualTo(0.05);
            assertThat(params.exitMach()).isEqualTo(3.0);
            assertThat(params.chamberPressure()).isEqualTo(7e6);
            assertThat(params.chamberTemperature()).isEqualTo(3500);
        }
        
        @Test
        @DisplayName("Should reject exit Mach < 1")
        void shouldRejectSubsonicExitMach() {
            assertThatThrownBy(() -> NozzleDesignParameters.builder()
                    .exitMach(0.8)
                    .throatRadius(0.05)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.AIR)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Exit Mach");
        }
        
        @Test
        @DisplayName("Should reject negative throat radius")
        void shouldRejectNegativeThroatRadius() {
            assertThatThrownBy(() -> NozzleDesignParameters.builder()
                    .throatRadius(-0.05)
                    .exitMach(3.0)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.AIR)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class);
        }
        
        @Test
        @DisplayName("Should reject chamber pressure below ambient")
        void shouldRejectLowChamberPressure() {
            assertThatThrownBy(() -> NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(3.0)
                    .chamberPressure(50000)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.AIR)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Chamber pressure must exceed ambient");
        }
        
        @Test
        @DisplayName("Should reject invalid wall angle")
        void shouldRejectInvalidWallAngle() {
            assertThatThrownBy(() -> NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(3.0)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.AIR)
                    .wallAngleInitialDegrees(60) // > 45 degrees
                    .build())
                    .isInstanceOf(IllegalArgumentException.class);
        }
        
        @Test
        @DisplayName("Should reject few characteristic lines")
        void shouldRejectFewCharLines() {
            assertThatThrownBy(() -> NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(3.0)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.AIR)
                    .numberOfCharLines(3)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("At least 5 characteristic lines required");
        }
    }

    @Nested
    @DisplayName("Calculated Properties")
    class CalculatedPropertiesTests {
        
        @Test
        @DisplayName("Should calculate exit area ratio")
        void shouldCalculateExitAreaRatio() {
            double ar = params.exitAreaRatio();
            assertThat(ar).isGreaterThan(1.0);
            assertThat(ar).isCloseTo(params.gasProperties().areaRatio(3.0), within(0.001));
        }
        
        @Test
        @DisplayName("Should calculate exit radius")
        void shouldCalculateExitRadius() {
            double re = params.exitRadius();
            double rt = params.throatRadius();
            double ar = params.exitAreaRatio();
            
            assertThat(re).isGreaterThan(rt);
            assertThat(re * re / (rt * rt)).isCloseTo(ar, within(0.001));
        }
        
        @Test
        @DisplayName("Should calculate throat area")
        void shouldCalculateThroatArea() {
            double at = params.throatArea();
            double expected = Math.PI * 0.05 * 0.05;
            assertThat(at).isCloseTo(expected, within(1e-6));
        }
        
        @Test
        @DisplayName("Should calculate exit area")
        void shouldCalculateExitArea() {
            double ae = params.exitArea();
            double at = params.throatArea();
            double ar = params.exitAreaRatio();
            assertThat(ae).isCloseTo(at * ar, within(1e-6));
        }
        
        @Test
        @DisplayName("Should calculate pressure ratio")
        void shouldCalculatePressureRatio() {
            double pr = params.pressureRatio();
            assertThat(pr).isCloseTo(7e6 / 101325, within(0.1));
        }
        
        @Test
        @DisplayName("Should calculate ideal exit pressure")
        void shouldCalculateIdealExitPressure() {
            double pe = params.idealExitPressure();
            GasProperties gas = params.gasProperties();
            double expected = params.chamberPressure() * gas.isentropicPressureRatio(3.0);
            assertThat(pe).isCloseTo(expected, within(1.0));
        }
        
        @Test
        @DisplayName("Should calculate exit temperature")
        void shouldCalculateExitTemperature() {
            double te = params.exitTemperature();
            assertThat(te).isLessThan(params.chamberTemperature());
            assertThat(te).isGreaterThan(0);
        }
        
        @Test
        @DisplayName("Should calculate exit velocity")
        void shouldCalculateExitVelocity() {
            double ve = params.exitVelocity();
            assertThat(ve).isGreaterThan(0);
            
            // Should be supersonic at exit
            double ae = params.gasProperties().speedOfSound(params.exitTemperature());
            assertThat(ve / ae).isCloseTo(3.0, within(0.01));
        }
        
        @Test
        @DisplayName("Should calculate characteristic velocity")
        void shouldCalculateCharacteristicVelocity() {
            double cStar = params.characteristicVelocity();
            assertThat(cStar).isGreaterThan(0);
            assertThat(cStar).isLessThan(5000); // Reasonable range
        }
        
        @Test
        @DisplayName("Should calculate ideal thrust coefficient")
        void shouldCalculateIdealThrustCoefficient() {
            double cf = params.idealThrustCoefficient();
            assertThat(cf).isGreaterThan(1.0);
            assertThat(cf).isLessThan(2.5); // Reasonable range for supersonic
        }
        
        @Test
        @DisplayName("Should calculate ideal specific impulse")
        void shouldCalculateIdealSpecificImpulse() {
            double isp = params.idealSpecificImpulse();
            assertThat(isp).isGreaterThan(0);
            assertThat(isp).isLessThan(500); // Air Isp should be moderate
        }
        
        @Test
        @DisplayName("Should calculate max Prandtl-Meyer angle")
        void shouldCalculateMaxPrandtlMeyerAngle() {
            double nuMax = params.maxPrandtlMeyerAngle();
            assertThat(Math.toDegrees(nuMax)).isCloseTo(130.45, within(1.0));
        }
    }
    
    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {
        
        @Test
        @DisplayName("Builder should use defaults")
        void builderShouldUseDefaults() {
            NozzleDesignParameters defaultParams = NozzleDesignParameters.builder()
                    .exitMach(2.0)
                    .build();
            
            assertThat(defaultParams.numberOfCharLines()).isEqualTo(NozzleDesignParameters.DEFAULT_CHAR_LINES);
            assertThat(defaultParams.axisymmetric()).isTrue();
        }
        
        @Test
        @DisplayName("Builder should allow planar configuration")
        void builderShouldAllowPlanar() {
            NozzleDesignParameters planarParams = NozzleDesignParameters.builder()
                    .exitMach(2.0)
                    .planar()
                    .build();
            
            assertThat(planarParams.axisymmetric()).isFalse();
        }
        
        @Test
        @DisplayName("Builder should accept wall angle in degrees")
        void builderShouldAcceptWallAngleDegrees() {
            NozzleDesignParameters p = NozzleDesignParameters.builder()
                    .exitMach(2.0)
                    .wallAngleInitialDegrees(25)
                    .build();
            
            assertThat(Math.toDegrees(p.wallAngleInitial())).isCloseTo(25, within(0.001));
        }
    }
    
    @Nested
    @DisplayName("Planar vs Axisymmetric")
    class PlanarVsAxisymmetricTests {
        
        @Test
        @DisplayName("Axisymmetric should use circular throat area")
        void axisymmetricShouldUseCircularArea() {
            NozzleDesignParameters axiParams = NozzleDesignParameters.builder()
                    .throatRadius(0.1)
                    .exitMach(2.0)
                    .axisymmetric(true)
                    .build();
            
            double at = axiParams.throatArea();
            assertThat(at).isCloseTo(Math.PI * 0.1 * 0.1, within(1e-6));
        }
        
        @Test
        @DisplayName("Planar should use 2D throat area")
        void planarShouldUse2DArea() {
            NozzleDesignParameters planarParams = NozzleDesignParameters.builder()
                    .throatRadius(0.1) // Half-height in 2D
                    .exitMach(2.0)
                    .axisymmetric(false)
                    .build();
            
            double at = planarParams.throatArea();
            assertThat(at).isCloseTo(2.0 * 0.1, within(1e-6));
        }
    }
}
