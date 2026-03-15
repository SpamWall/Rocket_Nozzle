package com.nozzle.chemistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.within;

@DisplayName("PropellantComposition Tests")
class PropellantComposition_UT {

    private PropellantComposition comp;

    @BeforeEach
    void setUp() {
        comp = new PropellantComposition();
    }

    @Nested
    @DisplayName("Direct assignment")
    class DirectAssignmentTests {

        @Test
        @DisplayName("set() normalises fractions that do not sum to 1")
        void setShouldNormalise() {
            comp.set(Map.of("H2O", 0.5, "CO2", 0.5, "CO", 0.5));
            double sum = comp.get().values().stream().mapToDouble(Double::doubleValue).sum();
            assertThat(sum).isCloseTo(1.0, within(1e-6));
        }

        @Test
        @DisplayName("set() with empty map leaves composition empty")
        void setEmptyMapLeavesCompositionEmpty() {
            comp.set(Map.of());
            assertThat(comp.isEmpty()).isTrue();
            assertThat(comp.get()).isEmpty();
        }

        @Test
        @DisplayName("isEmpty() returns true before any species are added")
        void isEmptyTrueOnFreshInstance() {
            assertThat(comp.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("isEmpty() returns false after set()")
        void isEmptyFalseAfterSet() {
            comp.set(Map.of("H2O", 1.0));
            assertThat(comp.isEmpty()).isFalse();
        }
    }

    @Nested
    @DisplayName("LOX/RP-1 composition presets")
    class LoxRp1Tests {

        @Test
        @DisplayName("Near-stoichiometric (MR 2.0–2.8) contains CO2 and H2O")
        void nearStoichiometricContainsCo2AndH2o() {
            comp.setLoxRp1(2.5);
            Map<String, Double> fractions = comp.get();
            assertThat(fractions).isNotEmpty();
            assertThat(fractions.values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
        }

        @Test
        @DisplayName("Fuel-rich (MR < 2.0) contains CO")
        void fuelRichContainsCo() {
            comp.setLoxRp1(1.5);
            assertThat(comp.get()).containsKey("CO");
            assertThat(comp.get().values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
        }

        @Test
        @DisplayName("Oxidizer-rich (MR >= 2.8) contains O2")
        void oxidizerRichContainsO2() {
            comp.setLoxRp1(3.5);
            assertThat(comp.get()).containsKey("O2");
            assertThat(comp.get().values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
        }
    }

    @Nested
    @DisplayName("LOX/CH4 composition presets")
    class LoxCh4Tests {

        @Test
        @DisplayName("Near-stoichiometric (MR 3.0–4.0) contains H2O and CO2")
        void nearStoichiometricContainsH2oAndCo2() {
            comp.setLoxCh4(3.5);
            assertThat(comp.get()).containsKey("H2O");
            assertThat(comp.get()).containsKey("CO2");
            assertThat(comp.get().values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
        }

        @Test
        @DisplayName("Fuel-rich (MR < 3.0) contains CO and H")
        void fuelRichContainsCoAndH() {
            comp.setLoxCh4(2.0);
            assertThat(comp.get()).containsKey("CO");
            assertThat(comp.get()).containsKey("H");
            assertThat(comp.get().values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
        }

        @Test
        @DisplayName("Oxidizer-rich (MR >= 4.0) contains O2")
        void oxidizerRichContainsO2() {
            comp.setLoxCh4(5.0);
            assertThat(comp.get()).containsKey("O2");
            assertThat(comp.get().values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
        }
    }

    @Nested
    @DisplayName("LOX/LH2 composition presets")
    class LoxLh2Tests {

        @Test
        @DisplayName("Near-stoichiometric (MR 5.0–7.0) contains H2O")
        void nearStoichiometricContainsH2o() {
            comp.setLoxLh2(6.0);
            assertThat(comp.get()).containsKey("H2O");
            assertThat(comp.get().values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
        }

        @Test
        @DisplayName("Fuel-rich (MR < 5.0) contains H2")
        void fuelRichContainsH2() {
            comp.setLoxLh2(4.0);
            assertThat(comp.get()).containsKey("H2");
            assertThat(comp.get().values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
        }

        @Test
        @DisplayName("Oxidizer-rich (MR >= 7.0) contains O2")
        void oxidizerRichContainsO2() {
            comp.setLoxLh2(8.0);
            assertThat(comp.get()).containsKey("O2");
            assertThat(comp.get().values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-6));
        }
    }
}
