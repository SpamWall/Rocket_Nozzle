package com.nozzle.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("GasProperties Tests")
class GasPropertiesTest {
    
    private GasProperties air;
    
    @BeforeEach
    void setUp() {
        air = GasProperties.AIR;
    }
    
    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {
        
        @Test
        @DisplayName("Should create valid gas properties")
        void shouldCreateValidGasProperties() {
            GasProperties gas = new GasProperties(1.4, 28.97, 287.05, 1.716e-5, 273.15, 110.4);
            
            assertThat(gas.gamma()).isEqualTo(1.4);
            assertThat(gas.molecularWeight()).isEqualTo(28.97);
            assertThat(gas.gasConstant()).isEqualTo(287.05);
        }
        
        @Test
        @DisplayName("Should reject gamma <= 1")
        void shouldRejectInvalidGamma() {
            assertThatThrownBy(() -> new GasProperties(1.0, 28.97, 287.05, 1.716e-5, 273.15, 110.4))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Gamma must be greater than 1.0");
        }
        
        @Test
        @DisplayName("Should reject negative molecular weight")
        void shouldRejectNegativeMolecularWeight() {
            assertThatThrownBy(() -> new GasProperties(1.4, -28.97, 287.05, 1.716e-5, 273.15, 110.4))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        
        @Test
        @DisplayName("Should create from gamma and MW")
        void shouldCreateFromGammaAndMW() {
            GasProperties gas = GasProperties.fromGammaAndMW(1.3, 24.0);
            
            assertThat(gas.gamma()).isEqualTo(1.3);
            assertThat(gas.molecularWeight()).isEqualTo(24.0);
            assertThat(gas.gasConstant()).isCloseTo(8314.46 / 24.0, within(0.01));
        }
    }
    
    @Nested
    @DisplayName("Predefined Gas Tests")
    class PredefinedGasTests {
        
        @Test
        @DisplayName("AIR should have correct properties")
        void airShouldHaveCorrectProperties() {
            assertThat(GasProperties.AIR.gamma()).isEqualTo(1.4);
            assertThat(GasProperties.AIR.molecularWeight()).isCloseTo(28.97, within(0.01));
            assertThat(GasProperties.AIR.gasConstant()).isCloseTo(287.05, within(0.1));
        }
        
        @Test
        @DisplayName("HYDROGEN should have correct properties")
        void hydrogenShouldHaveCorrectProperties() {
            assertThat(GasProperties.HYDROGEN.gamma()).isCloseTo(1.41, within(0.01));
            assertThat(GasProperties.HYDROGEN.molecularWeight()).isCloseTo(2.016, within(0.001));
        }
        
        @Test
        @DisplayName("LOX_RP1_PRODUCTS should have correct properties")
        void loxRp1ShouldHaveCorrectProperties() {
            assertThat(GasProperties.LOX_RP1_PRODUCTS.gamma()).isCloseTo(1.24, within(0.01));
        }
    }
    
    @Nested
    @DisplayName("Thermodynamic Calculations")
    class ThermodynamicCalculationsTests {
        
        @Test
        @DisplayName("Should calculate specific heats correctly")
        void shouldCalculateSpecificHeats() {
            double cp = air.specificHeatCp();
            double cv = air.specificHeatCv();
            
            assertThat(cp).isCloseTo(1004.5, within(1.0));
            assertThat(cv).isCloseTo(717.5, within(1.0));
            assertThat(cp / cv).isCloseTo(1.4, within(0.001));
        }
        
        @Test
        @DisplayName("Should calculate speed of sound")
        void shouldCalculateSpeedOfSound() {
            double a = air.speedOfSound(288.15);
            assertThat(a).isCloseTo(340.3, within(0.5));
        }
        
        @ParameterizedTest
        @CsvSource({
                "300, 347.2",
                "288.15, 340.3",
                "273.15, 331.3"
        })
        @DisplayName("Speed of sound at various temperatures")
        void speedOfSoundAtVariousTemps(double temp, double expectedA) {
            double a = air.speedOfSound(temp);
            assertThat(a).isCloseTo(expectedA, within(1.0));
        }
        
        @Test
        @DisplayName("Should calculate viscosity using Sutherland's law")
        void shouldCalculateViscosity() {
            double mu = air.calculateViscosity(300);
            assertThat(mu).isCloseTo(1.85e-5, within(1e-6));
        }
    }
    
    @Nested
    @DisplayName("Isentropic Relations")
    class IsentropicRelationsTests {
        
        @Test
        @DisplayName("Should calculate temperature ratio at Mach 2")
        void shouldCalculateTemperatureRatio() {
            double ratio = air.isentropicTemperatureRatio(2.0);
            assertThat(ratio).isCloseTo(0.5556, within(0.001));
        }
        
        @Test
        @DisplayName("Should calculate pressure ratio at Mach 2")
        void shouldCalculatePressureRatio() {
            double ratio = air.isentropicPressureRatio(2.0);
            assertThat(ratio).isCloseTo(0.1278, within(0.001));
        }
        
        @Test
        @DisplayName("Should calculate density ratio at Mach 2")
        void shouldCalculateDensityRatio() {
            double ratio = air.isentropicDensityRatio(2.0);
            assertThat(ratio).isCloseTo(0.23, within(0.01));
        }
        
        @ParameterizedTest
        @ValueSource(doubles = {1.0, 1.5, 2.0, 3.0, 4.0})
        @DisplayName("Isentropic ratios should be consistent")
        void isentropicRatiosShouldBeConsistent(double mach) {
            double T_ratio = air.isentropicTemperatureRatio(mach);
            double P_ratio = air.isentropicPressureRatio(mach);
            double rho_ratio = air.isentropicDensityRatio(mach);
            
            // P = rho * R * T, so P_ratio = rho_ratio * T_ratio
            assertThat(P_ratio).isCloseTo(rho_ratio * T_ratio, within(0.001));
        }
    }
    
    @Nested
    @DisplayName("Prandtl-Meyer Function")
    class PrandtlMeyerTests {
        
        @Test
        @DisplayName("Prandtl-Meyer should be zero at Mach 1")
        void prandtlMeyerShouldBeZeroAtMach1() {
            double nu = air.prandtlMeyerFunction(1.0);
            assertThat(nu).isCloseTo(0.0, within(1e-6));
        }
        
        @Test
        @DisplayName("Prandtl-Meyer at Mach 2 for gamma=1.4")
        void prandtlMeyerAtMach2() {
            double nu = air.prandtlMeyerFunction(2.0);
            assertThat(Math.toDegrees(nu)).isCloseTo(26.38, within(0.1));
        }
        
        @ParameterizedTest
        @ValueSource(doubles = {1.5, 2.0, 2.5, 3.0, 4.0, 5.0})
        @DisplayName("Mach from Prandtl-Meyer should be inverse")
        void machFromPrandtlMeyerShouldBeInverse(double mach) {
            double nu = air.prandtlMeyerFunction(mach);
            double recoveredMach = air.machFromPrandtlMeyer(nu);
            assertThat(recoveredMach).isCloseTo(mach, within(0.001));
        }
    }
    
    @Nested
    @DisplayName("Area Ratio")
    class AreaRatioTests {
        
        @Test
        @DisplayName("Area ratio should be 1 at Mach 1")
        void areaRatioShouldBeOneAtMach1() {
            double ar = air.areaRatio(1.0);
            assertThat(ar).isCloseTo(1.0, within(1e-6));
        }
        
        @Test
        @DisplayName("Area ratio at Mach 2 for gamma=1.4")
        void areaRatioAtMach2() {
            double ar = air.areaRatio(2.0);
            assertThat(ar).isCloseTo(1.6875, within(0.001));
        }
        
        @Test
        @DisplayName("Area ratio at Mach 3 for gamma=1.4")
        void areaRatioAtMach3() {
            double ar = air.areaRatio(3.0);
            assertThat(ar).isCloseTo(4.2346, within(0.001));
        }
        
        @ParameterizedTest
        @ValueSource(doubles = {1.5, 2.0, 3.0, 4.0, 5.0})
        @DisplayName("Mach from area ratio should be inverse")
        void machFromAreaRatioShouldBeInverse(double mach) {
            double ar = air.areaRatio(mach);
            double recoveredMach = air.machFromAreaRatio(ar);
            assertThat(recoveredMach).isCloseTo(mach, within(0.001));
        }
    }
    
    @Nested
    @DisplayName("Mach Angle")
    class MachAngleTests {
        
        @Test
        @DisplayName("Mach angle at Mach 1 should be 90 degrees")
        void machAngleAtMach1() {
            double mu = air.machAngle(1.0);
            assertThat(Math.toDegrees(mu)).isCloseTo(90.0, within(0.001));
        }
        
        @Test
        @DisplayName("Mach angle at Mach 2 should be 30 degrees")
        void machAngleAtMach2() {
            double mu = air.machAngle(2.0);
            assertThat(Math.toDegrees(mu)).isCloseTo(30.0, within(0.001));
        }
    }
    
    @Test
    @DisplayName("Should create copy with modified gamma")
    void shouldCreateCopyWithModifiedGamma() {
        GasProperties modified = air.withGamma(1.3);
        
        assertThat(modified.gamma()).isEqualTo(1.3);
        assertThat(modified.molecularWeight()).isEqualTo(air.molecularWeight());
        assertThat(modified.gasConstant()).isEqualTo(air.gasConstant());
    }
}
