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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link Units} conversion methods.
 *
 * <p>Strategy:
 * <ul>
 *   <li>Round-trip tests verify that {@code from(to(x)) ≈ x} to within floating-point error.</li>
 *   <li>Known-value tests check specific reference points against published standards.</li>
 *   <li>Parameterized tests cover multiple magnitudes for each category.</li>
 * </ul>
 */
class Units_UT {

    private static final double EPSILON = 1e-9;
    private static final double LOOSE   = 1e-4; // for derived/compound conversions

    // =========================================================================
    // LENGTH
    // =========================================================================

    @Test
    void metersToFeet_oneFootExact() {
        // 1 ft = 0.3048 m (exact)
        assertThat(Units.metersToFeet(0.3048)).isCloseTo(1.0, within(EPSILON));
    }

    @Test
    void feetToMeters_roundTrip() {
        double ft = 42.5;
        assertThat(Units.feetToMeters(Units.metersToFeet(ft))).isCloseTo(ft, within(EPSILON));
    }

    @Test
    void metersToInches_oneInchExact() {
        // 1 in = 0.0254 m (exact)
        assertThat(Units.metersToInches(0.0254)).isCloseTo(1.0, within(EPSILON));
    }

    @Test
    void inchesToMeters_roundTrip() {
        double in = 24.0;
        assertThat(Units.inchesToMeters(Units.metersToInches(in))).isCloseTo(in, within(EPSILON));
    }

    @Test
    void metersToMillimeters_scale() {
        assertThat(Units.metersToMillimeters(0.05)).isCloseTo(50.0, within(EPSILON));
    }

    @Test
    void millimetersToMeters_roundTrip() {
        double mm = 123.456;
        assertThat(Units.millimetersToMeters(Units.metersToMillimeters(mm))).isCloseTo(mm, within(EPSILON));
    }

    @Test
    void metersToCentimeters_scale() {
        assertThat(Units.metersToCentimeters(1.0)).isCloseTo(100.0, within(EPSILON));
    }

    @Test
    void centimetersToMeters_oneHundredCm() {
        assertThat(Units.centimetersToMeters(100.0)).isCloseTo(1.0, within(EPSILON));
    }

    @Test
    void centimetersToMeters_roundTrip() {
        double cm = 47.3;
        assertThat(Units.metersToCentimeters(Units.centimetersToMeters(cm))).isCloseTo(cm, within(EPSILON));
    }

    // =========================================================================
    // PRESSURE
    // =========================================================================

    @Test
    void pascalsToPsi_standardAtm() {
        // 1 atm = 101325 Pa = 14.6959 psi (approx)
        assertThat(Units.pascalsToPsi(101325.0)).isCloseTo(14.6959, within(1e-3));
    }

    @Test
    void psiToPascals_roundTrip() {
        double psi = 1000.0;
        assertThat(Units.psiToPascals(Units.pascalsToPsi(psi))).isCloseTo(psi, within(EPSILON));
    }

    @Test
    void pascalsToAtm_oneAtm() {
        assertThat(Units.pascalsToAtm(101325.0)).isCloseTo(1.0, within(EPSILON));
    }

    @Test
    void atmToPascals_roundTrip() {
        assertThat(Units.atmToPascals(Units.pascalsToAtm(7e6))).isCloseTo(7e6, within(1.0));
    }

    @Test
    void pascalsToBar_oneBar() {
        assertThat(Units.pascalsToBar(1.0e5)).isCloseTo(1.0, within(EPSILON));
    }

    @Test
    void pascalsToMegapascals_scale() {
        assertThat(Units.pascalsToMegapascals(7e6)).isCloseTo(7.0, within(EPSILON));
    }

    @Test
    void megapascalsToPascals_roundTrip() {
        double mpa = 3.5;
        assertThat(Units.megapascalsToPascals(Units.pascalsToMegapascals(mpa))).isCloseTo(mpa, within(EPSILON));
    }

    @Test
    void pascalsToKilopascals_scale() {
        assertThat(Units.pascalsToKilopascals(1e4)).isCloseTo(10.0, within(EPSILON));
    }

    @Test
    void kilopascalsToPascals_scale() {
        assertThat(Units.kilopascalsToPascals(7.0)).isCloseTo(7000.0, within(EPSILON));
    }

