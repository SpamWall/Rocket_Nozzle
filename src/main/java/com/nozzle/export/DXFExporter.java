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

import com.nozzle.geometry.FullNozzleGeometry;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.geometry.Point2D;
import com.nozzle.moc.AerospikeNozzle;
import com.nozzle.moc.CharacteristicNet;
import com.nozzle.moc.CharacteristicPoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Exports nozzle geometry to DXF format for CAD import.
 */
public class DXFExporter {

    private static final Logger LOG = LoggerFactory.getLogger(DXFExporter.class);

    /** Creates a {@code DXFExporter} with default settings. */
    public DXFExporter() {}

    private static final String DXF_HEADER = """
            0
            SECTION
            2
            HEADER
            9
            $ACADVER
            1
            AC1015
            9
            $INSUNITS
            70
            4
            0
            ENDSEC
            0
            SECTION
            2
            ENTITIES
            """;
    
    private static final String DXF_FOOTER = """
            0
            ENDSEC
            0
            EOF
            """;
    
    /** Scale factor applied to all coordinates before writing (default: 1000 → metres to mm). */
    private double scaleFactor = 1000.0;

    /**
     * Sets the coordinate scale factor applied to all exported geometry.
     * The default value (1000) converts metres to millimetres, matching the
     * typical expectation of DXF-importing CAD tools.
     *
     * @param scale Multiplicative scale factor (e.g. {@code 1000} for m → mm)
     * @return This instance for method chaining
     */
    public DXFExporter setScaleFactor(double scale) {
        this.scaleFactor = scale;
        return this;
    }

    /**
     * Exports the nozzle wall contour as a DXF POLYLINE entity together with a
     * straight LINE along the symmetry axis (y = 0).
     *
     * @param contour  Nozzle contour whose points define the wall profile
     * @param filePath Destination DXF file path
     * @throws IOException              If the file cannot be written
     * @throws IllegalArgumentException If the contour has no points
     */
    public void exportContour(NozzleContour contour, Path filePath) throws IOException {
        List<Point2D> points = contour.getContourPoints();
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Contour has no points");
        }

