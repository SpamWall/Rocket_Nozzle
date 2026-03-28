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

package com.nozzle.core;

import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Immutable record representing thermodynamic gas properties.
 * Follows Jakarta Bean Validation standards.
 *
 * @param gamma          Ratio of specific heats (Cp/Cv)
 * @param molecularWeight Molecular weight in kg/kmol
 * @param gasConstant    Specific gas constant in J/(kg·K)
 * @param viscosityRef   Reference viscosity in Pa·s
 * @param tempRef        Reference temperature for viscosity in K
 * @param sutherlandConst Sutherland's constant in K
 */
public record GasProperties(
        @Positive double gamma,
        @Positive double molecularWeight,
        @Positive double gasConstant,
        @Positive double viscosityRef,
        @Positive double tempRef,
        @Positive double sutherlandConst
) {
    
    private static final Logger LOG = LoggerFactory.getLogger(GasProperties.class);

    /**
     * Universal gas constant in J/(kmol·K).
     */
    public static final double UNIVERSAL_GAS_CONSTANT = 8314.46;
    
    /**
     * Standard gas properties for air at standard conditions.
     */
    public static final GasProperties AIR = new GasProperties(
            1.4,
            28.97,
            287.05,
            1.716e-5,
            273.15,
            110.4
    );
    
    /**
     * Standard gas properties for hydrogen at standard conditions.
     */
    public static final GasProperties HYDROGEN = new GasProperties(
            1.41,
            2.016,
            4124.2,
            8.76e-6,
            293.85,
            72.0
    );
    
    /**
     * Standard gas properties for nitrogen at standard conditions.
     */
    public static final GasProperties NITROGEN = new GasProperties(
            1.4,
            28.014,
            296.8,
            1.663e-5,
            273.0,
            107.0
    );
    
    /**
     * Standard gas properties for combustion products (typical LOX/RP-1).
     */
    public static final GasProperties LOX_RP1_PRODUCTS = new GasProperties(
            1.24,
            23.0,
            361.5,
            4.5e-5,
            3500.0,
            240.0
    );
    
    /**
     * Standard gas properties for combustion products (typical LOX/LH2).
     */
    public static final GasProperties LOX_LH2_PRODUCTS = new GasProperties(
            1.26,
            12.0,
            692.9,
            3.5e-5,
            3200.0,
            200.0
    );

    /**
     * Standard gas properties for combustion products (typical LOX/CH4).
     */
    public static final GasProperties LOX_CH4_PRODUCTS = new GasProperties(
            1.23,
            22.0,
            377.9,
            4.5e-5,
            3500.0,
            240.0
    );

    /**
     * Compact constructor with validation.
     */
    public GasProperties {
        if (gamma <= 1.0) {
            throw new IllegalArgumentException("Gamma must be greater than 1.0");
        }
        if (molecularWeight <= 0) {
            throw new IllegalArgumentException("Molecular weight must be positive");
        }
        if (gasConstant <= 0) {
            throw new IllegalArgumentException("Gas constant must be positive");
        }
    }
    
    /**
     * Creates gas properties from gamma and molecular weight.
     *
     * @param gamma          Ratio of specific heats
     * @param molecularWeight Molecular weight in kg/kmol
     * @return New GasProperties instance
     */
    public static GasProperties fromGammaAndMW(double gamma, double molecularWeight) {
        double gasConstant = UNIVERSAL_GAS_CONSTANT / molecularWeight;
        return new GasProperties(gamma, molecularWeight, gasConstant, 1.716e-5, 273.15, 110.4);
    }
    
    /**
     * Calculates dynamic viscosity using Sutherland's law.
     *
     * @param temperature Temperature in K
     * @return Dynamic viscosity in Pa·s
     */
    public double calculateViscosity(double temperature) {
        return viscosityRef * Math.pow(temperature / tempRef, 1.5) 
               * (tempRef + sutherlandConst) / (temperature + sutherlandConst);
    }
    
    /**
     * Calculates specific heat at constant pressure.
     *
     * @return Cp in J/(kg·K)
     */
    public double specificHeatCp() {
        return gamma * gasConstant / (gamma - 1);
    }
    
    /**
     * Calculates specific heat at constant volume.
     *
     * @return Cv in J/(kg·K)
     */
    public double specificHeatCv() {
        return gasConstant / (gamma - 1);
    }
    
    /**
     * Calculates the speed of sound.
     *
     * @param temperature Temperature in K
     * @return Speed of sound in m/s
     */
    public double speedOfSound(double temperature) {
        return Math.sqrt(gamma * gasConstant * temperature);
    }
    
    /**
     * Calculates isentropic temperature ratio.
     *
     * @param mach Mach number
     * @return T/T0 ratio
     */
    public double isentropicTemperatureRatio(double mach) {
        return 1.0 / (1.0 + (gamma - 1) / 2.0 * mach * mach);
    }
    
    /**
     * Calculates isentropic pressure ratio.
     *
     * @param mach Mach number
     * @return P/P0 ratio
     */
    public double isentropicPressureRatio(double mach) {
        return Math.pow(isentropicTemperatureRatio(mach), gamma / (gamma - 1));
    }
    
    /**
     * Calculates isentropic density ratio.
     *
     * @param mach Mach number
     * @return rho/rho0 ratio
     */
    public double isentropicDensityRatio(double mach) {
        return Math.pow(isentropicTemperatureRatio(mach), 1.0 / (gamma - 1));
    }
    
    /**
     * Calculates Prandtl-Meyer function.
     *
     * @param mach Mach number (must be &gt;= 1.0)
     * @return Prandtl-Meyer angle in radians
     * @throws IllegalArgumentException if mach is less than 1.0
     */
    public double prandtlMeyerFunction(double mach) throws IllegalArgumentException {
        if (mach < 1.0) {
            throw new IllegalArgumentException("Prandtl-Meyer function is undefined for Mach < 1.0, got: " + mach);
        }
        double gp1 = gamma + 1;
        double gm1 = gamma - 1;
        double sqrtRatio = Math.sqrt(gp1 / gm1);
        double term = Math.sqrt(mach * mach - 1);
        return sqrtRatio * Math.atan(term / sqrtRatio) - Math.atan(term);
    }
    
    /**
     * Calculates Mach number from Prandtl-Meyer function using Newton-Raphson.
     *
     * @param nu Prandtl-Meyer angle in radians
     * @return Mach number
     */
    public double machFromPrandtlMeyer(double nu) {
        if (nu <= 0) {
            return 1.0;
        }
        
        // Initial guess using approximate formula
        double mach = 1.0 + Math.sqrt(6.0 * (gamma - 1) / (gamma + 1)) * Math.pow(nu, 0.5);
        
        // Newton-Raphson iteration
        boolean converged = false;
        for (int i = 0; i < 50; i++) {
            double f = prandtlMeyerFunction(mach) - nu;
            double mach2 = mach * mach;
            double df = Math.sqrt(mach2 - 1) / (1.0 + (gamma - 1) / 2.0 * mach2) / mach;
            double deltaMach = f / df;
            mach -= deltaMach;
            if (Math.abs(deltaMach) < 1e-10) {
                converged = true;
                break;
            }
        }
        if (!converged) {
            LOG.warn("machFromPrandtlMeyer: did not converge in 50 iterations for nu={}", nu);
        }
        return mach;
    }
    
    /**
     * Calculates Mach angle.
     *
     * @param mach Mach number
     * @return Mach angle in radians
     */
    public double machAngle(double mach) {
        if (mach <= 1.0) {
            return Math.PI / 2;
        }
        return Math.asin(1.0 / mach);
    }
    
    /**
     * Calculates area ratio (A/A*) for isentropic flow.
     *
     * @param mach Mach number
     * @return Area ratio
     */
    public double areaRatio(double mach) {
        if (mach <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        double gp1 = gamma + 1;
        double gm1 = gamma - 1;
        double term1 = 2.0 / gp1;
        double term2 = 1.0 + gm1 / 2.0 * mach * mach;
        double exponent = (gp1) / (2.0 * gm1);
        return (1.0 / mach) * Math.pow(term1 * term2, exponent);
    }
    
    /**
     * Calculates Mach number from area ratio (supersonic solution).
     *
     * @param areaRatio A/A* ratio
     * @return Mach number (supersonic)
     */
    public double machFromAreaRatio(double areaRatio) {
        if (areaRatio <= 1.0) {
            return 1.0;
        }
        
        // Initial guess
        double mach = 1.0 + Math.sqrt(areaRatio - 1.0);
        
        // Newton-Raphson iteration
        boolean converged = false;
        for (int i = 0; i < 50; i++) {
            double f = areaRatio(mach) - areaRatio;
            // Closed-form derivative: d(A/A*)/dM = A/A*(M) × (M²−1) / (M × (1 + (γ−1)/2 × M²))
            // gp1 cancels in the derivation and does not appear in the final expression.
            double gm1 = gamma - 1;
            double mach2 = mach * mach;
            double term = 1.0 + gm1 / 2.0 * mach2;
            double df = areaRatio(mach) * (mach2 - 1) / (mach * term);
            double deltaMach = f / df;
            mach -= deltaMach;
            if (Math.abs(deltaMach) < 1e-10) {
                converged = true;
                break;
            }
        }
        if (!converged) {
            LOG.warn("machFromAreaRatio: did not converge in 50 iterations for areaRatio={}", areaRatio);
        }
        return mach;
    }
    
    /**
     * Creates a copy with modified gamma for chemistry effects.
     *
     * @param newGamma New gamma value
     * @return New GasProperties with modified gamma
     */
    public GasProperties withGamma(double newGamma) {
        return new GasProperties(newGamma, molecularWeight, gasConstant, 
                                 viscosityRef, tempRef, sutherlandConst);
    }
}
