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

    /** Creates an {@code STLExporter} with default settings (72 segments, binary format, metre-to-mm scale). */
    public STLExporter() {}

    /** Number of circumferential segments used when revolving the 2D profile (default: 72 = 5° steps). */
    private int circumferentialSegments = 72;
    /** Scale factor applied to all coordinates before writing (default: 1000 → metres to mm). */
    private double scaleFactor = 1000.0;
    /** When {@code true} (default), write binary STL; when {@code false}, write ASCII STL. */
    private boolean binaryFormat = true;

    /**
     * Sets the number of circumferential segments used when revolving the 2D
     * profile to produce the 3D mesh.  Higher values give a smoother surface
     * at the cost of larger file size.
     *
     * @param segments Number of angular divisions (e.g. 72 for 5° steps)
     * @return This instance for method chaining
     */
    public STLExporter setCircumferentialSegments(int segments) {
        this.circumferentialSegments = segments;
        return this;
    }

    /**
     * Sets the coordinate scale factor applied to all exported vertices.
     * The default (1000) converts metres to millimetres.
     *
     * @param scale Multiplicative scale factor
     * @return This instance for method chaining
     */
    public STLExporter setScaleFactor(double scale) {
        this.scaleFactor = scale;
        return this;
    }

    /**
     * Selects between binary (default) and ASCII STL output.
     * Binary files are typically five to ten times smaller.
     *
     * @param binary {@code true} for binary STL; {@code false} for ASCII STL
     * @return This instance for method chaining
     */
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
     * Convenience method that exports the inner (flow-wetted) surface mesh,
     * equivalent to {@link #exportMesh(NozzleContour, Path)}.
     * The inward-facing normals produced by {@link #generateTriangles} make the
     * mesh directly usable as a CFD wall boundary patch.
     *
     * @param contour  Nozzle contour to revolve
     * @param filePath Destination STL file path
     * @throws IOException If the file cannot be written
     */
    public void exportInnerSurfaceMesh(NozzleContour contour, Path filePath) throws IOException {
        exportMesh(contour, filePath);
    }
    
    /**
     * Generates the full triangle list by revolving the 2D profile around the
     * x-axis.  Each axial segment of the profile contributes
     * {@code 2 × circumferentialSegments} triangles.  Annular end caps are added
     * at the inlet and outlet if the profile does not close on the axis
     * (radius &gt; 1 µm).
     *
     * @param profile Ordered list of 2D profile points (at least 2 required)
     * @return List of {@link Triangle}s with inward-facing unit normals
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
     * Adds an annular fan of triangles that caps the open end of the revolved
     * mesh at the given profile point.  The normal points in the −x direction
     * for the inlet cap and in the +x direction for the outlet cap.
     *
     * @param triangles Accumulator list to which the cap triangles are appended
     * @param point     Profile point at the cap location (only x and y are used)
     * @param isStart   {@code true} to generate an inlet (−x normal) cap;
     *                  {@code false} for an outlet (+x normal) cap
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
     * Maps a 2D profile point to a 3D point by revolving around the x-axis.
     * Coordinates are scaled by {@link #scaleFactor} before revolution.
     *
     * @param point 2D point with axial (x) and radial (y) coordinates in metres
     * @param theta Circumferential angle in radians
     * @return Scaled 3D Cartesian point after revolution
     */
    private Point3D revolvePoint(Point2D point, double theta) {
        double x = point.x() * scaleFactor;
        double r = point.y() * scaleFactor;
        double y = r * Math.cos(theta);
        double z = r * Math.sin(theta);
        return new Point3D(x, y, z);
    }
    
    /**
     * Calculates the unit outward-normal vector for the triangle formed by
     * {@code v1}, {@code v2}, {@code v3} using the cross product
     * {@code (v2 − v1) × (v3 − v1)}.
     * Returns a zero vector if the triangle is degenerate (edge length &lt; 10⁻¹⁰).
     *
     * @param v1 First vertex
     * @param v2 Second vertex
     * @param v3 Third vertex
     * @return Unit normal vector (or zero vector for degenerate triangles)
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
     * Writes the triangle list to a binary STL file (little-endian IEEE 754
     * single-precision floats).  The 80-byte header contains a plain-text
     * description and the 4-byte triangle count precedes the triangle records.
     * Each record is 50 bytes: normal (12 B) + 3 vertices (36 B) + attribute (2 B).
     *
     * @param triangles Ordered list of triangles to write
     * @param filePath  Destination file path
     * @throws IOException If the file cannot be written
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
    
    /**
     * Writes a single {@code float} value to {@code dos} in little-endian byte order.
     *
     * @param dos   Output stream to write to
     * @param value Float value to write
     * @throws IOException If the stream throws
     */
    private void writeFloatLE(DataOutputStream dos, float value) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        bb.putFloat(value);
        dos.write(bb.array());
    }
    
    /**
     * Writes the triangle list to an ASCII STL file using the standard
     * {@code solid / facet normal / outer loop / vertex / endloop / endfacet / endsolid}
     * syntax.  ASCII STL is human-readable but roughly 5–10× larger than binary.
     *
     * @param triangles Ordered list of triangles to write
     * @param filePath  Destination file path
     * @throws IOException If the file cannot be written
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
     * Returns the estimated total triangle count for a revolution mesh with the
     * given number of profile points.  Includes the lateral wall triangles and
     * both end-cap fans:
     * {@code 2 × (profilePoints − 1) × segments + 2 × segments}.
     *
     * @param profilePoints Number of 2D profile points
     * @return Estimated triangle count
     */
    public int estimateTriangleCount(int profilePoints) {
        return 2 * (profilePoints - 1) * circumferentialSegments + 2 * circumferentialSegments;
    }
    
    /**
     * Immutable 3D Cartesian point used as STL vertex and normal data.
     *
     * @param x X-coordinate (axial), scaled to the export unit
     * @param y Y-coordinate, scaled to the export unit
     * @param z Z-coordinate, scaled to the export unit
     */
    private record Point3D(double x, double y, double z) {}

    /**
     * Immutable STL facet comprising a unit outward normal and three vertices.
     *
     * @param normal Unit outward normal vector of the facet
     * @param v1     First vertex
     * @param v2     Second vertex (winding order: right-hand rule for outward normal)
     * @param v3     Third vertex
     */
    private record Triangle(Point3D normal, Point3D v1, Point3D v2, Point3D v3) {}
}
