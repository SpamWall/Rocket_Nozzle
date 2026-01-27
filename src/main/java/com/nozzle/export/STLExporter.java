package com.nozzle.export;

import com.nozzle.geometry.NozzleContour;
import com.nozzle.geometry.Point2D;

import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Exports nozzle geometry to STL format for 3D printing and CFD meshing.
 * Generates a revolved 3D mesh from the 2D contour.
 */
public class STLExporter {
    
    private int circumferentialSegments = 72; // 5-degree increments
    private double scaleFactor = 1000.0; // Convert m to mm
    private boolean binaryFormat = true;
    
    public STLExporter setCircumferentialSegments(int segments) {
        this.circumferentialSegments = segments;
        return this;
    }
    
    public STLExporter setScaleFactor(double scale) {
        this.scaleFactor = scale;
        return this;
    }
    
    public STLExporter setBinaryFormat(boolean binary) {
        this.binaryFormat = binary;
        return this;
    }
    
    /**
     * Exports nozzle as revolved STL mesh.
     *
     * @param contour  Nozzle contour
     * @param filePath Output file path
     * @throws IOException If write fails
     */
    public void exportMesh(NozzleContour contour, Path filePath) throws IOException {
        List<Point2D> points = contour.getContourPoints();
        if (points.size() < 2) {
            throw new IllegalArgumentException("Contour needs at least 2 points");
        }
        
        List<Triangle> triangles = generateTriangles(points);
        
        if (binaryFormat) {
            exportBinarySTL(triangles, filePath);
        } else {
            exportAsciiSTL(triangles, filePath);
        }
    }
    
    /**
     * Exports inner surface mesh for CFD boundary.
     */
    public void exportInnerSurfaceMesh(NozzleContour contour, Path filePath) throws IOException {
        exportMesh(contour, filePath);
    }
    
    /**
     * Generates triangles by revolving the 2D profile.
     */
    private List<Triangle> generateTriangles(List<Point2D> profile) {
        List<Triangle> triangles = new ArrayList<>();
        double angleStep = 2 * Math.PI / circumferentialSegments;
        
        for (int i = 0; i < profile.size() - 1; i++) {
            Point2D p1 = profile.get(i);
            Point2D p2 = profile.get(i + 1);
            
            for (int j = 0; j < circumferentialSegments; j++) {
                double theta1 = j * angleStep;
                double theta2 = (j + 1) * angleStep;
                
                // Four corners of the quad
                Point3D v1 = revolvePoint(p1, theta1);
                Point3D v2 = revolvePoint(p1, theta2);
                Point3D v3 = revolvePoint(p2, theta2);
                Point3D v4 = revolvePoint(p2, theta1);
                
                // Split quad into two triangles (inward-facing normals)
                Point3D normal1 = calculateNormal(v1, v3, v2);
                Point3D normal2 = calculateNormal(v1, v4, v3);
                
                triangles.add(new Triangle(normal1, v1, v3, v2));
                triangles.add(new Triangle(normal2, v1, v4, v3));
            }
        }
        
        // Cap the ends if the profile doesn't close at axis
        Point2D firstPoint = profile.getFirst();
        Point2D lastPoint = profile.getLast();
        
        // Start cap (if not on axis)
        if (firstPoint.y() > 1e-6) {
            addEndCap(triangles, firstPoint, true);
        }
        
        // End cap (if not on axis)  
        if (lastPoint.y() > 1e-6) {
            addEndCap(triangles, lastPoint, false);
        }
        
        return triangles;
    }
    
    /**
     * Adds an end cap at the specified profile point.
     */
    private void addEndCap(List<Triangle> triangles, Point2D point, boolean isStart) {
        double angleStep = 2 * Math.PI / circumferentialSegments;
        Point3D center = new Point3D(point.x() * scaleFactor, 0, 0);
        Point3D normal = isStart ? new Point3D(-1, 0, 0) : new Point3D(1, 0, 0);
        
        for (int j = 0; j < circumferentialSegments; j++) {
            double theta1 = j * angleStep;
            double theta2 = (j + 1) * angleStep;
            
            Point3D v1 = revolvePoint(point, theta1);
            Point3D v2 = revolvePoint(point, theta2);
            
            if (isStart) {
                triangles.add(new Triangle(normal, center, v2, v1));
            } else {
                triangles.add(new Triangle(normal, center, v1, v2));
            }
        }
    }
    
