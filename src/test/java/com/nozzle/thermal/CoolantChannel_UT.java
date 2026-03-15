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

@DisplayName("CoolantChannel Tests")
class CoolantChannel_UT {

    private NozzleDesignParameters params;
    private NozzleContour contour;

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
    }

    @Nested
    @DisplayName("Hydraulic Calculation Tests")
    class HydraulicTests {

        @Test
        @DisplayName("Should produce a non-empty profile")
        void shouldProduceNonEmptyProfile() {
            CoolantChannel channel = new CoolantChannel(contour).calculate();
            assertThat(channel.getProfile()).isNotEmpty();
        }

        @Test
        @DisplayName("Heat transfer coefficient should be positive at every station")
        void heatTransferCoeffShouldBePositive() {
            CoolantChannel channel = new CoolantChannel(contour).calculate();
            for (CoolantChannel.ChannelPoint pt : channel.getProfile()) {
                assertThat(pt.heatTransferCoeff())
                        .as("h at x=%.4f", pt.x())
                        .isGreaterThan(0.0);
            }
        }

        @Test
        @DisplayName("High mass flow rate should produce turbulent flow (Re > 4000)")
        void highFlowRateShouldBeTurbulent() {
            CoolantChannel channel = new CoolantChannel(contour)
                    .setCoolant(CoolantChannel.CoolantProperties.RP1, 5.0, 300, 8e6)
                    .calculate();
            for (CoolantChannel.ChannelPoint pt : channel.getProfile()) {
                assertThat(pt.reynoldsNumber())
                        .as("Re at x=%.4f", pt.x())
                        .isGreaterThan(4000);
            }
        }

        @Test
        @DisplayName("Pressure should decrease monotonically in the flow direction")
        void pressureShouldDecreaseAlongFlowPath() {
            // Counter-flow: inlet is at exit (last axial point), outlet at throat (first axial)
            CoolantChannel channel = new CoolantChannel(contour).calculate();
            List<CoolantChannel.ChannelPoint> profile = channel.getProfile();

            // Pressure at the inlet (exit end) should exceed pressure at the outlet (throat end)
            double inletPressure  = profile.getLast().coolantPressure();
            double outletPressure = profile.getFirst().coolantPressure();
            assertThat(inletPressure).isGreaterThan(outletPressure);
        }

        @Test
        @DisplayName("Total pressure drop should be positive")
        void totalPressureDropShouldBePositive() {
            CoolantChannel channel = new CoolantChannel(contour).calculate();
            assertThat(channel.getTotalPressureDrop()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("Higher mass flow rate should increase pressure drop")
        void higherFlowRateShouldIncreasePressureDrop() {
            double dP_low  = new CoolantChannel(contour)
                    .setCoolant(CoolantChannel.CoolantProperties.RP1, 1.0, 300, 8e6)
                    .calculate().getTotalPressureDrop();
            double dP_high = new CoolantChannel(contour)
                    .setCoolant(CoolantChannel.CoolantProperties.RP1, 5.0, 300, 8e6)
                    .calculate().getTotalPressureDrop();

            assertThat(dP_high).isGreaterThan(dP_low);
        }

        @Test
        @DisplayName("Hydraulic diameter should match analytical formula")
        void hydraulicDiameterShouldMatchFormula() {
            double w = 0.003, h = 0.004;
            CoolantChannel channel = new CoolantChannel(contour)
                    .setChannelGeometry(100, w, h, 0.001)
                    .calculate();

            double expected = 2.0 * w * h / (w + h);
            assertThat(channel.hydraulicDiameter()).isCloseTo(expected, within(1e-9));
        }
    }

    @Nested
    @DisplayName("Thermal Analysis Tests")
    class ThermalTests {

        private List<HeatTransferModel.WallThermalPoint> thermalProfile;

        @BeforeEach
        void computeThermalProfile() {
            thermalProfile = new HeatTransferModel(params, contour)
                    .calculate(List.of())
                    .getWallThermalProfile();
        }

        @Test
        @DisplayName("Coolant temperature should rise with heat input")
        void coolantTemperatureShouldRise() {
            CoolantChannel channel = new CoolantChannel(contour)
                    .setCoolant(CoolantChannel.CoolantProperties.RP1, 1.0, 300, 8e6)
                    .calculate(thermalProfile);

            assertThat(channel.getCoolantTemperatureRise()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("Higher mass flow should reduce temperature rise")
        void higherMassFlowShouldReduceTemperatureRise() {
            double rise_low = new CoolantChannel(contour)
                    .setCoolant(CoolantChannel.CoolantProperties.RP1, 0.5, 300, 8e6)
                    .calculate(thermalProfile).getCoolantTemperatureRise();
            double rise_high = new CoolantChannel(contour)
                    .setCoolant(CoolantChannel.CoolantProperties.RP1, 5.0, 300, 8e6)
                    .calculate(thermalProfile).getCoolantTemperatureRise();

            assertThat(rise_high).isLessThan(rise_low);
        }

        @Test
        @DisplayName("Boiling margin should be computed (non-zero) for all profile points")
        void boilingMarginShouldBeComputed() {
            CoolantChannel channel = new CoolantChannel(contour)
                    .setCoolant(CoolantChannel.CoolantProperties.RP1, 2.0, 300, 8e6)
                    .calculate(thermalProfile);

            long computed = channel.getProfile().stream()
                    .filter(pt -> pt.boilingMargin() != 0.0)
                    .count();
            assertThat(computed).isGreaterThan(0);
        }

        @Test
        @DisplayName("Higher inlet pressure should improve boiling margin (higher T_sat)")
        void higherInletPressureShouldImproveBoilingMargin() {
            CoolantChannel channelLow = new CoolantChannel(contour)
                    .setCoolant(CoolantChannel.CoolantProperties.RP1, 2.0, 300, 5e6)
                    .calculate(thermalProfile);
            CoolantChannel channelHigh = new CoolantChannel(contour)
                    .setCoolant(CoolantChannel.CoolantProperties.RP1, 2.0, 300, 15e6)
                    .calculate(thermalProfile);
            assertThat(channelHigh.getMinBoilingMargin())
                    .isGreaterThan(channelLow.getMinBoilingMargin());
        }

        @Test
        @DisplayName("Saturation temperature should increase with pressure")
        void satTempShouldIncreaseWithPressure() {
            double tLow  = CoolantChannel.CoolantProperties.RP1.saturationTemperature(1e6);
            double tHigh = CoolantChannel.CoolantProperties.RP1.saturationTemperature(10e6);
            assertThat(tHigh).isGreaterThan(tLow);
        }

        @Test
        @DisplayName("Counter-flow should warm coolant toward throat; co-current toward exit")
        void counterflowShouldReverseTemperatureGradient() {
            // Counter-flow (default): coolant enters at the exit end, absorbs heat as it
            // travels toward the throat.  In the axially-ordered profile the throat (first
            // point) is the warmest end and the exit (last point) is the coolest (inlet).
            CoolantChannel counterflow = new CoolantChannel(contour)
                    .setCoolant(CoolantChannel.CoolantProperties.RP1, 1.0, 300, 8e6)
                    .setCounterflow(true)
                    .calculate(thermalProfile);

            double cfThroatTemp = counterflow.getProfile().getFirst().coolantTemperature();
            double cfExitTemp   = counterflow.getProfile().getLast().coolantTemperature();
            assertThat(cfThroatTemp).isGreaterThan(cfExitTemp);

            // Co-current: coolant enters at the throat end, warms toward the exit.
            // In the axially-ordered profile the exit (last point) is the warmest end.
            CoolantChannel coflow = new CoolantChannel(contour)
                    .setCoolant(CoolantChannel.CoolantProperties.RP1, 1.0, 300, 8e6)
                    .setCounterflow(false)
                    .calculate(thermalProfile);

            double coThroatTemp = coflow.getProfile().getFirst().coolantTemperature();
            double coExitTemp   = coflow.getProfile().getLast().coolantTemperature();
            assertThat(coExitTemp).isGreaterThan(coThroatTemp);
        }
    }

    @Nested
    @DisplayName("HeatTransferModel Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Setting a channel should not break the thermal calculation")
        void settingChannelShouldNotBreakThermalCalculation() {
            CoolantChannel channel = new CoolantChannel(contour).calculate();

            HeatTransferModel model = new HeatTransferModel(params, contour)
                    .setCoolantChannel(channel)
                    .calculate(List.of());

            assertThat(model.getWallThermalProfile()).isNotEmpty();
            assertThat(model.getMaxWallTemperature()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("Position-varying h_coolant should change wall temperatures vs fixed scalar")
        void positionVaryingChannelShouldAffectWallTemperatures() {
            // Very low fixed coolant h → high wall temps
            HeatTransferModel withLowFixed = new HeatTransferModel(params, contour)
                    .setCoolantProperties(300, 500)
                    .calculate(List.of());

            // Sized channel will have much higher h than 500 W/(m²·K)
            CoolantChannel channel = new CoolantChannel(contour)
                    .setCoolant(CoolantChannel.CoolantProperties.RP1, 3.0, 300, 8e6)
                    .calculate();

            HeatTransferModel withChannel = new HeatTransferModel(params, contour)
                    .setCoolantChannel(channel)
                    .calculate(List.of());

            assertThat(withChannel.getMaxWallTemperature())
                    .isLessThan(withLowFixed.getMaxWallTemperature());
        }

        @Test
        @DisplayName("getHeatTransferCoeffAt should interpolate within profile bounds")
        void getHCoeffAtShouldReturnPositiveValue() {
            CoolantChannel channel = new CoolantChannel(contour).calculate();
            double xMid = channel.getProfile().get(channel.getProfile().size() / 2).x();
            assertThat(channel.getHeatTransferCoeffAt(xMid)).isGreaterThan(0.0);
        }
    }

    @Nested
    @DisplayName("Auto-Generate Contour Tests")
    class AutoGenerateContourTests {

        @Test
        @DisplayName("calculate() on ungenerated contour triggers auto-generate (L267 TRUE)")
        void calculateOnUngeneratedContourAutoGenerates() {
            NozzleContour ungenerated = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);

            CoolantChannel channel = new CoolantChannel(ungenerated).calculate();

            assertThat(channel.getProfile()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Empty Profile / Pre-calculate Getter Tests")
    class EmptyProfileGetterTests {

        @Test
        @DisplayName("getHeatTransferCoeffAt() before calculate() returns fallback 5000 W/(m²·K) (L414 TRUE)")
        void getHeatTransferCoeffBeforeCalculateReturnsFallback() {
            CoolantChannel channel = new CoolantChannel(contour);
            assertThat(channel.getHeatTransferCoeffAt(0.05)).isCloseTo(5000.0, within(1e-9));
        }

        @Test
        @DisplayName("getCoolantTemperatureRise() before calculate() returns 0 (L430 TRUE)")
        void getCoolantTemperatureRiseBeforeCalculateReturnsZero() {
            CoolantChannel channel = new CoolantChannel(contour);
            assertThat(channel.getCoolantTemperatureRise()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("getMinBoilingMargin() after hydraulics-only calculate() returns MAX_VALUE (L456 FALSE — all margins filtered)")
        void getMinBoilingMarginWithHydraulicsOnlyReturnsMaxValue() {
            // Hydraulics-only: all boilingMargin = 0.0 → filter(m != 0.0) always FALSE → orElse(MAX_VALUE)
            CoolantChannel channel = new CoolantChannel(contour).calculate();
            assertThat(channel.getMinBoilingMargin()).isEqualTo(Double.MAX_VALUE);
        }
    }

    @Nested
    @DisplayName("Nucleate Boiling Tests")
    class NucleateBoilingTests {

        @Test
        @DisplayName("ChannelPoint.isNucleateBoiling() TRUE — boilingMargin < 0")
        void isNucleateBoilingTrueWhenMarginNegative() {
            // Directly construct a record with negative boilingMargin (coldWall > T_sat)
            CoolantChannel.ChannelPoint boilingPt = new CoolantChannel.ChannelPoint(
                    0.01, 0.05, 300, 8e6, 1.0, 5000, 50, 3000, 1000, 900, 600, -300.0);
            assertThat(boilingPt.isNucleateBoiling()).isTrue();
        }

        @Test
        @DisplayName("ChannelPoint.isNucleateBoiling() FALSE — boilingMargin >= 0")
        void isNucleateBoilingFalseWhenMarginPositive() {
            // Directly construct a record with positive boilingMargin (coldWall < T_sat)
            CoolantChannel.ChannelPoint subcooledPt = new CoolantChannel.ChannelPoint(
                    0.01, 0.05, 300, 8e6, 1.0, 5000, 50, 3000, 1000, 350, 600, 250.0);
            assertThat(subcooledPt.isNucleateBoiling()).isFalse();
        }

        @Test
        @DisplayName("isFullySubcooled() TRUE — hydraulics-only mode (all margins = 0.0, not boiling)")
        void isFullySubcooledTrueWithHydraulicsOnly() {
            // Hydraulics-only calculate(): all boilingMargin = 0.0
            // isNucleateBoiling(0.0) = (0.0 < 0.0) = false → noneMatch → isFullySubcooled() = true
            CoolantChannel channel = new CoolantChannel(contour).calculate();
            assertThat(channel.isFullySubcooled()).isTrue();
        }

        @Test
        @DisplayName("isFullySubcooled() FALSE — LH2 at very low flow: coldWall >> T_sat")
        void isFullySubcooledFalseWithLh2NucleateBoiling() {
            // LH2 T_sat ≈ 23 K at 2e5 Pa; any real heat flux drives coldWall >> 23 K → boiling
            List<HeatTransferModel.WallThermalPoint> thermalProfile =
                    new HeatTransferModel(params, contour).calculate(List.of()).getWallThermalProfile();

            CoolantChannel channel = new CoolantChannel(contour)
                    .setCoolant(CoolantChannel.CoolantProperties.LH2, 0.05, 20.0, 2e5)
                    .calculate(thermalProfile);

            assertThat(channel.isFullySubcooled()).isFalse();
        }
    }

    @Nested
    @DisplayName("Uncovered Utility Method Tests")
    class UtilityMethodTests {

        @Test
        @DisplayName("setWallConductivity() changes wall conduction resistance")
        void setWallConductivityAffectsColdWallTemperature() {
            List<HeatTransferModel.WallThermalPoint> thermalProfile =
                    new HeatTransferModel(params, contour)
                            .calculate(List.of())
                            .getWallThermalProfile();

            // Low conductivity → more conduction resistance → higher cold-wall temperature
            CoolantChannel lowCond = new CoolantChannel(contour)
                    .setCoolant(CoolantChannel.CoolantProperties.RP1, 2.0, 300, 8e6)
                    .setWallConductivity(5.0)      // low conductivity W/(m·K)
                    .calculate(thermalProfile);

            CoolantChannel highCond = new CoolantChannel(contour)
                    .setCoolant(CoolantChannel.CoolantProperties.RP1, 2.0, 300, 8e6)
                    .setWallConductivity(200.0)     // high conductivity W/(m·K)
                    .calculate(thermalProfile);

            double minMarginLow  = lowCond.getMinBoilingMargin();
            double minMarginHigh = highCond.getMinBoilingMargin();
            // Higher conductivity → lower cold-wall temp → better (larger) boiling margin
            assertThat(minMarginHigh).isGreaterThan(minMarginLow);
        }

        @Test
        @DisplayName("totalFlowArea() equals numberOfChannels × width × height")
        void totalFlowAreaMatchesAnalyticalFormula() {
            CoolantChannel channel = new CoolantChannel(contour)
                    .setChannelGeometry(120, 0.004, 0.006, 0.001);

            double expected = 120 * 0.004 * 0.006;
            assertThat(channel.totalFlowArea()).isCloseTo(expected, within(1e-12));
        }
    }
}
