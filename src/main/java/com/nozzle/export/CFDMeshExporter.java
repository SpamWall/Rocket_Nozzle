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

import com.nozzle.geometry.NozzleContour;
import com.nozzle.geometry.Point2D;
import com.nozzle.moc.AerospikeNozzle;

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

    /** Creates a {@code CFDMeshExporter} with default settings. */
    public CFDMeshExporter() {}

    /**
     * Supported CFD mesh output formats.
     */
    public enum Format {
        /** OpenFOAM {@code blockMeshDict} with a 5° axisymmetric wedge topology. */
        OPENFOAM_BLOCKMESH,
        /** Gmsh {@code .geo} script with transfinite surface mesh instructions. */
        GMSH_GEO,
        /**
         * CFD General Notation System (CGNS) format.
         * Currently delegated to the {@link #PLOT3D} exporter as a text-based
         * structured-grid approximation.
         */
        CGNS,
        /** Plot3D structured multiblock grid (ASCII, single block, 2-D + z = 0 plane). */
        PLOT3D
    }

    /** Number of cells in the axial (flow) direction (default: 100). */
    private int axialCells = 100;
    /** Number of cells in the radial direction (default: 50). */
    private int radialCells = 50;
    /** Cell-size expansion ratio from wall to interior (default: 1.2). */
    private double expansionRatio = 1.2;
    /**
     * First cell height y₁ in metres for y⁺-controlled grading.
     * When positive, overrides {@link #expansionRatio} in all export methods.
     * Zero means disabled (use {@link #expansionRatio} directly).
     */
    private double firstLayerThickness = 0.0;

    /**
     * Sets the number of cells in the axial (flow) direction.
     *
     * @param cells Axial cell count (&gt; 0)
     * @return This instance for method chaining
     */
    public CFDMeshExporter setAxialCells(int cells) {
        this.axialCells = cells;
        return this;
    }

    /**
     * Sets the number of cells in the radial direction.
     *
     * @param cells Radial cell count (&gt; 0)
     * @return This instance for method chaining
     */
    public CFDMeshExporter setRadialCells(int cells) {
        this.radialCells = cells;
        return this;
    }

    /**
     * Sets the cell-size expansion ratio from the wall to the interior, used for
     * radial grading in all exported mesh formats.
     * Has no effect when {@link #setFirstLayerThickness(double)} is also set.
     *
     * @param expansion Cell-size expansion ratio (e.g. 1.2 clusters cells toward the wall)
     * @return This instance for method chaining
     */
    public CFDMeshExporter setExpansionRatio(double expansion) {
        this.expansionRatio = expansion;
        return this;
    }

    /**
     * Sets the first cell height y₁ (metres) for y⁺-controlled radial grading,
     * overriding the fixed {@link #setExpansionRatio(double) expansionRatio}.
     * The domain height H used is the exit radius for bell nozzles and the
     * annular gap (r_cowl − r_spike_root) for aerospike nozzles.
     * The grading derived per format is:
     * <ul>
     *   <li><b>OpenFOAM blockMesh</b>: expansion ratio g = H / y₁</li>
     *   <li><b>Gmsh transfinite progression</b>: r = (H / y₁)<sup>1/N</sup>,
     *       where N = {@link #setRadialCells(int) radialCells}</li>
     *   <li><b>Plot3D power-law exponent</b>: p = ln(H / y₁) / ln(N)</li>
     * </ul>
     *
     * @param t First cell height in metres (must be positive)
     * @return This instance for method chaining
     * @throws IllegalArgumentException if {@code t} is not positive
     */
    public CFDMeshExporter setFirstLayerThickness(double t) {
        if (t <= 0) throw new IllegalArgumentException(
                "firstLayerThickness must be positive, got: " + t);
        this.firstLayerThickness = t;
        return this;
    }
    
    // -------------------------------------------------------------------------
    // y⁺-grading helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the OpenFOAM blockMesh expansion ratio for a given domain height.
     * When {@link #firstLayerThickness} is set: g = H / y₁ (strong-grading approximation
     * where the last-to-first cell ratio equals the domain-to-first-cell ratio).
     * Falls back to {@link #expansionRatio} otherwise.
     */
    private double blockMeshGrading(double domainHeight) {
        if (firstLayerThickness <= 0) return expansionRatio;
        return Math.max(1.0, domainHeight / firstLayerThickness);
    }

    /**
     * Returns the Gmsh {@code Using Progression} cell-to-cell ratio r for a given
     * domain height.  r = (H / y₁)<sup>1/N</sup> where N = {@link #radialCells}.
     * Falls back to {@link #expansionRatio} otherwise.
     */
    private double gmshProgression(double domainHeight) {
        if (firstLayerThickness <= 0) return expansionRatio;
        double ratio = Math.max(1.0, domainHeight / firstLayerThickness);
        return Math.pow(ratio, 1.0 / radialCells);
    }

    /**
     * Returns the Plot3D power-law stretching exponent p for a given domain height.
     * The exponent satisfies y₁ = H · (1/N)<sup>p</sup>, giving
     * p = ln(H / y₁) / ln(N) where N = {@link #radialCells}.
     * Falls back to {@link #expansionRatio} otherwise.
     */
    private double plot3dExponent(double domainHeight) {
        if (firstLayerThickness <= 0) return expansionRatio;
        double ratio = Math.max(1.0, domainHeight / firstLayerThickness);
        return Math.log(ratio) / Math.log(radialCells);
    }

    /**
     * Dispatches the mesh export to the appropriate format-specific method.
     *
     * @param contour  Nozzle contour whose points define the flow domain wall
     * @param filePath Destination file path
     * @param format   Target mesh format
     * @throws IOException If the file cannot be written
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
     * Exports an OpenFOAM {@code blockMeshDict} for an axisymmetric 5° wedge mesh.
     * The mesh uses a single hex block with a spline edge along the wall profile
     * and six boundary patches: {@code inlet}, {@code outlet}, {@code wall},
     * {@code axis}, {@code wedge0}, and {@code wedge1}.
     *
     * @param contour  Nozzle contour providing at least 2 wall points
     * @param filePath Destination file path
     * @throws IOException              If the file cannot be written
     * @throws IllegalArgumentException If the contour has fewer than 2 points
     */
    public void exportOpenFOAM(NozzleContour contour, Path filePath) throws IOException {
        List<Point2D> points = contour.getContourPoints();
        if (points.size() < 2) {
            throw new IllegalArgumentException("Contour needs at least 2 points");
        }
        
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("FoamFile\n{\n");
            writer.write("    version     2.0;\n");
            writer.write("    format      ASCII;\n");
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
            double g = blockMeshGrading(points.getLast().y());
            writer.write("    simpleGrading (\n");
            writer.write("        1\n");
            writer.write(String.format("        ((0.5 0.5 %.2f) (0.5 0.5 %.2f))\n",
                    g, 1.0 / g));
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
     * Exports a Gmsh {@code .geo} script for a structured 2-D axisymmetric mesh.
     * The script defines wall points as a Spline, closes the domain with Inlet,
     * Outlet, and Axis lines, and applies {@code Transfinite} / {@code Recombine}
     * directives to produce a mapped quadrilateral surface mesh.
     * Physical groups {@code inlet}, {@code outlet}, {@code wall}, {@code axis},
     * and {@code fluid} are exported for boundary-condition assignment.
     *
     * @param contour  Nozzle contour providing the wall geometry
     * @param filePath Destination {@code .geo} file path
     * @throws IOException If the file cannot be written
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
            int axisLine = lineId;
            
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
            double r = gmshProgression(points.getLast().y());
            writer.write(String.format("Transfinite Curve {%d, %d} = %d Using Progression %.4f;\n",
                    inletLine, outletLine, radialCells + 1, r));
            writer.write("Transfinite Surface {1};\n");
            writer.write("Recombine Surface {1};\n");
        }
    }

    /**
     * Exports a 2-D structured grid in ASCII Plot3D format (single block, z = 0).
     * The grid is generated by linearly mapping each axial station from the axis
     * to the wall with a power-law radial stretching controlled by
     * {@link #expansionRatio} or, when {@link #firstLayerThickness} is set, by a
     * y⁺-derived exponent.  Coordinates are written in Fortran row-major order
     * (j-loop outer, i-loop inner, 5 values per line).
     *
     * @param contour  Nozzle contour used to evaluate the wall radius at each axial station
     * @param filePath Destination file path
     * @throws IOException If the file cannot be written
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
        double exitRadius = points.getLast().y();
        double p = plot3dExponent(exitRadius);

        for (int i = 0; i < ni; i++) {
            double xi = xMin + (double) i / (ni - 1) * (xMax - xMin);
            double rWall = contour.getRadiusAt(xi);

            for (int j = 0; j < nj; j++) {
                double eta = (double) j / (nj - 1);
                // Apply wall-clustering power-law stretch: η' = 1 − (1−η)^p
                eta = 1.0 - Math.pow(1.0 - eta, p);

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
     * Exports a CGNS-compatible structured grid.
     *
     * <p><b>Note:</b> True CGNS requires a binary HDF5 or ADF file.  This
     * implementation delegates to {@link #exportPlot3D} and writes an ASCII
     * structured-grid approximation instead, which can be converted to full CGNS
     * with external tools such as {@code cgns_convert}.
     *
     * @param contour  Nozzle contour providing wall geometry
     * @param filePath Destination file path
     * @throws IOException If the file cannot be written
     */
    public void exportCGNS(NozzleContour contour, Path filePath) throws IOException {
        // CGNS is binary; export a simplified text version with grid coordinates
        exportPlot3D(contour, filePath);
    }

    // -------------------------------------------------------------------------
    // Aerospike mesh export
    // -------------------------------------------------------------------------

    /**
     * Dispatches the Aerospike mesh export to the appropriate format-specific method.
     *
     * <p>The Aerospike flow domain is annular: the inner wall follows the spike
     * contour profile and the outer boundary is held at the cowl-lip radius
     * {@code rt} (throat outer radius).  This gives:
     * <ul>
     *   <li>Inlet patch — annular face at x = 0, r ∈ [ri, rt]</li>
     *   <li>Outlet patch — annular face at x = L_spike, r ∈ [r_spike_tip, rt]</li>
     *   <li>Inner-wall patch — the spike surface</li>
     *   <li>Outer-wall patch — constant r = rt (freestream or cowl)</li>
     * </ul>
     *
     * @param nozzle   Aerospike nozzle (must have been generated)
     * @param filePath Destination file path
     * @param format   Target mesh format
     * @throws IOException If the file cannot be written
     */
    public void exportAerospike(AerospikeNozzle nozzle, Path filePath, Format format)
            throws IOException {
        switch (format) {
            case OPENFOAM_BLOCKMESH -> exportAerospikeOpenFOAM(nozzle, filePath);
            case GMSH_GEO -> exportAerospikeGmsh(nozzle, filePath);
            case PLOT3D, CGNS -> exportAerospikePlot3D(nozzle, filePath);
        }
    }

    /**
     * Exports an OpenFOAM {@code blockMeshDict} for the Aerospike annular flow domain.
     * Uses a 5° axisymmetric wedge topology with the inner wall following the spike
     * contour and the outer wall at constant radius {@code rt}.
     *
     * @param nozzle   Aerospike nozzle (must have been generated)
     * @param filePath Destination file path
     * @throws IOException If the file cannot be written
     */
    private void exportAerospikeOpenFOAM(AerospikeNozzle nozzle, Path filePath)
            throws IOException {
        List<Point2D> spike = nozzle.getTruncatedSpikeContour();
        double rt = nozzle.getParameters().throatRadius();
        double L  = nozzle.getTruncatedLength();
        double ri = nozzle.getParameters().throatRadius() * nozzle.getSpikeRadiusRatio();
        double rTip = nozzle.getTruncatedBaseRadius();

        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("FoamFile\n{\n");
            writer.write("    version     2.0;\n");
            writer.write("    format      ASCII;\n");
            writer.write("    class       dictionary;\n");
            writer.write("    object      blockMeshDict;\n");
            writer.write("}\n\n");
            writer.write("convertToMeters 1.0;\n\n");

            double wedge = Math.toRadians(2.5);

            // Four corner vertices of the annular domain (wedge geometry)
            // 0,1: inlet inner (spike surface at x=0, r=ri)
            // 2,3: inlet outer (cowl lip at x=0, r=rt)
            // 4,5: outlet inner (spike tip, r=rTip)
            // 6,7: outlet outer (x=L, r=rt)
            writer.write("vertices\n(\n");
            writeWedgeVertex(writer, 0,  ri, wedge, 0);
            writeWedgeVertex(writer, 1,  rt, wedge, 0);
            writeWedgeVertex(writer, 2,  rTip, wedge, L);
            writeWedgeVertex(writer, 3,  rt, wedge, L);
            writer.write(");\n\n");

            writer.write("blocks\n(\n");
            writer.write(String.format(
                    "    hex (0 1 3 2 0 1 3 2) (%d %d 1)\n", axialCells, radialCells));
            double gAero = blockMeshGrading(rt - ri);
            writer.write("    simpleGrading (\n");
            writer.write("        1\n");
            writer.write(String.format(
                    "        ((0.5 0.5 %.2f) (0.5 0.5 %.2f))\n",
                    gAero, 1.0 / gAero));
            writer.write("        1\n");
            writer.write("    )\n");
            writer.write(");\n\n");

            // Spline edges along inner (spike) wall
            writer.write("edges\n(\n");
            writer.write("    spline 0 2 (\n");
            for (int i = 1; i < spike.size() - 1; i++) {
                Point2D p = spike.get(i);
                writer.write(String.format("        (%.8f %.8f %.8f)\n",
                        p.x(), p.y() * Math.cos(wedge), p.y() * Math.sin(wedge)));
            }
            writer.write("    )\n");
            writer.write(");\n\n");

            writer.write("boundary\n(\n");
            writer.write("    inlet\n    {\n        type patch;\n");
            writer.write("        faces ((0 1 1 0));\n    }\n\n");
            writer.write("    outlet\n    {\n        type patch;\n");
            writer.write("        faces ((2 3 3 2));\n    }\n\n");
            writer.write("    spike\n    {\n        type wall;\n");
            writer.write("        faces ((0 2 2 0));\n    }\n\n");
            writer.write("    cowl\n    {\n        type wall;\n");
            writer.write("        faces ((1 3 3 1));\n    }\n\n");
            writer.write("    wedge0\n    {\n        type wedge;\n");
            writer.write("        faces ((0 1 3 2));\n    }\n\n");
            writer.write("    wedge1\n    {\n        type wedge;\n");
            writer.write("        faces ((0 2 3 1));\n    }\n");
            writer.write(");\n\n");
            writer.write("mergePatchPairs\n(\n);\n");
        }
    }

    /** Writes a pair of wedge vertices (±z) for OpenFOAM blockMeshDict. */
    private static void writeWedgeVertex(BufferedWriter writer, int id,
                                          double r, double wedge, double x) throws IOException {
        double y = r * Math.cos(wedge);
        double z = r * Math.sin(wedge);
        writer.write(String.format("    (%.8f %.8f %.8f) // %d+\n", x, y,  z, id));
        writer.write(String.format("    (%.8f %.8f %.8f) // %d-\n", x, y, -z, id));
    }

    /**
     * Exports a Gmsh {@code .geo} script for the Aerospike annular flow domain.
     * Physical groups: {@code inlet}, {@code outlet}, {@code spike}, {@code cowl},
     * {@code fluid}.
     *
     * @param nozzle   Aerospike nozzle (must have been generated)
     * @param filePath Destination {@code .geo} file path
     * @throws IOException If the file cannot be written
     */
    private void exportAerospikeGmsh(AerospikeNozzle nozzle, Path filePath) throws IOException {
        List<Point2D> spike = nozzle.getTruncatedSpikeContour();
        double rt  = nozzle.getParameters().throatRadius();
        double L   = nozzle.getTruncatedLength();
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("// Gmsh geometry file for Aerospike nozzle annular domain\n");
            writer.write("// Generated by Nozzle MOC Design Tool\n\n");
            writer.write(String.format("lc = %.6f;\n\n", rt / radialCells));

            // Spike wall points
            int pid = 1;
            List<Integer> spikeIds = new ArrayList<>();
            for (Point2D p : spike) {
                writer.write(String.format("Point(%d) = {%.8f, %.8f, 0, lc};\n",
                        pid, p.x(), p.y()));
                spikeIds.add(pid++);
            }

            // Outer wall corners (x=0, rt) and (x=L, rt)
            int pInletOuter  = pid++;
            int pOutletOuter = pid;
            writer.write(String.format("Point(%d) = {%.8f, %.8f, 0, lc}; // inlet outer\n",
                    pInletOuter,  0.0, rt));
            writer.write(String.format("Point(%d) = {%.8f, %.8f, 0, lc}; // outlet outer\n",
                    pOutletOuter, L,   rt));
            writer.write("\n");

            int lid = 1;

            // Spike spline
            StringBuilder spikePts = new StringBuilder();
            for (int i = 0; i < spikeIds.size(); i++) {
                if (i > 0) spikePts.append(", ");
                spikePts.append(spikeIds.get(i));
            }
            writer.write(String.format("Spline(%d) = {%s}; // Spike\n", lid, spikePts));
            int spikeSpline = lid++;

            // Outlet (spike tip to outer)
            writer.write(String.format("Line(%d) = {%d, %d}; // Outlet\n",
                    lid, spikeIds.getLast(), pOutletOuter));
            int outletLine = lid++;

            // Outer cowl (outlet to inlet)
            writer.write(String.format("Line(%d) = {%d, %d}; // Cowl\n",
                    lid, pOutletOuter, pInletOuter));
            int cowlLine = lid++;

            // Inlet (outer to spike start)
            writer.write(String.format("Line(%d) = {%d, %d}; // Inlet\n",
                    lid, pInletOuter, spikeIds.getFirst()));
            int inletLine = lid;

            writer.write("\n");
            writer.write(String.format("Curve Loop(1) = {%d, %d, %d, %d};\n",
                    spikeSpline, outletLine, cowlLine, inletLine));
            writer.write("Plane Surface(1) = {1};\n\n");

            writer.write("Physical Curve(\"inlet\")  = {" + inletLine   + "};\n");
            writer.write("Physical Curve(\"outlet\") = {" + outletLine  + "};\n");
            writer.write("Physical Curve(\"spike\")  = {" + spikeSpline + "};\n");
            writer.write("Physical Curve(\"cowl\")   = {" + cowlLine    + "};\n");
            writer.write("Physical Surface(\"fluid\") = {1};\n\n");

            writer.write(String.format("Transfinite Curve {%d, %d} = %d Using Progression 1;\n",
                    spikeSpline, cowlLine, axialCells + 1));
            double rAero = gmshProgression(rt - spike.getFirst().y());
            writer.write(String.format("Transfinite Curve {%d, %d} = %d Using Progression %.4f;\n",
                    inletLine, outletLine, radialCells + 1, rAero));
            writer.write("Transfinite Surface {1};\n");
            writer.write("Recombine Surface {1};\n");
        }
    }

    /**
     * Exports the Aerospike flow domain as a 2-D structured Plot3D grid.
     * The grid spans from the spike surface (j=0) to the outer cowl radius r=rt (j=nj-1),
     * with a power-law radial stretching.
     *
     * @param nozzle   Aerospike nozzle (must have been generated)
     * @param filePath Destination file path
     * @throws IOException If the file cannot be written
     */
    private void exportAerospikePlot3D(AerospikeNozzle nozzle, Path filePath) throws IOException {
        List<Point2D> spike = nozzle.getTruncatedSpikeContour();
        double rt = nozzle.getParameters().throatRadius();
        double L  = nozzle.getTruncatedLength();

        int ni = axialCells + 1;
        int nj = radialCells + 1;

        double[][] x = new double[ni][nj];
        double[][] y = new double[ni][nj];

        double pAero = plot3dExponent(rt - spike.getFirst().y());
        for (int i = 0; i < ni; i++) {
            double xi    = (double) i / (ni - 1) * L;
            double rInner = spikeRadiusAt(spike, xi);

            for (int j = 0; j < nj; j++) {
                double eta = (double) j / (nj - 1);
                eta = 1.0 - Math.pow(1.0 - eta, pAero);
                x[i][j] = xi;
                y[i][j] = rInner + eta * (rt - rInner);
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("1\n");
            writer.write(String.format("%d %d 1\n", ni, nj));
            for (int j = 0; j < nj; j++) {
                for (int i = 0; i < ni; i++) {
                    writer.write(String.format("%.8e ", x[i][j]));
                    if ((i + 1) % 5 == 0) writer.write("\n");
                }
            }
            writer.write("\n");
            for (int j = 0; j < nj; j++) {
                for (int i = 0; i < ni; i++) {
                    writer.write(String.format("%.8e ", y[i][j]));
                    if ((i + 1) % 5 == 0) writer.write("\n");
                }
            }
            writer.write("\n");
            for (int j = 0; j < nj; j++) {
                for (int i = 0; i < ni; i++) {
                    writer.write("0.0 ");
                    if ((i + 1) % 5 == 0) writer.write("\n");
                }
            }
        }
    }

    /**
     * Linearly interpolates the spike surface radius at axial position {@code x}.
     * Clamps to the first/last contour radius outside the contour range.
     *
     * @param spike Ordered spike contour points (x monotonically increasing)
     * @param x     Axial query position in metres
     * @return Interpolated spike surface radius in metres
     */
    private static double spikeRadiusAt(List<Point2D> spike, double x) {
        if (x <= spike.getFirst().x()) return spike.getFirst().y();
        // Walk segments left-to-right; return as soon as x is within the segment.
        // When x >= spike.getLast().x() the loop exhausts and falls through to the
        // final return — equivalent to the former early-return guard, but reachable
        // by the final grid station at xi = L in exportAerospikePlot3D.
        for (int i = 0; i < spike.size() - 1; i++) {
            Point2D a = spike.get(i);
            Point2D b = spike.get(i + 1);
            if (x < b.x()) {
                double t = (x - a.x()) / (b.x() - a.x());
                return a.y() + t * (b.y() - a.y());
            }
        }
        return spike.getLast().y();  // x >= spike.getLast().x()
    }
}
