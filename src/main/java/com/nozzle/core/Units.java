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

/**
 * Imperial/SI and other unit conversion utilities for the nozzle design library.
 *
 * <p>The library operates exclusively in SI units internally.  Use this class
 * at system boundaries — when accepting user input in customary units or
 * presenting output to users who prefer imperial units.
 *
 * <h2>Design notes</h2>
 * <ul>
 *   <li>All methods are pure functions: stateless, side-effect-free, and thread-safe.</li>
 *   <li>Conversion factors are defined as named constants so that the derivation is
 *       transparent and auditable.</li>
 *   <li>Methods are grouped by physical quantity and named
 *       {@code fromUnitToUnit(value)} (e.g. {@link #metersToFeet}).</li>
 *   <li>Specific impulse (Isp) in seconds is numerically identical in SI
 *       (N·s/kg) and US customary (lbf·s/lbm) because {@code g₀} cancels;
 *       no conversion method is provided.</li>
 *   <li>Molecular weight in kg/kmol equals g/mol and lb/lbmol numerically;
 *       no conversion is needed.</li>
 * </ul>
 *
 * <h2>Usage example</h2>
 * <pre>{@code
 * double rt = Units.inchesToMeters(2.0);          // 2" throat radius → SI
 * double pc = Units.psiToPascals(1000.0);          // 1000 psia → SI
 * double isp = 350.0;                              // seconds — no conversion needed
 * double thrust_lbf = Units.newtonsToLbf(thrust);  // SI thrust → display value
 * }</pre>
 */
public final class Units {

    // ── Exact / primary conversion factors ──────────────────────────────────
    /** Exact metres per foot (by definition: 1 ft = 0.3048 m exactly). */
    private static final double METERS_PER_FOOT       = 0.3048;
    /** Exact metres per inch (1 in = 2.54 cm exactly). */
    private static final double METERS_PER_INCH        = 0.0254;
    /** Pascals per psi (lbf/in²): 1 psi = 6894.757293168 Pa. */
    private static final double PASCALS_PER_PSI        = 6894.757293168;
    /** Standard atmosphere in Pa (exact per ISO 2533). */
    private static final double PASCALS_PER_ATM        = 101325.0;
    /** Newtons per pound-force (exact: 1 lbf = 0.45359237 kg × 9.80665 m/s²). */
    private static final double NEWTONS_PER_LBF        = 4.4482216152605;
    /** Kilograms per pound-mass (exact: 1 lbm = 0.45359237 kg). */
    private static final double KG_PER_LBM             = 0.45359237;
    /** Kilograms per slug (1 slug = 14.593902937 kg). */
    private static final double KG_PER_SLUG            = 14.593902937;
    /** Standard gravity in m/s² (exact per ISO 80000-3). */
    private static final double G0_SI                  = 9.80665;
    /** BTU per joule (thermochemical BTU: 1 BTU_th = 1054.3503 J). */
    private static final double JOULES_PER_BTU         = 1055.05585262;
    /** W/(m·K) per BTU/(h·ft·°F) — thermal conductivity conversion. */
    private static final double W_PER_MK_PER_BTU_HRFTF = 1.7307356887;
    /**
     * W/(m²·K) per BTU/(h·ft²·°F) — heat transfer coefficient (film coefficient) conversion.
     * Derivation: 1 BTU/(h·ft²·°F) = 1055.056 J / (3600 s × 0.3048² m² × 5/9 K)
     *           = 1055.056 / (3600 × 0.09290304 × 0.5556) = 5.6782633 W/(m²·K)
     */
    private static final double W_PER_M2K_PER_BTU_HRFT2F = 5.6782633;
    /** W/m² per BTU/ft²/s. */
    private static final double W_PER_M2_PER_BTU_FT2S  = 11356.5267;
    /** Pa·s per lb/ft·s. */
    private static final double PAS_PER_LB_FT_S        = 1.4881639;
    /** kg/m³ per lb/ft³. */
    private static final double KG_M3_PER_LB_FT3       = 16.018463374;
    /** m²/s per ft²/s. */
    private static final double M2_PER_S_PER_FT2_PER_S = METERS_PER_FOOT * METERS_PER_FOOT;

