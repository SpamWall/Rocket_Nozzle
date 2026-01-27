package com.nozzle.export;

import com.nozzle.geometry.NozzleContour;
import com.nozzle.geometry.Point2D;

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
    
    private double scaleFactor = 1.0; // STEP uses meters by default
    private String authorName = "Supersonic Nozzle MOC";
    private String organizationName = "Nozzle Design Tool";
    
    public STEPExporter setScaleFactor(double scale) {
        this.scaleFactor = scale;
        return this;
    }
    
    public STEPExporter setAuthor(String author) {
        this.authorName = author;
        return this;
    }
    
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
        
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writeHeader(writer);
            writeData(writer, points);
            writeFooter(writer);
        }
    }
    
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
        int shapeDefRepId = entityId++;
        writer.write(String.format("#%d=SHAPE_DEFINITION_REPRESENTATION(#%d,#%d);\n",
                shapeDefRepId, productDefShapeId, shapeRepId));
        
        writer.write("ENDSEC;\n");
    }
    
    private void writeFooter(BufferedWriter writer) throws IOException {
        writer.write("END-ISO-10303-21;\n");
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
}
