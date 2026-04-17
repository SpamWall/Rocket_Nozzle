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

package com.nozzle.solid;

import com.nozzle.core.GasProperties;

/**
 * Immutable record of solid propellant thermochemical and ballistic properties.
 *
 * <p>Burn rate follows <em>Vieille's law</em> (de Saint-Robert's law) with a
 * temperature-sensitivity correction:
 * <pre>
 *   r(P, T) = a₀ · exp(σ_p · (T − T_ref)) · P^n
 * </pre>
 * where:
 * <ul>
 *   <li>{@code a₀} — burn-rate coefficient at {@code T_ref} [m/s/Pa^n]</li>
 *   <li>{@code n}  — pressure exponent (dimensionless); must be in (0, 1) for
 *       stable combustion</li>
 *   <li>{@code σ_p} — temperature sensitivity [1/K]; typically 0.001–0.004 /K</li>
 *   <li>{@code T_ref} — reference temperature [K]; usually 294 K (21 °C)</li>
 * </ul>
 *
 * <p>The quasi-steady equilibrium chamber pressure for a given Kn (burning
 * area / throat area ratio) is derived by equating mass generation to mass
 * discharge through the throat:
 * <pre>
 *   P_c = (ρ_p · a(T) · Kn · c*)^(1/(1−n))
 * </pre>
 * This is numerically stable for all {@code n} &lt; 1 — the only physically
 * admissible range.
 *
 * <p>Factory methods are provided for five common propellant families.  All
 * burn-rate data are representative mid-lot values; actual hardware will vary
 * by ±5–15%.  For mission-critical work, measure burn rate for each lot.
 *
 * @param density                  propellant bulk density ρ_p [kg/m³]
 * @param burnRateCoefficient      Vieille coefficient a₀ [m/s/Pa^n] at
 *                                 {@code referenceTemperature}
 * @param burnRateExponent         pressure exponent n; must be in (0, 1)
 * @param temperatureSensitivity   σ_p [1/K] — fractional change in burn rate
 *                                 per kelvin deviation from {@code referenceTemperature}
 * @param referenceTemperature     T_ref [K] at which {@code burnRateCoefficient} is defined
 * @param chamberTemperature       adiabatic flame temperature T_c [K]
 * @param characteristicVelocity   practical c* [m/s] (efficiency-corrected)
 * @param combustionProducts       thermodynamic properties of the exhaust gas
 *
 * @see SolidMotorChamber
 * @see GrainGeometry
 */
