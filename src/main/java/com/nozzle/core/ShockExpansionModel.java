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

/**
 * Computes off-design nozzle performance using shock-expansion theory.
 *
 * <p>For a nozzle with fixed geometry (design exit Mach M<sub>e</sub>, area ratio
 * A<sub>e</sub>/A<sub>t</sub>) operating at an ambient pressure p<sub>a</sub> that
 * differs from the design exit pressure p<sub>e</sub>, four distinct flow regimes can
 * exist:
 *
 * <ol>
 *   <li><b>Underexpanded</b> (p<sub>e</sub> &gt; p<sub>a</sub>): A Prandtl-Meyer
 *       expansion fan forms at the exit lip and the flow accelerates outward to
 *       match p<sub>a</sub>.</li>
 *   <li><b>Ideally expanded</b> (p<sub>e</sub> ≈ p<sub>a</sub>): No wave at the exit;
 *       maximum specific impulse for the operating condition.</li>
 *   <li><b>Overexpanded, oblique shock</b> (p<sub>a</sub> &gt; p<sub>e</sub>, attached
 *       shock): A weak oblique shock at the exit lip compresses the jet to ambient
 *       pressure.  The shock angle β is determined analytically from the upstream Mach
 *       number and the required pressure ratio.</li>
 *   <li><b>Overexpanded, Mach disk</b> (p<sub>a</sub> &gt; p<sub>e</sub> × PR<sub>NS</sub>):
 *       The required shock is stronger than a normal shock at M<sub>e</sub>; a Mach
 *       disk (normal shock) stands in the near-field plume.</li>
 * </ol>
 *
 * <h2>Thrust coefficient correction for separated flow</h2>
 * <p>When the ambient pressure is high enough to trigger boundary-layer separation
 * inside the nozzle (detected via {@link FlowSeparationPredictor}), the effective
 * nozzle is truncated at the separation plane and the thrust coefficient is
 * recalculated accordingly, replacing the crude fixed-penalty approach.
 * When separation occurs at Mach M<sub>sep</sub> and area ratio AR<sub>sep</sub>,
 * the corrected C<sub>f</sub> is computed as if the nozzle ended at that cross-section:
 * {@code Cf_sep = Cf_momentum(Msep) + (p_sep - p_a) / p0 * AR_sep}.
 * This is physically more accurate than the standard full-nozzle formula, which
 * yields an unrealistically large negative pressure term when p<sub>e</sub> ≪ p<sub>a</sub>.
 *
 * <h2>Standard-formula validity for non-separated cases</h2>
 * For the underexpanded and overexpanded-shock regimes, the thrust is set at the
 * nozzle exit plane by the momentum flux and exit pressure:
 * F = ṁ V<sub>e</sub> + (p<sub>e</sub> − p<sub>a</sub>) A<sub>e</sub>.
 * The external wave structure does not alter this, so the standard isentropic C<sub>f</sub>
 * formula is used with the actual ambient pressure substituted.
 */
public class ShockExpansionModel {

    /** Fractional pressure-match tolerance used to classify a flow as ideally expanded (1 %). */
    private static final double IDEAL_EXPANSION_TOLERANCE = 0.01;
    /** Standard gravity (m/s²) used to convert c*·Cf to specific impulse in seconds. */
    private static final double G0 = 9.80665;

    /** Flow regime at the nozzle exit for a given ambient pressure. */
    public enum FlowRegime {
        /** p<sub>e</sub> &gt; p<sub>a</sub>: Prandtl-Meyer expansion fan at exit lip. */
        UNDEREXPANDED,
        /** |p<sub>e</sub> − p<sub>a</sub>| / p<sub>a</sub> ≤ 1 %: no wave. */
        IDEALLY_EXPANDED,
        /** p<sub>a</sub> &gt; p<sub>e</sub>, attached oblique shock at lip. */
        OVEREXPANDED_OBLIQUE,
        /** p<sub>a</sub> &gt; p<sub>e</sub> × PR<sub>NS</sub>: Mach disk in plume. */
        OVEREXPANDED_MACH_DISK,
        /** p<sub>e</sub> &lt; p<sub>sep</sub>: internal boundary-layer separation. */
        SEPARATED
    }

