package com.nozzle.chemistry;

/**
 * Species thermodynamic data using NASA-7 polynomial coefficients.
 * Coefficients sourced from NASA TP-2002-211556 (McBride, Zehe, Gordon).
 *
 * @param name            Chemical formula or name of the species (e.g. "H2O", "CO2")
 * @param molecularWeight Molar mass in kg/kmol
 * @param lowTempCoeffs   NASA-7 coefficients for the low-temperature range (200–1000 K);
 *                        elements a0–a4 are Cp/R polynomial terms, a5 is the enthalpy
 *                        integration constant (H/RT), a6 is the entropy integration
 *                        constant (S/R)
 * @param highTempCoeffs  NASA-7 coefficients for the high-temperature range (1000–6000 K);
 *                        same layout as {@code lowTempCoeffs}
 */
public record SpeciesData(
        String name,
        double molecularWeight,
        double[] lowTempCoeffs,
        double[] highTempCoeffs
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
        double cpMolar = R_UNIVERSAL * (coeffs[0] + coeffs[1] * temp_deg_k
                + coeffs[2] * temp_deg_k * temp_deg_k
                + coeffs[3] * temp_deg_k * temp_deg_k * temp_deg_k
                + coeffs[4] * temp_deg_k * temp_deg_k * temp_deg_k * temp_deg_k);
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
        double hOverRT = a[0] + a[1] * temp_deg_k / 2 + a[2] * temp_deg_k * temp_deg_k / 3
                + a[3] * temp_deg_k * temp_deg_k * temp_deg_k / 4
                + a[4] * temp_deg_k * temp_deg_k * temp_deg_k * temp_deg_k / 5
                + a[5] / temp_deg_k;
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
        double sOverR = a[0] * Math.log(temp_deg_k) + a[1] * temp_deg_k
                + a[2] * temp_deg_k * temp_deg_k / 2
                + a[3] * temp_deg_k * temp_deg_k * temp_deg_k / 3
                + a[4] * temp_deg_k * temp_deg_k * temp_deg_k * temp_deg_k / 4
                + a[6];
        return R_UNIVERSAL * sOverR / molecularWeight;
    }

    /**
     * Calculates dimensionless standard Gibbs free energy g0/(RT) = H/(RT) − S/R.
     * Computed at the standard reference pressure (1 atm).
     *
     * @param temp_deg_k Temperature in K
     * @return Dimensionless g0/(RT)
     */
    public double gibbsOverRT(double temp_deg_k) {
        double[] a = temp_deg_k < 1000 ? lowTempCoeffs : highTempCoeffs;
        double hOverRT = a[0] + a[1] * temp_deg_k / 2 + a[2] * temp_deg_k * temp_deg_k / 3
                + a[3] * temp_deg_k * temp_deg_k * temp_deg_k / 4
                + a[4] * temp_deg_k * temp_deg_k * temp_deg_k * temp_deg_k / 5
                + a[5] / temp_deg_k;
        double sOverR = a[0] * Math.log(temp_deg_k) + a[1] * temp_deg_k
                + a[2] * temp_deg_k * temp_deg_k / 2
                + a[3] * temp_deg_k * temp_deg_k * temp_deg_k / 3
                + a[4] * temp_deg_k * temp_deg_k * temp_deg_k * temp_deg_k / 4
                + a[6];
        return hOverRT - sOverR;
    }
}
