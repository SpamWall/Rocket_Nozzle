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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for RASP {@code .eng} solid rocket motor thrust-curve files.
 *
 * <h3>File format</h3>
 * <p>A RASP {@code .eng} file consists of one or more motor definitions.
 * Lines beginning with {@code ;} are comments and are ignored.  Each motor
 * definition has:
 * <ol>
 *   <li>A single header line with exactly seven whitespace-delimited fields:
 *       <pre>
 *   &lt;name&gt; &lt;diameter_mm&gt; &lt;length_mm&gt; &lt;delays&gt;
 *       &lt;prop_mass_kg&gt; &lt;total_mass_kg&gt; &lt;manufacturer&gt;</pre>
 *       where {@code delays} is a comma-separated list of ejection-charge
 *       delay times in seconds, the letter {@code P} for a plugged motor,
 *       or {@code 0} for no delay.
 *   </li>
 *   <li>One or more data lines, each with two fields:
 *       <pre>   &lt;time_s&gt;  &lt;thrust_N&gt;</pre>
 *   </li>
 *   <li>An optional terminating {@code ;} line (or end of file) that ends
 *       the data section for that motor.</li>
 * </ol>
 *
 * <p>Multiple motor definitions may appear in a single file; use
 * {@link #loadAll(Path)} to retrieve all of them.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Single motor (first motor in the file)
 * RaspMotorData motor = RaspImporter.load(Path.of("F52-8.eng"));
 *
 * // All motors in a multi-motor file
 * List<RaspMotorData> motors = RaspImporter.loadAll(Path.of("aerotech.eng"));
 * }</pre>
 */
public final class RaspImporter {

    private RaspImporter() {}

    /**
     * Loads and returns the first motor definition from a {@code .eng} file.
     *
     * @param file path to the {@code .eng} file
     * @return the first {@link RaspMotorData} found in the file
     * @throws IOException              if the file cannot be read
     * @throws IllegalArgumentException if the file contains no valid motor
     *                                  definition or a header is malformed
     */
    public static RaspMotorData load(Path file) throws IOException {
        List<RaspMotorData> motors = loadAll(file);
        if (motors.isEmpty()) {
            throw new IllegalArgumentException(
                    "No motor definition found in: " + file);
        }
        return motors.getFirst();
    }

    /**
     * Loads and returns all motor definitions from a {@code .eng} file.
     *
     * @param file path to the {@code .eng} file
     * @return list of {@link RaspMotorData}; never null, may be empty
     * @throws IOException              if the file cannot be read
     * @throws IllegalArgumentException if a header line is malformed
     */
    public static List<RaspMotorData> loadAll(Path file) throws IOException {
        return parseAll(Files.readString(file));
    }

    /**
     * Parses the first motor definition from a string of {@code .eng} content.
     *
     * @param content raw {@code .eng} text
     * @return parsed motor data
     * @throws IllegalArgumentException if no valid motor is found or a header
     *                                  is malformed
     */
    static RaspMotorData parse(String content) {
        List<RaspMotorData> motors = parseAll(content);
        if (motors.isEmpty()) {
            throw new IllegalArgumentException(
                    "No motor definition found in supplied content");
        }
        return motors.getFirst();
    }

    /**
     * Parses all motor definitions from a string of {@code .eng} content.
     *
     * @param content raw {@code .eng} text
     * @return list of parsed motors; never null, may be empty
     * @throws IllegalArgumentException if a header line is malformed
     */
    static List<RaspMotorData> parseAll(String content) {
        List<RaspMotorData> result = new ArrayList<>();
        String[]            lines  = content.split("\\r?\\n");

        int i = 0;
        while (i < lines.length) {
            String line = lines[i].trim();
            i++;

            // skip blank lines and comment-only lines
            if (line.isEmpty() || line.startsWith(";")) {
                continue;
            }

            // first non-comment, non-blank line is the motor header
            String[] tok = line.split("\\s+");
            if (tok.length < 7) {
                throw new IllegalArgumentException(
                        "Malformed RASP header (expected 7 fields): \"" + line + "\"");
            }

            String name         = tok[0];
            double diameterMm   = parseDouble(tok[1], "diameter",   name);
            double lengthMm     = parseDouble(tok[2], "length",     name);
            String delays       = tok[3];
            double propMassKg   = parseDouble(tok[4], "prop mass",  name);
            double totalMassKg  = parseDouble(tok[5], "total mass", name);
            String manufacturer = tok[6];

            // read data points until a ';' terminator or end of file
            List<double[]> points = new ArrayList<>();
            while (i < lines.length) {
                String dataLine = lines[i].trim();
                i++;

                if (dataLine.startsWith(";")) {
                    break;  // motor terminated
                }
                if (dataLine.isEmpty()) {
                    continue;
                }

                String[] parts = dataLine.split("\\s+");
                if (parts.length < 2) {
                    continue;
                }
                double time   = parseDouble(parts[0], "time",   name);
                double thrust = parseDouble(parts[1], "thrust", name);
                points.add(new double[]{time, thrust});
            }

            if (points.isEmpty()) {
                throw new IllegalArgumentException(
                        "Motor \"" + name + "\" has no data points");
            }

            double[] timeArr   = new double[points.size()];
            double[] thrustArr = new double[points.size()];
            for (int j = 0; j < points.size(); j++) {
                timeArr[j]   = points.get(j)[0];
                thrustArr[j] = points.get(j)[1];
            }

            result.add(new RaspMotorData(name, diameterMm, lengthMm, delays,
                    propMassKg, totalMassKg, manufacturer,
                    timeArr, thrustArr));
        }

        return result;
    }

    private static double parseDouble(String s, String field, String motor) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid " + field + " value \"" + s
                    + "\" in motor \"" + motor + "\"");
        }
    }
}
