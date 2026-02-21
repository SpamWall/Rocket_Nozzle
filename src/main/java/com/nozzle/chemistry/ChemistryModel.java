package com.nozzle.chemistry;

import com.nozzle.core.GasProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Models chemical reactions and equilibrium/frozen flow chemistry in rocket nozzles.
 * Supports both frozen flow (constant composition) and equilibrium flow (shifting composition).
 */
public class ChemistryModel {

    private static final Logger LOG = LoggerFactory.getLogger(ChemistryModel.class);

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

    /** Species ordering for the elemental composition matrix. */
    private static final String[] SPECIES_ORDER = {
            "H2O", "CO2", "H2", "CO", "OH", "O2", "N2", "H", "O"
    };
    private static final int NUM_SPECIES = SPECIES_ORDER.length;

    /** Atoms of each element (H, C, O, N) per molecule of each species. */
    private static final double[][] ELEM_COMPOSITION = {
            //  H2O  CO2  H2   CO   OH   O2   N2   H    O
            {   2,   0,   2,   0,   1,   0,   0,   1,   0 },  // H
            {   0,   1,   0,   1,   0,   0,   0,   0,   0 },  // C
            {   1,   2,   0,   1,   1,   2,   0,   0,   1 },  // O
            {   0,   0,   0,   0,   0,   0,   2,   0,   0 },  // N
    };
    private static final int NUM_ELEMENTS = ELEM_COMPOSITION.length;

    /** Standard reference pressure (1 atm) in Pa. */
    private static final double P_REF = 101325.0;

    /** Minimum mole number to avoid log(0). */
    private static final double MIN_MOLES = 1e-20;

    /** Convergence tolerance on max |correction| per iteration. */
    private static final double CONVERGENCE_TOL = 1e-8;

    /** Maximum Newton-Raphson iterations. */
    private static final int MAX_ITER = 200;

