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
import com.nozzle.moc.DualBellNozzle;
import com.nozzle.moc.RaoNozzle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Exports nozzle geometry to STEP (ISO 10303-21) format.
 * Generates a 3D revolved solid from the 2D contour.
 */
public class STEPExporter {

    private static final Logger LOG = LoggerFactory.getLogger(STEPExporter.class);

    /** Creates a {@code STEPExporter} with default settings (metres-to-mm scale, generic author). */
    public STEPExporter() {}

    /** Scale factor applied to all coordinates (default: 1000 → metres to mm, as required by AUTOMOTIVE_DESIGN). */
    private double scaleFactor = 1000.0;
    /** Author name written into the FILE_NAME STEP header entity. */
    private String authorName = "Supersonic Nozzle MOC";
    /** Organisation name written into the FILE_NAME STEP header entity. */
    private String organizationName = "Nozzle Design Tool";

    /**
     * Sets the coordinate scale factor.  The STEP AUTOMOTIVE_DESIGN schema
     * expects millimetres, so the default of 1000 converts metres to mm.
     *
     * @param scale Multiplicative scale factor
     * @return This instance for method chaining
     */
    public STEPExporter setScaleFactor(double scale) {
        this.scaleFactor = scale;
        return this;
    }

    /**
     * Sets the author name recorded in the STEP file header.
     *
     * @param author Author name string
     * @return This instance for method chaining
     */
    public STEPExporter setAuthor(String author) {
        this.authorName = author;
        return this;
    }

    /**
     * Sets the organization name recorded in the STEP file header.
     *
     * @param org Organisation name string
     * @return This instance for method chaining
     */
    public STEPExporter setOrganization(String org) {
        this.organizationName = org;
        return this;
    }
    
    /**
     * Exports nozzle as revolved solid to STEP format.
     *
     * @param contour  Nozzle contour
     * @param filePath Output file path
     * @throws IOException If write fails
     */
    public void exportRevolvedSolid(NozzleContour contour, Path filePath) throws IOException {
        List<Point2D> points = contour.getContourPoints();
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Contour has no points");
        }

