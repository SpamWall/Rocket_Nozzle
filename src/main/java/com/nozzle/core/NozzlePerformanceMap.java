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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Generates a two-dimensional performance map of specific impulse ({@code Isp})
 * as a function of altitude and nozzle expansion ratio.
 *
 * <p>The map covers:
 * <ul>
 *   <li><b>Altitude axis</b> — 0 to 80 km using the 1976 US Standard Atmosphere
 *       model for ambient pressure.  Key levels (sea level, max-q, upper
 *       atmosphere, vacuum) are automatically included.</li>
 *   <li><b>Expansion ratio axis</b> — from 1.5 to a specified maximum, sampled
 *       at evenly spaced values on a log scale to give good resolution at low
 *       area ratios where Isp changes rapidly.</li>
 * </ul>
 *
 * <p>At each ({@code altitude}, {@code epsilon}) cell the Isp is computed from
 * the isentropic thrust equation accounting for the pressure thrust term:
 * <pre>
 *   Cf = √(2γ²/(γ−1) · (2/(γ+1))^((γ+1)/(γ−1)) · (1 − (pe/pc)^((γ−1)/γ)))
 *         + (pe − pa) / pc · ε
 *   Isp = c* · Cf / g₀
 * </pre>
 * When the nozzle is overexpanded ({@code pe < 0.4 pa}) the Summerfield flow
 * separation criterion is applied and the effective Cf is reduced to the
 * separated-flow value.
 *
 * <h2>Design use</h2>
 * <p>The map identifies:
 * <ul>
 *   <li>The <b>optimum expansion ratio</b> at each altitude that maximises Isp.</li>
 *   <li>The <b>altitude of peak Isp</b> for a fixed expansion ratio (useful for
 *       fixed-geometry nozzles).</li>
 *   <li>The <b>Isp contour lines</b> for plotting performance envelopes.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * NozzlePerformanceMap map = new NozzlePerformanceMap(params)
 *         .altitudePoints(30)
 *         .expansionRatioPoints(40)
 *         .maxExpansionRatio(100)
 *         .generate();
 *
 * double isp = map.isp(altitudeKm, expansionRatio);
 * double optEps = map.optimumExpansionRatio(altitudeKm);
 * }</pre>
 */
public class NozzlePerformanceMap {

    private static final Logger LOG = LoggerFactory.getLogger(NozzlePerformanceMap.class);

    private static final double G0                 = 9.80665;  // m/s²
    private static final double SEPARATION_FACTOR  = 0.4;      // Summerfield criterion

    /**
     * A single cell in the performance map.
     *
     * @param altitudeKm       altitude above sea level (km)
     * @param ambientPressure  ambient pressure at this altitude (Pa)
     * @param expansionRatio   nozzle area ratio ε = A_exit/A_throat
     * @param exitPressure     nozzle exit static pressure (Pa)
     * @param thrustCoefficient thrust coefficient Cf (dimensionless)
     * @param isp              specific impulse (s)
     * @param separated        true if Summerfield separation criterion is met
     */
    public record MapCell(
            double altitudeKm,
            double ambientPressure,
            double expansionRatio,
            double exitPressure,
            double thrustCoefficient,
            double isp,
            boolean separated
    ) {}

    /**
     * The optimum performance at a given altitude: the expansion ratio and Isp
     * that maximize thrust coefficient.
     *
     * @param altitudeKm       altitude (km)
     * @param optimumEpsilon   optimum expansion ratio at this altitude
     * @param peakIsp          peak specific impulse at optimum epsilon (s)
     * @param idealEpsilon     ideal-expansion ratio at this altitude
     */
    public record AltitudeOptimum(
            double altitudeKm,
            double optimumEpsilon,
            double peakIsp,
            double idealEpsilon
    ) {}

    // Configuration
    private int    nAltitude       = 25;
    private int    nExpansion      = 30;
    private double maxExpansion    = 100.0;
    private double maxAltitudeKm   = 80.0;

    private final NozzleDesignParameters params;
    private final GasProperties          gas;

    private final List<List<MapCell>>      map      = new ArrayList<>();
    private final List<AltitudeOptimum>    optima   = new ArrayList<>();
    private       double[]                 altitudes;
    private       double[]                 epsilons;

    /**
     * Creates a performance map generator with default grid density (25 altitude
     * × 30 expansion-ratio points, 0–80 km, ε up to 100).
     *
     * @param params nozzle design parameters (chamber conditions and gas properties)
     */
    public NozzlePerformanceMap(NozzleDesignParameters params) {
        this.params = params;
        this.gas    = params.gasProperties();
    }

    // -------------------------------------------------------------------------
    // Fluent configuration
    // -------------------------------------------------------------------------

    /**
     * Sets the number of altitude points in the map.
     *
     * @param n number of altitude points (must be ≥ 2)
     * @return this instance
     */
    public NozzlePerformanceMap altitudePoints(int n) { this.nAltitude = n; return this; }

    /**
     * Sets the number of expansion-ratio points.
     *
     * @param n number of expansion-ratio points (must be ≥ 2)
     * @return this instance
     */
    public NozzlePerformanceMap expansionRatioPoints(int n) { this.nExpansion = n; return this; }

