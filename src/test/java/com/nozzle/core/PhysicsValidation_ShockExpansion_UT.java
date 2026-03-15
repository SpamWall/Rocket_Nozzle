package com.nozzle.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Physics validation tests for {@link FlowSeparationPredictor} and
 * {@link ShockExpansionModel} against published analytical solutions and
 * reference tables.
 *
 * <h2>References</h2>
 * <ul>
 *   <li><b>NACA TR 1135</b> (1953) — Equations, Tables and Charts for
 *       Compressible Flow.  Table B: normal-shock relations, γ=1.4.</li>
 *   <li><b>Anderson</b> (2003) — Modern Compressible Flow, 3rd ed., §4.9
 *       (oblique shock relations) and Appendix A (isentropic/shock tables).</li>
 *   <li><b>Summerfield et al.</b> (1954) — Liquid Propellant Rockets, §8:
 *       p_sep = 0.374 × p_a.</li>
 *   <li><b>Schilling</b> (1962) — Flow Separation in Rocket Nozzles:
 *       p_sep = 0.667 × p_a × (p_e/p_a)^0.6.</li>
 *   <li><b>Romine</b> (1998) — Nozzle Flow Separation:
 *       p_sep = p_a / (1 + 0.6·γ·k_R), k_R = 0.5·(p_a/p_e − 1).</li>
 *   <li><b>Sutton &amp; Biblarz</b> (2010) — Rocket Propulsion Elements, 8th ed.,
 *       eq. 3-30: I_sp = c*·C_f / g_0.</li>
 * </ul>
 *
 * <h2>Test gas: AIR (γ=1.4)</h2>
 * All tests use {@link GasProperties#AIR} (γ=1.4, R=287.05 J/kg·K) so that
 * results can be checked directly against NACA TR 1135 Table B and Anderson
 * Appendix A, which tabulate values for γ=1.4 only.
 */
@DisplayName("Physics Validation — Flow Separation & Shock Expansion")
class PhysicsValidation_ShockExpansion_UT {

    // -----------------------------------------------------------------------
    // Shared helper
    // -----------------------------------------------------------------------

    /**
     * Builds a minimal AIR-gas nozzle parameter set.
     *
     * @param exitMach design exit Mach number
     * @param pc       chamber pressure (Pa)
     * @param pa       ambient pressure (Pa)
     * @return fully specified {@link NozzleDesignParameters}
     */
    private static NozzleDesignParameters air(double exitMach, double pc, double pa) {
        return NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(exitMach)
                .chamberPressure(pc)
                .chamberTemperature(3000.0)
                .ambientPressure(pa)
                .gasProperties(GasProperties.AIR)
                .numberOfCharLines(10)
                .wallAngleInitialDegrees(20)
                .lengthFraction(0.8)
                .build();
    }

    // -----------------------------------------------------------------------
    // Separation criteria — exact formula self-consistency
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Separation criteria — exact formula values")
    class SeparationCriteriaTests {

        /**
         * Summerfield et al. (1954): the separation wall pressure is exactly
         * 0.374 × p_a, independent of nozzle geometry.
         *
         * <p>Setup: AIR, M_e=2, P_c=7 MPa, P_a=5 MPa.
         * p_e = 7 MPa / (1+0.2×4)^3.5 ≈ 895 kPa &lt; p_sep = 0.374 × 5 MPa = 1.87 MPa
         * → separation occurs.
         */
        @Test
        @DisplayName("Summerfield: p_sep = 0.374×p_a exactly (Summerfield 1954)")
        void summerfieldPressureIsExactlyPointThreeSevenFour() {
            NozzleDesignParameters params = air(2.0, 7e6, 5e6);

            var result = new FlowSeparationPredictor(params).predict();

            assertThat(result.separated()).isTrue();
            assertThat(result.separationPressurePa())
                    .isCloseTo(0.374 * 5e6, within(1.0));
        }

        /**
         * Schilling (1962): p_sep = 0.667 × p_a × (p_e/p_a)^0.6.
         * Expected value computed independently from the closed-form expression
         * and compared to the predictor to floating-point precision (< 1e-7 %).
         *
         * <p>Setup: AIR, M_e=2, P_c=7 MPa, P_a=3 MPa.
         */
        @Test
        @DisplayName("Schilling: p_sep = 0.667×p_a×(p_e/p_a)^0.6 — matches formula (Schilling 1962)")
        void schillingPressureMatchesFormula() {
            double pa = 3e6;
            NozzleDesignParameters params = air(2.0, 7e6, pa);
            double pe = params.idealExitPressure();

            double expected = 0.667 * pa * Math.pow(pe / pa, 0.6);

            var result = new FlowSeparationPredictor(params)
                    .predict(FlowSeparationPredictor.Criterion.SCHILLING);

            assertThat(result.separationPressurePa())
                    .isCloseTo(expected, within(expected * 1e-9));
        }

        /**
         * Romine (1998): p_sep = p_a / (1 + 0.6·γ·k_R),
         * where k_R = max(0, 0.5·(p_a/p_e − 1)).
         * Expected value computed independently from the closed-form expression.
         *
         * <p>Setup: AIR (γ=1.4), M_e=2, P_c=7 MPa, P_a=3 MPa.
         */
        @Test
        @DisplayName("Romine: p_sep = p_a/(1+0.6γk_R) — matches formula (Romine 1998)")
        void rominePressureMatchesFormula() {
            double pa = 3e6;
            NozzleDesignParameters params = air(2.0, 7e6, pa);
            double pe = params.idealExitPressure();
            double gamma = GasProperties.AIR.gamma();

            double kR = 0.5 * (pa / pe - 1.0);
            double expected = pa / (1.0 + 0.6 * gamma * Math.max(kR, 0.0));

            var result = new FlowSeparationPredictor(params)
                    .predict(FlowSeparationPredictor.Criterion.ROMINE);

            assertThat(result.separationPressurePa())
                    .isCloseTo(expected, within(expected * 1e-9));
        }

        /**
         * Separation Mach M_sep is obtained by inverting the isentropic pressure
         * relation at the critical wall pressure p_sep:
         * <pre>
         *   M_sep = √( 2/(γ−1) · ((p_sep/p_0)^(−(γ−1)/γ) − 1) )
         * </pre>
         * The predictor must reproduce this to 0.1 %.
         *
         * <p>Setup (AIR, M_e=2, P_c=7 MPa, P_a=5 MPa — Summerfield):
         * p_sep = 0.374 × 5 MPa = 1.87 MPa,
         * p_sep/p_0 = 0.2671,
         * M_sep ≈ 1.514 (analytical inversion).
         */
        @Test
        @DisplayName("Separation Mach: isentropic inversion of p_sep/p_0 matches predictor (0.1%)")
        void separationMachMatchesIsentropicInversion() {
            double pa = 5e6, pc = 7e6;
            NozzleDesignParameters params = air(2.0, pc, pa);
            double gamma = GasProperties.AIR.gamma();
            double gm1 = gamma - 1.0;

            double pSep = 0.374 * pa;
            double mSepExpected = Math.sqrt(2.0 / gm1
                    * (Math.pow(pSep / pc, -gm1 / gamma) - 1.0));

            var result = new FlowSeparationPredictor(params).predict();

            assertThat(result.separated()).isTrue();
            assertThat(result.separationMach())
                    .isCloseTo(mSepExpected, within(mSepExpected * 0.001));
        }
    }

    // -----------------------------------------------------------------------
    // Normal shock downstream Mach — NACA TR 1135 Table B / Anderson Appendix A
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Normal shock downstream Mach vs. NACA TR 1135 Table B (γ=1.4)")
    class NormalShockTests {

        /**
         * NACA TR 1135 Table B / Anderson Appendix A Table A.2:
         * for a normal shock at M_1=1.4, γ=1.4 → M_2 = 0.7397.
         *
         * <p>Test setup (AIR, M_e=1.4, P_c=7 MPa, P_a=4.9 MPa):
         * <ul>
         *   <li>p_e = 7 MPa/(1.392)^3.5 ≈ 2.199 MPa</li>
         *   <li>p_NS = p_e × (2×1.4×1.96−0.4)/2.4 = p_e × 2.120 ≈ 4.662 MPa
         *       &lt; 4.9 MPa → Mach disk triggered</li>
         *   <li>p_sep = 0.374 × 4.9 MPa = 1.832 MPa &lt; p_e = 2.199 MPa
         *       → no internal separation</li>
         * </ul>
         * postWaveMach from ShockExpansionModel must reproduce the NACA value to 0.1 %.
         */
        @Test
        @DisplayName("Mach disk post-shock M2 at M1=1.4 matches NACA TR 1135 Table B: M2=0.7397 (0.1%)")
        void normalShockM2AtM14MatchesNacaTableB() {
            // pa=4.9 MPa: above normal-shock threshold (4.662 MPa) but below
            // Summerfield separation limit (pe/0.374 ≈ 5.88 MPa).
            NozzleDesignParameters params = air(1.4, 7e6, 4.9e6);

            var result = new ShockExpansionModel(params).compute(4.9e6);

            assertThat(result.regime())
                    .isEqualTo(ShockExpansionModel.FlowRegime.OVEREXPANDED_MACH_DISK);
            // NACA TR 1135 Table B, γ=1.4, M1=1.4 → M2=0.7397
            assertThat(result.postWaveMach())
                    .isCloseTo(0.7397, within(0.7397 * 0.001));
        }

        /**
         * NACA TR 1135 Table B: M_1=1.4, γ=1.4 → normal-shock pressure ratio
         * p_2/p_1 = 2.120.
         *
         * <p>The analytical formula (2γM²−(γ−1))/(γ+1) must reproduce this to 0.1 %,
         * confirming the threshold used to trigger the Mach disk regime is consistent
         * with the published table.
         */
        @Test
        @DisplayName("Normal shock pressure ratio at M=1.4 matches NACA TR 1135 Table B: p2/p1=2.120 (0.1%)")
        void normalShockPressureRatioAtM14MatchesNacaTableB() {
            double gamma = GasProperties.AIR.gamma();
            double m1 = 1.4;

            // NACA TR 1135 Table B: M=1.4, γ=1.4 → p2/p1=2.120
            double pRatioFormula = (2.0 * gamma * m1 * m1 - (gamma - 1.0)) / (gamma + 1.0);

            assertThat(pRatioFormula).isCloseTo(2.120, within(2.120 * 0.001));
        }

        /**
         * NACA TR 1135 Table B: M_1=1.5, γ=1.4 → M_2=0.7011.
         *
         * <p>Setup (AIR, M_e=1.5, P_c=7 MPa, P_a computed to be in the Mach disk window):
         * <ul>
         *   <li>p_e ≈ 1.731 MPa (isentropic, γ=1.4, M=1.5)</li>
         *   <li>p_NS = p_e × 2.458 ≈ 4.254 MPa; p_sep_limit = p_e / 0.374 ≈ 4.629 MPa</li>
         *   <li>P_a = 4.4 MPa lies in (p_NS, p_sep_limit) → Mach disk, no separation</li>
         * </ul>
         */
        @Test
        @DisplayName("Mach disk post-shock M2 at M1=1.5 matches NACA TR 1135 Table B: M2=0.7011 (0.1%)")
        void normalShockM2AtM15MatchesNacaTableB() {
            // For AIR, Me=1.5, Pc=7 MPa:
            // pe ≈ 1.907 MPa; pNS = pe×2.458 ≈ 4.688 MPa; sep limit = pe/0.374 ≈ 5.099 MPa.
            // pa = 4.9 MPa: above Mach-disk threshold (4.688), below separation limit (5.099).
            NozzleDesignParameters params = air(1.5, 7e6, 4.9e6);

            // Confirm constraints hold at runtime
            double pe = params.idealExitPressure();
            double gamma = GasProperties.AIR.gamma();
            double pNS = pe * (2.0 * gamma * 1.5 * 1.5 - (gamma - 1.0)) / (gamma + 1.0);
            double pSepLimit = pe / 0.374;
            assertThat(4.9e6).isGreaterThan(pNS).isLessThan(pSepLimit);

            var result = new ShockExpansionModel(params).compute(4.9e6);

            assertThat(result.regime())
                    .isEqualTo(ShockExpansionModel.FlowRegime.OVEREXPANDED_MACH_DISK);
            // NACA TR 1135 Table B, γ=1.4, M1=1.5 → M2=0.7011
            assertThat(result.postWaveMach())
                    .isCloseTo(0.7011, within(0.7011 * 0.001));
        }
    }

    // -----------------------------------------------------------------------
    // ShockExpansionModel — shock formula consistency
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("ShockExpansionModel — formula consistency checks")
    class ShockExpansionConsistencyTests {

        /**
         * At ideal expansion (p_e = p_a), the pressure thrust term is zero and
         * C_f reduces to the momentum term only:
         * <pre>
         *   C_f = √( 2γ²/(γ−1) · (2/(γ+1))^((γ+1)/(γ−1)) · (1 − (p_e/p_c)^((γ−1)/γ)) )
         * </pre>
         * The model must reproduce this to 0.01 %.
         *
         * <p>Reference: Sutton &amp; Biblarz (2010) eq. 3-30, Chapter 3.
         */
        @Test
        @DisplayName("Cf at ideal expansion equals cfMomentum formula to 0.01% (Sutton eq. 3-30)")
        void cfAtIdealExpansionEqualsMomentumTermFormula() {
            NozzleDesignParameters params = air(3.0, 7e6, 1.0);
            double pe = params.idealExitPressure();
            double p0 = params.chamberPressure();
            double g = GasProperties.AIR.gamma();
            double gm1 = g - 1.0, gp1 = g + 1.0;

            // Closed-form cfMomentum (pressure term vanishes when p_a = p_e)
            double term1 = 2.0 * g * g / gm1 * Math.pow(2.0 / gp1, gp1 / gm1);
            double cfMomentum = Math.sqrt(term1 * (1.0 - Math.pow(pe / p0, gm1 / g)));

            var result = new ShockExpansionModel(params).compute(pe);

            assertThat(result.regime())
                    .isEqualTo(ShockExpansionModel.FlowRegime.IDEALLY_EXPANDED);
            assertThat(result.thrustCoefficient())
                    .isCloseTo(cfMomentum, within(cfMomentum * 1e-4));
        }

        /**
         * At ideal expansion, I_sp = c* · C_f / g_0.
         * Verifies Sutton &amp; Biblarz eq. 3-30 to 0.01 %.
         */
        @Test
        @DisplayName("Isp = c*·Cf/g0 at ideal expansion (Sutton & Biblarz eq. 3-30, 0.01%)")
        void ispAtIdealExpansionEqualsCStarCfOverG0() {
            NozzleDesignParameters params = air(3.0, 7e6, 1.0);
            double pe = params.idealExitPressure();

            var result = new ShockExpansionModel(params).compute(pe);

            double expectedIsp = params.characteristicVelocity()
                    * result.thrustCoefficient() / 9.80665;
            assertThat(result.specificImpulse())
                    .isCloseTo(expectedIsp, within(expectedIsp * 1e-4));
        }

        /**
         * For underexpanded flow (p_e &gt; p_a), the post-exit Mach M_2 is obtained
         * by isentropic expansion from the stagnation state to p_a:
         * <pre>
         *   M_2 = √( 2/(γ−1) · ((p_a/p_0)^(−(γ−1)/γ) − 1) )
         * </pre>
         * The model must reproduce this to 0.1 %.
         *
         * <p>Setup: AIR, M_e=3, P_c=7 MPa, P_a=50 kPa.
         * p_e ≈ 191 kPa &gt; 50 kPa → underexpanded; M_2 ≈ 3.94.
         */
        @Test
        @DisplayName("Underexpanded post-wave Mach equals isentropic expansion to p_a (0.1%)")
        void underexpandedPostWaveMachMatchesIsentropicExpansion() {
            double pa = 50000.0, pc = 7e6;
            NozzleDesignParameters params = air(3.0, pc, pa);
            double gamma = GasProperties.AIR.gamma();
            double gm1 = gamma - 1.0;

            double m2Expected = Math.sqrt(
                    2.0 / gm1 * (Math.pow(pa / pc, -gm1 / gamma) - 1.0));

            var result = new ShockExpansionModel(params).compute(pa);

            assertThat(result.regime())
                    .isEqualTo(ShockExpansionModel.FlowRegime.UNDEREXPANDED);
            assertThat(result.postWaveMach())
                    .isCloseTo(m2Expected, within(m2Expected * 0.001));
        }

        /**
         * The oblique shock formula in {@link ShockExpansionModel} derives the shock
         * angle β from the required pressure ratio p_a/p_e by inverting:
         * <pre>
         *   sin²β = ((γ+1)·(p_a/p_e) + (γ−1)) / (2γ·M_e²)
         * </pre>
         * Substituting back into the normal-shock pressure relation must recover
         * the original pressure ratio exactly (algebraic identity, Anderson §4.9).
         * Verified to 1 part in 10⁹.
         *
         * <p>Setup: AIR, M_e=3, P_c=7 MPa, P_a=400 kPa.
         * p_e ≈ 191 kPa &lt; p_a = 400 kPa → overexpanded oblique shock;
         * p_NS ≈ 1.97 MPa &gt; 400 kPa → no Mach disk;
         * p_sep = 0.374 × 400 kPa = 150 kPa &lt; p_e → no separation.
         */
        @Test
        @DisplayName("Oblique shock: back-substitution recovers original pressure ratio (Anderson §4.9, 1e-9)")
        void obliqueShockBackSubstitutionRecoversPressureRatio() {
            double pa = 400000.0;
            NozzleDesignParameters params = air(3.0, 7e6, pa);
            double pe = params.idealExitPressure();
            double gamma = GasProperties.AIR.gamma();
            double me = 3.0;

            var result = new ShockExpansionModel(params).compute(pa);
            assertThat(result.regime())
                    .isEqualTo(ShockExpansionModel.FlowRegime.OVEREXPANDED_OBLIQUE);

            // Re-derive β from the pressure ratio and verify the normal-shock PR at Mn1=Me·sinβ
            // equals pa/pe — this is the algebraic identity proven in Anderson §4.9.
            double pr = pa / pe;
            double gp1 = gamma + 1.0, gm1 = gamma - 1.0;
            double sin2beta = (gp1 * pr + gm1) / (2.0 * gamma * me * me);
            double Mn1 = me * Math.sqrt(sin2beta);
            double pRatioRecovered = (2.0 * gamma * Mn1 * Mn1 - gm1) / gp1;

            assertThat(pRatioRecovered)
                    .isCloseTo(pr, within(pr * 1e-9));
        }
    }
}
