# Supersonic Nozzle Design — Method of Characteristics

A Java 21 library for supersonic rocket nozzle design using the Method of
Characteristics (MOC), with equilibrium chemistry, thermal analysis,
structural analysis, and multi-format CAD/CFD export.

---

## Features

### Nozzle Design
- **MOC solver** — full axisymmetric characteristic net with axisymmetric correction
- **Contour families** — Rao optimum bell, conical, Truncated Ideal Contour (TIC),
  MOC-generated, and custom cubic spline
- **Rao bell nozzle** — empirical optimum bell with length fraction control and
  comparison against full MOC results
- **Aerospike (plug) nozzle** — MOC kernel-flow algorithm, spike geometry,
  truncation, and altitude compensation
- **Dual-bell nozzle** — altitude-compensating two-section bell with sea-level and
  high-altitude Isp, transition pressure, and kink geometry

### Chemistry
- **Frozen and equilibrium flow** — selectable per analysis stage
- **Gibbs free energy minimization** — multi-species equilibrium solver
  (H₂O, CO₂, CO, H₂, O₂, OH, O, H, N₂, NO, C, Ar and propellant-specific species)
- **NASA-7 polynomial thermodynamics** — Cp, H, S for all species
- **O/F sweep** — Isp(O/F) and c★(O/F) curves with golden-section optimum search
- **Propellants** — LOX/RP-1, LOX/LH₂, LOX/CH₄, N₂O/Ethanol, N₂O/Propane

### Thermal and Structural Analysis
- **Boundary layer correction** — displacement and momentum thickness,
  skin-friction loss
- **Heat transfer model** — Bartz/Eckert convective correlation plus radiation;
  wall temperature and heat-flux profiles along the contour
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

### Validation
- **NASA SP-8120 compliance** — automated checks against the NASA design guidelines
  for divergence angle, throat-arc radius, and exit-area ratio bounds

### Export
| Format | Bell/TIC/Conical | Aerospike | Dual-Bell |
|---|---|---|---|
| CSV (contour, thermal, stress, altitude sweep) | ✓ | ✓ | ✓ |
| DXF (2D wall profile + axis) | ✓ | ✓ | ✓ |
| STEP (ISO 10303-21 revolved solid) | ✓ | ✓ | ✓ |
| STL (binary/ASCII revolved mesh) | ✓ | ✓ | ✓ |
| OpenFOAM blockMesh (5° wedge) | ✓ | ✓ (annular) | ✓ |
| Gmsh .geo transfinite mesh | ✓ | ✓ | ✓ |
| Plot3D / CGNS structured grid | ✓ | ✓ | ✓ |
| OpenFOAM rhoCentralFoam case | ✓ | — | ✓ |

---

## Requirements

- Java 21 (preview features enabled)
- Maven 3.8+

---

## Building

```bash
mvn clean package
```

Run the demonstration application:

```bash
mvn exec:java -Dexec.mainClass="com.nozzle.Main"
```

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

// Or use the Rao bell approximation
NozzleContour raoContour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
raoContour.generate(200);
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

## Thermal Analysis

```java
// Boundary layer and convective heat transfer
BoundaryLayerCorrection bl  = new BoundaryLayerCorrection(params, net).calculate();
HeatTransferModel       htr = new HeatTransferModel(params, net, bl).calculate();

System.out.printf("Peak wall temperature: %.0f K%n",  htr.getPeakWallTemperature());
System.out.printf("Peak heat flux       : %.2e W/m²%n", htr.getPeakHeatFlux());

// Regenerative coolant channel
CoolantChannel channel = new CoolantChannel(params, htr, CoolantChannel.Coolant.RP1)
        .calculate();

// Thermal stress and fatigue
ThermalStressAnalysis stress = new ThermalStressAnalysis(params, htr,
        ThermalStressAnalysis.Material.CU_CR_ZR).calculate();

System.out.printf("Peak von Mises stress: %.1f MPa%n",
        stress.getPeakVonMisesStress() / 1e6);
System.out.printf("Fatigue life         : %.0f cycles%n", stress.getFatigueLife());
```

---

