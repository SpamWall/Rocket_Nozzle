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
  `throatWidth(w)` to model wind-tunnel or linear aerospike nozzles with a
  rectangular cross-section; throat area becomes `2 × r_t × w` and all
  derived quantities (exit area, mass flow, thrust) scale accordingly;
  the CSV exporter emits `throat_width` only when the geometry is planar
- **Discharge coefficient** — `NozzleDesignParameters.dischargeCoefficient()`
  computes the geometric sonic-line Cd from the Kliegel & Levine (1969)
  harmonic-mean curvature model; for Rao defaults (r_cd/r_t = 0.382,
  r_cu/r_t = 1.5, γ = 1.4) this gives Cd ≈ 0.9962

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
  skin-friction loss
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

Run the demonstration application:

```bash
mvn exec:java -Dexec.mainClass="com.nozzle.Main"
```

---

## Quick Start

### 1. Define design intent

```
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

```
CharacteristicNet net = new CharacteristicNet(params).generate();

System.out.printf("Wall points : %d%n", net.getWallPoints().size());
System.out.printf("Exit A/A*  : %.4f%n", net.calculateExitAreaRatio());
```

### 3. Build the wall contour

```
// From MOC wall points (recommended)
NozzleContour contour = NozzleContour.fromMOCWallPoints(params, net.getWallPoints());
contour.generate(200);   // 200 discrete points along the wall

// Rao optimum bell approximation
NozzleContour raoContour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
raoContour.generate(200);

// Truncated Ideal Contour (TIC) — truncation fraction set by lengthFraction
NozzleContour ticContour = new NozzleContour(NozzleContour.ContourType.TRUNCATED_IDEAL, params);
ticContour.generate(200);

// Conical nozzle
NozzleContour conical = new NozzleContour(NozzleContour.ContourType.CONICAL, params);
conical.generate(200);
```

### 4. Calculate performance

```
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

```
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

```
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

`ChemistryModel` also exposes `calculateFrozenIsp` and
`calculateEquilibriumIsp` separately if only one value is needed.

---

## Aerospike Nozzle

```
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

```
DualBellNozzle db = new DualBellNozzle(params, 4.0).generate();  // transition AR = 4.0

DualBellNozzle.PerformanceSummary s = db.getPerformanceSummary();
System.out.printf("Sea-level Isp    : %.1f s%n",  s.seaLevelIsp());
System.out.printf("High-altitude Isp: %.1f s%n",  s.highAltitudeIsp());
System.out.printf("Isp gain         : %.1f s%n",  s.ispGain());
System.out.printf("Transition at    : %.0f Pa%n",  s.transitionPressure());
```

---

## Ablative Liner

`AblativeNozzleModel` computes Arrhenius char recession and optional mechanical
erosion for five liner material families. Build a heat-transfer profile first,
then run the ablative model.

```
NozzleContour contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
contour.generate(60);

List<HeatTransferModel.WallThermalPoint> heatProfile =
        new HeatTransferModel(params, contour)
                .setWallProperties(1.0, 0.020)    // carbon-phenolic: k, liner thickness
                .setCoolantProperties(300.0, 0.0) // no active cooling
                .calculate(List.of())
                .getWallThermalProfile();

AblativeNozzleModel ablative = new AblativeNozzleModel(params, contour)
        .setMaterial(AblativeNozzleModel.AblativeMaterial.CARBON_PHENOLIC)
        .setInitialLinerThickness(0.020)  // 20 mm
        .setBurnTime(10.0)                // seconds
        .setErosionFactor(1e-5)           // mechanical erosion coefficient, m/s
        .calculate(heatProfile);

