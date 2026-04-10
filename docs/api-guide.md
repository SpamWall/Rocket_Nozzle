# API Guide

Package-by-package reference with usage examples for experienced Java developers.

---

## com.nozzle.core

### NozzleDesignParameters

The root input to every calculation. It is a Java record and must be constructed
through its builder.

```
NozzleDesignParameters params = NozzleDesignParameters.builder()
        .throatRadius(0.05)                         // metres
        .exitMach(3.5)
        .chamberPressure(7e6)                       // Pa
        .chamberTemperature(3500)                   // K
        .ambientPressure(101_325)                   // Pa (sea level)
        .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
        .numberOfCharLines(30)                      // MOC grid density
        .wallAngleInitialDegrees(30)                // throat-side wall angle
        .lengthFraction(0.8)                        // 80% of ideal nozzle length
        .axisymmetric(true)
        .throatCurvatureRatio(0.382)                // r_cd / r_t (optional, default 0.382)
        .upstreamCurvatureRatio(1.5)               // r_cu / r_t (optional, default 1.5)
        .convergentHalfAngleDegrees(30)            // convergent cone half-angle (optional, default 30°)
        .contractionRatio(4.0)                     // Ac/At (optional, default 4.0)
        .build();
```

**Key builder defaults:** `throatRadius=0.05`, `exitMach=3.0`, `Pc=7 MPa`,
`Tc=3500 K`, `Pa=101325 Pa`, `numberOfCharLines=50`, `wallAngleDeg=30`,
`lengthFraction=0.8`, `axisymmetric=true`, `throatCurvatureRatio=0.382`,
`upstreamCurvatureRatio=1.5`, `convergentHalfAngleDeg=30`, `contractionRatio=4.0`.

**`throatCurvatureRatio(double ratio)`** — Downstream throat arc radius as a
multiple of r_t (`r_cd = ratio × r_t`). Valid range `(0, 2.0]`.

**`upstreamCurvatureRatio(double ratio)`** — Upstream throat arc radius as a
multiple of r_t (`r_cu = ratio × r_t`). Controls convergent-side arc shape and
the curvature of the sonic line. Valid range `(0, 3.0]`.

**`convergentHalfAngleDegrees(double deg)`** —
Half-angle of the convergent cone that connects the upstream arc to the
combustion chamber. Valid range `[5°, 60°]`.

**`contractionRatio(double ratio)`** — Chamber-to-throat area ratio (Ac/At).
Determines chamber radius: `r_c = r_t × √(contractionRatio)`. Valid range
`[1.5, 20]`.

**Derived quantities also added:**

| Method                         | Returns                                  |
|--------------------------------|------------------------------------------|
| `chamberRadius()`              | r_t · √(contractionRatio)                |
| `convergentHalfAngleDegrees()` | convergentHalfAngle converted to degrees |

---

### ConvergentSection

Generates the full convergent-section wall contour (chamber face → throat) and
computes the sonic-line discharge-coefficient correction.

```
ConvergentSection cs = new ConvergentSection(params).generate(60);

// Geometry
double chamberR  = cs.getChamberRadius();      // r_c in metres
double length    = cs.getLength();             // axial extent (positive) in metres
double arcEndX   = cs.getArcEndX();            // x of arc/cone junction (negative)
List<Point2D> pts = cs.getContourPoints();     // ordered chamber → throat (x < 0)

// Sonic-line Cd correction
double cdGeo = cs.getSonicLineCdCorrection();  // typically 0.995–0.9999

// Build geometry-complete contour (chamber → exit)
NozzleContour divergent = new NozzleContour(ContourType.RAO_BELL, params).generate(100);
NozzleContour full      = divergent.withConvergentSection(cs);

// Pass full contour to any exporter for complete geometry output
new DXFExporter().exportRevolutionProfile(full, path);
new STLExporter().exportMesh(full, path);

// BL integration starts at the chamber face automatically
BoundaryLayerCorrection bl = new BoundaryLayerCorrection(params, full).calculate(null);

// Incorporate Cd_geo into PerformanceCalculator
PerformanceCalculator pc = new PerformanceCalculator(
        params, net, full, bl, null, cs).calculate();
System.out.println(pc.getSonicLineCdCorrection());  // Cd_geo
System.out.println(pc.getMassFlowRate());           // scaled by Cd_geo
System.out.println(pc.getThrust());                 // scaled by Cd_geo
System.out.println(pc.getSpecificImpulse());        // unchanged
```

**Key methods:**

