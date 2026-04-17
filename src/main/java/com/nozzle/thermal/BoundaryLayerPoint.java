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

package com.nozzle.thermal;

/**
 * Immutable snapshot of boundary-layer quantities at a single wall point.
 *
 * @param x                     Axial position in metres (from contour origin)
 * @param y                     Radial position (wall radius) in metres
 * @param runningLength         Integrated wall arc length from the first contour
 *                              point to this point, used as the reference length
 *                              for boundary-layer scaling (metres)
 * @param reynoldsNumber        Local running-length Reynolds number
 *                              {@code Re = ρ · V · s / μ} (dimensionless)
 * @param thickness             Full boundary-layer thickness δ in metres, including
 *                              the Van Driest compressibility correction
 * @param displacementThickness Displacement thickness δ* in metres
 *                              (δ/8 turbulent, 1.72·s/√Re laminar)
 * @param momentumThickness     Momentum thickness θ in metres
 *                              (7/72·δ turbulent, 0.664·s/√Re laminar)
 * @param skinFrictionCoeff     Local skin-friction coefficient cf (dimensionless),
 *                              corrected for the wall-to-adiabatic-wall temperature ratio
 * @param isTurbulent           {@code true} if the boundary layer is turbulent at this point
 *                              (either forced turbulent or Re exceeds transition threshold)
 * @param mach                  Local Mach number used for the compressibility correction
 */
public record BoundaryLayerPoint(
        double x,
        double y,
        double runningLength,
        double reynoldsNumber,
        double thickness,
        double displacementThickness,
        double momentumThickness,
        double skinFrictionCoeff,
        boolean isTurbulent,
        double mach
) {
    /**
     * Returns the incompressible shape factor {@code H = δ* / θ}.
     * Typical values: ~1.3 turbulent, ~2.6 laminar.
     *
     * @return Shape factor; returns {@code 1.4} if momentum thickness is zero
     */
    public double shapeFactor() {
        return momentumThickness > 0 ? displacementThickness / momentumThickness : 1.4;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public String toString() {
        return String.format("BL[x=%.4f, δ*=%.2e, cf=%.4f, %s]",
                x, displacementThickness, skinFrictionCoeff,
                isTurbulent ? "turbulent" : "laminar");
    }
}
