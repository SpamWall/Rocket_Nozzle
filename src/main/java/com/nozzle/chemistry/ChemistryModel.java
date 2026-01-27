package com.nozzle.chemistry;

import com.nozzle.core.GasProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Models chemical reactions and equilibrium/frozen flow chemistry in rocket nozzles.
 * Supports both frozen flow (constant composition) and equilibrium flow (shifting composition).
 */
public class ChemistryModel {
    
    /**
     * Chemistry model type.
     */
    public enum ModelType {
        /** Frozen flow - composition fixed at throat */
        FROZEN,
        /** Equilibrium flow - composition shifts with temperature */
        EQUILIBRIUM,
        /** Finite rate chemistry */
        FINITE_RATE
    }
    
    private final ModelType modelType;
    private final GasProperties baseProperties;
    private final Map<String, Double> speciesMassFractions;
    private final Map<String, SpeciesData> speciesDatabase;
    
    /**
     * Creates a chemistry model.
     *
     * @param modelType      Type of chemistry model
     * @param baseProperties Base gas properties
     */
    public ChemistryModel(ModelType modelType, GasProperties baseProperties) {
        this.modelType = modelType;
        this.baseProperties = baseProperties;
        this.speciesMassFractions = new HashMap<>();
        this.speciesDatabase = new HashMap<>();
        initializeSpeciesDatabase();
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
     * Initializes the species thermodynamic database.
     */
    private void initializeSpeciesDatabase() {
        // Common combustion product species
        speciesDatabase.put("H2O", new SpeciesData("H2O", 18.015, 
                new double[]{4.198, -2.036e-3, 6.52e-6, -5.488e-9, 1.772e-12},
                new double[]{3.034, 2.176e-3, -1.641e-7, -9.704e-11, 1.682e-14}));
        
        speciesDatabase.put("CO2", new SpeciesData("CO2", 44.01,
                new double[]{2.356, 8.984e-3, -7.123e-6, 2.459e-9, -1.437e-13},
                new double[]{4.636, 2.741e-3, -9.959e-7, 1.604e-10, -9.162e-15}));
        
        speciesDatabase.put("H2", new SpeciesData("H2", 2.016,
                new double[]{2.344, 7.980e-3, -1.948e-5, 2.016e-8, -7.376e-12},
                new double[]{2.932, 8.266e-4, -1.464e-7, 1.541e-11, -6.888e-16}));
        
        speciesDatabase.put("CO", new SpeciesData("CO", 28.01,
                new double[]{3.579, -6.103e-4, 1.017e-6, 9.070e-10, -9.044e-13},
                new double[]{3.048, 1.351e-3, -4.857e-7, 7.886e-11, -4.698e-15}));
        
        speciesDatabase.put("OH", new SpeciesData("OH", 17.007,
                new double[]{3.992, -2.401e-3, 4.617e-6, -3.879e-9, 1.363e-12},
                new double[]{2.839, 1.107e-3, -2.940e-7, 4.206e-11, -2.422e-15}));
        
        speciesDatabase.put("O2", new SpeciesData("O2", 32.0,
                new double[]{3.782, -2.997e-3, 9.847e-6, -9.681e-9, 3.243e-12},
                new double[]{3.661, 6.563e-4, -1.411e-7, 2.058e-11, -1.299e-15}));
        
        speciesDatabase.put("N2", new SpeciesData("N2", 28.014,
                new double[]{3.298, 1.408e-3, -3.963e-6, 5.642e-9, -2.445e-12},
                new double[]{2.927, 1.487e-3, -5.685e-7, 1.010e-10, -6.753e-15}));
        
        speciesDatabase.put("H", new SpeciesData("H", 1.008,
                new double[]{2.5, 0, 0, 0, 0},
                new double[]{2.5, 0, 0, 0, 0}));
        
        speciesDatabase.put("O", new SpeciesData("O", 16.0,
                new double[]{3.169, -3.279e-3, 6.643e-6, -6.128e-9, 2.113e-12},
                new double[]{2.543, -2.731e-5, -4.190e-9, 4.954e-12, -4.795e-16}));
    }
    
    /**
     * Sets species mass fractions for the mixture.
     *
     * @param fractions Map of species name to mass fraction
     */
    public void setSpeciesMassFractions(Map<String, Double> fractions) {
        speciesMassFractions.clear();
        speciesMassFractions.putAll(fractions);
        normalizeComposition();
    }
    
    /**
     * Sets LOX/RP-1 combustion products composition.
     *
     * @param mixtureRatio O/F mixture ratio
     */
    public void setLoxRp1Composition(double mixtureRatio) {
        speciesMassFractions.clear();
        
        // Approximate composition based on mixture ratio
        if (mixtureRatio < 2.0) {
            // Fuel rich
            speciesMassFractions.put("CO", 0.45);
            speciesMassFractions.put("H2O", 0.25);
            speciesMassFractions.put("CO2", 0.15);
            speciesMassFractions.put("H2", 0.10);
            speciesMassFractions.put("OH", 0.05);
        } else if (mixtureRatio < 2.8) {
            // Near stoichiometric
            speciesMassFractions.put("CO2", 0.35);
            speciesMassFractions.put("H2O", 0.35);
            speciesMassFractions.put("CO", 0.20);
            speciesMassFractions.put("OH", 0.05);
            speciesMassFractions.put("H2", 0.05);
        } else {
            // Oxidizer rich
            speciesMassFractions.put("CO2", 0.45);
            speciesMassFractions.put("H2O", 0.30);
            speciesMassFractions.put("O2", 0.15);
            speciesMassFractions.put("OH", 0.10);
        }
        
        normalizeComposition();
    }
    
    /**
     * Sets LOX/LH2 combustion products composition.
     *
     * @param mixtureRatio O/F mixture ratio
     */
    public void setLoxLh2Composition(double mixtureRatio) {
        speciesMassFractions.clear();
        
        if (mixtureRatio < 5.0) {
            // Fuel rich
            speciesMassFractions.put("H2O", 0.60);
            speciesMassFractions.put("H2", 0.30);
            speciesMassFractions.put("OH", 0.08);
            speciesMassFractions.put("H", 0.02);
        } else if (mixtureRatio < 7.0) {
            // Near stoichiometric
            speciesMassFractions.put("H2O", 0.85);
            speciesMassFractions.put("OH", 0.10);
            speciesMassFractions.put("H2", 0.03);
            speciesMassFractions.put("O2", 0.02);
        } else {
            // Oxidizer rich
            speciesMassFractions.put("H2O", 0.75);
            speciesMassFractions.put("O2", 0.15);
            speciesMassFractions.put("OH", 0.10);
        }
        
        normalizeComposition();
    }
    
    /**
     * Normalizes composition to sum to 1.0.
     */
    private void normalizeComposition() {
        double sum = speciesMassFractions.values().stream()
                .mapToDouble(Double::doubleValue).sum();
        if (sum > 0) {
            speciesMassFractions.replaceAll((k, v) -> v / sum);
        }
    }
    
    /**
     * Calculates mixture molecular weight.
     *
     * @return Molecular weight in kg/kmol
     */
    public double calculateMolecularWeight() {
        if (speciesMassFractions.isEmpty()) {
            return baseProperties.molecularWeight();
        }
        
        double invMW = 0;
        for (Map.Entry<String, Double> entry : speciesMassFractions.entrySet()) {
            SpeciesData species = speciesDatabase.get(entry.getKey());
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
        if (speciesMassFractions.isEmpty()) {
            return baseProperties.specificHeatCp();
        }
        
        double cp = 0;
        for (Map.Entry<String, Double> entry : speciesMassFractions.entrySet()) {
            SpeciesData species = speciesDatabase.get(entry.getKey());
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
        if (modelType == ModelType.FROZEN || speciesMassFractions.isEmpty()) {
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
     * Calculates equilibrium composition at given temperature and pressure.
     *
     * @param temperature Temperature in K
     * @param pressure    Pressure in Pa
     */
    public void calculateEquilibrium(double temperature, double pressure) {
        if (modelType != ModelType.EQUILIBRIUM) {
            return;
        }
        
        // Simplified equilibrium calculation
        // For full implementation, would use Gibbs minimization
        double dissociationFactor = Math.exp(-50000 / (8.314 * temperature));
        
        // Adjust composition based on dissociation
        if (speciesMassFractions.containsKey("H2O")) {
            double h2o = speciesMassFractions.get("H2O");
            double dissociated = h2o * dissociationFactor * 0.1;
            speciesMassFractions.put("H2O", h2o - dissociated);
            speciesMassFractions.merge("OH", dissociated * 0.6, Double::sum);
            speciesMassFractions.merge("H2", dissociated * 0.2, Double::sum);
            speciesMassFractions.merge("O2", dissociated * 0.2, Double::sum);
        }
        
        if (speciesMassFractions.containsKey("CO2")) {
            double co2 = speciesMassFractions.get("CO2");
            double dissociated = co2 * dissociationFactor * 0.05;
            speciesMassFractions.put("CO2", co2 - dissociated);
            speciesMassFractions.merge("CO", dissociated * 0.7, Double::sum);
            speciesMassFractions.merge("O2", dissociated * 0.3, Double::sum);
        }
        
        normalizeComposition();
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
        return new HashMap<>(speciesMassFractions);
    }
    
    /**
     * Species thermodynamic data using NASA polynomial coefficients.
     */
    public record SpeciesData(
            String name,
            double molecularWeight,
            double[] lowTempCoeffs,  // 200-1000 K
            double[] highTempCoeffs  // 1000-6000 K
    ) {
        private static final double R_UNIVERSAL = 8314.46; // J/(kmol·K)
        
        /**
         * Calculates specific heat at constant pressure.
         *
         * @param temperature Temperature in K
         * @return Cp in J/(kg·K)
         */
        public double cp(double temperature) {
            double[] coeffs = temperature < 1000 ? lowTempCoeffs : highTempCoeffs;
            double T = temperature;
            double cpMolar = R_UNIVERSAL * (coeffs[0] + coeffs[1]*T + coeffs[2]*T*T 
                    + coeffs[3]*T*T*T + coeffs[4]*T*T*T*T);
            return cpMolar / molecularWeight;
        }
        
        /**
         * Calculates enthalpy.
         *
         * @param temperature Temperature in K
         * @return Enthalpy in J/kg
         */
        public double enthalpy(double temperature) {
            double[] coeffs = temperature < 1000 ? lowTempCoeffs : highTempCoeffs;
            double T = temperature;
            double hMolar = R_UNIVERSAL * T * (coeffs[0] + coeffs[1]*T/2 + coeffs[2]*T*T/3 
                    + coeffs[3]*T*T*T/4 + coeffs[4]*T*T*T*T/5);
            return hMolar / molecularWeight;
        }
        
        /**
         * Calculates entropy.
         *
         * @param temperature Temperature in K
         * @return Entropy in J/(kg·K)
         */
        public double entropy(double temperature) {
            double[] coeffs = temperature < 1000 ? lowTempCoeffs : highTempCoeffs;
            double T = temperature;
            double sMolar = R_UNIVERSAL * (coeffs[0]*Math.log(T) + coeffs[1]*T + coeffs[2]*T*T/2 
                    + coeffs[3]*T*T*T/3 + coeffs[4]*T*T*T*T/4);
            return sMolar / molecularWeight;
        }
    }
}