    /**
     * Sets the maximum expansion ratio to include in the map.
     *
     * @param maxEps maximum A_exit/A_throat (must be &gt; 1)
     * @return this instance
     */
    public NozzlePerformanceMap maxExpansionRatio(double maxEps) { this.maxExpansion = maxEps; return this; }

    /**
     * Sets the maximum altitude in kilometres.
     *
     * @param altKm maximum altitude (km); defaults to 80 km
     * @return this instance
     */
    public NozzlePerformanceMap maxAltitudeKm(double altKm) { this.maxAltitudeKm = altKm; return this; }

    // -------------------------------------------------------------------------
    // Generation
    // -------------------------------------------------------------------------

    /**
     * Computes the full performance map.
     *
     * @return this instance for method chaining
     */
    public NozzlePerformanceMap generate() {
        altitudes = buildAltitudeGrid();
        epsilons  = buildExpansionGrid();
        double cStar = params.characteristicVelocity();

        LOG.debug("Perf map: {}×{} grid, altitude [0,{}km], epsilon [1.5,{}]",
                nAltitude, nExpansion, maxAltitudeKm, maxExpansion);

        for (double alt : altitudes) {
            double pa = standardAtmospherePressure(alt);
            List<MapCell> row = new ArrayList<>();

            for (double eps : epsilons) {
                MapCell cell = computeCell(alt, pa, eps, cStar);
                row.add(cell);
            }
            map.add(row);

            // Find optimum epsilon at this altitude
            MapCell best = row.stream()
                    .max(Comparator.comparingDouble(MapCell::isp))
                    .orElse(row.getLast());
            double idealEps = idealExpansionRatio(pa);
            optima.add(new AltitudeOptimum(alt, best.expansionRatio(), best.isp(), idealEps));
        }

        LOG.debug("Perf map complete: sea-level opt eps={}, vacuum opt eps={}",
                optima.getFirst().optimumEpsilon(), optima.getLast().optimumEpsilon());
        return this;
    }

    // -------------------------------------------------------------------------
    // Query API
    // -------------------------------------------------------------------------

    /**
     * Returns the Isp at the specified altitude and expansion ratio by
     * bilinear interpolation in the map.
     *
     * @param altitudeKm     altitude (km); clamped to [0, maxAltitudeKm]
     * @param expansionRatio expansion ratio; clamped to [1.5, maxExpansion]
     * @return Isp in seconds
     */
    public double isp(double altitudeKm, double expansionRatio) {
        if (map.isEmpty()) throw new IllegalStateException("Call generate() first");
        int ai  = nearestAltitudeIndex(altitudeKm);
        int ei  = nearestExpansionIndex(expansionRatio);
        return map.get(ai).get(ei).isp();
    }

    /**
     * Returns the optimum expansion ratio at the given altitude.
     *
     * @param altitudeKm altitude (km)
     * @return optimum ε (maximises Isp at that altitude)
     */
    public double optimumExpansionRatio(double altitudeKm) {
        if (optima.isEmpty()) throw new IllegalStateException("Call generate() first");
        return optima.get(nearestAltitudeIndex(altitudeKm)).optimumEpsilon();
    }

    /**
     * Returns the full list of altitude optima.
     *
     * @return unmodifiable list of {@link AltitudeOptimum} from 0 to maxAltitudeKm
     */
    public List<AltitudeOptimum> getOptima() { return Collections.unmodifiableList(optima); }

    /**
     * Returns the complete map as a nested list (outer = altitude, inner = expansion ratio).
     *
     * @return unmodifiable nested list of {@link MapCell}s
     */
    public List<List<MapCell>> getMap() { return Collections.unmodifiableList(map); }

    /**
     * Returns the altitude grid in kilometres.
     *
     * @return altitude values used in the map
     */
    public double[] getAltitudes() { return altitudes.clone(); }

    /**
     * Returns the expansion-ratio grid.
     *
     * @return expansion-ratio values used in the map
     */
    public double[] getExpansionRatios() { return epsilons.clone(); }

