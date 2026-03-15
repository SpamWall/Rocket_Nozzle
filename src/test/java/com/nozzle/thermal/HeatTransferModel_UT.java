package com.nozzle.thermal;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.geometry.Point2D;
import com.nozzle.moc.CharacteristicPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
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

    @Nested
    @DisplayName("Auto-generate Contour Branch Tests")
    class AutoGenerateContourTests {

        @Test
        @DisplayName("calculate() auto-generates contour when none was pre-built (line 108 TRUE)")
        void calculateAutoGeneratesContourWhenEmpty() {
            // Pass a contour that has NOT been generate()'d → isEmpty() TRUE
            NozzleContour emptyContour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
            HeatTransferModel model = new HeatTransferModel(params, emptyContour)
                    .calculate(List.of());

            assertThat(model.getWallThermalProfile()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Flow Points Branch Tests")
    class FlowPointsBranchTests {

        private CharacteristicPoint flowPoint(double x, double y, double mach, double temp) {
            return new CharacteristicPoint(x, y, mach, 0.0, 0.5, 0.3,
                    50000, temp, 0.5, mach * 350,
                    0, 0, CharacteristicPoint.PointType.INTERIOR);
        }

        @Test
        @DisplayName("calculate(null) covers null flowPoints branch (line 167 a=true)")
        void calculateWithNullFlowPointsCoversNullBranch() {
            // flowPoints == null → short-circuits to null, nearestFlow falls back to defaults
            HeatTransferModel model = new HeatTransferModel(params, contour)
                    .calculate(null);

            assertThat(model.getWallThermalProfile()).isNotEmpty();
        }

        @Test
        @DisplayName("calculate with non-empty flowPoints covers nearest-flow branches (lines 167,174,178,123,125)")
        void calculateWithNonEmptyFlowPointsCoversNearestFlowBranches() {
            // flowPoints != null and non-empty → all findNearestFlowPoint branches hit
            // nearestFlow != null → TRUE branches on lines 123+125 covered
            List<CharacteristicPoint> flowPoints = List.of(
                    flowPoint(0.01, 0.06, 1.5, 2500),
                    flowPoint(0.03, 0.08, 2.0, 2000),
                    flowPoint(0.05, 0.10, 2.5, 1700)
            );

            HeatTransferModel model = new HeatTransferModel(params, contour)
                    .calculate(flowPoints);

            List<HeatTransferModel.WallThermalPoint> profile = model.getWallThermalProfile();
            assertThat(profile).isNotEmpty();
            // Recovery temp derived from real flow point (non-fallback gas temp)
            assertThat(profile.getFirst().recoveryTemperature()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Coolant Channel Branch Tests")
    class CoolantChannelBranchTests {

        @Test
        @DisplayName("Non-empty channel profile uses channel h_coolant (line 131 a=true, b=true)")
        void calculateWithCalculatedChannelUsesChannelCoeff() {
            // CoolantChannel.calculate() fills channelProfile → !isEmpty() TRUE
            CoolantChannel channel = new CoolantChannel(contour)
                    .setCoolant(CoolantChannel.CoolantProperties.RP1, 2.0, 300.0, 8e6)
                    .calculate();

            HeatTransferModel model = new HeatTransferModel(params, contour)
                    .setCoolantChannel(channel)
                    .calculate(List.of());

            assertThat(model.getWallThermalProfile()).isNotEmpty();
        }

        @Test
        @DisplayName("Empty channel profile falls back to fixed h_coolant (line 131 a=true, b=false)")
        void calculateWithUncalculatedChannelUsesDefaultCoeff() {
            // CoolantChannel not calculate()'d → channelProfile empty → !isEmpty() FALSE
            CoolantChannel channel = new CoolantChannel(contour);  // no calculate() call

            HeatTransferModel model = new HeatTransferModel(params, contour)
                    .setCoolantChannel(channel)
                    .calculate(List.of());

            assertThat(model.getWallThermalProfile()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Local Radius of Curvature Branch Tests")
    class LocalRadiusOfCurvatureBranchTests {

        @Test
        @DisplayName("Fewer than 3 contour points returns throatRadius early (line 255 TRUE)")
        void fewContourPointsReturnThroatRadiusEarly() {
            // generate(2) → 2 points → pts.size() < 3 → early return in localRadiusOfCurvature
            NozzleContour tinyContour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
            tinyContour.generate(2);

            HeatTransferModel model = new HeatTransferModel(params, tinyContour)
                    .calculate(List.of());

            // With only 2 contour points the model still produces 2 thermal points
            assertThat(model.getWallThermalProfile()).hasSize(2);
        }

        @Test
        @DisplayName("Straight conical wall gives d2ydx2 ≈ 0, triggering cap branch (line 286 TRUE)")
        void conicalContourTriggersNearStraightWallCap() {
            // Conical wall: y = const + x*tan(θ) → perfectly straight → d2ydx2 = 0 → line 286 TRUE
            NozzleContour conical = new NozzleContour(NozzleContour.ContourType.CONICAL, params);
            conical.generate(50);

            HeatTransferModel model = new HeatTransferModel(params, conical)
                    .calculate(List.of());

            assertThat(model.getMaxWallTemperature()).isGreaterThan(300);
        }

        @Test
        @DisplayName("First two points share x triggers h1 < 1e-12 branch (line 277 a=TRUE)")
        void identicalFirstXCoordinatesTriggersH1Branch() throws Exception {
            // pts[0].x == pts[1].x → h1 = 0 < 1e-12 → short-circuit TRUE (a=true branch)
            NozzleContour injected = new NozzleContour(NozzleContour.ContourType.CONICAL, params);
            Field cpField = NozzleContour.class.getDeclaredField("contourPoints");
            cpField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Point2D> pts = (List<Point2D>) cpField.get(injected);
            pts.add(new Point2D(0.010, 0.060));
            pts.add(new Point2D(0.010, 0.061));  // same x → h1 = 0 < 1e-12
            pts.add(new Point2D(0.020, 0.070));

            HeatTransferModel model = new HeatTransferModel(params, injected)
                    .calculate(List.of());

            assertThat(model.getWallThermalProfile()).hasSize(3);
        }

        @Test
        @DisplayName("Last two points share x triggers h2 < 1e-12 branch (line 277 a=false,b=TRUE)")
        void identicalLastXCoordinatesTriggersH2Branch() throws Exception {
            // pts[1].x == pts[2].x → h1 normal, h2 = 0 < 1e-12 → TRUE via second operand (b=true branch)
            NozzleContour injected = new NozzleContour(NozzleContour.ContourType.CONICAL, params);
            Field cpField = NozzleContour.class.getDeclaredField("contourPoints");
            cpField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Point2D> pts = (List<Point2D>) cpField.get(injected);
            pts.add(new Point2D(0.010, 0.060));
            pts.add(new Point2D(0.020, 0.070));
            pts.add(new Point2D(0.020, 0.071));  // same x as pts[1] → h2 = 0 < 1e-12

            HeatTransferModel model = new HeatTransferModel(params, injected)
                    .calculate(List.of());

            assertThat(model.getWallThermalProfile()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("getTotalHeatLoad Loop Branch Tests")
    class TotalHeatLoadLoopBranchTests {

        @Test
        @DisplayName("Smaller contour after calculate exits loop via second condition (line 370 a=true,b=false)")
        void totalHeatLoadExitsViaContourSizeCondition() {
            // After calculate with 50-pt contour (50 thermal points), regenerate to 5 points.
            // At i=5: i < wallThermalProfile.size()=50 is TRUE, i < contourPoints.size()=5 is FALSE
            HeatTransferModel model = new HeatTransferModel(params, contour)
                    .calculate(List.of());

            contour.generate(5);  // shrink contour — thermal profile still has 50 entries

            double heat = model.getTotalHeatLoad();
            assertThat(heat).isNotNaN();
        }
    }
}
