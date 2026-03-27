package com.nozzle.moc;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AerospikeNozzle Tests")
class AerospikeNozzle_UT {

    private NozzleDesignParameters params;

    @BeforeEach
    void setUp() {
        params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(2.5)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.AIR)
                .numberOfCharLines(15)
                .wallAngleInitialDegrees(25)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();
    }

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {

        @Test
        @DisplayName("Default constructor accepts valid parameters")
        void defaultConstructorCreatesInstance() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params);

            assertThat(nozzle.getParameters()).isEqualTo(params);
            assertThat(nozzle.getSpikeRadiusRatio())
                    .isCloseTo(AerospikeNozzle.DEFAULT_SPIKE_RADIUS_RATIO, within(1e-12));
            assertThat(nozzle.getTruncationFraction())
                    .isCloseTo(AerospikeNozzle.DEFAULT_TRUNCATION_FRACTION, within(1e-12));
        }

        @Test
        @DisplayName("Full constructor stores all supplied values")
        void fullConstructorStoresValues() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params, 0.55, 0.70, 50);

            assertThat(nozzle.getSpikeRadiusRatio()).isCloseTo(0.55, within(1e-12));
            assertThat(nozzle.getTruncationFraction()).isCloseTo(0.70, within(1e-12));
        }

        @Test
        @DisplayName("spikeRadiusRatio = 0 throws IllegalArgumentException")
        void spikeRadiusRatioZeroThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new AerospikeNozzle(params, 0.0, 0.8, 50))
                    .withMessageContaining("spikeRadiusRatio");
        }

        @Test
        @DisplayName("spikeRadiusRatio = 1 throws IllegalArgumentException")
        void spikeRadiusRatioOneThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new AerospikeNozzle(params, 1.0, 0.8, 50))
                    .withMessageContaining("spikeRadiusRatio");
        }

        @Test
        @DisplayName("truncationFraction = 0 throws IllegalArgumentException")
        void truncationFractionZeroThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new AerospikeNozzle(params, 0.6, 0.0, 50))
                    .withMessageContaining("truncationFraction");
        }

        @Test
        @DisplayName("truncationFraction > 1 throws IllegalArgumentException")
        void truncationFractionAboveOneThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new AerospikeNozzle(params, 0.6, 1.1, 50))
                    .withMessageContaining("truncationFraction");
        }

        @Test
        @DisplayName("numSpikePoints < 2 throws IllegalArgumentException")
        void numSpikePointsTooSmallThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new AerospikeNozzle(params, 0.6, 0.8, 1))
                    .withMessageContaining("numSpikePoints");
        }

        @Test
        @DisplayName("generate() returns this for method chaining")
        void generateReturnsSelf() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params);
            assertThat(nozzle.generate()).isSameAs(nozzle);
        }
    }

    // -----------------------------------------------------------------------
    // Area calculations
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Area Calculation Tests")
    class AreaCalculationTests {

        @Test
        @DisplayName("Annular throat area equals π(rt² − ri²)")
        void annularThroatAreaFormula() {
            double rt = params.throatRadius();
            double ri = rt * AerospikeNozzle.DEFAULT_SPIKE_RADIUS_RATIO;
            double expected = Math.PI * (rt * rt - ri * ri);

            AerospikeNozzle nozzle = new AerospikeNozzle(params);
            assertThat(nozzle.getAnnularThroatArea()).isCloseTo(expected, within(1e-12));
        }

        @Test
        @DisplayName("Annular throat area is less than full-disk throat area")
        void annularAreaLessThanFullDisk() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params);
            double fullDisk = Math.PI * params.throatRadius() * params.throatRadius();
            assertThat(nozzle.getAnnularThroatArea()).isLessThan(fullDisk);
        }

        @Test
        @DisplayName("Annular exit area > annular throat area (supersonic expansion)")
        void exitAreaGreaterThanThroatArea() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params);
            assertThat(nozzle.getAnnularExitArea())
                    .isGreaterThan(nozzle.getAnnularThroatArea());
        }

        @Test
        @DisplayName("Exit area / throat area matches design area ratio")
        void exitToThroatAreaRatioMatchesDesign() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params);
            double ratio = nozzle.getAnnularExitArea() / nozzle.getAnnularThroatArea();
            assertThat(ratio).isCloseTo(params.exitAreaRatio(), within(1e-6));
        }
    }

    // -----------------------------------------------------------------------
    // Performance — thrust coefficient
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Thrust Coefficient Tests")
    class ThrustCoefficientTests {

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Cf at design ambient pressure is positive")
        void cfAtDesignPressureIsPositive() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params).generate();
            double cf = nozzle.calculateThrustCoefficient(params.ambientPressure());
            assertThat(cf).isGreaterThan(0.0);
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Aerospike Cf at high ambient (low altitude) >= bell nozzle Cf")
        void aeroSpikeCfAtLowAltitudeNotWorseThanBell() {
            // At pa > pe_design: Aerospike adapts, bell has over-expansion loss.
            double pa = params.chamberPressure() * 0.05;  // high ambient, M_pa < M_design
            AerospikeNozzle nozzle = new AerospikeNozzle(params).generate();

            double cfAerospike = nozzle.calculateThrustCoefficient(pa);
            double cfBell      = nozzle.calculateBellNozzleThrustCoefficient(pa);

            assertThat(cfAerospike).isGreaterThanOrEqualTo(cfBell);
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Aerospike Cf at very low altitude (pa >> pe) is still positive")
        void aeroSpikeCfAtVeryLowAltitudeIsPositive() {
            double pa = params.chamberPressure() * 0.2;
            AerospikeNozzle nozzle = new AerospikeNozzle(params).generate();
            assertThat(nozzle.calculateThrustCoefficient(pa)).isGreaterThan(0.0);
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Cf at vacuum (pa=0) > Cf at sea level (under-expanded branch)")
        void cfIncreasesWithDecreasingPressure() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params).generate();
            double cfVacuum   = nozzle.calculateThrustCoefficient(1.0);    // ~vacuum
            double cfSeaLevel = nozzle.calculateThrustCoefficient(101325); // sea level
            assertThat(cfVacuum).isGreaterThan(cfSeaLevel);
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Bell-nozzle Cf at sea level is positive")
        void bellNozzleCfAtSeaLevelIsPositive() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params).generate();
            assertThat(nozzle.calculateBellNozzleThrustCoefficient(101325)).isGreaterThan(0.0);
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Isp is positive at design altitude")
        void ispIsPositiveAtDesignAltitude() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params).generate();
            assertThat(nozzle.calculateIsp(params.ambientPressure())).isGreaterThan(0.0);
        }
    }

    // -----------------------------------------------------------------------
    // Lazy generation (getFullSpikeContour triggers generate())
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Lazy Generation Tests")
    class LazyGenerationTests {

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("getFullSpikeContour() without explicit generate() still produces contour")
        void lazyGenerationOnGetFullSpike() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params);
            // No explicit generate() call
            assertThat(nozzle.getFullSpikeContour()).isNotEmpty();
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("getTruncatedSpikeContour() without generate() still returns contour")
        void lazyGenerationOnGetTruncatedSpike() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params);
            assertThat(nozzle.getTruncatedSpikeContour()).isNotEmpty();
        }

        @Test
        @DisplayName("getFullSpikeLength() before generate() returns 0")
        void fullSpikeLengthBeforeGenerateIsZero() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params);
            assertThat(nozzle.getFullSpikeLength()).isCloseTo(0.0, within(1e-12));
        }
    }

    // -----------------------------------------------------------------------
    // Different gas properties
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Gas Properties Variation Tests")
    class GasPropertiesTests {

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("LOX/RP-1 combustion products produce a valid spike contour")
        void loxRp1SpikeContourIsValid() {
            NozzleDesignParameters rp1 = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0).chamberPressure(7e6)
                    .chamberTemperature(3500).ambientPressure(101325)
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .numberOfCharLines(15).wallAngleInitialDegrees(25)
                    .lengthFraction(0.8).build();

            AerospikeNozzle nozzle = new AerospikeNozzle(rp1).generate();
            assertThat(nozzle.getFullSpikeLength()).isGreaterThan(0.0);
            assertThat(nozzle.calculateThrustCoefficient(101325)).isGreaterThan(0.0);
        }
    }

    // -----------------------------------------------------------------------
    // Branch-coverage specifics
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Branch Coverage Tests")
    class BranchCoverageTests {

        // --- machFromChamberPressureRatio: bracket <= 0 branch (returns 1.0)

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Cf at pa = pc returns a valid value (pressure ratio = 1, sonic branch)")
        void cfAtPaEqualsToPcHandlesSonicCondition() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params).generate();
            // pa = pc → pRatio = 1 → bracket = (1)^exponent - 1 = 0 → mach = 1 (sonic)
            double cf = nozzle.calculateThrustCoefficient(params.chamberPressure());
            assertThat(cf).isGreaterThanOrEqualTo(0.0);
        }

        // --- calculateThrustCoefficient: mPa <= mExit branch (over-compressed)
        //     and mPa > mExit branch (under-expanded)
        //     The low-altitude test above covers mPa < mExit.
        //     A near-vacuum test covers mPa > mExit.

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Cf at near-vacuum (under-expanded, mPa > mExit) uses pressure-term branch")
        void cfAtNearVacuumUsesUnderExpandedBranch() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params).generate();
            // pa very small → M_pa very large > M_exit=2.5 → under-expanded branch
            double cf = nozzle.calculateThrustCoefficient(500.0);
            assertThat(cf).isGreaterThan(0.0);
        }

    }
}
