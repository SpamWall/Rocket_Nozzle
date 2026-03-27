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

package com.nozzle.io;

import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.geometry.Point2D;
import com.nozzle.moc.CharacteristicPoint;

import java.time.Instant;
import java.util.List;

/**
 * Serializable snapshot of a complete nozzle design session.
 *
 * <p>A {@code DesignDocument} bundles the design intent ({@link NozzleDesignParameters}),
 * the computed MOC flow field ({@link #wallPoints} and {@link #netPoints}), and the
 * wall geometry ({@link #contourPoints}) into a single JSON-serializable unit.
 * Use {@link NozzleSerializer} to save and load instances.
 *
 * <p>The {@link #version} field is written by the serializer to identify the document
 * schema; {@link #createdAt} records the ISO-8601 UTC timestamp of the save operation.
 * Both are informational and are not validated on load.
 *
 * <p>Any of the computed lists may be empty — for example, a document that captures
 * only the design parameters (before any computation) has empty {@code wallPoints},
 * {@code netPoints}, and {@code contourPoints}.
 *
 * @param version        Schema version string written by {@link NozzleSerializer}
 * @param createdAt      ISO-8601 UTC timestamp of the save operation
 * @param parameters     Nozzle design parameters
 * @param wallPoints     MOC wall-boundary characteristic points; empty before
 *                       {@link com.nozzle.moc.CharacteristicNet#generate()} is called
 * @param netPoints      Full MOC interior network, row-by-row; empty before generation
 * @param contourPoints  Discrete nozzle wall contour points; empty before
 *                       {@link com.nozzle.geometry.NozzleContour#generate(int)} is called
 */
public record DesignDocument(
        String version,
        String createdAt,
        NozzleDesignParameters parameters,
        List<CharacteristicPoint> wallPoints,
        List<List<CharacteristicPoint>> netPoints,
        List<Point2D> contourPoints
) {
    /**
     * Creates a design document containing only parameters (no computed results).
     * Useful for saving the design intent before running the MOC solver.
     *
     * @param version    Schema version string
     * @param parameters Nozzle design parameters
     * @return New {@code DesignDocument} with empty computed lists
     */
    public static DesignDocument parametersOnly(String version, NozzleDesignParameters parameters) {
        return new DesignDocument(version, Instant.now().toString(),
                parameters, List.of(), List.of(), List.of());
    }
}
