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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.moc.CharacteristicNet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Utility for saving and loading nozzle design sessions as JSON documents.
 *
 * <p>All persistence is handled through the {@link DesignDocument} envelope record,
 * which captures design parameters, MOC results, and wall geometry in a single file.
 * Files are written as pretty-printed JSON; the schema version is embedded in every
 * document so future migrations can be detected.
 *
 * <p>Usage — save a full design:
 * <pre>{@code
 * NozzleDesignParameters params = NozzleDesignParameters.builder()
 *         .throatRadius(0.05).exitMach(3.0).chamberPressure(7e6)
 *         .gasProperties(GasProperties.LOX_RP1_PRODUCTS).build();
 *
 * CharacteristicNet net = new CharacteristicNet(params).generate();
 * NozzleContour     contour = new NozzleContour(ContourType.RAO_BELL, params);
 * contour.generate(200);
 *
 * NozzleSerializer.save(NozzleSerializer.document(params, net, contour),
 *                        Path.of("my_nozzle.json"));
 * }</pre>
 *
 * <p>Usage — reload:
 * <pre>{@code
 * DesignDocument doc    = NozzleSerializer.load(Path.of("my_nozzle.json"));
 * NozzleDesignParameters params = doc.parameters();
 * List<CharacteristicPoint> wall = doc.wallPoints();
 * }</pre>
 */
public final class NozzleSerializer {

    private static final Logger LOG = LoggerFactory.getLogger(NozzleSerializer.class);

    /** Current document schema version. Increment when the {@link DesignDocument} structure changes. */
    public static final String SCHEMA_VERSION = "1.0";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private NozzleSerializer() {}

    // -----------------------------------------------------------------------
    // Document builders
    // -----------------------------------------------------------------------

    /**
     * Creates a {@link DesignDocument} containing only the design parameters.
     * Use this before the MOC solve when you want to persist the design intent.
     *
     * @param parameters Nozzle design parameters to store
     * @return Document with empty computed lists
     */
    public static DesignDocument document(NozzleDesignParameters parameters) {
        return DesignDocument.parametersOnly(SCHEMA_VERSION, parameters);
    }

    /**
     * Creates a {@link DesignDocument} from a fully computed design.
     *
     * @param parameters Nozzle design parameters
     * @param net        Computed characteristic network ({@link CharacteristicNet#generate()}
     *                   must have been called)
     * @param contour    Generated nozzle wall contour ({@link NozzleContour#generate(int)}
     *                   must have been called)
     * @return Document containing parameters, wall points, net interior, and contour points
     */
    public static DesignDocument document(NozzleDesignParameters parameters,
                                           CharacteristicNet net,
                                           NozzleContour contour) {
        return new DesignDocument(
                SCHEMA_VERSION,
                Instant.now().toString(),
                parameters,
                net.getWallPoints(),
                net.getNetPoints(),
                contour.getContourPoints());
    }

    // -----------------------------------------------------------------------
    // I/O
    // -----------------------------------------------------------------------

    /**
     * Writes a {@link DesignDocument} to a JSON file.
     * The file is created or overwritten; parent directories must already exist.
     *
     * @param document Document to serialize
     * @param path     Target file path
     * @throws UncheckedIOException if the file cannot be written
     */
    public static void save(DesignDocument document, Path path) {
        LOG.debug("Saving design document to {}", path);
        try {
            MAPPER.writeValue(path.toFile(), document);
        } catch (IOException e) {
            LOG.error("Failed to write design document to {}", path, e);
            throw new UncheckedIOException("Failed to write design document to: " + path, e);
        }
    }

    /**
     * Writes a {@link DesignDocument} to a JSON string for in-memory transfer
     * or logging.
     *
     * @param document Document to serialize
     * @return Pretty-printed JSON string
     * @throws UncheckedIOException if serialization fails
     */
    public static String toJson(DesignDocument document) {
        try {
            return MAPPER.writeValueAsString(document);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialise design document", e);
        }
    }

    /**
     * Reads a {@link DesignDocument} from a JSON file.
     *
     * @param path Source file path
     * @return Restored design document
     * @throws UncheckedIOException if the file cannot be read or parsed
     */
    public static DesignDocument load(Path path) {
        LOG.debug("Loading design document from {}", path);
        try {
            return MAPPER.readValue(path.toFile(), DesignDocument.class);
        } catch (IOException e) {
            LOG.error("Failed to read design document from {}", path, e);
            throw new UncheckedIOException("Failed to read design document from: " + path, e);
        }
    }

    /**
     * Parses a {@link DesignDocument} from a JSON string.
     *
     * @param json JSON string produced by {@link #toJson(DesignDocument)}
     * @return Restored design document
     * @throws UncheckedIOException if the string cannot be parsed
     */
    public static DesignDocument fromJson(String json) {
        try {
            return MAPPER.readValue(json, DesignDocument.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse design document from JSON", e);
        }
    }
}
