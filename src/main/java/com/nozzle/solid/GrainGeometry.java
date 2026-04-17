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
 * Defines the geometry of a solid-propellant grain by supplying burning surface
 * area and propellant volume as functions of web regression depth.
 *
 * <p>The <em>web</em> is the minimum propellant thickness in the burn direction.
 * When {@code webBurned ≥ webThickness()} the propellant is fully consumed and
 * the motor has reached burnout.
 *
 * <p>All lengths are in metres and all areas in m².  Implementations must be
 * pure functions of {@code webBurned} — identical inputs must always produce
 * identical outputs.
 *
 * <p>Burn neutrality terminology:
 * <ul>
 *   <li><b>Neutral</b> — {@code burningArea(y) ≈ const} throughout the burn</li>
 *   <li><b>Progressive</b> — {@code burningArea(y)} increases with {@code y}</li>
 *   <li><b>Regressive</b> — {@code burningArea(y)} decreases with {@code y}</li>
 * </ul>
 */
public interface GrainGeometry {

    /**
     * Returns the total exposed burning surface area at regression depth {@code y}.
     *
     * @param webBurned web regression depth [m]; must be in [0, {@link #webThickness()}]
     * @return burning surface area A_b [m²]
     */
    double burningArea(double webBurned);

    /**
     * Returns the initial propellant volume (web burned = 0).
     *
     * @return initial propellant volume V_p [m³]
     */
    double propellantVolume();

    /**
     * Returns the total burnout web thickness — the maximum regression depth
     * before motor burnout.
     *
     * @return web thickness w [m]
     */
    double webThickness();

    /**
     * Returns a short human-readable description of the grain configuration.
     *
     * @return grain description string
     */
    String name();

    /**
     * Returns {@code true} when the grain web has been fully consumed.
     *
     * @param webBurned web regression depth [m]
     * @return {@code true} if {@code webBurned ≥ webThickness()}
     */
    default boolean isBurnedOut(double webBurned) {
        return webBurned >= webThickness();
    }
}