System.out.printf("Max recession    : %.4f mm%n", ablative.getMaxRecessionDepth() * 1000);
System.out.printf("Min remaining    : %.4f mm%n", ablative.getMinRemainingThickness() * 1000);
System.out.printf("Total ablated mass: %.4f kg%n", ablative.getTotalAblatedMass());
System.out.printf("Perforated anywhere: %s%n",    ablative.isPerforatedAnywhere());
```

Available materials: `CARBON_PHENOLIC`, `SILICA_PHENOLIC`, `EPDM`, `GRAPHITE`,
`CARBON_CARBON`.  The `AblativeMaterial` enum exposes `charRateAt(T)` for quick
Arrhenius evaluation without running a full model.

---

## Radiation-Cooled Extension

`RadiationCooledExtension` balances convective heat input against surface
radiation to find the equilibrium wall temperature of a high-emissivity nozzle
extension.

```
// LOX/LH2 upper-stage engine — bell regeneratively cooled to x_start;
// extension is radiation-cooled from x_start to exit.
double extensionStartX = contour.getLength() * 0.30;  // 30 % of bell length

RadiationCooledExtension rad = new RadiationCooledExtension(params, contour)
        .setMaterial(RadiationCooledExtension.ExtensionMaterial.NIOBIUM_C103)
        .setExtensionStartX(extensionStartX)
        .setEnvironmentTemperature(3.0)   // deep-space (K)
        .calculate(List.of());

System.out.printf("Max wall temperature : %.0f K%n",  rad.getMaxWallTemperature());
System.out.printf("Temperature margin   : %.0f K%n",  rad.getMinTemperatureMargin());
System.out.printf("Total radiated power : %.2f kW%n", rad.getTotalRadiatedPower() / 1000);
System.out.printf("Overtemperature      : %s%n",      rad.isOvertemperatureAnywhere());
```

Available materials: `NIOBIUM_C103`, `RHENIUM_IRIDIUM`, `TITANIUM_6AL_4V`,
`CARBON_CARBON`. Each material records an emissivity and a temperature limit;
`isOvertemperatureAnywhere()` flags if any station exceeds that limit.

---

## Convergent Section

`ConvergentSection` generates the wall contour from the chamber face to just
upstream of the throat and computes the geometric discharge-coefficient
correction arising from sonic-line curvature.

```
ConvergentSection conv = new ConvergentSection(params).generate(60);

System.out.printf("Convergent length : %.3f m%n",  conv.getLength());
System.out.printf("Chamber radius    : %.3f m%n",  conv.getChamberRadius());
System.out.printf("Sonic-line Cd     : %.4f%n",    conv.getSonicLineCdCorrection());

// Wall points can be prepended to a divergent contour
List<Point2D> convergentWall = conv.getContourPoints();  // chamber face → just upstream of throat
```

The same Cd value is also available directly from the parameters record without
constructing a `ConvergentSection`:

```
double cd = params.dischargeCoefficient();  // geometric sonic-line Cd
```

---

## Full Nozzle Geometry

`FullNozzleGeometry` combines a `ConvergentSection` and a divergent `NozzleContour`
into a single wall-point list ordered from the injector face (x < 0) to the exit
(x > 0). All geometry-complete exporters accept this object.

```
// Default: Rao bell divergent + ConvergentSection derived from params
FullNozzleGeometry geom = new FullNozzleGeometry(params).generate(50, 100);

List<Point2D> wall = geom.getWallPoints(); // injector face → exit
System.out.printf("Total length  : %.3f m%n", geom.getTotalLength());
System.out.printf("Throat radius : %.3f m%n", geom.getThroatRadius());
System.out.printf("Sonic-line Cd : %.4f%n",   geom.getSonicLineCd());

// MOC-derived divergent section
CharacteristicNet net = new CharacteristicNet(params).generate();
FullNozzleGeometry mocGeom = FullNozzleGeometry.fromMOC(params, net).generate(50, 0);
// pass 0 for numDivergentPoints to keep the MOC wall as-is
```

Pass a `FullNozzleGeometry` to any of the full-nozzle DXF export methods:

```
DXFExporter exporter = new DXFExporter().setScaleFactor(1000);

// Profile only (WALL + AXIS + THROAT layers)
exporter.exportFullNozzleProfile(geom, Path.of("nozzle.dxf"));

