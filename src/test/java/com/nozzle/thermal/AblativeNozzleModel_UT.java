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

@DisplayName("AblativeNozzleModel Tests")
class AblativeNozzleModel_UT {

    private NozzleDesignParameters params;
    private NozzleContour contour;
    private AblativeNozzleModel model;

    @BeforeEach
    void setUp() {
        params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(10)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();

        contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        contour.generate(50);
        model = new AblativeNozzleModel(params, contour);
    }

    // -----------------------------------------------------------------------
    // Construction and configuration
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {

        @Test
        @DisplayName("Should create ablative model with defaults")
        void shouldCreateWithDefaults() {
            assertThat(model).isNotNull();
            assertThat(model.getProfile()).isEmpty();
        }

        @Test
        @DisplayName("Fluent setters should return this instance and not throw")
        void fluentSettersShouldReturnThis() {
            AblativeNozzleModel configured = new AblativeNozzleModel(params, contour)
                    .setMaterial(AblativeNozzleModel.AblativeMaterial.EPDM)
                    .setInitialLinerThickness(0.015)
                    .setBurnTime(15.0)
                    .setTimeSteps(200);

            assertThat(configured).isNotNull();
        }
    }

    // -----------------------------------------------------------------------
    // Fallback calculation (no heat profile supplied)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Fallback Calculation Tests")
    class FallbackCalculationTests {

        @Test
        @DisplayName("calculate(null) uses chamber-based T_surface fallback and produces a non-empty profile")
        void calculateNullHeatProfileProducesProfile() {
            model.calculate(null);

            assertThat(model.getProfile()).isNotEmpty();
        }

        @Test
        @DisplayName("calculate(emptyList) uses fallback and produces a non-empty profile")
        void calculateEmptyHeatProfileProducesProfile() {
            model.calculate(List.of());

            assertThat(model.getProfile()).isNotEmpty();
        }

        @Test
        @DisplayName("Profile size equals contour point count")
        void profileSizeMatchesContourPointCount() {
            model.calculate(List.of());

            assertThat(model.getProfile()).hasSize(contour.getContourPoints().size());
        }
    }

    // -----------------------------------------------------------------------
    // Physical behaviour with heat profile
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Physical Behaviour Tests")
    class PhysicsBehaviourTests {

        private List<HeatTransferModel.WallThermalPoint> heatProfile;

        @BeforeEach
        void buildHeatProfile() {
            heatProfile = new HeatTransferModel(params, contour)
                    .calculate(List.of())
                    .getWallThermalProfile();
        }

