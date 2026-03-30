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

import java.util.HashMap;
import java.util.Map;

/**
 * Models chemical reactions and equilibrium/frozen flow chemistry in rocket nozzles.
 * Supports both frozen flow (constant composition) and equilibrium flow (shifting composition).
 * <p>
 * Orchestrates {@link PropellantComposition} for species mass-fraction management,
 * {@link NasaSpeciesDatabase} for NASA-7 polynomial thermodynamic data, and
 * {@link GibbsMinimizer} for Gibbs free energy minimization.
 */
public class ChemistryModel {

    /**
     * Chemistry model type.
     */
    public enum ModelType {
        /** Frozen flow — composition fixed at throat. */
        FROZEN,
        /** Equilibrium flow — composition shifts with temperature. */
        EQUILIBRIUM,
        /** Finite rate chemistry. */
        FINITE_RATE
    }

    private final ModelType modelType;
    private final GasProperties baseProperties;
    private final PropellantComposition composition;
    private final NasaSpeciesDatabase database;
    private final GibbsMinimizer minimizer;

    /**
     * Creates a chemistry model.
     *
     * @param modelType      Type of chemistry model
     * @param baseProperties Base gas properties
     */
    public ChemistryModel(ModelType modelType, GasProperties baseProperties) {
        this.modelType = modelType;
        this.baseProperties = baseProperties;
        this.composition = new PropellantComposition();
        this.database = new NasaSpeciesDatabase();
        this.minimizer = new GibbsMinimizer(database);
    }

    /**
     * Creates a frozen flow model.
     *
     * @param baseProperties Base gas properties
     * @return Frozen flow chemistry model
     */
    public static ChemistryModel frozen(GasProperties baseProperties) {
        return new ChemistryModel(ModelType.FROZEN, baseProperties);
    }

    /**
     * Creates an equilibrium flow model.
     *
     * @param baseProperties Base gas properties
     * @return Equilibrium flow chemistry model
     */
    public static ChemistryModel equilibrium(GasProperties baseProperties) {
        return new ChemistryModel(ModelType.EQUILIBRIUM, baseProperties);
    }

    /**
     * Sets species mass fractions for the mixture.
     *
     * @param fractions Map of species name to mass fraction
     */
    public void setSpeciesMassFractions(Map<String, Double> fractions) {
        composition.set(fractions);
    }

    /**
     * Sets LOX/RP-1 combustion products composition.
     *
     * @param mixtureRatio O/F mixture ratio
     */
    public void setLoxRp1Composition(double mixtureRatio) {
        composition.setLoxRp1(mixtureRatio);
    }

    /**
     * Sets LOX/CH4 combustion products composition.
     * CH4 + 2O2 → CO2 + 2H2O; stoichiometric O/F ≈ 4.0.
     *
     * @param mixtureRatio O/F mixture ratio
     */
    public void setLoxCh4Composition(double mixtureRatio) {
        composition.setLoxCh4(mixtureRatio);
    }

    /**
     * Sets LOX/LH2 combustion products composition.
     *
     * @param mixtureRatio O/F mixture ratio
     */
    public void setLoxLh2Composition(double mixtureRatio) {
        composition.setLoxLh2(mixtureRatio);
    }

    /**
     * Sets N2O/ethanol combustion products composition.
     * Ethanol (C2H5OH) + N2O → CO2, H2O, CO, N2; stoichiometric O/F ≈ 5.73.
     *
     * @param mixtureRatio O/F mixture ratio
     */
    public void setN2oEthanolComposition(double mixtureRatio) {
        composition.setN2oEthanol(mixtureRatio);
    }

    /**
     * Sets N2O/propane combustion products composition.
     * Propane (C3H8) + N2O → CO2, H2O, CO, N2; stoichiometric O/F ≈ 9.98.
     *
     * @param mixtureRatio O/F mixture ratio
     */
    public void setN2oPropaneComposition(double mixtureRatio) {
        composition.setN2oPropane(mixtureRatio);
    }

    /**
     * Calculates mixture molecular weight.
     *
     * @return Molecular weight in kg/kmol
     */
    public double calculateMolecularWeight() {
        if (composition.isEmpty()) {
            return baseProperties.molecularWeight();
        }
        double invMW = 0;
        for (Map.Entry<String, Double> entry : composition.get().entrySet()) {
            SpeciesData species = database.get(entry.getKey());
            if (species != null) {
                invMW += entry.getValue() / species.molecularWeight();
            }
        }
        return invMW > 0 ? 1.0 / invMW : baseProperties.molecularWeight();
    }

    /**
     * Calculates mixture specific heat at constant pressure.
     *
     * @param temperature Temperature in K
     * @return Cp in J/(kg·K)
     */
    public double calculateCp(double temperature) {
        if (composition.isEmpty()) {
            return baseProperties.specificHeatCp();
        }
        double cp = 0;
        for (Map.Entry<String, Double> entry : composition.get().entrySet()) {
            SpeciesData species = database.get(entry.getKey());
            if (species != null) {
                cp += entry.getValue() * species.cp(temperature);
            }
        }
        return cp > 0 ? cp : baseProperties.specificHeatCp();
    }

    /**
     * Calculates frozen specific-heat ratio γ = Cp/(Cp − R/M̄).
     * Composition is held fixed at the values set by the most recent call to
     * {@link #calculateEquilibrium} (or the initial propellant composition if
     * that method has not been called).
     *
     * @param temperature Temperature in K
     * @return Frozen γ
     */
    public double calculateGamma(double temperature) {
        double cp = calculateCp(temperature);
        double mw = calculateMolecularWeight();
        double R = GasProperties.UNIVERSAL_GAS_CONSTANT / mw;
        double cv = cp - R;
        return cv > 0 ? cp / cv : baseProperties.gamma();
    }

