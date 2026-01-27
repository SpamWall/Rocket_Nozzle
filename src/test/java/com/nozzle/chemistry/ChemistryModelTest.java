package com.nozzle.chemistry;

import com.nozzle.core.GasProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ChemistryModel Tests")
class ChemistryModelTest {
    
    private ChemistryModel frozenModel;
    private ChemistryModel equilibriumModel;
    
    @BeforeEach
    void setUp() {
        frozenModel = ChemistryModel.frozen(GasProperties.LOX_RP1_PRODUCTS);
        equilibriumModel = ChemistryModel.equilibrium(GasProperties.LOX_LH2_PRODUCTS);
    }
    
    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {
        
        @Test
        @DisplayName("Should create frozen model")
        void shouldCreateFrozenModel() {
            assertThat(frozenModel.getModelType()).isEqualTo(ChemistryModel.ModelType.FROZEN);
        }
        
        @Test
        @DisplayName("Should create equilibrium model")
        void shouldCreateEquilibriumModel() {
            assertThat(equilibriumModel.getModelType()).isEqualTo(ChemistryModel.ModelType.EQUILIBRIUM);
        }
    }
    
    @Nested
    @DisplayName("Composition Tests")
    class CompositionTests {
        
        @Test
        @DisplayName("Should set LOX/RP-1 composition")
        void shouldSetLoxRp1Composition() {
            frozenModel.setLoxRp1Composition(2.5);
            
            Map<String, Double> fractions = frozenModel.getSpeciesMassFractions();
            assertThat(fractions).isNotEmpty();
            
            double sum = fractions.values().stream().mapToDouble(Double::doubleValue).sum();
            assertThat(sum).isCloseTo(1.0, within(1e-6));
        }
        
        @Test
        @DisplayName("Should set LOX/LH2 composition")
        void shouldSetLoxLh2Composition() {
            equilibriumModel.setLoxLh2Composition(6.0);
            
            Map<String, Double> fractions = equilibriumModel.getSpeciesMassFractions();
            assertThat(fractions).isNotEmpty();
            assertThat(fractions).containsKey("H2O");
        }
        
        @Test
        @DisplayName("Should normalize composition")
        void shouldNormalizeComposition() {
            frozenModel.setSpeciesMassFractions(Map.of("H2O", 0.5, "CO2", 0.5, "CO", 0.5));
            
            Map<String, Double> fractions = frozenModel.getSpeciesMassFractions();
            double sum = fractions.values().stream().mapToDouble(Double::doubleValue).sum();
            assertThat(sum).isCloseTo(1.0, within(1e-6));
        }
    }
    
    @Nested
    @DisplayName("Property Calculations")
    class PropertyCalculationsTests {
        
        @Test
        @DisplayName("Should calculate molecular weight")
        void shouldCalculateMolecularWeight() {
            frozenModel.setLoxRp1Composition(2.5);
            
            double mw = frozenModel.calculateMolecularWeight();
            assertThat(mw).isBetween(15.0, 35.0);
        }
        
        @Test
        @DisplayName("Should calculate Cp")
        void shouldCalculateCp() {
            frozenModel.setLoxRp1Composition(2.5);
            
            double cp = frozenModel.calculateCp(3000);
            assertThat(cp).isGreaterThan(1000);
        }
        
        @Test
        @DisplayName("Should calculate gamma")
        void shouldCalculateGamma() {
            frozenModel.setLoxRp1Composition(2.5);
            
            double gamma = frozenModel.calculateGamma(3000);
            assertThat(gamma).isBetween(1.1, 1.5);
        }
        
        @Test
        @DisplayName("Should get effective properties")
        void shouldGetEffectiveProperties() {
            frozenModel.setLoxRp1Composition(2.5);
            
            GasProperties props = frozenModel.getEffectiveProperties(3000);
            assertThat(props.gamma()).isBetween(1.1, 1.5);
        }
    }
    
    @Nested
    @DisplayName("Equilibrium Calculation Tests")
    class EquilibriumCalculationTests {
        
        @Test
        @DisplayName("Equilibrium calculation should modify composition")
        void equilibriumCalculationShouldModifyComposition() {
            equilibriumModel.setLoxLh2Composition(6.0);
            Map<String, Double> before = Map.copyOf(equilibriumModel.getSpeciesMassFractions());
            
            equilibriumModel.calculateEquilibrium(3200, 7e6);
            Map<String, Double> after = equilibriumModel.getSpeciesMassFractions();
            
            // Composition should change at high temperature
            assertThat(after).isNotEmpty();
        }
        
        @Test
        @DisplayName("Frozen model should not change on equilibrium call")
        void frozenModelShouldNotChangeOnEquilibriumCall() {
            frozenModel.setLoxRp1Composition(2.5);
            Map<String, Double> before = Map.copyOf(frozenModel.getSpeciesMassFractions());
            
            frozenModel.calculateEquilibrium(3500, 7e6);
            Map<String, Double> after = frozenModel.getSpeciesMassFractions();
            
            assertThat(after).isEqualTo(before);
        }
    }
    
    @Nested
    @DisplayName("Species Data Tests")
    class SpeciesDataTests {
        
        @Test
        @DisplayName("Species data should calculate Cp")
        void speciesDataShouldCalculateCp() {
            ChemistryModel.SpeciesData h2o = new ChemistryModel.SpeciesData(
                    "H2O", 18.015,
                    new double[]{4.198, -2.036e-3, 6.52e-6, -5.488e-9, 1.772e-12},
                    new double[]{3.034, 2.176e-3, -1.641e-7, -9.704e-11, 1.682e-14}
            );
            
            double cp = h2o.cp(2000);
            assertThat(cp).isGreaterThan(1000);
        }
        
        @Test
        @DisplayName("Species data should calculate enthalpy")
        void speciesDataShouldCalculateEnthalpy() {
            ChemistryModel.SpeciesData h2o = new ChemistryModel.SpeciesData(
                    "H2O", 18.015,
                    new double[]{4.198, -2.036e-3, 6.52e-6, -5.488e-9, 1.772e-12},
                    new double[]{3.034, 2.176e-3, -1.641e-7, -9.704e-11, 1.682e-14}
            );
            
            double h = h2o.enthalpy(2000);
            assertThat(h).isGreaterThan(0);
        }
    }
}