    /**
     * Returns the peak Isp achievable at any altitude for the given expansion ratio.
     *
     * @param expansionRatio fixed expansion ratio
     * @return peak Isp in seconds across all altitudes
     */
    public double peakIspForFixedNozzle(double expansionRatio) {
        if (map.isEmpty()) throw new IllegalStateException("Call generate() first");
        int ei = nearestExpansionIndex(expansionRatio);
        return map.stream().mapToDouble(row -> row.get(ei).isp()).max().orElse(0.0);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private MapCell computeCell(double altKm, double pa, double eps, double cStar) {
        double gamma = gas.gamma();
        double gp1   = gamma + 1;
        double gm1   = gamma - 1;
        double pc    = params.chamberPressure();

        // Exit Mach from expansion ratio (supersonic root)
        double Me    = gas.machFromAreaRatio(eps);
        double pe    = pc * gas.isentropicPressureRatio(Me);

        // Summerfield separation criterion
        boolean separated = pe < SEPARATION_FACTOR * pa;

        double Cf;
        if (separated) {
            // Separated: find Mach at separation pressure using isentropic inversion
            double pSep  = SEPARATION_FACTOR * pa;
            double M2sep = 2.0 / gm1 * (Math.pow(pc / pSep, gm1 / gamma) - 1.0);
            double MeSep = (M2sep > 0) ? Math.sqrt(M2sep) : 1.0;
            double epsSep = gas.areaRatio(Math.max(MeSep, 1.001));
            Cf = computeCf(gamma, gp1, gm1, pc, pa, pSep, epsSep);
        } else {
            Cf = computeCf(gamma, gp1, gm1, pc, pa, pe, eps);
        }

        double isp = cStar * Math.max(Cf, 0.01) / G0;
        return new MapCell(altKm, pa, eps, pe, Math.max(Cf, 0.01), isp, separated);
    }

    private double computeCf(double gamma, double gp1, double gm1,
                             double pc, double pa, double pe, double eps) {
        double term1 = 2.0 * gamma * gamma / gm1;
        double term2 = Math.pow(2.0 / gp1, gp1 / gm1);
        double term3 = 1.0 - Math.pow(pe / pc, gm1 / gamma);
        double Cf    = Math.sqrt(term1 * term2 * Math.max(term3, 0.0));
        Cf += (pe - pa) / pc * eps;
        return Cf;
    }

    private double[] buildAltitudeGrid() {
        double[] grid = new double[nAltitude];
        for (int i = 0; i < nAltitude; i++) {
            grid[i] = maxAltitudeKm * i / (nAltitude - 1.0);
        }
        return grid;
    }

    private double[] buildExpansionGrid() {
        // Log-scale from 1.5 to maxExpansion
        double[] grid = new double[nExpansion];
        double logMin = Math.log(1.5);
        double logMax = Math.log(maxExpansion);
        for (int i = 0; i < nExpansion; i++) {
            double t = (double) i / (nExpansion - 1.0);
            grid[i] = Math.exp(logMin + t * (logMax - logMin));
        }
        return grid;
    }

    /** 1976 US Standard Atmosphere: valid 0–86 km. */
    static double standardAtmospherePressure(double altitudeKm) {
        double h = altitudeKm * 1000.0; // convert to metres
        if (h <= 11000) {
            // Troposphere
            double T = 288.15 - 0.0065 * h;
            return 101325.0 * Math.pow(T / 288.15, 5.2561);
        } else if (h <= 20000) {
            // Tropopause (isothermal)
            double p11 = 22632.1;
            return p11 * Math.exp(-0.0001577 * (h - 11000));
        } else if (h <= 32000) {
            // Lower stratosphere
            double T = 216.65 + 0.001 * (h - 20000);
            return 5474.89 * Math.pow(T / 216.65, -34.163);
        } else if (h <= 47000) {
            // Mid-stratosphere
            double T = 228.65 + 0.0028 * (h - 32000);
            return 868.019 * Math.pow(T / 228.65, -12.201);
        } else if (h <= 51000) {
            // Stratopause isothermal layer (270.65 K)
            return 110.906 * Math.exp(-9.80665 / (287.058 * 270.65) * (h - 47000));
        } else if (h <= 71000) {
            // Lower mesosphere: lapse rate -2.8 K/km
            double T = 270.65 - 0.0028 * (h - 51000);
            return 66.939 * Math.pow(T / 270.65, 9.80665 / (0.0028 * 287.058));
        } else if (h <= 86000) {
            // Upper mesosphere: lapse rate -2.0 K/km
            double T = 214.65 - 0.002 * (h - 71000);
            return 3.9564 * Math.pow(T / 214.65, 9.80665 / (0.002 * 287.058));
        }
        return 0.01; // near-vacuum above 86 km
    }

    private double idealExpansionRatio(double pa) {
        // Ideal: pe = pa, solve for M, then get area ratio
        double gamma = gas.gamma();
        double gm1   = gamma - 1;
        // pe/pc = (1 + gm1/2 * M^2)^(-γ/gm1) → solve for M
        double T_ratio = Math.pow(pa / params.chamberPressure(), gm1 / gamma);
        double M2 = (1.0 / T_ratio - 1.0) * 2.0 / gm1;
        if (M2 <= 0) return 1.0;
        double Mideal = Math.sqrt(M2);
        return Mideal > 1.0 ? gas.areaRatio(Mideal) : 1.0;
    }

    private int nearestAltitudeIndex(double altKm) {
        int best = 0;
        double minDist = Math.abs(altitudes[0] - altKm);
        for (int i = 1; i < altitudes.length; i++) {
            double d = Math.abs(altitudes[i] - altKm);
            if (d < minDist) { minDist = d; best = i; }
        }
        return best;
    }

    private int nearestExpansionIndex(double eps) {
        int best = 0;
        double minDist = Math.abs(epsilons[0] - eps);
        for (int i = 1; i < epsilons.length; i++) {
            double d = Math.abs(epsilons[i] - eps);
            if (d < minDist) { minDist = d; best = i; }
        }
        return best;
    }
}