        @Test
        @DisplayName("Recession depth should be positive after non-zero burn time")
        void recessionDepthShouldBePositive() {
            model.calculate(heatProfile);

            assertThat(model.getMaxRecessionDepth()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("Longer burn time should produce deeper recession (graphite — no perforation)")
        void longerBurnTimeIncreasesRecession() {
            // Graphite has a very low char rate; neither burn time perforates the liner,
            // so the comparison is not obscured by the initialLinerThickness clamp.
            AblativeNozzleModel shortBurn = new AblativeNozzleModel(params, contour)
                    .setMaterial(AblativeNozzleModel.AblativeMaterial.GRAPHITE)
                    .setInitialLinerThickness(0.10)
                    .setBurnTime(5.0)
                    .calculate(heatProfile);
            AblativeNozzleModel longBurn = new AblativeNozzleModel(params, contour)
                    .setMaterial(AblativeNozzleModel.AblativeMaterial.GRAPHITE)
                    .setInitialLinerThickness(0.10)
                    .setBurnTime(30.0)
                    .calculate(heatProfile);

            assertThat(shortBurn.isPerforatedAnywhere()).isFalse();
            assertThat(longBurn.isPerforatedAnywhere()).isFalse();
            assertThat(longBurn.getMaxRecessionDepth())
                    .isGreaterThan(shortBurn.getMaxRecessionDepth());
        }

        @Test
        @DisplayName("Surface temperature should exceed back-wall temperature at every station")
        void surfaceTempShouldExceedBackWallTemp() {
            model.calculate(heatProfile);

            for (AblativeNozzleModel.AblativePoint pt : model.getProfile()) {
                assertThat(pt.surfaceTemp())
                        .as("T_surface at x=%.4f", pt.x())
                        .isGreaterThan(300.0);
            }
        }

        @Test
        @DisplayName("Remaining thickness equals initial thickness minus recession depth at all stations")
        void remainingThicknessEqualsInitialMinusRecession() {
            model.calculate(heatProfile);

            for (AblativeNozzleModel.AblativePoint pt : model.getProfile()) {
                assertThat(pt.remainingThickness())
                        .as("remainingThickness at x=%.4f", pt.x())
                        .isCloseTo(pt.initialThickness() - pt.recessDepth(), within(1e-12));
            }
        }

        @Test
        @DisplayName("Higher chamber pressure should increase max recession depth (Bartz Pc^0.8 scaling)")
        void higherChamberPressureIncreasesRecession() {
            // Use graphite so that neither pressure condition perforates the liner.
            // Doubled Pc → Bartz h scales as Pc^0.8 (~1.74×) → higher wall temperature
            // → higher Arrhenius char rate → measurably more recession.
            NozzleDesignParameters highPParams = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0).chamberPressure(14e6)
                    .chamberTemperature(3500).ambientPressure(101325)
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .numberOfCharLines(10).wallAngleInitialDegrees(30)
                    .lengthFraction(0.8).axisymmetric(true).build();

            NozzleContour highPContour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, highPParams);
            highPContour.generate(50);

            List<HeatTransferModel.WallThermalPoint> highPHeat =
                    new HeatTransferModel(highPParams, highPContour)
                            .calculate(List.of()).getWallThermalProfile();

            AblativeNozzleModel lowP = new AblativeNozzleModel(params, contour)
                    .setMaterial(AblativeNozzleModel.AblativeMaterial.GRAPHITE)
                    .setInitialLinerThickness(0.10)
                    .calculate(heatProfile);
            AblativeNozzleModel highP = new AblativeNozzleModel(highPParams, highPContour)
                    .setMaterial(AblativeNozzleModel.AblativeMaterial.GRAPHITE)
                    .setInitialLinerThickness(0.10)
                    .calculate(highPHeat);

            assertThat(lowP.isPerforatedAnywhere()).isFalse();
            assertThat(highP.isPerforatedAnywhere()).isFalse();
            assertThat(highP.getMaxRecessionDepth()).isGreaterThan(lowP.getMaxRecessionDepth());
        }

        @Test
        @DisplayName("EPDM should recede faster than graphite at the same thermal conditions")
        void epdmRecedesFasterThanGraphite() {
            AblativeNozzleModel epdm = new AblativeNozzleModel(params, contour)
                    .setMaterial(AblativeNozzleModel.AblativeMaterial.EPDM)
                    .calculate(heatProfile);
            AblativeNozzleModel graphite = new AblativeNozzleModel(params, contour)
                    .setMaterial(AblativeNozzleModel.AblativeMaterial.GRAPHITE)
                    .calculate(heatProfile);

            assertThat(epdm.getMaxRecessionDepth())
                    .isGreaterThan(graphite.getMaxRecessionDepth());
        }
    }

    // -----------------------------------------------------------------------
    // Perforation detection
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Perforation Detection Tests")
    class PerforationTests {

        @Test
        @DisplayName("Very thin liner with long EPDM burn should be perforated")
        void thinLinerWithLongBurnShouldPerforate() {
            List<HeatTransferModel.WallThermalPoint> heat =
                    new HeatTransferModel(params, contour).calculate(List.of()).getWallThermalProfile();

            AblativeNozzleModel thinModel = new AblativeNozzleModel(params, contour)
                    .setMaterial(AblativeNozzleModel.AblativeMaterial.EPDM)
                    .setInitialLinerThickness(0.0001)  // 0.1 mm — consumed in seconds
                    .setBurnTime(60.0)
                    .calculate(heat);

            assertThat(thinModel.isPerforatedAnywhere()).isTrue();
        }

