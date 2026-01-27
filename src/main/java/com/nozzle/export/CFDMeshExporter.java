package com.nozzle.export;

import com.nozzle.geometry.NozzleContour;
import com.nozzle.geometry.Point2D;
import com.nozzle.moc.CharacteristicNet;
import com.nozzle.moc.CharacteristicPoint;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Exports nozzle geometry in formats suitable for CFD mesh generation.
 * Supports OpenFOAM blockMesh, CGNS, and Gmsh formats.
 */
public class CFDMeshExporter {
    
    /**
     * Mesh export format.
     */
    public enum Format {
        OPENFOAM_BLOCKMESH,
        GMSH_GEO,
        CGNS,
        PLOT3D
    }
    
    private int axialCells = 100;
    private int radialCells = 50;
    private int circumferentialCells = 36;
    private double firstLayerThickness = 1e-6;
    private double expansionRatio = 1.2;
    
    public CFDMeshExporter setAxialCells(int cells) {
        this.axialCells = cells;
        return this;
    }
    
    public CFDMeshExporter setRadialCells(int cells) {
        this.radialCells = cells;
        return this;
    }
    
    public CFDMeshExporter setCircumferentialCells(int cells) {
        this.circumferentialCells = cells;
        return this;
    }
    
    public CFDMeshExporter setBoundaryLayerParams(double firstLayer, double expansion) {
        this.firstLayerThickness = firstLayer;
        this.expansionRatio = expansion;
        return this;
    }
    
    /**
     * Exports mesh definition in specified format.
     */
    public void export(NozzleContour contour, Path filePath, Format format) throws IOException {
        switch (format) {
            case OPENFOAM_BLOCKMESH -> exportOpenFOAM(contour, filePath);
            case GMSH_GEO -> exportGmsh(contour, filePath);
            case PLOT3D -> exportPlot3D(contour, filePath);
            case CGNS -> exportCGNS(contour, filePath);
        }
    }
    