    @Test
    void kilopascalsToPascals_roundTrip() {
        double kpa = 68.95;
        assertThat(Units.pascalsToKilopascals(Units.kilopascalsToPascals(kpa))).isCloseTo(kpa, within(EPSILON));
    }

    @Test
    void barToPascals_oneBar() {
        assertThat(Units.barToPascals(1.0)).isCloseTo(1.0e5, within(EPSILON));
    }

    @Test
    void barToPascals_roundTrip() {
        double bar = 70.0;
        assertThat(Units.pascalsToBar(Units.barToPascals(bar))).isCloseTo(bar, within(EPSILON));
    }

    // =========================================================================
    // TEMPERATURE
    // =========================================================================

    @Test
    void kelvinToCelsius_absoluteZero() {
        assertThat(Units.kelvinToCelsius(0.0)).isCloseTo(-273.15, within(EPSILON));
    }

    @Test
    void celsiusToKelvin_freezingPoint() {
        assertThat(Units.celsiusToKelvin(0.0)).isCloseTo(273.15, within(EPSILON));
    }

    @Test
    void kelvinToFahrenheit_boilingPoint() {
        // 100°C = 373.15 K = 212°F
        assertThat(Units.kelvinToFahrenheit(373.15)).isCloseTo(212.0, within(1e-3));
    }

    @Test
    void fahrenheitToKelvin_freezingPoint() {
        // 32°F = 273.15 K
        assertThat(Units.fahrenheitToKelvin(32.0)).isCloseTo(273.15, within(1e-3));
    }

    @Test
    void kelvinToRankine_roomTemp() {
        // 300 K = 540 °R
        assertThat(Units.kelvinToRankine(300.0)).isCloseTo(540.0, within(EPSILON));
    }

    @Test
    void rankineToKelvin_roundTrip() {
        double r = 6300.0; // ~3500 K combustion temperature in Rankine
        assertThat(Units.rankineToKelvin(Units.kelvinToRankine(r))).isCloseTo(r, within(EPSILON));
    }

    @Test
    void celsiusToFahrenheit_bodyTemp() {
        // 37°C = 98.6°F
        assertThat(Units.celsiusToFahrenheit(37.0)).isCloseTo(98.6, within(1e-3));
    }

    @Test
    void fahrenheitToCelsius_roundTrip() {
        double f = 77.0;
        assertThat(Units.celsiusToFahrenheit(Units.fahrenheitToCelsius(f))).isCloseTo(f, within(EPSILON));
    }

    // =========================================================================
    // FORCE
    // =========================================================================

    @Test
    void newtonsToLbf_oneKilogramForce() {
        // 1 kgf = 9.80665 N = 2.20462 lbf
        assertThat(Units.newtonsToLbf(9.80665)).isCloseTo(2.20462, within(1e-4));
    }

    @Test
    void lbfToNewtons_roundTrip() {
        double lbf = 500.0;
        assertThat(Units.lbfToNewtons(Units.newtonsToLbf(lbf))).isCloseTo(lbf, within(EPSILON));
    }

    @Test
    void newtonsToKilonewtons_scale() {
        assertThat(Units.newtonsToKilonewtons(5000.0)).isCloseTo(5.0, within(EPSILON));
    }

    @Test
    void kilonewtonsToNewtons_roundTrip() {
        double kn = 22.0;
        assertThat(Units.kilonewtonsToNewtons(Units.newtonsToKilonewtons(kn))).isCloseTo(kn, within(EPSILON));
    }

    @Test
    void newtonsToKilogramForce_gravity() {
        // 9.80665 N = 1 kgf
        assertThat(Units.newtonsToKilogramForce(9.80665)).isCloseTo(1.0, within(EPSILON));
    }

    @Test
    void kilogramForceToNewtons_oneKgf() {
        // 1 kgf = g₀ = 9.80665 N (exact)
        assertThat(Units.kilogramForceToNewtons(1.0)).isCloseTo(9.80665, within(EPSILON));
    }

    @Test
    void kilogramForceToNewtons_roundTrip() {
        double kgf = 250.0;
        assertThat(Units.newtonsToKilogramForce(Units.kilogramForceToNewtons(kgf))).isCloseTo(kgf, within(EPSILON));
    }

    // =========================================================================
    // MASS
    // =========================================================================