| Method                       | Description                                               |
|------------------------------|-----------------------------------------------------------|
| `generate(int n)`            | Populate wall points; returns `this`                      |
| `getContourPoints()`         | Ordered wall points from chamber face to throat (x < 0)   |
| `getChamberRadius()`         | r_c = r_t · √(contractionRatio) in metres                 |
| `getLength()`                | Axial extent of convergent section (positive value)       |
| `getArcEndX()`               | x-coordinate of arc/cone junction                         |
| `getArcEndY()`               | Radius at arc/cone junction                               |
| `getSonicLineCdCorrection()` | Cd_geo ∈ [0.98, 1.0] from harmonic-mean curvature formula |

**Derived quantities (computed lazily on first access):**

| Method                     | Returns                                        |
|----------------------------|------------------------------------------------|
| `exitAreaRatio()`          | A/A* from isentropic area-velocity relation    |
| `exitRadius()`             | r_throat · √(exitAreaRatio)                    |
| `throatArea()`             | π·r_throat² (axisymmetric) or 2·r_throat (2-D) |
| `idealExitPressure()`      | Pc · (isentropic pressure ratio at Me)         |
| `exitTemperature()`        | Tc · (isentropic temperature ratio at Me)      |
| `exitVelocity()`           | Me · √(γ·R·T_exit)                             |
| `characteristicVelocity()` | c* = √(γ·R·Tc)/γ · (2/(γ+1))^((γ+1)/(2(γ-1)))  |
| `idealThrustCoefficient()` | Cf from the isentropic thrust equation         |
| `idealSpecificImpulse()`   | c* · Cf / g₀                                   |
| `maxPrandtlMeyerAngle()`   | Prandtl-Meyer ν at M = ∞                       |

---

### GasProperties

Immutable record holding frozen thermodynamic properties for a specific
combustion product mixture.

**Built-in instances:**

```
GasProperties.AIR                  // air, γ=1.4, M=28.97
GasProperties.HYDROGEN             // H₂, γ=1.4, M=2.016
GasProperties.NITROGEN             // N₂, γ=1.4, M=28.014
GasProperties.LOX_RP1_PRODUCTS     // kerosene/LOX products
GasProperties.LOX_LH2_PRODUCTS     // hydrogen/LOX products
GasProperties.LOX_CH4_PRODUCTS     // methane/LOX products
GasProperties.N2O_ETHANOL_PRODUCTS // nitrous oxide/ethanol products
GasProperties.N2O_PROPANE_PRODUCTS // nitrous oxide/propane products
```

**Custom instance:**

```
GasProperties custom = new GasProperties(
        1.25,    // gamma
        22.0,    // molecular weight, kg/kmol
        /* gasConstant computed: R_universal / MW */
        1.8e-5,  // viscosity reference, Pa·s
        273.0,   // viscosity reference temperature, K
        110.4    // Sutherland constant, K
);
```

**Key methods:**

```
gas.prandtlMeyerFunction(2.5)   // ν at M=2.5
gas.machFromPrandtlMeyer(0.87)  // M from ν
gas.areaRatio(2.5)              // A/A* at M=2.5
gas.machFromAreaRatio(6.79)     // M from A/A* (supersonic branch)
gas.isentropicPressureRatio(2.5)
gas.isentropicTemperatureRatio(2.5)
gas.calculateViscosity(2000)    // Sutherland law, Pa·s
```

---

### PerformanceCalculator

Computes Cf, Isp, and individual loss mechanisms. All arguments except
`parameters` are optional (pass `null` to skip that loss mechanism).

```
// Full calculation with all loss mechanisms
BoundaryLayerCorrection bl   = new BoundaryLayerCorrection(params, contour).calculate();
HeatTransferModel       htr  = new HeatTransferModel(params, contour).calculate();
ChemistryModel          chem = ChemistryModel.equilibrium(params.gasProperties());
chem.setLoxRp1Composition(2.4);
chem.calculateEquilibrium(params.chamberTemperature(), params.chamberPressure());

PerformanceCalculator pc = new PerformanceCalculator(params, net, contour, bl, chem)
        .calculate();

System.out.println(pc.getSpecificImpulse());         // actual Isp, seconds
System.out.println(pc.getActualThrustCoefficient()); // Cf after losses
System.out.println(pc.getEfficiency());              // Cf_actual / Cf_ideal
System.out.println(pc.getDivergenceLoss());          // fractional loss
System.out.println(pc.getBoundaryLayerLoss());
System.out.println(pc.getChemicalLoss());

// Summary record
PerformanceCalculator.PerformanceSummary summary = pc.getSummary();

// Simple mode (ideal performance only, no secondary inputs)
PerformanceCalculator simple = PerformanceCalculator.simple(params).calculate();
```

