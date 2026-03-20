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
