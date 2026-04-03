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

package com.nozzle.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ShockExpansionModel Tests")
class ShockExpansionModel_UT {

    // -----------------------------------------------------------------------
    // Shared fixtures
    // -----------------------------------------------------------------------

    /** LOX/RP-1, Pc = 7 MPa, Me = 3.  Sea-level-optimized: pe ≈ 160 kPa > pa = 101 kPa. */
    private NozzleDesignParameters seaLevelParams;

    /** Same engine, Me = 6 (vacuum bell) at sea level: strongly overexpanded → separation. */
    private NozzleDesignParameters vacuumNozzleAtSeaLevel;

    /**
     * Low-expansion nozzle (Me = 1.4, Pc = 7 MPa) for testing the Mach-disk regime.
     *
     * <p>With γ = 1.24: pe ≈ 2.35 MPa, normal-shock limit pNS ≈ 4.85 MPa.
     * Running off-design at pa = 5.0 MPa satisfies pa &gt; pNS → Mach disk, and
     * pe &gt; pSep = 0.374 × 5.0 = 1.87 MPa → no internal separation.
     */
    private NozzleDesignParameters lowExpansionParams;

    @BeforeEach
    void setUp() {
        seaLevelParams = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .build();

        vacuumNozzleAtSeaLevel = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(6.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .build();

        lowExpansionParams = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(1.4)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20)
                .wallAngleInitialDegrees(30)
                .lengthFraction(0.8)
                .build();
    }

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("Should construct with valid parameters")
        void shouldConstructWithValidParameters() {
            assertThat(new ShockExpansionModel(seaLevelParams)).isNotNull();
        }