---

### FlowSeparationPredictor

```
FlowSeparationPredictor fsp = new FlowSeparationPredictor(params);

// Three available criteria
FlowSeparationPredictor.SeparationResult r =
        fsp.predict(FlowSeparationPredictor.Criterion.SUMMERFIELD);
// Also: SCHILLING, ROMINE

System.out.println(r.mode());            // NO_SEPARATION, FSS, or RSS
System.out.println(r.axialFraction());   // x/L where separation occurs
System.out.println(r.estimatedSideLoadN()); // estimated side load, Newtons
```

---

### ShockExpansionModel

```
ShockExpansionModel model = new ShockExpansionModel(params);

// Evaluate at a specific altitude (converts altitude to ISA pressure internally)
ShockExpansionModel.OffDesignResult result = model.compute(50_000); // Pa

System.out.println(result.regime());         // FlowRegime enum
System.out.println(result.thrustCoefficient());
System.out.println(result.specificImpulse());
System.out.println(result.plumeHalfAngleDeg());
System.out.println(result.postWaveMach());
```

---

## com.nozzle.moc

### CharacteristicNet

The primary MOC solver. Call `generate()` once; results are immutable thereafter.

```
CharacteristicNet net = new CharacteristicNet(params).generate();

// Full grid: outer list = rows (data lines), inner = points in each row
List<List<CharacteristicPoint>> grid = net.getNetPoints();

// Wall-point sequence (ordered throat → exit)
List<CharacteristicPoint> wall = net.getWallPoints();

// Derived quantities
double exitAR = net.calculateExitAreaRatio();
int    total  = net.getTotalPointCount();

// Validation against design intent
List<String> issues = net.validate(); // empty if consistent
```

**CharacteristicPoint fields:**

```
CharacteristicPoint p = wall.get(10);
p.x()           // axial position, m
p.y()           // radial position, m
p.mach()        // local Mach number
p.theta()       // flow angle, radians
p.thetaDeg()    // flow angle, degrees (convenience)
p.nu()          // Prandtl-Meyer function value
p.mu()          // Mach angle, radians
p.pressure()    // local static pressure, Pa
p.temperature() // local static temperature, K
p.density()     // local density, kg/m³
p.velocity()    // local velocity magnitude, m/s
p.type()        // INITIAL, INTERIOR, WALL, CENTERLINE, EXIT
```

**Tuning the solver:**

```
// Custom convergence tolerance and iteration limit
CharacteristicNet net = new CharacteristicNet(params, 1e-10, 200).generate();
```

`numberOfCharLines` in the design parameters controls the grid density. Values
between 20 and 100 are typical; higher values improve accuracy at the cost of
computation time (O(n²) points).

---

### RaoNozzle

Rao optimum bell nozzle using a cubic Bézier approximation. The internal angles
(inflection and exit) are computed from Rao's empirical correlations based on
exit area ratio and length fraction.

```
RaoNozzle rao = new RaoNozzle(params).generate();
// or with explicit controls:
RaoNozzle rao = new RaoNozzle(params, 0.8, 200).generate(); // (params, lf, numPoints)

List<Point2D> contour = rao.getContourPoints();

// Performance
double cf         = rao.calculateThrustCoefficient();
double efficiency = rao.calculateEfficiency();

// Compare against MOC solution
CharacteristicNet net = new CharacteristicNet(params).generate();
RaoNozzle.NozzleComparison cmp = rao.compareTo(net);
System.out.printf("Max radial deviation: %.4f m%n",  cmp.maxRadialDifference());
System.out.printf("Avg radial deviation: %.4f m%n",  cmp.averageRadialDifference());
System.out.printf("Cf difference:        %.4f%n",    cmp.thrustCoefficientDifference());
```

---

### DualBellNozzle

Altitude-compensating dual-bell nozzle with kink-locked flow separation.

```
// Default transition AR = √(exitAreaRatio)
DualBellNozzle db = new DualBellNozzle(params).generate();

// Custom transition area ratio (kink at AR=4)
DualBellNozzle db = new DualBellNozzle(params, 4.0).generate();

// Full constructor
DualBellNozzle db = new DualBellNozzle(
        params,
        4.0,               // transitionAreaRatio — kink at A/A*=4
        0.8,               // baseLengthFraction
        0.8,               // extensionLengthFraction
        Math.toRadians(3), // kinkAngle — inward re-inflection
        200                // numContourPoints
).generate();

// Results
DualBellNozzle.PerformanceSummary s = db.getPerformanceSummary();
s.seaLevelIsp()          // base-bell Isp at params.ambientPressure(), s
s.highAltitudeIsp()      // full-nozzle Isp at pa=0 (vacuum), s
s.ispGain()              // high-alt minus sea-level, s
s.transitionPressure()   // Summerfield switch pressure, Pa
s.seaLevelCf()
s.highAltitudeCf()

// Geometry
db.getBaseLength()        // m — length to kink
db.getTotalLength()       // m — total length
db.getTransitionRadius()  // m — wall radius at kink
db.getTransitionMach()    // Mach at kink
db.getKinkIndex()         // index in getContourPoints() of kink point
db.getInflectionAngle()   // rad
db.getBaseExitAngle()     // rad — θ_E1
db.getExtensionExitAngle()// rad — θ_E2
```