    /** Nozzle design parameters that define the fixed geometry and chamber conditions. */
    private final NozzleDesignParameters parameters;

    /**
     * Creates a shock-expansion model for the given nozzle design.
     *
     * @param parameters Nozzle design parameters (must not be null)
     */
    public ShockExpansionModel(NozzleDesignParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("parameters must not be null");
        }
        this.parameters = parameters;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Computes off-design performance at the ambient pressure stored in the design
     * parameters (i.e., the design operating point).
     *
     * @return {@link OffDesignResult} describing the flow regime, corrected Cf,
     *         corrected Isp, and plume wave geometry at the design ambient pressure
     */
    public OffDesignResult compute() {
        return compute(parameters.ambientPressure());
    }

    /**
     * Computes off-design performance at the specified ambient pressure.
     * Checks for flow separation first; if separated, returns a corrected Cf
     * based on the truncated nozzle at the separation plane.  Otherwise,
     * classifies the exit flow as ideally expanded, underexpanded, overexpanded
     * oblique shock, or Mach-disk, and returns the corresponding wave geometry.
     *
     * @param ambientPressure Ambient static pressure in Pa (must be &gt; 0)
     * @return {@link OffDesignResult} describing the flow regime, corrected Cf,
     *         corrected Isp, and plume wave geometry
     * @throws IllegalArgumentException if {@code ambientPressure} ≤ 0
     */
    public OffDesignResult compute(double ambientPressure) {
        if (ambientPressure <= 0) {
            throw new IllegalArgumentException("Ambient pressure must be positive");
        }

        double p0 = parameters.chamberPressure();
        double pe = parameters.idealExitPressure();
        double Me = parameters.exitMach();
        double gamma = parameters.gasProperties().gamma();

        // --- Separation check (highest priority) ---
        NozzleDesignParameters offDesignParams = withAmbient(ambientPressure);
        FlowSeparationPredictor.SeparationResult sep =
                new FlowSeparationPredictor(offDesignParams).predict();

        if (sep.separated()) {
            double cfSep = correctedCfSeparated(sep, p0, ambientPressure, gamma);
            double ispSep = parameters.characteristicVelocity() * cfSep / G0;
            return new OffDesignResult(
                    FlowRegime.SEPARATED, ambientPressure,
                    cfSep, ispSep,
                    Double.NaN,                      // plume half-angle undefined
                    sep.separationMach(),
                    Double.NaN,                      // wave angle undefined
                    sep);
        }

        // Standard Cf (valid for all non-separated regimes)
        double cfStd = computeStandardCf(pe, ambientPressure, p0, gamma);
        double ispStd = parameters.characteristicVelocity() * cfStd / G0;

        // --- Ideally expanded ---
        if (Math.abs(pe - ambientPressure) / ambientPressure < IDEAL_EXPANSION_TOLERANCE) {
            return new OffDesignResult(
                    FlowRegime.IDEALLY_EXPANDED, ambientPressure,
                    cfStd, ispStd,
                    0.0, Me, 0.0, null);
        }

        // --- Underexpanded ---
        if (pe > ambientPressure) {
            // Isentropic expansion from pe to pa (same stagnation state).
            double M2 = machFromIsentropicPressure(ambientPressure / p0, gamma);
            double nu1 = parameters.gasProperties().prandtlMeyerFunction(Me);
            double nu2 = parameters.gasProperties().prandtlMeyerFunction(M2);
            double expansionAngleDeg = Math.toDegrees(nu2 - nu1); // outward turning
            double mu1Deg = Math.toDegrees(Math.asin(1.0 / Me));  // first wave angle
            return new OffDesignResult(
                    FlowRegime.UNDEREXPANDED, ambientPressure,
                    cfStd, ispStd,
                    expansionAngleDeg, M2, mu1Deg, null);
        }

        // --- Overexpanded ---
        double pNormalShock = pe * normalShockPressureRatio(Me, gamma);

        if (ambientPressure > pNormalShock) {
            // Normal shock (Mach disk) in plume.
            double M2 = machBehindNormalShock(Me, gamma);
            return new OffDesignResult(
                    FlowRegime.OVEREXPANDED_MACH_DISK, ambientPressure,
                    cfStd, ispStd,
                    0.0, M2, 90.0, null);
        }

        // Attached oblique shock at exit lip.
        double[] shock = obliqueShock(Me, ambientPressure / pe, gamma);
        double betaDeg = Math.toDegrees(shock[0]);
        double deltaDeg = Math.toDegrees(shock[1]);
        double M2 = shock[2];
        return new OffDesignResult(
                FlowRegime.OVEREXPANDED_OBLIQUE, ambientPressure,
                cfStd, ispStd,
                deltaDeg, M2, betaDeg, null);
    }

