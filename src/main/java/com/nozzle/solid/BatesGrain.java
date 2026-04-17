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
 * BATES (Ballistic Test and Evaluation System) grain geometry.
 *
 * <p>A BATES grain consists of {@code N} identical cylindrical segments arranged
 * axially within a motor case.  Each segment has:
 * <ul>
 *   <li>An <b>inhibited outer cylindrical surface</b> — bonded to the case liner,
 *       does not burn.</li>
 *   <li>A <b>free inner bore</b> (circular port) — burns radially outward.</li>
 *   <li><b>Two free annular end faces</b> — burn axially inward, reducing the
 *       annular area as the bore expands.</li>
 * </ul>
 *
 * <p>At regression depth {@code y} all free surfaces recede by {@code y}:
 * the bore expands radially and the two end faces advance axially inward.
 * The instantaneous bore diameter is {@code D_i(y) = D_p + 2y} and the
 * effective segment length is {@code L_eff(y) = L − 2y}.  The total burning
 * surface is:
 * <pre>
 *   A_b(y) = N × [π·D_i(y)·(L−2y)  +  π/2·(D_o² − D_i(y)²)]
 *             ↑ lateral bore               ↑ two end-face annuli
 * </pre>
 * This formulation satisfies {@code dV/dy = −A_b} exactly, guaranteeing
 * mass conservation through the burn trajectory.
 *
 * <p>Burnout occurs when the bore reaches the case wall (radial web exhausted)
 * or when the two end faces meet at the segment center (axial web exhausted),
 * whichever comes first:
 * <pre>
 *   w = min((D_o − D_p)/2,  L/2)
 * </pre>
 * The grain is exactly neutral at its endpoints when
 * {@code L = (3·D_o + D_p) / 2}.
 *
 * <p>References: Sutton &amp; Biblarz, <em>Rocket Propulsion Elements</em>,
 * 9th ed., §12.3; Nakka, R., <em>Solid Propellant Grain Design</em>, 2023.
 *
 * @param outerDiameter  outer grain diameter D_o [m]; must be positive
 * @param portDiameter   initial bore (port) diameter D_p [m]; must be in (0, D_o)
 * @param segmentLength  length of each segment L [m]; must be positive
 * @param numSegments    number of identical segments N; must be ≥ 1
 */
public record BatesGrain(
        double outerDiameter,
        double portDiameter,
        double segmentLength,
        int    numSegments
) implements GrainGeometry {

    /**
     * Compact constructor — validates all components.
     *
     * @throws IllegalArgumentException if any component is out of range
     */
    public BatesGrain {
        if (outerDiameter <= 0) {
            throw new IllegalArgumentException(
                    "Outer diameter must be positive; got " + outerDiameter);
        }
        if (portDiameter <= 0 || portDiameter >= outerDiameter) {
            throw new IllegalArgumentException(
                    "Port diameter must be in (0, outerDiameter); got " + portDiameter);
        }
        if (segmentLength <= 0) {
            throw new IllegalArgumentException(
                    "Segment length must be positive; got " + segmentLength);
        }
        if (numSegments < 1) {
            throw new IllegalArgumentException(
                    "Number of segments must be >= 1; got " + numSegments);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses the exact formula:
     * {@code A_b(y) = N × [π·(D_p+2y)·(L−2y) + π/2·(D_o² − (D_p+2y)²)]}.
     * The effective segment length {@code L−2y} accounts for axial recession of
     * both end faces, ensuring {@code dV/dy = −A_b} and exact mass conservation.
     *
     * @param webBurned regression depth y [m]; must be in [0, {@link #webThickness()}]
     */
    @Override
    public double burningArea(double webBurned) {
        double boreDiameter    = portDiameter + 2.0 * webBurned;
        double effectiveLength = segmentLength - 2.0 * webBurned;
        double lateral  = Math.PI * boreDiameter * effectiveLength;
        double endFaces = 0.5 * Math.PI * (outerDiameter * outerDiameter
                                           - boreDiameter * boreDiameter);
        return numSegments * (lateral + endFaces);
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code V_p = N × π/4 × (D_o² − D_p²) × L}
     */
    @Override
    public double propellantVolume() {
        return numSegments * 0.25 * Math.PI
               * (outerDiameter * outerDiameter - portDiameter * portDiameter)
               * segmentLength;
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code w = min((D_o − D_p)/2,  L/2)} — the lesser of the radial
     * (bore-to-wall) web and the axial (face-to-center) web.
     */
    @Override
    public double webThickness() {
        return Math.min(0.5 * (outerDiameter - portDiameter), 0.5 * segmentLength);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String name() {
        return String.format("BATES(%d seg, Do=%.0fmm, Dp=%.0fmm, L=%.0fmm)",
                numSegments,
                outerDiameter * 1000,
                portDiameter  * 1000,
                segmentLength * 1000);
    }

    /**
     * Returns the segment length at which the grain exhibits exactly neutral
     * burn character at its endpoints ({@code A_b(0) == A_b(webThickness())})
     * under radial burnout, i.e., when the bore reaches the case wall before the
     * end faces meet.
     *
     * <p>Setting {@code A_b(0) = A_b(w)} with {@code w = (D_o − D_p)/2} and
     * the corrected effective-length formula yields:
     * {@code L_neutral = (3·D_o + D_p) / 2}.
     *
     * <p>For {@code L > L_neutral} the burn is initially progressive then
     * regressive; for {@code L < L_neutral} the reverse applies.
     *
     * @return neutral-burn segment length [m] (valid only for radial burnout)
     */
    public double neutralSegmentLength() {
        return 0.5 * (3.0 * outerDiameter + portDiameter);
    }
}