---

### AerospikeNozzle

Plug (Aerospike) nozzle with natural altitude compensation. The spike contour is
the inner streamline of the centred Prandtl-Meyer expansion fan at the cowl lip.

```
// Defaults: spikeRadiusRatio=0.60, truncationFraction=0.80, numPoints=100
AerospikeNozzle aero = new AerospikeNozzle(params).generate();

// Explicit
AerospikeNozzle aero = new AerospikeNozzle(params, 0.4, 0.85, 150).generate();

// Geometry
aero.getFullSpikeContour()      // List<Point2D> — untruncated spike
aero.getTruncatedSpikeContour() // List<Point2D> — at truncationFraction
aero.getFullSpikeLength()       // m
aero.getTruncatedLength()       // m
aero.getTruncatedBaseRadius()   // m — base radius at truncation plane
aero.getSpikeRadiusRatio()      // ri/rt
aero.getAnnularThroatArea()     // m²
aero.getAnnularExitArea()       // m²

// Performance
double cf  = aero.calculateThrustCoefficient(101_325); // at sea level
double isp = aero.calculateIsp(0);                     // at vacuum

// Altitude sweep
double[] ambientPressures = {101325, 70000, 40000, 10000, 1000, 0};
AltitudePerformance ap = aero.calculateAltitudePerformance(ambientPressures);
ap.aerospikeIsp()               // double[] — Isp at each altitude
ap.aerospikeCf()                // double[]
ap.bellNozzleCf()               // double[] — equivalent bell reference
ap.averageAltitudeAdvantage()   // mean Cf advantage vs. bell
```

---

## com.nozzle.geometry

### NozzleContour

Provides a smooth, queryable wall contour from any of five families.

```
// From MOC (recommended — captures the actual computed wall)
NozzleContour contour = NozzleContour.fromMOCWallPoints(params, net.getWallPoints());
contour.generate(200);    // 200 output points

// Rao bell approximation
NozzleContour rao = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
rao.generate(200);

// Conical
NozzleContour cone = new NozzleContour(NozzleContour.ContourType.CONICAL, params);
cone.generate(200);

// Truncated ideal contour (TIC)
NozzleContour tic = new NozzleContour(NozzleContour.ContourType.TRUNCATED_IDEAL, params);
tic.generate(200);

// Custom spline (caller-supplied control points)
NozzleContour custom = new NozzleContour(NozzleContour.ContourType.CUSTOM_SPLINE, params);
custom.addControlPoint(0.00, 0.050);
custom.addControlPoint(0.05, 0.055);
custom.addControlPoint(0.12, 0.085);
custom.addControlPoint(0.20, 0.105);
custom.generate(200);

// From an already-computed List<Point2D> (RaoNozzle, DualBellNozzle)
NozzleContour fromNozzle = NozzleContour.fromPoints(params, raoNozzle.getContourPoints());

// Querying
double r = contour.getRadiusAt(0.05);     // wall radius at x=50mm
double α = contour.getAngleAt(0.05);      // wall angle, radians
double L = contour.getLength();           // total axial length, m
double A = contour.calculateSurfaceArea(); // wetted surface area, m²
List<Point2D> pts = contour.getContourPoints();
```

---

## com.nozzle.chemistry

### ChemistryModel

Orchestrates frozen or equilibrium chemistry. The same instance should not be
used concurrently; `OFSweep.computeAt()` creates a fresh instance per call.