    @Test
    void kilogramsToPounds_oneKg() {
        // 1 kg = 2.20462 lbm (approx)
        assertThat(Units.kilogramsToPounds(1.0)).isCloseTo(2.20462, within(1e-4));
    }

    @Test
    void poundsToKilograms_roundTrip() {
        double lbm = 150.0;
        assertThat(Units.poundsToKilograms(Units.kilogramsToPounds(lbm))).isCloseTo(lbm, within(EPSILON));
    }

    @Test
    void kilogramsToSlugs_oneSlug() {
        // 1 slug = 14.5939 kg
        assertThat(Units.kilogramsToSlugs(14.593903)).isCloseTo(1.0, within(1e-5));
    }

    @Test
    void slugsToKilograms_oneSlug() {
        // 1 slug = 14.593902937 kg (exact definition)
        assertThat(Units.slugsToKilograms(1.0)).isCloseTo(14.593902937, within(1e-6));
    }

    @Test
    void slugsToKilograms_roundTrip() {
        double slug = 3.5;
        assertThat(Units.kilogramsToSlugs(Units.slugsToKilograms(slug))).isCloseTo(slug, within(EPSILON));
    }

    // =========================================================================
    // MASS FLOW RATE
    // =========================================================================

    @Test
    void kgPerSecToLbPerSec_oneKgPerSec() {
        assertThat(Units.kgPerSecToLbPerSec(1.0)).isCloseTo(2.20462, within(1e-4));
    }

    @Test
    void lbPerSecToKgPerSec_roundTrip() {
        double lbps = 100.0;
        assertThat(Units.lbPerSecToKgPerSec(Units.kgPerSecToLbPerSec(lbps))).isCloseTo(lbps, within(EPSILON));
    }

    // =========================================================================
    // VELOCITY
    // =========================================================================

    @Test
    void metersPerSecToFeetPerSec_oneMPS() {
        assertThat(Units.metersPerSecToFeetPerSec(1.0)).isCloseTo(3.28084, within(1e-4));
    }

    @Test
    void feetPerSecToMetersPerSec_roundTrip() {
        double fps = 3000.0;
        assertThat(Units.feetPerSecToMetersPerSec(Units.metersPerSecToFeetPerSec(fps))).isCloseTo(fps, within(EPSILON));
    }

    @Test
    void metersPerSecToKnots_speedOfSound() {
        // Speed of sound at sea level ≈ 340 m/s ≈ 661.5 knots
        assertThat(Units.metersPerSecToKnots(340.0)).isCloseTo(661.0, within(1.0));
    }

    @Test
    void knotsToMetersPerSec_roundTrip() {
        double kt = 500.0;
        assertThat(Units.knotsToMetersPerSec(Units.metersPerSecToKnots(kt))).isCloseTo(kt, within(EPSILON));
    }

    @Test
    void metersPerSecToFeetPerSec_referenceValue() {
        // 1 m/s = 1/0.3048 ft/s ≈ 3.28084 ft/s
        assertThat(Units.metersPerSecToFeetPerSec(0.3048)).isCloseTo(1.0, within(EPSILON));
    }

    @Test
    void feetPerSecToMetersPerSec_referenceValue() {
        // 1 ft/s = 0.3048 m/s (exact)
        assertThat(Units.feetPerSecToMetersPerSec(1.0)).isCloseTo(0.3048, within(EPSILON));
    }

    @Test
    void knotsToMetersPerSec_oneKnot() {
        // 1 knot = 1852/3600 m/s = 0.514444... m/s
        assertThat(Units.knotsToMetersPerSec(1.0)).isCloseTo(1852.0 / 3600.0, within(1e-6));
    }

    // =========================================================================
    // AREA
    // =========================================================================

    @Test
    void squareMetersToSquareInches_oneSquareMeter() {
        assertThat(Units.squareMetersToSquareInches(1.0)).isCloseTo(1550.0031, within(1e-3));
    }

    @Test
    void squareInchesToSquareMeters_roundTrip() {
        double in2 = 78.54; // ~1 inch radius circle
        assertThat(Units.squareInchesToSquareMeters(Units.squareMetersToSquareInches(in2))).isCloseTo(in2, within(EPSILON));
    }

    @Test
    void squareMetersToSquareFeet_oneSquareMeter() {
        assertThat(Units.squareMetersToSquareFeet(1.0)).isCloseTo(10.7639, within(1e-3));
    }