    /**
     * Calculates the equilibrium isentropic exponent γ_s using central
     * finite-difference perturbations of the Gibbs minimizer:
     * <pre>
     *   γ_s = Cp_eq / [(-β)·Cp_eq − (R/M̄)·α²]
     * </pre>
     * where
     * <ul>
     *   <li>α = (∂ln V/∂ln T)_P = 1 − (T/M̄)·(∂M̄/∂T)_P — captures the
     *       increase in specific volume as dissociation lightens the mixture</li>
     *   <li>β = (∂ln V/∂ln P)_T = −1 − (P/M̄)·(∂M̄/∂P)_T — captures the
     *       reduction in dissociation (heavier mixture) at higher pressure</li>
     *   <li>Cp_eq = (∂H/∂T)_P along the equilibrium path — includes the
     *       latent-heat contribution of composition shifts</li>
     * </ul>
     * For frozen composition (α = 1, β = −1) the formula reduces to the
     * standard Cp/(Cp − R/M̄).  This matches CEA's {@code GAMMAs} output for
     * a {@code tp} problem.
     *
     * @param temperature Temperature in K
     * @param pressure    Pressure in Pa
     * @return Equilibrium isentropic exponent γ_s
     */
    public double calculateEquilibriumGamma(double temperature, double pressure) {
        if (composition.isEmpty()) {
            return baseProperties.gamma();
        }

        double dT = temperature * 0.01;
        double dP = pressure   * 0.01;

        Map<String, Double> compTPlus  = minimizer.minimize(composition.get(), temperature + dT, pressure);
        Map<String, Double> compTMinus = minimizer.minimize(composition.get(), temperature - dT, pressure);

        double hPlus  = mixtureEnthalpy(compTPlus,  temperature + dT);
        double hMinus = mixtureEnthalpy(compTMinus, temperature - dT);
        double cpEq   = (hPlus - hMinus) / (2.0 * dT);

        double mw       = calculateMolecularWeight();
        double mwTPlus  = calculateMolecularWeightOf(compTPlus);
        double mwTMinus = calculateMolecularWeightOf(compTMinus);
        double alpha    = 1.0 - (temperature / mw) * (mwTPlus - mwTMinus) / (2.0 * dT);

        Map<String, Double> compPPlus  = minimizer.minimize(composition.get(), temperature, pressure + dP);
        Map<String, Double> compPMinus = minimizer.minimize(composition.get(), temperature, pressure - dP);
        double mwPPlus  = calculateMolecularWeightOf(compPPlus);
        double mwPMinus = calculateMolecularWeightOf(compPMinus);
        double beta     = -1.0 - (pressure / mw) * (mwPPlus - mwPMinus) / (2.0 * dP);

        double R     = GasProperties.UNIVERSAL_GAS_CONSTANT / mw;
        double denom = (-beta) * cpEq - R * alpha * alpha;
        return denom > 0 ? cpEq / denom : baseProperties.gamma();
    }

    private double mixtureEnthalpy(Map<String, Double> comp, double temperature) {
        double h = 0;
        for (Map.Entry<String, Double> entry : comp.entrySet()) {
            SpeciesData species = database.get(entry.getKey());
            if (species != null) {
                h += entry.getValue() * species.enthalpy(temperature);
            }
        }
        return h;
    }

    private double calculateMolecularWeightOf(Map<String, Double> comp) {
        double invMW = 0;
        for (Map.Entry<String, Double> entry : comp.entrySet()) {
            SpeciesData species = database.get(entry.getKey());
            if (species != null) {
                invMW += entry.getValue() / species.molecularWeight();
            }
        }
        return invMW > 0 ? 1.0 / invMW : baseProperties.molecularWeight();
    }

    /**
     * Gets effective gas properties at given temperature.
     *
     * @param temperature Temperature in K
     * @return Effective gas properties
     */
    public GasProperties getEffectiveProperties(double temperature) {
        if (modelType == ModelType.FROZEN || composition.isEmpty()) {
            return baseProperties;
        }
        double gamma = calculateGamma(temperature);
        double mw = calculateMolecularWeight();
        double R = GasProperties.UNIVERSAL_GAS_CONSTANT / mw;
        return new GasProperties(gamma, mw, R,
                baseProperties.viscosityRef(),
                baseProperties.tempRef(),
                baseProperties.sutherlandConst());
    }

    /**
     * Calculates equilibrium composition at given temperature and pressure
     * using Gibbs free energy minimization with Lagrangian elemental constraints.
     * <p>
     * Has no effect when {@link ModelType} is not {@code EQUILIBRIUM}.
     *
     * @param temperature Temperature in K
     * @param pressure    Pressure in Pa
     */
    public void calculateEquilibrium(double temperature, double pressure) {
        if (modelType != ModelType.EQUILIBRIUM) {
            return;
        }
        Map<String, Double> result = minimizer.minimize(composition.get(), temperature, pressure);
        composition.set(result);
    }

    /**
     * Gets the chemistry model type.
     *
     * @return Model type
     */
    public ModelType getModelType() {
        return modelType;
    }

    /**
     * Gets current species mass fractions.
     *
     * @return Map of species to mass fraction
     */
    public Map<String, Double> getSpeciesMassFractions() {
        return new HashMap<>(composition.get());
    }
}