```
// Frozen flow
ChemistryModel frozen = ChemistryModel.frozen(GasProperties.LOX_RP1_PRODUCTS);
frozen.setLoxRp1Composition(2.4);          // O/F ratio
double gamma = frozen.calculateGamma(3500);
double mw    = frozen.calculateMolecularWeight();
double cp    = frozen.calculateCp(3500);

// Equilibrium flow
ChemistryModel eq = ChemistryModel.equilibrium(GasProperties.LOX_RP1_PRODUCTS);
eq.setLoxRp1Composition(2.4);
eq.calculateEquilibrium(3500, 7e6);         // (Tc, Pc)

Map<String, Double> fractions = eq.getSpeciesMassFractions();
double gammaEq = eq.calculateEquilibriumGamma(3500, 7e6);

// Isp comparison (frozen vs equilibrium)
ChemistryModel.IspComparison cmp = eq.compareIsp(3500, 7e6, 3.5, 101_325);
System.out.printf("Frozen Isp:      %.1f s%n",   cmp.frozenIsp());
System.out.printf("Equilibrium Isp: %.1f s%n",   cmp.equilibriumIsp());
System.out.printf("Gain:            %.1f s (%.2f %%)%n", cmp.delta(), cmp.deltaPercent());
```

**Propellant composition setters:**

| Method                       | Argument       | Propellant               |
|------------------------------|----------------|--------------------------|
| `setLoxRp1Composition(of)`   | O/F mass ratio | LOX/RP-1 (kerosene)      |
| `setLoxLh2Composition()`     | —              | LOX/LH₂ (stoichiometric) |
| `setLoxCh4Composition()`     | —              | LOX/CH₄ (stoichiometric) |
| `setN2oEthanolComposition()` | —              | N₂O/Ethanol              |
| `setN2oPropaneComposition()` | —              | N₂O/Propane              |

---

### OFSweep

Sweeps or optimizes over the oxidizer-to-fuel ratio. Two modes: adiabatic (Tc
solved from energy balance) and fixed-Tc.

```
// Adiabatic mode (typical)
OFSweep sweep = OFSweep.adiabatic(
        OFSweep.Propellant.LOX_RP1,
        7e6,      // Pc, Pa
        3.5,      // exit Mach
        101_325   // Pa ambient
);

// Fixed-Tc mode
OFSweep fixed = new OFSweep(OFSweep.Propellant.LOX_RP1, 3400, 7e6, 3.5, 101_325);

// Sweep
List<OFSweep.OFPoint> curve = sweep.sweep(1.0, 4.0, 40);
for (OFSweep.OFPoint p : curve) {
    System.out.printf("OF=%.2f Tc=%.0f K Isp=%.1f s c*=%.1f m/s%n",
            p.of(), p.chamberTemperature(), p.isp(), p.cStar());
}

// Optimum Isp
OFSweep.OFPoint best = sweep.optimumIsp(1.0, 4.0);

// Optimum c* (different from optimum Isp due to Cf(γ) dependence)
OFSweep.OFPoint bestCstar = sweep.optimumCstar(1.0, 4.0);

// Single-point evaluation
OFSweep.OFPoint pt = sweep.computeAt(2.4);
```

**Available propellants:**
`LOX_RP1`, `LOX_CH4`, `LOX_LH2`, `N2O_ETHANOL`, `N2O_PROPANE`

---

## com.nozzle.thermal

### HeatTransferModel

```
HeatTransferModel htr = new HeatTransferModel(params, contour)
        .setWallProperties(390, 0.002)    // conductivity W/(m·K), thickness m
        .setCoolantProperties(400, 5000)  // coolant T (K), film h W/(m²·K)
        .setEmissivity(0.6)
        .calculate();

double peakWallT  = htr.getPeakWallTemperature();   // K
double peakFlux   = htr.getPeakHeatFlux();           // W/m²
double totalPower = htr.getTotalHeatLoad();          // W

List<HeatTransferModel.WallThermalPoint> profile = htr.getWallProfile();
for (var p : profile) {
    // p.x(), p.wallTemperature(), p.convectiveFlux(), p.radiativeFlux()
}
```

### BoundaryLayerCorrection

```
BoundaryLayerCorrection bl = new BoundaryLayerCorrection(params, contour)
        .setTransitionReynolds(5e5)    // default; set lower to force early transition
        .setForceTurbulent(false)
        .calculate();

// Used by PerformanceCalculator and HeatTransferModel
```

### ThermalStressAnalysis

```
ThermalStressAnalysis stress = new ThermalStressAnalysis(
        params, htr, ThermalStressAnalysis.Material.CU_CR_ZR).calculate();

stress.getPeakVonMisesStress()    // Pa
stress.getPeakThermalStress()     // Pa
stress.getPeakPressureStress()    // Pa
stress.getSafetyFactor()          // vs. yield strength
stress.getFatigueLife()           // cycles (Basquin / Coffin-Manson)
stress.getMaxAllowableTemperature() // material limit, K
```

**Available materials:** `CU_CR_ZR`, `INCONEL_718`, `STAINLESS_304`, `TITANIUM_6AL4V`