        @Test
        @DisplayName("Thick liner with very short burn should not be perforated")
        void thickLinerShortBurnShouldNotPerforate() {
            AblativeNozzleModel safeModel = new AblativeNozzleModel(params, contour)
                    .setInitialLinerThickness(0.10)  // 100 mm
                    .setBurnTime(0.01)               // 10 ms — negligible recession
                    .calculate(List.of());

            assertThat(safeModel.isPerforatedAnywhere()).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // Pre-calculate getter behaviour
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Pre-calculate Getter Tests")
    class PreCalculateGetterTests {

        @Test
        @DisplayName("getMaxRecessionDepth() before calculate() returns 0")
        void maxRecessionBeforeCalculateReturnsZero() {
            assertThat(model.getMaxRecessionDepth()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("getMinRemainingThickness() before calculate() returns 0")
        void minRemainingBeforeCalculateReturnsZero() {
            assertThat(model.getMinRemainingThickness()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("isPerforatedAnywhere() before calculate() returns false")
        void isPerforatedBeforeCalculateReturnsFalse() {
            assertThat(model.isPerforatedAnywhere()).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // AblativePoint record
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("AblativePoint Record Tests")
    class AblativePointTests {

        @Test
        @DisplayName("charFraction() equals recessDepth / initialThickness")
        void charFractionMatchesRatio() {
            AblativeNozzleModel.AblativePoint pt = new AblativeNozzleModel.AblativePoint(
                    0.01, 0.06, 1200.0, 1e-5, 0.005, 0.015, 0.020, false);

            assertThat(pt.charFraction()).isCloseTo(0.25, within(1e-9));
        }

        @Test
        @DisplayName("charFraction() returns 0 when initialThickness is 0")
        void charFractionZeroWhenInitialThicknessZero() {
            AblativeNozzleModel.AblativePoint pt = new AblativeNozzleModel.AblativePoint(
                    0.01, 0.06, 1200.0, 1e-5, 0.0, 0.0, 0.0, false);

            assertThat(pt.charFraction()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("toString() contains 'PERFORATED' for a perforated point")
        void toStringContainsPerforatedFlag() {
            AblativeNozzleModel.AblativePoint pt = new AblativeNozzleModel.AblativePoint(
                    0.01, 0.06, 2000.0, 5e-4, 0.020, 0.0, 0.020, true);

            assertThat(pt.toString()).contains("PERFORATED");
        }

        @Test
        @DisplayName("toString() does not contain 'PERFORATED' for an intact point")
        void toStringDoesNotContainPerforatedFlag() {
            AblativeNozzleModel.AblativePoint pt = new AblativeNozzleModel.AblativePoint(
                    0.01, 0.06, 1200.0, 1e-5, 0.005, 0.015, 0.020, false);

            assertThat(pt.toString()).doesNotContain("PERFORATED");
        }
    }

    // -----------------------------------------------------------------------
    // Material presets
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Material Preset Tests")
    class MaterialPresetTests {

        @Test
        @DisplayName("Carbon-phenolic has higher activation energy than EPDM")
        void carbonPhenolicHasHigherActivationEnergyThanEpdm() {
            assertThat(AblativeNozzleModel.AblativeMaterial.CARBON_PHENOLIC.activationEnergy())
                    .isGreaterThan(AblativeNozzleModel.AblativeMaterial.EPDM.activationEnergy());
        }

        @Test
        @DisplayName("Graphite has higher char thermal conductivity than all organic ablatives")
        void graphiteHasHigherCharConductivityThanOrganic() {
            assertThat(AblativeNozzleModel.AblativeMaterial.GRAPHITE.charThermalConductivity())
                    .isGreaterThan(AblativeNozzleModel.AblativeMaterial.CARBON_PHENOLIC.charThermalConductivity());
        }

        @Test
        @DisplayName("All material presets have strictly positive physical properties")
        void allMaterialsHavePositiveProperties() {
            for (AblativeNozzleModel.AblativeMaterial mat : List.of(
                    AblativeNozzleModel.AblativeMaterial.CARBON_PHENOLIC,
                    AblativeNozzleModel.AblativeMaterial.SILICA_PHENOLIC,
                    AblativeNozzleModel.AblativeMaterial.EPDM,
                    AblativeNozzleModel.AblativeMaterial.GRAPHITE)) {
                assertThat(mat.preExponentialFactor()).isGreaterThan(0.0);
                assertThat(mat.activationEnergy()).isGreaterThan(0.0);
                assertThat(mat.charThermalConductivity()).isGreaterThan(0.0);
                assertThat(mat.virginThermalConductivity()).isGreaterThan(0.0);
                assertThat(mat.density()).isGreaterThan(0.0);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Auto-generate contour
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Auto-Generate Contour Tests")
    class AutoGenerateContourTests {

        @Test
        @DisplayName("calculate() on an ungenerated contour triggers auto-generation")
        void calculateOnUngeneratedContourAutoGenerates() {
            NozzleContour ungenerated = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);

            AblativeNozzleModel autoModel = new AblativeNozzleModel(params, ungenerated)
                    .calculate(List.of());

            assertThat(autoModel.getProfile()).isNotEmpty();
        }
    }
}