    /**
     * Revolves a 2D point around the x-axis.
     */
    private Point3D revolvePoint(Point2D point, double theta) {
        double x = point.x() * scaleFactor;
        double r = point.y() * scaleFactor;
        double y = r * Math.cos(theta);
        double z = r * Math.sin(theta);
        return new Point3D(x, y, z);
    }
    
    /**
     * Calculates the normal vector for a triangle.
     */
    private Point3D calculateNormal(Point3D v1, Point3D v2, Point3D v3) {
        // Edge vectors
        double ax = v2.x - v1.x;
        double ay = v2.y - v1.y;
        double az = v2.z - v1.z;
        
        double bx = v3.x - v1.x;
        double by = v3.y - v1.y;
        double bz = v3.z - v1.z;
        
        // Cross product
        double nx = ay * bz - az * by;
        double ny = az * bx - ax * bz;
        double nz = ax * by - ay * bx;
        
        // Normalize
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1e-10) {
            nx /= len;
            ny /= len;
            nz /= len;
        }
        
        return new Point3D(nx, ny, nz);
    }
    
    /**
     * Exports triangles to binary STL format.
     */
    private void exportBinarySTL(List<Triangle> triangles, Path filePath) throws IOException {
        try (OutputStream os = Files.newOutputStream(filePath);
             DataOutputStream dos = new DataOutputStream(os)) {
            
            // 80-byte header
            byte[] header = new byte[80];
            String headerText = "Supersonic Nozzle MOC - Binary STL";
            System.arraycopy(headerText.getBytes(), 0, header, 0, 
                    Math.min(headerText.length(), 80));
            dos.write(header);
            
            // Number of triangles (little-endian)
            ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            bb.putInt(triangles.size());
            dos.write(bb.array());
            
            // Write triangles
            for (Triangle tri : triangles) {
                writeFloatLE(dos, (float) tri.normal.x);
                writeFloatLE(dos, (float) tri.normal.y);
                writeFloatLE(dos, (float) tri.normal.z);
                
                writeFloatLE(dos, (float) tri.v1.x);
                writeFloatLE(dos, (float) tri.v1.y);
                writeFloatLE(dos, (float) tri.v1.z);
                
                writeFloatLE(dos, (float) tri.v2.x);
                writeFloatLE(dos, (float) tri.v2.y);
                writeFloatLE(dos, (float) tri.v2.z);
                
                writeFloatLE(dos, (float) tri.v3.x);
                writeFloatLE(dos, (float) tri.v3.y);
                writeFloatLE(dos, (float) tri.v3.z);
                
                // Attribute byte count
                dos.writeShort(0);
            }
        }
    }
    
    private void writeFloatLE(DataOutputStream dos, float value) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        bb.putFloat(value);
        dos.write(bb.array());
    }
    
    /**
     * Exports triangles to ASCII STL format.
     */
    private void exportAsciiSTL(List<Triangle> triangles, Path filePath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("solid nozzle\n");
            
            for (Triangle tri : triangles) {
                writer.write(String.format("  facet normal %.6e %.6e %.6e\n",
                        tri.normal.x, tri.normal.y, tri.normal.z));
                writer.write("    outer loop\n");
                writer.write(String.format("      vertex %.6e %.6e %.6e\n",
                        tri.v1.x, tri.v1.y, tri.v1.z));
                writer.write(String.format("      vertex %.6e %.6e %.6e\n",
                        tri.v2.x, tri.v2.y, tri.v2.z));
                writer.write(String.format("      vertex %.6e %.6e %.6e\n",
                        tri.v3.x, tri.v3.y, tri.v3.z));
                writer.write("    endloop\n");
                writer.write("  endfacet\n");
            }
            
            writer.write("endsolid nozzle\n");
        }
    }
    
    /**
     * Gets the estimated number of triangles for a contour.
     */
    public int estimateTriangleCount(int profilePoints) {
        return 2 * (profilePoints - 1) * circumferentialSegments + 2 * circumferentialSegments;
    }
    
    private record Point3D(double x, double y, double z) {}
    
    private record Triangle(Point3D normal, Point3D v1, Point3D v2, Point3D v3) {}
}