### AblativeNozzleModel

```
AblativeNozzleModel ablative = new AblativeNozzleModel(
        params, htr, AblativeNozzleModel.Material.CARBON_PHENOLIC).calculate();

ablative.getCharRate(2500)          // m/s char recession rate at T=2500K
ablative.getPeakRecessionRate()     // m/s
ablative.getMechanicalErosionRate() // m/s
ablative.getTotalAblatedMass(120)   // kg after 120 s burn
```

**Available materials:**
`CARBON_PHENOLIC`, `SILICA_PHENOLIC`, `EPDM`, `GRAPHITE`, `CARBON_CARBON`

### RadiationCooledExtension

```
RadiationCooledExtension rad = new RadiationCooledExtension(
        params, htr, RadiationCooledExtension.Material.RHENIUM).calculate();

rad.getEquilibriumWallTemperature()  // K
rad.isOverTemperature()              // exceeds material limit?
rad.getMaterialLimit()               // material temperature limit, K
```

---

## com.nozzle.export

All exporters follow a fluent builder pattern — chain setters, then call the
export method.

### CSVExporter

```
CSVExporter csv = new CSVExporter();

csv.exportContour(contour, Path.of("wall.csv"));
csv.exportCharacteristicNet(net, Path.of("net.csv"));
csv.exportWallContour(net, Path.of("wall_pts.csv"));
csv.exportThermalProfile(htr, Path.of("thermal.csv"));
csv.exportBoundaryLayerProfile(bl, Path.of("bl.csv"));
csv.exportStressProfile(stress, Path.of("stress.csv"));
csv.exportDesignParameters(params, Path.of("params.csv"));

// Aerospike-specific
csv.exportSpikeContour(aero, Path.of("spike.csv"));
csv.exportAltitudePerformance(altPerf, Path.of("altitude.csv"));
csv.exportAerospikeReport(aero, altitudePressures, Path.of("report_dir/"));
```

### DXFExporter

```
new DXFExporter()
        .setScaleFactor(1000)            // metres → mm
        .exportContour(contour, Path.of("nozzle.dxf"));
        // or:
        .exportRevolutionProfile(contour, Path.of("profile.dxf"));
        // or:
        .exportAerospikeContour(aero, Path.of("spike.dxf"));
// DXF layers: WALL, AXIS (bell); SPIKE, COWL, AXIS (Aerospike)
```

### STEPExporter

```
new STEPExporter()
        .setScaleFactor(1000)
        .setAuthor("YOUR NAME HERE")
        .setOrganization("YOUR ORG HERE")
        .exportRevolvedSolid(contour, Path.of("nozzle.step"));
        // or:
        .exportAerospikeRevolvedSolid(aero, Path.of("spike.step"));
```

### STLExporter

```
new STLExporter()
        .setCircumferentialSegments(72)  // facets around the axis
        .setScaleFactor(1000)
        .setBinaryFormat(true)           // false for ASCII
        .exportMesh(contour, Path.of("nozzle.stl"));
        // or:
        .exportAerospikeMesh(aero, Path.of("spike.stl"));

int triangles = new STLExporter().estimateTriangleCount(contour.getContourPoints().size());
```

### CFDMeshExporter

Exports mesh input files — not a full solver case. For a complete OpenFOAM case
including boundary conditions, solver settings, and field initialization use
`OpenFOAMExporter` instead.

```
CFDMeshExporter cfd = new CFDMeshExporter()
        .setAxialCells(200)
        .setRadialCells(80)
        .setFirstLayerThickness(1e-5);   // y⁺-controlled grading; overrides expansionRatio

// Bell / dual-bell / Rao nozzle — generic NozzleContour path
cfd.export(contour,    Path.of("blockMeshDict"), CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);
cfd.export(contour,    Path.of("nozzle.geo"),    CFDMeshExporter.Format.GMSH_GEO);
cfd.export(contour,    Path.of("nozzle.xyz"),    CFDMeshExporter.Format.PLOT3D);
cfd.export(contour,    Path.of("nozzle.cgns"),   CFDMeshExporter.Format.CGNS);

// Convenience overloads — build NozzleContour internally
cfd.export(new RaoNozzle(params).generate(),      Path.of("bmd"), CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);
cfd.export(new DualBellNozzle(params).generate(), Path.of("bmd"), CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);

// Aerospike — annular domain (spike inner wall, cowl outer wall)
cfd.exportAerospike(aero, Path.of("aero_bmd"),   CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);
cfd.exportAerospike(aero, Path.of("aero.geo"),   CFDMeshExporter.Format.GMSH_GEO);
cfd.exportAerospike(aero, Path.of("aero.xyz"),   CFDMeshExporter.Format.PLOT3D);
```