    /**
     * Computes off-design performance at a given altitude using the ISA atmosphere
     * model to derive the ambient pressure, then delegates to {@link #compute(double)}.
     *
     * @param altitudeMeters Geometric altitude in metres (values ≤ 0 are treated as sea level)
     * @return {@link OffDesignResult} describing the flow regime, corrected Cf,
     *         corrected Isp, and plume wave geometry at the given altitude
     */
    public OffDesignResult computeAtAltitude(double altitudeMeters) {
        return compute(isaAtmosphere(altitudeMeters));
    }

    // -------------------------------------------------------------------------
    // Private physics helpers
    // -------------------------------------------------------------------------

    /**
     * Computes the standard isentropic thrust coefficient including the pressure thrust term.
     *
     * <p>F = ṁ V<sub>e</sub> + (p<sub>e</sub> − p<sub>a</sub>) A<sub>e</sub>
     * → C<sub>f</sub> = C<sub>f,momentum</sub> + (p<sub>e</sub> − p<sub>a</sub>) / p<sub>0</sub> × A<sub>e</sub>/A<sub>t</sub>
     *
     * @param pe    Isentropic exit pressure in Pa
     * @param pa    Ambient pressure in Pa
     * @param p0    Chamber (stagnation) pressure in Pa
     * @param gamma Ratio of specific heats
     * @return Standard thrust coefficient Cf (dimensionless)
     */
    private double computeStandardCf(double pe, double pa, double p0, double gamma) {
        double gp1 = gamma + 1.0;
        double gm1 = gamma - 1.0;
        double arExit = parameters.exitAreaRatio();
        double term1 = 2.0 * gamma * gamma / gm1 * Math.pow(2.0 / gp1, gp1 / gm1);
        double prRatio = pe / p0;
        double cfMomentum = Math.sqrt(term1 * (1.0 - Math.pow(prRatio, gm1 / gamma)));
        return cfMomentum + (pe - pa) / p0 * arExit;
    }

    /**
     * Computes the corrected thrust coefficient when the boundary layer has separated
     * at M<sub>sep</sub>, AR<sub>sep</sub>.  The nozzle is treated as if it terminated
     * at the separation plane, so the momentum and pressure thrust terms are evaluated
     * at the separation cross-section rather than the geometric exit.
     *
     * @param sep   Separation analysis result providing M<sub>sep</sub>, p<sub>sep</sub>,
     *              and AR<sub>sep</sub>
     * @param p0    Chamber (stagnation) pressure in Pa
     * @param pa    Ambient pressure in Pa
     * @param gamma Ratio of specific heats
     * @return Corrected thrust coefficient Cf at the separation plane (dimensionless)
     */
    private double correctedCfSeparated(
            FlowSeparationPredictor.SeparationResult sep,
            double p0, double pa, double gamma) {

        double pSep = sep.separationPressurePa();
        double arSep = sep.separationAreaRatio();
        double gp1 = gamma + 1.0;
        double gm1 = gamma - 1.0;
        double term1 = 2.0 * gamma * gamma / gm1 * Math.pow(2.0 / gp1, gp1 / gm1);
        double prSep = pSep / p0;
        double cfMomentum = Math.sqrt(term1 * (1.0 - Math.pow(prSep, gm1 / gamma)));
        return cfMomentum + (pSep - pa) / p0 * arSep;
    }

