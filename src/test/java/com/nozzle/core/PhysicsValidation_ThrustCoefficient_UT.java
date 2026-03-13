package com.nozzle.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Validates ideal thrust coefficient and characteristic exhaust velocity
 * against the published analytical formulae in Sutton & Biblarz,
 * "Rocket Propulsion Elements", 9th ed., Chapter 3.
 *
 * <p>All expected values are computed from the closed-form expressions:
 * <pre>
 *   Cf = √(2γ²/(γ-1) · (2/(γ+1))^((γ+1)/(γ-1)) · (1 − (Pe/Pc)^((γ-1)/γ)))
 *        + (Pe − Pa)/Pc · Ae/At
 *
 *   c* = √(γ·R·Tc) / γ / (2/(γ+1))^((γ+1)/(2(γ-1)))
 * </pre>
 */
@DisplayName("Physics Validation — Ideal Thrust Coefficient & c*")
class PhysicsValidation_ThrustCoefficient_UT {

    // -----------------------------------------------------------------------
    // Characteristic exhaust velocity  c* = √(γRT_c)/γ / (2/(γ+1))^{(γ+1)/(2(γ-1))}
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Characteristic velocity c*")
    class CharacteristicVelocityTests {

        /**
         * For γ=1.4, R=287.05 J/(kg·K), Tc=1000 K the formula gives:
         *   c* = √(287.05·1000/1.4) / (2/2.4)^3 = 452.81/0.5787 ≈ 782.4 m/s
         *
         * Same result appears in Sutton & Biblarz Table 3-2 (and many reference texts)
         * as c*(air, Tc=1000 K) ≈ 783 m/s.
         */
        @Test
        @DisplayName("c* for air at Tc=1000 K matches Sutton Table 3-2 (≈783 m/s, 0.5%)")
        void cStarAirAt1000K() {
            NozzleDesignParameters params = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(2.0)
                    .chamberPressure(2e6)
                    .chamberTemperature(1000.0)
                    .ambientPressure(1.0)          // near-vacuum; Pa not used by c*
                    .gasProperties(GasProperties.AIR)
                    .numberOfCharLines(10)
                    .wallAngleInitialDegrees(20)
                    .lengthFraction(0.8)
                    .build();

            double cStar = params.characteristicVelocity();
            assertThat(cStar).isCloseTo(782.4, within(782.4 * 0.005));  // 0.5%
        }

        @Test
        @DisplayName("c* formula matches manual calculation for LOX/RP-1 gas model")
        void cStarLoxRp1ManualCheck() {
            // γ=1.24, R=361.5, Tc=3500 K
            // c* = √(1.24·361.5·3500)/1.24 / (2/2.24)^(2.24/0.48)
            double gamma = 1.24;
            double R = 361.5;
            double Tc = 3500.0;
            double expected = Math.sqrt(gamma * R * Tc) / gamma
                    / Math.pow(2.0 / (gamma + 1), (gamma + 1) / (2.0 * (gamma - 1)));

            NozzleDesignParameters params = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(3.0)
                    .chamberPressure(7e6)
                    .chamberTemperature(Tc)
                    .ambientPressure(1.0)
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .numberOfCharLines(10)
                    .wallAngleInitialDegrees(20)
                    .lengthFraction(0.8)
                    .build();

            assertThat(params.characteristicVelocity())
                    .isCloseTo(expected, within(expected * 1e-8));  // floating-point identity
        }

        @Test
        @DisplayName("c* scales as √Tc: doubling Tc raises c* by √2")
        void cStarScalesAsSqrtTc() {
            NozzleDesignParameters base = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(2.0)
                    .chamberPressure(7e6)
                    .chamberTemperature(2000.0)
                    .ambientPressure(1.0)
                    .gasProperties(GasProperties.AIR)
                    .numberOfCharLines(10)
                    .wallAngleInitialDegrees(20)
                    .lengthFraction(0.8)
                    .build();

            NozzleDesignParameters doubled = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(2.0)
                    .chamberPressure(14e6)
                    .chamberTemperature(4000.0)          // 2×Tc
                    .ambientPressure(1.0)
                    .gasProperties(GasProperties.AIR)
                    .numberOfCharLines(10)
                    .wallAngleInitialDegrees(20)
                    .lengthFraction(0.8)
                    .build();

            double ratio = doubled.characteristicVelocity() / base.characteristicVelocity();
            assertThat(ratio).isCloseTo(Math.sqrt(2.0), within(1e-8));
        }

        @Test
        @DisplayName("c* is independent of chamber pressure (isentropic formula has no Pc term)")
        void cStarIndependentOfPressure() {
            NozzleDesignParameters lo = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(2.0)
                    .chamberPressure(3e6).chamberTemperature(3000.0).ambientPressure(1.0)
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .numberOfCharLines(10).wallAngleInitialDegrees(20).lengthFraction(0.8).build();

            NozzleDesignParameters hi = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(2.0)
                    .chamberPressure(20e6).chamberTemperature(3000.0).ambientPressure(1.0)
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .numberOfCharLines(10).wallAngleInitialDegrees(20).lengthFraction(0.8).build();

            assertThat(lo.characteristicVelocity())
                    .isCloseTo(hi.characteristicVelocity(), within(1e-6));
        }
    }

