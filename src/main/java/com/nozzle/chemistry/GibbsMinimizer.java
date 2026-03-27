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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Gibbs free energy minimization solver for chemical equilibrium.
 * Implements the Newton-Lagrange algorithm of Gordon &amp; McBride (NASA RP-1311):
 * minimise G = ∑ nⱼ·μⱼ subject to elemental mass-balance constraints.
 * <p>
 * After each damped Newton step, element conservation is enforced exactly by
 * projecting mole numbers onto the constraint surface
 * ({@link #enforceElementConstraints}).
 */
public final class GibbsMinimizer {

    private static final Logger LOG = LoggerFactory.getLogger(GibbsMinimizer.class);

    /** Species ordering for the elemental composition matrix. */
    static final String[] SPECIES_ORDER = {
            "H2O", "CO2", "H2", "CO", "OH", "O2", "N2", "H", "O"
    };
    static final int NUM_SPECIES = SPECIES_ORDER.length;

    /** Atoms of each element (H, C, O, N) per molecule of each species. */
    static final double[][] ELEM_COMPOSITION = {
            //  H2O  CO2  H2   CO   OH   O2   N2   H    O
            {   2,   0,   2,   0,   1,   0,   0,   1,   0 },  // H
            {   0,   1,   0,   1,   0,   0,   0,   0,   0 },  // C
            {   1,   2,   0,   1,   1,   2,   0,   0,   1 },  // O
            {   0,   0,   0,   0,   0,   0,   2,   0,   0 },  // N
    };
    static final int NUM_ELEMENTS = ELEM_COMPOSITION.length;

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

    private final NasaSpeciesDatabase database;

    /**
     * Creates a GibbsMinimizer backed by the supplied species database.
     *
     * @param database NASA-7 polynomial database for all supported species
     */
    public GibbsMinimizer(NasaSpeciesDatabase database) {
        this.database = database;
    }

    /**
     * Computes the equilibrium composition at the given temperature and pressure
     * using Gibbs free energy minimization with Lagrangian elemental constraints.
     *
     * @param massFractions Initial species mass fractions
     * @param temperature   Temperature in K
     * @param pressure      Pressure in Pa
     * @return Equilibrium species mass fractions, normalized to sum to 1.0
     */
    public Map<String, Double> minimize(Map<String, Double> massFractions,
                                        double temperature, double pressure) {
        // Phase A: Convert mass fractions to mole numbers
        double[] n = new double[NUM_SPECIES];
        for (int j = 0; j < NUM_SPECIES; j++) {
            Double massFraction = massFractions.get(SPECIES_ORDER[j]);
            SpeciesData species = database.get(SPECIES_ORDER[j]);
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
                SpeciesData species = database.get(SPECIES_ORDER[j]);
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
        Map<String, Double> result = new HashMap<>();
        double totalMass = 0;
        for (int j = 0; j < NUM_SPECIES; j++) {
            if (!speciesActive[j]) continue;
            SpeciesData species = database.get(SPECIES_ORDER[j]);
            if (species != null) {
                totalMass += n[j] * species.molecularWeight();
            }
        }

        for (int j = 0; j < NUM_SPECIES; j++) {
            if (!speciesActive[j]) continue;
            SpeciesData species = database.get(SPECIES_ORDER[j]);
            if (species != null) {
                double mf = n[j] * species.molecularWeight() / totalMass;
                if (mf > TRACE_THRESHOLD) {
                    result.put(SPECIES_ORDER[j], mf);
                }
            }
        }

        // Normalize output so fractions sum exactly to 1.0
        double sum = result.values().stream().mapToDouble(Double::doubleValue).sum();
        if (sum > 0) {
            result.replaceAll((_, v) -> v / sum);
        }

        return result;
    }

    /**
     * Enforces element conservation constraints exactly by projecting mole
     * numbers onto the constraint surface.  Uses multiplicative corrections
     * {@code n_j *= exp(∑_k a_kj · β_k)} where β solves the linear system
     * {@code C · β = r}, with
     * {@code C_ik = ∑_j a_ij · a_kj · n_j} and
     * {@code r_i = b_i − ∑_j a_ij · n_j}.
     * Up to five projection passes are performed until the max relative residual
     * is below 10⁻¹².
     *
     * @param n              Mole-number array for all species (modified in place)
     * @param b              Target elemental totals (moles of each element)
     * @param speciesActive  Boolean mask; {@code true} for species participating in iteration
     * @param activeElements Indices of the elements that are present in the mixture
     * @param numActive      Length of the {@code activeElements} prefix to use
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
     * @return Solution vector, or {@code null} if the matrix is singular
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
}