// Closed revolution sketch (adds INLET and OUTLET face lines)
exporter.exportFullNozzleRevolutionProfile(geom, Path.of("profile.dxf"));
```

---

## Unit Conversion

`Units` provides named static conversion methods for use at system input/output
boundaries. All internal calculations use SI.

```
// Length
double mm  = Units.metersToMillimeters(0.05);    // → 50.0
double ft  = Units.metersToFeet(0.3048);         // → 1.0
double m   = Units.inchesToMeters(2.0);          // → 0.0508

// Pressure
double psi = Units.pascalsToPsi(101_325.0);      // ≈ 14.696
double pa  = Units.psiToPascals(100.0);          // ≈ 689 476

// Temperature
double f   = Units.kelvinToFahrenheit(288.15);   // ≈ 59.0
double k   = Units.fahrenheitToKelvin(59.0);     // → 288.15

// Specific heat (propulsion-specific unit)
double btu = Units.jPerKgKToBtuPerLbmR(4186.8);  // ≈ 1.0
double cp  = Units.btuPerLbmRToJPerKgK(0.24);   // ≈ 1005

// Specific impulse
double s   = Units.lbfSPerLbmToSeconds(300.0);  // → 300.0  (already dimensionless)
```

---

## Thermal Analysis

```
// Boundary layer starting from the throat (divergent section only)
BoundaryLayerCorrection bl  = new BoundaryLayerCorrection(params, net).calculate();

// Boundary layer starting from the injector face (more accurate throat δ*)
BoundaryLayerCorrection blFull = new BoundaryLayerCorrection(params, net)
        .calculateFromInjectorFace(fullGeom, net.getFlowPoints());

System.out.printf("Throat δ*           : %.4f mm%n",
        blFull.getThroatDisplacementThickness() * 1000);
System.out.printf("BL Cd correction    : %.4f%n",
        blFull.getBoundaryLayerCdCorrection());
System.out.printf("Combined Cd (geo×BL): %.4f%n",
        blFull.getCombinedCd());

// Divergent-only heat transfer (fast; peak is always in the divergent section)
NozzleContour contour = NozzleContour.fromMOCWallPoints(params, net.getWallPoints());
HeatTransferModel htr = new HeatTransferModel(params, contour)
        .setWallProperties(20.0, 0.003)
        .setCoolantProperties(300.0, 5000.0)
        .calculate(net.getAllPoints());

System.out.printf("Max wall temp  : %.0f K%n",     htr.getMaxWallTemperature());
System.out.printf("Max heat flux  : %.2e W/m²%n",  htr.getMaxHeatFlux());
System.out.printf("Peak at x      : %+.3f mm%n",   htr.getPeakFluxX() * 1000);

// Full-profile heat transfer — covers convergent section (x < 0) as well,
// so the peak can be identified even when it sits upstream of the throat.
// The Bartz curvature correction uses parametric r_cd / r_cu in the arc zone.
FullNozzleGeometry fullGeom = new FullNozzleGeometry(params).generate(50, 100);
HeatTransferModel htrFull = new HeatTransferModel(params, contour)
        .setWallProperties(20.0, 0.003)
        .setCoolantProperties(300.0, 5000.0)
        .calculateFullProfile(fullGeom, net.getAllPoints());

HeatTransferModel.WallThermalPoint peak = htrFull.getPeakFluxPoint();
System.out.printf("Peak location  : %+.3f mm (+ = downstream of throat)%n",
        peak.x() * 1000);
System.out.printf("Peak heat flux : %.2e W/m²%n", peak.totalHeatFlux());
System.out.printf("Peak wall temp : %.0f K%n",    peak.wallTemperature());

// Regenerative coolant channel
CoolantChannel channel = new CoolantChannel(contour)
        .setChannelGeometry(120, 0.003, 0.005, 0.001)
        .setWallConductivity(20.0)
        .setCoolant(CoolantChannel.CoolantProperties.RP1, 2.0, 300.0, 8.5e6)
        .calculate();

channel.calculate(htr.getWallThermalProfile());  // thermal analysis
System.out.printf("Coolant temp rise    : %.1f K%n", channel.getCoolantTemperatureRise());
System.out.printf("Min boiling margin   : %.1f K%n", channel.getMinBoilingMargin());

