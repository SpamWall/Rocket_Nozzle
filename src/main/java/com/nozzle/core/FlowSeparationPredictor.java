package com.nozzle.core;

/**
 * Predicts flow separation in overexpanded rocket nozzles.
 *
 * <p>When ambient pressure exceeds the nozzle exit pressure sufficiently, the
 * supersonic boundary layer can no longer sustain the adverse pressure gradient
 * imposed by the surrounding environment and separates from the wall upstream of
 * the geometric exit plane.  This class implements three empirical criteria that
 * are widely used in the rocket nozzle community to estimate:
 * <ul>
 *   <li>whether separation occurs at all,</li>
 *   <li>the local wall pressure and Mach number at the separation point,</li>
 *   <li>the mode of separation (Free Shock Separation vs. Restricted Shock Separation),</li>
 *   <li>a first-order estimate of the resulting side load.</li>
 * </ul>
 *
 * Separation criteria
 * <dl>
 *   <dt>Summerfield (1954)</dt>
 *   <dd>Constant fraction of ambient pressure:
 *       p<sub>sep</sub> = 0.374 × p<sub>a</sub>.
 *       Conservative and simple; accurate near γ = 1.4.</dd>
 *   <dt>Schilling (1962)</dt>
 *   <dd>Scales the critical pressure with the degree of overexpansion:
 *       p<sub>sep</sub> = 0.667 × p<sub>a</sub> × (p<sub>e</sub>/p<sub>a</sub>)<sup>0.6</sup>,
 *       where p<sub>e</sub> is the design exit pressure.
 *       Tends to predict slightly less aggressive separation than Summerfield
 *       for mildly overexpanded nozzles.</dd>
 *   <dt>Romine (1998)</dt>
 *   <dd>Turbulent-boundary-layer based; accounts for gamma and the local
 *       Mach number at the throat:
 *       p<sub>sep</sub>/p<sub>a</sub> = 1 / (1 + 0.6 × γ × k<sub>R</sub>),
 *       where k<sub>R</sub> = 0.5 × (p<sub>e</sub>/p<sub>a</sub> − 1).
 *       Approaches 0.374 at typical NPR values.</dd>
 * </dl>
 *
 * Separation mode classification
 * <ul>
 *   <li><b>Free Shock Separation (FSS)</b> — separation well inside the nozzle;
 *       a free jet issues from the separation plane.  Separation Mach &lt; 70 % of
 *       design exit Mach.</li>
 *   <li><b>Restricted Shock Separation (RSS)</b> — separation near the exit; a
 *       re-circulation bubble forms and the shock is confined.  More typical of
 *       thrust-optimized bell nozzles and associated with larger side loads.
 *       Separation Mach ≥ 70 % of design exit Mach.</li>
 * </ul>
 *
 * Side-load estimate
 * Based on the simplified Capiaux–Washington pressure-imbalance model:
 * F<sub>side</sub> ≈ 0.3 × (p<sub>a</sub> − p<sub>sep</sub>) × A<sub>sep</sub>.
 * The factor 0.3 represents the typical circumferential asymmetry observed in
 * hot-fire tests.
 */
public class FlowSeparationPredictor {

    /** Available empirical separation criteria. */
    public enum Criterion {
        /** Summerfield et al. (1954) constant-fraction criterion. */
        SUMMERFIELD,
        /** Schilling (1962) overexpansion-scaled criterion. */
        SCHILLING,
        /** Romine (1998) turbulent-boundary-layer criterion. */
        ROMINE
    }

    /** Classification of the separation pattern. */
    public enum SeparationMode {
        /** Nozzle flows full — no separation. */
        NO_SEPARATION,
        /** Free Shock Separation: separation well upstream of exit. */
        FSS,
        /** Restricted Shock Separation: separation near exit, recirculation bubble. */
        RSS
    }

    /**
     * Ratio of the Mach number at the separation point to the design exit Mach
     * below which Free Shock Separation (FSS) is assumed.  At or above this
     * fraction, Restricted Shock Separation (RSS) is assumed.
     */
    private static final double RSS_MACH_FRACTION = 0.70;

    /**
     * Circumferential asymmetry factor used in the simplified side-load model.
     * Dimensionless; approximately 0.25–0.35 from hot-fire data.
     */
    private static final double SIDE_LOAD_ASYMMETRY = 0.30;

    private final NozzleDesignParameters parameters;