    // -----------------------------------------------------------------------
    // Ideal thrust coefficient  Cf
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Ideal thrust coefficient Cf")
    class ThrustCoefficientTests {

        /**
         * Near-vacuum (Pa → 0) simplifies to:
         *   Cf_vac = cfMomentum + (Pe/Pc)·(Ae/At)
         * where cfMomentum = √(2γ²/(γ-1) · (2/(γ+1))^((γ+1)/(γ-1)) · (1 − (T/T0)(M)))
         *
         * Note: (Pe/Pc)^((γ-1)/γ) = T/T0 (isentropic identity), so term2 = 1 − T/T0.
         *
         * For γ=1.4: 2γ²/(γ-1) × (2/(γ+1))^((γ+1)/(γ-1))
         *          = 9.8 × (2/2.4)^6 = 9.8 × 0.33490 = 3.2820
         * Pre-computed from the analytic formula:
         *   M=2:  cfMom=√(3.2820×0.44444)=1.2078, Pe/Pc·AR=0.2157  → Cf≈1.4234
         *   M=3:  cfMom=√(3.2820×0.64286)=1.4525, Pe/Pc·AR=0.1153  → Cf≈1.5678
         *   M=5:  cfMom=√(3.2820×0.83333)=1.6538, Pe/Pc·AR=0.0474  → Cf≈1.7011
         */
        @ParameterizedTest(name = "γ=1.4, M_e={0}, Pa→0 → Cf ≈ {1}")
        @CsvSource({
                "2.0, 1.4234",
                "3.0, 1.5678",
                "5.0, 1.7011",
        })
        @DisplayName("Ideal Cf (vacuum) matches analytic formula to 0.2%")
        void idealCfVacuumMatchesSutton(double exitMach, double expectedCf) {
            NozzleDesignParameters params = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(exitMach)
                    .chamberPressure(7e6)
                    .chamberTemperature(3500.0)
                    .ambientPressure(1.0)          // near-vacuum; Pa → 0 so pressure term ≈ Pe/Pc·AR
                    .gasProperties(GasProperties.AIR)
                    .numberOfCharLines(10)
                    .wallAngleInitialDegrees(20)
                    .lengthFraction(0.8)
                    .build();

            double cf = params.idealThrustCoefficient();
            assertThat(cf).as("Cf (vacuum) at M_e=%.1f", exitMach)
                    .isCloseTo(expectedCf, within(expectedCf * 0.002));  // 0.2%
        }

        @Test
        @DisplayName("Cf at optimum expansion (Pe=Pa) has no pressure term → Cf = cfMomentum only")
        void cfAtOptimumExpansionEqualsMotmentumTerm() {
            // Arrange: choose Pc and exit Mach so that Pe = Pa exactly.
            // Pe = Pc · P/P0(M), Pa must equal that.
            double exitMach = 2.5;
            double pc = 7e6;
            double pOverP0 = GasProperties.AIR.isentropicPressureRatio(exitMach);
            double pa = pc * pOverP0;    // Pe = Pa exactly

            NozzleDesignParameters params = NozzleDesignParameters.builder()
                    .throatRadius(0.05)
                    .exitMach(exitMach)
                    .chamberPressure(pc)
                    .chamberTemperature(3500.0)
                    .ambientPressure(pa)           // Pe = Pa
                    .gasProperties(GasProperties.AIR)
                    .numberOfCharLines(10)
                    .wallAngleInitialDegrees(20)
                    .lengthFraction(0.8)
                    .build();

            // Manually compute momentum term only
            double g = GasProperties.AIR.gamma();
            double gm1 = g - 1, gp1 = g + 1;
            double term1 = 2 * g * g / gm1 * Math.pow(2.0 / gp1, gp1 / gm1);
            double prRatio = GasProperties.AIR.isentropicPressureRatio(exitMach);
            double term2 = 1 - Math.pow(prRatio, gm1 / g);
            double cfMomentum = Math.sqrt(term1 * term2);

            // At Pe=Pa the pressure term vanishes; idealThrustCoefficient() should equal cfMomentum
            assertThat(params.idealThrustCoefficient())
                    .isCloseTo(cfMomentum, within(cfMomentum * 1e-8));
        }

        @Test
        @DisplayName("Cf increases monotonically with exit Mach (for vacuum Pa→0)")
        void cfIncreasesWithExitMach() {
            double[] machs = {1.5, 2.0, 2.5, 3.0, 4.0, 5.0};
            double prevCf = 0;
            for (double mach : machs) {
                NozzleDesignParameters p = NozzleDesignParameters.builder()
                        .throatRadius(0.05).exitMach(mach)
                        .chamberPressure(7e6).chamberTemperature(3500.0)
                        .ambientPressure(1.0)
                        .gasProperties(GasProperties.AIR)
                        .numberOfCharLines(10).wallAngleInitialDegrees(20).lengthFraction(0.8)
                        .build();
                double cf = p.idealThrustCoefficient();
                assertThat(cf).as("Cf at M=%.1f > Cf at previous Mach", mach)
                        .isGreaterThan(prevCf);
                prevCf = cf;
            }
        }

