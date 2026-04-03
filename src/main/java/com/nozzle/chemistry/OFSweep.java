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

package com.nozzle.chemistry;

import com.nozzle.core.GasProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * Computes Isp, c*, γ, and mean molecular weight as functions of the O/F mixture
 * ratio, and locates the optimum O/F via a golden-section search.
 *
 * <h2>Two operating modes</h2>
 * <dl>
 *   <dt>Fixed-Tc mode</dt>
 *   <dd>Created via {@link #OFSweep(Propellant, double, double, double, double)}.
 *       The supplied chamber temperature is used at every O/F point.  Useful for
 *       sensitivity studies at a known condition (e.g., matching a test measurement)
 *       but does <em>not</em> locate the true Isp-optimal O/F because the real Tc
 *       peaks near stoichiometric.</dd>
 *   <dt>Adiabatic mode</dt>
 *   <dd>Created via {@link #adiabatic(Propellant, double, double, double)}.
 *       The adiabatic chamber temperature is computed at each O/F via Newton
 *       iteration on the energy balance:
 *       <pre>H_reactants(298 K) = H_products(T_c)</pre>
 *       where reactant enthalpies include heats of formation (standard-state gas
 *       phase at 298 K) and product enthalpies are NASA-7 values of the Gibbs-
 *       equilibrium composition.  This mode gives the physically correct Isp(O/F)
 *       optimum.</dd>
 * </dl>
 *
 * <h2>Chemistry</h2>
 * <p>At each O/F the Gibbs minimizer is run at (T_c, P_c) to obtain the
 * equilibrium product composition.  γ and M̄ are then evaluated from that
 * composition using frozen specific heats (NASA-7 polynomials).
 *
 * <h2>Performance formulas</h2>
 * <pre>
 *   c* = √(γ R_sp T_c) / (γ · (2/(γ+1))^((γ+1)/(2(γ−1))))
 *
 *   pe/pc = (1 + (γ−1)/2 · Me²)^(−γ/(γ−1))        [isentropic at fixed Me]
 *   Ae/At = (1/Me) · ((2/(γ+1)) · (1 + (γ−1)/2 · Me²))^((γ+1)/(2(γ−1)))
 *
 *   Cf_ideal = √(2γ²/(γ−1) · (2/(γ+1))^((γ+1)/(γ−1)) · (1−(pe/pc)^((γ−1)/γ)))
 *              + (pe − pa)/pc · Ae/At
 *
 *   Isp = c* · Cf_ideal / g₀               (g₀ = 9.80665 m/s²)
 * </pre>
 *
 * <h2>Optimum search</h2>
 * <p>Both {@link #optimumIsp} and {@link #optimumCstar} use golden-section search
 * and assume the objective function is <em>unimodal</em> over the supplied O/F range.
 * This holds in adiabatic mode for all built-in propellants over physically meaningful
 * ranges (fuel-rich to moderately oxidizer-rich); supplying a range that spans multiple
 * extrema will return a local optimum.
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 *   // Adiabatic mode — finds true Isp-optimal O/F
 *   OFSweep sweep = OFSweep.adiabatic(OFSweep.Propellant.LOX_RP1, 7e6, 3.0, 101325.0);
 *
 *   List<OFSweep.OFPoint> curve  = sweep.sweep(1.5, 5.0, 50);
 *   OFSweep.OFPoint ispOpt       = sweep.optimumIsp(1.5, 5.0);
 *   OFSweep.OFPoint cstarOpt     = sweep.optimumCstar(1.5, 5.0);
 * }</pre>
 */
public class OFSweep {

    // -------------------------------------------------------------------------
    // Propellant type
    // -------------------------------------------------------------------------

    /**
     * Supported propellant combinations.
     *
     * <p>Each entry carries the representative chamber-product {@link GasProperties}
     * (used as the ChemistryModel base) and the standard-state (298 K, ideal gas)
     * specific formation enthalpies of the fuel and oxidizer in J/kg.  These are
     * used by the adiabatic-Tc Newton solver.
     *
     * <p>Sources: NIST-JANAF Tables 4th ed.; NASA TP-2002-211556 (McBride et al.);
     * CEA propellant database.  Gas-phase enthalpies are used throughout; latent
     * heats of the cryogenic propellants are omitted (≤ 3% effect on T_c).
     */
    public enum Propellant {

        /** LOX / RP-1 kerosene.  Stoichiometric O/F ≈ 3.4.
         *  RP-1 modelled as C₁₂H₂₃.₄ (MW ≈ 167.6 g/mol, ΔHf° ≈ −250 kJ/mol). */
        LOX_RP1(
                GasProperties.LOX_RP1_PRODUCTS,
                -1_491_647,   // RP-1  = −250 000 J/mol ÷ 0.1676 kg/mol
                0             // O₂ (g): ΔHf° = 0
        ),

        /** LOX / methane.  Stoichiometric O/F ≈ 4.0.
         *  CH₄: ΔHf° = −74 870 J/mol, MW = 16.04 g/mol. */
        LOX_CH4(
                GasProperties.LOX_CH4_PRODUCTS,
                -4_668_391,   // = −74 870 J/mol ÷ 0.01604 kg/mol
                0             // O₂ (g): ΔHf° = 0
        ),

        /** LOX / liquid hydrogen.  Stoichiometric O/F ≈ 8.0.
         *  H₂: ΔHf° = 0 (reference element). */
        LOX_LH2(
                GasProperties.LOX_LH2_PRODUCTS,
                0,            // H₂ (g): ΔHf° = 0
                0             // O₂ (g): ΔHf° = 0
        ),

        /** N₂O / ethanol.  Stoichiometric O/F ≈ 5.73.
         *  C₂H₅OH (g): ΔHf° = −235 300 J/mol, MW = 46.07 g/mol.
         *  N₂O (g): ΔHf° = +82 100 J/mol, MW = 44.01 g/mol. */
        N2O_ETHANOL(
                GasProperties.N2O_ETHANOL_PRODUCTS,
                -5_107_673,   // = −235 300 J/mol ÷ 0.04607 kg/mol
                1_865_484     // = +82 100 J/mol ÷ 0.04401 kg/mol
        ),

        /** N₂O / propane.  Stoichiometric O/F ≈ 9.98.
         *  C₃H₈ (g): ΔHf° = −103 850 J/mol, MW = 44.10 g/mol.
         *  N₂O (g): ΔHf° = +82 100 J/mol, MW = 44.01 g/mol. */
        N2O_PROPANE(
                GasProperties.N2O_PROPANE_PRODUCTS,
                -2_354_762,   // = −103 850 J/mol ÷ 0.04410 kg/mol
                1_865_484     // = +82 100 J/mol ÷ 0.04401 kg/mol
        );

        /** Representative chamber-product {@link GasProperties} for this combination. */
        public final GasProperties defaultProperties;

        /**
         * Standard-state (298 K, gas phase) specific enthalpy of the fuel in J/kg,
         * including heat of formation.
         */
        public final double hFuelJPerKg;

        /**
         * Standard-state (298 K, gas phase) specific enthalpy of the oxidizer in J/kg,
         * including heat of formation.
         */
        public final double hOxJPerKg;

        Propellant(GasProperties props, double hFuel, double hOx) {
            this.defaultProperties = props;
            this.hFuelJPerKg       = hFuel;
            this.hOxJPerKg         = hOx;
        }

        /**
         * Reactant mixture specific enthalpy (fuel + oxidizer) at 298 K in J/kg,
         * weighted by the given O/F ratio.
         *
         * @param of O/F mixture ratio
         * @return H_reactants(298 K) in J/kg
         */
        double reactantEnthalpy(double of) {
            return (hFuelJPerKg + of * hOxJPerKg) / (1.0 + of);
        }
    }

    // -------------------------------------------------------------------------
    // Result record
    // -------------------------------------------------------------------------

    /**
     * Performance quantities at one O/F operating point.
     *
     * @param of               O/F mixture ratio (dimensionless)
     * @param chamberTemperature Adiabatic or user-supplied chamber temperature in K
     * @param gamma            Frozen specific-heat ratio γ of the equilibrium
     *                         chamber composition (dimensionless)
     * @param molecularWeight  Mean molecular weight M̄ in kg/kmol
     * @param cStar            Characteristic exhaust velocity c* in m/s
     * @param isp              Ideal specific impulse Isp = c*·Cf_ideal/g₀ in seconds,
     *                         evaluated at the design exit Mach and ambient pressure
     *                         supplied to the constructor
     */
    public record OFPoint(
            double of,
            double chamberTemperature,
            double gamma,
            double molecularWeight,
            double cStar,
            double isp
    ) {}

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    private final Propellant propellant;
    private final double chamberPressure;      // Pa
    private final double exitMach;
    private final double ambientPressure;      // Pa
    private final double fixedChamberTemperature; // NaN in adiabatic mode

    /**
     * Creates an O/F sweep at a fixed chamber temperature.
     *
     * <p>Use this for sensitivity studies at a known operating condition.
     * To find the true Isp-optimal O/F, use {@link #adiabatic} instead.
     *
     * @param propellant          Propellant combination
     * @param chamberTemperature  Chamber stagnation temperature in K (fixed for all O/F)
     * @param chamberPressure     Chamber stagnation pressure in Pa
     * @param exitMach            Design exit Mach number (≥ 1)
     * @param ambientPressure     Ambient back-pressure at the nozzle exit in Pa
     */
    public OFSweep(Propellant propellant,
                   double chamberTemperature,
                   double chamberPressure,
                   double exitMach,
                   double ambientPressure) {
        this.propellant               = propellant;
        this.chamberPressure          = chamberPressure;
        this.exitMach                 = exitMach;
        this.ambientPressure          = ambientPressure;
        this.fixedChamberTemperature  = chamberTemperature;
    }

    private OFSweep(Propellant propellant,
                    double chamberPressure,
                    double exitMach,
                    double ambientPressure) {
        this.propellant               = propellant;
        this.chamberPressure          = chamberPressure;
        this.exitMach                 = exitMach;
        this.ambientPressure          = ambientPressure;
        this.fixedChamberTemperature  = Double.NaN;
    }

    /**
     * Creates an O/F sweep that computes the adiabatic chamber temperature at each
     * O/F point via Newton iteration on the energy balance
     * H_reactants(298 K) = H_products(T_c).
     *
     * <p>This mode gives the physically correct Isp(O/F) and c*(O/F) curves and
     * correctly locates the Isp-optimal O/F (typically slightly fuel-rich of the
     * stoichiometric ratio).
     *
     * @param propellant      Propellant combination
     * @param chamberPressure Chamber stagnation pressure in Pa
     * @param exitMach        Design exit Mach number (≥ 1)
     * @param ambientPressure Ambient back-pressure in Pa
     * @return An {@code OFSweep} in adiabatic mode
     */
    public static OFSweep adiabatic(Propellant propellant,
                                     double chamberPressure,
                                     double exitMach,
                                     double ambientPressure) {
        return new OFSweep(propellant, chamberPressure, exitMach, ambientPressure);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Evaluates performance at {@code points} linearly spaced O/F values from
     * {@code ofMin} to {@code ofMax} (inclusive).
     *
     * @param ofMin  Lower bound of the O/F range (must be positive)
     * @param ofMax  Upper bound of the O/F range (must exceed {@code ofMin})
     * @param points Number of evaluation points (must be ≥ 2)
     * @return Unmodifiable list of {@link OFPoint} in ascending O/F order
     */
    public List<OFPoint> sweep(double ofMin, double ofMax, int points) {
        List<OFPoint> result = new ArrayList<>(points);
        double step = (ofMax - ofMin) / (points - 1);
        for (int i = 0; i < points; i++) {
            result.add(computeAt(ofMin + i * step));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Finds the O/F ratio that maximizes specific impulse within the supplied
     * range using a golden-section search.
     *
     * <p>Converges to within 1×10⁻⁴ in O/F in approximately 25 Gibbs evaluations.
     * The Isp curve must be unimodal over [{@code ofMin}, {@code ofMax}].
     *
     * @param ofMin Lower bound of the search range
     * @param ofMax Upper bound of the search range
     * @return {@link OFPoint} at the Isp-optimal O/F
     */
    public OFPoint optimumIsp(double ofMin, double ofMax) {
        return goldenSection(ofMin, ofMax, OFPoint::isp);
    }

    /**
     * Finds the O/F ratio that maximizes the characteristic exhaust velocity c*
     * within the supplied range using a golden-section search.
     *
     * <p>In adiabatic mode the c* peak lies at a lower O/F than the Isp peak
     * because Cf increases with γ (which favors higher O/F), shifting the c*·Cf
     * product relative to c* alone.
     *
     * @param ofMin Lower bound of the search range
     * @param ofMax Upper bound of the search range
     * @return {@link OFPoint} at the c*-optimal O/F
     */
    public OFPoint optimumCstar(double ofMin, double ofMax) {
        return goldenSection(ofMin, ofMax, OFPoint::cStar);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Golden-section search for the maximum of {@code objective} over [{@code a}, {@code b}].
     * Converges to within 1×10⁻⁴ in O/F.
     */
    private OFPoint goldenSection(double a, double b, ToDoubleFunction<OFPoint> objective) {
        final double phi = (Math.sqrt(5.0) - 1.0) / 2.0;   // ≈ 0.618
        double c = b - phi * (b - a);
        double d = a + phi * (b - a);
        OFPoint pc = computeAt(c);
        OFPoint pd = computeAt(d);

        while (b - a > 1e-4) {
            if (objective.applyAsDouble(pc) < objective.applyAsDouble(pd)) {
                a  = c;
                c  = d;  pc = pd;
                d  = a + phi * (b - a);
                pd = computeAt(d);
            } else {
                b  = d;
                d  = c;  pd = pc;
                c  = b - phi * (b - a);
                pc = computeAt(c);
            }
        }
        return computeAt((a + b) / 2.0);
    }

    /**
     * Evaluates all performance quantities at a single O/F value.
     *
     * <p>Creates a fresh {@link ChemistryModel} and, in adiabatic mode, a fresh
     * {@link NasaSpeciesDatabase} on each call so callers share no state.
     *
     * @param of oxidiser-to-fuel mass ratio (must be positive)
     * @return {@link OFPoint} containing the chamber temperature, γ, molecular
     *         weight, c★, and Isp at the given O/F ratio
     */
    public OFPoint computeAt(double of) {
        double Tc = Double.isNaN(fixedChamberTemperature)
                    ? adiabaticTemperature(of)
                    : fixedChamberTemperature;

        ChemistryModel model = ChemistryModel.equilibrium(propellant.defaultProperties);
        applyComposition(model, of);
        model.calculateEquilibrium(Tc, chamberPressure);

        double gamma = model.calculateGamma(Tc);
        double mw    = model.calculateMolecularWeight();
        double R     = GasProperties.UNIVERSAL_GAS_CONSTANT / mw;

        double gp1  = gamma + 1.0;
        double gm1  = gamma - 1.0;

        // c* = √(γ R T_c) / (γ · (2/(γ+1))^((γ+1)/(2(γ−1))))
        double cStar = Math.sqrt(gamma * R * Tc) / gamma
                       / Math.pow(2.0 / gp1, gp1 / (2.0 * gm1));

        // Isentropic relations at design exit Mach
        double cf = computeThrustCoefficient(gm1, gamma, gp1);

        double isp = cStar * cf / 9.80665;

        return new OFPoint(of, Tc, gamma, mw, cStar, isp);
    }

    /**
     * Computes the ideal (isentropic) thrust coefficient Cf at {@link #exitMach}.
     *
     * <p>Cf is the dimensionless ratio of nozzle thrust to the product of chamber
     * pressure and throat area.  It combines the momentum-thrust term (the square-root
     * factor) with the pressure-thrust correction for the difference between exit and
     * ambient pressure:
     *
     * <pre>
     *   Ae/At  = (1/Me) · ((2/(γ+1)) · (1 + (γ−1)/2 · Me²))^((γ+1)/(2(γ−1)))
     *   pe/pc  = (1 + (γ−1)/2 · Me²)^(−γ/(γ−1))
     *   Cf     = √(2γ²/(γ−1) · (2/(γ+1))^((γ+1)/(γ−1)) · (1 − (pe/pc)^((γ−1)/γ)))
     *            + (pe − pa) / pc · Ae/At
     * </pre>
     *
     * @param gm1   γ − 1
     * @param gamma Ratio of specific heats γ
     * @param gp1   γ + 1
     * @return Dimensionless ideal thrust coefficient Cf
     */
    private double computeThrustCoefficient(double gm1, double gamma, double gp1) {
        double pePc = Math.pow(1.0 + gm1 / 2.0 * exitMach * exitMach, -gamma / gm1);
        double pe   = chamberPressure * pePc;
        double aeAt = (1.0 / exitMach)
                      * Math.pow((2.0 / gp1) * (1.0 + gm1 / 2.0 * exitMach * exitMach),
                                  gp1 / (2.0 * gm1));

        // Ideal thrust coefficient (momentum + pressure thrust)
        double term1 = 2.0 * gamma * gamma / gm1 * Math.pow(2.0 / gp1, gp1 / gm1);
        double term2 = 1.0 - Math.pow(pePc, gm1 / gamma);
       return Math.sqrt(term1 * term2)
                      + (pe - ambientPressure) / chamberPressure * aeAt;
    }

    /**
     * Newton iteration to find the adiabatic chamber temperature at the given O/F.
     *
     * <p>Solves H_reactants(298 K) = H_products(T_c), where both sides use
     * NASA-7 absolute enthalpies (including heats of formation).  The reactant
     * enthalpies are taken from the {@link Propellant} enum (standard-state gas
     * phase at 298 K).  Typically converges in 5–8 iterations to within 1 K.
     *
     * @param of O/F mixture ratio
     * @return Adiabatic chamber temperature in K
     */
    private double adiabaticTemperature(double of) {
        NasaSpeciesDatabase db = new NasaSpeciesDatabase();
        double hReact = propellant.reactantEnthalpy(of);

        double Tc       = 3000.0;  // initial guess
        double maxStep  = 300.0;   // K; halved each time the residual sign flips
        double prevSign = 0.0;

        for (int iter = 0; iter < 80; iter++) {
            ChemistryModel model = ChemistryModel.equilibrium(propellant.defaultProperties);
            applyComposition(model, of);
            model.calculateEquilibrium(Tc, chamberPressure);

            Map<String, Double> comp = model.getSpeciesMassFractions();

            double hProd = 0.0;
            double cpMix = 0.0;
            for (Map.Entry<String, Double> e : comp.entrySet()) {
                SpeciesData sd = db.get(e.getKey());
                if (sd != null) {
                    hProd += e.getValue() * sd.enthalpy(Tc);
                    cpMix += e.getValue() * sd.cp(Tc);
                }
            }

            if (cpMix <= 0.0) break;
            double residual = hReact - hProd;
            double sign = Math.signum(residual);

            // When the residual changes sign we bracketed the root — halve the
            // maximum step so the iteration converges like a bisection.
            if (prevSign != 0.0 && sign != prevSign) {
                maxStep = Math.max(maxStep * 0.5, 1.0);
            }
            prevSign = sign;

            double dTc = residual / Math.max(cpMix, 500.0);
            dTc = Math.clamp(dTc, -maxStep, maxStep);
            Tc  = Math.clamp(Tc + dTc, 1000.0, 5500.0);
            if (Math.abs(dTc) < 0.5) break;
        }

        return Tc;
    }

    private void applyComposition(ChemistryModel model, double of) {
        switch (propellant) {
            case LOX_RP1     -> model.setLoxRp1Composition(of);
            case LOX_CH4     -> model.setLoxCh4Composition(of);
            case LOX_LH2     -> model.setLoxLh2Composition(of);
            case N2O_ETHANOL -> model.setN2oEthanolComposition(of);
            case N2O_PROPANE -> model.setN2oPropaneComposition(of);
        }
    }
}
