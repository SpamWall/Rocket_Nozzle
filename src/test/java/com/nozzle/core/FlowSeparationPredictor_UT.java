package com.nozzle.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FlowSeparationPredictor Tests")
class FlowSeparationPredictor_UT {

    // -----------------------------------------------------------------------
    // Shared fixtures
    // -----------------------------------------------------------------------

    /** Sea-level-optimised nozzle: moderately expanded, pe > pa → no separation. */
    private NozzleDesignParameters seaLevelParams;

    /**
     * Vacuum-optimized bell nozzle operated at sea level: strongly overexpanded,
     * pe << pa → separation expected.
     */
    private NozzleDesignParameters vacuumNozzleAtSeaLevel;

    @BeforeEach
    void setUp() {
        // LOX/RP-1, Pc = 7 MPa, Tc = 3500 K, sea-level optimized (Me = 3)
        // pe ≈ 160 kPa > pa = 101 kPa → slightly underexpanded, no separation.
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

        // Same engine, high-expansion vacuum nozzle (Me = 6) run at sea level.
        // pe << pa → strongly overexpanded → flow separation guaranteed.
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
            assertThat(new FlowSeparationPredictor(seaLevelParams)).isNotNull();
        }

        @Test
        @DisplayName("Should reject null parameters")
        void shouldRejectNullParameters() {
            assertThatThrownBy(() -> new FlowSeparationPredictor(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // -----------------------------------------------------------------------
    // No-separation cases
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("No-separation cases")
    class NoSeparationTests {

        @Test
        @DisplayName("Sea-level-optimised nozzle should not separate (Summerfield)")
        void seaLevelNozzleShouldNotSeparateSummerfield() {
            var result = new FlowSeparationPredictor(seaLevelParams).predict();

            assertThat(result.separated()).isFalse();
            assertThat(result.mode()).isEqualTo(FlowSeparationPredictor.SeparationMode.NO_SEPARATION);
            assertThat(result.estimatedSideLoadN()).isZero();
        }

        @Test
        @DisplayName("Sea-level-optimised nozzle should not separate (Schilling)")
        void seaLevelNozzleShouldNotSeparateSchilling() {
            var result = new FlowSeparationPredictor(seaLevelParams)
                    .predict(FlowSeparationPredictor.Criterion.SCHILLING);

            assertThat(result.separated()).isFalse();
        }

        @Test
        @DisplayName("Sea-level-optimised nozzle should not separate (Romine)")
        void seaLevelNozzleShouldNotSeparateRomine() {
            var result = new FlowSeparationPredictor(seaLevelParams)
                    .predict(FlowSeparationPredictor.Criterion.ROMINE);

            assertThat(result.separated()).isFalse();
        }

        @Test
        @DisplayName("No-separation result should have NaN spatial fields")
        void noSeparationResultShouldHaveNaNSpatialFields() {
            var result = new FlowSeparationPredictor(seaLevelParams).predict();

            assertThat(result.separationMach()).isNaN();
            assertThat(result.separationAreaRatio()).isNaN();
            assertThat(result.separationAxialFraction()).isNaN();
        }
    }

    // -----------------------------------------------------------------------
    // Separation cases
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Separation cases")
    class SeparationTests {

        @Test
        @DisplayName("Vacuum nozzle at sea level should separate (Summerfield)")
        void vacuumNozzleShouldSeparateSummerfield() {
            var result = new FlowSeparationPredictor(vacuumNozzleAtSeaLevel).predict();

            assertThat(result.separated()).isTrue();
        }

        @Test
        @DisplayName("Vacuum nozzle at sea level should separate (Schilling)")
        void vacuumNozzleShouldSeparateSchilling() {
            var result = new FlowSeparationPredictor(vacuumNozzleAtSeaLevel)
                    .predict(FlowSeparationPredictor.Criterion.SCHILLING);

            assertThat(result.separated()).isTrue();
        }

        @Test
        @DisplayName("Vacuum nozzle at sea level should separate (Romine)")
        void vacuumNozzleShouldSeparateRomine() {
            var result = new FlowSeparationPredictor(vacuumNozzleAtSeaLevel)
                    .predict(FlowSeparationPredictor.Criterion.ROMINE);

            assertThat(result.separated()).isTrue();
        }

        @Test
        @DisplayName("Separation Mach should be less than design exit Mach")
        void separationMachShouldBeLessThanExitMach() {
            var result = new FlowSeparationPredictor(vacuumNozzleAtSeaLevel).predict();

            assertThat(result.separationMach()).isGreaterThan(1.0);
            assertThat(result.separationMach()).isLessThan(vacuumNozzleAtSeaLevel.exitMach());
        }

        @Test
        @DisplayName("Separation area ratio should be between 1 and exit area ratio")
        void separationAreaRatioShouldBeBounded() {
            var result = new FlowSeparationPredictor(vacuumNozzleAtSeaLevel).predict();

            assertThat(result.separationAreaRatio()).isGreaterThan(1.0);
            assertThat(result.separationAreaRatio())
                    .isLessThan(vacuumNozzleAtSeaLevel.exitAreaRatio());
        }

        @Test
        @DisplayName("Axial fraction should be between 0 and 1")
        void axialFractionShouldBeBounded() {
            var result = new FlowSeparationPredictor(vacuumNozzleAtSeaLevel).predict();

            assertThat(result.separationAxialFraction()).isBetween(0.0, 1.0);
        }

        @Test
        @DisplayName("Summerfield separation pressure should equal 37.4% of ambient")
        void summerfieldSeparationPressureShouldEqualThreshold() {
            var result = new FlowSeparationPredictor(vacuumNozzleAtSeaLevel).predict();

            double expected = 0.374 * vacuumNozzleAtSeaLevel.ambientPressure();
            assertThat(result.separationPressurePa()).isCloseTo(expected, within(1.0));
        }

        @Test
        @DisplayName("Separation pressure should be less than ambient pressure")
        void separationPressureShouldBeLessThanAmbient() {
            for (var criterion : FlowSeparationPredictor.Criterion.values()) {
                var result = new FlowSeparationPredictor(vacuumNozzleAtSeaLevel).predict(criterion);
                if (result.separated()) {
                    assertThat(result.separationPressurePa())
                            .as("criterion=%s", criterion)
                            .isLessThan(vacuumNozzleAtSeaLevel.ambientPressure());
                }
            }
        }

        @Test
        @DisplayName("Side load should be positive when separation occurs")
        void sideLoadShouldBePositiveWhenSeparated() {
            var result = new FlowSeparationPredictor(vacuumNozzleAtSeaLevel).predict();

            assertThat(result.estimatedSideLoadN()).isGreaterThan(0.0);
        }
    }

    // -----------------------------------------------------------------------
    // Mode classification
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Separation mode classification")
    class ModeClassificationTests {

        @Test
        @DisplayName("Strongly overexpanded nozzle should produce FSS")
        void stronglyOverexpandedShouldProduceFSS() {
            // Me = 6 at sea level → separation occurs far from exit → FSS
            var result = new FlowSeparationPredictor(vacuumNozzleAtSeaLevel).predict();

            assertThat(result.mode()).isEqualTo(FlowSeparationPredictor.SeparationMode.FSS);
        }

        @Test
        @DisplayName("Mildly overexpanded nozzle should produce RSS")
        void mildlyOverexpandedShouldProduceRSS() {
            // Design a nozzle that is just barely separated so M_sep is close to M_exit → RSS.
            // pa = 101325, pSep_Summerfield = 0.374 × 101325 ≈ 37,895 Pa
            // Need pe < 37,895 Pa.  With LOX/RP1 (γ=1.24):
            //   pe = 7e6 × (1 + 0.12 Me²)^(-5.167)
            //   At Me = 3.9: pe ≈ 32,900 Pa < pSep ✓
            //   M_sep ≈ 3.81 (where isoP = pSep/p0), M_sep/Me ≈ 0.98 >> 0.70 → RSS ✓
            NozzleDesignParameters mildlyOverexpanded = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(3.9)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .numberOfCharLines(20)
                    .wallAngleInitialDegrees(30)
                    .lengthFraction(0.8)
                    .build();

            var result = new FlowSeparationPredictor(mildlyOverexpanded).predict();

            // Should be separated and close to exit → RSS
            assertThat(result.separated()).isTrue();
            assertThat(result.mode()).isEqualTo(FlowSeparationPredictor.SeparationMode.RSS);
        }
    }

    // -----------------------------------------------------------------------
    // Physical consistency
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Physical consistency")
    class PhysicalConsistencyTests {

        @Test
        @DisplayName("Higher ambient pressure should increase likelihood of separation")
        void higherAmbientPressureShouldIncreaseSeparationLikelihood() {
            // Build a nozzle that is borderline at 50 kPa but separated at 101 kPa.
            NozzleDesignParameters highAltitude = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(4.5)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500)
                    .ambientPressure(50000)
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .numberOfCharLines(20)
                    .wallAngleInitialDegrees(30)
                    .lengthFraction(0.8)
                    .build();

            NozzleDesignParameters seaLevel = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(4.5)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .numberOfCharLines(20)
                    .wallAngleInitialDegrees(30)
                    .lengthFraction(0.8)
                    .build();

            var resultHighAlt = new FlowSeparationPredictor(highAltitude).predict();
            var resultSeaLevel = new FlowSeparationPredictor(seaLevel).predict();

            // Sea-level result should be either more separated or equally separated.
            // Both may be separated, but sea-level separation Mach should be higher
            // (separation closer to throat) or sea-level should be separated while
            // high-altitude is not.
            if (resultHighAlt.separated() && resultSeaLevel.separated()) {
                // At sea level the separation front is further upstream (lower M_sep).
                assertThat(resultSeaLevel.separationMach())
                        .isLessThanOrEqualTo(resultHighAlt.separationMach());
            } else {
                assertThat(resultSeaLevel.separated()).isTrue();
            }
        }

        @Test
        @DisplayName("Higher chamber pressure should reduce separation at fixed ambient")
        void higherChamberPressureShouldReduceSeparation() {
            // Use Me = 5.0 so that Pc determines whether pe exceeds pSep.
            // pSep_Summerfield = 0.374 × 101325 ≈ 37,895 Pa
            // With γ=1.24:  isoP(5.0) = (1 + 0.12×25)^(-5.167) ≈ 7.80e-4
            //   Pc = 7 MPa:  pe ≈  5,460 Pa < pSep → separated
            //   Pc = 70 MPa: pe ≈ 54,600 Pa > pSep → NOT separated
            NozzleDesignParameters basePc = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(5.0)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .numberOfCharLines(20)
                    .wallAngleInitialDegrees(30)
                    .lengthFraction(0.8)
                    .build();

            NozzleDesignParameters highPc = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(5.0)
                    .chamberPressure(70e6)   // 10× higher Pc
                    .chamberTemperature(3500)
                    .ambientPressure(101325)
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .numberOfCharLines(20)
                    .wallAngleInitialDegrees(30)
                    .lengthFraction(0.8)
                    .build();

            var baseResult = new FlowSeparationPredictor(basePc).predict();
            var highPcResult = new FlowSeparationPredictor(highPc).predict();

            assertThat(baseResult.separated()).isTrue();
            assertThat(highPcResult.separated()).isFalse();
        }

        @Test
        @DisplayName("All criteria should agree on strongly overexpanded case")
        void allCriteriaShouldAgreeOnStronglyOverexpandedCase() {
            for (var criterion : FlowSeparationPredictor.Criterion.values()) {
                var result = new FlowSeparationPredictor(vacuumNozzleAtSeaLevel).predict(criterion);
                assertThat(result.separated())
                        .as("criterion=%s should predict separation", criterion)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("All criteria should agree on clearly attached case")
        void allCriteriaShouldAgreeOnClearlyAttachedCase() {
            for (var criterion : FlowSeparationPredictor.Criterion.values()) {
                var result = new FlowSeparationPredictor(seaLevelParams).predict(criterion);
                assertThat(result.separated())
                        .as("criterion=%s should predict no separation", criterion)
                        .isFalse();
            }
        }
    }

    // -----------------------------------------------------------------------
    // toString
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("No-separation result toString should mention NO SEPARATION")
        void noSeparationToStringShouldMentionStatus() {
            var result = new FlowSeparationPredictor(seaLevelParams).predict();
            assertThat(result.toString()).contains("NO SEPARATION");
        }

        @Test
        @DisplayName("Separated result toString should contain key fields")
        void separatedResultToStringShouldContainKeyFields() {
            var result = new FlowSeparationPredictor(vacuumNozzleAtSeaLevel).predict();
            String s = result.toString();
            assertThat(s).contains("Separation Mach");
            assertThat(s).contains("side load");
        }
    }
}