**y⁺ grading:** When `setFirstLayerThickness(y₁)` is set, the expansion ratio is
derived per format:
- OpenFOAM blockMesh: `g = H / y₁` (strong-grading approximation)
- Gmsh progression: `r = (H / y₁)^(1/N)` where N = radialCells
- Plot3D power-law exponent: `p = ln(H / y₁) / ln(N)`

H is the exit radius (bell nozzles) or the annular gap (Aerospike).

### OpenFOAMExporter

Writes a complete, immediately runnable `rhoCentralFoam` case directory:

```
<caseDir>/
  system/  blockMeshDict  controlDict  fvSchemes  fvSolution
  constant/  thermophysicalProperties  turbulenceProperties
  0/  p  T  U  [k  omega]
```

```
new OpenFOAMExporter()
        .setAxialCells(300)
        .setRadialCells(100)
        .setWedgeAngleDeg(2.5)           // half-angle of axisymmetric wedge
        .setRadialGrading(5.0)           // wall-normal cell-size ratio
        .setFirstLayerThickness(1e-5)    // overrides radialGrading when set
        .setTurbulenceEnabled(true)      // k-ω SST; false → laminar
        .setTurbulenceIntensity(0.05)    // 5%
        .exportCase(params, contour, Path.of("nozzle_case"));

// Convenience overloads (build NozzleContour and extract params internally)
new OpenFOAMExporter().exportCase(new RaoNozzle(params).generate(),      caseDir);
new OpenFOAMExporter().exportCase(new DualBellNozzle(params).generate(), caseDir);
```

Run the generated case:
```bash
blockMesh && rhoCentralFoam
```

The `blockMeshDict` embeds the wall profile as a `spline` edge so the mesh
faithfully follows the nozzle contour. Boundary patches: `inlet`, `outlet`,
`wall`, `axis` (empty — axisymmetric), `wedge0`, `wedge1`.

---

## com.nozzle.optimization

### AltitudeAdaptiveOptimizer

Searches a parameter grid for the nozzle geometry that maximizes a
dwell-time-weighted mean Isp across a multi-altitude mission profile.
Evaluations run in parallel on Java virtual threads.

```
AltitudeAdaptiveOptimizer opt = new AltitudeAdaptiveOptimizer(params);

// Register altitude conditions (altitude m, weight, dwell time s)
opt.addAltitudeCondition(0,      1.0, 30)   // sea level boost
   .addAltitudeCondition(15_000, 0.8, 120)  // transonic
   .addAltitudeCondition(80_000, 1.5, 300); // upper atmosphere
// or use the built-in standard profile:
opt.addStandardProfile();

// Tune the search grid (defaults shown)
AltitudeAdaptiveOptimizer.OptimizationConfig config =
        new AltitudeAdaptiveOptimizer.OptimizationConfig(
                5,    // exit-Mach candidates (area-ratio multipliers)
                3,    // length-fraction candidates
                3,    // wall-angle candidates
                false // fine mode (true → 7×5×5 = 175 candidates)
        );

opt.optimize();

AltitudeAdaptiveOptimizer.OptimizationResult best = opt.getBestResult();
best.parameters()           // optimal NozzleDesignParameters
best.weightedMeanIsp()      // objective value, seconds
best.altitudeIspValues()    // Isp at each registered altitude, double[]

List<AltitudeAdaptiveOptimizer.OptimizationResult> top5 = opt.getTopResults(5);
```

### MonteCarloUncertainty

Propagates manufacturing and measurement uncertainties to performance metrics
via Monte Carlo sampling. Uses virtual threads; 10,000 samples takes a few
seconds on a modern machine.

```
MonteCarloUncertainty mc = new MonteCarloUncertainty(params, 10_000, 42L);

// Register individual uncertainties
mc.addNormalParameter("throatRadius",   params.throatRadius(),   0.0002) // ±0.2 mm 1σ
  .addNormalParameter("chamberPressure", params.chamberPressure(), 70_000) // ±70 kPa 1σ
  .addUniformParameter("wallAngle", 28.0, 32.0); // uniform [28°, 32°]

// Or register the full set of typical manufacturing tolerances at once
mc.addTypicalUncertainties();
// Registers: ±0.5% throatRadius, ±2% chamberPressure, ±3% chamberTemperature,
//            ±2% gamma, ±0.5° wallAngle (all normal distributions)

mc.run();

MonteCarloUncertainty.StatisticalSummary summary = mc.getSummary();
summary.cfStats().mean()
summary.cfStats().stddev()
summary.cfStats().p05()          // 5th percentile
summary.cfStats().p95()          // 95th percentile
summary.cfStats().coefficientOfVariation()  // stddev / mean
summary.ispStats()               // same structure for Isp

// Sensitivity rankings (Pearson r against Cf and Isp)
Map<String, Double> sens = mc.getSensitivities();
// Keys: "throatRadius_Cf_correlation", "throatRadius_Isp_correlation", ...
sens.entrySet().stream()
    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
    .forEach(e -> System.out.printf("%-40s %.4f%n", e.getKey(), e.getValue()));
```