    /** Species with mass fraction below this threshold are excluded from output. */
    private static final double TRACE_THRESHOLD = 1e-10;

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
     * Initializes the species thermodynamic database with NASA-7 polynomial coefficients.
     * Coefficients sourced from NASA TP-2002-211556 (McBride, Zehe, Gordon).
     * Each array has 7 elements: a0-a4 for Cp/R, a5 for enthalpy integration, a6 for entropy integration.
     */
    private void initializeSpeciesDatabase() {
        // H2O: Water vapor
        speciesDatabase.put("H2O", new SpeciesData("H2O", 18.015,
                new double[]{4.198640560e+00, -2.036434100e-03, 6.520402110e-06, -5.487970620e-09, 1.771978170e-12, -3.029372670e+04, -8.490322080e-01},
                new double[]{3.033992490e+00, 2.176918040e-03, -1.640725180e-07, -9.704198700e-11, 1.682009920e-14, -3.000429710e+04, 4.966770100e+00}));

        // CO2: Carbon dioxide
        speciesDatabase.put("CO2", new SpeciesData("CO2", 44.01,
                new double[]{2.356773520e+00, 8.984596770e-03, -7.123562690e-06, 2.459190220e-09, -1.436995480e-13, -4.837196970e+04, 9.901052220e+00},
                new double[]{4.636594930e+00, 2.741131400e-03, -9.958285310e-07, 1.603730110e-10, -9.161600030e-15, -4.912488530e+04, -1.935348550e+00}));

        // H2: Molecular hydrogen
        speciesDatabase.put("H2", new SpeciesData("H2", 2.016,
                new double[]{2.344331120e+00, 7.980520750e-03, -1.947815100e-05, 2.015720940e-08, -7.376117610e-12, -9.179351730e+02, 6.830102380e-01},
                new double[]{2.932865750e+00, 8.266079670e-04, -1.464023640e-07, 1.541003590e-11, -6.888044320e-16, -8.130655810e+02, -1.024328650e+00}));

        // CO: Carbon monoxide
        speciesDatabase.put("CO", new SpeciesData("CO", 28.01,
                new double[]{3.579533470e+00, -6.103536800e-04, 1.016814330e-06, 9.070058840e-10, -9.044244990e-13, -1.434408600e+04, 3.508409280e+00},
                new double[]{3.048485830e+00, 1.351728180e-03, -4.857940750e-07, 7.885364860e-11, -4.698074890e-15, -1.426611710e+04, 6.017097260e+00}));

        // OH: Hydroxyl radical
        speciesDatabase.put("OH", new SpeciesData("OH", 17.007,
                new double[]{3.992015430e+00, -2.401317520e-03, 4.617938410e-06, -3.881133330e-09, 1.364114700e-12, 3.615080560e+03, -1.039254580e-01},
                new double[]{2.838646070e+00, 1.107255860e-03, -2.939149780e-07, 4.205242470e-11, -2.421690790e-15, 3.943958520e+03, 5.844526620e+00}));

        // O2: Molecular oxygen
        speciesDatabase.put("O2", new SpeciesData("O2", 32.0,
                new double[]{3.782456360e+00, -2.996734160e-03, 9.847302010e-06, -9.681295090e-09, 3.243728370e-12, -1.063943560e+03, 3.657675730e+00},
                new double[]{3.660960650e+00, 6.563658110e-04, -1.411496270e-07, 2.057979350e-11, -1.299134360e-15, -1.215977180e+03, 3.415362790e+00}));

        // N2: Molecular nitrogen
        speciesDatabase.put("N2", new SpeciesData("N2", 28.014,
                new double[]{3.298677000e+00, 1.408240400e-03, -3.963222000e-06, 5.641515000e-09, -2.444854000e-12, -1.020899900e+03, 3.950372000e+00},
                new double[]{2.926640000e+00, 1.487976800e-03, -5.684760000e-07, 1.009703800e-10, -6.753351000e-15, -9.227977000e+02, 5.980528000e+00}));

        // H: Atomic hydrogen
        speciesDatabase.put("H", new SpeciesData("H", 1.008,
                new double[]{2.500000000e+00, 0.000000000e+00, 0.000000000e+00, 0.000000000e+00, 0.000000000e+00, 2.547365990e+04, -4.466828530e-01},
                new double[]{2.500000000e+00, 0.000000000e+00, 0.000000000e+00, 0.000000000e+00, 0.000000000e+00, 2.547365990e+04, -4.466828530e-01}));

        // O: Atomic oxygen
        speciesDatabase.put("O", new SpeciesData("O", 16.0,
                new double[]{3.168267100e+00, -3.279318840e-03, 6.643063960e-06, -6.128066240e-09, 2.112659710e-12, 2.912225920e+04, 2.051933460e+00},
                new double[]{2.543636970e+00, -2.731624860e-05, -4.190295200e-09, 4.954818230e-12, -4.795536940e-16, 2.922601200e+04, 4.922294570e+00}));
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
            speciesMassFractions.replaceAll((_, v) -> v / sum);
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
     * Calculates equilibrium composition at given temperature and pressure
     * using Gibbs free energy minimization with Lagrangian elemental constraints.
     * <p>
     * Uses a Newton-Raphson iteration on the Lagrangian system (Gordon &amp; McBride,
     * NASA RP-1311): minimize G = sum(n_j * mu_j) subject to elemental mass balance.
     * After each damped Newton step, element conservation is enforced exactly by
     * projecting mole numbers onto the constraint surface.
     *
     * @param temperature Temperature in K
     * @param pressure    Pressure in Pa
     */
    public void calculateEquilibrium(double temperature, double pressure) {
        if (modelType != ModelType.EQUILIBRIUM) {
            return;
        }

        // Phase A: Convert mass fractions to mole numbers
        double[] n = new double[NUM_SPECIES];
        for (int j = 0; j < NUM_SPECIES; j++) {
            Double massFraction = speciesMassFractions.get(SPECIES_ORDER[j]);
            SpeciesData species = speciesDatabase.get(SPECIES_ORDER[j]);
            if (massFraction != null && species != null && massFraction > 0) {
                n[j] = massFraction / species.molecularWeight();
            }
        }

        // Determine which elements are present from the original composition
        boolean[] elementPresent = new boolean[NUM_ELEMENTS];
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            for (int j = 0; j < NUM_SPECIES; j++) {
                if (ELEM_COMPOSITION[i][j] > 0 && n[j] > 0) {
                    elementPresent[i] = true;
                    break;
                }
            }
        }

        // Determine which species are active (all constituent elements present).
        // Species with absent elements (e.g. CO2 in a C-free system) are locked
        // at MIN_MOLES and excluded from the iteration to prevent them from
        // growing unconstrained and corrupting the element balance.
        boolean[] speciesActive = new boolean[NUM_SPECIES];
        for (int j = 0; j < NUM_SPECIES; j++) {
            speciesActive[j] = true;
            for (int i = 0; i < NUM_ELEMENTS; i++) {
                if (ELEM_COMPOSITION[i][j] > 0 && !elementPresent[i]) {
                    speciesActive[j] = false;
                    break;
                }
            }
        }

        // Compute elemental totals from original composition (before trace init)
        double[] b = new double[NUM_ELEMENTS];
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            for (int j = 0; j < NUM_SPECIES; j++) {
                b[i] += ELEM_COMPOSITION[i][j] * n[j];
            }
        }

