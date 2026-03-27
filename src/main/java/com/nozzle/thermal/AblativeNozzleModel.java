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

import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.geometry.Point2D;

import java.util.ArrayList;
import java.util.List;

/**
 * Models ablative nozzle liner degradation using an Arrhenius char-rate model.
 *
 * <p>Ablative liners are used primarily in solid-rocket motor nozzles, and in
 * liquid-rocket nozzle extensions where regenerative cooling is impractical.
 * As the hot gas heats the liner, the organic matrix pyrolysis at the char
 * front and the char layer grows inward toward the back wall.
 *
 * <p>At each axial station the model computes:
 * <ul>
 *   <li>Local surface temperature — taken from the supplied heat-transfer
 *       profile (gas-side wall temperature), or estimated as 30 % of the
 *       chamber stagnation temperature when no profile is provided</li>
 *   <li>Instantaneous char rate [m/s] from the Arrhenius expression
 *       {@code ṙ = A · exp(−Ea / (R · T_surface))}</li>
 *   <li>Cumulative recession depth [m] integrated over the specified burn
 *       time using a uniform time step</li>
 *   <li>Remaining liner thickness [m] clamped to zero at perforation, and a
 *       boolean flag when the liner has been consumed</li>
 * </ul>
 *
 * <p>Surface temperature is treated as constant during the burn (zero-order
 * hold on the steady-state wall temperature).  This conservative assumption
 * over-predicts recession because the growing char layer would in reality
 * insulate the surface and progressively reduce T_surface.  It is therefore
 * appropriate for margin estimation.
 *
 * <p>Reference: Sutton, G.P. &amp; Biblarz, O., <em>Rocket Propulsion
 * Elements</em>, 9th ed., §12.5; Tewari, A., <em>Atmospheric and Space
 * Flight Dynamics</em>, Birkhäuser 2007, Appendix C.
 */
public class AblativeNozzleModel {

    /** Universal gas constant in J/(mol·K). */
    private static final double R_UNIVERSAL = 8.314462;

    /**
     * Reference pressure for the mechanical erosion scaling law [Pa].
     * Chosen as 1 MPa so that {@code erosionFactor} has intuitive units of m/s
     * at a representative SRM chamber pressure.
     */
    private static final double P_REF = 1.0e6;

    private final NozzleDesignParameters parameters;
    private final NozzleContour contour;

    private AblativeMaterial material = AblativeMaterial.CARBON_PHENOLIC;
    private double initialLinerThickness = 0.020;  // m
    private double burnTime = 10.0;                // s
    private int timeSteps = 100;

    /**
     * Mechanical (particle-impingement) erosion factor {@code k_e} [m/s].
     * The erosion supplement {@code ṙ_mech = k_e · (P_c / P_ref)^0.8} is
     * added to the Arrhenius rate at every station.  The default value of 0
     * disables mechanical erosion, preserving the pure-Arrhenius behavior.
     */
    private double erosionFactor = 0.0;

    private final List<AblativePoint> profile = new ArrayList<>();

    /**
     * Creates an ablative nozzle model with default material (carbon-phenolic),
     * 20 mm initial liner thickness, and 10-second burn time.
     *
     * @param parameters Design parameters supplying gas stagnation conditions
     * @param contour    Nozzle contour; auto-generated if not yet built
     */
    public AblativeNozzleModel(NozzleDesignParameters parameters, NozzleContour contour) {
        this.parameters = parameters;
        this.contour = contour;
    }

    /**
     * Sets the ablative liner material.
     *
     * @param material Ablative material to use
     * @return This instance
     */
    public AblativeNozzleModel setMaterial(AblativeMaterial material) {
        this.material = material;
        return this;
    }

    /**
     * Sets the initial liner thickness at all axial stations.
     *
     * @param thickness Initial liner thickness in m (must be &gt; 0)
     * @return This instance
     */
    public AblativeNozzleModel setInitialLinerThickness(double thickness) {
        this.initialLinerThickness = thickness;
        return this;
    }