---

## com.nozzle.validation

### NASASP8120Validator

```
NASASP8120Validator v = new NASASP8120Validator(params, net, contour);

NASASP8120Validator.ValidationResult result = v.validate();

result.isValid()     // false if any hard constraint is violated
result.errors()      // List<String> — must-fix violations
result.warnings()    // List<String> — advisory issues
result.metrics()     // Map<String, Double> — numeric results

// Key metric keys:
// "area_ratio_error_percent"        — computed vs. design A/A*
// "thrust_coeff_error_percent"
// "isp_error_percent"
// "estimated_divergence_efficiency"
// "exit_flow_angle_deg"

if (!result.isValid()) {
    result.errors().forEach(System.err::println);
}
```

**Hard constraints checked:** area-ratio error < 1%, thrust-coefficient error < 1%,
Isp error < 2%, exit Mach in [1.5, 6.0], area ratio ≤ 400, wall angle ≤ 35°.

---

## com.nozzle.io

### NozzleSerializer / DesignDocument

`DesignDocument` is a JSON-serializable checkpoint record. Three states are
supported — parameters only, parameters + MOC net, and fully solved with contour.

```
// Stage 1 — capture design intent
DesignDocument stage1 = DesignDocument.parametersOnly(params);
NozzleSerializer.save(stage1, Path.of("nozzle.json"));

// Stage 2 — load, run MOC, save
DesignDocument d2    = NozzleSerializer.load(Path.of("nozzle.json"));
CharacteristicNet net = new CharacteristicNet(d2.parameters()).generate();
NozzleContour contour = NozzleContour.fromMOCWallPoints(d2.parameters(), net.getWallPoints());
contour.generate(200);
NozzleSerializer.save(d2.withNet(net).withContour(contour), Path.of("nozzle.json"));

// Stage 3 — load fully solved document
DesignDocument solved = NozzleSerializer.load(Path.of("nozzle.json"));
solved.parameters()    // NozzleDesignParameters
solved.wallPoints()    // List<CharacteristicPoint> — MOC wall points
solved.netPoints()     // List<List<CharacteristicPoint>> — full MOC grid
solved.contourPoints() // List<Point2D> — discrete contour

// JSON string (for HTTP APIs, logging, etc.)
String json = NozzleSerializer.toJson(stage1);
DesignDocument from = NozzleSerializer.fromJson(json);
```

The serialized schema version is `"1.0"`. The `createdAt` field records the
`Instant` at which the document was created.

---

## Threading Notes

- `NozzleDesignParameters`, `GasProperties`, and all result records are immutable
  and safe to share across threads.
- `CharacteristicNet`, `RaoNozzle`, `AerospikeNozzle`, `DualBellNozzle`,
  `NozzleContour`, `ChemistryModel`, and all thermal/export classes are **not**
  thread-safe. Construct one instance per thread or per logical calculation.
- `AltitudeAdaptiveOptimizer` and `MonteCarloUncertainty` manage their own
  internal thread pool (virtual threads). The optimizer/uncertainty instance
  itself should be used from a single thread.
- `OFSweep.computeAt()` is safe to call from multiple threads simultaneously
  because it constructs a fresh `ChemistryModel` on each invocation.

---

## Exception Handling

| Condition                                              | Exception                                                      |
|--------------------------------------------------------|----------------------------------------------------------------|
| Invalid `NozzleDesignParameters` field value           | `jakarta.validation.ConstraintViolationException`              |
| `transitionAreaRatio` out of range in `DualBellNozzle` | `IllegalArgumentException`                                     |
| Non-positive `firstLayerThickness`                     | `IllegalArgumentException`                                     |
| `NozzleContour.getContourPoints()` before `generate()` | Returns empty list (no exception)                              |
| `CharacteristicNet` convergence failure                | Logged at WARN; result may be incomplete                       |
| File I/O errors in exporters                           | `java.io.IOException` (checked)                                |
| JSON parse error in `NozzleSerializer.load()`          | `com.fasterxml.jackson.core.JsonProcessingException` (checked) |
