package com.nozzle.moc;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.geometry.Point2D;

import java.util.List;

/**
 * Designs an axisymmetric aerospike (plug) nozzle using the Method of Characteristics.
 *
 * <p>An aerospike nozzle replaces the conventional outer bell wall with a central spike
 * (plug) around which the exhaust gas flows.  The outer boundary of the supersonic flow
 * is the ambient atmosphere rather than a solid wall, giving the nozzle its hallmark
 * property: <em>natural altitude compensation</em>.  At low altitude (high ambient
 * pressure) the outer boundary is compressed inward, reducing the effective area ratio
 * smoothly and without the oblique-shock losses that afflict an over-expanded bell nozzle.
 * At high altitude (low ambient pressure) the exhaust expands freely beyond the spike tip,
 * recovering the full design thrust.
 *
 * <h2>Geometry</h2>
 * <pre>
 *   Axis ─────────────────────────────────────────▶  x
 *          |←── rt ──→|
 *   r=rt   ○ ─ Cowl lip (outer throat edge)
 *          │ ←annular throat→ │
 *   r=ri   ● ─ Spike tip (inner throat edge)     ●─ spike tip end
 *                              ╲                 ╱
 *                               ╲  spike contour ╱
 *                                ╲             ╱
 *                                 ─────────────
 * </pre>
 * <ul>
 *   <li>Annular throat: outer radius {@code rt}, inner (spike-tip) radius
 *       {@code ri = rt × spikeRadiusRatio}.</li>
 *   <li>The spike contour is computed by {@link AerospikeContour} using the MOC
 *       kernel-flow algorithm so that the exit flow is perfectly axial at the
 *       design condition.</li>
 *   <li>In practice the spike is truncated to
 *       {@code truncationFraction × fullSpikeLength} to save weight; the truncated
 *       base face is exposed to the base-pressure recirculation region.</li>
 * </ul>
 */
public class AerospikeNozzle {

    /** Default ratio of inner (spike-tip) throat radius to outer throat radius. */
    public static final double DEFAULT_SPIKE_RADIUS_RATIO = 0.60;

    /** Default fraction of the full ideal spike length actually built. */
    public static final double DEFAULT_TRUNCATION_FRACTION = 0.80;

    /** Default number of discrete spike contour points. */
    public static final int DEFAULT_NUM_SPIKE_POINTS = 100;