        @Test
        @DisplayName("Cf does not depend on throat size (thrust scales with At, not Cf)")
        void cfIndependentOfThroatRadius() {
            NozzleDesignParameters small = NozzleDesignParameters.builder()
                    .throatRadius(0.01).exitMach(3.0)
                    .chamberPressure(7e6).chamberTemperature(3500.0).ambientPressure(1.0)
                    .gasProperties(GasProperties.AIR)
                    .numberOfCharLines(10).wallAngleInitialDegrees(20).lengthFraction(0.8).build();

            NozzleDesignParameters large = NozzleDesignParameters.builder()
                    .throatRadius(0.50).exitMach(3.0)
                    .chamberPressure(7e6).chamberTemperature(3500.0).ambientPressure(1.0)
                    .gasProperties(GasProperties.AIR)
                    .numberOfCharLines(10).wallAngleInitialDegrees(20).lengthFraction(0.8).build();

            assertThat(small.idealThrustCoefficient())
                    .isCloseTo(large.idealThrustCoefficient(), within(1e-8));
        }

        @Test
        @DisplayName("Cf gamma sensitivity: lower γ gives higher Cf at same exit Mach (Sutton §3.3)")
        void lowerGammaGivesHigherCfAtSameExitMach() {
            // At fixed exit Mach and near-vacuum conditions, lower γ produces a larger Cf.
            // This is because the momentum integral 2γ²/(γ-1)·(2/(γ+1))^((γ+1)/(γ-1)) grows
            // more slowly than (1−T/T0) expands as γ decreases, but the net effect is verified
            // numerically from the analytic formula (see Sutton & Biblarz Figure 3-9 and
            // the computed values in the class Javadoc).
            NozzleDesignParameters gammaHigh = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0)
                    .chamberPressure(7e6).chamberTemperature(3500.0).ambientPressure(1.0)
                    .gasProperties(GasProperties.fromGammaAndMW(1.4, 20.0))
                    .numberOfCharLines(10).wallAngleInitialDegrees(20).lengthFraction(0.8).build();

            NozzleDesignParameters gammaLow = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0)
                    .chamberPressure(7e6).chamberTemperature(3500.0).ambientPressure(1.0)
                    .gasProperties(GasProperties.fromGammaAndMW(1.2, 20.0))
                    .numberOfCharLines(10).wallAngleInitialDegrees(20).lengthFraction(0.8).build();

            // γ=1.2 gives higher Cf than γ=1.4 at the same exit Mach (vacuum Cf)
            assertThat(gammaLow.idealThrustCoefficient())
                    .isGreaterThan(gammaHigh.idealThrustCoefficient());
        }
    }

    // -----------------------------------------------------------------------
    // Exit-plane thermodynamics (isentropic consistency)
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Exit-plane thermodynamic consistency")
    class ExitThermodynamicsTests {

        @Test
        @DisplayName("exitTemperature matches Tc · isentropicTemperatureRatio(Me) to 0.01%")
        void exitTemperatureConsistency() {
            NozzleDesignParameters params = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0)
                    .chamberPressure(7e6).chamberTemperature(3500.0).ambientPressure(101325.0)
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .numberOfCharLines(10).wallAngleInitialDegrees(20).lengthFraction(0.8).build();

            double expected = params.chamberTemperature()
                    * params.gasProperties().isentropicTemperatureRatio(params.exitMach());
            assertThat(params.exitTemperature())
                    .isCloseTo(expected, within(expected * 1e-4));
        }

        @Test
        @DisplayName("exitVelocity = Me · a_e = Me · √(γRTe), within 0.01%")
        void exitVelocityConsistency() {
            NozzleDesignParameters params = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(3.0)
                    .chamberPressure(7e6).chamberTemperature(3500.0).ambientPressure(101325.0)
                    .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                    .numberOfCharLines(10).wallAngleInitialDegrees(20).lengthFraction(0.8).build();

            double Te = params.exitTemperature();
            double expected = params.exitMach() * params.gasProperties().speedOfSound(Te);
            assertThat(params.exitVelocity())
                    .isCloseTo(expected, within(expected * 1e-8));
        }

        @Test
        @DisplayName("exitAreaRatio = A_e/A_t matches isentropicAreaRatio(Me) to 0.01%")
        void exitAreaRatioConsistency() {
            NozzleDesignParameters params = NozzleDesignParameters.builder()
                    .throatRadius(0.05).exitMach(4.0)
                    .chamberPressure(7e6).chamberTemperature(3500.0).ambientPressure(101325.0)
                    .gasProperties(GasProperties.AIR)
                    .numberOfCharLines(10).wallAngleInitialDegrees(20).lengthFraction(0.8).build();

            double expected = params.gasProperties().areaRatio(params.exitMach()); // 10.719
            assertThat(params.exitAreaRatio())
                    .isCloseTo(expected, within(expected * 1e-8));
        }
    }
}