        // Initialize absent active species at trace level for solver stability
        double nTotal0 = 0;
        for (int j = 0; j < NUM_SPECIES; j++) {
            if (n[j] > 0) nTotal0 += n[j];
        }
        double traceInit = Math.max(MIN_MOLES, nTotal0 * 1e-6);
        for (int j = 0; j < NUM_SPECIES; j++) {
            if (n[j] <= 0) {
                n[j] = speciesActive[j] ? traceInit : MIN_MOLES;
            }
        }

        // Identify active elements
        int[] activeElements = new int[NUM_ELEMENTS];
        int numActive = 0;
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            if (elementPresent[i]) {
                activeElements[numActive++] = i;
            }
        }

        // Precompute standard Gibbs for each active species at this temperature
        double[] g0overRT = new double[NUM_SPECIES];
        for (int j = 0; j < NUM_SPECIES; j++) {
            if (speciesActive[j]) {
                SpeciesData species = speciesDatabase.get(SPECIES_ORDER[j]);
                if (species != null) {
                    g0overRT[j] = species.gibbsOverRT(temperature);
                }
            }
        }

        double lnPoverPref = Math.log(pressure / P_REF);

        // Enforce element constraints on the initial state (trace init may have
        // slightly perturbed the elemental totals)
        enforceElementConstraints(n, b, speciesActive, activeElements, numActive);

        // Phase B: Newton-Raphson iteration (Gordon & McBride, NASA RP-1311)
        int sysSize = numActive + 1;

        for (int iter = 0; iter < MAX_ITER; iter++) {
            double nTotal = 0;
            for (int j = 0; j < NUM_SPECIES; j++) {
                if (speciesActive[j]) nTotal += n[j];
            }
            double lnNTotal = Math.log(nTotal);
            double majorThreshold = nTotal * 1e-8;

            // Chemical potentials for active species only
            double[] mu = new double[NUM_SPECIES];
            for (int j = 0; j < NUM_SPECIES; j++) {
                if (speciesActive[j]) {
                    mu[j] = g0overRT[j] + Math.log(Math.max(n[j], MIN_MOLES))
                            - lnNTotal + lnPoverPref;
                }
            }

            // Assemble linear system: A * x = rhs
            // x = [pi_0, ..., pi_{numActive-1}, delta_ln_n_total]
            double[][] A = new double[sysSize][sysSize];
            double[] rhs = new double[sysSize];

            for (int ii = 0; ii < numActive; ii++) {
                int i = activeElements[ii];
                double Si = 0;
                double sumANmu = 0;
                for (int j = 0; j < NUM_SPECIES; j++) {
                    if (!speciesActive[j]) continue;
                    double aij_nj = ELEM_COMPOSITION[i][j] * n[j];
                    Si += aij_nj;
                    sumANmu += aij_nj * mu[j];
                    for (int kk = 0; kk < numActive; kk++) {
                        A[ii][kk] += aij_nj * ELEM_COMPOSITION[activeElements[kk]][j];
                    }
                }
                A[ii][numActive] = Si;
                A[numActive][ii] = Si;
                rhs[ii] = b[i] - Si + sumANmu;
            }

            A[numActive][numActive] = nTotal;
            double sumNmu = 0;
            for (int j = 0; j < NUM_SPECIES; j++) {
                if (speciesActive[j]) {
                    sumNmu += n[j] * mu[j];
                }
            }
            rhs[numActive] = sumNmu;

            // Solve linear system
            double[] x = gaussianElimination(A, rhs);
            if (x == null) {
                LOG.warn("Gibbs equilibrium: singular matrix at iteration {}", iter);
                break;
            }

            double[] pi = new double[NUM_ELEMENTS];
            for (int ii = 0; ii < numActive; ii++) {
                pi[activeElements[ii]] = x[ii];
            }
            double deltaLnNTotal = x[numActive];

            // Species corrections (only active species)
            double[] deltaLnN = new double[NUM_SPECIES];
            double maxCorrMajor = 0;
            for (int j = 0; j < NUM_SPECIES; j++) {
                if (!speciesActive[j]) continue;
                double correction = -mu[j] + deltaLnNTotal;
                for (int i = 0; i < NUM_ELEMENTS; i++) {
                    correction += ELEM_COMPOSITION[i][j] * pi[i];
                }
                deltaLnN[j] = correction;
                if (n[j] > majorThreshold) {
                    maxCorrMajor = Math.max(maxCorrMajor, Math.abs(correction));
                }
            }

            // Damping based on major species only
            double dampMajor = 1.0;
            if (maxCorrMajor > 2.0) {
                dampMajor = 2.0 / maxCorrMajor;
            }

            // Update mole numbers (only active species)
            for (int j = 0; j < NUM_SPECIES; j++) {
                if (!speciesActive[j]) continue;
                double corr;
                if (n[j] > majorThreshold) {
                    corr = dampMajor * deltaLnN[j];
                } else {
                    corr = Math.max(-5.0, Math.min(5.0, deltaLnN[j]));
                }
                n[j] *= Math.exp(corr);
                n[j] = Math.max(n[j], MIN_MOLES);
            }

            // Enforce element conservation after damped step
            enforceElementConstraints(n, b, speciesActive, activeElements, numActive);

            // Convergence based on major species corrections
            if (maxCorrMajor * dampMajor < CONVERGENCE_TOL) {
                LOG.debug("Gibbs equilibrium converged in {} iterations", iter + 1);
                break;
            }

            if (iter == MAX_ITER - 1) {
                LOG.warn("Gibbs equilibrium did not converge after {} iterations, max correction = {}",
                        MAX_ITER, maxCorrMajor * dampMajor);
            }
        }

        // Final enforcement of element constraints
        enforceElementConstraints(n, b, speciesActive, activeElements, numActive);

        // Phase C: Convert mole numbers back to mass fractions
        speciesMassFractions.clear();
        double totalMass = 0;
        for (int j = 0; j < NUM_SPECIES; j++) {
            if (!speciesActive[j]) continue;
            SpeciesData species = speciesDatabase.get(SPECIES_ORDER[j]);
            if (species != null) {
                totalMass += n[j] * species.molecularWeight();
            }
        }

        for (int j = 0; j < NUM_SPECIES; j++) {
            if (!speciesActive[j]) continue;
            SpeciesData species = speciesDatabase.get(SPECIES_ORDER[j]);
            if (species != null) {
                double massFraction = n[j] * species.molecularWeight() / totalMass;
                if (massFraction > TRACE_THRESHOLD) {
                    speciesMassFractions.put(SPECIES_ORDER[j], massFraction);
                }
            }
        }

        normalizeComposition();
    }

    /**
     * Enforces element conservation constraints exactly by projecting mole
     * numbers onto the constraint surface. Uses multiplicative corrections
     * n_j *= exp(sum_k a_kj * beta_k) where beta solves C*beta = r,
     * with C_ik = sum_j a_ij * a_kj * n_j and r_i = b_i - sum_j a_ij * n_j.
     */
    private void enforceElementConstraints(double[] n, double[] b,
            boolean[] speciesActive, int[] activeElements, int numActive) {
        for (int pass = 0; pass < 5; pass++) {
            double maxRelResidual = 0;
            double[][] C = new double[numActive][numActive];
            double[] r = new double[numActive];

            for (int ii = 0; ii < numActive; ii++) {
                int i = activeElements[ii];
                double Si = 0;
                for (int j = 0; j < NUM_SPECIES; j++) {
                    if (!speciesActive[j]) continue;
                    double aij_nj = ELEM_COMPOSITION[i][j] * n[j];
                    Si += aij_nj;
                    for (int kk = 0; kk < numActive; kk++) {
                        C[ii][kk] += aij_nj * ELEM_COMPOSITION[activeElements[kk]][j];
                    }
                }
                r[ii] = b[i] - Si;
                if (b[i] > 0) {
                    maxRelResidual = Math.max(maxRelResidual, Math.abs(r[ii]) / b[i]);
                }
            }

            if (maxRelResidual < 1e-12) return;

            double[] beta = gaussianElimination(C, r);
            if (beta == null) return;

            for (int j = 0; j < NUM_SPECIES; j++) {
                if (!speciesActive[j]) continue;
                double correction = 0;
                for (int kk = 0; kk < numActive; kk++) {
                    correction += ELEM_COMPOSITION[activeElements[kk]][j] * beta[kk];
                }
                n[j] *= Math.exp(correction);
                n[j] = Math.max(n[j], MIN_MOLES);
            }
        }
    }

    /**
     * Solves Ax = b using Gaussian elimination with partial pivoting.
     *
     * @param A Coefficient matrix (modified in place)
     * @param b Right-hand side vector (modified in place)
     * @return Solution vector, or null if the matrix is singular
     */
    private static double[] gaussianElimination(double[][] A, double[] b) {
        int n = b.length;
        double[] x = new double[n];

        // Forward elimination with partial pivoting
        for (int col = 0; col < n; col++) {
            // Find pivot
            int maxRow = col;
            double maxVal = Math.abs(A[col][col]);
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(A[row][col]) > maxVal) {
                    maxVal = Math.abs(A[row][col]);
                    maxRow = row;
                }
            }

            if (maxVal < 1e-15) {
                return null; // Singular matrix
            }

            // Swap rows
            if (maxRow != col) {
                double[] tempRow = A[col];
                A[col] = A[maxRow];
                A[maxRow] = tempRow;
                double tempB = b[col];
                b[col] = b[maxRow];
                b[maxRow] = tempB;
            }

            // Eliminate below
            for (int row = col + 1; row < n; row++) {
                double factor = A[row][col] / A[col][col];
                for (int k = col; k < n; k++) {
                    A[row][k] -= factor * A[col][k];
                }
                b[row] -= factor * b[col];
            }
        }

        // Back substitution
        for (int row = n - 1; row >= 0; row--) {
            x[row] = b[row];
            for (int col = row + 1; col < n; col++) {
                x[row] -= A[row][col] * x[col];
            }
            x[row] /= A[row][row];
        }

        return x;
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
         * @param temp_deg_k Temperature in K
         * @return Cp in J/(kg·K)
         */
        public double cp(double temp_deg_k) {
            double[] coeffs = temp_deg_k < 1000 ? lowTempCoeffs : highTempCoeffs;
           double cpMolar = R_UNIVERSAL * (coeffs[0] + coeffs[1]* temp_deg_k + coeffs[2]* temp_deg_k * temp_deg_k
                 + coeffs[3]* temp_deg_k * temp_deg_k * temp_deg_k
                 + coeffs[4]* temp_deg_k * temp_deg_k * temp_deg_k * temp_deg_k);
            return cpMolar / molecularWeight;
        }
        
        /**
         * Calculates enthalpy using NASA-7 polynomial with integration constant a5.
         *
         * @param temp_deg_k Temperature in K
         * @return Enthalpy in J/kg
         */
        public double enthalpy(double temp_deg_k) {
            double[] a = temp_deg_k < 1000 ? lowTempCoeffs : highTempCoeffs;
            double hOverRT = a[0] + a[1]* temp_deg_k /2 + a[2]* temp_deg_k * temp_deg_k /3
                  + a[3]* temp_deg_k * temp_deg_k * temp_deg_k /4
                  + a[4]* temp_deg_k * temp_deg_k * temp_deg_k * temp_deg_k /5 + a[5]/ temp_deg_k;
            return R_UNIVERSAL * temp_deg_k * hOverRT / molecularWeight;
        }

        /**
         * Calculates entropy using NASA-7 polynomial with integration constant a6.
         *
         * @param temp_deg_k Temperature in K
         * @return Entropy in J/(kg·K) at standard pressure
         */
        public double entropy(double temp_deg_k) {
            double[] a = temp_deg_k < 1000 ? lowTempCoeffs : highTempCoeffs;
            double sOverR = a[0]*Math.log(temp_deg_k) + a[1]* temp_deg_k + a[2]* temp_deg_k * temp_deg_k /2
                  + a[3]* temp_deg_k * temp_deg_k * temp_deg_k /3
                  + a[4]* temp_deg_k * temp_deg_k * temp_deg_k * temp_deg_k /4 + a[6];
            return R_UNIVERSAL * sOverR / molecularWeight;
        }

        /**
         * Calculates dimensionless standard Gibbs free energy g0/(RT) = H/(RT) - S/R.
         * This is per mole, at the standard reference pressure (1 atm).
         *
         * @param temp_deg_k Temperature in K
         * @return Dimensionless g0/(RT)
         */
        public double gibbsOverRT(double temp_deg_k) {
            double[] a = temp_deg_k < 1000 ? lowTempCoeffs : highTempCoeffs;
            double hOverRT = a[0] + a[1]* temp_deg_k /2 + a[2]* temp_deg_k * temp_deg_k /3
                  + a[3] * temp_deg_k * temp_deg_k * temp_deg_k /4
                  + a[4] * temp_deg_k * temp_deg_k * temp_deg_k * temp_deg_k /5 + a[5]/ temp_deg_k;
            double sOverR = a[0]*Math.log(temp_deg_k) + a[1] * temp_deg_k
                  + a[2] * temp_deg_k * temp_deg_k /2 + a[3] * temp_deg_k * temp_deg_k * temp_deg_k /3
                  + a[4] * temp_deg_k * temp_deg_k * temp_deg_k * temp_deg_k /4 + a[6];
            return hOverRT - sOverR;
        }
    }
}