    /** Not instantiable. */
    private Units() {}

    // =========================================================================
    // LENGTH
    // =========================================================================

    /**
     * Converts metres to feet.
     *
     * @param m length in metres
     * @return length in feet
     */
    public static double metersToFeet(double m)       { return m / METERS_PER_FOOT; }

    /**
     * Converts feet to metres.
     *
     * @param ft length in feet
     * @return length in metres
     */
    public static double feetToMeters(double ft)      { return ft * METERS_PER_FOOT; }

    /**
     * Converts metres to inches.
     *
     * @param m length in metres
     * @return length in inches
     */
    public static double metersToInches(double m)     { return m / METERS_PER_INCH; }

    /**
     * Converts inches to metres.
     *
     * @param in length in inches
     * @return length in metres
     */
    public static double inchesToMeters(double in)    { return in * METERS_PER_INCH; }

    /**
     * Converts metres to millimetres.
     *
     * @param m length in metres
     * @return length in millimetres
     */
    public static double metersToMillimeters(double m)   { return m * 1000.0; }

    /**
     * Converts millimetres to metres.
     *
     * @param mm length in millimetres
     * @return length in metres
     */
    public static double millimetersToMeters(double mm)  { return mm / 1000.0; }

    /**
     * Converts metres to centimetres.
     *
     * @param m length in metres
     * @return length in centimetres
     */
    public static double metersToCentimeters(double m)   { return m * 100.0; }

    /**
     * Converts centimetres to metres.
     *
     * @param cm length in centimetres
     * @return length in metres
     */
    public static double centimetersToMeters(double cm)  { return cm / 100.0; }

    // =========================================================================
    // PRESSURE
    // =========================================================================

    /**
     * Converts pascals to pounds-force per square inch (psia or psig by context).
     *
     * @param pa pressure in Pa
     * @return pressure in psi
     */
    public static double pascalsToPsi(double pa)     { return pa / PASCALS_PER_PSI; }

    /**
     * Converts psi to pascals.
     *
     * @param psi pressure in psi
     * @return pressure in Pa
     */
    public static double psiToPascals(double psi)    { return psi * PASCALS_PER_PSI; }

    /**
     * Converts pascals to standard atmospheres.
     *
     * @param pa pressure in Pa
     * @return pressure in atm
     */
    public static double pascalsToAtm(double pa)     { return pa / PASCALS_PER_ATM; }

    /**
     * Converts standard atmospheres to pascals.
     *
     * @param atm pressure in atm
     * @return pressure in Pa
     */
    public static double atmToPascals(double atm)    { return atm * PASCALS_PER_ATM; }

    /**
     * Converts pascals to bar (1 bar = 100 000 Pa exactly).
     *
     * @param pa pressure in Pa
     * @return pressure in bar
     */
    public static double pascalsToBar(double pa)     { return pa / 1.0e5; }

    /**
     * Converts bar to pascals.
     *
     * @param bar pressure in bar
     * @return pressure in Pa
     */
    public static double barToPascals(double bar)    { return bar * 1.0e5; }

    /**
     * Converts pascals to megapascals.
     *
     * @param pa pressure in Pa
     * @return pressure in MPa
     */
    public static double pascalsToMegapascals(double pa)   { return pa / 1.0e6; }

    /**
     * Converts megapascals to pascals.
     *
     * @param mpa pressure in MPa
     * @return pressure in Pa
     */
    public static double megapascalsToPascals(double mpa)  { return mpa * 1.0e6; }

    /**
     * Converts pascals to kilopascals.
     *
     * @param pa pressure in Pa
     * @return pressure in kPa
     */
    public static double pascalsToKilopascals(double pa)   { return pa / 1.0e3; }

    /**
     * Converts kilopascals to pascals.
     *
     * @param kpa pressure in kPa
     * @return pressure in Pa
     */
    public static double kilopascalsToPascals(double kpa)  { return kpa * 1.0e3; }

    // =========================================================================
    // TEMPERATURE
    // =========================================================================

    /**
     * Converts kelvin to degrees Celsius.
     *
     * @param k temperature in K
     * @return temperature in °C
     */
    public static double kelvinToCelsius(double k)    { return k - 273.15; }

