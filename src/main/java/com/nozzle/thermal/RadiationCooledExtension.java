package com.nozzle.thermal;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.geometry.Point2D;
import com.nozzle.moc.CharacteristicPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Models the thermal equilibrium of a radiation-cooled nozzle extension.
 *
 * <p>Large nozzle extensions — like the niobium-alloy skirt on the RL-10 or the
 * rhenium/iridium extensions used on deep-space engines — are not actively cooled.
 * The wall temperature reaches a steady state when the incoming gas-side convective
 * heat flux equals the outgoing radiative flux to the environment:
 *
 * <pre>  h_gas · (T_aw − T_wall) = ε · σ · (T_wall⁴ − T_env⁴)</pre>
 *
 * <p>This nonlinear equation is solved at each axial station by a Newton–Raphson
 * iteration.  The Bartz convective coefficient {@code h_gas} is re-evaluated at
 * each Newton step using the Eckert reference-temperature method so that the
 * dependence of gas-property corrections on {@code T_wall} is captured.
 * Convergence is typically reached in 5–10 iterations to within 0.1 K.
 *
 * <p>Usage:
 * <pre>{@code
 * RadiationCooledExtension ext = new RadiationCooledExtension(params, contour)
 *         .setMaterial(RadiationCooledExtension.ExtensionMaterial.NIOBIUM_C103)
 *         .setExtensionStartX(0.12)          // radiation cooling begins at x = 120 mm
 *         .setEnvironmentTemperature(3.0)    // space environment
 *         .calculate(List.of());             // no MOC flow points: uses fallback conditions
 *
 * double peakWall = ext.getMaxWallTemperature();
 * boolean safe    = !ext.isOvertemperatureAnywhere();
 * }</pre>
 *
 * <p>Reference: Sutton, G.P. &amp; Biblarz, O., <em>Rocket Propulsion Elements</em>,
 * 9th ed., §8.3; Bartz, D.R., "A Simple Equation for Rapid Estimation of Rocket
 * Nozzle Convective Heat Transfer Coefficients", <em>Jet Propulsion</em>, 1957.
 */
public class RadiationCooledExtension {

    /** Stefan–Boltzmann constant [W·m⁻²·K⁻⁴]. */
    private static final double STEFAN_BOLTZMANN = 5.67e-8;

    /**
     * Step size for the second-derivative finite difference used in the local
     * radius-of-curvature calculation [m].  Small enough for 1 mm contour spacing,
     * large enough to avoid numerical noise.
     */
    private static final double CURVATURE_STEP = 1.0e-4;

    private final NozzleDesignParameters parameters;
    private final NozzleContour contour;

    private ExtensionMaterial material = ExtensionMaterial.NIOBIUM_C103;

    /**
     * Axial position [m] at which radiation cooling begins.  Contour points with
     * {@code x < extensionStartX} are skipped.  Default: process the full contour.
     */
    private double extensionStartX = Double.NEGATIVE_INFINITY;

    /**
     * Surrounding environment temperature [K].
     * Use ≈ 3 K for deep-space vacuum; use 300 K for sea-level ground-test conditions.
     */
    private double environmentTemperature = 3.0;

    /** Maximum Newton–Raphson iterations per station before accepting the current value. */
    private int maxNewtonIterations = 50;

    /** Convergence threshold: iteration stops when |ΔT_wall| < tolerance [K]. */
    private double convergenceTolerance = 0.1;

    private final List<ExtensionPoint> profile = new ArrayList<>();

    /**
     * Creates a radiation-cooled extension model with default material
     * ({@link ExtensionMaterial#NIOBIUM_C103}), space environment (3 K), and
     * Newton convergence tolerance of 0.1 K.
     *
     * @param parameters Design parameters supplying gas stagnation conditions
     * @param contour    Nozzle contour covering the extension section
     */
    public RadiationCooledExtension(NozzleDesignParameters parameters, NozzleContour contour) {
        this.parameters = parameters;
        this.contour = contour;
    }