    /**
     * Exports OpenFOAM blockMeshDict format.
     */
    public void exportOpenFOAM(NozzleContour contour, Path filePath) throws IOException {
        List<Point2D> points = contour.getContourPoints();
        if (points.size() < 2) {
            throw new IllegalArgumentException("Contour needs at least 2 points");
        }
        
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("FoamFile\n{\n");
            writer.write("    version     2.0;\n");
            writer.write("    format      ascii;\n");
            writer.write("    class       dictionary;\n");
            writer.write("    object      blockMeshDict;\n");
            writer.write("}\n\n");
            
            writer.write("convertToMeters 1.0;\n\n");
            
            // Vertices - create wedge geometry
            writer.write("vertices\n(\n");
            double wedgeAngle = Math.toRadians(2.5); // 5-degree wedge total
            
            int vertexId = 0;
            for (Point2D point : points) {
                double x = point.x();
                double r = point.y();
                double y = r * Math.cos(wedgeAngle);
                double zPos = r * Math.sin(wedgeAngle);
                double zNeg = -zPos;
                
                writer.write(String.format("    (%.8f %.8f %.8f) // %d\n", x, y, zPos, vertexId++));
                writer.write(String.format("    (%.8f %.8f %.8f) // %d\n", x, y, zNeg, vertexId++));
            }
            
            // Axis vertices
            writer.write(String.format("    (%.8f 0 0) // %d axis-inlet\n", 
                    points.getFirst().x(), vertexId++));
            writer.write(String.format("    (%.8f 0 0) // %d axis-outlet\n", 
                    points.getLast().x(), vertexId++));
            
            writer.write(");\n\n");
            
            // Blocks
            writer.write("blocks\n(\n");
            writer.write(String.format("    hex (0 2 3 1 %d %d %d %d) (%d %d 1)\n",
                    vertexId - 2, vertexId - 1, vertexId - 1, vertexId - 2,
                    axialCells, radialCells));
            writer.write("    simpleGrading (\n");
            writer.write("        1\n");
            writer.write(String.format("        ((0.5 0.5 %.2f) (0.5 0.5 %.2f))\n",
                    expansionRatio, 1.0 / expansionRatio));
            writer.write("        1\n");
            writer.write("    )\n");
            writer.write(");\n\n");
            
            // Edges (use spline for wall)
            writer.write("edges\n(\n");
            writer.write("    spline 0 2 (\n");
            for (int i = 1; i < points.size() - 1; i++) {
                Point2D p = points.get(i);
                double y = p.y() * Math.cos(wedgeAngle);
                double z = p.y() * Math.sin(wedgeAngle);
                writer.write(String.format("        (%.8f %.8f %.8f)\n", p.x(), y, z));
            }
            writer.write("    )\n");
            writer.write(");\n\n");
            
            // Boundary patches
            writer.write("boundary\n(\n");
            
            writer.write("    inlet\n    {\n");
            writer.write("        type patch;\n");
            writer.write("        faces ((0 1 %d %d));\n".formatted(vertexId - 2, vertexId - 2));
            writer.write("    }\n\n");
            
            writer.write("    outlet\n    {\n");
            writer.write("        type patch;\n");
            writer.write("        faces ((2 3 %d %d));\n".formatted(vertexId - 1, vertexId - 1));
            writer.write("    }\n\n");
            
            writer.write("    wall\n    {\n");
            writer.write("        type wall;\n");
            writer.write("        faces ((0 2 3 1));\n");
            writer.write("    }\n\n");
            
            writer.write("    axis\n    {\n");
            writer.write("        type empty;\n");
            writer.write("        faces ((%d %d 3 1) (0 2 %d %d));\n"
                    .formatted(vertexId - 2, vertexId - 1, vertexId - 1, vertexId - 2));
            writer.write("    }\n\n");
            
            writer.write("    wedge0\n    {\n");
            writer.write("        type wedge;\n");
            writer.write("        faces ((0 2 %d %d));\n".formatted(vertexId - 1, vertexId - 2));
            writer.write("    }\n\n");
            
            writer.write("    wedge1\n    {\n");
            writer.write("        type wedge;\n");
            writer.write("        faces ((1 3 %d %d));\n".formatted(vertexId - 1, vertexId - 2));
            writer.write("    }\n");
            
            writer.write(");\n\n");
            
            writer.write("mergePatchPairs\n(\n);\n");
        }
    }
    
    /**
     * Exports Gmsh .geo format.
     */
    public void exportGmsh(NozzleContour contour, Path filePath) throws IOException {
        List<Point2D> points = contour.getContourPoints();
        
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("// Gmsh geometry file for supersonic nozzle\n");
            writer.write("// Generated by Nozzle MOC Design Tool\n\n");
            
            writer.write(String.format("lc = %.6f; // Characteristic length\n\n",
                    points.getLast().y() / radialCells));
            
            // Points
            int pointId = 1;
            List<Integer> wallPointIds = new ArrayList<>();
            List<Integer> axisPointIds = new ArrayList<>();
            
            // Wall points
            for (Point2D point : points) {
                writer.write(String.format("Point(%d) = {%.8f, %.8f, 0, lc};\n",
                        pointId, point.x(), point.y()));
                wallPointIds.add(pointId);
                pointId++;
            }
            
            // Axis points
            writer.write(String.format("Point(%d) = {%.8f, 0, 0, lc};\n",
                    pointId, points.getFirst().x()));
            axisPointIds.add(pointId);
            pointId++;
            
            writer.write(String.format("Point(%d) = {%.8f, 0, 0, lc};\n",
                    pointId, points.getLast().x()));
            axisPointIds.add(pointId);
            pointId++;
            
            writer.write("\n");
            
            // Lines
            int lineId = 1;
            
            // Wall spline
            StringBuilder wallPts = new StringBuilder();
            for (int i = 0; i < wallPointIds.size(); i++) {
                if (i > 0) wallPts.append(", ");
                wallPts.append(wallPointIds.get(i));
            }
            writer.write(String.format("Spline(%d) = {%s}; // Wall\n", lineId, wallPts));
            int wallLine = lineId++;
            
            // Inlet
            writer.write(String.format("Line(%d) = {%d, %d}; // Inlet\n",
                    lineId, axisPointIds.getFirst(), wallPointIds.getFirst()));
            int inletLine = lineId++;
            
            // Outlet
            writer.write(String.format("Line(%d) = {%d, %d}; // Outlet\n",
                    lineId, wallPointIds.getLast(), axisPointIds.getLast()));
            int outletLine = lineId++;
            
            // Axis
            writer.write(String.format("Line(%d) = {%d, %d}; // Axis\n",
                    lineId, axisPointIds.getLast(), axisPointIds.getFirst()));
            int axisLine = lineId++;
            
            writer.write("\n");
            
            // Surface
            writer.write(String.format("Curve Loop(1) = {%d, %d, %d, %d};\n",
                    inletLine, wallLine, outletLine, axisLine));
            writer.write("Plane Surface(1) = {1};\n\n");
            
            // Physical groups
            writer.write("Physical Curve(\"inlet\") = {" + inletLine + "};\n");
            writer.write("Physical Curve(\"outlet\") = {" + outletLine + "};\n");
            writer.write("Physical Curve(\"wall\") = {" + wallLine + "};\n");
            writer.write("Physical Curve(\"axis\") = {" + axisLine + "};\n");
            writer.write("Physical Surface(\"fluid\") = {1};\n\n");
            
            // Mesh parameters
            writer.write("// Mesh parameters\n");
            writer.write(String.format("Transfinite Curve {%d} = %d Using Progression 1;\n",
                    axisLine, axialCells + 1));
            writer.write(String.format("Transfinite Curve {%d} = %d Using Progression 1;\n",
                    wallLine, axialCells + 1));
            writer.write(String.format("Transfinite Curve {%d, %d} = %d Using Progression %.2f;\n",
                    inletLine, outletLine, radialCells + 1, expansionRatio));
            writer.write("Transfinite Surface {1};\n");
            writer.write("Recombine Surface {1};\n");
        }
    }
    
    /**
     * Exports Plot3D format for structured grids.
     */
    public void exportPlot3D(NozzleContour contour, Path filePath) throws IOException {
        List<Point2D> points = contour.getContourPoints();
        
        // Generate 2D structured grid
        int ni = axialCells + 1;
        int nj = radialCells + 1;
        
        double[][] x = new double[ni][nj];
        double[][] y = new double[ni][nj];
        
        // Interpolate contour to uniform spacing
        double xMin = points.getFirst().x();
        double xMax = points.getLast().x();
        
        for (int i = 0; i < ni; i++) {
            double xi = xMin + (double) i / (ni - 1) * (xMax - xMin);
            double rWall = contour.getRadiusAt(xi);
            
            for (int j = 0; j < nj; j++) {
                double eta = (double) j / (nj - 1);
                // Apply stretching
                eta = 1.0 - Math.pow(1.0 - eta, expansionRatio);
                
                x[i][j] = xi;
                y[i][j] = eta * rWall;
            }
        }
        
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("1\n"); // Number of blocks
            writer.write(String.format("%d %d 1\n", ni, nj)); // Grid dimensions
            
            // X coordinates
            for (int j = 0; j < nj; j++) {
                for (int i = 0; i < ni; i++) {
                    writer.write(String.format("%.8e ", x[i][j]));
                    if ((i + 1) % 5 == 0) writer.write("\n");
                }
            }
            writer.write("\n");
            
            // Y coordinates
            for (int j = 0; j < nj; j++) {
                for (int i = 0; i < ni; i++) {
                    writer.write(String.format("%.8e ", y[i][j]));
                    if ((i + 1) % 5 == 0) writer.write("\n");
                }
            }
            writer.write("\n");
            
            // Z coordinates (all zeros for 2D)
            for (int j = 0; j < nj; j++) {
                for (int i = 0; i < ni; i++) {
                    writer.write("0.0 ");
                    if ((i + 1) % 5 == 0) writer.write("\n");
                }
            }
        }
    }
    
    /**
     * Exports CGNS format (simplified text-based representation).
     */
    public void exportCGNS(NozzleContour contour, Path filePath) throws IOException {
        // CGNS is binary; export a simplified text version with grid coordinates
        exportPlot3D(contour, filePath);
    }
}
