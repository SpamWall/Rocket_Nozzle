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

package com.nozzle.examples;

import com.nozzle.chemistry.ChemistryModel;
import com.nozzle.core.GasProperties;

import java.util.List;

/** Demonstrates frozen/equilibrium chemistry modeling and Isp comparison. */
public class DemonstrateChemistryModeling {

    public static void main(String[] ignoredArgs) {
        System.out.println("\n--- CHEMISTRY MODELING ---\n");

        ChemistryModel frozenModel = ChemistryModel.frozen(GasProperties.LOX_RP1_PRODUCTS);
        frozenModel.setLoxRp1Composition(2.5);

        System.out.println("Frozen Flow Model (LOX/RP-1, O/F=2.5):");
        System.out.printf("  Molecular Weight: %.2f kg/kmol%n", frozenModel.calculateMolecularWeight());
        System.out.printf("  Gamma at 3500K:   %.3f%n", frozenModel.calculateGamma(3500));
        System.out.printf("  Gamma at 2000K:   %.3f%n", frozenModel.calculateGamma(2000));
        System.out.printf("  Cp at 3500K:      %.0f J/(kg·K)%n", frozenModel.calculateCp(3500));

        ChemistryModel eqModel = ChemistryModel.equilibrium(GasProperties.LOX_LH2_PRODUCTS);
        eqModel.setLoxLh2Composition(6.0);
        eqModel.calculateEquilibrium(3200, 7e6);

        System.out.println("\nEquilibrium Flow Model (LOX/LH2, O/F=6.0, T=3200K, P=7MPa):");
        System.out.printf("  Molecular Weight:        %.3f kg/kmol%n", eqModel.calculateMolecularWeight());
        System.out.printf("  Frozen gamma:            %.4f%n", eqModel.calculateGamma(3200));
        System.out.printf("  Equilibrium gamma (CEA): %.4f%n", eqModel.calculateEquilibriumGamma(3200, 7e6));
        System.out.println("  Species mass fractions:");
        eqModel.getSpeciesMassFractions().forEach((species, fraction) ->
                System.out.printf("    %s: %.1f%%%n", species, fraction * 100));

        System.out.println("\nFrozen vs. Equilibrium Isp  (Tc=3500 K  Pc=7 MPa  Me=3.0  Pa=101 325 Pa)");
        System.out.println("  Propellant          Frozen (s)  Equil. (s)  ΔIsp (s)  Δ%");
        System.out.println("  " + "-".repeat(58));
        record IspEntry(String label, ChemistryModel model) {}
        ChemistryModel lh2Isp = ChemistryModel.equilibrium(GasProperties.LOX_LH2_PRODUCTS);
        lh2Isp.setLoxLh2Composition(6.0);
        lh2Isp.calculateEquilibrium(3500, 7e6);
        ChemistryModel rp1Isp = ChemistryModel.equilibrium(GasProperties.LOX_RP1_PRODUCTS);
        rp1Isp.setLoxRp1Composition(2.77);
        rp1Isp.calculateEquilibrium(3500, 7e6);
        ChemistryModel ch4Isp = ChemistryModel.equilibrium(GasProperties.LOX_CH4_PRODUCTS);
        ch4Isp.setLoxCh4Composition(3.5);
        ch4Isp.calculateEquilibrium(3500, 7e6);
        ChemistryModel n2oIsp = ChemistryModel.equilibrium(GasProperties.N2O_ETHANOL_PRODUCTS);
        n2oIsp.setN2oEthanolComposition(5.0);
        n2oIsp.calculateEquilibrium(3500, 7e6);
        for (IspEntry e : List.of(
                new IspEntry("LOX/LH2   (O/F=6.0) ", lh2Isp),
                new IspEntry("LOX/RP-1  (O/F=2.77)", rp1Isp),
                new IspEntry("LOX/CH4   (O/F=3.5) ", ch4Isp),
                new IspEntry("N2O/EtOH  (O/F=5.0) ", n2oIsp))) {
            ChemistryModel.IspComparison cmp = e.model().compareIsp(3500, 7e6, 3.0, 101_325.0);
            System.out.printf("  %s  %7.1f     %7.1f     %5.1f   %4.2f%%%n",
                    e.label(), cmp.frozenIsp(), cmp.equilibriumIsp(),
                    cmp.delta(), cmp.deltaPercent());
        }
    }
}
