package com.nozzle.moc;

/**
 * Altitude-performance summary computed by
 * {@link AerospikeNozzle#calculateAltitudePerformance(double[])}.
 *
 * <p>All arrays have the same length as the input {@code ambientPressures} array.
 *
 * @param ambientPressures Input ambient pressures in Pa (copy)
 * @param aerospikeCf      Aerospike thrust coefficient at each altitude
 * @param bellNozzleCf     Reference bell-nozzle thrust coefficient at each altitude;
 *                         shows the advantage of altitude compensation
 * @param aerospikeIsp     Aerospike specific impulse (s) at each altitude
 */
public record AltitudePerformance(
        double[] ambientPressures,
        double[] aerospikeCf,
        double[] bellNozzleCf,
        double[] aerospikeIsp
) {
    /**
     * Returns the index of the ambient-pressure entry for which the aerospike
     * advantage over the bell nozzle is greatest.
     *
     * @return Index into the arrays at which {@code aerospikeCf − bellNozzleCf}
     *         is maximized
     */
    public int indexOfMaxAltitudeAdvantage() {
        int best = 0;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < aerospikeCf.length; i++) {
            double advantage = aerospikeCf[i] - bellNozzleCf[i];
            if (advantage > max) {
                max = advantage;
                best = i;
            }
        }
        return best;
    }

    /**
     * Returns the average aerospike Cf advantage over the bell nozzle across
     * all provided altitudes.
     *
     * @return Mean {@code (aerospikeCf − bellNozzleCf)}, always ≥ 0 for a
     *         properly designed Aerospike
     */
    public double averageAltitudeAdvantage() {
        double sum = 0.0;
        for (int i = 0; i < aerospikeCf.length; i++) {
            sum += aerospikeCf[i] - bellNozzleCf[i];
        }
        return sum / aerospikeCf.length;
    }
}
