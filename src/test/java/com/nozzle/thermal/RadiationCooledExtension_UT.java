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

package com.nozzle.thermal;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.moc.CharacteristicPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("RadiationCooledExtension Tests")
class RadiationCooledExtension_UT {

    private NozzleDesignParameters params;
    private NozzleContour contour;
    private RadiationCooledExtension model;

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
        model = new RadiationCooledExtension(params, contour);
    }

    // -----------------------------------------------------------------------
    // Construction and configuration
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {

        @Test
        @DisplayName("Default model is non-null with empty profile")
        void defaultModelIsNonNullWithEmptyProfile() {
            assertThat(model).isNotNull();
            assertThat(model.getProfile()).isEmpty();
        }

        @Test
        @DisplayName("Fluent setters return this instance")
        void fluentSettersReturnThis() {
            RadiationCooledExtension configured = new RadiationCooledExtension(params, contour)
                    .setMaterial(RadiationCooledExtension.ExtensionMaterial.NIOBIUM_C103)
                    .setExtensionStartX(0.05)
                    .setEnvironmentTemperature(300.0)
                    .setConvergenceTolerance(0.01);

            assertThat(configured).isNotNull();
        }

        @Test
        @DisplayName("setEnvironmentTemperature throws on negative value")
        void setEnvironmentTemperatureRejectsNegativeValue() {
            assertThatThrownBy(() -> model.setEnvironmentTemperature(-1.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("environmentTemperature must be >= 0");
        }

        @Test
        @DisplayName("setConvergenceTolerance throws on zero value")
        void setConvergenceToleranceRejectsZero() {
            assertThatThrownBy(() -> model.setConvergenceTolerance(0.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("convergenceTolerance must be > 0");
        }

        @Test
        @DisplayName("setConvergenceTolerance throws on negative value")
        void setConvergenceToleranceRejectsNegative() {
            assertThatThrownBy(() -> model.setConvergenceTolerance(-0.1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("setMaxNewtonIterations throws on zero value")
        void setMaxNewtonIterationsRejectsZero() {
            assertThatThrownBy(() -> model.setMaxNewtonIterations(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxNewtonIterations must be > 0");
        }

        @Test
        @DisplayName("setMaxNewtonIterations throws on negative value")
        void setMaxNewtonIterationsRejectsNegative() {
            assertThatThrownBy(() -> model.setMaxNewtonIterations(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // -----------------------------------------------------------------------
    // Profile generation
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Profile Generation Tests")
    class ProfileGenerationTests {

        @Test
        @DisplayName("calculate(null) produces a non-empty profile")
        void calculateNullFlowPointsProducesProfile() {
            model.calculate(null);

            assertThat(model.getProfile()).isNotEmpty();
        }

        @Test
        @DisplayName("calculate(emptyList) produces a non-empty profile")
        void calculateEmptyFlowPointsProducesProfile() {
            model.calculate(List.of());

            assertThat(model.getProfile()).isNotEmpty();
        }

        @Test
        @DisplayName("Profile size equals contour point count when no start-x filter is set")
        void profileSizeMatchesContourCountWithNoFilter() {
            model.calculate(List.of());

            assertThat(model.getProfile()).hasSize(contour.getContourPoints().size());
        }

        @Test
        @DisplayName("setExtensionStartX filters out upstream stations")
        void extensionStartXFiltersUpstreamStations() {
            int fullSize = contour.getContourPoints().size();

            // Set start halfway along the contour
            double midX = contour.getContourPoints().get(fullSize / 2).x();
            model.setExtensionStartX(midX).calculate(List.of());

            assertThat(model.getProfile().size()).isLessThan(fullSize);
        }

        @Test
        @DisplayName("Setting extensionStartX beyond contour produces empty profile")
        void extensionStartXBeyondContourProducesEmptyProfile() {
            model.setExtensionStartX(Double.MAX_VALUE).calculate(List.of());

            assertThat(model.getProfile()).isEmpty();
        }

        @Test
        @DisplayName("calculate() on ungenerated contour auto-generates it")
        void calculateOnUngeneratedContourAutoGenerates() {
            NozzleContour ungenerated = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);

            RadiationCooledExtension ext = new RadiationCooledExtension(params, ungenerated)
                    .calculate(List.of());

            assertThat(ext.getProfile()).isNotEmpty();
        }

        @Test
        @DisplayName("Conical (straight-walled) contour produces a valid profile")
        void conicalContourProducesValidProfile() {
            // The conical section is piecewise-linear, so the second derivative of the
            // wall radius is zero — this exercises the near-zero-curvature guard in
            // localRadiusOfCurvature() which returns the capped value 10 × r_throat.
            NozzleContour conicalContour = new NozzleContour(NozzleContour.ContourType.CONICAL, params);
            conicalContour.generate(50);

            RadiationCooledExtension ext = new RadiationCooledExtension(params, conicalContour)
                    .calculate(List.of());

            assertThat(ext.getProfile()).isNotEmpty();
            assertThat(ext.getMaxWallTemperature()).isGreaterThan(0.0);
        }
    }

    // -----------------------------------------------------------------------
    // Flow point lookup (MOC path)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Flow Point Lookup Tests")
    class FlowPointLookupTests {

        @Test
        @DisplayName("calculate() with non-empty flowPoints list produces a profile")
        void calculateWithFlowPointsProducesProfile() {
            // Three wall-boundary flow points spanning the diverging section
            List<CharacteristicPoint> flowPoints = List.of(
                    CharacteristicPoint.create(0.02, 0.055, 2.0, 0.1, 0.5, 0.5,
                            4e6, 2800.0, 3.0, 2500.0, CharacteristicPoint.PointType.WALL),
                    CharacteristicPoint.create(0.05, 0.070, 2.5, 0.1, 0.7, 0.4,
                            2e6, 2000.0, 1.5, 3000.0, CharacteristicPoint.PointType.WALL),
                    CharacteristicPoint.create(0.09, 0.090, 3.0, 0.05, 0.9, 0.35,
                            1e6, 1500.0, 1.0, 3500.0, CharacteristicPoint.PointType.WALL));

            model.calculate(flowPoints);

            assertThat(model.getProfile()).isNotEmpty();
        }

        @Test
        @DisplayName("nearestFlowPoint covers both branches of the distance update guard")
        void nearestFlowPointCoversBothDistanceBranches() {
            // Position the three flow points as [far, at-first-contour-point, far]:
            //   • i=1: closest is nearer than farFirst  → if (dSq < minDistSq) == TRUE  (update)
            //   • i=2: farThird is farther than closest → if (dSq < minDistSq) == FALSE (no update)
            // The for-loop also exits naturally (i reaches size=3), covering the loop-exit branch.
            double refX = contour.getContourPoints().getFirst().x();
            double refY = contour.getContourPoints().getFirst().y();

            CharacteristicPoint farFirst = CharacteristicPoint.create(
                    refX + 0.5, refY + 0.5, 2.0, 0.1, 0.5, 0.5,
                    3e6, 2500.0, 2.0, 2800.0, CharacteristicPoint.PointType.INTERIOR);
            CharacteristicPoint closest = CharacteristicPoint.create(
                    refX, refY, 2.0, 0.1, 0.5, 0.5,
                    3e6, 2500.0, 2.0, 2800.0, CharacteristicPoint.PointType.WALL);
            CharacteristicPoint farThird = CharacteristicPoint.create(
                    refX + 1.0, refY + 1.0, 2.0, 0.1, 0.5, 0.5,
                    3e6, 2500.0, 2.0, 2800.0, CharacteristicPoint.PointType.INTERIOR);

            model.calculate(List.of(farFirst, closest, farThird));

            assertThat(model.getProfile()).isNotEmpty();
        }

        @Test
        @DisplayName("Newton loops exit via iteration limit when maxNewtonIterations is 1")
        void newtonLoopsExhaustWhenMaxIterationsIsOne() {
            // With maxNewtonIterations=1 and isentropic fallback (null flowPoints):
            //   • solveEquilibriumTemperature loop runs once and exits via for-condition
            //   • estimateMachFromAreaRatio    loop runs once and exits via for-condition
            // Both for-loop exit branches (i >= maxIterations) are covered.
            model.setMaxNewtonIterations(1).calculate(null);

            assertThat(model.getProfile()).isNotEmpty();
            assertThat(model.getMaxWallTemperature()).isGreaterThan(0.0);
        }
    }

    // -----------------------------------------------------------------------
    // Physical behaviour
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Physical Behaviour Tests")
    class PhysicsBehaviourTests {

        @Test
        @DisplayName("Equilibrium wall temperature is below recovery temperature at every station")
        void wallTemperatureBelowRecoveryTemperatureEverywhere() {
            model.calculate(List.of());

            for (RadiationCooledExtension.ExtensionPoint pt : model.getProfile()) {
                assertThat(pt.wallTemperature())
                        .as("T_wall at x=%.4f must be below T_aw=%.1f K", pt.x(), pt.recoveryTemperature())
                        .isLessThan(pt.recoveryTemperature());
            }
        }

        @Test
        @DisplayName("Equilibrium wall temperature is above environment temperature at every station")
        void wallTemperatureAboveEnvironmentEverywhere() {
            double T_env = 3.0;
            model.setEnvironmentTemperature(T_env).calculate(List.of());

            for (RadiationCooledExtension.ExtensionPoint pt : model.getProfile()) {
                assertThat(pt.wallTemperature())
                        .as("T_wall at x=%.4f must exceed T_env=%.1f K", pt.x(), T_env)
                        .isGreaterThan(T_env);
            }
        }

        @Test
        @DisplayName("Convective and radiative fluxes are approximately equal at equilibrium")
        void heatFluxBalanceIsNearZeroAtEquilibrium() {
            model.calculate(List.of());

            for (RadiationCooledExtension.ExtensionPoint pt : model.getProfile()) {
                double relativeImbalance = Math.abs(pt.heatFluxBalance())
                        / Math.max(pt.convectiveHeatFlux(), 1.0);
                assertThat(relativeImbalance)
                        .as("Heat-flux residual at x=%.4f: balance=%.2f W/m², conv=%.2f W/m²",
                                pt.x(), pt.heatFluxBalance(), pt.convectiveHeatFlux())
                        .isLessThan(1e-3);  // < 0.1 % relative imbalance
            }
        }

        @Test
        @DisplayName("Higher emissivity results in lower equilibrium wall temperature")
        void higherEmissivityLowersWallTemperature() {
            // Two custom materials identical except for emissivity
            RadiationCooledExtension.ExtensionMaterial lowEps = new RadiationCooledExtension.ExtensionMaterial(
                    "Low-ε test", 0.3, 2000.0, 5000.0, 20.0);
            RadiationCooledExtension.ExtensionMaterial highEps = new RadiationCooledExtension.ExtensionMaterial(
                    "High-ε test", 0.9, 2000.0, 5000.0, 20.0);

            double maxLow = new RadiationCooledExtension(params, contour)
                    .setMaterial(lowEps).calculate(List.of()).getMaxWallTemperature();
            double maxHigh = new RadiationCooledExtension(params, contour)
                    .setMaterial(highEps).calculate(List.of()).getMaxWallTemperature();

            assertThat(maxHigh).isLessThan(maxLow);
        }

        @Test
        @DisplayName("Higher chamber pressure raises equilibrium wall temperature (more convective flux)")
        void higherChamberPressureRaisesWallTemperature() {
            NozzleDesignParameters highPcParams = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0).chamberPressure(14e6)
                    .chamberTemperature(3500).ambientPressure(101325)
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .numberOfCharLines(10).wallAngleInitialDegrees(30)
                    .lengthFraction(0.8).axisymmetric(true).build();

            NozzleContour highPcContour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, highPcParams);
            highPcContour.generate(50);

            double lowPcMax  = new RadiationCooledExtension(params, contour)
                    .calculate(List.of()).getMaxWallTemperature();
            double highPcMax = new RadiationCooledExtension(highPcParams, highPcContour)
                    .calculate(List.of()).getMaxWallTemperature();

            assertThat(highPcMax).isGreaterThan(lowPcMax);
        }

        @Test
        @DisplayName("Wall temperature is positive and finite at every station")
        void wallTemperatureIsPositiveAndFinite() {
            model.calculate(List.of());

            for (RadiationCooledExtension.ExtensionPoint pt : model.getProfile()) {
                assertThat(pt.wallTemperature())
                        .as("T_wall at x=%.4f", pt.x())
                        .isGreaterThan(0.0)
                        .isFinite();
            }
        }

        @Test
        @DisplayName("Space environment (3 K) gives lower wall temperature than sea-level (300 K)")
        void spaceEnvironmentGivesLowerWallTemp() {
            double spaceMax = new RadiationCooledExtension(params, contour)
                    .setEnvironmentTemperature(3.0).calculate(List.of()).getMaxWallTemperature();
            double seaLevelMax = new RadiationCooledExtension(params, contour)
                    .setEnvironmentTemperature(300.0).calculate(List.of()).getMaxWallTemperature();

            assertThat(spaceMax).isLessThan(seaLevelMax);
        }
    }

    // -----------------------------------------------------------------------
    // Overtemperature detection
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Overtemperature Detection Tests")
    class OvertemperatureTests {

        @Test
        @DisplayName("Material with very low temperature limit flags overtemperature")
        void lowLimitMaterialFlagsOvertemperature() {
            RadiationCooledExtension.ExtensionMaterial restrictiveMaterial =
                    new RadiationCooledExtension.ExtensionMaterial(
                            "Test-restrictive", 0.8, 1.0, 5000.0, 20.0); // 1 K limit — always exceeded

            RadiationCooledExtension ext = new RadiationCooledExtension(params, contour)
                    .setMaterial(restrictiveMaterial)
                    .calculate(List.of());

            assertThat(ext.isOvertemperatureAnywhere()).isTrue();
        }

        @Test
        @DisplayName("Material with very high temperature limit never flags overtemperature")
        void highLimitMaterialNeverFlagsOvertemperature() {
            RadiationCooledExtension.ExtensionMaterial generousMaterial =
                    new RadiationCooledExtension.ExtensionMaterial(
                            "Test-generous", 0.8, 1.0e6, 5000.0, 20.0); // 1 MK limit — never exceeded

            RadiationCooledExtension ext = new RadiationCooledExtension(params, contour)
                    .setMaterial(generousMaterial)
                    .calculate(List.of());

            assertThat(ext.isOvertemperatureAnywhere()).isFalse();
        }

        @Test
        @DisplayName("temperatureMargin is negative when overtemperature, positive when safe")
        void temperatureMarginSignMatchesOvertemperatureFlag() {
            model.calculate(List.of());

            for (RadiationCooledExtension.ExtensionPoint pt : model.getProfile()) {
                if (pt.isOvertemperature()) {
                    assertThat(pt.temperatureMargin())
                            .as("margin must be negative when overtemperature at x=%.4f", pt.x())
                            .isLessThan(0.0);
                } else {
                    assertThat(pt.temperatureMargin())
                            .as("margin must be non-negative when safe at x=%.4f", pt.x())
                            .isGreaterThanOrEqualTo(0.0);
                }
            }
        }

        @Test
        @DisplayName("getMinTemperatureMargin() returns 0 before calculate()")
        void minMarginIsZeroBeforeCalculate() {
            assertThat(model.getMinTemperatureMargin()).isEqualTo(0.0);
        }
    }

    // -----------------------------------------------------------------------
    // Aggregate results
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Aggregate Result Tests")
    class AggregateResultTests {

        @Test
        @DisplayName("getMaxWallTemperature() returns 0 before calculate()")
        void maxWallTempZeroBeforeCalculate() {
            assertThat(model.getMaxWallTemperature()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("getMaxWallTemperature() is positive after calculate()")
        void maxWallTempPositiveAfterCalculate() {
            model.calculate(List.of());

            assertThat(model.getMaxWallTemperature()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("isOvertemperatureAnywhere() returns false before calculate()")
        void isOvertemperatureFalseBeforeCalculate() {
            assertThat(model.isOvertemperatureAnywhere()).isFalse();
        }

        @Test
        @DisplayName("getTotalRadiatedPower() returns 0 before calculate()")
        void totalRadiatedPowerZeroBeforeCalculate() {
            assertThat(model.getTotalRadiatedPower()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("getTotalRadiatedPower() is positive after calculate()")
        void totalRadiatedPowerPositiveAfterCalculate() {
            model.calculate(List.of());

            assertThat(model.getTotalRadiatedPower()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("Larger extension area produces more total radiated power")
        void largerExtensionAreaProducesMoreRadiatedPower() {
            // Higher exit Mach → larger exit area → more extension surface → more radiated power
            NozzleDesignParameters highMachParams = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(5.0).chamberPressure(7e6)
                    .chamberTemperature(3500).ambientPressure(101325)
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .numberOfCharLines(10).wallAngleInitialDegrees(30)
                    .lengthFraction(0.8).axisymmetric(true).build();

            NozzleContour highMachContour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, highMachParams);
            highMachContour.generate(50);

            double basePower = new RadiationCooledExtension(params, contour)
                    .calculate(List.of()).getTotalRadiatedPower();
            double highMachPower = new RadiationCooledExtension(highMachParams, highMachContour)
                    .calculate(List.of()).getTotalRadiatedPower();

            assertThat(highMachPower).isGreaterThan(basePower);
        }
    }

    // -----------------------------------------------------------------------
    // ExtensionPoint record
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("ExtensionPoint Record Tests")
    class ExtensionPointTests {

        @Test
        @DisplayName("heatFluxBalance() returns convective minus radiative flux")
        void heatFluxBalanceIsConvMinusRad() {
            RadiationCooledExtension.ExtensionPoint pt =
                    new RadiationCooledExtension.ExtensionPoint(
                            0.1, 0.08, 1200.0, 1800.0, 50000.0, 49000.0, 800.0, 250.0, false);

            assertThat(pt.heatFluxBalance()).isCloseTo(1000.0, within(1e-9));
        }

        @Test
        @DisplayName("toString() contains 'OVERTEMP' for overtemperature point")
        void toStringContainsOvertempFlag() {
            RadiationCooledExtension.ExtensionPoint pt =
                    new RadiationCooledExtension.ExtensionPoint(
                            0.1, 0.08, 1600.0, 1800.0, 50000.0, 50000.0, 800.0, -150.0, true);

            assertThat(pt.toString()).contains("OVERTEMP");
        }

        @Test
        @DisplayName("toString() does not contain 'OVERTEMP' for safe point")
        void toStringDoesNotContainOvertempWhenSafe() {
            RadiationCooledExtension.ExtensionPoint pt =
                    new RadiationCooledExtension.ExtensionPoint(
                            0.1, 0.08, 1200.0, 1800.0, 50000.0, 50000.0, 800.0, 250.0, false);

            assertThat(pt.toString()).doesNotContain("OVERTEMP");
        }
    }

    // -----------------------------------------------------------------------
    // Material presets
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Material Preset Tests")
    class MaterialPresetTests {

        @Test
        @DisplayName("All material presets have strictly positive physical properties")
        void allMaterialsHavePositiveProperties() {
            for (RadiationCooledExtension.ExtensionMaterial mat : List.of(
                    RadiationCooledExtension.ExtensionMaterial.NIOBIUM_C103,
                    RadiationCooledExtension.ExtensionMaterial.RHENIUM_IRIDIUM,
                    RadiationCooledExtension.ExtensionMaterial.TITANIUM_6AL_4V,
                    RadiationCooledExtension.ExtensionMaterial.CARBON_CARBON)) {
                assertThat(mat.emissivity()).isGreaterThan(0.0);
                assertThat(mat.emissivity()).isLessThanOrEqualTo(1.0);
                assertThat(mat.temperatureLimit()).isGreaterThan(0.0);
                assertThat(mat.density()).isGreaterThan(0.0);
                assertThat(mat.thermalConductivity()).isGreaterThan(0.0);
            }
        }

        @Test
        @DisplayName("RHENIUM_IRIDIUM has the highest temperature limit")
        void rheniumIridiumHasHighestTemperatureLimit() {
            assertThat(RadiationCooledExtension.ExtensionMaterial.RHENIUM_IRIDIUM.temperatureLimit())
                    .isGreaterThan(RadiationCooledExtension.ExtensionMaterial.NIOBIUM_C103.temperatureLimit())
                    .isGreaterThan(RadiationCooledExtension.ExtensionMaterial.TITANIUM_6AL_4V.temperatureLimit())
                    .isGreaterThan(RadiationCooledExtension.ExtensionMaterial.CARBON_CARBON.temperatureLimit());
        }

        @Test
        @DisplayName("TITANIUM_6AL_4V has lower density than the metallic materials (Nb, Re/Ir)")
        void titaniumLighterThanMetallics() {
            assertThat(RadiationCooledExtension.ExtensionMaterial.TITANIUM_6AL_4V.density())
                    .isLessThan(RadiationCooledExtension.ExtensionMaterial.NIOBIUM_C103.density())
                    .isLessThan(RadiationCooledExtension.ExtensionMaterial.RHENIUM_IRIDIUM.density());
        }

        @Test
        @DisplayName("CARBON_CARBON has the lowest density of all presets")
        void carbonCarbonHasLowestDensity() {
            assertThat(RadiationCooledExtension.ExtensionMaterial.CARBON_CARBON.density())
                    .isLessThan(RadiationCooledExtension.ExtensionMaterial.TITANIUM_6AL_4V.density())
                    .isLessThan(RadiationCooledExtension.ExtensionMaterial.NIOBIUM_C103.density())
                    .isLessThan(RadiationCooledExtension.ExtensionMaterial.RHENIUM_IRIDIUM.density());
        }

        @Test
        @DisplayName("RHENIUM_IRIDIUM has the highest density (mass budget concern)")
        void rheniumIridiumHasHighestDensity() {
            assertThat(RadiationCooledExtension.ExtensionMaterial.RHENIUM_IRIDIUM.density())
                    .isGreaterThan(RadiationCooledExtension.ExtensionMaterial.NIOBIUM_C103.density());
        }
    }
}