    /**
     * Inverts the isentropic pressure ratio to find the supersonic Mach number.
     *
     * <p>p/p<sub>0</sub> = (1 + (γ−1)/2 · M²)^(−γ/(γ−1))
     * → M = √( 2/(γ−1) · ( (p/p<sub>0</sub>)^(−(γ−1)/γ) − 1 ) )
     *
     * @param pOverP0 p / p<sub>0</sub>, must be in (0, 1)
     * @param gamma   Ratio of specific heats
     * @return Supersonic Mach number
     */
    static double machFromIsentropicPressure(double pOverP0, double gamma) {
        double gm1 = gamma - 1.0;
        return Math.sqrt(2.0 / gm1 * (Math.pow(pOverP0, -gm1 / gamma) - 1.0));
    }

    /**
     * Computes the normal-shock static pressure ratio across a standing normal shock:
     * p<sub>2</sub>/p<sub>1</sub> = (2γM² − (γ−1)) / (γ+1).
     *
     * @param mach  Upstream Mach number (must be ≥ 1)
     * @param gamma Ratio of specific heats
     * @return Pressure ratio p<sub>2</sub>/p<sub>1</sub> (dimensionless, ≥ 1)
     */
    private static double normalShockPressureRatio(double mach, double gamma) {
        return (2.0 * gamma * mach * mach - (gamma - 1.0)) / (gamma + 1.0);
    }

    /**
     * Computes the Mach number immediately downstream of a normal shock:
     * M<sub>2</sub>² = ((γ−1)M<sub>1</sub>² + 2) / (2γM<sub>1</sub>² − (γ−1)).
     *
     * @param M1    Upstream Mach number (must be ≥ 1)
     * @param gamma Ratio of specific heats
     * @return Subsonic downstream Mach number M<sub>2</sub> (dimensionless, &lt; 1)
     */
    private static double machBehindNormalShock(double M1, double gamma) {
        double gm1 = gamma - 1.0;
        return Math.sqrt((gm1 * M1 * M1 + 2.0) / (2.0 * gamma * M1 * M1 - gm1));
    }

    /**
     * Computes the oblique shock at the nozzle exit lip.
     *
     * <p>Given the upstream Mach number M<sub>1</sub> and the required pressure ratio
     * p<sub>2</sub>/p<sub>1</sub> = p<sub>a</sub>/p<sub>e</sub>, the shock angle β
     * follows directly from the normal-component pressure relation:
     * <pre>
     *   sin²β = ( (γ+1)·(p<sub>a</sub>/p<sub>e</sub>) + (γ−1) ) / ( 2γ·M<sub>1</sub>² )
     * </pre>
     * The flow deflection angle δ and post-shock Mach M<sub>2</sub> are then found
     * from the standard oblique-shock relations.
     *
     * @param M1    Upstream (exit) Mach number
     * @param pr    Pressure ratio p<sub>2</sub>/p<sub>1</sub> = p<sub>a</sub>/p<sub>e</sub> (&gt; 1)
     * @param gamma Ratio of specific heats
     * @return double[3] = {β (rad), δ (rad), M<sub>2</sub>}
     */
    private static double[] obliqueShock(double M1, double pr, double gamma) {
        double gp1 = gamma + 1.0;
        double gm1 = gamma - 1.0;

        // Shock angle from pressure-ratio relation.
        double sin2beta = (gp1 * pr + gm1) / (2.0 * gamma * M1 * M1);
        // Clamp to [0,1] for robustness near the normal-shock limit.
        sin2beta = Math.min(1.0, Math.max(0.0, sin2beta));
        double beta = Math.asin(Math.sqrt(sin2beta));

        // Normal component of upstream Mach.
        double M1n = M1 * Math.sin(beta);
        double M1n2 = M1n * M1n;

        // Flow deflection angle (θ-β-M relation).
        double cotBeta = Math.cos(beta) / Math.sin(beta);
        double numerator = 2.0 * cotBeta * (M1n2 - 1.0);
        double denominator = M1 * M1 * (gamma + Math.cos(2.0 * beta)) + 2.0;
        double delta = Math.atan(numerator / denominator);

        // Post-shock normal-component Mach, then total M2.
        double M2n2 = (M1n2 * gm1 / 2.0 + 1.0) / (gamma * M1n2 - gm1 / 2.0);
        double M2 = Math.sqrt(M2n2) / Math.sin(beta - delta);

        return new double[]{beta, delta, M2};
    }

