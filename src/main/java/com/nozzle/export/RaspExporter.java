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

package com.nozzle.export;

import com.nozzle.solid.RaspMotorData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Serializer for RASP {@code .eng} solid rocket motor thrust-curve files.
 *
 * <p>Generates output that can be loaded directly into OpenRocket or any other
 * tool that reads the RASP format.  The API mirrors {@link com.nozzle.solid.RaspImporter}:
 *
 * <pre>{@code
 * // Single motor
 * RaspMotorData motor = ...;
 * RaspExporter.save(motor, Path.of("F52-8.eng"));
 *
 * // Multiple motors in one file
 * List<RaspMotorData> motors = List.of(f, g);
 * RaspExporter.saveAll(motors, Path.of("aerotech.eng"));
 *
 * // In-memory string (e.g. for testing)
 * String engText = RaspExporter.format(motor);
 * }</pre>
 *
 * <h3>Output format</h3>
 * <p>Each motor block is written as:
 * <pre>
 * ; &lt;name&gt;
 * &lt;name&gt; &lt;diam_mm&gt; &lt;len_mm&gt; &lt;delays&gt; &lt;prop_kg&gt; &lt;total_kg&gt; &lt;mfr&gt;
 *    &lt;time_s&gt;   &lt;thrust_N&gt;
 *    ...
 * ;
 * </pre>
 * All numeric fields use enough significant figures to round-trip through
 * {@link com.nozzle.solid.RaspImporter} without loss.
 */
public final class RaspExporter {

    private RaspExporter() {}

    /**
     * Serializes a single motor to a RASP {@code .eng} string.
     *
     * @param motor the motor to serialize
     * @return RASP-formatted text, always terminated with a newline
     */
    public static String format(RaspMotorData motor) {
        StringBuilder sb = new StringBuilder();
        appendMotor(sb, motor);
        return sb.toString();
    }

    /**
     * Serializes a list of motors to a single RASP {@code .eng} string.
     *
     * @param motors motors to serialize; must not be null or empty
     * @return RASP-formatted text containing all motors in order
     * @throws IllegalArgumentException if {@code motors} is empty
     */
    public static String formatAll(List<RaspMotorData> motors) {
        if (motors.isEmpty()) {
            throw new IllegalArgumentException("Motor list must not be empty");
        }
        StringBuilder sb = new StringBuilder();
        for (RaspMotorData m : motors) {
            appendMotor(sb, m);
        }
        return sb.toString();
    }

    /**
     * Writes a single motor to a {@code .eng} file.
     *
     * @param motor    the motor to write
     * @param file     destination path (created or overwritten)
     * @throws IOException if the file cannot be written
     */
    public static void save(RaspMotorData motor, Path file) throws IOException {
        Files.writeString(file, format(motor));
    }

    /**
     * Writes a list of motors to a single {@code .eng} file.
     *
     * @param motors motors to write; must not be null or empty
     * @param file   destination path (created or overwritten)
     * @throws IOException              if the file cannot be written
     * @throws IllegalArgumentException if {@code motors} is empty
     */
    public static void saveAll(List<RaspMotorData> motors, Path file) throws IOException {
        Files.writeString(file, formatAll(motors));
    }

    // -------------------------------------------------------------------------

    private static void appendMotor(StringBuilder sb, RaspMotorData m) {
        sb.append("; ").append(m.name()).append("\n");
        sb.append(String.format("%s %.6g %.6g %s %.6g %.6g %s\n",
                m.name(),
                m.diameterMm(), m.lengthMm(),
                m.delays(),
                m.propellantMassKg(), m.totalMassKg(),
                m.manufacturer()));
        for (int i = 0; i < m.size(); i++) {
            sb.append(String.format("   %.6f   %.6f\n", m.timeAt(i), m.thrustAt(i)));
        }
        sb.append(";\n");
    }
}