    /**
     * Creates a predictor for the given nozzle design.
     *
     * @param parameters Nozzle design parameters (must not be null)
     */
    public FlowSeparationPredictor(NozzleDesignParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("parameters must not be null");
        }
        this.parameters = parameters;
    }

    /**
     * Predicts separation using the Summerfield criterion.
     *
     * @return Separation result
     */
    public SeparationResult predict() {
        return predict(Criterion.SUMMERFIELD);
    }

    /**
     * Predicts separation using the specified criterion.
     *
     * @param criterion Criterion to apply
     * @return Separation result
     */
    public SeparationResult predict(Criterion criterion) {
        double p0 = parameters.chamberPressure();
        double pa = parameters.ambientPressure();
        double pe = parameters.idealExitPressure();
        double gamma = parameters.gasProperties().gamma();
        double mExit = parameters.exitMach();

        double pSep = separationPressure(criterion, pa, pe, gamma);

        if (pe >= pSep) {
            // Nozzle is not overexpanded enough to cause separation.
            return SeparationResult.noSeparation(criterion, pSep);
        }

        // Separation occurs: find Mach number where isentropic wall pressure = pSep.
        double mSep = machFromPressureRatio(pSep / p0, gamma);
        double arSep = parameters.gasProperties().areaRatio(mSep);
        double arExit = parameters.exitAreaRatio();

        // Axial fraction: approximate radial scaling (r ∝ sqrt(A/A*)).
        double radSep = Math.sqrt(arSep);
        double radExit = Math.sqrt(arExit);
        double axialFraction = Math.max(0.0, Math.min(1.0, (radSep - 1.0) / (radExit - 1.0)));

        SeparationMode mode = (mSep / mExit >= RSS_MACH_FRACTION) ? SeparationMode.RSS : SeparationMode.FSS;

        double sideLoad = estimateSideLoad(pSep, pa, arSep);

        return new SeparationResult(true, criterion, pSep, mSep, arSep, axialFraction, mode, sideLoad);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Computes the critical wall pressure at which separation begins.
     */
    private double separationPressure(Criterion c, double pa, double pe, double gamma) {
        return switch (c) {
            // Summerfield (1954): constant fraction of ambient.
            case SUMMERFIELD -> 0.374 * pa;

            // Schilling (1962): scales with overexpansion ratio.
            // When pe/pa → 1 (matched), pSep → 0.667 × pa.
            // When pe/pa << 1 (strongly overexpanded), pSep → 0 (consistent with large M_exit).
            case SCHILLING -> 0.667 * pa * Math.pow(pe / pa, 0.6);

            // Romine (1998): turbulent boundary layer based.
            // k_R = 0.5 * (p_a/p_e - 1), accounts for overexpansion severity.
            case ROMINE -> {
                double kR = 0.5 * (pa / pe - 1.0);
                double denom = 1.0 + 0.6 * gamma * Math.max(kR, 0.0);
                yield pa / denom;
            }
        };
    }

    /**
     * Inverts the isentropic pressure ratio to find the Mach number analytically.
     *
     * <p>From: p/p₀ = (1 + (γ−1)/2 · M²)^(−γ/(γ−1))
     * → M = sqrt( 2/(γ−1) · ( (p/p₀)^(−(γ−1)/γ) − 1 ) )
     *
     * @param pOverP0 Static-to-stagnation pressure ratio (must be in (0, 1))
     * @param gamma   Ratio of specific heats
     * @return Mach number (≥ 1 for supersonic flow)
     */
    private static double machFromPressureRatio(double pOverP0, double gamma) {
        double gm1 = gamma - 1.0;
        double exponent = -gm1 / gamma; // -(γ-1)/γ
        double term = Math.pow(pOverP0, exponent) - 1.0;
        return Math.sqrt(2.0 * term / gm1);
    }

    /**
     * Estimates peak side load using the Capiaux–Washington asymmetric-pressure model.
     *
     * @param pSep  Local wall pressure at separation (Pa)
     * @param pa    Ambient pressure (Pa)
     * @param arSep Area ratio at separation (A_sep / A_throat)
     */
    private double estimateSideLoad(double pSep, double pa, double arSep) {
        double aSep = parameters.throatArea() * arSep;
        double delta_p = pa - pSep; // pressure rise across separation shock ≥ 0
        return SIDE_LOAD_ASYMMETRY * delta_p * aSep;
    }

    // -------------------------------------------------------------------------
    // Result record
    // -------------------------------------------------------------------------

    /**
     * Immutable result from a separation prediction.
     *
     * @param separated             True if flow separates before the geometric exit
     * @param criterion             Criterion used for this prediction
     * @param separationPressurePa  Critical wall pressure at the separation front (Pa)
     * @param separationMach        Mach number at the separation front (NaN if not separated)
     * @param separationAreaRatio   A/A* at the separation front (NaN if not separated)
     * @param separationAxialFraction Approximate fractional axial position, 0 = throat, 1 = exit
     *                               (NaN if not separated)
     * @param mode                  Separation mode classification
     * @param estimatedSideLoadN    Peak side load estimate in Newtons (0 if not separated)
     */
    public record SeparationResult(
            boolean separated,
            Criterion criterion,
            double separationPressurePa,
            double separationMach,
            double separationAreaRatio,
            double separationAxialFraction,
            SeparationMode mode,
            double estimatedSideLoadN
    ) {
        /**
         * Convenience factory for a non-separated nozzle.
         */
        static SeparationResult noSeparation(Criterion criterion, double separationThreshold) {
            return new SeparationResult(
                    false, criterion, separationThreshold,
                    Double.NaN, Double.NaN, Double.NaN,
                    SeparationMode.NO_SEPARATION, 0.0);
        }

        /**
         * Returns a concise description of the separation state.
         */
        @SuppressWarnings("NullableProblems")
        @Override
        public String toString() {
            if (!separated) {
                return String.format(
                        "FlowSeparation [%s]: NO SEPARATION  (p_sep_threshold=%.0f Pa)",
                        criterion, separationPressurePa);
            }
            return String.format(
                    """
                    FlowSeparation [%s]: %s
                      Separation pressure:  %.0f Pa
                      Separation Mach:      %.3f
                      Separation A/A*:      %.3f
                      Axial position:       %.1f%% of nozzle length
                      Est. side load:       %.1f N
                    """,
                    criterion, mode,
                    separationPressurePa,
                    separationMach,
                    separationAreaRatio,
                    separationAxialFraction * 100,
                    estimatedSideLoadN);
        }
    }
}