// Thermal stress and fatigue
ThermalStressAnalysis stress = new ThermalStressAnalysis(
        params, htr.getWallThermalProfile(),
        ThermalStressAnalysis.Material.COPPER_ALLOY_CuCrZr, 0.003, 320.0).calculate();

System.out.printf("Peak von Mises stress: %.1f MPa%n",
        stress.getMaxVonMisesStress() / 1e6);
System.out.printf("Min safety factor    : %.2f%n",   stress.getMinSafetyFactor());
System.out.printf("Min fatigue life     : %.0f cycles%n", stress.getMinFatigueCycles());
```

---

## CAD and CFD Export

All exporters accept either a divergent-only `NozzleContour` or a geometry-complete
`FullNozzleGeometry`.  When `FullNozzleGeometry` is passed the exported file covers
the full internal wall from injector face to exit.

### DXF (2D profile)
```
FullNozzleGeometry fullGeom = new FullNozzleGeometry(params).generate(50, 100);
DXFExporter dxf = new DXFExporter().setScaleFactor(1000);

// Divergent section only
dxf.exportContour(contour, Path.of("nozzle.dxf"));

// Full nozzle (injector face → exit)
dxf.exportFullNozzleProfile(fullGeom, Path.of("full_nozzle.dxf"));
dxf.exportFullNozzleRevolutionProfile(fullGeom, Path.of("full_nozzle_rev.dxf"));
```

### CSV (wall contour)
```
CSVExporter csv = new CSVExporter();

csv.exportContour(contour,   Path.of("contour_div.csv")); // x, y, angle_deg
csv.exportContour(fullGeom,  Path.of("contour_full.csv")); // + section column
```

### STEP (3D revolved solid)
```
STEPExporter step = new STEPExporter().setScaleFactor(1000).setAuthor("Me");

step.exportRevolvedSolid(contour,  Path.of("nozzle.step"));      // divergent only
step.exportRevolvedSolid(fullGeom, Path.of("nozzle_full.step")); // full nozzle
step.exportProfileCurve(contour,   Path.of("profile.step"));     // profile curve
```

### STL (3D revolved mesh)
```
STLExporter stl = new STLExporter().setCircumferentialSegments(72).setBinaryFormat(true);

stl.exportMesh(contour,  Path.of("nozzle.stl"));      // divergent only
stl.exportMesh(fullGeom, Path.of("nozzle_full.stl")); // full nozzle
```

### CFD mesh files (2D axisymmetric)

`setFirstLayerThickness(y₁)` enables y⁺-controlled wall-normal grading.
When set, the first cell height is fixed at `y₁` and the expansion ratio is
derived automatically per format (`g = H/y₁` for OpenFOAM blockMesh,
`r = (H/y₁)^(1/N)` for Gmsh, where H is the exit radius or annular gap).
Omit it to use a fixed `expansionRatio` instead.

```
CFDMeshExporter cfd = new CFDMeshExporter()
        .setAxialCells(200).setRadialCells(80).setFirstLayerThickness(1e-5);

// Divergent only — all four formats
cfd.export(contour, Path.of("blockMeshDict"), CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);
cfd.export(contour, Path.of("nozzle.geo"),    CFDMeshExporter.Format.GMSH_GEO);
cfd.export(contour, Path.of("nozzle.xyz"),    CFDMeshExporter.Format.PLOT3D);
cfd.export(contour, Path.of("nozzle.cgns"),   CFDMeshExporter.Format.CGNS);

// Geometry-complete (convergent + divergent — captures the transonic throat)
cfd.export(fullGeom, Path.of("blockMeshDict_full"), CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);
cfd.export(fullGeom, Path.of("nozzle_full.geo"),    CFDMeshExporter.Format.GMSH_GEO);
cfd.export(fullGeom, Path.of("nozzle_full.xyz"),    CFDMeshExporter.Format.PLOT3D);
```

### Complete OpenFOAM case (rhoCentralFoam)
```
// Divergent only
new OpenFOAMExporter()
        .setAxialCells(300).setRadialCells(100)
        .setTurbulenceEnabled(true).setFirstLayerThickness(1e-5)
        .exportCase(params, contour, Path.of("nozzle_case"));

