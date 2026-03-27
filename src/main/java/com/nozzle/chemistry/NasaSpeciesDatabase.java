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

import java.util.Map;

/**
 * NASA-7 polynomial thermodynamic database for nine combustion-product species.
 * Coefficients sourced from NASA TP-2002-211556 (McBride, Zehe, Gordon).
 * <p>
 * Supported species: H2O, CO2, H2, CO, OH, O2, N2, H, O.
 */
public final class NasaSpeciesDatabase {

    private final Map<String, SpeciesData> database;

    /**
     * Constructs the database and populates it with NASA-7 polynomial data
     * for all nine supported species.
     */
    public NasaSpeciesDatabase() {

       // H2O: Water vapor

       this.database = Map.of("H2O", new SpeciesData("H2O", 18.015,
                   new double[]{4.198640560e+00, -2.036434100e-03, 6.520402110e-06, -5.487970620e-09, 1.771978170e-12, -3.029372670e+04, -8.490322080e-01},
                   new double[]{3.033992490e+00, 2.176918040e-03, -1.640725180e-07, -9.704198700e-11, 1.682009920e-14, -3.000429710e+04, 4.966770100e+00}),

             // CO2: Carbon dioxide
             "CO2", new SpeciesData("CO2", 44.01,
                   new double[]{2.356773520e+00, 8.984596770e-03, -7.123562690e-06, 2.459190220e-09, -1.436995480e-13, -4.837196970e+04, 9.901052220e+00},
                   new double[]{4.636594930e+00, 2.741131400e-03, -9.958285310e-07, 1.603730110e-10, -9.161600030e-15, -4.912488530e+04, -1.935348550e+00}),

             // H2: Molecular hydrogen
             "H2", new SpeciesData("H2", 2.016,
                   new double[]{2.344331120e+00, 7.980520750e-03, -1.947815100e-05, 2.015720940e-08, -7.376117610e-12, -9.179351730e+02, 6.830102380e-01},
                   new double[]{2.932865750e+00, 8.266079670e-04, -1.464023640e-07, 1.541003590e-11, -6.888044320e-16, -8.130655810e+02, -1.024328650e+00}),

             // CO: Carbon monoxide
             "CO", new SpeciesData("CO", 28.01,
                   new double[]{3.579533470e+00, -6.103536800e-04, 1.016814330e-06, 9.070058840e-10, -9.044244990e-13, -1.434408600e+04, 3.508409280e+00},
                   new double[]{3.048485830e+00, 1.351728180e-03, -4.857940750e-07, 7.885364860e-11, -4.698074890e-15, -1.426611710e+04, 6.017097260e+00}),

             // OH: Hydroxyl radical
             "OH", new SpeciesData("OH", 17.007,
                   new double[]{3.992015430e+00, -2.401317520e-03, 4.617938410e-06, -3.881133330e-09, 1.364114700e-12, 3.615080560e+03, -1.039254580e-01},
                   new double[]{2.838646070e+00, 1.107255860e-03, -2.939149780e-07, 4.205242470e-11, -2.421690790e-15, 3.943958520e+03, 5.844526620e+00}),

             // O2: Molecular oxygen
             "O2", new SpeciesData("O2", 32.0,
                   new double[]{3.782456360e+00, -2.996734160e-03, 9.847302010e-06, -9.681295090e-09, 3.243728370e-12, -1.063943560e+03, 3.657675730e+00},
                   new double[]{3.660960650e+00, 6.563658110e-04, -1.411496270e-07, 2.057979350e-11, -1.299134360e-15, -1.215977180e+03, 3.415362790e+00}),

             // N2: Molecular nitrogen
             "N2", new SpeciesData("N2", 28.014,
                   new double[]{3.298677000e+00, 1.408240400e-03, -3.963222000e-06, 5.641515000e-09, -2.444854000e-12, -1.020899900e+03, 3.950372000e+00},
                   new double[]{2.926640000e+00, 1.487976800e-03, -5.684760000e-07, 1.009703800e-10, -6.753351000e-15, -9.227977000e+02, 5.980528000e+00}),

             // H: Atomic hydrogen
             "H", new SpeciesData("H", 1.008,
                   new double[]{2.500000000e+00, 0.000000000e+00, 0.000000000e+00, 0.000000000e+00, 0.000000000e+00, 2.547365990e+04, -4.466828530e-01},
                   new double[]{2.500000000e+00, 0.000000000e+00, 0.000000000e+00, 0.000000000e+00, 0.000000000e+00, 2.547365990e+04, -4.466828530e-01}),

             // O: Atomic oxygen
             "O", new SpeciesData("O", 16.0,
                   new double[]{3.168267100e+00, -3.279318840e-03, 6.643063960e-06, -6.128066240e-09, 2.112659710e-12, 2.912225920e+04, 2.051933460e+00},
                   new double[]{2.543636970e+00, -2.731624860e-05, -4.190295200e-09, 4.954818230e-12, -4.795536940e-16, 2.922601200e+04, 4.922294570e+00}));
    }

    /**
     * Returns the {@link SpeciesData} for the named species.
     *
     * @param name Chemical formula of the species (e.g. "H2O", "CO2")
     * @return Thermodynamic data, or {@code null} if the species is not in the database
     */
    public SpeciesData get(String name) {
        return database.get(name);
    }
}