        LOG.debug("Exporting STEP revolved solid: {} profile points → {}", points.size(), filePath);
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writeHeader(writer);
            writeData(writer, points);
            writeFooter(writer);
        }
        LOG.debug("STEP export complete → {}", filePath);
    }

    /**
     * Exports the complete nozzle (convergent + divergent) as a revolved solid to
     * STEP format.  The wall-point list from {@link FullNozzleGeometry#getWallPoints()}
     * spans the injector face (x &lt; 0) through the throat to the exit, producing a
     * geometry-complete solid suitable for manufacturing drawings or FEA models.
     *
     * @param fullGeometry Full nozzle geometry (must have been generated)
     * @param filePath     Destination STEP file path
     * @throws IllegalStateException If {@code fullGeometry} has not been generated
     * @throws IOException           If the file cannot be written
     */
    public void exportRevolvedSolid(FullNozzleGeometry fullGeometry, Path filePath)
            throws IOException {
        List<Point2D> points = fullGeometry.getWallPoints();
        if (points.isEmpty()) {
            throw new IllegalStateException(
                    "FullNozzleGeometry has no wall points — call generate() first");
        }
        LOG.debug("Exporting geometry-complete STEP revolved solid: {} wall points → {}",
                points.size(), filePath);
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writeHeader(writer);
            writeData(writer, points);
            writeFooter(writer);
        }
        LOG.debug("Geometry-complete STEP export complete → {}", filePath);
    }
    
    /**
     * Writes the ISO-10303-21 HEADER section including FILE_DESCRIPTION,
     * FILE_NAME (with current ISO timestamp, author, and organization), and
     * FILE_SCHEMA.
     *
     * @param writer Output writer (must be open)
     * @throws IOException If the writer throws
     */
    private void writeHeader(BufferedWriter writer) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        
        writer.write("ISO-10303-21;\n");
        writer.write("HEADER;\n");
        writer.write("FILE_DESCRIPTION(('Supersonic Nozzle Geometry'),'2;1');\n");
        writer.write(String.format("FILE_NAME('nozzle.step','%s',('%s'),('%s'),'','Nozzle MOC','');\n",
                timestamp, authorName, organizationName));
        writer.write("FILE_SCHEMA(('AUTOMOTIVE_DESIGN'));\n");
        writer.write("ENDSEC;\n\n");
    }
    
    /**
     * Writes the ISO-10303-21 DATA section containing CARTESIAN_POINT, DIRECTION,
     * AXIS2_PLACEMENT_3D, POLYLINE, EDGE_CURVE, SURFACE_OF_REVOLUTION, and the
     * product-definition chain required to make the file importable as a named
     * part in a compliant CAD application.
     *
     * @param writer Output writer (must be open)
     * @param points Ordered contour points (at least one entry expected)
     * @throws IOException If the writer throws
     */
    private void writeData(BufferedWriter writer, List<Point2D> points) throws IOException {
        writer.write("DATA;\n");
        
        int entityId = 1;
        List<Integer> cartesianPointIds = new ArrayList<>();
        
        // Write Cartesian points for the profile
        for (Point2D point : points) {
            writer.write(String.format("#%d=CARTESIAN_POINT('',(%.8f,%.8f,0.0));\n",
                    entityId, point.x() * scaleFactor, point.y() * scaleFactor));
            cartesianPointIds.add(entityId);
            entityId++;
        }
        
        // Add closing points (back to axis and to start)
        Point2D lastPoint = points.getLast();
        Point2D firstPoint = points.getFirst();
        
        writer.write(String.format("#%d=CARTESIAN_POINT('',(%.8f,0.0,0.0));\n",
                entityId, lastPoint.x() * scaleFactor));
        cartesianPointIds.add(entityId);
        entityId++;
        
        writer.write(String.format("#%d=CARTESIAN_POINT('',(%.8f,0.0,0.0));\n",
                entityId, firstPoint.x() * scaleFactor));
        cartesianPointIds.add(entityId);
        entityId++;
        
        // Direction vectors
        int dirXId = entityId++;
        int dirYId = entityId++;
        int dirZId = entityId++;
        writer.write(String.format("#%d=DIRECTION('',(1.0,0.0,0.0));\n", dirXId));
        writer.write(String.format("#%d=DIRECTION('',(0.0,1.0,0.0));\n", dirYId));
        writer.write(String.format("#%d=DIRECTION('',(0.0,0.0,1.0));\n", dirZId));
        
        // Origin
        int originId = entityId++;
        writer.write(String.format("#%d=CARTESIAN_POINT('',(0.0,0.0,0.0));\n", originId));
        
        // Axis placement for revolution
        int axis2Id = entityId++;
        writer.write(String.format("#%d=AXIS2_PLACEMENT_3D('',#%d,#%d,#%d);\n",
                axis2Id, originId, dirXId, dirYId));
        
        // Create polyline from points
        StringBuilder pointRefs = new StringBuilder();
        for (int i = 0; i < cartesianPointIds.size(); i++) {
            if (i > 0) pointRefs.append(",");
            pointRefs.append("#").append(cartesianPointIds.get(i));
        }
        
        int polylineId = entityId++;
        writer.write(String.format("#%d=POLYLINE('',(%s));\n", polylineId, pointRefs));
        
        // Edge curve
        int edgeCurveId = entityId++;
        int vertexStartId = entityId++;
        int vertexEndId = entityId++;
        
        writer.write(String.format("#%d=VERTEX_POINT('',#%d);\n", 
                vertexStartId, cartesianPointIds.getFirst()));
        writer.write(String.format("#%d=VERTEX_POINT('',#%d);\n", 
                vertexEndId, cartesianPointIds.getLast()));
        writer.write(String.format("#%d=EDGE_CURVE('',#%d,#%d,#%d,.T.);\n",
                edgeCurveId, vertexStartId, vertexEndId, polylineId));
        
        // Revolution axis
        int axis1Id = entityId++;
        writer.write(String.format("#%d=AXIS1_PLACEMENT('',#%d,#%d);\n",
                axis1Id, originId, dirXId));
        
        // Surface of revolution
        int surfaceId = entityId++;
        writer.write(String.format("#%d=SURFACE_OF_REVOLUTION('',#%d,#%d);\n",
                surfaceId, polylineId, axis1Id));
        
        // Geometric representation context
        int contextId = entityId++;
        writer.write(String.format("#%d=GEOMETRIC_REPRESENTATION_CONTEXT(3);\n", contextId));
        
        // Shape representation
        int shapeRepId = entityId++;
        writer.write(String.format("#%d=SHAPE_REPRESENTATION('Nozzle',(#%d),#%d);\n",
                shapeRepId, surfaceId, contextId));
        
        // Product definition
        int productId = entityId++;
        int productDefId = entityId++;
        int productDefFormId = entityId++;
        int productDefShapeId = entityId++;
        
        writer.write(String.format("#%d=PRODUCT('Supersonic_Nozzle','Nozzle','',(#%d));\n",
                productId, contextId));
        writer.write(String.format("#%d=PRODUCT_DEFINITION_FORMATION('','',#%d);\n",
                productDefFormId, productId));
        writer.write(String.format("#%d=PRODUCT_DEFINITION('design','',#%d,#%d);\n",
                productDefId, productDefFormId, contextId));
        writer.write(String.format("#%d=PRODUCT_DEFINITION_SHAPE('','',#%d);\n",
                productDefShapeId, productDefId));
        
        // Shape definition representation
        int shapeDefRepId = entityId;
        writer.write(String.format("#%d=SHAPE_DEFINITION_REPRESENTATION(#%d,#%d);\n",
                shapeDefRepId, productDefShapeId, shapeRepId));
        
        writer.write("ENDSEC;\n");
    }
    
    /**
     * Writes the ISO-10303-21 end marker ({@code END-ISO-10303-21;}).
     *
     * @param writer Output writer (must be open)
     * @throws IOException If the writer throws
     */
    private void writeFooter(BufferedWriter writer) throws IOException {
        writer.write("END-ISO-10303-21;\n");
    }
    
    /**
     * Exports the Aerospike truncated spike contour as a revolved STEP solid.
     *
     * <p>The spike contour (inner wall of the annular flow path) is revolved 360°
     * around the x-axis using the same SURFACE_OF_REVOLUTION encoding as
     * {@link #exportRevolvedSolid(NozzleContour, Path)}.
     *
     * @param nozzle   Aerospike nozzle (must have been generated)
     * @param filePath Destination STEP file path
     * @throws IOException If the file cannot be written
     */
    public void exportAerospikeRevolvedSolid(AerospikeNozzle nozzle, Path filePath)
            throws IOException {
        // getTruncatedSpikeContour() generates lazily, so the list is always non-empty after this call.
        List<Point2D> spike = nozzle.getTruncatedSpikeContour();
        LOG.debug("Exporting Aerospike STEP solid: {} spike points → {}", spike.size(), filePath);
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writeHeader(writer);
            writeData(writer, spike);
            writeFooter(writer);
        }
        LOG.debug("Aerospike STEP export complete → {}", filePath);
    }

    /**
     * Convenience overload that exports a Rao bell nozzle as a STEP revolved solid.
     *
     * @param nozzle   Rao bell nozzle (must have been generated)
     * @param filePath Destination STEP file path
     * @throws IllegalArgumentException if the nozzle has not been generated
     * @throws IOException              if the file cannot be written
     */
    public void exportRevolvedSolid(RaoNozzle nozzle, Path filePath) throws IOException {
        List<Point2D> pts = nozzle.getContourPoints();
        if (pts.size() < 2) {
            throw new IllegalArgumentException("RaoNozzle has no contour — call generate() first");
        }
        exportRevolvedSolid(NozzleContour.fromPoints(nozzle.getParameters(), pts), filePath);
    }

    /**
     * Convenience overload that exports a dual-bell nozzle (base bell + extension) as
     * a STEP revolved solid.  The full contour including the kink transition is exported.
     *
     * @param nozzle   Dual-bell nozzle (must have been generated)
     * @param filePath Destination STEP file path
     * @throws IllegalArgumentException if the nozzle has not been generated
     * @throws IOException              if the file cannot be written
     */
    public void exportRevolvedSolid(DualBellNozzle nozzle, Path filePath) throws IOException {
        List<Point2D> pts = nozzle.getContourPoints();
        if (pts.size() < 2) {
            throw new IllegalArgumentException("DualBellNozzle has no contour — call generate() first");
        }
        exportRevolvedSolid(NozzleContour.fromPoints(nozzle.getParameters(), pts), filePath);
    }

    /**
     * Exports the complete nozzle profile curve (convergent + divergent) to STEP
     * format as a B-spline curve.  Equivalent to
     * {@link #exportProfileCurve(NozzleContour, Path)} but uses the geometry-complete
     * wall-point list from {@link FullNozzleGeometry}.
     *
     * @param fullGeometry Full nozzle geometry (must have been generated)
     * @param filePath     Destination STEP file path
     * @throws IllegalStateException If {@code fullGeometry} has not been generated
     * @throws IOException           If the file cannot be written
     */
    public void exportProfileCurve(FullNozzleGeometry fullGeometry, Path filePath)
            throws IOException {
        List<Point2D> points = fullGeometry.getWallPoints();
        if (points.isEmpty()) {
            throw new IllegalStateException(
                    "FullNozzleGeometry has no wall points — call generate() first");
        }
        LOG.debug("Exporting geometry-complete STEP profile curve: {} wall points → {}",
                points.size(), filePath);
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writeHeader(writer);
            writer.write("DATA;\n");
            int entityId = 1;
            List<Integer> pointIds = new ArrayList<>();
            for (Point2D point : points) {
                writer.write(String.format("#%d=CARTESIAN_POINT('',(%.8f,%.8f,0.0));\n",
                        entityId, point.x() * scaleFactor, point.y() * scaleFactor));
                pointIds.add(entityId);
                entityId++;
            }
            StringBuilder ctrlPts = new StringBuilder();
            for (int i = 0; i < pointIds.size(); i++) {
                if (i > 0) ctrlPts.append(",");
                ctrlPts.append("#").append(pointIds.get(i));
            }
            int degree = Math.min(3, pointIds.size() - 1);
            writer.write(String.format(
                    "#%d=B_SPLINE_CURVE_WITH_KNOTS('Profile',%d,(%s),.UNSPECIFIED.,.F.,.F.,(),(),.UNSPECIFIED.);\n",
                    entityId, degree, ctrlPts));
            writer.write("ENDSEC;\n");
            writeFooter(writer);
        }
        LOG.debug("Geometry-complete STEP profile curve export complete → {}", filePath);
    }

    /**
     * Exports a simplified STEP file with just the profile curve.
     *
     * @param contour  Nozzle contour
     * @param filePath Output file path
     * @throws IOException If write fails
     */
    public void exportProfileCurve(NozzleContour contour, Path filePath) throws IOException {
        List<Point2D> points = contour.getContourPoints();
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Contour has no points");
        }

        LOG.debug("Exporting STEP profile curve: {} points → {}", points.size(), filePath);
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writeHeader(writer);
            
            writer.write("DATA;\n");
            
            int entityId = 1;
            
            // Write points
            List<Integer> pointIds = new ArrayList<>();
            for (Point2D point : points) {
                writer.write(String.format("#%d=CARTESIAN_POINT('',(%.8f,%.8f,0.0));\n",
                        entityId, point.x() * scaleFactor, point.y() * scaleFactor));
                pointIds.add(entityId);
                entityId++;
            }
            
            // Create B-spline curve
            StringBuilder ctrlPts = new StringBuilder();
            for (int i = 0; i < pointIds.size(); i++) {
                if (i > 0) ctrlPts.append(",");
                ctrlPts.append("#").append(pointIds.get(i));
            }
            
            int degree = Math.min(3, pointIds.size() - 1);
            writer.write(String.format("#%d=B_SPLINE_CURVE_WITH_KNOTS('Profile',%d,(%s),.UNSPECIFIED.,.F.,.F.,(),(),.UNSPECIFIED.);\n",
                    entityId, degree, ctrlPts));
            
            writer.write("ENDSEC;\n");
            writeFooter(writer);
        }
    }

    /**
     * Exports the truncated aerospike spike profile as a STEP B-spline curve.
     * The spike contour is exported in the r-x plane (y = 0 for the z-coordinate).
     *
     * @param nozzle   Aerospike nozzle (must have been generated)
     * @param filePath Destination STEP file path
     * @throws IllegalArgumentException if the nozzle has not been generated
     * @throws IOException              if the file cannot be written
     */
    public void exportProfileCurve(AerospikeNozzle nozzle, Path filePath) throws IOException {
        List<Point2D> points = nozzle.getTruncatedSpikeContour();
        if (points.isEmpty()) {
            throw new IllegalArgumentException("AerospikeNozzle has no contour — call generate() first");
        }
        LOG.debug("Exporting Aerospike STEP profile curve: {} spike points → {}", points.size(), filePath);
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writeHeader(writer);
            writer.write("DATA;\n");
            int entityId = 1;
            List<Integer> pointIds = new ArrayList<>();
            for (Point2D point : points) {
                writer.write(String.format("#%d=CARTESIAN_POINT('',(%.8f,%.8f,0.0));\n",
                        entityId, point.x() * scaleFactor, point.y() * scaleFactor));
                pointIds.add(entityId++);
            }
            StringBuilder ctrlPts = new StringBuilder();
            for (int i = 0; i < pointIds.size(); i++) {
                if (i > 0) ctrlPts.append(",");
                ctrlPts.append("#").append(pointIds.get(i));
            }
            int degree = Math.min(3, pointIds.size() - 1);
            writer.write(String.format(
                    "#%d=B_SPLINE_CURVE_WITH_KNOTS('SpikeProfile',%d,(%s),.UNSPECIFIED.,.F.,.F.,(),(),.UNSPECIFIED.);\n",
                    entityId, degree, ctrlPts));
            writer.write("ENDSEC;\n");
            writeFooter(writer);
        }
        LOG.debug("Aerospike STEP profile curve export complete → {}", filePath);
    }

    /**
     * Convenience overload that exports a Rao bell nozzle wall profile as a STEP B-spline curve.
     *
     * @param nozzle   Rao bell nozzle (must have been generated)
     * @param filePath Destination STEP file path
     * @throws IllegalArgumentException if the nozzle has not been generated
     * @throws IOException              if the file cannot be written
     */
    public void exportProfileCurve(RaoNozzle nozzle, Path filePath) throws IOException {
        List<Point2D> pts = nozzle.getContourPoints();
        if (pts.size() < 2) {
            throw new IllegalArgumentException("RaoNozzle has no contour — call generate() first");
        }
        exportProfileCurve(NozzleContour.fromPoints(nozzle.getParameters(), pts), filePath);
    }

    /**
     * Convenience overload that exports a dual-bell nozzle wall profile (base bell +
     * extension) as a STEP B-spline curve.
     *
     * @param nozzle   Dual-bell nozzle (must have been generated)
     * @param filePath Destination STEP file path
     * @throws IllegalArgumentException if the nozzle has not been generated
     * @throws IOException              if the file cannot be written
     */
    public void exportProfileCurve(DualBellNozzle nozzle, Path filePath) throws IOException {
        List<Point2D> pts = nozzle.getContourPoints();
        if (pts.size() < 2) {
            throw new IllegalArgumentException("DualBellNozzle has no contour — call generate() first");
        }
        exportProfileCurve(NozzleContour.fromPoints(nozzle.getParameters(), pts), filePath);
    }
}
