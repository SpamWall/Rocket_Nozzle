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

@DisplayName("AltitudePerformance Tests")
class AltitudePerformance_UT {

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
    // Record construction
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Record Construction Tests")
    class RecordConstructionTests {

        @Test
        @DisplayName("Constructor stores all array fields")
        void constructorStoresAllFields() {
            double[] pa   = {101325, 50000};
            double[] acf  = {1.80, 1.90};
            double[] bcf  = {1.70, 1.90};
            double[] aisp = {320.0, 340.0};

            AltitudePerformance perf = new AltitudePerformance(pa, acf, bcf, aisp);

            assertThat(perf.ambientPressures()).isEqualTo(pa);
            assertThat(perf.aerospikeCf()).isEqualTo(acf);
            assertThat(perf.bellNozzleCf()).isEqualTo(bcf);
            assertThat(perf.aerospikeIsp()).isEqualTo(aisp);
        }
    }

    // -----------------------------------------------------------------------
    // indexOfMaxAltitudeAdvantage
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("indexOfMaxAltitudeAdvantage Tests")
    class IndexOfMaxAltitudeAdvantageTests {

        @Test
        @DisplayName("Returns index of the entry with the greatest Aerospike advantage")
        void returnsIndexOfMaxAdvantage() {
            // advantage = aerospikeCf - bellNozzleCf: [0.1, 0.3, 0.2] → max at index 1
            AltitudePerformance perf = new AltitudePerformance(
                    new double[]{101325, 50000, 10000},
                    new double[]{1.8, 2.0, 1.9},
                    new double[]{1.7, 1.7, 1.7},
                    new double[]{320, 340, 330});

            assertThat(perf.indexOfMaxAltitudeAdvantage()).isEqualTo(1);
        }

        @Test
        @DisplayName("Single-element array returns index 0")
        void singleElementReturnsZero() {
            AltitudePerformance perf = new AltitudePerformance(
                    new double[]{101325},
                    new double[]{1.8},
                    new double[]{1.7},
                    new double[]{320});

            assertThat(perf.indexOfMaxAltitudeAdvantage()).isZero();
        }

        @Test
        @DisplayName("Equal advantages across all entries returns a valid index")
        void equalAdvantagesReturnsValidIndex() {
            AltitudePerformance perf = new AltitudePerformance(
                    new double[]{101325, 50000, 10000},
                    new double[]{1.8, 1.8, 1.8},
                    new double[]{1.7, 1.7, 1.7},
                    new double[]{320, 320, 320});

            assertThat(perf.indexOfMaxAltitudeAdvantage()).isBetween(0, 2);
        }
    }

    // -----------------------------------------------------------------------
    // averageAltitudeAdvantage
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("averageAltitudeAdvantage Tests")
    class AverageAltitudeAdvantageTests {

        @Test
        @DisplayName("Computes mean of (aerospikeCf - bellNozzleCf)")
        void computesMeanAdvantage() {
            // advantages: 0.1, 0.3 → mean = 0.2
            AltitudePerformance perf = new AltitudePerformance(
                    new double[]{101325, 50000},
                    new double[]{1.8, 2.0},
                    new double[]{1.7, 1.7},
                    new double[]{320, 340});

            assertThat(perf.averageAltitudeAdvantage()).isCloseTo(0.2, within(1e-12));
        }

        @Test
        @DisplayName("Zero advantage when Aerospike Cf equals bell Cf at all altitudes")
        void zeroAdvantageWhenCfsAreEqual() {
            AltitudePerformance perf = new AltitudePerformance(
                    new double[]{101325, 50000},
                    new double[]{1.8, 1.9},
                    new double[]{1.8, 1.9},
                    new double[]{320, 340});

            assertThat(perf.averageAltitudeAdvantage()).isCloseTo(0.0, within(1e-12));
        }

        @Test
        @DisplayName("Single-element average equals the single advantage value")
        void singleElementAverageEqualsItsAdvantage() {
            AltitudePerformance perf = new AltitudePerformance(
                    new double[]{101325},
                    new double[]{1.8},
                    new double[]{1.6},
                    new double[]{320});

            assertThat(perf.averageAltitudeAdvantage()).isCloseTo(0.2, within(1e-12));
        }
    }

    // -----------------------------------------------------------------------
    // Integration with AerospikeNozzle.calculateAltitudePerformance
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("calculateAltitudePerformance returns arrays of matching length")
        void altitudePerformanceArrayLengthsMatch() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params).generate();
            double[] pressures = {101325, 50000, 20000, 5000, 1000};

            AltitudePerformance perf = nozzle.calculateAltitudePerformance(pressures);

            assertThat(perf.ambientPressures()).hasSize(pressures.length);
            assertThat(perf.aerospikeCf()).hasSize(pressures.length);
            assertThat(perf.bellNozzleCf()).hasSize(pressures.length);
            assertThat(perf.aerospikeIsp()).hasSize(pressures.length);
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Aerospike Cf advantage is greatest at high ambient pressure (low altitude)")
        void aerospikeAdvantageGreatestAtHighAmbientPressure() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params).generate();
            double paLow  = 101325.0;
            double paHigh = 1000.0;

            double advantageLow  = nozzle.calculateThrustCoefficient(paLow)
                    - nozzle.calculateBellNozzleThrustCoefficient(paLow);
            double advantageHigh = nozzle.calculateThrustCoefficient(paHigh)
                    - nozzle.calculateBellNozzleThrustCoefficient(paHigh);

            assertThat(advantageLow).isGreaterThanOrEqualTo(advantageHigh);
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("indexOfMaxAltitudeAdvantage returns valid index")
        void indexOfMaxAltitudeAdvantageIsValid() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params).generate();
            double[] pressures = {101325, 80000, 50000, 20000, 5000};

            AltitudePerformance perf = nozzle.calculateAltitudePerformance(pressures);

            assertThat(perf.indexOfMaxAltitudeAdvantage()).isBetween(0, pressures.length - 1);
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("averageAltitudeAdvantage is non-negative for Aerospike vs bell")
        void averageAltitudeAdvantageNonNegative() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params).generate();
            double[] pressures = {101325, 80000, 50000, 20000, 5000, 1000};

            AltitudePerformance perf = nozzle.calculateAltitudePerformance(pressures);

            assertThat(perf.averageAltitudeAdvantage()).isGreaterThanOrEqualTo(0.0);
        }

        @Test
        @DisplayName("calculateAltitudePerformance throws for null input")
        void throwsForNullPressureArray() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params);
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> nozzle.calculateAltitudePerformance(null));
        }

        @Test
        @DisplayName("calculateAltitudePerformance throws for empty array")
        void throwsForEmptyPressureArray() {
            AerospikeNozzle nozzle = new AerospikeNozzle(params);
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> nozzle.calculateAltitudePerformance(new double[0]));
        }
    }
}
