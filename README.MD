# Supersonic Nozzle Design — Method of Characteristics

A Java 21 library for supersonic rocket nozzle design using the Method of
Characteristics (MOC), with equilibrium chemistry, thermal analysis,
structural analysis, and multi-format CAD/CFD export.

---

## Features

### Nozzle Design
- **MOC solver** — full axisymmetric characteristic net; initial data line placed on the
  curved sonic surface (Hall 1962: x_s(r) = coeff·r²·(1/R_cd + 1/(3·R_cu))) rather than
  a flat plane, removing leading-order bias from the first few characteristic rows;
  axisymmetric source-term correction applied throughout
- **Minimum-Length Nozzle (MLN)** — `MinimumLengthNozzle` implements the Foelsch/Hall
  algorithm: maximum wall angle θ_max = ν(M_e)/2 at the throat corner,
  characteristic propagation from the curved sonic initial-data line,
  axis-reflection symmetry condition, and termination when θ_wall ≤ 0;
  reports nozzle length, exit A/A★, and L/L_cone ratio
- **Viscous MOC correction** — `ViscousMOCSolver` applies a White (1974) compressible
  turbulent flat-plate boundary-layer displacement correction to the inviscid MOC wall;
  the physical (machined) wall is outset by δ★(x) at each station; reports Cd_BL and
  viscous Isp-loss fraction
- **Contour families** — Rao optimum bell, conical, Truncated Ideal Contour (TIC),
  MOC-generated, and custom cubic spline
- **Rao bell nozzle** — empirical optimum bell with length fraction control and
  comparison against full MOC results
- **Aerospike (plug) nozzle** — MOC kernel-flow algorithm, spike geometry,
  truncation, and altitude compensation
- **Dual-bell nozzle** — altitude-compensating two-section bell with sea-level and
  high-altitude Isp, transition pressure, and kink geometry
- **Convergent section geometry** — `ConvergentSection` generates the full
  chamber-to-throat wall contour: upstream circular arc (r_cu), conical section,
  and chamber face; C¹-continuous throughout
- **Configurable throat geometry** — `NozzleDesignParameters` exposes optional
  parameters (all backward-compatible): `throatCurvatureRatio` (r_cd/r_t,
  default 0.382), `upstreamCurvatureRatio` (r_cu/r_t, default 1.5),
  `convergentHalfAngle` (default 30°), `contractionRatio` (Ac/At, default 4.0),
  and `throatWidth` (span of a 2-D rectangular nozzle, default 1.0 m);
  derived quantities `convergentLength()` and `convergentLengthRatio()` give
  the axial extent of the convergent section and its ratio to throat diameter
- **Planar (2-D rectangular) nozzle support** — set `axisymmetric(false)` and
  `throatWidth(w)` to model wind-tunnel or linear Aerospike nozzles with a
  rectangular cross-section; throat area becomes `2 × r_t × w` and all
  derived quantities (exit area, mass flow, thrust) scale accordingly;
  the CSV exporter emits `throat_width` only when the geometry is planar
- **Discharge coefficient** — `NozzleDesignParameters.dischargeCoefficient()`
  computes the geometric sonic-line Cd from the Kliegel & Levine (1969)
  harmonic-mean curvature model; for Rao defaults (r_cd/r_t = 0.382,
  r_cu/r_t = 1.5, γ = 1.4) this gives Cd ≈ 0.9962
- **Performance map** — `NozzlePerformanceMap` generates a 2-D grid of Isp vs
  altitude (0–80 km, US Standard Atmosphere) and expansion ratio; reports the
  optimum ε at each altitude, handles flow separation via the Summerfield
  criterion, and interpolates via `isp(altKm, eps)` and
  `optimumExpansionRatio(altKm)`

### Solid Motor Ballistics
- **Vieille's law burn rate** — `r = a₀ · exp(σ_p · (T−T_ref)) · P^n`; five
  built-in propellants: APCP/HTPB, APCP/PBAN, KNSU, KNDX, double-base
- **Grain geometry** — `BatesGrain` (cylindrical bore + two free annular end
  faces; corrected `L−2y` effective-length formula guarantees exact mass
  conservation) and `EndBurningGrain` (perfectly neutral)
- **Quasi-steady chamber simulation** — `SolidMotorChamber` forward-Euler
  trajectory from ignition to burnout; outputs time-series pressure, mass
  flow, and burn rate
- **Throat sizing** — invert the Kn equation `P_c = (ρ_p · a · K_n · c*)^(1/(1−n))`
  to size `A_t` for a target initial chamber pressure
- **MIL-STD temperature sweep** — evaluate ballistic sensitivity over the
  −40 °C to +71 °C qualification range