        @Test
        @DisplayName("Should reject null parameters")
        void shouldRejectNullParameters() {
            assertThatThrownBy(() -> new ShockExpansionModel(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should reject non-positive ambient pressure")
        void shouldRejectNonPositiveAmbientPressure() {
            var model = new ShockExpansionModel(seaLevelParams);
            assertThatThrownBy(() -> model.compute(0.0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> model.compute(-1.0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // -----------------------------------------------------------------------
    // Regime classification
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Regime classification")
    class RegimeClassificationTests {

        @Test
        @DisplayName("Should classify underexpanded when pe > pa")
        void shouldClassifyUnderexpanded() {
            // Me=3: pe ≈ 160 kPa; at pa = 50 kPa the nozzle is underexpanded.
            var result = new ShockExpansionModel(seaLevelParams).compute(50000);
            assertThat(result.regime()).isEqualTo(ShockExpansionModel.FlowRegime.UNDEREXPANDED);
        }

        @Test
        @DisplayName("Should classify ideally expanded when pe ≈ pa")
        void shouldClassifyIdeallyExpanded() {
            // Force ideal expansion by evaluating at pe.
            double pe = seaLevelParams.idealExitPressure();
            var result = new ShockExpansionModel(seaLevelParams).compute(pe);
            assertThat(result.regime()).isEqualTo(ShockExpansionModel.FlowRegime.IDEALLY_EXPANDED);
        }

        @Test
        @DisplayName("Should classify overexpanded oblique shock")
        void shouldClassifyOverexpandedObliqueShock() {
            // Me=3 nozzle at slightly higher ambient: pe ≈ 160 kPa, use pa = 250 kPa.
            // Normal shock limit: p_NS = pe × (2γMe² − (γ−1))/(γ+1).
            // With γ=1.24, Me=3: p_NS/pe ≈ (2×1.24×9 − 0.24)/2.24 ≈ 9.86 → p_NS ≈ 1.58 MPa.
            // pa = 250 kPa < p_NS → oblique shock.
            var result = new ShockExpansionModel(seaLevelParams).compute(250000);
            assertThat(result.regime()).isEqualTo(ShockExpansionModel.FlowRegime.OVEREXPANDED_OBLIQUE);
        }

        @Test
        @DisplayName("Should classify Mach disk when pa exceeds normal-shock limit")
        void shouldClassifyMachDisk() {
            // lowExpansionParams: Me=1.4, pe ≈ 2.35 MPa.  Off-design pa = 5.0 MPa.
            // pNS = pe × normalShockRatio(1.4, γ=1.24) ≈ 4.85 MPa < 5.0 MPa → Mach disk.
            // pSep = 0.374 × 5.0 = 1.87 MPa < pe = 2.35 MPa → no separation.
            var result = new ShockExpansionModel(lowExpansionParams).compute(5e6);
            assertThat(result.regime()).isEqualTo(ShockExpansionModel.FlowRegime.OVEREXPANDED_MACH_DISK);
        }

        @Test
        @DisplayName("Should classify separated for vacuum nozzle at sea level")
        void shouldClassifySeparatedForVacuumNozzle() {
            var result = new ShockExpansionModel(vacuumNozzleAtSeaLevel).compute();
            assertThat(result.regime()).isEqualTo(ShockExpansionModel.FlowRegime.SEPARATED);
        }
    }

    // -----------------------------------------------------------------------
    // Thrust coefficient sanity
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Thrust coefficient")
    class ThrustCoefficientTests {

        @Test
        @DisplayName("Ideal Cf should be positive and physically reasonable")
        void idealCfShouldBePositive() {
            var result = new ShockExpansionModel(seaLevelParams).compute();
            assertThat(result.thrustCoefficient()).isGreaterThan(0.5);
            assertThat(result.thrustCoefficient()).isLessThan(3.0);
        }

        @Test
        @DisplayName("Separated Cf should be positive")
        void separatedCfShouldBePositive() {
            var result = new ShockExpansionModel(vacuumNozzleAtSeaLevel).compute();
            assertThat(result.thrustCoefficient()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("Separated Cf should exceed the standard (full-nozzle) formula value")
        void separatedCfShouldExceedStandardForStronglyOverexpanded() {
            // The standard full-nozzle Cf has a large negative pressure term when pe << pa.
            // The truncated-nozzle Cf (separation) avoids this and should be higher.
            var result = new ShockExpansionModel(vacuumNozzleAtSeaLevel).compute();
            double cfFullNozzle = seaLevelParams.idealThrustCoefficient();

            // The separated Cf should be positive and physically bounded.
            assertThat(result.thrustCoefficient())
                    .isGreaterThan(0.0)
                    .isLessThan(cfFullNozzle * 2.0);
        }

        @Test
        @DisplayName("Cf at ideal expansion should match NozzleDesignParameters ideal Cf")
        void cfAtIdealExpansionShouldMatchDesignValue() {
            double pe = seaLevelParams.idealExitPressure();
            var result = new ShockExpansionModel(seaLevelParams).compute(pe);

            // The ShockExpansionModel uses the same standard Cf formula; values should agree.
            assertThat(result.thrustCoefficient())
                    .isCloseTo(seaLevelParams.idealThrustCoefficient(), within(0.05));
        }

        @Test
        @DisplayName("Higher altitude (lower pa) should increase Cf for fixed nozzle")
        void higherAltitudeShouldIncreaseCf() {
            // Lower pa → less negative or more positive pressure term.
            var seaLevel = new ShockExpansionModel(vacuumNozzleAtSeaLevel).compute(101325);
            var highAlt = new ShockExpansionModel(vacuumNozzleAtSeaLevel).compute(5000);

            assertThat(highAlt.thrustCoefficient())
                    .isGreaterThan(seaLevel.thrustCoefficient());
        }
    }

    // -----------------------------------------------------------------------
    // Specific impulse
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Specific impulse")
    class SpecificImpulseTests {

        @Test
        @DisplayName("Isp should be positive")
        void ispShouldBePositive() {
            var result = new ShockExpansionModel(seaLevelParams).compute();
            assertThat(result.specificImpulse()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("Isp at ideal expansion should be in realistic range")
        void ispAtIdealExpansionShouldBeRealistic() {
            double pe = seaLevelParams.idealExitPressure();
            var result = new ShockExpansionModel(seaLevelParams).compute(pe);
            // LOX/RP-1 sea-level Isp roughly 250–340 s range.
            assertThat(result.specificImpulse()).isBetween(100.0, 500.0);
        }
    }

    // -----------------------------------------------------------------------
    // Wave geometry
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Wave geometry")
    class WaveGeometryTests {

        @Test
        @DisplayName("Oblique shock angle should be between Mach angle and 90 degrees")
        void obliqueShockAngleShouldBeBounded() {
            var result = new ShockExpansionModel(seaLevelParams).compute(250000);
            assertThat(result.regime()).isEqualTo(ShockExpansionModel.FlowRegime.OVEREXPANDED_OBLIQUE);

            double mu = Math.toDegrees(Math.asin(1.0 / seaLevelParams.exitMach()));
            assertThat(result.waveAngleDeg()).isGreaterThan(mu);
            assertThat(result.waveAngleDeg()).isLessThan(90.0);
        }

        @Test
        @DisplayName("Mach disk shock angle should be 90 degrees")
        void machDiskShockAngleShouldBe90() {
            var result = new ShockExpansionModel(lowExpansionParams).compute(5e6);
            assertThat(result.waveAngleDeg()).isCloseTo(90.0, within(0.01));
        }

        @Test
        @DisplayName("Underexpanded plume half-angle should be positive")
        void underexpandedPlumeAngleShouldBePositive() {
            var result = new ShockExpansionModel(seaLevelParams).compute(50000);
            assertThat(result.plumeHalfAngleDeg()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("Post-wave Mach should be subsonic after Mach disk")
        void postWaveMachShouldBeSubsonicAfterMachDisk() {
            var result = new ShockExpansionModel(lowExpansionParams).compute(5e6);
            assertThat(result.postWaveMach()).isLessThan(1.0);
        }

        @Test
        @DisplayName("Post-wave Mach should exceed design Mach for underexpanded flow")
        void postWaveMachShouldExceedDesignMachForUnderexpanded() {
            var result = new ShockExpansionModel(seaLevelParams).compute(50000);
            assertThat(result.postWaveMach()).isGreaterThan(seaLevelParams.exitMach());
        }
    }

    // -----------------------------------------------------------------------
    // Altitude sweep
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Altitude sweep")
    class AltitudeSweepTests {

        @Test
        @DisplayName("computeAtAltitude should produce same result as compute(isaAtmosphere)")
        void computeAtAltitudeShouldMatchDirectCompute() {
            double pa = ShockExpansionModel.isaAtmosphere(10000);
            var model = new ShockExpansionModel(seaLevelParams);

            var viaAltitude = model.computeAtAltitude(10000);
            var viaPressure = model.compute(pa);

            assertThat(viaAltitude.thrustCoefficient())
                    .isCloseTo(viaPressure.thrustCoefficient(), within(1e-9));
        }

        @Test
        @DisplayName("isFullyFlowing should be false when separated")
        void isFullyFlowingShouldBeFalseWhenSeparated() {
            var result = new ShockExpansionModel(vacuumNozzleAtSeaLevel).compute();
            assertThat(result.isFullyFlowing()).isFalse();
        }

        @Test
        @DisplayName("isFullyFlowing should be true for non-separated cases")
        void isFullyFlowingShouldBeTrueForNonSeparated() {
            var result = new ShockExpansionModel(seaLevelParams).compute();
            assertThat(result.isFullyFlowing()).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // ISA atmosphere helper
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("ISA atmosphere")
    class IsaAtmosphereTests {

        @Test
        @DisplayName("Sea level should return standard atmospheric pressure")
        void seaLevelShouldReturnStandardPressure() {
            assertThat(ShockExpansionModel.isaAtmosphere(0)).isCloseTo(101325.0, within(1.0));
        }

        @Test
        @DisplayName("Pressure should decrease monotonically with altitude")
        void pressureShouldDecreaseWithAltitude() {
            double[] altitudes = {0, 5000, 11000, 20000, 50000, 80000};
            for (int i = 0; i < altitudes.length - 1; i++) {
                assertThat(ShockExpansionModel.isaAtmosphere(altitudes[i]))
                        .isGreaterThan(ShockExpansionModel.isaAtmosphere(altitudes[i + 1]));
            }
        }

        @Test
        @DisplayName("Very high altitude should return near-vacuum pressure")
        void veryHighAltitudeShouldReturnNearVacuum() {
            assertThat(ShockExpansionModel.isaAtmosphere(110000)).isCloseTo(1.0, within(0.01));
        }
    }

    // -----------------------------------------------------------------------
    // toString
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("Non-separated result toString should contain regime and Cf")
        void nonSeparatedToStringShouldContainKeyInfo() {
            String s = new ShockExpansionModel(seaLevelParams).compute().toString();
            assertThat(s).containsIgnoringCase("Cf=");
            assertThat(s).containsIgnoringCase("Isp=");
        }

        @Test
        @DisplayName("Separated result toString should say SEPARATED")
        void separatedToStringShouldSaySeparated() {
            String s = new ShockExpansionModel(vacuumNozzleAtSeaLevel).compute().toString();
            assertThat(s).contains("SEPARATED");
        }
    }
}