    @Test
    void squareFeetToSquareMeters_roundTrip() {
        double ft2 = 100.0;
        assertThat(Units.squareFeetToSquareMeters(Units.squareMetersToSquareFeet(ft2))).isCloseTo(ft2, within(EPSILON));
    }

    @Test
    void squareMetersToSquareCentimeters_scale() {
        // 1 m² = 10,000 cm² (exact)
        assertThat(Units.squareMetersToSquareCentimeters(1.0)).isCloseTo(1.0e4, within(EPSILON));
    }

    @Test
    void squareCentimetersToSquareMeters_scale() {
        // 10,000 cm² = 1 m²
        assertThat(Units.squareCentimetersToSquareMeters(1.0e4)).isCloseTo(1.0, within(EPSILON));
    }

    @Test
    void squareCentimetersToSquareMeters_roundTrip() {
        double cm2 = 314.159; // ~1 cm radius circle area
        assertThat(Units.squareMetersToSquareCentimeters(Units.squareCentimetersToSquareMeters(cm2)))
                .isCloseTo(cm2, within(EPSILON));
    }

    @Test
    void squareInchesToSquareMeters_referenceValue() {
        // 1 in² = 0.0254² m² = 6.4516e-4 m² (exact)
        assertThat(Units.squareInchesToSquareMeters(1.0)).isCloseTo(6.4516e-4, within(1e-10));
    }

    @Test
    void squareFeetToSquareMeters_referenceValue() {
        // 1 ft² = 0.3048² m² = 0.09290304 m² (exact)
        assertThat(Units.squareFeetToSquareMeters(1.0)).isCloseTo(0.09290304, within(EPSILON));
    }

    // =========================================================================
    // ANGLE
    // =========================================================================

    @Test
    void radiansToDegrees_piIsOneEighty() {
        assertThat(Units.radiansToDegrees(Math.PI)).isCloseTo(180.0, within(EPSILON));
    }

    @Test
    void degreesToRadians_roundTrip() {
        double deg = 37.5;
        assertThat(Units.radiansToDegrees(Units.degreesToRadians(deg))).isCloseTo(deg, within(EPSILON));
    }

    // =========================================================================
    // DENSITY
    // =========================================================================

    @Test
    void kgPerCubicMeterToLbPerCubicFoot_air() {
        // Air at sea level ≈ 1.225 kg/m³ ≈ 0.0765 lb/ft³
        assertThat(Units.kgPerCubicMeterToLbPerCubicFoot(1.225)).isCloseTo(0.0765, within(1e-3));
    }

    @Test
    void lbPerCubicFootToKgPerCubicMeter_roundTrip() {
        double lbft3 = 0.075;
        assertThat(Units.lbPerCubicFootToKgPerCubicMeter(
                Units.kgPerCubicMeterToLbPerCubicFoot(lbft3))).isCloseTo(lbft3, within(EPSILON));
    }

    @Test
    void lbPerCubicFootToKgPerCubicMeter_referenceValue() {
        // Water at 4°C: 62.428 lb/ft³ ≈ 1000.0 kg/m³
        assertThat(Units.lbPerCubicFootToKgPerCubicMeter(62.428)).isCloseTo(1000.0, within(0.1));
    }

    @Test
    void kgPerCubicMeterToLbPerCubicFoot_referenceValue() {
        // 1 lb/ft³ = 16.018463 kg/m³
        assertThat(Units.kgPerCubicMeterToLbPerCubicFoot(16.018463)).isCloseTo(1.0, within(1e-5));
    }

    // =========================================================================
    // HEAT FLUX
    // =========================================================================

    @Test
    void wattsPerM2ToBtuPerFt2PerSec_referenceValue() {
        // 11356.5 W/m² = 1 BTU/ft²/s (definition)
        assertThat(Units.wattsPerM2ToBtuPerFt2PerSec(11356.5)).isCloseTo(1.0, within(1e-3));
    }

    @Test
    void btuPerFt2PerSecToWattsPerM2_roundTrip() {
        double val = 50.0;
        assertThat(Units.btuPerFt2PerSecToWattsPerM2(Units.wattsPerM2ToBtuPerFt2PerSec(val)))
                .isCloseTo(val, within(EPSILON));
    }