    /**
     * Converts degrees Celsius to kelvin.
     *
     * @param c temperature in °C
     * @return temperature in K
     */
    public static double celsiusToKelvin(double c)    { return c + 273.15; }

    /**
     * Converts kelvin to degrees Fahrenheit.
     *
     * @param k temperature in K
     * @return temperature in °F
     */
    public static double kelvinToFahrenheit(double k) { return (k - 273.15) * 9.0 / 5.0 + 32.0; }

    /**
     * Converts degrees Fahrenheit to kelvin.
     *
     * @param f temperature in °F
     * @return temperature in K
     */
    public static double fahrenheitToKelvin(double f) { return (f - 32.0) * 5.0 / 9.0 + 273.15; }

    /**
     * Converts kelvin to degrees Rankine.
     *
     * @param k temperature in K
     * @return temperature in °R
     */
    public static double kelvinToRankine(double k)    { return k * 1.8; }

    /**
     * Converts degrees Rankine to kelvin.
     *
     * @param r temperature in °R
     * @return temperature in K
     */
    public static double rankineToKelvin(double r)    { return r / 1.8; }

    /**
     * Converts degrees Celsius to degrees Fahrenheit.
     *
     * @param c temperature in °C
     * @return temperature in °F
     */
    public static double celsiusToFahrenheit(double c) { return c * 9.0 / 5.0 + 32.0; }

    /**
     * Converts degrees Fahrenheit to degrees Celsius.
     *
     * @param f temperature in °F
     * @return temperature in °C
     */
    public static double fahrenheitToCelsius(double f) { return (f - 32.0) * 5.0 / 9.0; }

    // =========================================================================
    // FORCE
    // =========================================================================

    /**
     * Converts newtons to pound-force.
     *
     * @param n force in N
     * @return force in lbf
     */
    public static double newtonsToLbf(double n)            { return n / NEWTONS_PER_LBF; }

    /**
     * Converts pound-force to newtons.
     *
     * @param lbf force in lbf
     * @return force in N
     */
    public static double lbfToNewtons(double lbf)          { return lbf * NEWTONS_PER_LBF; }

    /**
     * Converts newtons to kilonewtons.
     *
     * @param n force in N
     * @return force in kN
     */
    public static double newtonsToKilonewtons(double n)    { return n / 1000.0; }

    /**
     * Converts kilonewtons to newtons.
     *
     * @param kn force in kN
     * @return force in N
     */
    public static double kilonewtonsToNewtons(double kn)   { return kn * 1000.0; }

    /**
     * Converts newtons to kilogram-force (kgf, also known as kilopond).
     *
     * @param n force in N
     * @return force in kgf
     */
    public static double newtonsToKilogramForce(double n)  { return n / G0_SI; }

    /**
     * Converts kilogram-force to newtons.
     *
     * @param kgf force in kgf
     * @return force in N
     */
    public static double kilogramForceToNewtons(double kgf) { return kgf * G0_SI; }

    // =========================================================================
    // MASS
    // =========================================================================

    /**
     * Converts kilograms to pound-mass.
     *
     * @param kg mass in kg
     * @return mass in lbm
     */
    public static double kilogramsToPounds(double kg)  { return kg / KG_PER_LBM; }

    /**
     * Converts pound-mass to kilograms.
     *
     * @param lbm mass in lbm
     * @return mass in kg
     */
    public static double poundsToKilograms(double lbm) { return lbm * KG_PER_LBM; }

    /**
     * Converts kilograms to slugs.
     *
     * @param kg mass in kg
     * @return mass in slugs
     */
    public static double kilogramsToSlugs(double kg)   { return kg / KG_PER_SLUG; }

    /**
     * Converts slugs to kilograms.
     *
     * @param slug mass in slugs
     * @return mass in kg
     */
    public static double slugsToKilograms(double slug)  { return slug * KG_PER_SLUG; }

    // =========================================================================
    // MASS FLOW RATE
    // =========================================================================

    /**
     * Converts kg/s to lbm/s.
     *
     * @param kgps mass flow in kg/s
     * @return mass flow in lbm/s
     */
    public static double kgPerSecToLbPerSec(double kgps) { return kgps / KG_PER_LBM; }