    /**
     * Sets the total burn time over which recession is integrated.
     *
     * @param burnTime Burn time in s (must be &gt; 0)
     * @return This instance
     */
    public AblativeNozzleModel setBurnTime(double burnTime) {
        this.burnTime = burnTime;
        return this;
    }

    /**
     * Sets the number of uniform time steps for the recession integration.
     * More steps give higher accuracy at the cost of additional computation.
     *
     * @param steps Number of time steps (must be ≥ 1)
     * @return This instance
     */
    public AblativeNozzleModel setTimeSteps(int steps) {
        this.timeSteps = steps;
        return this;
    }

    /**
     * Sets the mechanical (particle-impingement) erosion factor {@code k_e}.
     *
     * <p>In solid-rocket motors the exhaust is laden with alumina (Al₂O₃)
     * particles that mechanically erode the char layer in addition to the
     * thermal pyrolysis captured by the Arrhenius expression.  The erosion
     * supplement is modeled as:
     *
     * <pre>  ṙ_mech = k_e · (P_c / P_ref)^0.8</pre>
     *
     * where {@code P_ref = 1 MPa} and the {@code 0.8} exponent mirrors the
     * Bartz pressure scaling for convective heat flux.  The total surface
     * recession rate at each station is therefore:
     *
     * <pre>  ṙ_total = A · exp(−Ea / (R · T_surface)) + k_e · (P_c / P_ref)^0.8</pre>
     *
     * <p>Typical values for alumina-loaded SRM propellants:
     * <ul>
     *   <li>Carbon-phenolic throat: {@code k_e ≈ 0.5–2 × 10⁻⁵} m/s</li>
     *   <li>Silica-phenolic extension: {@code k_e ≈ 1–4 × 10⁻⁵} m/s</li>
     * </ul>
     *
     * <p>Set to 0 (default) to disable mechanical erosion and use the pure
     * Arrhenius model.
     *
     * @param factor Erosion factor in m/s (must be ≥ 0)
     * @return This instance
     * @throws IllegalArgumentException if {@code factor} is negative
     */
    public AblativeNozzleModel setErosionFactor(double factor) {
        if (factor < 0.0) {
            throw new IllegalArgumentException(
                    "erosionFactor must be >= 0, got: " + factor);
        }
        this.erosionFactor = factor;
        return this;
    }