    @Test
    void btuPerFt2PerSecToWattsPerM2_referenceValue() {
        // 1 BTU/(ft²·s) = 11356.5 W/m² (definition)
        assertThat(Units.btuPerFt2PerSecToWattsPerM2(1.0)).isCloseTo(11356.5, within(0.1));
    }

    // Heat transfer coefficient (film coefficient): W/(m²·K) ↔ BTU/(h·ft²·°F)
    // Reference: 1 BTU/(h·ft²·°F) = 1055.056 J / (3600 s × 0.3048² m² × 5/9 K)
    //          = 1055.056 / (3600 × 0.09290304 × 0.5556) = 5.6782633 W/(m²·K)

    @Test
    void wPerM2KToBtuPerHrFt2F_referenceValue() {
        // 5.6782633 W/(m²·K) = 1 BTU/(h·ft²·°F)
        assertThat(Units.wPerM2KToBtuPerHrFt2F(5.6782633)).isCloseTo(1.0, within(1e-4));
    }

    @Test
    void wPerM2KToBtuPerHrFt2F_typicalFilmCoefficient() {
        // Typical convective h ≈ 100 W/(m²·K) ≈ 17.61 BTU/(h·ft²·°F)
        assertThat(Units.wPerM2KToBtuPerHrFt2F(100.0)).isCloseTo(17.61, within(0.05));
    }

    @Test
    void btuPerHrFt2FToWPerM2K_referenceValue() {
        // 1 BTU/(h·ft²·°F) = 5.6782633 W/(m²·K)
        assertThat(Units.btuPerHrFt2FToWPerM2K(1.0)).isCloseTo(5.6782633, within(1e-4));
    }

    @Test
    void btuPerHrFt2FToWPerM2K_roundTrip() {
        double val = 25.0;
        assertThat(Units.wPerM2KToBtuPerHrFt2F(Units.btuPerHrFt2FToWPerM2K(val)))
                .isCloseTo(val, within(LOOSE));
    }

    // =========================================================================
    // ENERGY
    // =========================================================================

    @Test
    void joulesToBtu_oneBtu() {
        // 1 BTU ≈ 1055.06 J
        assertThat(Units.joulesToBtu(1055.056)).isCloseTo(1.0, within(1e-3));
    }

    @Test
    void btuToJoules_roundTrip() {
        double btu = 100.0;
        assertThat(Units.btuToJoules(Units.joulesToBtu(btu))).isCloseTo(btu, within(EPSILON));
    }

    @Test
    void joulesToCalories_oneCalorie() {
        // 1 cal = 4.184 J (thermochemical)
        assertThat(Units.joulesToCalories(4.184)).isCloseTo(1.0, within(EPSILON));
    }

    @Test
    void caloriesToJoules_oneCalorie() {
        // 1 cal = 4.184 J (exact, thermochemical)
        assertThat(Units.caloriesToJoules(1.0)).isCloseTo(4.184, within(EPSILON));
    }

    @Test
    void caloriesToJoules_roundTrip() {
        double cal = 1000.0; // 1 kcal
        assertThat(Units.joulesToCalories(Units.caloriesToJoules(cal))).isCloseTo(cal, within(EPSILON));
    }

    @Test
    void btuToJoules_referenceValue() {
        // 1 BTU = 1055.056 J
        assertThat(Units.btuToJoules(1.0)).isCloseTo(1055.05585262, within(1e-4));
    }

    // =========================================================================
    // SPECIFIC ENTHALPY / HEAT
    // =========================================================================

    @Test
    void jPerKgToBtuPerLbm_referenceValue() {
        // 1 BTU/lbm = 1055.056 J / 0.45359237 kg = 2326.0 J/kg
        assertThat(Units.jPerKgToBtuPerLbm(2326.0)).isCloseTo(1.0, within(1e-3));
    }

    @Test
    void btuPerLbmToJPerKg_referenceValue() {
        // 1 BTU/lbm = 2326.0 J/kg
        assertThat(Units.btuPerLbmToJPerKg(1.0)).isCloseTo(2326.0, within(0.1));
    }

    @ParameterizedTest
    @CsvSource({
        "1000.0",
        "2326.0",
        "4186.8"
    })
    void jPerKgToBtuPerLbm_roundTrip(double jkg) {
        assertThat(Units.btuPerLbmToJPerKg(Units.jPerKgToBtuPerLbm(jkg))).isCloseTo(jkg, within(LOOSE));
    }

