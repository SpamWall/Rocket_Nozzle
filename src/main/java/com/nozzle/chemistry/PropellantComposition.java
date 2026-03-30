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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Combustion-product composition expressed as species mass fractions.
 * Provides preset compositions for LOX/RP-1, LOX/CH4, and LOX/LH2 propellant
 * combinations, as well as direct assignment from a caller-supplied map.
 * All setter methods normalize the resulting fractions so they sum to 1.0.
 */
public final class PropellantComposition {

    private final Map<String, Double> massFractions = new HashMap<>();

    /** Creates an empty {@code PropellantComposition} with no species assigned. */
    public PropellantComposition() {}

    /**
     * Replaces the current composition with the supplied mass-fraction map.
     * The fractions are normalized so they sum to 1.0.
     *
     * @param fractions Map of species name to mass fraction (need not be pre-normalized)
     */
    public void set(Map<String, Double> fractions) {
        massFractions.clear();
        massFractions.putAll(fractions);
        normalize();
    }

    /**
     * Sets LOX/RP-1 initial composition for Gibbs minimization.
     * RP-1 is modeled as C₁₂H₂₃.₄ (MW = 167.6 g/mol, matching NASA CEA).
     * Atom counts for the O/F ratio are mapped to solver species (CO, H₂O, H₂,
     * O₂) so that the element balance passed to the minimizer exactly matches
     * the propellant mixture — not an approximate product guess.
     *
     * @param mixtureRatio O/F mixture ratio by mass
     */
    public void setLoxRp1(double mixtureRatio) {
        massFractions.clear();
        // RP-1: C12H23.4, MW = 12*12 + 23.4*1.008 = 167.587 g/mol
        double mwRp1 = 167.587;
        double molC = 12.0  / (mwRp1 * (mixtureRatio + 1.0));
        double molH = 23.4  / (mwRp1 * (mixtureRatio + 1.0));
        double molO = 2.0 * mixtureRatio / (32.0 * (mixtureRatio + 1.0));
        massFractions.putAll(atomsToInitialComposition(molH, molC, molO, 0.0));
        normalize();
    }

    /**
     * Sets LOX/CH4 initial composition for Gibbs minimization.
     * CH4 (MW = 16.043 g/mol) + O₂ atom counts are mapped to solver species
     * so that the element balance exactly matches the propellant mixture.
     *
     * @param mixtureRatio O/F mixture ratio by mass
     */
    public void setLoxCh4(double mixtureRatio) {
        massFractions.clear();
        double molC = 1.0 / (16.043 * (mixtureRatio + 1.0));
        double molH = 4.0 / (16.043 * (mixtureRatio + 1.0));
        double molO = 2.0 * mixtureRatio / (32.0 * (mixtureRatio + 1.0));
        massFractions.putAll(atomsToInitialComposition(molH, molC, molO, 0.0));
        normalize();
    }

    /**
     * Sets LOX/LH2 initial composition for Gibbs minimization.
     * H₂ (MW = 2.016 g/mol) + O₂ atom counts are mapped to solver species
     * so that the element balance exactly matches the propellant mixture.
     *
     * @param mixtureRatio O/F mixture ratio by mass
     */
    public void setLoxLh2(double mixtureRatio) {
        massFractions.clear();
        double molH = 2.0 / (2.016 * (mixtureRatio + 1.0));
        double molO = 2.0 * mixtureRatio / (32.0 * (mixtureRatio + 1.0));
        massFractions.putAll(atomsToInitialComposition(molH, 0.0, molO, 0.0));
        normalize();
    }

    /**
     * Sets N₂O/ethanol initial composition for Gibbs minimization.
     * Ethanol is modeled as C₂H₅OH (MW = 46.069 g/mol).
     * N₂O (MW = 44.013 g/mol) contributes 2 N and 1 O per molecule; the
     * nitrogen atoms are seeded entirely as N₂, which is their dominant
     * equilibrium form at combustion temperatures.
     *
     * <p>Stoichiometric O/F is ≈ 5.73 (C₂H₅OH + 6 N₂O → 2 CO₂ + 3 H₂O + 6 N₂).
     *
     * @param mixtureRatio O/F mixture ratio by mass
     */
    public void setN2oEthanol(double mixtureRatio) {
        massFractions.clear();
        // N2O: 2 N + 1 O per molecule; Ethanol (C2H5OH): 2 C + 6 H + 1 O
        double mwN2o     = 44.013;
        double mwEthanol = 46.069;
        double total     = mixtureRatio + 1.0;
        double molN = 2.0 * mixtureRatio / (mwN2o * total);
        double molO = mixtureRatio / (mwN2o * total) + 1.0 / (mwEthanol * total);
        double molC = 2.0 / (mwEthanol * total);
        double molH = 6.0 / (mwEthanol * total);
        massFractions.putAll(atomsToInitialComposition(molH, molC, molO, molN));
        normalize();
    }

    /**
     * Sets N₂O/propane initial composition for Gibbs minimization.
     * Propane is modeled as C₃H₈ (MW = 44.097 g/mol).
     * N₂O (MW = 44.013 g/mol) contributes 2 N and 1 O per molecule; the
     * nitrogen atoms are seeded entirely as N₂.
     *
     * <p>Stoichiometric O/F is ≈ 9.98 (C₃H₈ + 10 N₂O → 3 CO₂ + 4 H₂O + 10 N₂).
     *
     * @param mixtureRatio O/F mixture ratio by mass
     */
    public void setN2oPropane(double mixtureRatio) {
        massFractions.clear();
        // N2O: 2 N + 1 O per molecule; Propane (C3H8): 3 C + 8 H, no O
        double mwN2o    = 44.013;
        double mwPropane = 44.097;
        double total     = mixtureRatio + 1.0;
        double molN = 2.0 * mixtureRatio / (mwN2o * total);
        double molO = mixtureRatio / (mwN2o * total);
        double molC = 3.0 / (mwPropane * total);
        double molH = 8.0 / (mwPropane * total);
        massFractions.putAll(atomsToInitialComposition(molH, molC, molO, molN));
        normalize();
    }

