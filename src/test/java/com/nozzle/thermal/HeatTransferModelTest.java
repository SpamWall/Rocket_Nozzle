package com.nozzle.thermal;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.moc.CharacteristicNet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("HeatTransferModel Tests")
class HeatTransferModelTest {
    
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