    /**
     * Converts lbm/s to kg/s.
     *
     * @param lbps mass flow in lbm/s
     * @return mass flow in kg/s
     */
    public static double lbPerSecToKgPerSec(double lbps) { return lbps * KG_PER_LBM; }

    // =========================================================================
    // VELOCITY
    // =========================================================================

    /**
     * Converts m/s to ft/s.
     *
     * @param mps velocity in m/s
     * @return velocity in ft/s
     */
    public static double metersPerSecToFeetPerSec(double mps) { return mps / METERS_PER_FOOT; }

    /**
     * Converts ft/s to m/s.
     *
     * @param fps velocity in ft/s
     * @return velocity in m/s
     */
    public static double feetPerSecToMetersPerSec(double fps) { return fps * METERS_PER_FOOT; }

    /**
     * Converts m/s to knots (1 knot = 1852 m/h = 0.514444 m/s).
     *
     * @param mps velocity in m/s
     * @return velocity in knots
     */
    public static double metersPerSecToKnots(double mps) { return mps * 3600.0 / 1852.0; }

    /**
     * Converts knots to m/s.
     *
     * @param knots velocity in knots
     * @return velocity in m/s
     */
    public static double knotsToMetersPerSec(double knots) { return knots * 1852.0 / 3600.0; }

    // =========================================================================
    // AREA
    // =========================================================================

    /**
     * Converts m² to in².
     *
     * @param m2 area in m²
     * @return area in in²
     */
    public static double squareMetersToSquareInches(double m2) {
        return m2 / (METERS_PER_INCH * METERS_PER_INCH);
    }

    /**
     * Converts in² to m².
     *
     * @param in2 area in in²
     * @return area in m²
     */
    public static double squareInchesToSquareMeters(double in2) {
        return in2 * METERS_PER_INCH * METERS_PER_INCH;
    }

    /**
     * Converts m² to ft².
     *
     * @param m2 area in m²
     * @return area in ft²
     */
    public static double squareMetersToSquareFeet(double m2) {
        return m2 / (METERS_PER_FOOT * METERS_PER_FOOT);
    }

    /**
     * Converts ft² to m².
     *
     * @param ft2 area in ft²
     * @return area in m²
     */
    public static double squareFeetToSquareMeters(double ft2) {
        return ft2 * METERS_PER_FOOT * METERS_PER_FOOT;
    }

    /**
     * Converts m² to cm².
     *
     * @param m2 area in m²
     * @return area in cm²
     */
    public static double squareMetersToSquareCentimeters(double m2) { return m2 * 1.0e4; }

    /**
     * Converts cm² to m².
     *
     * @param cm2 area in cm²
     * @return area in m²
     */
    public static double squareCentimetersToSquareMeters(double cm2) { return cm2 / 1.0e4; }

    // =========================================================================
    // ANGLE
    // =========================================================================

    /**
     * Converts radians to degrees.
     *
     * @param rad angle in radians
     * @return angle in degrees
     */
    public static double radiansToDegrees(double rad) { return Math.toDegrees(rad); }

    /**
     * Converts degrees to radians.
     *
     * @param deg angle in degrees
     * @return angle in radians
     */
    public static double degreesToRadians(double deg) { return Math.toRadians(deg); }

    // =========================================================================
    // DENSITY
    // =========================================================================

    /**
     * Converts kg/m³ to lb/ft³.
     *
     * @param kgm3 density in kg/m³
     * @return density in lb/ft³
     */
    public static double kgPerCubicMeterToLbPerCubicFoot(double kgm3) {
        return kgm3 / KG_M3_PER_LB_FT3;
    }

    /**
     * Converts lb/ft³ to kg/m³.
     *
     * @param lbft3 density in lb/ft³
     * @return density in kg/m³
     */
    public static double lbPerCubicFootToKgPerCubicMeter(double lbft3) {
        return lbft3 * KG_M3_PER_LB_FT3;
    }

    // =========================================================================
    // HEAT FLUX
    // =========================================================================

    /**
     * Converts W/m² to BTU/ft²/s.
     *
     * @param wpm2 heat flux in W/m²
     * @return heat flux in BTU/ft²/s
     */
    public static double wattsPerM2ToBtuPerFt2PerSec(double wpm2) {
        return wpm2 / W_PER_M2_PER_BTU_FT2S;
    }