    /**
     * ISA standard atmosphere — pressure in Pa at the given geometric altitude.
     * Matches the formula used in {@code AltitudeAdaptiveOptimizer}.
     *
     * @param altitude Altitude in metres
     * @return Static pressure in Pa
     */
    public static double isaAtmosphere(double altitude) {
        if (altitude <= 0) return 101325.0;
        if (altitude <= 11000) {
            double T = 288.15 - 0.0065 * altitude;
            return 101325.0 * Math.pow(T / 288.15, 5.2561);
        }
        if (altitude <= 25000) {
            return 22632.1 * Math.exp(-0.0001577 * (altitude - 11000));
        }
        if (altitude <= 100000) {
            return 101325.0 * Math.exp(-altitude / 7000.0);
        }
        return 1.0; // near vacuum
    }

    /**
     * Returns a copy of the design parameters with the ambient pressure replaced
     * by the supplied value.  All other fields are preserved unchanged.
     *
     * @param pa Replacement ambient pressure in Pa
     * @return A new {@link NozzleDesignParameters} instance with {@code pa} as ambient pressure
     */
    private NozzleDesignParameters withAmbient(double pa) {
        return NozzleDesignParameters.builder()
                .throatRadius(parameters.throatRadius())
                .exitMach(parameters.exitMach())
                .chamberPressure(parameters.chamberPressure())
                .chamberTemperature(parameters.chamberTemperature())
                .ambientPressure(pa)
                .gasProperties(parameters.gasProperties())
                .numberOfCharLines(parameters.numberOfCharLines())
                .wallAngleInitial(parameters.wallAngleInitial())
                .lengthFraction(parameters.lengthFraction())
                .axisymmetric(parameters.axisymmetric())
                .build();
    }

    // -------------------------------------------------------------------------
    // Result record
    // -------------------------------------------------------------------------

    /**
     * Immutable result from an off-design shock-expansion analysis.
     *
     * @param regime              Flow regime at the nozzle exit
     * @param ambientPressurePa   Ambient pressure used for this analysis (Pa)
     * @param thrustCoefficient   Corrected C<sub>f</sub> (dimensionless)
     * @param specificImpulse     Corrected I<sub>sp</sub> (s)
     * @param plumeHalfAngleDeg   Half-angle of the plume boundary (°):
     *                            expansion-fan turning angle for underexpanded;
     *                            shock deflection angle for overexpanded oblique;
     *                            NaN when not applicable
     * @param postWaveMach        Mach number just after the exit wave (NaN if no wave)
     * @param waveAngleDeg        Oblique shock angle β (°), or Mach-cone angle μ for
     *                            the first expansion wave (°); NaN or 0 otherwise
     * @param separationResult    Detailed separation analysis, or null if not separated
     */
    public record OffDesignResult(
            FlowRegime regime,
            double ambientPressurePa,
            double thrustCoefficient,
            double specificImpulse,
            double plumeHalfAngleDeg,
            double postWaveMach,
            double waveAngleDeg,
            FlowSeparationPredictor.SeparationResult separationResult
    ) {
        /**
         * Returns {@code true} if the nozzle is flowing full (no internal separation),
         * i.e., the flow regime is any value other than {@link FlowRegime#SEPARATED}.
         *
         * @return {@code true} when the regime is not {@link FlowRegime#SEPARATED}
         */
        public boolean isFullyFlowing() {
            return regime != FlowRegime.SEPARATED;
        }

        @SuppressWarnings("NullableProblems")
        @Override
        public String toString() {
            if (regime == FlowRegime.SEPARATED) {
                return String.format(
                        "OffDesign [SEPARATED @ %.0f Pa]: Cf=%.4f  Isp=%.1f s  M_sep=%.3f",
                        ambientPressurePa, thrustCoefficient, specificImpulse, postWaveMach);
            }
            return String.format(
                    "OffDesign [%-22s @ %.0f Pa]: Cf=%.4f  Isp=%.1f s  M_post=%.3f  wave=%.1f°",
                    regime, ambientPressurePa,
                    thrustCoefficient, specificImpulse,
                    Double.isNaN(postWaveMach) ? 0.0 : postWaveMach,
                    Double.isNaN(waveAngleDeg) ? 0.0 : waveAngleDeg);
        }
    }
}
