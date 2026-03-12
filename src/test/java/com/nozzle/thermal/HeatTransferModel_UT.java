package com.nozzle.thermal;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.geometry.NozzleContour;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("HeatTransferModel Tests")
class HeatTransferModel_UT {
    
    private NozzleDesignParameters params;
    private NozzleContour contour;
    
    @BeforeEach
    void setUp() {
        params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(2.5)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(10)
                .wallAngleInitialDegrees(25)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();
        
        contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        contour.generate(50);
    }
    
    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {
        
        @Test
        @DisplayName("Should create heat transfer model")
        void shouldCreateHeatTransferModel() {
            HeatTransferModel model = new HeatTransferModel(params, contour);
            assertThat(model).isNotNull();
        }
        
        @Test
        @DisplayName("Should set wall properties")
        void shouldSetWallProperties() {
            HeatTransferModel model = new HeatTransferModel(params, contour)
                    .setWallProperties(15.0, 0.002);
            assertThat(model).isNotNull();
        }
        
        @Test
        @DisplayName("Should set coolant properties")
        void shouldSetCoolantProperties() {
            HeatTransferModel model = new HeatTransferModel(params, contour)
                    .setCoolantProperties(250, 8000);
            assertThat(model).isNotNull();
        }
    }
    
    @Nested
    @DisplayName("Calculation Tests")
    class CalculationTests {
        
        @Test
        @DisplayName("Should calculate thermal profile")
        void shouldCalculateThermalProfile() {
            HeatTransferModel model = new HeatTransferModel(params, contour)
                    .calculate(List.of());
            
            List<HeatTransferModel.WallThermalPoint> profile = model.getWallThermalProfile();
            assertThat(profile).isNotEmpty();
        }
        
        @Test
        @DisplayName("Should calculate max wall temperature")
        void shouldCalculateMaxWallTemp() {
            HeatTransferModel model = new HeatTransferModel(params, contour)
                    .calculate(List.of());
            
            double maxTemp = model.getMaxWallTemperature();
            assertThat(maxTemp).isGreaterThan(300);
            assertThat(maxTemp).isLessThan(params.chamberTemperature());
        }
        
        @Test
        @DisplayName("Should calculate max heat flux")
        void shouldCalculateMaxHeatFlux() {
            HeatTransferModel model = new HeatTransferModel(params, contour)
                    .calculate(List.of());
            
            double maxFlux = model.getMaxHeatFlux();
            assertThat(maxFlux).isGreaterThan(0);
        }
        
        @Test
        @DisplayName("Should calculate total heat load")
        void shouldCalculateTotalHeatLoad() {
            HeatTransferModel model = new HeatTransferModel(params, contour)
                    .calculate(List.of());
            
            double heatLoad = model.getTotalHeatLoad();
            assertThat(heatLoad).isGreaterThan(0);
        }
    }
    
    @Nested
    @DisplayName("Bartz Reference Temperature Tests")
    class BartzReferenceTemperatureTests {

        @Test
        @DisplayName("Heat transfer coefficient should be positive at all points")
        void heatTransferCoeffShouldBePositiveEverywhere() {
            HeatTransferModel model = new HeatTransferModel(params, contour)
                    .calculate(List.of());

            for (HeatTransferModel.WallThermalPoint point : model.getWallThermalProfile()) {
                assertThat(point.heatTransferCoeff())
                        .as("h at x=%.4f", point.x())
                        .isGreaterThan(0.0);
            }
        }

        @Test
        @DisplayName("Heat transfer should peak near the throat, not at the exit")
        void heatTransferShouldPeakNearThroat() {
            HeatTransferModel model = new HeatTransferModel(params, contour)
                    .calculate(List.of());

            List<HeatTransferModel.WallThermalPoint> profile = model.getWallThermalProfile();
            double maxH = profile.stream()
                    .mapToDouble(HeatTransferModel.WallThermalPoint::heatTransferCoeff)
                    .max().orElseThrow();
            double exitH = profile.getLast().heatTransferCoeff();

            assertThat(maxH).isGreaterThan(exitH);
        }