    /**
     * Sets the wall material of the extension.
     *
     * @param material Extension wall material
     * @return This instance
     */
    public RadiationCooledExtension setMaterial(ExtensionMaterial material) {
        this.material = material;
        return this;
    }

    /**
     * Sets the axial position at which the radiation-cooled section begins.
     * Contour points upstream of this position are excluded from the profile.
     *
     * <p>Typically set to the axial position where regenerative cooling ends —
     * i.e. the flange at the downstream end of the regeneratively cooled chamber.
     * The throat is at {@code x = 0}; the diverging section has {@code x > 0}.
     *
     * @param x Start position in m (throat is at x = 0)
     * @return This instance
     */
    public RadiationCooledExtension setExtensionStartX(double x) {
        this.extensionStartX = x;
        return this;
    }

    /**
     * Sets the environment temperature used in the radiation balance.
     *
     * <ul>
     *   <li>Deep-space vacuum: ≈ 3 K (cosmic microwave background)</li>
     *   <li>Low-Earth orbit: ≈ 200–280 K (solar + albedo flux equivalent)</li>
     *   <li>Sea-level ground test: ≈ 300 K</li>
     * </ul>
     *
     * @param temperature Environment temperature in K (must be ≥ 0)
     * @return This instance
     * @throws IllegalArgumentException if {@code temperature} is negative
     */
    public RadiationCooledExtension setEnvironmentTemperature(double temperature) {
        if (temperature < 0.0) {
            throw new IllegalArgumentException(
                    "environmentTemperature must be >= 0, got: " + temperature);
        }
        this.environmentTemperature = temperature;
        return this;
    }

    /**
     * Sets the Newton–Raphson convergence tolerance.
     * Iteration stops when the wall-temperature correction {@code |ΔT_wall|} falls
     * below this value.  The default of 0.1 K is sufficient for all engineering
     * applications; tighter values may be used for validation studies.
     *
     * @param tolerance Convergence tolerance in K (must be &gt; 0)
     * @return This instance
     * @throws IllegalArgumentException if {@code tolerance} is not positive
     */
    public RadiationCooledExtension setConvergenceTolerance(double tolerance) {
        if (tolerance <= 0.0) {
            throw new IllegalArgumentException(
                    "convergenceTolerance must be > 0, got: " + tolerance);
        }
        this.convergenceTolerance = tolerance;
        return this;
    }

    /**
     * Sets the maximum number of Newton–Raphson iterations per station.
     * The iteration stops early when the temperature correction falls below
     * {@link #setConvergenceTolerance the convergence tolerance}.  The default
     * of 50 is sufficient for all engineering cases; smaller values may be used
     * to study convergence behavior.
     *
     * @param maxIterations Maximum iterations per station (must be &gt; 0)
     * @return This instance
     * @throws IllegalArgumentException if {@code maxIterations} is not positive
     */
    public RadiationCooledExtension setMaxNewtonIterations(int maxIterations) {
        if (maxIterations <= 0) {
            throw new IllegalArgumentException(
                    "maxNewtonIterations must be > 0, got: " + maxIterations);
        }
        this.maxNewtonIterations = maxIterations;
        return this;
    }