    @Test
    void jPerKgKToBtuPerLbmR_referenceValue() {
        // Cp of air ≈ 1005 J/(kg·K) ≈ 0.24 BTU/(lbm·°R)
        assertThat(Units.jPerKgKToBtuPerLbmR(1005.0)).isCloseTo(0.24, within(1e-2));
    }

    @Test
    void btuPerLbmRToJPerKgK_roundTrip() {
        double btu = 0.5;
        assertThat(Units.jPerKgKToBtuPerLbmR(Units.btuPerLbmRToJPerKgK(btu))).isCloseTo(btu, within(LOOSE));
    }

    // =========================================================================
    // THERMAL CONDUCTIVITY
    // =========================================================================

    @Test
    void wPerMKToBtuPerHrFtF_referenceValue() {
        // Stainless steel ≈ 16 W/(m·K) ≈ 9.24 BTU/(h·ft·°F)
        assertThat(Units.wPerMKToBtuPerHrFtF(16.0)).isCloseTo(9.24, within(0.1));
    }

    @Test
    void wPerMKToBtuPerHrFtF_oneUnit() {
        // 1 BTU/(h·ft·°F) = 1.730735 W/(m·K)
        assertThat(Units.wPerMKToBtuPerHrFtF(1.730735)).isCloseTo(1.0, within(1e-4));
    }

    @Test
    void btuPerHrFtFToWPerMK_referenceValue() {
        // 1 BTU/(h·ft·°F) = 1.730735 W/(m·K)
        assertThat(Units.btuPerHrFtFToWPerMK(1.0)).isCloseTo(1.730735, within(1e-4));
    }

    @Test
    void btuPerHrFtFToWPerMK_roundTrip() {
        double val = 12.5;
        assertThat(Units.btuPerHrFtFToWPerMK(Units.wPerMKToBtuPerHrFtF(val))).isCloseTo(val, within(LOOSE));
    }

    // =========================================================================
    // DYNAMIC VISCOSITY
    // =========================================================================

    @Test
    void paSToLbPerFtSec_referenceValue() {
        // 1 Pa·s = 1 kg/(m·s) = 1/1.4881639 lb/(ft·s) ≈ 0.67197 lb/(ft·s)
        assertThat(Units.paSToLbPerFtSec(1.0)).isCloseTo(1.0 / 1.4881639, within(1e-6));
    }

    @Test
    void paSToLbPerFtSec_roundTrip() {
        double pas = 1.8e-5; // air viscosity
        assertThat(Units.lbPerFtSecToPaS(Units.paSToLbPerFtSec(pas))).isCloseTo(pas, within(EPSILON));
    }

    @Test
    void lbPerFtSecToPaS_referenceValue() {
        // 1 lb/(ft·s) = 1.4881639 Pa·s
        assertThat(Units.lbPerFtSecToPaS(1.0)).isCloseTo(1.4881639, within(1e-6));
    }

    // =========================================================================
    // KINEMATIC VISCOSITY
    // =========================================================================

    @Test
    void m2PerSecToFt2PerSec_referenceValue() {
        // 1 m²/s = 1/0.3048² ft²/s ≈ 10.7639 ft²/s
        assertThat(Units.m2PerSecToFt2PerSec(1.0)).isCloseTo(1.0 / (0.3048 * 0.3048), within(1e-6));
    }

    @Test
    void m2PerSecToFt2PerSec_roundTrip() {
        double m2s = 1.5e-5;
        assertThat(Units.ft2PerSecToM2PerSec(Units.m2PerSecToFt2PerSec(m2s))).isCloseTo(m2s, within(EPSILON));
    }

    @Test
    void ft2PerSecToM2PerSec_referenceValue() {
        // 1 ft²/s = 0.3048² m²/s = 0.09290304 m²/s (exact)
        assertThat(Units.ft2PerSecToM2PerSec(1.0)).isCloseTo(0.09290304, within(EPSILON));
    }

    // =========================================================================
    // GAS CONSTANT
    // =========================================================================

    @Test
    void jPerKgKToFtLbfPerLbmR_roundTrip() {
        double jkgK = 287.05; // air specific gas constant
        assertThat(Units.ftLbfPerLbmRToJPerKgK(Units.jPerKgKToFtLbfPerLbmR(jkgK)))
                .isCloseTo(jkgK, within(LOOSE));
    }

