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

/**
 * End-burning grain geometry.
 *
 * <p>An end-burning grain is a solid cylinder that burns axially from one flat
 * face only.  All cylindrical surfaces are inhibited.  The burning surface is
 * constant throughout the burn, making this the simplest and most inherently
 * neutral grain configuration:
 * <pre>
 *   A_b = π/4 · D²   (constant, independent of web burned)
 * </pre>
 * Web thickness equals the grain length: {@code w = L}.
 *
 * <p>End-burning grains offer the highest propellant volumetric loading of any
 * configuration and are commonly used in gas generators, igniter charges, and
 * low-thrust sustainer motors.  Their chief limitation is low Kn (burning area
 * to throat area ratio) compared with a port-burning grain of the same diameter,
 * requiring a larger throat to achieve a useful chamber pressure.
 *
 * <p>References: Sutton &amp; Biblarz, <em>Rocket Propulsion Elements</em>,
 * 9th ed., §12.3; Humble, Henry &amp; Larson, <em>Space Propulsion Analysis and
 * Design</em>, §4.
 */
public final class EndBurningGrain implements GrainGeometry {

    private final double diameter;  // D [m]
    private final double length;    // L [m]

    /**
     * Creates an end-burning grain.
     *
     * @param diameter grain (and burning-face) diameter D [m]; must be positive
     * @param length   grain length L [m]; must be positive
     * @throws IllegalArgumentException if either parameter is non-positive
     */
    public EndBurningGrain(double diameter, double length) {
        if (diameter <= 0) {
            throw new IllegalArgumentException(
                    "Diameter must be positive; got " + diameter);
        }
        if (length <= 0) {
            throw new IllegalArgumentException(
                    "Length must be positive; got " + length);
        }
        this.diameter = diameter;
        this.length   = length;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The burning area is constant: {@code A_b = π/4 · D²},
     * independent of {@code webBurned}.
     */
    @Override
    public double burningArea(double webBurned) {
        return 0.25 * Math.PI * diameter * diameter;
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code V_p = π/4 · D² · L}
     */
    @Override
    public double propellantVolume() {
        return 0.25 * Math.PI * diameter * diameter * length;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The web thickness equals the grain length: {@code w = L}.
     */
    @Override
    public double webThickness() {
        return length;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String name() {
        return String.format("END_BURNING(D=%.0fmm, L=%.0fmm)",
                diameter * 1000,
                length   * 1000);
    }

    /**
     * Returns the grain diameter [m].
     *
     * @return diameter [m]
     */
    public double diameter() { return diameter; }

    /**
     * Returns the grain length [m].
     *
     * @return length [m]
     */
    public double length() { return length; }
}
