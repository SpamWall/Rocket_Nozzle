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
     * Calculates mixture gamma (Cp/Cv).
     *
     * @param temperature Temperature in K
     * @return Gamma
     */
    public double calculateGamma(double temperature) {
        double cp = calculateCp(temperature);
        double mw = calculateMolecularWeight();
        double R = GasProperties.UNIVERSAL_GAS_CONSTANT / mw;
        double cv = cp - R;
        return cv > 0 ? cp / cv : baseProperties.gamma();
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