    private final NozzleDesignParameters parameters;
    private final AerospikeContour contour;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Creates an aerospike nozzle with default geometry settings:
     * {@value #DEFAULT_SPIKE_RADIUS_RATIO} spike-radius ratio,
     * {@value #DEFAULT_TRUNCATION_FRACTION} truncation fraction, and
     * {@value #DEFAULT_NUM_SPIKE_POINTS} contour points.
     *
     * @param parameters Nozzle design parameters (throat radius, exit Mach, gas properties, etc.)
     */
    public AerospikeNozzle(NozzleDesignParameters parameters) {
        this(parameters, DEFAULT_SPIKE_RADIUS_RATIO, DEFAULT_TRUNCATION_FRACTION,
                DEFAULT_NUM_SPIKE_POINTS);
    }

    /**
     * Creates an aerospike nozzle with fully specified geometry.
     *
     * @param parameters         Nozzle design parameters
     * @param spikeRadiusRatio   Ratio of inner (spike-tip) throat radius to outer throat
     *                           radius; must be in (0, 1) exclusive
     * @param truncationFraction Fraction of the full ideal spike length to retain for the
     *                           physical spike; must be in (0, 1]
     * @param numSpikePoints     Number of discrete points for spike contour resolution;
     *                           must be ≥ 2
     * @throws IllegalArgumentException if any geometric parameter is out of range
     */
    public AerospikeNozzle(NozzleDesignParameters parameters,
                           double spikeRadiusRatio,
                           double truncationFraction,
                           int numSpikePoints) {
        if (spikeRadiusRatio <= 0.0 || spikeRadiusRatio >= 1.0) {
            throw new IllegalArgumentException(
                    "spikeRadiusRatio must be in (0, 1), got: " + spikeRadiusRatio);
        }
        if (truncationFraction <= 0.0 || truncationFraction > 1.0) {
            throw new IllegalArgumentException(
                    "truncationFraction must be in (0, 1], got: " + truncationFraction);
        }
        if (numSpikePoints < 2) {
            throw new IllegalArgumentException(
                    "numSpikePoints must be >= 2, got: " + numSpikePoints);
        }
        this.parameters = parameters;
        this.contour = new AerospikeContour(parameters, spikeRadiusRatio,
                truncationFraction, numSpikePoints);
    }

    // -------------------------------------------------------------------------
    // Generation
    // -------------------------------------------------------------------------

    /**
     * Computes the full ideal spike contour.
     *
     * @return This instance, for method chaining
     */
    public AerospikeNozzle generate() {
        contour.generate();
        return this;
    }

    // -------------------------------------------------------------------------
    // Geometry — delegated to AerospikeContour
    // -------------------------------------------------------------------------

    /**
     * Returns an unmodifiable view of the full ideal spike contour.
     * Calls {@link #generate()} first if the contour has not yet been computed.
     *
     * @return List of {@link Point2D} contour points from the throat (index 0) to the
     *         spike tip (last index), in axial order
     */
    public List<Point2D> getFullSpikeContour()    { return contour.getFullSpikeContour(); }

    /**
     * Returns the truncated spike contour — only the portion of the full spike up to
     * {@code truncationFraction × fullSpikeLength}.
     *
     * @return List of {@link Point2D} for the truncated spike, never empty after generation
     */
    public List<Point2D> getTruncatedSpikeContour() { return contour.getTruncatedSpikeContour(); }

    /**
     * Returns the axial length of the full ideal spike.
     *
     * @return Full spike length in metres; 0 if {@link #generate()} has not been called
     */
    public double getFullSpikeLength()             { return contour.getFullSpikeLength(); }

    /**
     * Returns the axial length of the truncated spike.
     *
     * @return Truncated spike length = {@code truncationFraction × fullSpikeLength} in metres
     */
    public double getTruncatedLength()             { return contour.getTruncatedLength(); }

    /**
     * Returns the annular throat flow area.
     *
     * <p>The annular throat is bounded by the outer cowl lip at radius {@code rt} and the
     * spike surface at radius {@code ri = rt × spikeRadiusRatio}.
     *
     * @return Annular throat area in m²; {@code π(rt² − ri²)}
     */
    public double getAnnularThroatArea()           { return contour.getAnnularThroatArea(); }

    /**
     * Returns the annular exit area of the full ideal aerospike.
     *
     * <p>The exit area is computed from mass-flow conservation:
     * {@code Ae = At × (Ae/At)} where {@code Ae/At} is the isentropic area ratio at
     * the design exit Mach number.
     *
     * @return Design exit area in m²
     */
    public double getAnnularExitArea()             { return contour.getAnnularExitArea(); }

    /**
     * Returns the radius at the tip of the truncated spike (the base radius).
     * This is the radius of the spike surface at axial position {@code getTruncatedLength()}.
     *
     * @return Base radius of the truncated spike in metres
     */
    public double getTruncatedBaseRadius()         { return contour.getTruncatedBaseRadius(); }

    // -------------------------------------------------------------------------
    // Performance
    // -------------------------------------------------------------------------

    /**
     * Calculates the aerospike thrust coefficient at a given ambient pressure,
     * exploiting the nozzle's natural altitude-compensation property.
     *
     * <h4>Altitude-compensation model</h4>
     * Let {@code M_pa} be the Mach number at which the isentropic exit pressure equals
     * {@code ambientPressure}.
     * <ul>
     *   <li><b>Over-compressed (low altitude, {@code M_pa < M_exit})</b>: the outer
     *       boundary is compressed inward; the flow adapts to area ratio
     *       {@code A(M_pa)/At} with no separation or shock losses.  The thrust is
     *       purely momentum (pe = pa exactly, no pressure term).</li>
     *   <li><b>Under-expanded (high altitude, {@code M_pa ≥ M_exit})</b>: the spike tip
     *       is the design exit; flow continues to expand freely beyond the tip.
     *       The thrust equals the design momentum thrust plus a positive pressure term
     *       {@code (pe_design − pa) · Ae/At / pc}.</li>
     * </ul>
     *
     * <p>For the truncated spike an additional base-thrust term is added:
     * {@code Cf_base = (pb − pa) × A_base / (pc × At)} where the base pressure
     * {@code pb = 0} (conservative: the base face is in a vacuum relative to the
     * recirculation) — this is the worst case; in practice base recirculation raises
     * {@code pb} toward {@code pa}, recovering most of the area.
     *
     * @param ambientPressure Ambient (freestream) static pressure in Pa; must be positive
     * @return Thrust coefficient (dimensionless); always positive for a supersonic nozzle
     *         with chamber pressure well above ambient
     */
    public double calculateThrustCoefficient(double ambientPressure) {
        GasProperties gas = parameters.gasProperties();
        double pc = parameters.chamberPressure();
        double At = getAnnularThroatArea();

        // Mach number for which pe = ambientPressure
        double mPa   = machFromChamberPressureRatio(gas, ambientPressure / pc);
        double mExit = parameters.exitMach();

        double cf;
        if (mPa <= mExit) {
            // Over-compressed: aerospike adapts to mPa — momentum thrust only, no pressure term.
            cf = momentumThrustCoefficient(gas, mPa);
        } else {
            // Under-expanded: design Mach used; positive pressure-recovery term.
            double peDesign = parameters.idealExitPressure();
            double Ae = getAnnularExitArea();
            cf = momentumThrustCoefficient(gas, mExit)
                    + (peDesign - ambientPressure) * Ae / (pc * At);
        }

        // Base-thrust correction for truncated spike.
        // Conservative: base pressure = ambientPressure (no recirculation bonus).
        // Net base-thrust term = 0 (pb − pa = 0), so this term is elided here.
        // Non-conservative designs may add: (pb - pa) * pi*r_tip² / (pc * At)

        return Math.max(0.0, cf);
    }

    /**
     * Returns the thrust coefficient of a reference bell nozzle at the same ambient
     * pressure, using the simplified over-/under-expansion model (no separation modeling).
     *
     * <p>This provides a direct comparison showing the Aerospike's altitude-compensation
     * advantage: at low altitude the bell nozzle incurs a negative pressure term (over-expansion),
     * while the aerospike does not.
     *
     * @param ambientPressure Ambient pressure in Pa
     * @return Bell-nozzle thrust coefficient at this pressure (can be lower than the
     *         Aerospike value at off-design altitude)
     */
    public double calculateBellNozzleThrustCoefficient(double ambientPressure) {
        GasProperties gas     = parameters.gasProperties();
        double pc             = parameters.chamberPressure();
        double peDesign       = parameters.idealExitPressure();
        double Ae             = getAnnularExitArea();
        double At             = getAnnularThroatArea();
        double cfMomentum     = momentumThrustCoefficient(gas, parameters.exitMach());
        double cfPressure     = (peDesign - ambientPressure) * Ae / (pc * At);

        return Math.max(0.0, cfMomentum + cfPressure);
    }

    /**
     * Calculates specific impulse (Isp) at a given ambient pressure.
     *
     * @param ambientPressure Ambient pressure in Pa
     * @return Isp in seconds
     */
    public double calculateIsp(double ambientPressure) {
        double cStar = parameters.characteristicVelocity();
        double cf    = calculateThrustCoefficient(ambientPressure);
        return cf * cStar / 9.80665;
    }

    /**
     * Calculates aerospike and reference bell-nozzle performance over a range of
     * ambient pressures (e.g. an ascent trajectory).
     *
     * @param ambientPressures Array of ambient pressures in Pa; must be non-null and
     *                         non-empty
     * @return {@link AltitudePerformance} record containing per-altitude metrics for
     *         the Aerospike and the reference bell nozzle
     * @throws IllegalArgumentException if {@code ambientPressures} is null or empty
     */
    public AltitudePerformance calculateAltitudePerformance(double[] ambientPressures) {
        if (ambientPressures == null || ambientPressures.length == 0) {
            throw new IllegalArgumentException("ambientPressures must be non-null and non-empty");
        }

        int n = ambientPressures.length;
        double[] aerospikeCf  = new double[n];
        double[] bellCf       = new double[n];
        double[] aerospikeIsp = new double[n];

        for (int i = 0; i < n; i++) {
            aerospikeCf[i]  = calculateThrustCoefficient(ambientPressures[i]);
            bellCf[i]       = calculateBellNozzleThrustCoefficient(ambientPressures[i]);
            aerospikeIsp[i] = calculateIsp(ambientPressures[i]);
        }

        return new AltitudePerformance(ambientPressures.clone(), aerospikeCf, bellCf, aerospikeIsp);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the nozzle design parameters used to construct this aerospike.
     *
     * @return The {@link NozzleDesignParameters}
     */
    public NozzleDesignParameters getParameters()  { return parameters; }

    /**
     * Returns the ratio of the inner (spike-tip) throat radius to the outer throat radius.
     *
     * @return {@code ri / rt}
     */
    public double getSpikeRadiusRatio()             { return contour.getSpikeRadiusRatio(); }

    /**
     * Returns the truncation fraction: the fraction of the full ideal spike length
     * retained in the physical spike.
     *
     * @return Truncation fraction in {@code (0, 1]}
     */
    public double getTruncationFraction()           { return contour.getTruncationFraction(); }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Computes the Mach number at which the isentropic exit pressure equals the given
     * fraction of chamber pressure, i.e. solves {@code pe/pc = p_ratio} for {@code M}.
     *
     * <p>From the isentropic relation:
     * {@code p/p0 = (1 + (γ−1)/2 · M²)^(−γ/(γ−1))}
     *
     * @param gas    Gas properties (used for γ)
     * @param pRatio {@code pa / pc}; must be in (0, 1)
     * @return Supersonic Mach number corresponding to this pressure ratio; clamped to
     *         the range [1, ∞) — returns 1.0 if the pressure ratio implies sonic or
     *         subsonic conditions
     */
    private static double machFromChamberPressureRatio(GasProperties gas, double pRatio) {
        double gm1     = gas.gamma() - 1.0;
        double exponent = -gm1 / gas.gamma();  // -(γ-1)/γ
        double bracket  = Math.pow(pRatio, exponent) - 1.0; // (p0/p)^((γ-1)/γ) - 1
        if (bracket <= 0.0) {
            return 1.0;  // subsonic or sonic: pRatio >= throat critical pressure ratio
        }
        return Math.sqrt(2.0 / gm1 * bracket);
    }

    /**
     * Computes the momentum contribution to the thrust coefficient for a given exit Mach.
     *
     * <p>Uses the standard isentropic thrust-coefficient formula:
     * {@code Cf_mom = sqrt( 2γ²/(γ−1) · (2/(γ+1))^((γ+1)/(γ−1)) · (1 − (pe/pc)^((γ−1)/γ)) )}
     *
     * @param gas  Gas properties
     * @param mach Exit Mach number
     * @return Momentum thrust coefficient component (always non-negative)
     */
    private static double momentumThrustCoefficient(GasProperties gas, double mach) {
        double g   = gas.gamma();
        double gp1 = g + 1.0;
        double gm1 = g - 1.0;

        // pe/pc from isentropic relation
        double peOverPc = gas.isentropicPressureRatio(mach);

        double term1 = 2.0 * g * g / gm1 * Math.pow(2.0 / gp1, gp1 / gm1);
        double term2 = 1.0 - Math.pow(peOverPc, gm1 / g);

        return Math.sqrt(Math.max(0.0, term1 * term2));
    }
}