// Geometry-complete (inlet is at the injector face)
new OpenFOAMExporter()
        .setAxialCells(300).setRadialCells(100).setTurbulenceEnabled(true)
        .exportCase(params, fullGeom, Path.of("nozzle_case_full"));

// Aerospike — annular domain (spike inner wall + cowl outer wall)
// Patches: inlet, outlet, spike (wall), cowl (wall), wedge0, wedge1
new OpenFOAMExporter()
        .setAxialCells(150).setRadialCells(60)
        .exportAerospikeCase(aerospikeNozzle, Path.of("aerospike_case"));
```

The exported case is ready to run with:
```bash
blockMesh && rhoCentralFoam
```

### Full 3-D revolved mesh
```
RevolvedMeshExporter rev = new RevolvedMeshExporter()
        .setAxialCells(100).setRadialCells(40).setAzimuthalCells(16);

// Divergent only
rev.export(contour,  Path.of("blockMeshDict_3d"), RevolvedMeshExporter.Format.OPENFOAM_BLOCKMESH);

// Geometry-complete
rev.export(fullGeom, Path.of("blockMeshDict_3d_full"), RevolvedMeshExporter.Format.OPENFOAM_BLOCKMESH);
rev.export(fullGeom, Path.of("nozzle_3d_full.geo"),    RevolvedMeshExporter.Format.GMSH_GEO);
rev.export(fullGeom, Path.of("nozzle_3d_full.xyz"),    RevolvedMeshExporter.Format.PLOT3D);
```

---

## Persistent Workflows (Serialization)

```
// Stage 1 — save design intent
DesignDocument doc = DesignDocument.parametersOnly(params);
NozzleSerializer.save(doc, Path.of("nozzle.json"));

// Stage 2 — load, solve, save
DesignDocument loaded = NozzleSerializer.load(Path.of("nozzle.json"));
CharacteristicNet net = new CharacteristicNet(loaded.parameters()).generate();
NozzleSerializer.save(loaded.withNet(net), Path.of("nozzle.json"));

// Stage 3 — load and analyze
DesignDocument solved = NozzleSerializer.load(Path.of("nozzle.json"));
// ... run performance, thermal, export ...
```

---

## NASA SP-8120 Validation

```
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

| Package                   | Contents                                                                                                                                          |
|---------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| `com.nozzle.core`         | `NozzleDesignParameters`, `GasProperties`, `PerformanceCalculator`, `FlowSeparationPredictor`, `Units`                                            |
| `com.nozzle.moc`          | `CharacteristicNet`, `RaoNozzle`, `AerospikeNozzle`, `DualBellNozzle`, `CharacteristicPoint`                                                      |
| `com.nozzle.geometry`     | `NozzleContour` (five contour families), `ConvergentSection`, `FullNozzleGeometry`, `Point2D`                                                     |
| `com.nozzle.chemistry`    | `ChemistryModel`, `OFSweep`, `GibbsMinimizer`, `NasaSpeciesDatabase`                                                                              |
| `com.nozzle.thermal`      | `HeatTransferModel`, `BoundaryLayerCorrection`, `CoolantChannel`, `ThermalStressAnalysis`, `AblativeNozzleModel`, `RadiationCooledExtension`      |
| `com.nozzle.export`       | `CFDMeshExporter`, `OpenFOAMExporter`, `CSVExporter`, `DXFExporter`, `STEPExporter`, `STLExporter`, `RevolvedMeshExporter`                        |
| `com.nozzle.optimization` | `AltitudeAdaptiveOptimizer`, `MonteCarloUncertainty`                                                                                              |
| `com.nozzle.validation`   | `NASASP8120Validator`                                                                                                                             |
| `com.nozzle.io`           | `NozzleSerializer`, `DesignDocument`                                                                                                              |

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