    /**
     * Converts BTU/ft²/s to W/m².
     *
     * @param btuft2s heat flux in BTU/ft²/s
     * @return heat flux in W/m²
     */
    public static double btuPerFt2PerSecToWattsPerM2(double btuft2s) {
        return btuft2s * W_PER_M2_PER_BTU_FT2S;
    }

    /**
     * Converts W/(m²·K) to BTU/(h·ft²·°F) (thermal conductance / film coefficient).
     *
     * <p>Note: this conversion uses a different factor from thermal conductivity
     * ({@link #wPerMKToBtuPerHrFtF}); the units differ by one length dimension.
     *
     * @param wpm2k heat transfer coefficient in W/(m²·K)
     * @return heat transfer coefficient in BTU/(h·ft²·°F)
     */
    public static double wPerM2KToBtuPerHrFt2F(double wpm2k) {
        return wpm2k / W_PER_M2K_PER_BTU_HRFT2F;
    }

    /**
     * Converts BTU/(h·ft²·°F) to W/(m²·K).
     *
     * @param btu heat transfer coefficient in BTU/(h·ft²·°F)
     * @return heat transfer coefficient in W/(m²·K)
     */
    public static double btuPerHrFt2FToWPerM2K(double btu) {
        return btu * W_PER_M2K_PER_BTU_HRFT2F;
    }

    // =========================================================================
    // ENERGY
    // =========================================================================

    /**
     * Converts joules to BTU (thermochemical).
     *
     * @param j energy in J
     * @return energy in BTU
     */
    public static double joulesToBtu(double j)    { return j / JOULES_PER_BTU; }

    /**
     * Converts BTU to joules.
     *
     * @param btu energy in BTU
     * @return energy in J
     */
    public static double btuToJoules(double btu)  { return btu * JOULES_PER_BTU; }

    /**
     * Converts joules to thermochemical calories (1 cal = 4.184 J exactly).
     *
     * @param j energy in J
     * @return energy in cal
     */
    public static double joulesToCalories(double j)   { return j / 4.184; }

    /**
     * Converts thermochemical calories to joules.
     *
     * @param cal energy in cal
     * @return energy in J
     */
    public static double caloriesToJoules(double cal) { return cal * 4.184; }

    // =========================================================================
    // SPECIFIC ENTHALPY / SPECIFIC HEAT
    // =========================================================================

    /**
     * Converts J/kg to BTU/lbm.
     *
     * @param jkg specific enthalpy in J/kg
     * @return specific enthalpy in BTU/lbm
     */
    public static double jPerKgToBtuPerLbm(double jkg) {
        return jkg * KG_PER_LBM / JOULES_PER_BTU;
    }

    /**
     * Converts BTU/lbm to J/kg.
     *
     * @param btulbm specific enthalpy in BTU/lbm
     * @return specific enthalpy in J/kg
     */
    public static double btuPerLbmToJPerKg(double btulbm) {
        return btulbm * JOULES_PER_BTU / KG_PER_LBM;
    }

    /**
     * Converts J/(kg·K) to BTU/(lbm·°R).
     *
     * <p>Derivation: 1 BTU/(lbm·°R) = 1055.056 J / (0.45359 kg × 5/9 K) = 4186.8 J/(kg·K).
     * Therefore: x J/(kg·K) × (1 BTU/(lbm·°R)) / 4186.8 J/(kg·K).
     *
     * @param jkgK specific heat in J/(kg·K)
     * @return specific heat in BTU/(lbm·°R)
     */
    public static double jPerKgKToBtuPerLbmR(double jkgK) {
        // 4186.8 J/(kg·K) = 1 BTU/(lbm·°R)  →  divide by 4186.8
        return jkgK * KG_PER_LBM / (JOULES_PER_BTU * 1.8);
    }

    /**
     * Converts BTU/(lbm·°R) to J/(kg·K).
     *
     * @param btulbmR specific heat in BTU/(lbm·°R)
     * @return specific heat in J/(kg·K)
     */
    public static double btuPerLbmRToJPerKgK(double btulbmR) {
        // 1 BTU/(lbm·°R) = 4186.8 J/(kg·K)
        return btulbmR * JOULES_PER_BTU * 1.8 / KG_PER_LBM;
    }

