package com.nozzle.export;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.geometry.Point2D;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Exports a complete, runnable OpenFOAM case for a compressible axisymmetric
 * rocket nozzle simulation.
 *
 * <h3>Case structure written</h3>
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
 * <h3>Mesh topology</h3>
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
 * <h3>Solver</h3>
 * {@code rhoCentralFoam} (density-based, Kurganov-Tadmor central scheme) — suitable
 * for the full Mach range from subsonic chamber to supersonic exit.
 *
 * <h3>Usage</h3>
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

    private static final double DEFAULT_WEDGE_ANGLE_DEG = 2.5;

    private int    axialCells        = 200;
    private int    radialCells       = 80;
    private double wedgeAngleDeg     = DEFAULT_WEDGE_ANGLE_DEG;
    private double radialGrading     = 4.0;    // wall-normal mesh stretching ratio
    private boolean turbulenceEnabled = true;
    private double turbulenceIntensity = 0.05; // 5 %

    // -------------------------------------------------------------------------
    // Fluent configuration
    // -------------------------------------------------------------------------

    public OpenFOAMExporter setAxialCells(int n)         { this.axialCells = n;         return this; }
    public OpenFOAMExporter setRadialCells(int n)        { this.radialCells = n;        return this; }
    public OpenFOAMExporter setWedgeAngleDeg(double deg) { this.wedgeAngleDeg = deg;    return this; }
    public OpenFOAMExporter setRadialGrading(double g)   { this.radialGrading = g;      return this; }
    public OpenFOAMExporter setTurbulenceEnabled(boolean b) { this.turbulenceEnabled = b; return this; }
    public OpenFOAMExporter setTurbulenceIntensity(double i) { this.turbulenceIntensity = i; return this; }

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

        Path system   = caseDir.resolve("system");
        Path constant = caseDir.resolve("constant");
        Path zero     = caseDir.resolve("0");
        Files.createDirectories(system);
        Files.createDirectories(constant);
        Files.createDirectories(zero);

        writeBlockMeshDict(params, pts, system.resolve("blockMeshDict"));
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
    }

    // -------------------------------------------------------------------------
    // system/blockMeshDict
    // -------------------------------------------------------------------------

    private void writeBlockMeshDict(NozzleDesignParameters params,
                                    List<Point2D> pts,
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
            w.printf("    hex (0 1 2 3 4 5 6 7) (%d %d 1)%n", axialCells, radialCells);
            w.printf("    simpleGrading (1 %s 1)%n", gradingSpec(radialGrading));
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
            w.println("writeFormat     ascii;");
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
            emptyPatch(w, "axis");
            wedgePatch(w, "front");
            wedgePatch(w, "back");
            w.println("}");
            divider(w);
        }
    }

    // -------------------------------------------------------------------------
    // 0/T  — temperature
    // -------------------------------------------------------------------------

    private void writeTemperatureField(NozzleDesignParameters params, Path file) throws IOException {
        double t0 = params.chamberTemperature();
        double ta = params.exitTemperature();

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
            emptyPatch(w, "axis");
            wedgePatch(w, "front");
            wedgePatch(w, "back");
            w.println("}");
            divider(w);
        }
    }

    // -------------------------------------------------------------------------
    // 0/U  — velocity
    // -------------------------------------------------------------------------

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
            emptyPatch(w, "axis");
            wedgePatch(w, "front");
            wedgePatch(w, "back");
            w.println("}");
            divider(w);
        }
    }

    // -------------------------------------------------------------------------
    // 0/k  — turbulent kinetic energy
    // -------------------------------------------------------------------------

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
            emptyPatch(w, "axis");
            wedgePatch(w, "front");
            wedgePatch(w, "back");
            w.println("}");
            divider(w);
        }
    }

    // -------------------------------------------------------------------------
    // 0/omega  — specific dissipation rate
    // -------------------------------------------------------------------------

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
            emptyPatch(w, "axis");
            wedgePatch(w, "front");
            wedgePatch(w, "back");
            w.println("}");
            divider(w);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Returns a two-part grading specification for symmetric boundary-layer stretching. */
    private static String gradingSpec(double ratio) {
        if (ratio <= 1.0) return "1";
        // Two sub-layers: inner 20% stretched toward wall, outer 80% relaxed.
        return String.format("((0.2 0.2 %.2f)(0.8 0.8 %.4f))", ratio, 1.0 / ratio);
    }

    private static void patch(PrintWriter w, String name, String type, String faces) {
        w.printf("    %s%n    {%n        type  %s;%n        faces (%s);%n    }%n%n",
                name, type, faces);
    }

    private static void zeroGradientPatch(PrintWriter w, String name) {
        w.printf("    %s    { type zeroGradient; }%n", name);
    }

    private static void emptyPatch(PrintWriter w, String name) {
        w.printf("    %s    { type empty; }%n", name);
    }

    private static void wedgePatch(PrintWriter w, String name) {
        w.printf("    %s    { type wedge; }%n", name);
    }

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
        w.println("    format      ascii;");
        w.printf( "    class       %s;%n", cls);
        w.printf( "    location    \"%s\";%n", location);
        w.printf( "    object      %s;%n", object);
        w.println("}");
        divider(w);
        w.println();
    }

    private static void divider(PrintWriter w) {
        w.println("// ************************************************************************* //");
    }

    private static PrintWriter printer(Path path) throws IOException {
        return new PrintWriter(Files.newBufferedWriter(path));
    }
}
