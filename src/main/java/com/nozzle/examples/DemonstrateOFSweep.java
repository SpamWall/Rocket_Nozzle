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

import com.nozzle.chemistry.OFSweep;

import java.util.List;

/** Demonstrates O/F sweep and optimum search across propellant families. */
public class DemonstrateOFSweep {

    public static void main(String[] ignoredArgs) {
        System.out.println("\n--- O/F SWEEP AND OPTIMUM SEARCH ---\n");

        final double PC = 7e6;
        final double ME = 3.0;
        final double PA = 101_325.0;

        record PropEntry(OFSweep.Propellant p, double ofLo, double ofHi, String label) {}
        List<PropEntry> propellants = List.of(
                new PropEntry(OFSweep.Propellant.LOX_RP1,     1.5,  5.0, "LOX/RP-1  "),
                new PropEntry(OFSweep.Propellant.LOX_CH4,     2.0,  5.5, "LOX/CH4   "),
                new PropEntry(OFSweep.Propellant.LOX_LH2,     2.0,  8.0, "LOX/LH2   "),
                new PropEntry(OFSweep.Propellant.N2O_ETHANOL, 2.0,  8.0, "N2O/EtOH  "),
                new PropEntry(OFSweep.Propellant.N2O_PROPANE, 4.0, 14.0, "N2O/C3H8  ")
        );

        System.out.println("Propellant       Opt O/F   Tc (K)   γ      MW    c* (m/s)  Isp (s)");
        System.out.println("-".repeat(72));
        for (PropEntry e : propellants) {
            OFSweep sweep = OFSweep.adiabatic(e.p(), PC, ME, PA);
            OFSweep.OFPoint opt = sweep.optimumIsp(e.ofLo(), e.ofHi());
            System.out.printf("%-16s  %5.2f   %6.0f  %.3f  %5.2f  %7.0f   %6.1f%n",
                    e.label(), opt.of(), opt.chamberTemperature(),
                    opt.gamma(), opt.molecularWeight(), opt.cStar(), opt.isp());
        }

        System.out.println("\nLOX/RP-1 adiabatic sweep  Pc=7 MPa  Me=3.0  Pa=101325 Pa");
        System.out.println("  O/F    Tc (K)   γ      MW    c* (m/s)  Isp (s)");
        System.out.println("  " + "-".repeat(54));
        OFSweep rp1Sweep = OFSweep.adiabatic(OFSweep.Propellant.LOX_RP1, PC, ME, PA);
        for (OFSweep.OFPoint pt : rp1Sweep.sweep(1.5, 4.5, 11)) {
            System.out.printf("  %4.2f   %6.0f  %.3f  %5.2f  %7.0f   %6.1f%n",
                    pt.of(), pt.chamberTemperature(), pt.gamma(),
                    pt.molecularWeight(), pt.cStar(), pt.isp());
        }

        System.out.println("\nIsp-optimal vs c*-optimal O/F (LOX/RP-1, adiabatic):");
        OFSweep.OFPoint ispOpt   = rp1Sweep.optimumIsp(1.5, 5.0);
        OFSweep.OFPoint cstarOpt = rp1Sweep.optimumCstar(1.5, 5.0);
        System.out.printf("  Isp-optimal:   O/F=%.3f  Isp=%.1f s   c*=%.0f m/s%n",
                ispOpt.of(), ispOpt.isp(), ispOpt.cStar());
        System.out.printf("  c*-optimal:    O/F=%.3f  Isp=%.1f s   c*=%.0f m/s%n",
                cstarOpt.of(), cstarOpt.isp(), cstarOpt.cStar());

        System.out.println("\nFixed-Tc (3500 K) vs adiabatic at O/F=2.7  (LOX/RP-1):");
        OFSweep fixedSweep = new OFSweep(OFSweep.Propellant.LOX_RP1, 3500.0, PC, ME, PA);
        OFSweep.OFPoint fixed     = fixedSweep.computeAt(2.7);
        OFSweep.OFPoint adiabatic = rp1Sweep.computeAt(2.7);
        System.out.printf("  Fixed-Tc:   Tc=%5.0f K  c*=%6.0f m/s  Isp=%6.1f s%n",
                fixed.chamberTemperature(), fixed.cStar(), fixed.isp());
        System.out.printf("  Adiabatic:  Tc=%5.0f K  c*=%6.0f m/s  Isp=%6.1f s%n",
                adiabatic.chamberTemperature(), adiabatic.cStar(), adiabatic.isp());
    }
}