## CAD and CFD Export

### DXF (2D profile)
```java
new DXFExporter()
        .setScaleFactor(1000)        // metres → mm
        .exportContour(contour, Path.of("nozzle.dxf"));
```

### STEP (3D solid)
```java
new STEPExporter()
        .setScaleFactor(1000)
        .setAuthor("Craig Walters")
        .exportRevolvedSolid(contour, Path.of("nozzle.step"));
```

### STL (3D mesh)
```java
new STLExporter()
        .setCircumferentialSegments(72)
        .setBinaryFormat(true)
        .exportMesh(contour, Path.of("nozzle.stl"));
```

### CFD mesh files
```java
CFDMeshExporter cfd = new CFDMeshExporter()
        .setAxialCells(200)
        .setRadialCells(80)
        .setFirstLayerThickness(1e-5);   // y⁺-controlled grading

cfd.export(contour, Path.of("blockMeshDict"), CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);
cfd.export(contour, Path.of("nozzle.geo"),   CFDMeshExporter.Format.GMSH_GEO);
cfd.export(contour, Path.of("nozzle.xyz"),   CFDMeshExporter.Format.PLOT3D);

// Or pass a nozzle object directly
cfd.export(new RaoNozzle(params).generate(), Path.of("blockMeshDict"),
        CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);
```

### Complete OpenFOAM case (rhoCentralFoam)
```java
new OpenFOAMExporter()
        .setAxialCells(300)
        .setRadialCells(100)
        .setTurbulenceEnabled(true)
        .setFirstLayerThickness(1e-5)
        .exportCase(params, contour, Path.of("nozzle_case"));

// Or pass a nozzle object directly
new OpenFOAMExporter().exportCase(new DualBellNozzle(params).generate(),
        Path.of("dualbell_case"));
```

The exported case is ready to run with:
```bash
blockMesh && rhoCentralFoam
```

---

## Persistent Workflows (Serialization)

```java
// Stage 1 — save design intent
DesignDocument doc = DesignDocument.parametersOnly(params);
NozzleSerializer.save(doc, Path.of("nozzle.json"));

// Stage 2 — load, solve, save
DesignDocument loaded = NozzleSerializer.load(Path.of("nozzle.json"));
CharacteristicNet net = new CharacteristicNet(loaded.parameters()).generate();
NozzleSerializer.save(loaded.withNet(net), Path.of("nozzle.json"));

// Stage 3 — load and analyse
DesignDocument solved = NozzleSerializer.load(Path.of("nozzle.json"));
// ... run performance, thermal, export ...
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

| Package | Contents |
|---|---|
| `com.nozzle.core` | `NozzleDesignParameters`, `GasProperties`, `PerformanceCalculator`, `FlowSeparationPredictor` |
| `com.nozzle.moc` | `CharacteristicNet`, `RaoNozzle`, `AerospikeNozzle`, `DualBellNozzle`, `CharacteristicPoint` |
| `com.nozzle.geometry` | `NozzleContour` (five contour families), `Point2D` |
| `com.nozzle.chemistry` | `ChemistryModel`, `OFSweep`, `GibbsMinimizer`, `NasaSpeciesDatabase` |
| `com.nozzle.thermal` | `HeatTransferModel`, `BoundaryLayerCorrection`, `CoolantChannel`, `ThermalStressAnalysis`, `AblativeNozzleModel`, `RadiationCooledExtension` |
| `com.nozzle.export` | `CFDMeshExporter`, `OpenFOAMExporter`, `CSVExporter`, `DXFExporter`, `STEPExporter`, `STLExporter`, `RevolvedMeshExporter` |
| `com.nozzle.optimization` | `AltitudeAdaptiveOptimizer`, `MonteCarloUncertainty` |
| `com.nozzle.validation` | `NASASP8120Validator` |
| `com.nozzle.io` | `NozzleSerializer`, `DesignDocument` |

---

## Design Conventions

- **Pure SI throughout** — all inputs and outputs are in SI units (metres, Pascals,
  Kelvin, Newtons, kg). Unit conversion at system boundaries is the caller's
  responsibility.
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
