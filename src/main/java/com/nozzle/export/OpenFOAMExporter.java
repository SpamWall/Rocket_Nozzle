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

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.geometry.Point2D;
import com.nozzle.moc.DualBellNozzle;
import com.nozzle.moc.RaoNozzle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Exports a complete, runnable OpenFOAM case for a compressible axisymmetric
 * rocket nozzle simulation.
 *
 * <h2>Case structure written</h2>
 * <pre>
 *   &lt;caseDir&gt;/
 *     system/
 *       blockMeshDict          – wedge mesh with spline wall profile
 *       controlDict            – rhoCentralFoam run control
 *       fvSchemes              – Kurganov-Tadmor central-upwind schemes
 *       fvSolution             – turbulence-scalar solvers
 *     constant/
 *       thermophysicalProperties – perfect-gas model from NozzleDesignParameters
 *       turbulenceProperties     – k-ω SST (or laminar)
 *     0/
 *       p                      – total-pressure BC at inlet, fixed at outlet
 *       T                      – total-temperature BC at inlet
 *       U                      – pressureInletOutletVelocity at inlet, zeroGradient at outlet
 *       k                      – turbulent kinetic energy   (if turbulence enabled)
 *       omega                  – specific dissipation rate  (if turbulence enabled)
 * </pre>
 *
 * <h2>Mesh topology</h2>
 * A 5° axisymmetric wedge (±{@value #DEFAULT_WEDGE_ANGLE_DEG}°) is generated with a
 * single hex block.  The nozzle wall profile is embedded as a {@code spline} edge so
 * the mesh faithfully follows the contour.  Boundary patches:
 * <ul>
 *   <li>{@code inlet}  — {@code patch}</li>
 *   <li>{@code outlet} — {@code patch}</li>
 *   <li>{@code wall}   — {@code wall}</li>
 *   <li>{@code axis}   — {@code empty} (axisymmetric treatment)</li>
 *   <li>{@code front} / {@code back} — {@code wedge}</li>
 * </ul>
 *
 * <h2>Solver</h2>
 * {@code rhoCentralFoam} (density-based, Kurganov-Tadmor central scheme) — suitable
 * for the full Mach range from subsonic chamber to supersonic exit.
 *
 * <h2>Usage</h2>
 * <pre>
 *   new OpenFOAMExporter()
 *       .setAxialCells(300)
 *       .setRadialCells(100)
 *       .setRadialGrading(5.0)
 *       .exportCase(params, contour, Path.of("nozzle_case"));
 * </pre>
 * Then run: {@code blockMesh && rhoCentralFoam}
 */
public class OpenFOAMExporter {

    private static final Logger LOG = LoggerFactory.getLogger(OpenFOAMExporter.class);

    /** Creates an {@code OpenFOAMExporter} with default mesh and solver settings. */
    public OpenFOAMExporter() {}

    /** Half-angle of the axisymmetric wedge in degrees (default ±2.5° = 5° total). */
    private static final double DEFAULT_WEDGE_ANGLE_DEG = 2.5;

    /** Number of cells in the axial (flow) direction of the hex block. */
    private int    axialCells        = 200;
    /** Number of cells in the wall-normal (radial) direction of the hex block. */
    private int    radialCells       = 80;
    /** Half-angle of the wedge in degrees; determines the z-coordinate spread of wall vertices. */
    private double wedgeAngleDeg     = DEFAULT_WEDGE_ANGLE_DEG;
    /** Wall-normal mesh grading ratio (cell size at wall / cell size at axis). */
    private double radialGrading     = 4.0;
    /** When {@code true}, writes k-ω SST turbulence model; when {@code false}, writes laminar. */
    private boolean turbulenceEnabled = true;
    /** Turbulent intensity fraction used to initialise k and ω fields (default 5 %). */
    private double turbulenceIntensity = 0.05;
    /**
     * First cell height y₁ in metres for y⁺-controlled radial grading.
     * When positive, overrides {@link #radialGrading} in {@code blockMeshDict}:
     * effective grading = r_exit / y₁.  Zero means disabled.
     */
    private double firstLayerThickness = 0.0;

    // -------------------------------------------------------------------------
    // Fluent configuration
    // -------------------------------------------------------------------------

    /**
     * Sets the number of cells in the axial direction of the hex block.
     *
     * @param n Number of axial cells (positive integer)
     * @return This instance for method chaining
     */
    public OpenFOAMExporter setAxialCells(int n)         { this.axialCells = n;         return this; }

    /**
     * Sets the number of cells in the wall-normal (radial) direction of the hex block.
     *
     * @param n Number of radial cells (positive integer)
     * @return This instance for method chaining
     */
    public OpenFOAMExporter setRadialCells(int n)        { this.radialCells = n;        return this; }

    /**
     * Sets the wedge half-angle used for the axisymmetric mesh (default
     * {@value #DEFAULT_WEDGE_ANGLE_DEG}°).  The total wedge spans ±{@code deg} degrees.
     *
     * @param deg Wedge half-angle in degrees (typically 2.5° for a 5° wedge)
     * @return This instance for method chaining
     */
    public OpenFOAMExporter setWedgeAngleDeg(double deg) { this.wedgeAngleDeg = deg;    return this; }

    /**
     * Sets the wall-normal mesh grading ratio (ratio of the cell size nearest the
     * axis to the cell size nearest the wall).  Values greater than 1 cluster cells
     * toward the wall to resolve the boundary layer.
     *
     * @param g Grading ratio (≥ 1; use 1 for uniform spacing)
     * @return This instance for method chaining
     */
    public OpenFOAMExporter setRadialGrading(double g)   { this.radialGrading = g;      return this; }

    /**
     * Enables or disables turbulence modeling.  When enabled, k-ω SST is written
     * to {@code constant/turbulenceProperties} and the {@code 0/k} and {@code 0/omega}
     * fields are generated.  When disabled, a laminar simulation type is written.
     *
     * @param b {@code true} to enable k-ω SST; {@code false} for laminar
     * @return This instance for method chaining
     */
    public OpenFOAMExporter setTurbulenceEnabled(boolean b) { this.turbulenceEnabled = b; return this; }

    /**
     * Sets the turbulent intensity fraction used to initialize the {@code k} and
     * {@code omega} fields from the chamber speed of sound
     * ({@code k₀ = 1.5 · (I · a₀)²}).
     *
     * @param i Turbulent intensity as a fraction of the local velocity (e.g. 0.05 = 5 %)
     * @return This instance for method chaining
     */
    public OpenFOAMExporter setTurbulenceIntensity(double i) { this.turbulenceIntensity = i; return this; }

    /**
     * Sets the first cell height y₁ (metres) for y⁺-controlled radial grading,
     * overriding the fixed {@link #setRadialGrading(double) radialGrading}.
     * The effective grading written to {@code blockMeshDict} is
     * {@code r_exit / y₁}, where {@code r_exit} is the nozzle exit radius.
     *
     * @param t First cell height in metres (must be positive)
     * @return This instance for method chaining
     * @throws IllegalArgumentException if {@code t} is not positive
     */
    public OpenFOAMExporter setFirstLayerThickness(double t) {
        if (t <= 0) throw new IllegalArgumentException(
                "firstLayerThickness must be positive, got: " + t);
        this.firstLayerThickness = t;
        return this;
    }

    // -------------------------------------------------------------------------
    // Main export entry point
    // -------------------------------------------------------------------------

    /**
     * Writes a complete OpenFOAM case directory.
     *
     * @param params   Nozzle design parameters — used to set BCs and thermophysical model
     * @param contour  Pre-generated nozzle wall contour
     * @param caseDir  Output directory (created if absent)
     * @throws IOException on any file write failure
     */
    public void exportCase(NozzleDesignParameters params, NozzleContour contour,
                           Path caseDir) throws IOException {
        List<Point2D> pts = contour.getContourPoints();
        if (pts.size() < 2) {
            throw new IllegalArgumentException("Contour must be generated before export");
        }

        LOG.debug("Exporting OpenFOAM case: {} contour points, axial={} radial={} turbulence={} → {}",
                pts.size(), axialCells, radialCells, turbulenceEnabled, caseDir);

        Path system   = caseDir.resolve("system");
        Path constant = caseDir.resolve("constant");
        Path zero     = caseDir.resolve("0");
        Files.createDirectories(system);
        Files.createDirectories(constant);
        Files.createDirectories(zero);

        writeBlockMeshDict(pts, system.resolve("blockMeshDict"));
        writeControlDict(system.resolve("controlDict"));
        writeFvSchemes(system.resolve("fvSchemes"));
        writeFvSolution(system.resolve("fvSolution"));

        writeThermophysicalProperties(params, constant.resolve("thermophysicalProperties"));
        writeTurbulenceProperties(constant.resolve("turbulenceProperties"));

        writePressureField(params, zero.resolve("p"));
        writeTemperatureField(params, zero.resolve("T"));
        writeVelocityField(zero.resolve("U"));
        if (turbulenceEnabled) {
            writeKField(params, zero.resolve("k"));
            writeOmegaField(params, pts, zero.resolve("omega"));
        }

        LOG.debug("OpenFOAM case export complete → {}", caseDir);
    }

    /**
     * Convenience overload that exports a complete OpenFOAM case for a Rao bell nozzle.
     *
     * @param nozzle  Rao bell nozzle (must have been generated)
     * @param caseDir Output directory (created if absent)
     * @throws IOException on any file write failure
     */
    public void exportCase(RaoNozzle nozzle, Path caseDir) throws IOException {
        exportCase(nozzle.getParameters(),
                NozzleContour.fromPoints(nozzle.getParameters(), nozzle.getContourPoints()),
                caseDir);
    }

    /**
     * Convenience overload that exports a complete OpenFOAM case for a dual-bell nozzle.
     * The full contour (base bell + extension) is exported.
     *
     * @param nozzle  Dual-bell nozzle (must have been generated)
     * @param caseDir Output directory (created if absent)
     * @throws IOException on any file write failure
     */
    public void exportCase(DualBellNozzle nozzle, Path caseDir) throws IOException {
        exportCase(nozzle.getParameters(),
                NozzleContour.fromPoints(nozzle.getParameters(), nozzle.getContourPoints()),
                caseDir);
    }

    // -------------------------------------------------------------------------
    // system/blockMeshDict
    // -------------------------------------------------------------------------

    /**
     * Writes {@code system/blockMeshDict} defining the 5° axisymmetric wedge mesh.
     * Eight vertices form a single hex block; the wall profile is embedded as two
     * {@code spline} edges (front and back wedge faces).
     *
     * @param pts  Ordered contour points defining the wall profile (at least 2 required)
     * @param file Destination file path
     * @throws IOException if the file cannot be written
     */
    private void writeBlockMeshDict(List<Point2D> pts,
                                    Path file) throws IOException {
        double psi = Math.toRadians(wedgeAngleDeg);
        double cosP = Math.cos(psi);
        double sinP = Math.sin(psi);

        Point2D inlet  = pts.getFirst();
        Point2D outlet = pts.getLast();
        double xIn   = inlet.x();
        double rIn   = inlet.y();
        double xOut  = outlet.x();
        double rOut  = outlet.y();

        try (PrintWriter w = printer(file)) {
            foamHeader(w, "dictionary", "system", "blockMeshDict");
            w.println("convertToMeters 1;");
            w.println();

            // --- vertices ---
            // v0  axis-inlet       v1  axis-outlet
            // v2  wall-outlet-front  v3  wall-inlet-front
            // v4  axis-inlet-back (=v0 degenerate)
            // v5  axis-outlet-back (=v1 degenerate)
            // v6  wall-outlet-back   v7  wall-inlet-back
            w.println("vertices");
            w.println("(");
            w.printf("    ( %12.8f %12.8f %12.8f )  // 0  axis-inlet%n",
                    xIn, 0.0, 0.0);
            w.printf("    ( %12.8f %12.8f %12.8f )  // 1  axis-outlet%n",
                    xOut, 0.0, 0.0);
            w.printf("    ( %12.8f %12.8f %12.8f )  // 2  wall-outlet-front%n",
                    xOut, rOut * cosP,  rOut * sinP);
            w.printf("    ( %12.8f %12.8f %12.8f )  // 3  wall-inlet-front%n",
                    xIn,  rIn  * cosP,  rIn  * sinP);
            w.printf("    ( %12.8f %12.8f %12.8f )  // 4  axis-inlet-back%n",
                    xIn, 0.0, 0.0);
            w.printf("    ( %12.8f %12.8f %12.8f )  // 5  axis-outlet-back%n",
                    xOut, 0.0, 0.0);
            w.printf("    ( %12.8f %12.8f %12.8f )  // 6  wall-outlet-back%n",
                    xOut, rOut * cosP, -rOut * sinP);
            w.printf("    ( %12.8f %12.8f %12.8f )  // 7  wall-inlet-back%n",
                    xIn,  rIn  * cosP, -rIn  * sinP);
            w.println(");");
            w.println();

            // --- blocks ---
            // i-direction: v0→v1 (axial), j-direction: v0→v3 (radial), k-direction: wedge
            w.println("blocks");
            w.println("(");
            double effectiveGrading = (firstLayerThickness > 0)
                    ? Math.max(1.0, rOut / firstLayerThickness)
                    : radialGrading;
            w.printf("    hex (0 1 2 3 4 5 6 7) (%d %d 1)%n", axialCells, radialCells);
            w.printf("    simpleGrading (1 %s 1)%n", gradingSpec(effectiveGrading));
            w.println(");");
            w.println();

            // --- spline edges for wall profile ---
            w.println("edges");
            w.println("(");
            // front face: vertex 3 (inlet wall) → vertex 2 (outlet wall)
            w.println("    spline 3 2");
            w.println("    (");
            for (int i = 1; i < pts.size() - 1; i++) {
                Point2D p = pts.get(i);
                w.printf("        ( %12.8f %12.8f %12.8f )%n",
                        p.x(), p.y() * cosP, p.y() * sinP);
            }
            w.println("    )");
            // back face: vertex 7 → vertex 6 (mirror of front)
            w.println("    spline 7 6");
            w.println("    (");
            for (int i = 1; i < pts.size() - 1; i++) {
                Point2D p = pts.get(i);
                w.printf("        ( %12.8f %12.8f %12.8f )%n",
                        p.x(), p.y() * cosP, -p.y() * sinP);
            }
            w.println("    )");
            w.println(");");
            w.println();

            // --- boundary patches ---
            w.println("boundary");
            w.println("(");
            patch(w, "inlet",  "patch",  "(0 4 7 3)");
            patch(w, "outlet", "patch",  "(1 2 6 5)");
            patch(w, "wall",   "wall",   "(3 7 6 2)");
            patch(w, "axis",   "empty",  "(0 1 5 4)");
            patch(w, "front",  "wedge",  "(0 3 2 1)");
            patch(w, "back",   "wedge",  "(4 7 6 5)");
            w.println(");");
            w.println();
            w.println("mergePatchPairs");
            w.println("(");
            w.println(");");
            divider(w);
        }
    }

    // -------------------------------------------------------------------------
    // system/controlDict
    // -------------------------------------------------------------------------

    /**
     * Writes {@code system/controlDict} configuring {@code rhoCentralFoam} with
     * CFL-limited adaptive time-stepping and periodic ASCII write-outs.
     *
     * @param file Destination file path
     * @throws IOException if the file cannot be written
     */
    private void writeControlDict(Path file) throws IOException {
        try (PrintWriter w = printer(file)) {
            foamHeader(w, "dictionary", "system", "controlDict");
            w.println("application     rhoCentralFoam;");
            w.println();
            w.println("startFrom       startTime;");
            w.println("startTime       0;");
            w.println("stopAt          endTime;");
            w.println("endTime         5e-3;");
            w.println();
            w.println("deltaT          1e-9;");
            w.println("adjustTimeStep  yes;");
            w.println("maxCo           0.4;");
            w.println("maxDeltaT       1e-5;");
            w.println();
            w.println("writeControl    adjustableRunTime;");
            w.println("writeInterval   5e-4;");
            w.println("purgeWrite      5;");
            w.println("writeFormat     ASCII;");
            w.println("writePrecision  10;");
            w.println("writeCompression off;");
            w.println();
            w.println("timeFormat      general;");
            w.println("timePrecision   6;");
            w.println("runTimeModifiable true;");
            divider(w);
        }
    }

    // -------------------------------------------------------------------------
    // system/fvSchemes  (Kurganov-Tadmor central-upwind)
    // -------------------------------------------------------------------------

    /**
     * Writes {@code system/fvSchemes} with the Kurganov-Tadmor central-upwind flux
     * scheme, van Leer reconstruction for primitive variables, and limited linear
     * discretization for all transported scalars.
     *
     * @param file Destination file path
     * @throws IOException if the file cannot be written
     */
    private void writeFvSchemes(Path file) throws IOException {
        try (PrintWriter w = printer(file)) {
            foamHeader(w, "dictionary", "system", "fvSchemes");
            w.println("fluxScheme      Kurganov;");
            w.println();
            w.println("ddtSchemes      { default Euler; }");
            w.println();
            w.println("gradSchemes");
            w.println("{");
            w.println("    default         Gauss linear;");
            w.println("}");
            w.println();
            w.println("divSchemes");
            w.println("{");
            w.println("    default                         none;");
            w.println("    div(phi,rho)                    Gauss limitedLinear 1;");
            w.println("    div(phi,U)                      Gauss limitedLinearV 1;");
            w.println("    div(phi,e)                      Gauss limitedLinear 1;");
            w.println("    div(phi,k)                      Gauss limitedLinear 1;");
            w.println("    div(phi,omega)                  Gauss limitedLinear 1;");
            w.println("    div(phid,p)                     Gauss limitedLinear 1;");
            w.println("    div((muEff*dev2(T(grad(U)))))   Gauss linear;");
            w.println("}");
            w.println();
            w.println("laplacianSchemes { default Gauss linear corrected; }");
            w.println();
            w.println("interpolationSchemes");
            w.println("{");
            w.println("    default           linear;");
            w.println("    reconstruct(rho)  vanLeer;");
            w.println("    reconstruct(U)    vanLeerV;");
            w.println("    reconstruct(T)    vanLeer;");
            w.println("}");
            w.println();
            w.println("snGradSchemes   { default corrected; }");
            divider(w);
        }
    }

    // -------------------------------------------------------------------------
    // system/fvSolution
    // -------------------------------------------------------------------------

    /**
     * Writes {@code system/fvSolution} with a diagonal solver for the explicit
     * density-based variables ({@code rho}, {@code rhoU}, {@code rhoE}) and a
     * PBiCGStab solver for the turbulence scalars ({@code k}, {@code omega}).
     *
     * @param file Destination file path
     * @throws IOException if the file cannot be written
     */
    private void writeFvSolution(Path file) throws IOException {
        try (PrintWriter w = printer(file)) {
            foamHeader(w, "dictionary", "system", "fvSolution");
            // rhoCentralFoam uses an explicit central scheme for the main flow;
            // only turbulence scalars need an implicit linear solver.
            w.println("solvers");
            w.println("{");
            w.println("    \"(rho|rhoU|rhoE)\"");
            w.println("    {");
            w.println("        solver    diagonal;");
            w.println("    }");
            w.println();
            w.println("    \"(k|omega)\"");
            w.println("    {");
            w.println("        solver          PBiCGStab;");
            w.println("        preconditioner  DILU;");
            w.println("        tolerance       1e-10;");
            w.println("        relTol          0;");
            w.println("    }");
            w.println("}");
            divider(w);
        }
    }

    // -------------------------------------------------------------------------
    // constant/thermophysicalProperties
    // -------------------------------------------------------------------------

    /**
     * Writes {@code constant/thermophysicalProperties} using the {@code hePsiThermo}
     * compressible framework with a perfect-gas equation of state, constant-Cp
     * enthalpy model, and Sutherland transport coefficients derived from the
     * {@link GasProperties} reference data.
     *
     * @param params Nozzle design parameters providing gas properties
     * @param file   Destination file path
     * @throws IOException if the file cannot be written
     */
    private void writeThermophysicalProperties(NozzleDesignParameters params,
                                               Path file) throws IOException {
        GasProperties gas = params.gasProperties();
        double cp  = gas.specificHeatCp();
        double mw  = gas.molecularWeight();

        // Convert to OpenFOAM Sutherland form: μ = As·T^(3/2) / (T + Ts)
        // from: μ = μ_ref·(T/T_ref)^(3/2)·(T_ref + S)/(T + S)
        double muRef = gas.viscosityRef();
        double tRef  = gas.tempRef();
        double S     = gas.sutherlandConst();
        double as    = muRef * (tRef + S) / Math.pow(tRef, 1.5);

        try (PrintWriter w = printer(file)) {
            foamHeader(w, "dictionary", "constant", "thermophysicalProperties");
            w.println("thermoType");
            w.println("{");
            w.println("    type            hePsiThermo;");
            w.println("    mixture         pureMixture;");
            w.println("    transport       sutherland;");
            w.println("    thermo          hConst;");
            w.println("    equationOfState perfectGas;");
            w.println("    specie          specie;");
            w.println("    energy          sensibleEnthalpy;");
            w.println("}");
            w.println();
            w.println("mixture");
            w.println("{");
            w.println("    specie");
            w.println("    {");
            w.printf("        nMoles      1;%n");
            w.printf("        molWeight   %.4f;  // kg/kmol%n", mw);
            w.println("    }");
            w.println("    thermodynamics");
            w.println("    {");
            w.printf("        Cp   %.2f;  // J/(kg·K)%n", cp);
            w.println("        Hf   0;");
            w.println("    }");
            w.println("    transport");
            w.println("    {");
            w.printf("        As   %.6e;  // Sutherland coefficient [Pa·s/sqrt(K)]%n", as);
            w.printf("        Ts   %.4f;  // Sutherland temperature [K]%n", S);
            w.println("    }");
            w.println("}");
            divider(w);
        }
    }

    // -------------------------------------------------------------------------
    // constant/turbulenceProperties
    // -------------------------------------------------------------------------

    /**
     * Writes {@code constant/turbulenceProperties} selecting either k-ω SST
     * (when {@link #turbulenceEnabled} is {@code true}) or a laminar simulation type.
     *
     * @param file Destination file path
     * @throws IOException if the file cannot be written
     */
    private void writeTurbulenceProperties(Path file) throws IOException {
        try (PrintWriter w = printer(file)) {
            foamHeader(w, "dictionary", "constant", "turbulenceProperties");
            if (turbulenceEnabled) {
                w.println("simulationType  RAS;");
                w.println();
                w.println("RAS");
                w.println("{");
                w.println("    RASModel        kOmegaSST;");
                w.println("    turbulence      on;");
                w.println("    printCoeffs     on;");
                w.println("}");
            } else {
                w.println("simulationType  laminar;");
            }
            divider(w);
        }
    }

    // -------------------------------------------------------------------------
    // 0/p  — pressure
    // -------------------------------------------------------------------------

    /**
     * Writes {@code 0/p} with a {@code totalPressure} boundary condition at the inlet
     * (stagnation pressure = chamber pressure), a {@code waveTransmissive} outlet
     * condition at the design ambient pressure, and {@code zeroGradient} on the wall.
     *
     * @param params Nozzle design parameters providing chamber and ambient pressures
     * @param file   Destination file path
     * @throws IOException if the file cannot be written
     */
    private void writePressureField(NozzleDesignParameters params, Path file) throws IOException {
        double p0 = params.chamberPressure();
        double pa = params.ambientPressure();

        try (PrintWriter w = printer(file)) {
            foamHeader(w, "volScalarField", "0", "p");
            w.println("dimensions      [1 -1 -2 0 0 0 0];");
            w.printf("%ninternalField   uniform %.2f;%n%n", p0);
            w.println("boundaryField");
            w.println("{");
            // inlet: total-pressure condition (subsonic, stagnation prescribed)
            w.println("    inlet");
            w.println("    {");
            w.println("        type            totalPressure;");
            w.println("        p0              uniform " + p0 + ";");
            w.println("    }");
            // outlet: fixed ambient pressure (waveTransmissive for cleaner exit)
            w.println("    outlet");
            w.println("    {");
            w.println("        type            waveTransmissive;");
            w.println("        field           p;");
            w.println("        psi             thermo:psi;");
            w.println("        gamma           " + params.gasProperties().gamma() + ";");
            w.println("        fieldInf        uniform " + pa + ";");
            w.println("        lInf            " + (params.exitRadius() * 10) + ";");
            w.println("        value           uniform " + pa + ";");
            w.println("    }");
            zeroGradientPatch(w, "wall");
            emptyPatch(w);
            wedgePatch(w, "front");
            wedgePatch(w, "back");
            w.println("}");
            divider(w);
        }
    }

    // -------------------------------------------------------------------------
    // 0/T  — temperature
    // -------------------------------------------------------------------------

    /**
     * Writes {@code 0/T} with a {@code totalTemperature} boundary condition at the
     * inlet (stagnation temperature = chamber temperature), {@code zeroGradient} at
     * the outlet, and an adiabatic ({@code zeroGradient}) wall condition.
     *
     * @param params Nozzle design parameters providing chamber temperature and γ
     * @param file   Destination file path
     * @throws IOException if the file cannot be written
     */
    private void writeTemperatureField(NozzleDesignParameters params, Path file) throws IOException {
        double t0 = params.chamberTemperature();
        try (PrintWriter w = printer(file)) {
            foamHeader(w, "volScalarField", "0", "T");
            w.println("dimensions      [0 0 0 1 0 0 0];");
            w.printf("%ninternalField   uniform %.2f;%n%n", t0);
            w.println("boundaryField");
            w.println("{");
            w.println("    inlet");
            w.println("    {");
            w.println("        type            totalTemperature;");
            w.println("        gamma           " + params.gasProperties().gamma() + ";");
            w.println("        T0              uniform " + t0 + ";");
            w.println("    }");
            zeroGradientPatch(w, "outlet");
            w.println("    wall");
            w.println("    {");
            w.println("        type            zeroGradient;  // adiabatic wall");
            w.println("    }");
            emptyPatch(w);
            wedgePatch(w, "front");
            wedgePatch(w, "back");
            w.println("}");
            divider(w);
        }
    }

    // -------------------------------------------------------------------------
    // 0/U  — velocity
    // -------------------------------------------------------------------------

    /**
     * Writes {@code 0/U} with a {@code pressureInletOutletVelocity} condition at the
     * inlet (velocity derived from the pressure field), {@code zeroGradient} at the
     * outlet, and a {@code noSlip} wall condition.
     *
     * @param file Destination file path
     * @throws IOException if the file cannot be written
     */
    private void writeVelocityField(Path file) throws IOException {
        try (PrintWriter w = printer(file)) {
            foamHeader(w, "volVectorField", "0", "U");
            w.println("dimensions      [0 1 -1 0 0 0 0];");
            w.println();
            w.println("internalField   uniform (0 0 0);");
            w.println();
            w.println("boundaryField");
            w.println("{");
            w.println("    inlet");
            w.println("    {");
            w.println("        type            pressureInletOutletVelocity;");
            w.println("        value           uniform (0 0 0);");
            w.println("    }");
            zeroGradientPatch(w, "outlet");
            w.println("    wall    { type noSlip; }");
            emptyPatch(w);
            wedgePatch(w, "front");
            wedgePatch(w, "back");
            w.println("}");
            divider(w);
        }
    }

    // -------------------------------------------------------------------------
    // 0/k  — turbulent kinetic energy
    // -------------------------------------------------------------------------

    /**
     * Writes {@code 0/k} (turbulent kinetic energy) with an initial value estimated
     * from the chamber speed of sound and the configured turbulence intensity
     * ({@code k₀ = 1.5 · (I · a₀)²}).  Uses a {@code turbulentIntensityKineticEnergyInlet}
     * boundary condition at the inlet and a {@code kqRWallFunction} on the wall.
     *
     * @param params Nozzle design parameters providing gas and chamber conditions
     * @param file   Destination file path
     * @throws IOException if the file cannot be written
     */
    private void writeKField(NozzleDesignParameters params, Path file) throws IOException {
        // Estimate chamber-condition k from turbulence intensity and speed of sound.
        double a0 = params.gasProperties().speedOfSound(params.chamberTemperature());
        double k0 = 1.5 * Math.pow(turbulenceIntensity * a0, 2.0);

        try (PrintWriter w = printer(file)) {
            foamHeader(w, "volScalarField", "0", "k");
            w.println("dimensions      [0 2 -2 0 0 0 0];");
            w.printf("%ninternalField   uniform %.4f;%n%n", k0);
            w.println("boundaryField");
            w.println("{");
            w.println("    inlet");
            w.println("    {");
            w.println("        type            turbulentIntensityKineticEnergyInlet;");
            w.printf("        intensity       %.3f;%n", turbulenceIntensity);
            w.println("        value           uniform " + k0 + ";");
            w.println("    }");
            zeroGradientPatch(w, "outlet");
            w.println("    wall    { type kqRWallFunction; value uniform " + k0 + "; }");
            emptyPatch(w);
            wedgePatch(w, "front");
            wedgePatch(w, "back");
            w.println("}");
            divider(w);
        }
    }

    // -------------------------------------------------------------------------
    // 0/omega  — specific dissipation rate
    // -------------------------------------------------------------------------

    /**
     * Writes {@code 0/omega} (specific turbulent dissipation rate) with an initial
     * value derived from a Bradshaw mixing-length estimate at the throat
     * ({@code ω₀ = Cμ^(−¼) · √k₀ / lₜ}, where {@code lₜ = 0.07 · r_throat}).
     * Uses a {@code turbulentMixingLengthFrequencyInlet} inlet condition and an
     * {@code omegaWallFunction} on the wall.
     *
     * @param params Nozzle design parameters providing gas and chamber conditions
     * @param pts    Contour points; the first point's {@code y} coordinate supplies
     *               the throat radius for the mixing-length estimate
     * @param file   Destination file path
     * @throws IOException if the file cannot be written
     */
    private void writeOmegaField(NozzleDesignParameters params,
                                  List<Point2D> pts,
                                  Path file) throws IOException {
        // Length scale ≈ 7% of throat radius (Bradshaw mixing-length estimate).
        double lt = 0.07 * pts.getFirst().y();
        double a0 = params.gasProperties().speedOfSound(params.chamberTemperature());
        double k0 = 1.5 * Math.pow(turbulenceIntensity * a0, 2.0);
        double cmu = 0.09;
        double omega0 = Math.pow(cmu, -0.25) * Math.sqrt(k0) / lt;

        try (PrintWriter w = printer(file)) {
            foamHeader(w, "volScalarField", "0", "omega");
            w.println("dimensions      [0 0 -1 0 0 0 0];");
            w.printf("%ninternalField   uniform %.2f;%n%n", omega0);
            w.println("boundaryField");
            w.println("{");
            w.println("    inlet");
            w.println("    {");
            w.println("        type            turbulentMixingLengthFrequencyInlet;");
            w.printf("        mixingLength    %.6f;%n", lt);
            w.println("        value           uniform " + omega0 + ";");
            w.println("    }");
            zeroGradientPatch(w, "outlet");
            w.println("    wall    { type omegaWallFunction; value uniform " + omega0 + "; }");
            emptyPatch(w);
            wedgePatch(w, "front");
            wedgePatch(w, "back");
            w.println("}");
            divider(w);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code blockMeshDict} grading specification string for wall-normal
     * boundary-layer stretching.  For {@code ratio} ≤ 1 a plain {@code "1"} (uniform)
     * is returned.  Otherwise, a two-sub-layer specification is produced: the inner
     * 20% of cells are stretched toward the wall at the given ratio; the outer 80%
     * relax back at the inverse ratio.
     *
     * @param ratio Cell-size ratio (wall cell / axis cell); must be ≥ 1 for stretching
     * @return OpenFOAM grading specification string, e.g. {@code "((0.2 0.2 4.00)(0.8 0.8 0.2500))"}
     */
    private static String gradingSpec(double ratio) {
        if (ratio <= 1.0) return "1";
        // Two sub-layers: inner 20% stretched toward wall, outer 80% relaxed.
        return String.format("((0.2 0.2 %.2f)(0.8 0.8 %.4f))", ratio, 1.0 / ratio);
    }

    /**
     * Writes a single named boundary patch entry inside the {@code boundary} list of
     * {@code blockMeshDict}.
     *
     * @param w     Writer to append to
     * @param name  Patch name (e.g. {@code "inlet"})
     * @param type  OpenFOAM patch type (e.g. {@code "patch"}, {@code "wall"}, {@code "wedge"})
     * @param faces Face list string in OpenFOAM vertex-index notation (e.g. {@code "(0 4 7 3)"})
     */
    private static void patch(PrintWriter w, String name, String type, String faces) {
        w.printf("    %s%n    {%n        type  %s;%n        faces (%s);%n    }%n%n",
                name, type, faces);
    }

    /**
     * Writes a one-line {@code zeroGradient} boundary condition entry for the named patch.
     *
     * @param w    Writer to append to
     * @param name Patch name
     */
    private static void zeroGradientPatch(PrintWriter w, String name) {
        w.printf("    %s    { type zeroGradient; }%n", name);
    }

    /**
     * Writes a one-line {@code empty} boundary condition entry for the {@code axis} patch
     * (used for the symmetry axis in axisymmetric cases).
     *
     * @param w Writer to append to
     */
    private static void emptyPatch(PrintWriter w) {
        w.printf("    axis    { type empty; }%n");
    }

    /**
     * Writes a one-line {@code wedge} boundary condition entry for the named patch
     * (used for the front and back faces of the axisymmetric wedge).
     *
     * @param w    Writer to append to
     * @param name Patch name
     */
    private static void wedgePatch(PrintWriter w, String name) {
        w.printf("    %s    { type wedge; }%n", name);
    }

    /**
     * Writes the standard OpenFOAM file header including the ASCII banner and
     * {@code FoamFile} dictionary with the given class, location, and object name.
     *
     * @param w        Writer to append to
     * @param cls      OpenFOAM class name (e.g. {@code "dictionary"}, {@code "volScalarField"})
     * @param location Subdirectory location string (e.g. {@code "system"}, {@code "0"})
     * @param object   File object name (e.g. {@code "controlDict"}, {@code "p"})
     */
    private static void foamHeader(PrintWriter w, String cls, String location, String object) {
        w.println("/*--------------------------------*- C++ -*----------------------------------*\\");
        w.println("  =========                 |");
        w.println("  \\\\      /  F ield         | OpenFOAM: The Open Source CFD Toolbox");
        w.println("   \\\\    /   O peration     |");
        w.println("    \\\\  /    A nd           | www.openfoam.com");
        w.println("     \\\\/     M anipulation  |");
        w.println("\\*---------------------------------------------------------------------------*/");
        w.println("FoamFile");
        w.println("{");
        w.println("    version     2.0;");
        w.println("    format      ASCII;");
        w.printf( "    class       %s;%n", cls);
        w.printf( "    location    \"%s\";%n", location);
        w.printf( "    object      %s;%n", object);
        w.println("}");
        divider(w);
        w.println();
    }

    /**
     * Writes the standard OpenFOAM end-of-file divider line.
     *
     * @param w Writer to append to
     */
    private static void divider(PrintWriter w) {
        w.println("// ************************************************************************* //");
    }

    /**
     * Opens a buffered {@link PrintWriter} for writing to the given path,
     * using the platform default charset.
     *
     * @param path Destination file path (parent directory must exist)
     * @return A new {@link PrintWriter} ready for writing
     * @throws IOException if the file cannot be opened or created
     */
    private static PrintWriter printer(Path path) throws IOException {
        return new PrintWriter(Files.newBufferedWriter(path));
    }
}
