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
     * Sets LOX/RP-1 combustion products composition.
     *
     * @param mixtureRatio O/F mixture ratio
     */
    public void setLoxRp1(double mixtureRatio) {
        massFractions.clear();
        if (mixtureRatio < 2.0) {
            // Fuel rich
            massFractions.put("CO",  0.45);
            massFractions.put("H2O", 0.25);
            massFractions.put("CO2", 0.15);
            massFractions.put("H2",  0.10);
            massFractions.put("OH",  0.05);
        } else if (mixtureRatio < 2.8) {
            // Near stoichiometric
            massFractions.put("CO2", 0.35);
            massFractions.put("H2O", 0.35);
            massFractions.put("CO",  0.20);
            massFractions.put("OH",  0.05);
            massFractions.put("H2",  0.05);
        } else {
            // Oxidizer rich
            massFractions.put("CO2", 0.45);
            massFractions.put("H2O", 0.30);
            massFractions.put("O2",  0.15);
            massFractions.put("OH",  0.10);
        }
        normalize();
    }

    /**
     * Sets LOX/CH4 combustion products composition.
     * CH4 + 2O2 → CO2 + 2H2O; stoichiometric O/F ≈ 4.0.
     *
     * @param mixtureRatio O/F mixture ratio
     */
    public void setLoxCh4(double mixtureRatio) {
        massFractions.clear();
        if (mixtureRatio < 3.0) {
            // Fuel rich
            massFractions.put("CO",  0.40);
            massFractions.put("H2O", 0.20);
            massFractions.put("H2",  0.20);
            massFractions.put("CO2", 0.10);
            massFractions.put("OH",  0.05);
            massFractions.put("H",   0.05);
        } else if (mixtureRatio < 4.0) {
            // Near stoichiometric
            massFractions.put("H2O", 0.35);
            massFractions.put("CO2", 0.25);
            massFractions.put("CO",  0.20);
            massFractions.put("H2",  0.10);
            massFractions.put("OH",  0.08);
            massFractions.put("O2",  0.02);
        } else {
            // Oxidizer rich
            massFractions.put("CO2", 0.40);
            massFractions.put("H2O", 0.30);
            massFractions.put("O2",  0.15);
            massFractions.put("OH",  0.10);
            massFractions.put("CO",  0.05);
        }
        normalize();
    }

    /**
     * Sets LOX/LH2 combustion products composition.
     *
     * @param mixtureRatio O/F mixture ratio
     */
    public void setLoxLh2(double mixtureRatio) {
        massFractions.clear();
        if (mixtureRatio < 5.0) {
            // Fuel rich
            massFractions.put("H2O", 0.60);
            massFractions.put("H2",  0.30);
            massFractions.put("OH",  0.08);
            massFractions.put("H",   0.02);
        } else if (mixtureRatio < 7.0) {
            // Near stoichiometric
            massFractions.put("H2O", 0.85);
            massFractions.put("OH",  0.10);
            massFractions.put("H2",  0.03);
            massFractions.put("O2",  0.02);
        } else {
            // Oxidizer rich
            massFractions.put("H2O", 0.75);
            massFractions.put("O2",  0.15);
            massFractions.put("OH",  0.10);
        }
        normalize();
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
            massFractions.replaceAll((k, v) -> v / sum);
        }
    }
}