        @Test
        @DisplayName("Heat transfer coefficient should vary along the nozzle")
        void heatTransferCoeffShouldVaryAlongNozzle() {
            HeatTransferModel model = new HeatTransferModel(params, contour)
                    .calculate(List.of());

            List<HeatTransferModel.WallThermalPoint> profile = model.getWallThermalProfile();
            double minH = profile.stream()
                    .mapToDouble(HeatTransferModel.WallThermalPoint::heatTransferCoeff)
                    .min().orElseThrow();
            double maxH = profile.stream()
                    .mapToDouble(HeatTransferModel.WallThermalPoint::heatTransferCoeff)
                    .max().orElseThrow();

            // Area ratio and curvature terms should produce meaningful variation
            assertThat(maxH).isGreaterThan(2.0 * minH);
        }

        @Test
        @DisplayName("Doubling chamber pressure should increase heat transfer coefficient (Bartz Pc^0.8 scaling)")
        void doublingChamberPressureShouldIncreaseHeatTransferCoeff() {
            NozzleDesignParameters highPParams = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(2.5)
                    .chamberPressure(14e6)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .numberOfCharLines(10)
                    .wallAngleInitialDegrees(25)
                    .lengthFraction(0.8)
                    .axisymmetric(true)
                    .build();
            NozzleContour highPContour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, highPParams);
            highPContour.generate(50);

            List<HeatTransferModel.WallThermalPoint> baseProfile =
                    new HeatTransferModel(params, contour).calculate(List.of()).getWallThermalProfile();
            List<HeatTransferModel.WallThermalPoint> highProfile =
                    new HeatTransferModel(highPParams, highPContour).calculate(List.of()).getWallThermalProfile();

            double baseMaxH = baseProfile.stream()
                    .mapToDouble(HeatTransferModel.WallThermalPoint::heatTransferCoeff).max().orElseThrow();
            double highMaxH = highProfile.stream()
                    .mapToDouble(HeatTransferModel.WallThermalPoint::heatTransferCoeff).max().orElseThrow();

            // Bartz scales as Pc^0.8: doubling Pc gives ~1.74x increase in h
            assertThat(highMaxH).isGreaterThan(baseMaxH);
            assertThat(highMaxH / baseMaxH).isCloseTo(Math.pow(2.0, 0.8), within(0.05));
        }

        @Test
        @DisplayName("Recovery temperature should exceed local static temperature")
        void recoveryTempShouldExceedLocalStaticTemp() {
            HeatTransferModel model = new HeatTransferModel(params, contour)
                    .calculate(List.of());

            // Recovery temperature = static temp * (1 + r*(γ-1)/2*M²) > static temp
            // The fallback static temp is 0.8 * Tc; recovery temp should be above that
            double fallbackStaticTemp = params.chamberTemperature() * 0.8;
            for (HeatTransferModel.WallThermalPoint point : model.getWallThermalProfile()) {
                assertThat(point.recoveryTemperature())
                        .as("T_aw at x=%.4f", point.x())
                        .isGreaterThan(fallbackStaticTemp);
            }
        }
    }

    @Nested
    @DisplayName("Thermal Point Tests")
    class ThermalPointTests {
        
        @Test
        @DisplayName("Thermal points should have valid data")
        void thermalPointsShouldHaveValidData() {
            HeatTransferModel model = new HeatTransferModel(params, contour)
                    .calculate(List.of());
            
            for (HeatTransferModel.WallThermalPoint point : model.getWallThermalProfile()) {
                assertThat(point.wallTemperature()).isGreaterThan(0);
                assertThat(point.recoveryTemperature()).isGreaterThan(point.wallTemperature());
            }
        }
    }
}