    /**
     * Computes the equilibrium wall temperature profile along the extension.
     *
     * <p>For each contour point at {@code x ≥ extensionStartX}:
     * <ol>
     *   <li>Local gas temperature and Mach number are taken from the nearest
     *       point in {@code flowPoints} (linear nearest-neighbor search), or
     *       estimated from isentropic relations when the list is empty.</li>
     *   <li>The adiabatic wall (recovery) temperature is computed as
     *       {@code T_aw = T_gas · (1 + r · (γ−1)/2 · M²)} with turbulent
     *       recovery factor {@code r = Pr^(1/3)}.</li>
     *   <li>Newton–Raphson iteration solves the equilibrium balance
     *       {@code h_gas·(T_aw − T_wall) = ε·σ·(T_wall⁴ − T_env⁴)}.</li>
     *   <li>An {@link ExtensionPoint} is recorded with all thermal quantities
     *       and the temperature margin to the material limit.</li>
     * </ol>
     *
     * @param flowPoints MOC flow-field points for local condition lookup;
     *                   may be {@code null} or empty (triggers isentropic fallback)
     * @return This instance
     */
    public RadiationCooledExtension calculate(List<CharacteristicPoint> flowPoints) {
        profile.clear();

        List<Point2D> contourPoints = contour.getContourPoints();
        if (contourPoints.isEmpty()) {
            contour.generate(100);
            contourPoints = contour.getContourPoints();
        }

        GasProperties gas = parameters.gasProperties();
        double gamma = gas.gamma();
        double Pr = 4.0 * gamma / (9.0 * gamma - 5.0);       // Eucken relation
        double recoveryFactor = Math.pow(Pr, 1.0 / 3.0);      // turbulent BL

        for (Point2D point : contourPoints) {
            if (point.x() < extensionStartX) continue;

            // Local gas temperature and Mach from nearest MOC point (linear scan).
            // The extension spans O(10–50) contour points and the flow-point list is
            // O(100–500); O(n×m) is negligible compared to the Newton iterations.
            double T_gas;
            double mach;
            if (flowPoints != null && !flowPoints.isEmpty()) {
                CharacteristicPoint nearest = nearestFlowPoint(point, flowPoints);
                T_gas = nearest.temperature();
                mach  = nearest.mach();
            } else {
                // Isentropic fallback: estimate local Mach from area ratio
                double areaRatio = (point.y() * point.y())
                        / (parameters.throatRadius() * parameters.throatRadius());
                mach  = estimateMachFromAreaRatio(areaRatio, gamma, maxNewtonIterations);
                T_gas = parameters.chamberTemperature()
                        / (1.0 + (gamma - 1.0) / 2.0 * mach * mach);
            }

            // Adiabatic (recovery) wall temperature
            double T_aw = T_gas * (1.0 + recoveryFactor * (gamma - 1.0) / 2.0 * mach * mach);

            // Solve equilibrium: h·(T_aw - T_wall) = ε·σ·(T_wall⁴ - T_env⁴)
            double T_wall = solveEquilibriumTemperature(point.x(), point.y(), T_gas, T_aw);

            // Final thermal quantities at converged T_wall
            double hGas   = computeBartzH(point.x(), point.y(), T_gas, T_aw, T_wall);
            double qConv  = hGas * (T_aw - T_wall);
            double qRad   = material.emissivity() * STEFAN_BOLTZMANN
                    * (Math.pow(T_wall, 4) - Math.pow(environmentTemperature, 4));

            boolean overtemp = T_wall > material.temperatureLimit();
            double margin    = material.temperatureLimit() - T_wall;

            profile.add(new ExtensionPoint(
                    point.x(), point.y(), T_wall, T_aw, qConv, qRad, hGas, margin, overtemp));
        }

        return this;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Solves {@code h_gas·(T_aw − T_w) = ε·σ·(T_w⁴ − T_env⁴)} for {@code T_w}
     * using Newton–Raphson iteration.
     *
     * <p>{@code h_gas} is re-evaluated at each step via the Bartz/Eckert method
     * so that the gas-property correction for high wall temperatures is captured.
     * The derivative used is the simplified form
     * {@code f'(T_w) ≈ 4·ε·σ·T_w³ + h_gas}, which omits {@code ∂h/∂T_w} (a
     * second-order term) and is sufficient for convergence within a few steps.
     *
     * @param x     Axial position in m
     * @param y     Radial position (wall radius) in m
     * @param T_gas Local static gas temperature in K
     * @param T_aw  Adiabatic wall (recovery) temperature in K
     * @return Equilibrium wall temperature in K
     */
    private double solveEquilibriumTemperature(double x, double y, double T_gas, double T_aw) {
        double eps    = material.emissivity();
        double T_env4 = Math.pow(environmentTemperature, 4);

        // Initial guess: 70 % of T_aw.  At this temperature the radiation term
        // typically under-shoots the convection term, so Newton steps increase T_w
        // monotonically toward the equilibrium, giving stable convergence.
        double T_w = 0.7 * T_aw;

        for (int i = 0; i < maxNewtonIterations; i++) {
            double hGas  = computeBartzH(x, y, T_gas, T_aw, T_w);
            double qRad  = eps * STEFAN_BOLTZMANN * (Math.pow(T_w, 4) - T_env4);
            double qConv = hGas * (T_aw - T_w);
            double f     = qRad - qConv;
            double df    = 4.0 * eps * STEFAN_BOLTZMANN * Math.pow(T_w, 3) + hGas;
            double delta = -f / df;

            T_w = Math.max(environmentTemperature, Math.min(T_w + delta, T_aw));

            if (Math.abs(delta) < convergenceTolerance) break;
        }

        return T_w;
    }

    /**
     * Computes the gas-side convective heat-transfer coefficient using the Bartz
     * correlation with the Eckert reference-temperature correction.
     *
     * <p>This variant has no coolant-side resistance; {@code T_wall} is used
     * directly as the wall-temperature boundary condition for the reference
     * temperature {@code T* = 0.5·(T_wall + T_gas) + 0.22·√Pr·(T_aw − T_gas)}.
     *
     * @param x      Axial position in m
     * @param y      Local wall radius in m
     * @param T_gas  Local static gas temperature in K
     * @param T_aw   Adiabatic wall (recovery) temperature in K
     * @param T_wall Current wall temperature estimate in K
     * @return Gas-side heat-transfer coefficient in W/(m²·K)
     */
    private double computeBartzH(double x, double y, double T_gas, double T_aw, double T_wall) {
        GasProperties gas = parameters.gasProperties();
        double gamma = gas.gamma();
        double Pr    = 4.0 * gamma / (9.0 * gamma - 5.0);

        double rt   = parameters.throatRadius();
        double Dt   = 2.0 * rt;
        double At   = Math.PI * rt * rt;
        double A    = Math.PI * y * y;
        double r_c  = localRadiusOfCurvature(x);

        // Mass flux at throat: G* = P_c / c*
        double G_Star = parameters.chamberPressure() / parameters.characteristicVelocity();

        // Eckert reference temperature (Eckert 1955)
        double T_star  = 0.5 * (T_wall + T_gas) + 0.22 * Math.sqrt(Pr) * (T_aw - T_gas);
        double mu_star = gas.calculateViscosity(T_star);
        double cp_star = gas.specificHeatCp();

        return (0.026 / Math.pow(Dt, 0.2))
                * (Math.pow(mu_star, 0.2) * cp_star / Math.pow(Pr, 0.6))
                * Math.pow(G_Star, 0.8)
                * Math.pow(Dt / r_c, 0.1)
                * Math.pow(At / A, 0.9);
    }

    /**
     * Computes the local wall radius of curvature at axial position {@code x}
     * using the central-difference second derivative of the contour radius.
     *
     * <p>Uses {@link NozzleContour#getSlopeAt(double)} (which is itself a
     * central-difference first derivative) to avoid duplicating finite-difference
     * logic.  The result is capped at {@code 10 × r_throat} so the Bartz
     * curvature correction term remains bounded on near-straight wall sections.
     *
     * @param x Axial position in m
     * @return Local radius of curvature in m
     */
    private double localRadiusOfCurvature(double x) {
        double dydx   = contour.getSlopeAt(x);
        double d2ydx2 = (contour.getSlopeAt(x + CURVATURE_STEP)
                       - contour.getSlopeAt(x - CURVATURE_STEP))
                      / (2.0 * CURVATURE_STEP);

        if (Math.abs(d2ydx2) < 1e-10) {
            return 10.0 * parameters.throatRadius();
        }

        double r_c = Math.pow(1.0 + dydx * dydx, 1.5) / Math.abs(d2ydx2);
        return Math.min(r_c, 10.0 * parameters.throatRadius());
    }

    /**
     * Returns the nearest flow point to {@code contourPoint} by Euclidean distance
     * in the (x, y) plane.  Linear scan is acceptable because the extension spans
     * O(10–50) contour points and the flow-point list is O(100–500) entries.
     *
     * @param contourPoint Query point on the nozzle wall
     * @param flowPoints   Non-empty list of MOC flow-field points
     * @return Nearest {@link CharacteristicPoint}
     */
    private static CharacteristicPoint nearestFlowPoint(Point2D contourPoint,
                                                         List<CharacteristicPoint> flowPoints) {
        CharacteristicPoint nearest = flowPoints.getFirst();
        double minDistSq = distSq(contourPoint, nearest);
        for (int i = 1; i < flowPoints.size(); i++) {
            double dSq = distSq(contourPoint, flowPoints.get(i));
            if (dSq < minDistSq) {
                minDistSq = dSq;
                nearest   = flowPoints.get(i);
            }
        }
        return nearest;
    }

    private static double distSq(Point2D p, CharacteristicPoint cp) {
        double dx = p.x() - cp.x();
        double dy = p.y() - cp.y();
        return dx * dx + dy * dy;
    }

    /**
     * Estimates the local Mach number from an area ratio using a Newton iteration
     * on the isentropic area-Mach relation for the supersonic branch.
     *
     * <p>Used only when no MOC flow-point list is provided.
     *
     * @param areaRatio    {@code A / A_throat} (must be ≥ 1)
     * @param gamma        Specific heat ratio
     * @param maxIterations Maximum Newton iterations before returning the current estimate
     * @return Supersonic Mach number corresponding to {@code areaRatio}
     */
    private static double estimateMachFromAreaRatio(double areaRatio, double gamma,
                                                     int maxIterations) {
        // Start from 1.5 and walk to supersonic root
        double M = Math.max(1.5, areaRatio * 0.5);
        double gm1 = gamma - 1.0;
        double exp = (gamma + 1.0) / (gm1);

        for (int i = 0; i < maxIterations; i++) {
            double t    = 1.0 + gm1 / 2.0 * M * M;
            double u_k  = Math.pow(2.0 / (gamma + 1.0) * t, 0.5 * exp);
            double f    = u_k / M - areaRatio;
            // df/dM = u^k · ((γ+1)/(2t) − 1/M²)  [correct derivative of u^k/M]
            double dfDM = u_k * ((gamma + 1.0) / (2.0 * t) - 1.0 / (M * M));
            double delta = -f / dfDM;
            M += delta;
            M = Math.max(1.001, M);
            if (Math.abs(delta) < 1e-6) break;
        }
        return M;
    }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------

    /**
     * Returns the equilibrium thermal profile computed by the most recent
     * {@link #calculate} call, one point per extension contour station.
     *
     * @return Unmodifiable snapshot of the profile; empty before
     *         {@link #calculate} is called
     */
    public List<ExtensionPoint> getProfile() {
        return new ArrayList<>(profile);
    }

    /**
     * Returns the maximum wall temperature across all extension stations.
     *
     * @return Maximum wall temperature in K; 0 if {@link #calculate} has not
     *         been called
     */
    public double getMaxWallTemperature() {
        return profile.stream()
                .mapToDouble(ExtensionPoint::wallTemperature)
                .max()
                .orElse(0.0);
    }

    /**
     * Returns the minimum temperature margin ({@code T_limit − T_wall}) across
     * all extension stations.  Negative values indicate an overtemperature condition.
     *
     * @return Minimum temperature margin in K; 0 if {@link #calculate} has not
     *         been called
     */
    public double getMinTemperatureMargin() {
        return profile.stream()
                .mapToDouble(ExtensionPoint::temperatureMargin)
                .min()
                .orElse(0.0);
    }

    /**
     * Returns {@code true} if any station exceeds the material temperature limit.
     *
     * @return {@code true} if any station is overtemperature
     */
    public boolean isOvertemperatureAnywhere() {
        return profile.stream().anyMatch(ExtensionPoint::isOvertemperature);
    }

    /**
     * Returns the total radiated power [W] integrated over the extension surface
     * using the trapezoidal rule on annular frustums.
     *
     * @return Total radiated power in W; 0 if fewer than two stations are
     *         available or {@link #calculate} has not been called
     */
    public double getTotalRadiatedPower() {
        if (profile.size() < 2) return 0.0;

        double total = 0.0;
        for (int i = 0; i < profile.size() - 1; i++) {
            ExtensionPoint a = profile.get(i);
            ExtensionPoint b = profile.get(i + 1);
            double dx    = Math.abs(b.x() - a.x());
            double rMean = (a.y() + b.y()) * 0.5;
            double qMean = (a.radiativeHeatFlux() + b.radiativeHeatFlux()) * 0.5;
            total += qMean * 2.0 * Math.PI * rMean * dx;
        }
        return total;
    }

    // -----------------------------------------------------------------------
    // Nested types
    // -----------------------------------------------------------------------

    /**
     * Immutable snapshot of the equilibrium thermal state at a single axial
     * station of the radiation-cooled extension.
     *
     * @param x                  Axial position in m (from the throat)
     * @param y                  Radial position (wall radius) in m
     * @param wallTemperature    Equilibrium gas-side wall temperature in K,
     *                           satisfying {@code q_conv = q_rad}
     * @param recoveryTemperature Adiabatic wall (recovery) temperature in K;
     *                           {@code T_wall} converges toward this as
     *                           radiation efficiency decreases
     * @param convectiveHeatFlux Gas-side convective heat flux in W/m²:
     *                           {@code h_gas · (T_aw − T_wall)}
     * @param radiativeHeatFlux  Outward radiative flux in W/m²:
     *                           {@code ε · σ · (T_wall⁴ − T_env⁴)}
     * @param heatTransferCoeff  Bartz gas-side heat-transfer coefficient in W/(m²·K)
     * @param temperatureMargin  {@code T_limit − T_wall} in K; negative when the
     *                           equilibrium temperature exceeds the material limit
     * @param isOvertemperature  {@code true} when {@code T_wall > T_limit}
     */
    public record ExtensionPoint(
            double x,
            double y,
            double wallTemperature,
            double recoveryTemperature,
            double convectiveHeatFlux,
            double radiativeHeatFlux,
            double heatTransferCoeff,
            double temperatureMargin,
            boolean isOvertemperature
    ) {
        /**
         * Returns the heat-flux residual {@code q_conv − q_rad} [W/m²].
         * At exact convergence this is zero; the magnitude indicates the quality
         * of the Newton–Raphson solution.
         *
         * @return Heat-flux balance residual in W/m²
         */
        public double heatFluxBalance() {
            return convectiveHeatFlux - radiativeHeatFlux;
        }

        @SuppressWarnings("NullableProblems")
        @Override
        public String toString() {
            return String.format(
                    "Extension[x=%.4f, Tw=%.0f K, margin=%.0f K%s]",
                    x, wallTemperature, temperatureMargin,
                    isOvertemperature ? " OVERTEMP" : "");
        }
    }

    /**
     * Wall material of the radiation-cooled extension, characterized by its
     * hemispherical total emissivity, temperature limit, density, and thermal
     * conductivity.
     *
     * <p>All built-in presets reflect representative values from open literature;
     * the emissivities correspond to oxidized or coated surfaces at operating
     * temperature, as these conditions are typical in service.
     *
     * @param name               Human-readable material name
     * @param emissivity         Hemispherical total emissivity {@code ε} (0–1)
     * @param temperatureLimit   Maximum allowable continuous-use wall temperature
     *                           in K; used to compute {@link ExtensionPoint#temperatureMargin()}
     * @param density            Material density in kg/m³; used for mass budgeting
     * @param thermalConductivity Thermal conductivity in W/(m·K) at operating temperature
     */
    public record ExtensionMaterial(
            String name,
            double emissivity,
            double temperatureLimit,
            double density,
            double thermalConductivity
    ) {
        /**
         * Niobium C-103 alloy (Nb–10Hf–1Ti) — the standard material for
         * radiation-cooled nozzle extensions on high-performance hydrolox engines
         * such as the RL-10, Vinci, and HM7B.  Offers excellent formability and
         * weldability; coated with disilicide ({@code NbSi₂}) to prevent rapid
         * oxidation above ≈ 700 K.  The disilicide coating raises emissivity to
         * ≈ 0.8 and is included in the temperature limit.
         *
         * <p>Typical service temperature on the RL-10A-4: 1 250–1 450 K.
         */
        public static final ExtensionMaterial NIOBIUM_C103 = new ExtensionMaterial(
                "Niobium C-103 (NbSi₂ coated)",
                0.80,    // ε — disilicide-coated surface at operating temperature
                1450.0,  // T_limit [K]
                8860.0,  // ρ [kg/m³]
                55.0     // k [W/(m·K)]
        );

        /**
         * Rhenium with iridium coating (Re/Ir) — used for thruster chambers and
         * high-performance extensions requiring continuous-use temperatures above
         * the niobium alloy limit.  The iridium coating ({@code ≈ 50 µm}) provides
         * oxidation resistance and raises emissivity.  Used on the Deep Space 1
         * and Cassini AACS thrusters, and in development for upper-stage engines.
         *
         * <p>Very high density limits its use to small-diameter, thin-walled
         * extensions where mass budget is secondary to performance.
         */
        public static final ExtensionMaterial RHENIUM_IRIDIUM = new ExtensionMaterial(
                "Rhenium / Iridium coating",
                0.85,    // ε — iridium surface at operating temperature
                2200.0,  // T_limit [K]
                19300.0, // ρ [kg/m³] — Re dominated
                48.0     // k [W/(m·K)]
        );

        /**
         * Titanium 6Al-4V alloy — suitable for nozzle extensions on low-heat-flux
         * upper stages (e.g. apogee kick motors) where the gas-side heat flux in the
         * supersonic extension is modest.  Low density makes it attractive for mass-
         * constrained spacecraft propulsion.  Maximum temperature is set by the rapid
         * drop in yield strength above ≈ 750 K; short-duration excursions to 850 K
         * are survivable but should be avoided in the design margin.
         */
        public static final ExtensionMaterial TITANIUM_6AL_4V = new ExtensionMaterial(
                "Titanium 6Al-4V",
                0.60,   // ε — anodized/oxidized surface
                800.0,  // T_limit [K]
                4430.0, // ρ [kg/m³]
                6.7     // k [W/(m·K)]
        );

        /**
         * Carbon–carbon (C/C) composite — used for very-high-temperature extensions
         * in oxidizer-lean or inert-exhaust environments (e.g. hydrazine / MMH
         * engines, solid-rocket motor exit cones in space).  High emissivity and
         * very low density are the key advantages; however, C/C oxidises rapidly
         * in oxygen-rich exhaust and requires an anti-oxidation coating
         * ({@code SiC} or {@code ZrB₂}) for oxidizing propellant combinations.
         * The temperature limit stated here is for coated C/C.
         */
        public static final ExtensionMaterial CARBON_CARBON = new ExtensionMaterial(
                "Carbon-Carbon Composite (coated)",
                0.85,   // ε — C/C surface at operating temperature
                1800.0, // T_limit [K]
                1900.0, // ρ [kg/m³]
                50.0    // k [W/(m·K)]
        );
    }
}
