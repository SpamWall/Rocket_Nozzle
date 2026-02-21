package com.nozzle.chemistry;

import com.nozzle.core.GasProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.within;

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

            assertThat(after).isNotEmpty();
            assertThat(after).isNotEqualTo(before);

            double sum = after.values().stream().mapToDouble(Double::doubleValue).sum();
            assertThat(sum).isCloseTo(1.0, within(1e-6));
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

        @Test
        @DisplayName("Equilibrium should conserve elemental composition")
        void equilibriumShouldConserveElements() {
            equilibriumModel.setLoxLh2Composition(6.0);

            // Compute elemental totals before
            double[] elementsBefore = computeElementalMoles(equilibriumModel.getSpeciesMassFractions());

            equilibriumModel.calculateEquilibrium(3200, 7e6);

            // Compute elemental totals after
            double[] elementsAfter = computeElementalMoles(equilibriumModel.getSpeciesMassFractions());

            // H, C, O, N should be conserved
            for (int i = 0; i < elementsBefore.length; i++) {
                if (elementsBefore[i] > 1e-15) {
                    assertThat(elementsAfter[i]).isCloseTo(elementsBefore[i],
                            within(elementsBefore[i] * 1e-4));
                }
            }
        }

        @Test
        @DisplayName("Higher pressure should reduce dissociation (Le Chatelier)")
        void higherPressureShouldReduceDissociation() {
            // Low pressure
            ChemistryModel lowP = ChemistryModel.equilibrium(GasProperties.LOX_LH2_PRODUCTS);
            lowP.setLoxLh2Composition(6.0);
            lowP.calculateEquilibrium(3200, 1e5);
            double h2oLowP = lowP.getSpeciesMassFractions().getOrDefault("H2O", 0.0);

            // High pressure
            ChemistryModel highP = ChemistryModel.equilibrium(GasProperties.LOX_LH2_PRODUCTS);
            highP.setLoxLh2Composition(6.0);
            highP.calculateEquilibrium(3200, 1e7);
            double h2oHighP = highP.getSpeciesMassFractions().getOrDefault("H2O", 0.0);

            // Higher pressure should favor H2O (fewer total moles) over dissociation products
            assertThat(h2oHighP).isGreaterThan(h2oLowP);
        }

        @Test
        @DisplayName("Higher temperature should increase dissociation")
        void higherTemperatureShouldIncreaseDissociation() {
            // Low temperature
            ChemistryModel lowT = ChemistryModel.equilibrium(GasProperties.LOX_LH2_PRODUCTS);
            lowT.setLoxLh2Composition(6.0);
            lowT.calculateEquilibrium(1500, 7e6);
            double h2oLowT = lowT.getSpeciesMassFractions().getOrDefault("H2O", 0.0);

            // High temperature
            ChemistryModel highT = ChemistryModel.equilibrium(GasProperties.LOX_LH2_PRODUCTS);
            highT.setLoxLh2Composition(6.0);
            highT.calculateEquilibrium(4000, 7e6);
            double h2oHighT = highT.getSpeciesMassFractions().getOrDefault("H2O", 0.0);

            // Higher temperature should dissociate more H2O
            assertThat(h2oLowT).isGreaterThan(h2oHighT);
        }

        @Test
        @DisplayName("Equilibrium should be idempotent at same conditions")
        void equilibriumShouldBeIdempotent() {
            equilibriumModel.setLoxLh2Composition(6.0);
            equilibriumModel.calculateEquilibrium(3200, 7e6);
            Map<String, Double> first = Map.copyOf(equilibriumModel.getSpeciesMassFractions());

            equilibriumModel.calculateEquilibrium(3200, 7e6);
            Map<String, Double> second = equilibriumModel.getSpeciesMassFractions();

            for (String species : first.keySet()) {
                assertThat(second.getOrDefault(species, 0.0))
                        .isCloseTo(first.get(species), within(1e-6));
            }
        }

        @Test
        @DisplayName("Mass fractions should sum to one after equilibrium")
        void massFractionsShouldSumToOne() {
            equilibriumModel.setLoxLh2Composition(6.0);
            equilibriumModel.calculateEquilibrium(3200, 7e6);

            double sum = equilibriumModel.getSpeciesMassFractions().values()
                    .stream().mapToDouble(Double::doubleValue).sum();
            assertThat(sum).isCloseTo(1.0, within(1e-6));
        }

        /**
         * Computes moles of each element (H, C, O, N) from mass fractions.
         */
        private double[] computeElementalMoles(Map<String, Double> massFractions) {
            String[] speciesOrder = {"H2O", "CO2", "H2", "CO", "OH", "O2", "N2", "H", "O"};
            double[] mw = {18.015, 44.01, 2.016, 28.01, 17.007, 32.0, 28.014, 1.008, 16.0};
            double[][] elemComp = {
                    {2, 0, 2, 0, 1, 0, 0, 1, 0},  // H
                    {0, 1, 0, 1, 0, 0, 0, 0, 0},  // C
                    {1, 2, 0, 1, 1, 2, 0, 0, 1},  // O
                    {0, 0, 0, 0, 0, 0, 2, 0, 0},  // N
            };

            double[] elements = new double[4];
            for (int j = 0; j < speciesOrder.length; j++) {
                double y = massFractions.getOrDefault(speciesOrder[j], 0.0);
                double moles = y / mw[j];
                for (int i = 0; i < 4; i++) {
                    elements[i] += elemComp[i][j] * moles;
                }
            }
            return elements;
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
                    new double[]{4.198640560e+00, -2.036434100e-03, 6.520402110e-06, -5.487970620e-09, 1.771978170e-12, -3.029372670e+04, -8.490322080e-01},
                    new double[]{3.033992490e+00, 2.176918040e-03, -1.640725180e-07, -9.704198700e-11, 1.682009920e-14, -3.000429710e+04, 4.966770100e+00}
            );

            double cp = h2o.cp(2000);
            assertThat(cp).isGreaterThan(1000);
        }

        @Test
        @DisplayName("Species data should calculate enthalpy")
        void speciesDataShouldCalculateEnthalpy() {
            ChemistryModel.SpeciesData h2o = new ChemistryModel.SpeciesData(
                    "H2O", 18.015,
                    new double[]{4.198640560e+00, -2.036434100e-03, 6.520402110e-06, -5.487970620e-09, 1.771978170e-12, -3.029372670e+04, -8.490322080e-01},
                    new double[]{3.033992490e+00, 2.176918040e-03, -1.640725180e-07, -9.704198700e-11, 1.682009920e-14, -3.000429710e+04, 4.966770100e+00}
            );

            // H2O has negative heat of formation, so enthalpy includes that
            double h = h2o.enthalpy(2000);
            assertThat(h).isNotNaN();
        }

        @Test
        @DisplayName("Species data should calculate entropy")
        void speciesDataShouldCalculateEntropy() {
            ChemistryModel.SpeciesData h2o = new ChemistryModel.SpeciesData(
                    "H2O", 18.015,
                    new double[]{4.198640560e+00, -2.036434100e-03, 6.520402110e-06, -5.487970620e-09, 1.771978170e-12, -3.029372670e+04, -8.490322080e-01},
                    new double[]{3.033992490e+00, 2.176918040e-03, -1.640725180e-07, -9.704198700e-11, 1.682009920e-14, -3.000429710e+04, 4.966770100e+00}
            );

            double s = h2o.entropy(2000);
            assertThat(s).isNotNaN();
            assertThat(s).isFinite();
            // H2O standard molar entropy at 2000K is ~264 J/(mol·K), so per-mass ~14,650 J/(kg·K)
            assertThat(s).isBetween(12_000.0, 17_000.0);

            // Entropy should increase with temperature
            double sLow = h2o.entropy(500);
            assertThat(s).isGreaterThan(sLow);
        }

        @Test
        @DisplayName("Species data should calculate gibbsOverRT")
        void speciesDataShouldCalculateGibbsOverRT() {
            ChemistryModel.SpeciesData h2o = new ChemistryModel.SpeciesData(
                    "H2O", 18.015,
                    new double[]{4.198640560e+00, -2.036434100e-03, 6.520402110e-06, -5.487970620e-09, 1.771978170e-12, -3.029372670e+04, -8.490322080e-01},
                    new double[]{3.033992490e+00, 2.176918040e-03, -1.640725180e-07, -9.704198700e-11, 1.682009920e-14, -3.000429710e+04, 4.966770100e+00}
            );

            double g = h2o.gibbsOverRT(3000);
            assertThat(g).isNotNaN();
            assertThat(g).isFinite();
            // g0/RT should be negative for stable species at high temperature
            assertThat(g).isNegative();
        }
    }
}