        LOG.debug("Exporting DXF contour: {} points → {}", points.size(), filePath);
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write(DXF_HEADER);
            writePolyline(writer, points, "WALL");
            writeLine(writer, new Point2D(points.getFirst().x(), 0),
                    new Point2D(points.getLast().x(), 0), "AXIS");
            writer.write(DXF_FOOTER);
        }
        LOG.debug("DXF contour export complete → {}", filePath);
    }
    
    /**
     * Exports the MOC wall-point sequence as a DXF POLYLINE together with an axis
     * LINE at y = 0.
     *
     * @param net      Characteristic net whose wall points define the contour
     * @param filePath Destination DXF file path
     * @throws IOException If the file cannot be written
     */
    public void exportCharacteristicNet(CharacteristicNet net, Path filePath) throws IOException {
        LOG.debug("Exporting DXF characteristic net → {}", filePath);
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write(DXF_HEADER);
            
            List<CharacteristicPoint> wallPoints = net.getWallPoints();
            if (!wallPoints.isEmpty()) {
                writeCharacteristicPolyline(writer, wallPoints);
            }

            double xMax = wallPoints.isEmpty() ? 1.0 : wallPoints.getLast().x();
            writeLine(writer, new Point2D(0, 0), new Point2D(xMax, 0), "AXIS");

            writer.write(DXF_FOOTER);
        }
        LOG.debug("DXF characteristic net export complete → {}", filePath);
    }

    /**
     * Exports a closed 2D revolution-profile cross-section as four DXF entities:
     * the wall POLYLINE plus three LINE segments that close the profile along the
     * exit face, axis, and throat face.
     * Suitable for use as the sketch profile for a revolve operation in CAD tools.
     *
     * @param contour  Nozzle contour whose points define the outer wall
     * @param filePath Destination DXF file path
     * @throws IOException              If the file cannot be written
     * @throws IllegalArgumentException If the contour has no points
     */
    public void exportRevolutionProfile(NozzleContour contour, Path filePath) throws IOException {
        List<Point2D> points = contour.getContourPoints();
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Contour has no points");
        }

        LOG.debug("Exporting DXF revolution profile: {} points → {}", points.size(), filePath);
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write(DXF_HEADER);
            writePolyline(writer, points, "WALL");

            Point2D exitTop = points.getLast();
            Point2D exitBottom = new Point2D(exitTop.x(), 0);
            Point2D throatBottom = new Point2D(points.getFirst().x(), 0);
            Point2D throatTop = points.getFirst();

            writeLine(writer, exitTop, exitBottom, "AXIS");
            writeLine(writer, exitBottom, throatBottom, "AXIS");
            writeLine(writer, throatBottom, throatTop, "AXIS");
            
            writer.write(DXF_FOOTER);
        }
    }
    
    /**
     * Writes an open DXF POLYLINE entity to {@code writer}.
     *
     * @param writer Writer receiving the DXF entities section content
     * @param points Ordered list of 2D points defining the polyline vertices
     * @param layer  DXF layer name string
     * @throws IOException If the writer throws
     */
    private void writePolyline(BufferedWriter writer, List<Point2D> points, String layer)
            throws IOException {
        writer.write("0\nPOLYLINE\n8\n" + layer + "\n66\n1\n70\n0\n");
        for (Point2D point : points) {
            writeVertex(writer, point, layer);
        }
        writer.write("0\nSEQEND\n");
    }
    
    /**
     * Writes an open DXF POLYLINE entity from a list of {@link CharacteristicPoint}s
     * on the {@code WALL} layer, using only their (x, y) coordinates.
     *
     * @param writer Writer receiving the DXF entities section content
     * @param points Ordered list of characteristic points
     * @throws IOException If the writer throws
     */
    private void writeCharacteristicPolyline(BufferedWriter writer,
                                              List<CharacteristicPoint> points)
            throws IOException {
        writer.write("0\nPOLYLINE\n8\nWALL\n66\n1\n70\n0\n");
        for (CharacteristicPoint point : points) {
            writeVertex(writer, new Point2D(point.x(), point.y()), "WALL");
        }
        writer.write("0\nSEQEND\n");
    }
    
    /**
     * Writes a single DXF VERTEX entity (group codes 0, 8, 10, 20, 30).
     * The z-coordinate is always written as {@code 0.0}.
     * Coordinates are scaled by {@link #scaleFactor} before writing.
     *
     * @param writer Writer receiving the DXF entities section content
     * @param point  2D point whose x/y coordinates are the vertex position
     * @param layer  DXF layer name string
     * @throws IOException If the writer throws
     */
    private void writeVertex(BufferedWriter writer, Point2D point, String layer)
            throws IOException {
        writer.write("0\nVERTEX\n8\n" + layer + "\n");
        writer.write(String.format("10\n%.6f\n20\n%.6f\n30\n0.0\n", 
                point.x() * scaleFactor, point.y() * scaleFactor));
    }
    
    /**
     * Writes a DXF LINE entity between two 2D points (z = 0).
     * Coordinates are scaled by {@link #scaleFactor} before writing.
     *
     * @param writer Writer receiving the DXF entities section content
     * @param start  Start point of the line
     * @param end    End point of the line
     * @param layer  DXF layer name string
     * @throws IOException If the writer throws
     */
    private void writeLine(BufferedWriter writer, Point2D start, Point2D end, String layer)
            throws IOException {
        writer.write("0\nLINE\n8\n" + layer + "\n");
        writer.write(String.format("10\n%.6f\n20\n%.6f\n30\n0.0\n",
                start.x() * scaleFactor, start.y() * scaleFactor));
        writer.write(String.format("11\n%.6f\n21\n%.6f\n31\n0.0\n",
                end.x() * scaleFactor, end.y() * scaleFactor));
    }

    /**
     * Exports the complete nozzle wall profile — from the injector face through the
     * convergent section, throat, and full divergent section to the exit — as a DXF
     * file.
     *
     * <p>Three DXF layers are written:
     * <ul>
     *   <li>{@code WALL}   — polyline tracing the inner wall from chamber face to exit</li>
     *   <li>{@code AXIS}   — centerline from the chamber face x to the exit x</li>
     *   <li>{@code THROAT} — vertical line marking the throat plane at x = 0</li>
     * </ul>
     *
     * <p>The {@code FullNozzleGeometry} must have been generated before calling this
     * method (see {@link FullNozzleGeometry#generate}).
     *
     * @param fullGeometry Full nozzle geometry (chamber face → exit)
     * @param filePath     Destination DXF file path
     * @throws IOException              If the file cannot be written
     * @throws IllegalArgumentException If the geometry has no wall points
     */
    public void exportFullNozzleProfile(FullNozzleGeometry fullGeometry, Path filePath)
            throws IOException {
        List<Point2D> points = fullGeometry.getWallPoints();
        if (points.isEmpty()) {
            throw new IllegalArgumentException(
                    "FullNozzleGeometry has no wall points — call generate() first");
        }

        double rt    = fullGeometry.getThroatRadius();
        double xMin  = points.getFirst().x();
        double xMax  = points.getLast().x();

        LOG.debug("Exporting full-nozzle DXF: {} wall points, x=[{}, {}] → {}",
                points.size(),
                String.format("%.4f", xMin),
                String.format("%.4f", xMax),
                filePath);

        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write(DXF_HEADER);

            // Inner wall profile
            writePolyline(writer, points, "WALL");

            // Symmetry axis from chamber face to exit
            writeLine(writer,
                    new Point2D(xMin, 0),
                    new Point2D(xMax, 0),
                    "AXIS");

            // Throat marker
            writeLine(writer,
                    new Point2D(0, 0),
                    new Point2D(0, rt),
                    "THROAT");

            writer.write(DXF_FOOTER);
        }
        LOG.debug("Full-nozzle DXF export complete → {}", filePath);
    }

    /**
     * Exports the complete nozzle as a closed revolution-profile cross-section
     * suitable for use as the sketch for a revolve operation in CAD tools.
     *
     * <p>Five DXF entities are written:
     * <ul>
     *   <li>{@code WALL}    — inner wall polyline from chamber face to exit</li>
     *   <li>{@code AXIS}    — centerline from chamber face to exit, closing the bottom</li>
     *   <li>{@code INLET}   — vertical inlet (chamber) face line from axis to wall</li>
     *   <li>{@code OUTLET}  — vertical exit face line from wall to axis</li>
     *   <li>{@code THROAT}  — vertical throat plane marker</li>
     * </ul>
     *
     * @param fullGeometry Full nozzle geometry (must be generated)
     * @param filePath     Destination DXF file path
     * @throws IOException              If the file cannot be written
     * @throws IllegalArgumentException If the geometry has no wall points
     */
    public void exportFullNozzleRevolutionProfile(FullNozzleGeometry fullGeometry, Path filePath)
            throws IOException {
        List<Point2D> points = fullGeometry.getWallPoints();
        if (points.isEmpty()) {
            throw new IllegalArgumentException(
                    "FullNozzleGeometry has no wall points — call generate() first");
        }

        double rt     = fullGeometry.getThroatRadius();
        double xMin   = points.getFirst().x();
        double xMax   = points.getLast().x();
        double rInlet = points.getFirst().y();
        double rExit  = points.getLast().y();

        LOG.debug("Exporting full-nozzle revolution DXF: {} pts → {}", points.size(), filePath);

        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write(DXF_HEADER);

            // Inner wall
            writePolyline(writer, points, "WALL");

            // Axis (bottom closure)
            writeLine(writer, new Point2D(xMin, 0), new Point2D(xMax, 0), "AXIS");

            // Inlet face (left vertical)
            writeLine(writer, new Point2D(xMin, 0), new Point2D(xMin, rInlet), "INLET");

            // Exit face (right vertical)
            writeLine(writer, new Point2D(xMax, rExit), new Point2D(xMax, 0), "OUTLET");

            // Throat marker
            writeLine(writer, new Point2D(0, 0), new Point2D(0, rt), "THROAT");

            writer.write(DXF_FOOTER);
        }
        LOG.debug("Full-nozzle revolution DXF export complete → {}", filePath);
    }

    /**
     * Exports Aerospike geometry as a DXF file with three layers:
     * <ul>
     *   <li>{@code SPIKE} — the full ideal spike contour polyline</li>
     *   <li>{@code COWL} — a line from the inner throat radius to the outer throat
     *       radius at x = 0, representing the annular throat face</li>
     *   <li>{@code AXIS} — symmetry axis from x = 0 to the spike-tip x position</li>
     * </ul>
     *
     * @param nozzle   Aerospike nozzle (must have been generated)
     * @param filePath Destination DXF file path
     * @throws IOException If the file cannot be written
     */
    public void exportAerospikeContour(AerospikeNozzle nozzle, Path filePath) throws IOException {
        // getFullSpikeContour() generates lazily, so the list is always non-empty after this call.
        List<Point2D> spike = nozzle.getFullSpikeContour();
        LOG.debug("Exporting Aerospike DXF contour: {} spike points → {}", spike.size(), filePath);

        double rt = nozzle.getParameters().throatRadius();
        double ri = rt * nozzle.getSpikeRadiusRatio();
        double xTip = spike.getLast().x();

        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write(DXF_HEADER);

            // Spike contour
            writePolyline(writer, spike, "SPIKE");

            // Annular throat face: inner (spike-tip at x=0) to outer (cowl lip)
            writeLine(writer, new Point2D(0, ri), new Point2D(0, rt), "COWL");

            // Symmetry axis
            writeLine(writer, new Point2D(0, 0), new Point2D(xTip, 0), "AXIS");

            writer.write(DXF_FOOTER);
        }
    }
}
