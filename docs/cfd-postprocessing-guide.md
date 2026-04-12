# CFD Post-Processing Guide

How to take an exported OpenFOAM case from `OpenFOAMExporter` or a mesh from
`CFDMeshExporter`, run it successfully, inspect the flow field, and extract
Cf and Isp to compare against the library's analytical predictions.

This guide assumes OpenFOAM v2206 or later (ESI releases) or OpenFOAM 10+
(Foundation releases). Commands are bash. ParaView 5.10+ is used for
visualization.

---

## Table of Contents

1. [Case directory overview](#1-case-directory-overview)
2. [Reviewing and tuning boundary conditions](#2-reviewing-and-tuning-boundary-conditions)
3. [Mesh generation and quality checks](#3-mesh-generation-and-quality-checks)
4. [Running the solver](#4-running-the-solver)
5. [Monitoring convergence](#5-monitoring-convergence)
6. [Visualizing the flow field](#6-visualizing-the-flow-field)
7. [Extracting Cf and Isp from CFD results](#7-extracting-cf-and-isp-from-cfd-results)
8. [Comparing CFD against the analytical model](#8-comparing-cfd-against-the-analytical-model)
9. [Gmsh workflow](#9-gmsh-workflow)
10. [Plot3D and CGNS with other solvers](#10-plot3d-and-cgns-with-other-solvers)

---

## 1. Case directory overview

`OpenFOAMExporter` writes this structure:

```
nozzle_case/
├── 0/
│   ├── p          pressure (Pa)
│   ├── T          temperature (K)
│   ├── U          velocity (m/s)
│   ├── k          turbulent kinetic energy (m²/s²)   [if turbulence enabled]
│   └── omega      specific dissipation rate (1/s)    [if turbulence enabled]
├── constant/
│   ├── thermophysicalProperties   gas model, γ, molecular weight
│   └── turbulenceProperties       k-ω SST coefficients
└── system/
    ├── blockMeshDict              nozzle geometry + mesh
    ├── controlDict                time step, end time, write frequency
    ├── fvSchemes                  discretisation schemes
    └── fvSolution                 linear solver settings
```

The initial fields in `0/` are set from `NozzleDesignParameters`:

| Field | Inlet value                              | Outlet value                      |
|-------|------------------------------------------|-----------------------------------|
| `p`   | `chamberPressure`                        | `ambientPressure`                 |
| `T`   | `chamberTemperature`                     | derived from isentropic expansion |
| `U`   | normal to inlet, from mass-flow estimate | zero-gradient                     |

These are appropriate starting points but will almost certainly need adjustment
for your specific operating condition. Section 2 covers what to check and change.

---

## 2. Reviewing and tuning boundary conditions

### `0/p` — pressure

Open `0/p` and confirm the boundary conditions for each patch:

```
boundaryField
{
    inlet
    {
        type            totalPressure;
        p0              uniform 7000000;   // Pa — from params.chamberPressure()
    }
    outlet
    {
        type            waveTransmissive;
        field           p;
        psi             thermo:psi;
        gamma           1.3;
        fieldInf        101325;            // Pa — from params.ambientPressure()
        lInf            0.3;               // characteristic length, m
    }
    wall    { type zeroGradient; }
    axis    { type empty; }
    wedge0  { type wedge; }
    wedge1  { type wedge; }
}
```

**Common adjustments:**

- **Inlet type:** `totalPressure` is correct for a subsonic inlet where total
  conditions are prescribed. If you are starting the simulation from rest and
  the solver diverges, temporarily switch to `fixedValue` at Pc until the
  solution is established, then restore `totalPressure`.

- **Outlet `lInf`:** The characteristic length should be approximately the exit
  radius. Update it if your throat radius differs from the library default.
  Incorrect `lInf` causes spurious pressure reflections at the exit.

- **`waveTransmissive` vs. `zeroGradient` at outlet:** For a supersonic exit
  (which is normal for a nozzle operating at design conditions), the exit is
  fully supersonic and all characteristics carry information out of the domain.
  In this case `zeroGradient` works well and is simpler. Use `waveTransmissive`
  if the nozzle may be over-expanded and the exit flow is transonic or if you
  are modeling the plume.

### `0/T` — temperature

The inlet temperature should be the total temperature (chamber temperature).
The outlet is typically `zeroGradient` for a supersonic exit.

```
inlet   { type fixedValue; value uniform 3500; }   // Tc in K
outlet  { type zeroGradient; }
wall    { type zeroGradient; }                      // adiabatic wall (simplest)
```

**Wall thermal boundary condition:** The exporter sets `zeroGradient` (adiabatic)
on the wall by default. For a first analysis this is conservative — the actual
wall is cooled, so the adiabatic assumption gives maximum wall-adjacent gas
temperature. To model the cooled wall, change to:

```
wall
{
    type            fixedValue;
    value           uniform 800;    // K — estimated wall temperature from HeatTransferModel
}
```

Use `HeatTransferModel.getMaxWallTemperature()` from the library as a guide
for setting this value. Running with a fixed wall temperature is more physically
realistic but more sensitive to getting the value right.

### `0/U` — velocity

The inlet velocity is initialized from a one-dimensional mass-flow estimate.
For a compressible nozzle flow simulation the inlet velocity magnitude is less
important than the total pressure — the solver will find the correct velocity.
The important thing is direction: `inletValue` must be normal to the inlet face
(negative x for a nozzle with flow in the +x direction).

```
inlet
{
    type        pressureInletOutletVelocity;
    value       uniform (0 0 0);
}
outlet
{
    type        pressureInletOutletVelocity;
    inletValue  uniform (0 0 0);
    value       uniform (0 0 0);
}
```

This boundary type handles both inflow and outflow, which is needed if the
exit goes transiently subsonic during startup.

### `0/k` and `0/omega` — turbulence

The exported values are computed from `turbulenceIntensity` (I) and a mixing-length 
estimate. If you changed `turbulenceIntensity` in the exporter call but
the file still shows the old values, recompute manually:

```
k     = 1.5 × (U_ref × I)²          // U_ref ≈ exit velocity
omega = k^0.5 / (C_mu^0.25 × L)     // L ≈ throat radius, C_mu = 0.09
```

For a first run the precise turbulence initialization matters less than getting
pressure and temperature right. The turbulence field develops rapidly once the
flow is established.

---

## 3. Mesh generation and quality checks

### Generate the mesh

```bash
cd nozzle_case
blockMesh
```

Successful output ends with:

```
End
```

If `blockMesh` reports errors, the most common causes are:

| Error message                  | Cause                                             | Fix                                 |
|--------------------------------|---------------------------------------------------|-------------------------------------|
| `face 0 vertices not coplanar` | Wedge angle too large                             | Reduce `setWedgeAngleDeg()` to 2.5° |
| `Negative volume in cell`      | Too few axial cells relative to contour curvature | Increase `setAxialCells()`          |
| `Cannot find block`            | Corrupted `blockMeshDict`                         | Re-export the case                  |

### Check mesh quality

```bash
checkMesh
```

Review the output. The key metrics and acceptable ranges for a nozzle mesh:

| Metric                | Ideal | Acceptable | Action if exceeded                       |
|-----------------------|-------|------------|------------------------------------------|
| Max non-orthogonality | < 40° | < 70°      | Increase cell count or reduce grading    |
| Max skewness          | < 1.0 | < 4.0      | Increase axial cells near throat         |
| Max aspect ratio      | < 100 | < 1000     | Reduce radial grading or add axial cells |
| Max cell volume ratio | < 100 | < 1000     | Check grading parameters                 |

Non-orthogonality above 70° will require `nNonOrthogonalCorrectors` to be
increased in `fvSolution` (see Section 4). Values above 85° are very difficult
to solve reliably and the mesh should be refined.

High aspect ratio is expected and acceptable in the wall-normal direction for
wall-resolved turbulence. The concern is high aspect ratio in the axial
direction near the throat, which can cause slow convergence of the pressure
equation.

### Visualize the mesh before solving

```bash
paraFoam -builtin &
```

In ParaView:
1. Apply the reader, then switch representation to **Wireframe**.
2. Check that the wall spline is smooth — no kinks at block boundaries.
3. Check that cell density increases toward the wall (grading is visible as
   compressed cells near the bottom of the domain).
4. Confirm the throat region has adequate axial resolution. The throat should
   span at least 5 cells axially; if it spans fewer than 3, increase
   `setAxialCells()`.

---

## 4. Running the solver

### Set the time step

Open `system/controlDict`. The critical setting is `deltaT` (or `maxCo` if
the exporter wrote a Courant-number-controlled run):

```
application     rhoCentralFoam;
startFrom       startTime;
startTime       0;
stopAt          endTime;
endTime         5e-4;           // seconds — adjust to reach steady state
deltaT          1e-8;           // initial time step — start small
maxCo           0.3;            // Courant number limit
adjustTimeStep  yes;
writeControl    adjustableRunTime;
writeInterval   1e-5;
```

**Choosing `endTime`:** Steady state is typically reached within 1–5 flow-through
times. The flow-through time is approximately:

```
t_flow ≈ L_nozzle / U_exit
```

For a 200 mm nozzle with M=3.5 exit at 3500 K, U_exit ≈ 2100 m/s, so
t_flow ≈ 95 µs. Set `endTime` to at least 5 × t_flow ≈ 0.5 ms. Use 2 ms if
you are unsure whether steady state has been reached.

**Choosing `deltaT`:** Start at 1 × 10⁻⁸ s. The solver will increase the time
step automatically under Courant-number control. If the solver diverges in the
first 10 time steps, halve `deltaT` and restart.

### Stabilizing a cold-start

A nozzle simulation started from rest (uniform field) will pass through a
transient where the shock system forms and the flow chokes at the throat. This
transient is numerically stressful. To stabilize it:

1. **Ramp the inlet pressure.** Edit `0/p` to use a `uniformFixedValue` with a
   time-series file that ramps from `ambientPressure` to `chamberPressure` over
   the first 0.1 ms. Once the flow is established, switch to `totalPressure`.

2. **Initialize from a 1-D solution.** Use `setFields` to initialize the domain
   with the isentropic profile: high pressure/temperature upstream of the throat,
   low downstream. This dramatically reduces transient time.

3. **Run in 2-D first.** If available, run a 2-D axisymmetric simulation with
   a coarser mesh to get a converged field, then map it onto the 3-D wedge mesh
   using `mapFields`.

### Running in parallel

For meshes with more than ~100 000 cells, run in parallel:

```bash
decomposePar
mpirun -np 8 rhoCentralFoam -parallel
reconstructPar
```

Update `system/decomposeParDict` to set `numberOfSubdomains` to your core count.
Use `scotch` decomposition for best load balance on a structured mesh.

---

## 5. Monitoring convergence

### Residuals

While the solver runs, residuals are printed to the terminal and to
`postProcessing/residuals/0/residuals.dat` if the `residuals` function object
is active. Steady state is indicated when residuals plateau at a low level for
multiple flow-through times.

Typical converged residuals for `rhoCentralFoam`:

| Field   | Converged order of magnitude |
|---------|------------------------------|
| `p`     | 10⁻⁶ – 10⁻⁸                  |
| `T`     | 10⁻⁶ – 10⁻⁸                  |
| `Ux`    | 10⁻⁶ – 10⁻⁸                  |
| `k`     | 10⁻⁴ – 10⁻⁶                  |
| `omega` | 10⁻⁴ – 10⁻⁶                  |

If residuals plateau above 10⁻⁴ for p, T, or U, the solution is not well
converged. Check for:
- Numerical instability near the throat (reduce `maxCo` to 0.2).
- Unphysical separation in the diverging section (check for negative pressure).
- Incorrect boundary conditions (inlet or outlet).

### Integrated quantities

Monitor the wall pressure force and mass flow at each write interval. Add these
`functionObjects` to `system/controlDict`:

```
functions
{
    forces
    {
        type            forces;
        libs            ( "libforces.so" );
        patches         ( wall );
        rho             rhoInf;
        rhoInf          1.0;        // placeholder; overridden by solver
        CofR            ( 0 0 0 );
        writeControl    timeStep;
        writeInterval   10;
    }

    massFlow
    {
        type            surfaceFieldValue;
        libs            ( "libfieldFunctionObjects.so" );
        regionType      patch;
        name            inlet;
        operation       sum;
        fields          ( phi );    // phi = mass flux, kg/s
        writeControl    timeStep;
        writeInterval   10;
    }

    wallPressure
    {
        type            pressure;
        libs            ( "libfieldFunctionObjects.so" );
        patches         ( wall );
        mode            totalPressure;
        p0              uniform 7000000;
        writeControl    timeStep;
        writeInterval   10;
    }
}
```

Plot the integrated thrust force vs. time. When it stops changing (< 0.1%
variation over 5 flow-through times), the simulation is at steady state.

---

## 6. Visualizing the flow field

Open the results in ParaView:

```bash
paraFoam &
```

Apply the reader and click through the time steps to the last written time.

### Key flow features to examine

**Mach number contour**

Calculate Mach number: `Filters → Calculator → mag(U) / sqrt(1.4 * p / rho)`.
(Replace 1.4 with your γ from `thermophysicalProperties`.) A well-designed
nozzle should show:

- Smooth subsonic flow from inlet to throat.
- Clean sonic line at the throat plane (M = 1 contour should be nearly planar
  and perpendicular to the axis).
- Progressively increasing M through the diverging section.
- Uniform exit Mach profile with only a thin boundary layer near the wall.

**Oblique shocks**

Shocks appear as abrupt Mach number gradients. Some internal shocks are normal
in a nozzle and are captured by the MOC computation. What you should not see
is a strong normal shock filling the cross-section — that indicates the nozzle
is operating in a highly over-expanded regime and the throat may not be choked.

**Flow separation**

Look at the axial velocity component near the wall in the diverging section. A
region of negative Ux (reverse flow) against the wall indicates separation. If
separation is present at the design ambient pressure, the nozzle geometry or
the operating condition needs revision.

**Pressure along the wall**

`Filters → Plot Over Line` from (0, r_throat, 0) to (L, r_exit, 0) along the
wall. Compare the extracted pressure profile against `HeatTransferModel`'s
isentropic prediction. Good agreement (< 2%) confirms the CFD flow field is
physically consistent with the analytical model.

**Temperature contours**

The hot-gas temperature drops from Tc at the inlet to roughly Tc × (1 +
(γ-1)/2 × Me²)⁻¹ at the exit. The wall temperature should be significantly
lower than the gas temperature in a cooled design. A temperature layer hugging
the wall that is hotter than the analytical prediction indicates the wall
boundary condition is set incorrectly (too hot a wall temperature or an
adiabatic condition where active cooling should be modeled).

---

## 7. Extracting Cf and Isp from CFD results

The library reports Cf and Isp analytically. Extracting equivalent quantities
from the CFD solution requires integrating the pressure and momentum flux over
the appropriate surfaces.

### Thrust from CFD

Thrust is the net axial momentum flux plus the pressure-area term at the exit,
minus the momentum flux at the inlet:

```
F = ∫_exit (ρ Ux² + (p - Pa)) dA  −  ∫_inlet ρ Ux² dA  +  F_wall_pressure
```

In OpenFOAM, use the `forces` function object output (written to
`postProcessing/forces/`). The relevant column is the pressure force on the
`wall` patch in the x-direction, plus the momentum flux difference between exit
and inlet patches.

Alternatively, a simpler but equivalent approach: the axial momentum balance
gives:

```
F_net = dot(forces_wall, x_hat) + massFlowRate × (U_exit - U_inlet)
```

Extract these from the `forces` and `massFlow` function object output files.

### Mass flow rate

Read from `postProcessing/massFlow/0/surfaceFieldValue.dat`. The `phi` field
integrated over the inlet patch gives the total mass flow rate in kg/s.

Verify that mass is conserved: the integral of `phi` over the inlet should
equal the integral over the outlet (within 0.01% for a converged steady-state
solution).

### Thrust coefficient Cf

```
Cf_CFD = F_net / (Pc_CFD × A_throat)
```

where:
- `F_net` — extracted from forces as above.
- `Pc_CFD` — the area-averaged total pressure at the inlet patch. Extract with
  `Filters → Integrate Variables` applied to the inlet surface, field `p`.
- `A_throat` — π × r_throat² (known from `params.throatArea()`).

### Specific impulse Isp

```
Isp_CFD = F_net / (m_dot × g0)
```

where `m_dot` is the mass flow rate from the `massFlow` function object and
`g0 = 9.80665 m/s²`.

### Step-by-step extraction using Python + ofpost

If you have the `ofpost` Python package available:

```python
import ofpost

case = ofpost.Case("nozzle_case")
last_time = case.times[-1]

# Wall force
forces = ofpost.read_forces(case, time=last_time, patch="wall")
F_net = forces["pressure"]["x"] + forces["viscous"]["x"]

# Mass flow (phi is negative for flow leaving through inlet — take abs)
m_dot = abs(ofpost.read_surface_field_value(case, "massFlow", last_time))

# Inlet total pressure
Pc_cfd = ofpost.patch_average(case, "p", "inlet", last_time)

A_throat = 3.14159 * (0.05**2)   # from params.throatArea()
g0 = 9.80665

Cf_cfd  = F_net / (Pc_cfd * A_throat)
Isp_cfd = F_net / (m_dot * g0)

print(f"F_net   = {F_net:.1f} N")
print(f"m_dot   = {m_dot:.4f} kg/s")
print(f"Pc_CFD  = {Pc_cfd/1e6:.3f} MPa")
print(f"Cf_CFD  = {Cf_cfd:.4f}")
print(f"Isp_CFD = {Isp_cfd:.1f} s")
```

---

## 8. Comparing CFD against the analytical model

Run the library's `PerformanceCalculator` with the same design parameters used
for the CFD export, then compare:

```
PerformanceCalculator perf = new PerformanceCalculator(params, net, contour, bl, chem)
        .calculate();

System.out.printf("%-25s  %8s  %8s  %8s%n", "Quantity", "Analytical", "CFD",     "Diff %%");
System.out.printf("%-25s  %8.4f  %8.4f  %8.2f%n",
        "Ideal Cf",      perf.getIdealThrustCoefficient(), Cf_ideal_cfd,
        pctDiff(perf.getIdealThrustCoefficient(), Cf_ideal_cfd));
System.out.printf("%-25s  %8.4f  %8.4f  %8.2f%n",
        "Actual Cf",     perf.getActualThrustCoefficient(), Cf_cfd,
        pctDiff(perf.getActualThrustCoefficient(), Cf_cfd));
System.out.printf("%-25s  %8.1f  %8.1f  %8.2f%n",
        "Isp (s)",       perf.getSpecificImpulse(), Isp_cfd,
        pctDiff(perf.getSpecificImpulse(), Isp_cfd));
```

### Expected agreement and what discrepancies mean

| Quantity              | Expected CFD vs. analytical agreement | If difference is larger                                                          |
|-----------------------|---------------------------------------|----------------------------------------------------------------------------------|
| Ideal Cf              | < 0.5%                                | Check that Pc_CFD matches `params.chamberPressure()`                             |
| Actual Cf             | 0.5–2%                                | Expected; CFD captures additional 3-D and viscous effects                        |
| Isp                   | 0.5–2%                                | Check mass flow extraction; verify `m_dot_CFD ≈ m_dot_analytical`                |
| Exit Mach             | < 1%                                  | Check that the exit area ratio in the CFD mesh matches the design                |
| Wall pressure profile | < 2% point-wise                       | Larger deviation near throat is normal; large exit deviation suggests separation |

**The analytical model is faster but less complete.** The CFD solution captures
effects the library does not model: 3-D flow structure inside the diverging
section, shock-boundary-layer interactions, viscous dissipation in the boundary
layer, and any separated flow regions. Agreement within 1–2% on Cf and Isp
means the analytical model is a reliable predictor for preliminary design.
Larger discrepancies require investigation.

### Wall pressure comparison

Extract the wall pressure from CFD using `Plot Over Line` along the wall
contour, and compare against the isentropic prediction from `CharacteristicPoint`:

```
// Analytical isentropic wall pressure at each MOC wall point
for (CharacteristicPoint p : net.getWallPoints()) {
    double pStatic = params.chamberPressure()
            * GasProperties.LOX_RP1_PRODUCTS.isentropicPressureRatio(p.mach());
    System.out.printf("x=%.4f m  M=%.4f  p=%.0f Pa%n", p.x(), p.mach(), pStatic);
}
```

A CFD wall pressure that matches the isentropic prediction within 2% confirms
that:
- The contour is correctly captured by the mesh spline.
- The flow is fully attached through the diverging section.
- The throat is choked (no oblique shock at the throat visible in the pressure trace).

A local pressure bump in the diverging section indicates an oblique shock.
Compare its location with the characteristic lines in the MOC net to determine
whether it is a physical feature or a mesh artifact.

---

## 9. Gmsh workflow

`CFDMeshExporter` with `Format.GMSH_GEO` writes a `.geo` file that Gmsh can
mesh into an unstructured or structured grid.

### Generating the mesh

```bash
gmsh nozzle.geo -2 -format msh2 -o nozzle.msh
```

The `-2` flag generates a 2-D surface mesh. For an axisymmetric 2-D CFD
simulation this is directly usable in solvers that support 2-D axisymmetric
flow (e.g. SU2 with `AXISYMMETRIC= YES`).

For a 3-D mesh revolved around the axis:

```bash
gmsh nozzle.geo -3 -format msh2 -o nozzle_3d.msh
```

### Key Gmsh settings in the exported file

The `.geo` file written by the exporter uses `Transfinite` curves and surfaces
to produce a structured quad mesh. Verify these settings in the file:

```
Transfinite Curve {wall_line}  = 200 Using Progression 1.0;  // axial cells
Transfinite Curve {radial_line} = 80  Using Progression r;   // radial cells, ratio r
Transfinite Surface {...};
Recombine Surface {...};   // quads, not triangles
```

If you change the cell counts in the library before re-exporting, these
`Transfinite` counts update automatically.

### Converting Gmsh output to OpenFOAM

```bash
gmshToFoam nozzle.msh
```

Then set boundary conditions in the converted case following the same guidance
as Section 2.

---

## 10. Plot3D and CGNS with other solvers

`CFDMeshExporter` with `Format.PLOT3D` or `Format.CGNS` writes structured grid
files compatible with most commercial and research solvers (OVERFLOW, FUN3D,
STAR-CCM+, ANSYS Fluent).

### Plot3D

The exported `.xyz` file is a binary multi-block Plot3D grid with a single
block (the nozzle interior). Import into your solver using its standard
Plot3D reader. The grid ordering is:

- **i-direction:** axial (throat to exit).
- **j-direction:** radial (axis to wall).
- **k-direction:** circumferential (single plane for 2-D; use axisymmetric option in solver).

After import, define boundary conditions on the faces:

| Plot3D face | Boundary condition                     |
|-------------|----------------------------------------|
| i = 1       | Inlet (subsonic total pressure inflow) |
| i = imax    | Outlet (back pressure)                 |
| j = 1       | Axis (symmetry / axisymmetric)         |
| j = jmax    | Wall (no-slip or slip depending on y⁺) |

### CGNS

The exported `.cgns` file includes zone connectivity and boundary condition
markers. Most CGNS-compatible solvers will read the boundary names directly:
`inlet`, `outlet`, `wall`, `axis`. Confirm the boundary condition types are
set correctly in the solver after import — CGNS carries the zone names but not
the physical boundary condition type.

### Setting inlet conditions for non-OpenFOAM solvers

The inlet total conditions to set in any solver:

| Quantity          | Value                                           | Source  |
|-------------------|-------------------------------------------------|---------|
| Total pressure    | `params.chamberPressure()`                      | Pa      |
| Total temperature | `params.chamberTemperature()`                   | K       |
| γ                 | `params.gasProperties().gamma()`                | —       |
| Molecular weight  | `params.gasProperties().molecularWeight()`      | kg/kmol |
| Viscosity         | `params.gasProperties().calculateViscosity(Tc)` | Pa·s    |

Use a supersonic outflow (zero-gradient / extrapolation) condition at the exit
if the nozzle operates at or above the design expansion ratio. Use a specified
back pressure if you are modeling over-expanded operation.