    /**
     * Maps element mole-counts (per gram of mixture) to a balanced
     * product-dissociation initial composition for the Newton–Lagrange solver.
     *
     * <p>Starting from a product-like state with a realistic dissociation seed
     * gives the solver a good initial gradient.
     *
     * <p>Strategy:
     * <ol>
     *   <li>C-containing systems: assign C to CO first (1 O each), promote up
     *       to 70 % to CO₂ where O allows (1 more O each), form H₂O from
     *       remaining O (limited by available H) with a 10 % OH seed, and
     *       route any leftover O to O₂ and leftover H to H₂.</li>
     *   <li>C-free (LOX/LH₂): reserve 10 % of the O budget for O₂ so that
     *       both radical channels are seeded regardless of mixture ratio;
     *       form H₂O from the remaining 90 % of O (limited by H) with a
     *       3 % OH seed, and place leftover H in H₂.</li>
     * </ol>
     *
     * <p>Element balance is preserved exactly: H and O atom counts across
     * all returned species equal {@code molH} and {@code molO}.
     * <p>
     * The returned map values are in grams per gram of mixture (sum ≈ 1 before
     * the caller normalizes).
     */
    private static Map<String, Double> atomsToInitialComposition(
            double molH, double molC, double molO, double molN) {
        Map<String, Double> map = new HashMap<>();

        if (molC > 1e-15) {
            // Carbon-containing propellants (LOX/CH4, LOX/RP-1)
            double oRem = molO;

            // Assign all C to CO first (1 O per CO), then promote up to 70% to CO2
            double nCo = Math.min(molC, oRem);
            oRem -= nCo;
            double nCo2 = Math.min(0.70 * molC, Math.min(nCo, oRem));
            nCo -= nCo2;
            oRem -= nCo2;

            // Form H2O with 10% OH seed, limited by both remaining O and available H
            double nH2Omax = molH / 2.10;
            double nH2O = oRem > 1e-15 ? Math.min(oRem / 1.10, nH2Omax) : 0.0;
            double nOH  = 0.10 * nH2O;
            oRem -= nH2O + nOH;

            // Excess O → O2 (oxidizer-rich case); excess H → H2
            double nO2  = oRem > 1e-15 ? oRem / 2.0 : 0.0;
            double hRem = molH - 2.0 * nH2O - nOH;
            double nH2  = hRem > 1e-15 ? hRem / 2.0 : 0.0;

            if (nCo2 > 1e-15) map.put("CO2", nCo2 * 44.01);
            if (nCo  > 1e-15) map.put("CO",  nCo  * 28.01);
            if (nH2O > 1e-15) map.put("H2O", nH2O * 18.015);
            if (nOH  > 1e-15) map.put("OH",  nOH  * 17.007);
            if (nH2  > 1e-15) map.put("H2",  nH2  * 2.016);
            if (nO2  > 1e-15) map.put("O2",  nO2  * 32.0);
        } else {
            // C-free propellants (LOX/LH2)
            // Reserve 10 % of O for O2 so both H2 and O2 channels are always seeded.
            // Form H2O from the remaining 90 % of O (limited by H) with a 3 % OH seed.
            double oForWater = 0.90 * molO;
            double nH2Omax = molH / 2.03;
            double nH2O = oForWater > 1e-15 ? Math.min(oForWater / 1.03, nH2Omax) : 0.0;
            double nOH  = 0.03 * nH2O;
            double oRem = molO - nH2O - nOH;
            double nO2  = oRem > 1e-15 ? oRem / 2.0 : 0.0;
            double hRem = molH - 2.0 * nH2O - nOH;
            double nH2  = hRem > 1e-15 ? hRem / 2.0 : 0.0;

            if (nH2O > 1e-15) map.put("H2O", nH2O * 18.015);
            if (nOH  > 1e-15) map.put("OH",  nOH  * 17.007);
            if (nH2  > 1e-15) map.put("H2",  nH2  * 2.016);
            if (nO2  > 1e-15) map.put("O2",  nO2  * 32.0);
        }

        // Seed all nitrogen as N2 — the overwhelmingly dominant N-species at
        // combustion temperatures; no other N-containing species are in the database.
        if (molN > 1e-15) map.put("N2", (molN / 2.0) * 28.014);

        return map;
    }

    /**
     * Returns an unmodifiable snapshot of the current mass-fraction map.
     *
     * @return Unmodifiable map of species name to mass fraction
     */
    public Map<String, Double> get() {
        return Collections.unmodifiableMap(massFractions);
    }

    /**
     * Returns {@code true} if no species have been added yet.
     *
     * @return {@code true} if the composition is empty
     */
    public boolean isEmpty() {
        return massFractions.isEmpty();
    }

    /**
     * Normalizes the mass-fraction map so that all values sum to 1.0.
     * Has no effect if the map is empty or the total is zero.
     */
    private void normalize() {
        double sum = massFractions.values().stream().mapToDouble(Double::doubleValue).sum();
        if (sum > 0) {
            massFractions.replaceAll((_, v) -> v / sum);
        }
    }
}