    // =========================================================================
    // THERMAL CONDUCTIVITY
    // =========================================================================

    /**
     * Converts W/(m·K) to BTU/(h·ft·°F).
     *
     * @param wMK thermal conductivity in W/(m·K)
     * @return thermal conductivity in BTU/(h·ft·°F)
     */
    public static double wPerMKToBtuPerHrFtF(double wMK) {
        return wMK / W_PER_MK_PER_BTU_HRFTF;
    }

    /**
     * Converts BTU/(h·ft·°F) to W/(m·K).
     *
     * @param btuhftF thermal conductivity in BTU/(h·ft·°F)
     * @return thermal conductivity in W/(m·K)
     */
    public static double btuPerHrFtFToWPerMK(double btuhftF) {
        return btuhftF * W_PER_MK_PER_BTU_HRFTF;
    }

    // =========================================================================
    // DYNAMIC VISCOSITY
    // =========================================================================

    /**
     * Converts Pa·s to lb/(ft·s).
     *
     * @param pas dynamic viscosity in Pa·s
     * @return dynamic viscosity in lb/(ft·s)
     */
    public static double paSToLbPerFtSec(double pas)      { return pas / PAS_PER_LB_FT_S; }

    /**
     * Converts lb/(ft·s) to Pa·s.
     *
     * @param lbfts dynamic viscosity in lb/(ft·s)
     * @return dynamic viscosity in Pa·s
     */
    public static double lbPerFtSecToPaS(double lbfts)    { return lbfts * PAS_PER_LB_FT_S; }

    // =========================================================================
    // KINEMATIC VISCOSITY
    // =========================================================================

    /**
     * Converts m²/s to ft²/s.
     *
     * @param m2s kinematic viscosity in m²/s
     * @return kinematic viscosity in ft²/s
     */
    public static double m2PerSecToFt2PerSec(double m2s) {
        return m2s / M2_PER_S_PER_FT2_PER_S;
    }

    /**
     * Converts ft²/s to m²/s.
     *
     * @param ft2s kinematic viscosity in ft²/s
     * @return kinematic viscosity in m²/s
     */
    public static double ft2PerSecToM2PerSec(double ft2s) {
        return ft2s * M2_PER_S_PER_FT2_PER_S;
    }

    // =========================================================================
    // GAS CONSTANT
    // =========================================================================

    /**
     * Converts J/(kg·K) to ft·lbf/(slug·°R).
     *
     * <p>Derivation: 1 ft·lbf/(slug·°R) = 1 ft²/(s²·°R)
     * = 0.3048² m²/s² / (5/9 K) = 0.3048² × 1.8 J/(kg·K) = 0.16723 J/(kg·K).
     * Therefore x J/(kg·K) = x / 0.16723 ft·lbf/(slug·°R).
     *
     * @param jkgK specific gas constant in J/(kg·K)
     * @return specific gas constant in ft·lbf/(slug·°R)
     */
    public static double jPerKgKToFtLbfPerSlugR(double jkgK) {
        // 1 ft·lbf/(slug·°R) = METERS_PER_FOOT² × 1.8 J/(kg·K)
        return jkgK / (METERS_PER_FOOT * METERS_PER_FOOT * 1.8);
    }

    /**
     * Converts J/(kg·K) to ft·lbf/(lbm·°R).
     *
     * @param jkgK specific gas constant in J/(kg·K)
     * @return specific gas constant in ft·lbf/(lbm·°R)
     */
    public static double jPerKgKToFtLbfPerLbmR(double jkgK) {
        // 1 ft·lbf/(lbm·°R) = 5.380320456 J/(kg·K)
        return jkgK / 5.380320456;
    }

    /**
     * Converts ft·lbf/(lbm·°R) to J/(kg·K).
     *
     * @param ftlbflbmR specific gas constant in ft·lbf/(lbm·°R)
     * @return specific gas constant in J/(kg·K)
     */
    public static double ftLbfPerLbmRToJPerKgK(double ftlbflbmR) {
        return ftlbflbmR * 5.380320456;
    }
}