    @Test
    void jPerKgKToFtLbfPerLbmR_air() {
        // Air: R = 287.05 J/(kg·K) ≈ 53.35 ft·lbf/(lbm·°R)
        assertThat(Units.jPerKgKToFtLbfPerLbmR(287.05)).isCloseTo(53.35, within(0.1));
    }

    @Test
    void ftLbfPerLbmRToJPerKgK_referenceValue() {
        // 1 ft·lbf/(lbm·°R) = 5.380320456 J/(kg·K)
        assertThat(Units.ftLbfPerLbmRToJPerKgK(1.0)).isCloseTo(5.380320456, within(1e-6));
    }

    @Test
    void ftLbfPerLbmRToJPerKgK_roundTrip() {
        double ftlbf = 53.35;
        assertThat(Units.jPerKgKToFtLbfPerLbmR(Units.ftLbfPerLbmRToJPerKgK(ftlbf)))
                .isCloseTo(ftlbf, within(LOOSE));
    }

    // jPerKgKToFtLbfPerSlugR: 1 ft·lbf/(slug·°R) = 1 ft²/(s²·°R)
    //   = 0.3048² m²/s² / (5/9 K) = 0.09290304 / 0.5556 = 0.16723 J/(kg·K)
    //   so 1 J/(kg·K) = 1/0.16723 = 5.9795 ft·lbf/(slug·°R)
    //   Air: R = 287.05 J/(kg·K) → 287.05 × 5.9795 ≈ 1716.5 ft·lbf/(slug·°R)

    @Test
    void jPerKgKToFtLbfPerSlugR_air() {
        // Air specific gas constant: R = 287.05 J/(kg·K) = 1716.5 ft·lbf/(slug·°R)
        assertThat(Units.jPerKgKToFtLbfPerSlugR(287.05)).isCloseTo(1716.5, within(1.0));
    }

    @Test
    void jPerKgKToFtLbfPerSlugR_oneUnit() {
        // 1 ft·lbf/(slug·°R) = 0.3048² × 1.8 J/(kg·K) = 0.16723 J/(kg·K)
        assertThat(Units.jPerKgKToFtLbfPerSlugR(0.3048 * 0.3048 * 1.8)).isCloseTo(1.0, within(1e-6));
    }

    // =========================================================================
    // SYMMETRY — forward–backward consistency for all pairs
    // =========================================================================

    @ParameterizedTest(name = "Length round-trip: {0} m")
    @CsvSource({"0.001", "0.05", "1.0", "100.0"})
    void lengthRoundTrips(double m) {
        assertThat(Units.feetToMeters(Units.metersToFeet(m))).isCloseTo(m, within(EPSILON));
        assertThat(Units.inchesToMeters(Units.metersToInches(m))).isCloseTo(m, within(EPSILON));
        assertThat(Units.millimetersToMeters(Units.metersToMillimeters(m))).isCloseTo(m, within(EPSILON));
    }

    @ParameterizedTest(name = "Pressure round-trip: {0} Pa")
    @CsvSource({"101325.0", "1000000.0", "7000000.0"})
    void pressureRoundTrips(double pa) {
        assertThat(Units.psiToPascals(Units.pascalsToPsi(pa))).isCloseTo(pa, within(pa * EPSILON));
        assertThat(Units.atmToPascals(Units.pascalsToAtm(pa))).isCloseTo(pa, within(pa * EPSILON));
        assertThat(Units.megapascalsToPascals(Units.pascalsToMegapascals(pa))).isCloseTo(pa, within(pa * EPSILON));
    }

    @ParameterizedTest(name = "Temperature round-trip: {0} K")
    @CsvSource({"273.15", "300.0", "3500.0"})
    void temperatureRoundTrips(double k) {
        assertThat(Units.celsiusToKelvin(Units.kelvinToCelsius(k))).isCloseTo(k, within(EPSILON));
        assertThat(Units.fahrenheitToKelvin(Units.kelvinToFahrenheit(k))).isCloseTo(k, within(EPSILON));
        assertThat(Units.rankineToKelvin(Units.kelvinToRankine(k))).isCloseTo(k, within(EPSILON));
    }
}