    /**
     * Calculates the ablative recession profile along the nozzle.
     *
     * <p>The gas-side wall temperature at each axial station is taken from the
     * nearest point in {@code heatProfile}.  When {@code heatProfile} is
     * {@code null} or empty a fallback surface temperature of
     * {@code 0.3 × T_chamber} is used instead.
     *
     * @param heatProfile Wall thermal profile from {@link HeatTransferModel};
     *                    may be {@code null}
     * @return This instance
     */
    public AblativeNozzleModel calculate(List<HeatTransferModel.WallThermalPoint> heatProfile) {
        profile.clear();

        List<Point2D> contourPoints = contour.getContourPoints();
        if (contourPoints.isEmpty()) {
            contour.generate(100);
            contourPoints = contour.getContourPoints();
        }

        for (Point2D pt : contourPoints) {
            double T_surface = findSurfaceTemperature(pt.x(), heatProfile);
            AblativePoint ablPt = integrateRecession(pt.x(), pt.y(), T_surface);
            profile.add(ablPt);
        }

        return this;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Returns the gas-side wall temperature at axial position {@code x}.
     * Uses the nearest {@link HeatTransferModel.WallThermalPoint} from the
     * supplied profile, or a chamber-based estimate when the profile is absent.
     *
     * @param x           Axial position in m
     * @param heatProfile Thermal profile; {@code null} or empty triggers fallback
     * @return Surface temperature in K
     */
    private double findSurfaceTemperature(double x,
                                           List<HeatTransferModel.WallThermalPoint> heatProfile) {
        if (heatProfile == null || heatProfile.isEmpty()) {
            return parameters.chamberTemperature() * 0.30;
        }

        HeatTransferModel.WallThermalPoint nearest = null;
        double minDx = Double.MAX_VALUE;
        for (HeatTransferModel.WallThermalPoint pt : heatProfile) {
            double dx = Math.abs(pt.x() - x);
            if (dx < minDx) {
                minDx = dx;
                nearest = pt;
            }
        }
        return nearest != null ? nearest.wallTemperature() : parameters.chamberTemperature() * 0.30;
    }

    /**
     * Integrates char recession at a single axial station over the full burn
     * time using a uniform Euler scheme.
     *
     * <p>Surface temperature is held constant at {@code T_surface} (the
     * steady-state wall temperature from the heat-transfer model).  The char
     * depth is clamped to {@code initialLinerThickness} to represent complete
     * liner consumption; the remaining thickness is computed exactly as
     * {@code initialLinerThickness − charDepth}.
     *
     * @param x         Axial position in m
     * @param y         Radial position in m
     * @param T_surface Constant surface temperature in K
     * @return Ablative state after {@code burnTime} seconds
     */
    private AblativePoint integrateRecession(double x, double y, double T_surface) {
        double dt = burnTime / timeSteps;
        double charDepth = 0.0;

        // Arrhenius pyrolysis term — constant because T_surface is held fixed.
        double totalCharRate = getTotalCharRate(T_surface);

        // Both rate terms are constant for this station (T_surface is held fixed,
        // erosionFactor and Pc are global constants).  The Euler loop is retained
        // rather than collapsed to the closed form min(thickness, rate·burnTime)
        // so that future callers can override integrateRecession() and introduce
        // temperature-history feedback (e.g. insulating char-layer growth) without
        // restructuring the loop.
        for (int step = 0; step < timeSteps; step++) {
            charDepth = Math.min(initialLinerThickness, charDepth + totalCharRate * dt);
        }

        boolean perforated = charDepth >= initialLinerThickness;
        double remaining = initialLinerThickness - charDepth;

        return new AblativePoint(x, y, T_surface, totalCharRate,
                charDepth, remaining, initialLinerThickness, perforated);
    }

    /**
     * Computes the total surface recession rate at a single axial station by
     * combining the Arrhenius pyrolysis rate with the mechanical erosion supplement.
     *
     * <p>The total rate is:
     * <pre>  ṙ_total = A · exp(−Ea / (R · T_surface)) + k_e · (P_c / P_ref)^0.8</pre>
     *
     * <p>The first term is the Arrhenius (thermal pyrolysis) contribution, where
     * {@code A} and {@code Ea} are the pre-exponential factor and activation energy
     * of the current {@link AblativeMaterial}, and {@code R} is the universal gas
     * constant (8.314462 J/(mol·K)).
     *
     * <p>The second term is the mechanical erosion supplement from particle
     * impingement (see {@link #setErosionFactor(double)}).  It scales as
     * {@code (P_c / P_ref)^0.8}, consistent with the Bartz pressure exponent for
     * convective heat flux, where {@code P_ref = 1 MPa}.  When
     * {@link #erosionFactor} is zero (the default), this term vanishes and the
     * result is the pure Arrhenius rate.
     *
     * <p>Both terms are evaluated at the station-level surface temperature
     * {@code T_surface} (held constant over the burn in the zero-order-hold
     * assumption) and the global chamber pressure {@code P_c}.  Because neither
     * value changes during the integration loop in {@link #integrateRecession},
     * this method is called once per station outside the time-step loop.
     *
     * @param T_surface Gas-side liner surface temperature in K at this station;
     *                  must be &gt; 0 to avoid a singularity in the Arrhenius exponent
     * @return Total instantaneous recession rate in m/s
     */
    private double getTotalCharRate(double T_surface) {
        double arrheniusRate = material.preExponentialFactor()
                * Math.exp(-material.activationEnergy() / (R_UNIVERSAL * T_surface));

        // Mechanical erosion supplement from particle impingement (§ setErosionFactor).
        // Scales as (P_c / P_ref)^0.8, consistent with Bartz heat-flux pressure exponent.
        double mechanicalRate = erosionFactor
                * Math.pow(parameters.chamberPressure() / P_REF, 0.8);

       return arrheniusRate + mechanicalRate;
    }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------

    /**
     * Returns the ablative profile computed by the most recent
     * {@link #calculate} call (one point per contour station).
     *
     * @return Unmodifiable snapshot of the profile; empty before
     *         {@link #calculate} is called
     */
    public List<AblativePoint> getProfile() {
        return new ArrayList<>(profile);
    }

    /**
     * Returns the maximum recession depth across all axial stations.
     *
     * @return Maximum recession depth in m; 0 if {@link #calculate} has not
     *         been called
     */
    public double getMaxRecessionDepth() {
        return profile.stream()
                .mapToDouble(AblativePoint::recessDepth)
                .max()
                .orElse(0.0);
    }

    /**
     * Returns the minimum remaining liner thickness across all axial stations.
     *
     * @return Minimum remaining thickness in m; 0 if {@link #calculate} has
     *         not been called
     */
    public double getMinRemainingThickness() {
        return profile.stream()
                .mapToDouble(AblativePoint::remainingThickness)
                .min()
                .orElse(0.0);
    }

    /**
     * Returns {@code true} if the liner has been completely consumed
     * ({@code recessDepth ≥ initialLinerThickness}) at any axial station.
     *
     * @return {@code true} if any station is perforated
     */
    public boolean isPerforatedAnywhere() {
        return profile.stream().anyMatch(AblativePoint::isPerforated);
    }

    /**
     * Returns the ablated mass per unit annular area [kg/m²] at each axial
     * station, in the same order as {@link #getProfile()}.
     *
     * <p>The ablated mass per unit area at station {@code i} is:
     * <pre>  Δm_i = ρ_material · recessDepth_i</pre>
     *
     * <p>This quantity is useful for mass-budget analysis: multiply by the
     * local annular area {@code 2π r Δx} to obtain the absolute mass loss at
     * each station.
     *
     * @return List of ablated mass per unit area [kg/m²]; empty before
     *         {@link #calculate} is called
     */
    public List<Double> getAblatedMassPerStation() {
        double rho = material.density();
        return profile.stream()
                .map(pt -> rho * pt.recessDepth())
                .toList();
    }

    /**
     * Returns the total ablated mass [kg] integrated over the full nozzle
     * contour using the trapezoidal rule on the annular surface area.
     *
     * <p>Each adjacent pair of axial stations defines an annular frustum of
     * width {@code Δx} and mean radius {@code (r_i + r_{i+1}) / 2}.  The
     * ablated mass of that frustum is:
     * <pre>  Δm = ρ · (recession_i + recession_{i+1}) / 2 · 2π · r_mean · |Δx|</pre>
     *
     * @return Total ablated mass in kg; 0 if fewer than two stations are
     *         available or {@link #calculate} has not been called
     */
    public double getTotalAblatedMass() {
        if (profile.size() < 2) return 0.0;

        double rho = material.density();
        double totalMass = 0.0;

        for (int i = 0; i < profile.size() - 1; i++) {
            AblativePoint a = profile.get(i);
            AblativePoint b = profile.get(i + 1);
            double dx = Math.abs(b.x() - a.x());
            double rMean = (a.y() + b.y()) * 0.5;
            double recessionMean = (a.recessDepth() + b.recessDepth()) * 0.5;
            totalMass += rho * recessionMean * 2.0 * Math.PI * rMean * dx;
        }

        return totalMass;
    }

    // -----------------------------------------------------------------------
    // Nested types
    // -----------------------------------------------------------------------

    /**
     * Immutable snapshot of the ablative state at a single axial station after
     * the specified burn time.
     *
     * @param x                  Axial position in m (from the throat)
     * @param y                  Radial position (wall radius) in m
     * @param surfaceTemp        Gas-side liner surface temperature in K used as
     *                           the driving temperature for the char-rate calculation
     * @param charRate           Instantaneous Arrhenius char rate in m/s at
     *                           {@code surfaceTemp}
     * @param recessDepth        Cumulative recession depth in m (clamped to
     *                           {@code initialThickness} at perforation)
     * @param remainingThickness Remaining virgin liner thickness in m;
     *                           {@code initialThickness − recessDepth}; ≥ 0
     * @param initialThickness   Initial liner thickness in m as set on the model
     * @param isPerforated       {@code true} when {@code recessDepth} reached
     *                           {@code initialThickness}
     */
    public record AblativePoint(
            double x,
            double y,
            double surfaceTemp,
            double charRate,
            double recessDepth,
            double remainingThickness,
            double initialThickness,
            boolean isPerforated
    ) {
        /**
         * Returns the char fraction, i.e. the fraction of the initial liner
         * thickness that has been consumed: {@code recessDepth / initialThickness}.
         *
         * @return Char fraction in [0, 1]; 0 when {@code initialThickness} is 0
         */
        public double charFraction() {
            return initialThickness > 0 ? recessDepth / initialThickness : 0.0;
        }

        @SuppressWarnings("NullableProblems")
        @Override
        public String toString() {
            return String.format(
                    "Ablative[x=%.4f, Ts=%.0f K, \u1e59=%.3e m/s, recession=%.4f m%s]",
                    x, surfaceTemp, charRate, recessDepth, isPerforated ? " PERFORATED" : "");
        }
    }

    /**
     * Ablative liner material defined by Arrhenius char-rate parameters and
     * thermal conductivities for the char and virgin-material layers.
     *
     * <p>All built-in presets are calibrated to representative values from
     * open literature (Sutton &amp; Biblarz, §12.5) to produce char rates in
     * the range 0.01–5 mm/s at gas-side wall temperatures of 500–1 500 K.
     *
     * @param name                      Human-readable material name
     * @param preExponentialFactor      {@code A} [m/s] in {@code ṙ = A·exp(−Ea/(R·T))}
     * @param activationEnergy          {@code Ea} [J/mol]; higher values give a
     *                                  steeper Arrhenius temperature sensitivity
     * @param charThermalConductivity   Thermal conductivity of the char layer
     *                                  {@code k_char} [W/(m·K)]
     * @param virginThermalConductivity Thermal conductivity of the uncharred
     *                                  material {@code k_virgin} [W/(m·K)]
     * @param density                   Material density [kg/m³]
     */
    public record AblativeMaterial(
            String name,
            double preExponentialFactor,
            double activationEnergy,
            double charThermalConductivity,
            double virginThermalConductivity,
            double density
    ) {

        /**
         * Returns the instantaneous Arrhenius char rate [m/s] at the given
         * surface temperature, without any mechanical erosion supplement.
         *
         * <pre>  ṙ(T) = A · exp(−Ea / (R · T))</pre>
         *
         * <p>This convenience method is useful for generating material char-rate
         * curves and for sensitivity studies without constructing a full model.
         *
         * @param surfaceTemperature Surface temperature in K (must be &gt; 0)
         * @return Arrhenius char rate in m/s
         */
        public double charRateAt(double surfaceTemperature) {
            return preExponentialFactor
                    * Math.exp(-activationEnergy / (R_UNIVERSAL * surfaceTemperature));
        }

        /**
         * Carbon-phenolic — the preferred high-performance ablative for
         * solid-rocket motor nozzles and re-entry vehicles.  High activation
         * energy gives low char rates at moderate temperatures; the dense char
         * layer provides excellent structural strength and insulation.
         *
         * <p>Char rate ≈ 0.22 mm/s at 1 000 K; ≈ 0.89 mm/s at 1 200 K.
         */
        public static final AblativeMaterial CARBON_PHENOLIC = new AblativeMaterial(
                "Carbon-Phenolic",
                1.0,       // A  [m/s]
                7.0e4,     // Ea [J/mol]
                1.0,       // k_char   [W/(m·K)]
                0.4,       // k_virgin [W/(m·K)]
                1600.0     // ρ  [kg/m³]
        );

        /**
         * Silica-phenolic — lower density than carbon-phenolic; used where
         * weight budget is tight and heat loads are moderate (e.g. upper-stage
         * SRM nozzle extensions).
         *
         * <p>Char rate ≈ 0.30 mm/s at 1 000 K; slightly less than carbon-phenolic.
         */
        public static final AblativeMaterial SILICA_PHENOLIC = new AblativeMaterial(
                "Silica-Phenolic",
                0.8,       // A  [m/s]
                6.5e4,     // Ea [J/mol]
                0.6,       // k_char   [W/(m·K)]
                0.35,      // k_virgin [W/(m·K)]
                1400.0     // ρ  [kg/m³]
        );

        /**
         * EPDM rubber — lower cost, lower activation energy; suitable for
         * moderate heat-flux nozzle extensions and motor cases.  Chars quickly
         * at relatively low surface temperatures.
         *
         * <p>Char rate ≈ 2.2 mm/s at 1 000 K.
         */
        public static final AblativeMaterial EPDM = new AblativeMaterial(
                "EPDM",
                0.5,       // A  [m/s]
                4.5e4,     // Ea [J/mol]
                0.4,       // k_char   [W/(m·K)]
                0.25,      // k_virgin [W/(m·K)]
                1150.0     // ρ  [kg/m³]
        );

        /**
         * Graphite — used for high-temperature throat inserts in SRMs.
         * Very low pre-exponential factor gives negligible char rate at the
         * wall temperatures reached with active or ablative insulation; failure
         * mode is mechanical erosion rather than pyrolysis.
         *
         * <p>Char rate ≈ 0.0007 mm/s at 1 000 K.
         */
        public static final AblativeMaterial GRAPHITE = new AblativeMaterial(
                "Graphite",
                1.0e-3,    // A  [m/s]
                6.0e4,     // Ea [J/mol]
                120.0,     // k_char   [W/(m·K)]
                60.0,      // k_virgin [W/(m·K)]
                1800.0     // ρ  [kg/m³]
        );

        /**
         * Carbon–carbon (C/C) composite — the preferred material for
         * high-performance SRM nozzle throat inserts and exit-cone liners
         * operating above ≈ 2 500 K.  The woven carbon–carbon structure
         * oxidizes extremely slowly via the Arrhenius mechanism; the dominant
         * failure mode is sublimation and particle-impingement erosion rather
         * than classic pyrolysis.
         *
         * <p>Very high thermal conductivity spreads heat rapidly, limiting
         * peak surface temperature and further reducing the already-negligible
         * Arrhenius char rate.  Pair with {@link AblativeNozzleModel#setErosionFactor(double)} to model
         * the alumina-particle erosion that governs C/C throat recession in
         * aluminised SRM propellants.
         *
         * <p>Char rate ≈ 2.0 × 10⁻⁵ mm/s at 2 500 K (Arrhenius only);
         * mechanical erosion typically dominates.
         *
         * <p>References: Kuo &amp; Acharya, <em>Fundamentals of Solid-Propellant
         * Combustion</em>, AIAA, 1984; Tran et al., "Ablative and Erosive
         * Characteristics of C/C Nozzles", AIAA 2002-3826.
         */
        public static final AblativeMaterial CARBON_CARBON = new AblativeMaterial(
                "Carbon-Carbon Composite",
                5.0e-5,    // A  [m/s] — very low, sublimation-limited
                1.1e5,     // Ea [J/mol] — high barrier; C oxidation kinetics
                50.0,      // k_char   [W/(m·K)] — C/C retains high k even charred
                40.0,      // k_virgin [W/(m·K)]
                1900.0     // ρ  [kg/m³]
        );
    }
}