- **Erosive burning** — `ErosiveBurningModel` implements the Lenoir-Robillard (1957)
  model: `r_e = α · G^0.8 · exp(−β · r_0 · ρ_p / G)` with self-limiting
  transpiration-blocking term; provides `augmentedBurnRate`, `erosiveFraction`,
  `coreFlux`, and `axiallyAveragedErosion` (Simpson's-rule integral over grain length)
- **Two-phase flow losses** — `TwoPhaseFlowModel` performs 1-D Lagrangian particle
  tracking (Schiller-Naumann drag, Ranz-Marshall heat transfer) through the MOC
  wall-point gas field; reports two-phase efficiency η_2φ and Isp-loss fraction;
  built-in Al₂O₃ defaults for APCP/HTPB with 18 % aluminum
- **Pipeline bridge** — `BurnTrajectory.toNozzleParameters()` overlays average
  (or peak) `P_c`, `T_c`, and combustion `GasProperties` onto a
  `NozzleDesignParameters` template, feeding directly into the contour,
  performance, thermal, and export pipeline
- **RASP `.eng` import** — `RaspImporter` parses OpenMotor / ThrustCurve `.eng`
  thrust-curve files; `RaspMotorData.toNozzleParameters()` inverts the
  isentropic Cf equation to recover `P_c` from measured thrust and feeds
  directly into the same nozzle pipeline (supports single- and multi-motor
  files, NAR motor classification, and peak-pressure structural sizing)
- **RASP `.eng` export** — `RaspExporter` writes a thrust-time curve computed
  from a `BurnTrajectory` into the standard RASP `.eng` format so outputs
  can be imported by OpenMotor, RASAero II, and ThrustCurve.org

### Chemistry
- **Frozen and equilibrium flow** — selectable per analysis stage
- **Gibbs free energy minimization** — multi-species equilibrium solver
  (H₂O, CO₂, CO, H₂, O₂, OH, O, H, N₂, NO, C, Ar and propellant-specific species)
- **NASA-7 polynomial thermodynamics** — Cp, H, S for all species
- **O/F sweep** — Isp(O/F) and c★(O/F) curves with golden-section optimum search
- **Isp comparison** — `ChemistryModel.compareIsp()` returns an `IspComparison`
  record with `frozenIsp`, `equilibriumIsp`, `delta()`, and `deltaPercent()`
  at a single operating point
- **Propellants** — LOX/RP-1, LOX/LH₂, LOX/CH₄, N₂O/Ethanol, N₂O/Propane

### Thermal and Structural Analysis
- **Boundary layer correction** — displacement and momentum thickness,
  skin-friction loss; `calculateFromInjectorFace()` starts BL growth at the
  chamber inlet for a physically accurate throat δ★ and combined Cd
- **Heat transfer model** — Bartz/Eckert convective correlation plus radiation;
  wall temperature and heat-flux profiles along the contour; `calculateFullProfile()`
  covers the complete wall (convergent + divergent) and reports the heat-flux peak
  location, which shifts with upstream curvature ratio `r_cu`
- **Regenerative coolant channel** — hydraulics, coolant-side heat transfer,
  temperature rise along channel length
- **Ablative liner model** — Arrhenius char-rate, mechanical erosion supplement,
  material recession, and ablated-mass budget for five material families
  (carbon-phenolic, silica-phenolic, EPDM, graphite, C/C composite)
- **Radiation-cooled extension** — equilibrium wall temperature for a radiating
  nozzle extension with material overtemperature detection
- **Thermal stress analysis** — Timoshenko thin-shell plus Lamé pressure stress,
  von Mises criterion, fatigue life (Basquin and Coffin-Manson)

### Performance
- **PerformanceCalculator** — thrust coefficient and Isp with four loss mechanisms:
  divergence, boundary layer, chemistry (frozen/equilibrium), and two-phase
- **Flow separation prediction** — Summerfield, Schmucker, and Stark criteria
- **Shock-expansion off-design** — altitude sweep of Cf and Isp via shock-expansion
  theory
- **Altitude-adaptive optimizer** — multi-altitude Isp optimization over geometry
  parameters
- **Monte Carlo uncertainty analysis** — 1000-sample sensitivity to input tolerances

### Full Nozzle Geometry
- **`FullNozzleGeometry`** — assembles the complete internal wall profile from
  injector face to exit by concatenating a `ConvergentSection` (x < 0) with a
  `NozzleContour` divergent section (x ≥ 0) into a single, axially monotonic
  wall-point sequence suitable for CAD/CFD exporters

### Unit Conversion
- **`Units`** — static utility class providing ~60 named conversion methods for
  common aerospace units: length, pressure, temperature, force, mass, specific
  impulse, and specific heat; designed for use at system input/output boundaries

### Validation
- **NASA SP-8120 compliance** — automated checks against the NASA design guidelines
  for divergence angle, throat-arc radius, and exit-area ratio bounds

### Export
| Format                                         | Bell/TIC/Conical | Aerospike   | Dual-Bell | Full nozzle (conv+div) |
|------------------------------------------------|------------------|-------------|-----------|------------------------|
| CSV (contour, thermal, stress, altitude sweep) | ✓                | ✓           | ✓         | ✓                      |
| DXF (2D wall profile + axis, full nozzle)      | ✓                | ✓           | ✓         | ✓                      |
| STEP (ISO 10303-21 revolved solid)             | ✓                | ✓           | ✓         | ✓                      |
| STL (binary/ASCII revolved mesh)               | ✓                | ✓           | ✓         | ✓                      |
| OpenFOAM blockMesh (5° wedge)                  | ✓                | ✓ (annular) | ✓         | ✓                      |
| Gmsh .geo transfinite mesh                     | ✓                | ✓           | ✓         | ✓                      |
| Plot3D / CGNS structured grid                  | ✓                | ✓           | ✓         | ✓ (Plot3D)             |
| OpenFOAM rhoCentralFoam case                   | ✓                | ✓ (annular) | ✓         | ✓                      |
| OpenFOAM blockMesh (full 3-D revolved)         | ✓                | —           | ✓         | ✓                      |
| Gmsh .geo (full 3-D revolved)                  | ✓                | —           | ✓         | ✓                      |
| Plot3D .xyz (full 3-D revolved)                | ✓                | —           | ✓         | ✓                      |
| RASP .eng (thrust-curve export)                | ✓ (solid motor)  | —           | —         | —                      |

Every exporter accepts a `FullNozzleGeometry` overload that covers the complete
internal wall from injector face (x < 0) to nozzle exit (x > 0).

DXF exports for dual-bell nozzles include a `KINK` layer marking the inflection
point (POINT entity + vertical drop-line to the axis).  CSV dual-bell reports
label each contour row as `BASE` or `EXTENSION`.  The Aerospike and dual-bell
STEP exports include both a revolved solid and a profile B-spline curve.

---

## Requirements

- Java 21 (preview features enabled)
- Maven 3.8+

---

## Building

```bash
mvn clean package
```

Run all demonstrations:

```bash
mvn exec:java -Dexec.mainClass="com.nozzle.Main"
```

Run a single demonstration by name:

```bash
mvn exec:java -Dexec.mainClass="com.nozzle.Main" -Dexec.args="solidmotor"
mvn exec:java -Dexec.mainClass="com.nozzle.examples.DemonstrateSolidMotor"
```

Each class in `com.nozzle.examples` has its own `public static void main` and
can be run independently without loading any other demonstration.

---

## Demonstrations (`com.nozzle.examples`)

Each `Demonstrate*` class is a self-contained runnable that exercises one area
of the library. Pass its lower-case name (no `Demonstrate` prefix) to `Main`
to run just that one, or run the class directly.

| Class                                        | What it shows                                                             |
|----------------------------------------------|---------------------------------------------------------------------------|
| `DemonstrateBasicDesign`                     | MOC net, sonic-line offset, performance, CSV export                       |
| `DemonstrateRaoComparison`                   | MOC vs Rao bell length and exit-angle comparison                          |
| `DemonstrateTruncatedIdealContour`           | TIC at three truncation fractions                                         |
| `DemonstrateThermalAnalysis`                 | Bartz heat transfer, coolant channel, BL correction                       |
| `DemonstrateChemistryModeling`               | Frozen/equilibrium Isp comparison table                                   |
| `DemonstrateValidation`                      | NASA SP-8120 validation checks                                            |
| `DemonstrateOptimization`                    | Altitude-adaptive Isp optimizer                                           |
| `DemonstrateUncertaintyAnalysis`             | Monte Carlo sensitivity analysis                                          |
| `DemonstrateFlowSeparationAndShockExpansion` | Summerfield/Schmucker/Stark criteria, shock-expansion altitude sweep      |
| `DemonstrateThermalStressAnalysis`           | Timoshenko stress, von Mises, fatigue life for four materials             |
| `DemonstrateExports`                         | DXF, STEP, STL, CSV, CFDMesh, OpenFOAM, Gmsh, Plot3D, 3-D revolved        |
| `DemonstrateAerospikeNozzle`                 | MOC kernel-flow spike, altitude performance, all exports                  |
| `DemonstrateAblativeLiner`                   | Arrhenius char-rate, erosion supplement, material comparison              |
| `DemonstrateRadiationCooledExtension`        | Equilibrium wall temperature, material comparison                         |
| `DemonstrateSerialization`                   | Three-stage NozzleSerializer workflow                                     |
| `DemonstrateYPlusGrading`                    | y⁺-derived first-cell height via CFDMeshExporter / OpenFOAMExporter       |
| `DemonstrateRevolvedMesh`                    | RevolvedMeshExporter in OpenFOAM / Gmsh / Plot3D, y⁺ grading              |
| `DemonstrateOFSweep`                         | O/F sweep across five propellant families, Isp-optimal vs c★-optimal      |
| `DemonstrateDualBellNozzle`                  | Dual-bell sweep, DLR cross-check, all exports                             |
| `DemonstrateThroatCurvatureRatio`            | r_cd/r_t effect on MOC, Rao bell, and conical contours                    |
| `DemonstrateConvergentSection`               | Convergent geometry, Cd sweep, BL comparison, exports                     |
| `DemonstrateUnitsConversion`                 | US-customary ↔ SI conversion utilities                                    |
| `DemonstrateFullNozzleGeometry`              | Chamber-face-to-exit geometry, MOC-backed variant, DXF exports            |
| `DemonstrateBoundaryLayerFromInjectorFace`   | Injector-face BL vs throat-only: throat δ★, combined Cd                   |
| `DemonstrateHeatFluxPeakLocation`            | Peak heat-flux shift with r_cu; `calculateFullProfile()` vs `calculate()` |
| `DemonstratePlanarWindTunnelNozzle`          | 2-D rectangular nozzle, span sensitivity, DXF/CSV/STL/STEP exports        |
| `DemonstrateSolidMotor`                      | APCP/HTPB BATES ballistics, MIL-STD sweep, KNSU comparison, exports       |
| `DemonstrateRaspImport`                      | RASP .eng parse, thrust-curve inversion, Rao bell design, exports         |
| `DemonstrateMinimumLengthNozzle`             | MLN geometry, Mach sweep, length comparison vs Rao bell                   |
| `DemonstrateViscousMOCSolver`                | BL displacement wall, Cd vs Pc sensitivity table                          |
| `DemonstrateTwoPhaseFlowModel`               | Al₂O₃ Lagrangian tracking, η_2φ vs α and d_p                              |
| `DemonstrateNozzlePerformanceMap`            | 2-D Isp map, altitude-optimum ε table, fixed-nozzle peak Isp              |
| `DemonstrateErosiveBurning`                  | Lenoir-Robillard model: G sweep, axial average, blocking, Pc sensitivity  |

---

## Quick Start

### 1. Define design intent

```java
NozzleDesignParameters params = NozzleDesignParameters.builder()
        .throatRadius(0.05)               // 50 mm throat radius
        .exitMach(3.5)
        .chamberPressure(7e6)             // 7 MPa
        .chamberTemperature(3500)         // K — or use OFSweep for adiabatic Tc
        .ambientPressure(101_325)
        .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
        .numberOfCharLines(30)
        .wallAngleInitialDegrees(30)
        .lengthFraction(0.8)              // 80% of ideal nozzle length
        .axisymmetric(true)
        // convergent-section geometry (all optional — defaults shown)
        .throatCurvatureRatio(0.382)      // r_cd/r_t — classical Rao value
        .upstreamCurvatureRatio(1.5)      // r_cu/r_t
        .convergentHalfAngleDegrees(30)   // conical convergent half-angle
        .contractionRatio(4.0)            // Ac/At
        // .throatWidth(0.15)             // span for 2-D planar nozzles (ignored when axisymmetric)
        .build();
```

### 2. Run the MOC solver

```java
CharacteristicNet net = new CharacteristicNet(params).generate();

System.out.printf("Wall points : %d%n", net.getWallPoints().size());
System.out.printf("Exit A/A*  : %.4f%n", net.calculateExitAreaRatio());
```

### 3. Build the wall contour

```java
// From MOC wall points (recommended)
NozzleContour contour = NozzleContour.fromMOCWallPoints(params, net.getWallPoints());
contour.generate(200);   // 200 discrete points along the wall

// Rao optimum bell approximation
NozzleContour raoContour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
raoContour.generate(200);

// Minimum-Length Nozzle (shortest possible for uniform exit flow)
MinimumLengthNozzle mln = new MinimumLengthNozzle(params).generate();
System.out.printf("MLN length : %.2f mm  (%.1f%% shorter than Rao)%n",
        mln.nozzleLength() * 1000,
        (1.0 - mln.nozzleLength() / raoContour.getLength()) * 100.0);

// Viscous-corrected wall (outset by δ★ for physically correct machined profile)
ViscousMOCSolver viscous = new ViscousMOCSolver(net).solve();
System.out.printf("Cd_BL      : %.5f%n", viscous.dischargeCoefficient());
```

### 4. Calculate performance

```java
BoundaryLayerCorrection bl  = new BoundaryLayerCorrection(params, net).calculate();
HeatTransferModel       htr = new HeatTransferModel(params, net, bl).calculate();
ChemistryModel          chem = ChemistryModel.equilibrium(params.gasProperties());

PerformanceCalculator perf = new PerformanceCalculator(params, net, contour, bl, chem)
        .calculate();

System.out.printf("Isp        : %.1f s%n",   perf.getSpecificImpulse());
System.out.printf("Cf (actual): %.4f%n",      perf.getActualThrustCoefficient());
System.out.printf("Efficiency : %.2f %%%n",   perf.getEfficiency() * 100);
```

---

## Minimum-Length Nozzle

The MLN is the theoretically shortest supersonic nozzle that produces
a specified uniform exit Mach number. All turning is concentrated at the
throat corner (Prandtl-Meyer fan); the wall downstream is derived entirely
from characteristic reflections.

```java
MinimumLengthNozzle mln = new MinimumLengthNozzle(params).generate();

System.out.printf("Max wall angle   : %.2f°%n",
        Math.toDegrees(mln.getMaximumWallAngle()));
System.out.printf("Nozzle length    : %.2f mm%n", mln.nozzleLength() * 1000);
System.out.printf("Exit area ratio  : %.4f%n",    mln.exitAreaRatio());
System.out.printf("L / L_cone(15°)  : %.4f%n",    mln.lengthRatioVsCone());
```

---

## Viscous MOC Solver

Applies the White (1974) compressible turbulent flat-plate displacement
correction to the inviscid MOC wall. The machined wall is outset by δ★(x)
to maintain the design exit Mach number.

```java
CharacteristicNet net    = new CharacteristicNet(params).generate();
ViscousMOCSolver  viscous = new ViscousMOCSolver(net).solve();

System.out.printf("Discharge coefficient : %.5f%n", viscous.dischargeCoefficient());
System.out.printf("Viscous Isp loss      : %.3f%%%n",
        viscous.viscousIspLossFraction() * 100.0);

// Per-station data
for (ViscousMOCSolver.ViscousWallPoint wp : viscous.getCorrectedWall()) {
    System.out.printf("x=%.2f mm  r_inv=%.4f mm  δ*=%.2f μm  r_phys=%.4f mm%n",
            wp.x() * 1000, wp.rInviscid() * 1000,
            wp.displacementThickness() * 1e6, wp.rPhysical() * 1000);
}
```

---

## Nozzle Performance Map

Generates a 2-D Isp map over altitude (0–80 km) and expansion ratio.

```java
NozzlePerformanceMap map = new NozzlePerformanceMap(params)
        .altitudePoints(30)
        .expansionRatioPoints(40)
        .maxExpansionRatio(100.0)
        .generate();

// Point query
double isp = map.isp(10.0, 25.0);              // Isp at 10 km, ε = 25

// Optimum ε at each altitude
double optEps = map.optimumExpansionRatio(20.0); // ε that maximises Isp at 20 km

// Peak Isp for a fixed-ε nozzle across all altitudes
double peakIsp = map.peakIspForFixedNozzle(20.0);
```

---

## Two-Phase Flow Model

Lagrangian Al₂O₃ particle tracking through the MOC gas field.

```java
// Default: Al₂O₃ properties for APCP/HTPB with 18 % aluminium
double alpha = 0.30;   // condensed-phase mass fraction
TwoPhaseFlowModel model = new TwoPhaseFlowModel(params, alpha)
        .solve(net.getWallPoints());

System.out.printf("Two-phase η : %.4f%n", model.twoPhaseEfficiency());
System.out.printf("Isp loss    : %.2f%%%n", model.ispLossFraction() * 100.0);
System.out.printf("Delivered Isp: %.1f s%n",
        params.idealSpecificImpulse() * model.twoPhaseEfficiency());

// Custom particle properties
TwoPhaseFlowModel custom = new TwoPhaseFlowModel(
        params, 0.25,
        4e-6,                               // d_p: 4 µm Sauter mean
        TwoPhaseFlowModel.AL2O3_DENSITY,
        TwoPhaseFlowModel.AL2O3_SPECIFIC_HEAT)
        .solve(net.getWallPoints());
```

---

## Erosive Burning Model

Lenoir-Robillard (1957) model with transpiration-blocking term.

```java
SolidPropellant     prop  = SolidPropellant.APCP_HTPB();
ErosiveBurningModel model = new ErosiveBurningModel(prop);  // default α, β, G_th

double r0 = prop.burnRate(7e6);               // base burn rate at 7 MPa
double G  = 400.0;                            // core mass flux kg/m²/s

double rTotal  = model.augmentedBurnRate(r0, G);   // r_0 + r_e
double fraction = model.erosiveFraction(r0, G);    // r_e / r_0

// Axially-averaged erosion over a grain of length L and bore radius ri
double avgRe = model.axiallyAveragedErosion(r0, 0.025, 0.150);

// Local core flux at axial position z from head end
double Gz = model.coreFlux(r0, 0.025, 0.075);
```

---

## Solid Motor Ballistics

### Propellant

```java
SolidPropellant p = SolidPropellant.APCP_HTPB();

System.out.printf("Burn rate @ 7 MPa : %.2f mm/s%n", p.burnRate(7e6) * 1000);
System.out.printf("Pressure exponent : %.3f%n",       p.burnRateExponent());
System.out.printf("c*                : %.0f m/s%n",   p.characteristicVelocity());
System.out.printf("T_c               : %.0f K%n",     p.chamberTemperature());
```

Built-in propellants: `APCP_HTPB()`, `APCP_PBAN()`, `KNSU()`, `KNDX()`, `DOUBLE_BASE()`.

### Grain geometry

```java
// BATES grain: 4 segments, Do=100 mm, Dp=50 mm, L=75 mm
BatesGrain grain = new BatesGrain(0.100, 0.050, 0.075, 4);

System.out.printf("Web thickness : %.1f mm%n", grain.webThickness() * 1000);
System.out.printf("Ab(0)         : %.4f m²%n", grain.burningArea(0.0));
System.out.printf("Propellant vol: %.1f cm³%n", grain.propellantVolume() * 1e6);

// End-burning grain: diameter=75 mm, length=200 mm (perfectly neutral)
EndBurningGrain ebGrain = new EndBurningGrain(0.075, 0.200);
```

### Throat sizing (target initial P_c)

```java
double targetPc = 7.0e6;   // 7 MPa
double ab0 = grain.burningArea(0.0);
double at  = ab0 * p.density() * p.burnRateCoefficient()
             * p.characteristicVelocity()
             / Math.pow(targetPc, 1.0 - p.burnRateExponent());
```

### Ballistic simulation

```java
SolidMotorChamber chamber = new SolidMotorChamber(p, grain, at);
BurnTrajectory traj = chamber.computeBurnTrajectory(294.0);  // 294 K nominal

System.out.printf("Propellant mass : %.4f kg%n",  traj.propellantMass());
System.out.printf("Burn time       : %.3f s%n",   traj.burnTime());
System.out.printf("Average Pc      : %.3f MPa%n", traj.averagePressure() / 1e6);
System.out.printf("Peak Pc         : %.3f MPa%n", traj.maxPressure()     / 1e6);
```

### MIL-STD temperature sweep

```java
BurnTrajectory cold = chamber.computeBurnTrajectory(233.0);  // −40 °C
BurnTrajectory hot  = chamber.computeBurnTrajectory(344.0);  // +71 °C

System.out.printf("Pressure swing: %+.1f %%%n",
        (hot.averagePressure() - cold.averagePressure())
        / cold.averagePressure() * 100.0);
```

### Bridge to the nozzle design pipeline

```java
NozzleDesignParameters template = NozzleDesignParameters.builder()
        .throatRadius(Math.sqrt(at / Math.PI))
        .exitMach(3.5)
        .ambientPressure(101325)
        .axisymmetric(true)
        .lengthFraction(0.8)
        .build();

// Average Pc (nominal design point)
NozzleDesignParameters params = traj.toNozzleParameters(template);

// Peak Pc (structural sizing)
NozzleDesignParameters paramsMax = traj.toNozzleParametersAtMaxPressure(template);

// Drop straight into the contour/performance/thermal/export pipeline
NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
PerformanceCalculator perf = new PerformanceCalculator(params, null, contour, null, null);
perf.calculate();

System.out.printf("Delivered Isp : %.1f s%n",  perf.getSummary().specificImpulseSeconds());
System.out.printf("Thrust        : %.3f kN%n", perf.getSummary().thrustNewtons() / 1000);
```

---

## RASP `.eng` Import (OpenMotor Bridge)

`RaspImporter` reads the RASP `.eng` thrust-curve format used by OpenMotor,
ThrustCurve.org, and most hobby-rocketry simulation tools.  `RaspMotorData`
exposes the parsed data, derived performance quantities, and a direct bridge
into the nozzle design pipeline.

### Loading a file

```java
// Single motor (first definition in the file)
RaspMotorData motor = RaspImporter.load(Path.of("H128-14.eng"));

// All motors from a multi-motor file
List<RaspMotorData> motors = RaspImporter.loadAll(Path.of("aerotech_catalog.eng"));
```

### Derived performance quantities

```java
System.out.printf("Motor class   : %s%n",      motor.motorClass());
System.out.printf("Total impulse : %.1f N·s%n", motor.totalImpulseNs());
System.out.printf("Burn time     : %.3f s%n",   motor.burnTime());
System.out.printf("Average thrust: %.1f N%n",   motor.averageThrustN());
System.out.printf("Peak thrust   : %.1f N%n",   motor.maxThrustN());
System.out.printf("Isp           : %.1f s%n",   motor.specificImpulseSeconds());
System.out.printf("Avg mdot      : %.4f kg/s%n", motor.averageMassFlowRateKgPerS());
```

### Bridge to the nozzle pipeline

```java
NozzleDesignParameters template = NozzleDesignParameters.builder()
        .throatRadius(0.012)
        .exitMach(3.0)
        .ambientPressure(101325)
        .chamberPressure(5.0e6)                       // placeholder — overridden
        .chamberTemperature(3200)                      // from propellant data sheet
        .gasProperties(GasProperties.APCP_HTPB_PRODUCTS)
        .axisymmetric(true)
        .lengthFraction(0.8)
        .build();

NozzleDesignParameters params    = motor.toNozzleParameters(template);
NozzleDesignParameters paramsMax = motor.toNozzleParametersAtMaxPressure(template);

NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
contour.generate(60);

PerformanceCalculator perf = new PerformanceCalculator(params, null, contour, null, null);
perf.calculate();
System.out.printf("Delivered Isp : %.1f s%n", perf.getSummary().specificImpulseSeconds());
```

### `.eng` file format

```
; Comment lines start with semicolon
<name> <diameter_mm> <length_mm> <delays> <prop_mass_kg> <total_mass_kg> <manufacturer>
   <time_s>  <thrust_N>
   <time_s>  <thrust_N>
   ...
;   ← optional terminator (or end of file)
```

---

## RASP `.eng` Export

`RaspExporter` writes a thrust-time curve from a `BurnTrajectory` into the RASP
`.eng` format for use in OpenMotor, RASAero II, and ThrustCurve.org.

```java
SolidMotorChamber chamber  = new SolidMotorChamber(propellant, grain, at);
BurnTrajectory    traj     = chamber.computeBurnTrajectory(294.0);

new RaspExporter().export(traj, Path.of("motor.eng"),
        "MyMotor-38mm", 38.0, 193.0, "0", "DEMO");
```

---

## O/F Sweep and Optimum Search

```java
OFSweep sweep = OFSweep.adiabatic(
        OFSweep.Propellant.LOX_RP1,
        7e6,    // Pc (Pa)
        3.5,    // exit Mach
        101_325 // Pa
);

OFSweep.OFPoint best = sweep.optimumIsp(1.5, 4.0);
System.out.printf("Optimum O/F : %.2f → Isp = %.1f s, Tc = %.0f K%n",
        best.of(), best.isp(), best.chamberTemperature());

// Feed optimum Tc back into the design
NozzleDesignParameters optimisedParams = NozzleDesignParameters.builder()
        // ... other fields ...
        .chamberTemperature(best.chamberTemperature())
        .build();
```

---

## Isp Comparison (Frozen vs Equilibrium)

```java
ChemistryModel chem = ChemistryModel.equilibrium(GasProperties.LOX_RP1_PRODUCTS);

ChemistryModel.IspComparison isp = chem.compareIsp(
        3500,    // chamber temperature K
        7e6,     // chamber pressure Pa
        3.5,     // exit Mach
        101_325  // ambient pressure Pa
);

System.out.printf("Frozen Isp     : %.1f s%n",   isp.frozenIsp());
System.out.printf("Equilibrium Isp: %.1f s%n",   isp.equilibriumIsp());
System.out.printf("Delta          : %.1f s%n",   isp.delta());
System.out.printf("Delta %%        : %.2f %%%n", isp.deltaPercent());
```

---

## Aerospike Nozzle

```java
AerospikeNozzle aero = new AerospikeNozzle(
        params,
        0.4,   // spike inner-to-throat radius ratio
        0.8,   // truncation fraction
        100    // contour points
).generate();

System.out.printf("Full spike length  : %.3f m%n", aero.getFullSpikeLength());
System.out.printf("Truncated length   : %.3f m%n", aero.getTruncatedLength());
System.out.printf("Cf @ sea level     : %.4f%n",
        aero.calculateThrustCoefficient(101_325));

// Altitude sweep
double[] altitudes = {0, 5000, 10000, 20000, 30000};
AltitudePerformance ap = aero.calculateAltitudePerformance(altitudes);
```

---

## Dual-Bell Nozzle

```java
DualBellNozzle db = new DualBellNozzle(params, 4.0).generate();  // transition AR = 4.0

DualBellNozzle.PerformanceSummary s = db.getPerformanceSummary();
System.out.printf("Sea-level Isp    : %.1f s%n",  s.seaLevelIsp());
System.out.printf("High-altitude Isp: %.1f s%n",  s.highAltitudeIsp());
System.out.printf("Isp gain         : %.1f s%n",  s.ispGain());
System.out.printf("Transition at    : %.0f Pa%n",  s.transitionPressure());
```

---

## Ablative Liner

```java
NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
contour.generate(60);

List<HeatTransferModel.WallThermalPoint> heatProfile =
        new HeatTransferModel(params, contour)
                .setWallProperties(1.0, 0.020)
                .setCoolantProperties(300.0, 0.0)
                .calculate(List.of())
                .getWallThermalProfile();

AblativeNozzleModel ablative = new AblativeNozzleModel(params, contour)
        .setMaterial(AblativeNozzleModel.AblativeMaterial.CARBON_PHENOLIC)
        .setInitialLinerThickness(0.020)
        .setBurnTime(10.0)
        .setErosionFactor(1e-5)
        .calculate(heatProfile);

System.out.printf("Max recession    : %.4f mm%n", ablative.getMaxRecessionDepth() * 1000);
System.out.printf("Min remaining    : %.4f mm%n", ablative.getMinRemainingThickness() * 1000);
System.out.printf("Total ablated mass: %.4f kg%n", ablative.getTotalAblatedMass());
System.out.printf("Perforated anywhere: %s%n",    ablative.isPerforatedAnywhere());
```

Available materials: `CARBON_PHENOLIC`, `SILICA_PHENOLIC`, `EPDM`, `GRAPHITE`,
`CARBON_CARBON`.

---

## Radiation-Cooled Extension

```java
double extensionStartX = contour.getLength() * 0.30;

RadiationCooledExtension rad = new RadiationCooledExtension(params, contour)
        .setMaterial(RadiationCooledExtension.ExtensionMaterial.NIOBIUM_C103)
        .setExtensionStartX(extensionStartX)
        .setEnvironmentTemperature(3.0)
        .calculate(List.of());

System.out.printf("Max wall temperature : %.0f K%n",  rad.getMaxWallTemperature());
System.out.printf("Temperature margin   : %.0f K%n",  rad.getMinTemperatureMargin());
System.out.printf("Total radiated power : %.2f kW%n", rad.getTotalRadiatedPower() / 1000);
System.out.printf("Overtemperature      : %s%n",      rad.isOvertemperatureAnywhere());
```

Available materials: `NIOBIUM_C103`, `RHENIUM_IRIDIUM`, `TITANIUM_6AL_4V`, `CARBON_CARBON`.

---

## Convergent Section

```java
ConvergentSection conv = new ConvergentSection(params).generate(60);

System.out.printf("Convergent length : %.3f m%n",  conv.getLength());
System.out.printf("Chamber radius    : %.3f m%n",  conv.getChamberRadius());
System.out.printf("Sonic-line Cd     : %.4f%n",    conv.getSonicLineCdCorrection());

// Or from params directly (no ConvergentSection needed)
double cd = params.dischargeCoefficient();
```

---

## Full Nozzle Geometry

```java
// Rao bell divergent + ConvergentSection derived from params
FullNozzleGeometry geom = new FullNozzleGeometry(params).generate(50, 100);

List<Point2D> wall = geom.getWallPoints(); // injector face → exit
System.out.printf("Total length  : %.3f m%n", geom.getTotalLength());
System.out.printf("Throat radius : %.3f m%n", geom.getThroatRadius());
System.out.printf("Sonic-line Cd : %.4f%n",   geom.getSonicLineCd());

// MOC-derived divergent section
CharacteristicNet net = new CharacteristicNet(params).generate();
FullNozzleGeometry mocGeom = FullNozzleGeometry.fromMOC(params, net).generate(50, 0);

// DXF export
DXFExporter dxf = new DXFExporter().setScaleFactor(1000);
dxf.exportFullNozzleProfile(geom, Path.of("nozzle.dxf"));
dxf.exportFullNozzleRevolutionProfile(geom, Path.of("profile.dxf"));
```

---

## Unit Conversion

```java
double mm  = Units.metersToMillimeters(0.05);     // → 50.0
double psi = Units.pascalsToPsi(101_325.0);        // ≈ 14.696
double pa  = Units.psiToPascals(100.0);            // ≈ 689 476
double f   = Units.kelvinToFahrenheit(288.15);     // ≈ 59.0
double btu = Units.jPerKgKToBtuPerLbmR(4186.8);   // ≈ 1.0
```

---

## Thermal Analysis

```java
// Boundary layer from throat (divergent section only)
BoundaryLayerCorrection bl = new BoundaryLayerCorrection(params, net).calculate();

// Boundary layer from injector face (more accurate throat δ★)
FullNozzleGeometry fullGeom = new FullNozzleGeometry(params).generate(50, 100);
BoundaryLayerCorrection blFull = new BoundaryLayerCorrection(params, contour)
        .setForceTurbulent(true)
        .calculateFromInjectorFace(fullGeom, null);

System.out.printf("Throat δ*           : %.4f mm%n",
        blFull.getThroatDisplacementThickness() * 1000);
System.out.printf("Combined Cd (geo×BL): %.4f%n", blFull.getCombinedCd());

// Full-profile heat transfer
HeatTransferModel htrFull = new HeatTransferModel(params, contour)
        .setWallProperties(20.0, 0.003)
        .setCoolantProperties(300.0, 5000.0)
        .calculateFullProfile(fullGeom, null);

HeatTransferModel.WallThermalPoint peak = htrFull.getPeakFluxPoint();
System.out.printf("Peak location  : %+.3f mm (+ = downstream of throat)%n",
        peak.x() * 1000);
System.out.printf("Peak heat flux : %.2e W/m²%n", peak.totalHeatFlux());
```

---

## CAD and CFD Export

```java
// DXF
new DXFExporter().setScaleFactor(1000)
        .exportFullNozzleProfile(fullGeom, Path.of("full_nozzle.dxf"));

// STEP revolved solid
new STEPExporter().exportRevolvedSolid(fullGeom, Path.of("nozzle_full.step"));

// STL mesh
new STLExporter().setCircumferentialSegments(72).setBinaryFormat(true)
        .exportMesh(fullGeom, Path.of("nozzle_full.stl"));

// CFD mesh with y⁺ grading
new CFDMeshExporter()
        .setAxialCells(200).setRadialCells(80).setFirstLayerThickness(1e-5)
        .export(fullGeom, Path.of("blockMeshDict"), CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);

// Complete OpenFOAM case
new OpenFOAMExporter()
        .setAxialCells(300).setRadialCells(100).setTurbulenceEnabled(true)
        .exportCase(params, fullGeom, Path.of("nozzle_case"));

// Full 3-D revolved mesh
new RevolvedMeshExporter()
        .setAxialCells(100).setRadialCells(40).setAzimuthalCells(16)
        .export(fullGeom, Path.of("blockMeshDict_3d"), RevolvedMeshExporter.Format.OPENFOAM_BLOCKMESH);
```

---

## Persistent Workflows (Serialization)

```java
// Stage 1 — save design intent
NozzleSerializer.save(DesignDocument.parametersOnly(params), Path.of("nozzle.json"));

// Stage 2 — load, solve, save
DesignDocument loaded = NozzleSerializer.load(Path.of("nozzle.json"));
CharacteristicNet net = new CharacteristicNet(loaded.parameters()).generate();
NozzleSerializer.save(loaded.withNet(net), Path.of("nozzle.json"));

// Stage 3 — load and analyze
DesignDocument solved = NozzleSerializer.load(Path.of("nozzle.json"));
```

---

## NASA SP-8120 Validation

```java
NASASP8120Validator validator = new NASASP8120Validator(params, net, contour);
List<String> warnings = validator.validate();
if (warnings.isEmpty()) {
    System.out.println("All SP-8120 checks passed.");
} else {
    warnings.forEach(System.out::println);
}
```

---

## Package Overview

| Package                   | Contents                                                                                                                                                                                |
|---------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `com.nozzle`              | `Main` — thin dispatcher to `com.nozzle.examples`                                                                                                                                       |
| `com.nozzle.core`         | `NozzleDesignParameters`, `GasProperties`, `PerformanceCalculator`, `FlowSeparationPredictor`, `ShockExpansionModel`, `NozzlePerformanceMap`, `Units`                                   |
| `com.nozzle.moc`          | `CharacteristicNet`, `MinimumLengthNozzle`, `ViscousMOCSolver`, `RaoNozzle`, `AerospikeNozzle`, `DualBellNozzle`, `CharacteristicPoint`, `AltitudePerformance`                          |
| `com.nozzle.geometry`     | `NozzleContour` (five contour families), `ConvergentSection`, `FullNozzleGeometry`, `Point2D`                                                                                           |
| `com.nozzle.solid`        | `SolidPropellant`, `GrainGeometry`, `BatesGrain`, `EndBurningGrain`, `SolidMotorChamber`, `BurnTrajectory`, `ErosiveBurningModel`, `TwoPhaseFlowModel`, `RaspImporter`, `RaspMotorData` |
| `com.nozzle.chemistry`    | `ChemistryModel`, `OFSweep`, `GibbsMinimizer`, `NasaSpeciesDatabase`                                                                                                                    |
| `com.nozzle.thermal`      | `HeatTransferModel`, `BoundaryLayerCorrection`, `CoolantChannel`, `ThermalStressAnalysis`, `AblativeNozzleModel`, `RadiationCooledExtension`                                            |
| `com.nozzle.export`       | `CFDMeshExporter`, `OpenFOAMExporter`, `CSVExporter`, `DXFExporter`, `STEPExporter`, `STLExporter`, `RevolvedMeshExporter`, `RaspExporter`                                              |
| `com.nozzle.optimization` | `AltitudeAdaptiveOptimizer`, `MonteCarloUncertainty`                                                                                                                                    |
| `com.nozzle.validation`   | `NASASP8120Validator`                                                                                                                                                                   |
| `com.nozzle.io`           | `NozzleSerializer`, `DesignDocument`                                                                                                                                                    |
| `com.nozzle.examples`     | 33 `Demonstrate*` classes — one per feature area, each independently runnable                                                                                                           |

---

## Design Conventions

- **Pure SI throughout** — all inputs and outputs are in SI units (metres, Pascals,
  Kelvin, Newtons, kg). The `Units` class in `com.nozzle.core` provides static
  conversion methods for use at imperial/SI system boundaries.
- **Immutable parameters** — `NozzleDesignParameters` is a Java record; the builder
  produces a new instance and is not re-entrant.
- **Fluent configuration** — all exporter and solver classes use method chaining for
  optional settings before the terminal `generate()` / `calculate()` / `export*()`
  call.
- **Jakarta Validation** — constraint annotations on all public API parameters;
  Hibernate Validator enforces them at construction time.

---

## License

This project is licensed under the GNU General Public License v3.0.
See the `LICENSE.TXT` file for the full license text.
