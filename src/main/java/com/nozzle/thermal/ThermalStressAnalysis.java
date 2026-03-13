package com.nozzle.thermal;

import com.nozzle.core.NozzleDesignParameters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Converts the HeatTransferModel wall-temperature and heat-flux results into
 * structural stresses and an estimated fatigue life for each point along the nozzle wall.
 *
 * <h2>Thermal stress model</h2>
 * Assumes a thin-walled axisymmetric shell with a linear temperature profile
 * through the wall thickness (Timoshenko, "Theory of Thermal Stresses", §12).
 * The temperature gradient is derived from the steady-state heat flux:
 * <pre>
 *   ΔT_wall = q_flux · t_wall / k_wall
 * </pre>
 * This produces a bending-type thermal stress at each surface:
 * <pre>
 *   σ_thermal = α · E · ΔT_wall / (2 · (1 − ν))
 * </pre>
 * Sign convention: compressive on the hot (gas) face, tensile on the cold (coolant) face.
 *
 * <h2>Pressure stress model</h2>
 * Thin-wall Lamé (Shigley's, §3-12) using chamber pressure as a conservative
 * upper bound (local static pressure is lower everywhere in the divergent section):
 * <pre>
 *   σ_hoop  = Pc · r / t_wall
 *   σ_axial = Pc · r / (2 · t_wall)
 * </pre>
 *
 * <h2>Combined von Mises stress</h2>
 * At the cold face (coolant side), both thermal and pressure stresses are tensile
 * — the most critical location for fracture and fatigue:
 * <pre>
 *   σ₁ = σ_hoop_P  + σ_thermal   (combined hoop)
 *   σ₂ = σ_axial_P + σ_thermal   (combined axial)
 *   σ_VM = √(σ₁² − σ₁·σ₂ + σ₂²)
 * </pre>
 *
 * <h2>Fatigue life</h2>
 * Each engine start-shutdown cycle is counted as one load reversal from
 * σ_VM (hot) → 0 (cold). Life is estimated via:
 * <ul>
 *   <li><b>Elastic regime</b> (σ_VM ≤ σ_yield): Basquin equation
 *       N_f = ½ · (σ′_f / σ_VM)^(1/b)</li>
 *   <li><b>Plastic regime</b> (σ_VM > σ_yield): Coffin-Manson equation
 *       with plastic strain amplitude Δε_p/2 ≈ (σ_VM/E) · (σ_VM/σ_yield − 1)</li>
 *   <li><b>Infinite life</b>: σ_VM &lt; 0.5 · σ_ultimate (below endurance limit)</li>
 * </ul>
 *
 * @see HeatTransferModel
 */
public class ThermalStressAnalysis {

    // -----------------------------------------------------------------------
    // Material library
    // -----------------------------------------------------------------------

    /**
     * Structural material properties for thermal-fatigue analysis.
     *
     * <p>All Basquin/Coffin-Manson coefficients are referenced to fully-reversed
     * (R = −1) uniaxial loading.  Sources:
     * <ul>
     *   <li>Inconel 718: Aerospace Structural Metals Handbook, AFML-TR-68-115</li>
     *   <li>SS 304:       Landgraf et al., SAE Paper 690196</li>
     *   <li>Cu-Cr-Zr:    NIST Materials Data Repository, NASA/TM-2004-213085</li>
     * </ul>
     *
     * @param name                    Human-readable material name
     * @param youngsModulus           E in Pa
     * @param thermalExpansionCoeff   α in 1/K
     * @param poissonsRatio           ν (dimensionless)
     * @param yieldStrength           σ_y in Pa (0.2% proof)
     * @param ultimateStrength        σ_u in Pa (UTS)
     * @param fatigueStrengthCoeff    σ′_f in Pa (Basquin intercept)
     * @param fatigueStrengthExp      b    (Basquin exponent, negative)
     * @param fatigueDuctilityCoeff   ε′_f (Coffin-Manson ductility coefficient)
     * @param fatigueDuctilityExp     c    (Coffin-Manson exponent, negative)
     */
    public record Material(
            String name,
            double youngsModulus,
            double thermalExpansionCoeff,
            double poissonsRatio,
            double yieldStrength,
            double ultimateStrength,
            double fatigueStrengthCoeff,
            double fatigueStrengthExp,
            double fatigueDuctilityCoeff,
            double fatigueDuctilityExp
    ) {
        /**
         * Inconel 718 — nickel superalloy, common for regeneratively cooled
         * thrust chambers and turbopump components.
         * E = 207 GPa, α = 13 × 10⁻⁶ /K, σ_y = 1100 MPa.
         */
        public static final Material INCONEL_718 = new Material(
                "Inconel 718",
                207e9, 13e-6, 0.29,
                1100e6, 1380e6,
                1700e6, -0.08,
                1.50, -0.65
        );

        /**
         * Stainless steel 304 — used in lower-temperature nozzle sections
         * and test articles.
         * E = 193 GPa, α = 17.2 × 10⁻⁶ /K, σ_y = 310 MPa.
         */
        public static final Material STAINLESS_304 = new Material(
                "Stainless Steel 304",
                193e9, 17.2e-6, 0.29,
                310e6, 620e6,
                1000e6, -0.114,
                0.17, -0.402
        );

        /**
         * Copper-chromium-zirconium alloy (Cu-Cr-Zr / C18150) — high thermal
         * conductivity liner material for regeneratively cooled chambers
         * (e.g., RS-68, Vulcain, SSME inner liner).
         * E = 130 GPa, α = 17 × 10⁻⁶ /K, σ_y = 380 MPa.
         */
        public static final Material COPPER_ALLOY_CuCrZr = new Material(
                "Cu-Cr-Zr (C18150)",
                130e9, 17.0e-6, 0.34,
                380e6, 450e6,
                550e6, -0.08,
                0.50, -0.50
        );

        /**
         * Returns an endurance limit estimate equal to 50 % of the ultimate tensile
         * strength — a conservative lower bound for Ni-based and stainless steel alloys.
         *
         * @return Endurance limit in Pa (= 0.5 × σ_u)
         */
        public double enduranceLimit() {
            return 0.5 * ultimateStrength;
        }
    }

    // -----------------------------------------------------------------------
    // Result record
    // -----------------------------------------------------------------------

    /**
     * Structural analysis results at one axial wall position.
     *
     * @param x                  Axial position (m)
     * @param y                  Radial position / wall radius (m)
     * @param wallTemperature    Hot-face (gas-side) wall temperature (K)
     * @param deltaT             Through-wall temperature drop (K)
     * @param thermalHoopStress  Thermal hoop stress at the cold face (Pa, tensile +)
     * @param pressureHoopStress Pressure-induced hoop stress (Pa, tensile +)
     * @param pressureAxialStress Pressure-induced axial stress (Pa, tensile +)
     * @param vonMisesStress     Combined von Mises stress at cold face (Pa)
     * @param safetyFactor       σ_yield / σ_VM (values &lt; 1 indicate yielding)
     * @param estimatedCycles    Fatigue life in start-shutdown cycles to crack initiation
     */
    public record WallStressPoint(
            double x,
            double y,
            double wallTemperature,
            double deltaT,
            double thermalHoopStress,
            double pressureHoopStress,
            double pressureAxialStress,
            double vonMisesStress,
            double safetyFactor,
            double estimatedCycles
    ) {
        @SuppressWarnings("NullableProblems")
        @Override
        public String toString() {
            return String.format(
                    "StressPoint[x=%.4f m, T_w=%.0f K, ΔT=%.0f K, σ_VM=%.1f MPa, SF=%.2f, N_f=%.2e]",
                    x, wallTemperature, deltaT, vonMisesStress / 1e6, safetyFactor, estimatedCycles);
        }
    }

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final NozzleDesignParameters parameters;
    private final List<HeatTransferModel.WallThermalPoint> thermalProfile;
    private final Material material;
    private final double wallThickness;    // m
    private final double wallConductivity; // W/(m·K)
    private final List<WallStressPoint> stressProfile = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    /**
     * Creates a thermal stress analyzer.
     *
     * @param parameters       Nozzle design parameters (used for chamber pressure)
     * @param thermalProfile   Output from {@link HeatTransferModel#getWallThermalProfile()}
     * @param material         Wall structural material
     * @param wallThickness    Wall thickness in m (must match value used in HeatTransferModel)
     * @param wallConductivity Wall thermal conductivity in W/(m·K) (must match HeatTransferModel)
     */
    public ThermalStressAnalysis(NozzleDesignParameters parameters,
                                  List<HeatTransferModel.WallThermalPoint> thermalProfile,
                                  Material material,
                                  double wallThickness,
                                  double wallConductivity) {
        this.parameters = parameters;
        this.thermalProfile = List.copyOf(thermalProfile);
        this.material = material;
        this.wallThickness = wallThickness;
        this.wallConductivity = wallConductivity;
    }

    // -----------------------------------------------------------------------
    // Calculation
    // -----------------------------------------------------------------------

    /**
     * Computes stress and fatigue life at every wall point.
     *
     * @return This instance (for chaining)
     */
    public ThermalStressAnalysis calculate() {
        stressProfile.clear();
        for (HeatTransferModel.WallThermalPoint tp : thermalProfile) {
            stressProfile.add(analysePoint(tp));
        }
        return this;
    }

    /**
     * Performs the full stress and fatigue analysis at a single wall point.
     * Executes steps 1–7: ΔT from heat flux, thermal stress, Lamé pressure
     * stresses, combined stresses, von Mises, safety factor, and fatigue life.
     *
     * @param tp Thermal data point from {@link HeatTransferModel}
     * @return {@link WallStressPoint} containing all computed structural results
     */
    private WallStressPoint analysePoint(HeatTransferModel.WallThermalPoint tp) {
        // --- 1. Through-wall temperature gradient ----------------------------
        // ΔT = q_flux · R_wall = q_flux · t / k
        // Use max(0, q) so that radiation-dominated points give ΔT = 0.
        double qFlux = Math.max(0.0, tp.totalHeatFlux());
        double deltaT = qFlux * wallThickness / wallConductivity;

        // --- 2. Thermal stress at cold face (tensile, worst case) -----------
        // σ_thermal = α · E · ΔT / (2(1 − ν))   [Timoshenko §12]
        double sigThermal = material.thermalExpansionCoeff()
                * material.youngsModulus()
                * deltaT
                / (2.0 * (1.0 - material.poissonsRatio()));

        // --- 3. Pressure stresses (thin-wall Lamé, conservative using Pc) ---
        // Chamber pressure is the upper bound; local static pressure is lower
        // in the divergent section.  This gives a conservative estimate.
        double radius = tp.y();           // inner wall radius ≈ contour radius
        double Pc = parameters.chamberPressure();
        double sigHoopP  = Pc * radius / wallThickness;
        double sigAxialP = Pc * radius / (2.0 * wallThickness);

        // --- 4. Combined stresses at cold face (both thermal and pressure tensile) ---
        double sigHoop  = sigHoopP  + sigThermal;
        double sigAxial = sigAxialP + sigThermal;

        // --- 5. Von Mises stress (biaxial, σ₃ ≈ 0 for thin wall) -----------
        double sigVM = Math.sqrt(sigHoop * sigHoop
                - sigHoop * sigAxial
                + sigAxial * sigAxial);

        // --- 6. Safety factor ------------------------------------------------
        double safetyFactor = material.yieldStrength() / sigVM;

        // --- 7. Fatigue life estimate ----------------------------------------
        double cycles = estimateFatigueCycles(sigVM);

        return new WallStressPoint(
                tp.x(), tp.y(), tp.wallTemperature(),
                deltaT, sigThermal, sigHoopP, sigAxialP,
                sigVM, safetyFactor, cycles);
    }

    /**
     * Estimates fatigue life (start-shutdown cycles) using Basquin's equation
     * for elastic loading and the Coffin-Manson relation for plastic loading.
     *
     * @param sigVM Von Mises stress amplitude (Pa)
     * @return Estimated cycles to crack initiation
     */
    private double estimateFatigueCycles(double sigVM) {
        // Below the endurance limit → effectively infinite life
        if (sigVM < material.enduranceLimit()) {
            return Double.POSITIVE_INFINITY;
        }

        // Basquin: σ_a = σ′_f · (2N_f)^b  →  N_f = ½ · (σ′_f / σ_a)^(1/b)
        // b is negative, so (1/b) is also negative → result is finite for σ > σ′_f at 1 cycle
        double nfElastic = 0.5 * Math.pow(
                material.fatigueStrengthCoeff() / sigVM,
                1.0 / material.fatigueStrengthExp());

        if (sigVM <= material.yieldStrength()) {
            return Math.max(0.5, nfElastic);   // fully elastic
        }

        // Coffin-Manson (plastic regime):
        // Plastic strain amplitude ≈ (σ_VM / E) · (σ_VM / σ_yield − 1)
        double deltaEpHalf = (sigVM / material.youngsModulus())
                * (sigVM / material.yieldStrength() - 1.0);
        double nfPlastic = 0.5 * Math.pow(
                deltaEpHalf / material.fatigueDuctilityCoeff(),
                1.0 / material.fatigueDuctilityExp());

        // Return the lower (more conservative) of the two estimates
        return Math.max(0.5, Math.min(nfElastic, nfPlastic));
    }

    // -----------------------------------------------------------------------
    // Result accessors
    // -----------------------------------------------------------------------

    /**
     * Returns the per-point stress and fatigue profile computed by the last call
     * to {@link #calculate()}.
     *
     * @return Unmodifiable list of {@link WallStressPoint}s in wall-contour order;
     *         empty if {@link #calculate()} has not yet been called
     */
    public List<WallStressPoint> getStressProfile() {
        return Collections.unmodifiableList(stressProfile);
    }

    /**
     * Returns the wall point with the highest von Mises stress (the critical
     * structural location for fatigue crack initiation).
     *
     * @return The {@link WallStressPoint} with the maximum {@code σ_VM}
     * @throws IllegalStateException if {@link #calculate()} has not been called yet
     */
    public WallStressPoint getCriticalPoint() {
        return stressProfile.stream()
                .max(Comparator.comparingDouble(WallStressPoint::vonMisesStress))
                .orElseThrow(() -> new IllegalStateException("No stress points; call calculate() first"));
    }

    /**
     * Returns the maximum von Mises stress along the nozzle wall.
     *
     * @return Peak σ_VM in Pa
     * @throws IllegalStateException if {@link #calculate()} has not been called yet
     */
    public double getMaxVonMisesStress() {
        return getCriticalPoint().vonMisesStress();
    }

    /**
     * Returns the minimum safety factor along the nozzle wall
     * ({@code σ_yield / σ_VM}); values below 1.0 indicate local yielding.
     *
     * @return Minimum safety factor (dimensionless)
     * @throws IllegalStateException if {@link #calculate()} has not been called yet
     */
    public double getMinSafetyFactor() {
        return stressProfile.stream()
                .mapToDouble(WallStressPoint::safetyFactor)
                .min()
                .orElseThrow(() -> new IllegalStateException("No stress points; call calculate() first"));
    }

    /**
     * Returns the minimum estimated fatigue life across all wall points.
     * Points below the endurance limit (infinite life) are excluded from the
     * minimum search; if every point has infinite life, {@link Double#POSITIVE_INFINITY}
     * is returned.
     *
     * @return Minimum finite fatigue life in start-shutdown cycles, or
     *         {@link Double#POSITIVE_INFINITY} if no point exceeds the endurance limit
     */
    public double getMinFatigueCycles() {
        return stressProfile.stream()
                .mapToDouble(WallStressPoint::estimatedCycles)
                .filter(Double::isFinite)
                .min()
                .orElse(Double.POSITIVE_INFINITY);
    }

    /**
     * Returns the maximum through-wall temperature drop (ΔT = q · t / k) along
     * the nozzle wall.
     *
     * @return Maximum ΔT in K; returns {@code 0.0} if the profile is empty
     */
    public double getMaxDeltaT() {
        return stressProfile.stream()
                .mapToDouble(WallStressPoint::deltaT)
                .max()
                .orElse(0.0);
    }

    /**
     * Returns the structural material used for all stress and fatigue calculations
     * in this analysis.
     *
     * @return The {@link Material} supplied at construction time
     */
    public Material getMaterial() {
        return material;
    }
}