public record SolidPropellant(
        double density,
        double burnRateCoefficient,
        double burnRateExponent,
        double temperatureSensitivity,
        double referenceTemperature,
        double chamberTemperature,
        double characteristicVelocity,
        GasProperties combustionProducts
) {

    /**
     * Standard reference temperature [K] used by all built-in factory propellants.
     * Corresponds to 21 °C (70 °F), the MIL-STD propellant qualification baseline.
     */
    public static final double STANDARD_REFERENCE_TEMP = 294.0;

    /**
     * Compact canonical constructor with full parameter validation.
     *
     * @throws IllegalArgumentException if any parameter is out of range
     */
    public SolidPropellant {
        if (density <= 0) {
            throw new IllegalArgumentException(
                    "Propellant density must be positive; got " + density);
        }
        if (burnRateCoefficient <= 0) {
            throw new IllegalArgumentException(
                    "Burn-rate coefficient must be positive; got " + burnRateCoefficient);
        }
        if (burnRateExponent <= 0 || burnRateExponent >= 1.0) {
            throw new IllegalArgumentException(
                    "Burn-rate exponent must be in (0, 1) for stable combustion; got "
                    + burnRateExponent);
        }
        if (temperatureSensitivity < 0) {
            throw new IllegalArgumentException(
                    "Temperature sensitivity must be non-negative; got "
                    + temperatureSensitivity);
        }
        if (referenceTemperature <= 0) {
            throw new IllegalArgumentException(
                    "Reference temperature must be positive; got " + referenceTemperature);
        }
        if (chamberTemperature <= 0) {
            throw new IllegalArgumentException(
                    "Chamber temperature must be positive; got " + chamberTemperature);
        }
        if (characteristicVelocity <= 0) {
            throw new IllegalArgumentException(
                    "Characteristic velocity must be positive; got " + characteristicVelocity);
        }
        if (combustionProducts == null) {
            throw new IllegalArgumentException("Combustion products gas properties must not be null");
        }
    }

    // -------------------------------------------------------------------------
    // Burn-rate and chamber-pressure calculations
    // -------------------------------------------------------------------------

    /**
     * Returns the burn rate at the given chamber pressure and reference temperature.
     *
     * <p>{@code r = a₀ · P^n}
     *
     * @param pressurePa chamber pressure [Pa]; must be positive
     * @return burn rate [m/s]
     */
    public double burnRate(double pressurePa) {
        return burnRateCoefficient * Math.pow(pressurePa, burnRateExponent);
    }

    /**
     * Returns the burn rate at the given chamber pressure and propellant temperature,
     * applying the temperature-sensitivity correction.
     *
     * <p>{@code r = a₀ · exp(σ_p · (T − T_ref)) · P^n}
     *
     * @param pressurePa              chamber pressure [Pa]; must be positive
     * @param propellantTemperatureK  propellant bulk temperature [K]
     * @return burn rate [m/s]
     */
    public double burnRate(double pressurePa, double propellantTemperatureK) {
        double tempFactor = Math.exp(
                temperatureSensitivity * (propellantTemperatureK - referenceTemperature));
        return burnRateCoefficient * tempFactor * Math.pow(pressurePa, burnRateExponent);
    }

    /**
     * Returns the Klemmung (Kn) — the ratio of burning surface area to throat area.
     * Kn is the primary design variable linking grain geometry to chamber pressure.
     *
     * @param burningAreaM2 burning surface area A_b [m²]
     * @param throatAreaM2  throat area A_t [m²]
     * @return Kn = A_b / A_t (dimensionless)
     */
    public double klemmung(double burningAreaM2, double throatAreaM2) {
        return burningAreaM2 / throatAreaM2;
    }

    /**
     * Returns the quasi-steady equilibrium chamber pressure at the given burning
     * area, throat area, and reference propellant temperature.
     *
     * <p>{@code P_c = (ρ_p · a₀ · Kn · c*)^(1/(1−n))}
     *
     * @param burningAreaM2 burning surface area A_b [m²]
     * @param throatAreaM2  throat area A_t [m²]
     * @return equilibrium chamber pressure [Pa]
     */
    public double equilibriumPressure(double burningAreaM2, double throatAreaM2) {
        return equilibriumPressure(burningAreaM2, throatAreaM2, referenceTemperature);
    }

    /**
     * Returns the quasi-steady equilibrium chamber pressure at the given burning
     * area, throat area, and propellant temperature.
     *
     * <p>{@code P_c = (ρ_p · a(T) · Kn · c*)^(1/(1−n))}
     * where {@code a(T) = a₀ · exp(σ_p · (T − T_ref))}.
     *
     * @param burningAreaM2          burning surface area A_b [m²]
     * @param throatAreaM2           throat area A_t [m²]
     * @param propellantTemperatureK propellant bulk temperature [K]
     * @return equilibrium chamber pressure [Pa]
     */
    public double equilibriumPressure(double burningAreaM2, double throatAreaM2,
                                       double propellantTemperatureK) {
        double aT  = burnRateCoefficient
                     * Math.exp(temperatureSensitivity
                                * (propellantTemperatureK - referenceTemperature));
        double kn  = burningAreaM2 / throatAreaM2;
        double base = density * aT * kn * characteristicVelocity;
        return Math.pow(base, 1.0 / (1.0 - burnRateExponent));
    }

    // -------------------------------------------------------------------------
    // Built-in propellant factories
    // -------------------------------------------------------------------------

    /**
     * Returns nominal properties for HTPB/AP composite propellant
     * (68% AP / 18% Al / 14% HTPB by mass, O/F ≈ 2.12, T_c ≈ 3200 K).
     *
     * <p>Burn rate: 10 mm/s at 7.0 MPa, n = 0.35.
     * Temperature sensitivity: σ_p = 0.00177 /K.
     * Characteristic velocity: 1590 m/s (practical, η_c* ≈ 0.97).
     *
     * <p>Sources: Sutton &amp; Biblarz, 9th ed., Table 13-1;
     * Humble et al., <em>Space Propulsion Analysis and Design</em>, §4.5.
     *
     * @return APCP/HTPB propellant properties
     */
    public static SolidPropellant APCP_HTPB() {
        // r = 0.010 m/s at P = 7.0e6 Pa, n = 0.35
        // a0 = 0.010 / (7.0e6)^0.35 = 0.010 / 248.6 = 4.022e-5 m/s/Pa^0.35
        return new SolidPropellant(
                1750.0,
                4.022e-5,
                0.35,
                0.00177,
                STANDARD_REFERENCE_TEMP,
                3200.0,
                1590.0,
                GasProperties.APCP_HTPB_PRODUCTS
        );
    }

    /**
     * Returns nominal properties for PBAN/AP composite propellant
     * (Space-Shuttle-SRB type: 69.6% AP / 16% Al / 12% PBAN / 2.4% epoxy cure,
     * T_c ≈ 3400 K).
     *
     * <p>Burn rate: 8.6 mm/s at 6.3 MPa, n = 0.30.
     * Temperature sensitivity: σ_p = 0.00200 /K.
     * Characteristic velocity: 1570 m/s.
     *
     * <p>Source: Thiokol/ATK SRB Motor Specification; Sutton &amp; Biblarz, 9th ed., §13.
     *
     * @return APCP/PBAN propellant properties
     */
    public static SolidPropellant APCP_PBAN() {
        // r = 0.0086 m/s at P = 6.3e6 Pa, n = 0.30
        // a0 = 0.0086 / (6.3e6)^0.30 = 0.0086 / 109.6 = 7.847e-5 m/s/Pa^0.30
        return new SolidPropellant(
                1770.0,
                7.847e-5,
                0.30,
                0.00200,
                STANDARD_REFERENCE_TEMP,
                3400.0,
                1570.0,
                GasProperties.APCP_PBAN_PRODUCTS
        );
    }

    /**
     * Returns nominal properties for KNSU (potassium nitrate/sucrose, 65/35 by mass).
     *
     * <p>KNSU is a low-cost, storable "candy propellant" widely used in amateur
     * rocketry.  Its modest performance (Isp ≈ 130 s vacuum) is offset by
     * exceptional simplicity and safety during propellant preparation.
     *
     * <p>Burn rate: 13 mm/s at 1.0 MPa, n = 0.32.
     * Temperature sensitivity: σ_p = 0.00250 /K.
     * Characteristic velocity: 889 m/s (η_c* ≈ 0.96 × 926 m/s theoretical).
     *
     * <p>Source: Nakka, R., <em>KN-Sucrose Propellant</em>, Rev. 2 (2002).
     *
     * @return KNSU propellant properties
     */
    public static SolidPropellant KNSU() {
        // r = 0.013 m/s at P = 1.0e6 Pa, n = 0.32
        // a0 = 0.013 / (1.0e6)^0.32 = 0.013 / 83.18 = 1.563e-4 m/s/Pa^0.32
        return new SolidPropellant(
                1889.0,
                1.563e-4,
                0.32,
                0.00250,
                STANDARD_REFERENCE_TEMP,
                1720.0,
                889.0,
                GasProperties.KNSU_PRODUCTS
        );
    }

    /**
     * Returns nominal properties for KNDX (potassium nitrate/dextrose, 65/35 by mass).
     *
     * <p>KNDX burns slightly cooler than KNSU (higher C:H ratio in dextrose relative
     * to sucrose) and is less hygroscopic, making it better suited for humid climates.
     *
     * <p>Burn rate: 7.5 mm/s at 1.0 MPa, n = 0.40.
     * Temperature sensitivity: σ_p = 0.00250 /K.
     * Characteristic velocity: 858 m/s.
     *
     * <p>Source: Nakka, R., <em>KN-Dextrose Propellant</em>, Rev. 1 (2005).
     *
     * @return KNDX propellant properties
     */
    public static SolidPropellant KNDX() {
        // r = 0.0075 m/s at P = 1.0e6 Pa, n = 0.40
        // a0 = 0.0075 / (1.0e6)^0.40 = 0.0075 / 251.2 = 2.986e-5 m/s/Pa^0.40
        return new SolidPropellant(
                1879.0,
                2.986e-5,
                0.40,
                0.00250,
                STANDARD_REFERENCE_TEMP,
                1688.0,
                858.0,
                GasProperties.KNDX_PRODUCTS
        );
    }

    /**
     * Returns nominal properties for double-base propellant
     * (JPN formulation: 52% nitrocellulose / 43% nitroglycerin / 5% additives).
     *
     * <p>Double-base propellants are used extensively in military rockets and
     * pyrotechnic igniters.  They contain no ammonium perchlorate, producing a
     * cleaner, HCl-free exhaust, but exhibit higher temperature sensitivity than
     * composite propellants.
     *
     * <p>Burn rate: 15 mm/s at 7.0 MPa, n = 0.28.
     * Temperature sensitivity: σ_p = 0.00350 /K.
     * Characteristic velocity: 1390 m/s.
     *
     * <p>Source: Sutton &amp; Biblarz, 9th ed., Table 13-1;
     * NATO STANAG 4170.
     *
     * @return double-base propellant properties
     */
    public static SolidPropellant DOUBLE_BASE() {
        // r = 0.015 m/s at P = 7.0e6 Pa, n = 0.28
        // a0 = 0.015 / (7.0e6)^0.28 = 0.015 / 82.49 = 1.818e-4 m/s/Pa^0.28
        return new SolidPropellant(
                1600.0,
                1.818e-4,
                0.28,
                0.00350,
                STANDARD_REFERENCE_TEMP,
                2500.0,
                1390.0,
                GasProperties.DOUBLE_BASE_PRODUCTS
        );
    }
}
