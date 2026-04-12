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

| Method                         | Returns                                                                     |
|--------------------------------|-----------------------------------------------------------------------------|
| `chamberRadius()`              | r_t · √(contractionRatio)                                                   |
| `convergentHalfAngleDegrees()` | convergentHalfAngle converted to degrees                                    |
| `dischargeCoefficient()`       | Cd ∈ [0.98, 1.0] — sonic-line curvature correction to effective throat area |

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

// Build geometry-complete wall (chamber face → exit) with FullNozzleGeometry
FullNozzleGeometry fullGeom = new FullNozzleGeometry(params).generate(50, 100);

// Pass to full-nozzle DXF export methods
new DXFExporter().exportFullNozzleProfile(fullGeom, path);
new DXFExporter().exportFullNozzleRevolutionProfile(fullGeom, path);

// BL integration starting from the injector face — more accurate throat δ*
CharacteristicNet net = new CharacteristicNet(params).generate();
BoundaryLayerCorrection bl = new BoundaryLayerCorrection(params, net)
        .calculateFromInjectorFace(fullGeom, net.getFlowPoints());

// Cd is applied automatically — no ConvergentSection needed by PerformanceCalculator
PerformanceCalculator pc = new PerformanceCalculator(params, net,
        NozzleContour.fromMOCWallPoints(params, net.getWallPoints()), bl, null).calculate();
System.out.println(params.dischargeCoefficient());  // geometric Cd ∈ [0.98, 1.0]
System.out.println(bl.getCombinedCd());             // geometric × BL Cd
System.out.println(pc.getSpecificImpulse());
```

**Key methods:**

| Method                       | Description                                                     |
|------------------------------|-----------------------------------------------------------------|
| `generate(int n)`            | Populate wall points; returns `this`                            |
| `getContourPoints()`         | Ordered wall points from chamber face to throat (x < 0)         |
| `getChamberRadius()`         | r_c = r_t · √(contractionRatio) in metres                       |
| `getLength()`                | Axial extent of convergent section (positive value)             |
| `getArcEndX()`               | x-coordinate of arc/cone junction                               |
| `getArcEndY()`               | Radius at arc/cone junction                                     |
| `getSonicLineCdCorrection()` | Cd ∈ [0.98, 1.0] — delegates to `params.dischargeCoefficient()` |

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

### Units

Static utility class for unit conversion at imperial/SI system boundaries.
All internal library calculations use SI; use `Units` only when ingesting
external data in non-SI units or formatting results for non-SI consumers.

**Length:**

```
Units.metersToMillimeters(0.05)   // → 50.0
Units.metersToInches(0.0254)      // → 1.0
Units.metersToFeet(0.3048)        // → 1.0
Units.millimetersToMeters(50.0)   // → 0.05
Units.inchesToMeters(1.0)         // → 0.0254
Units.feetToMeters(1.0)           // → 0.3048
```

**Pressure:**

```
Units.pascalsToPsi(101_325.0)     // ≈ 14.696
Units.psiToPascals(14.696)        // ≈ 101 325
Units.pascalsToBar(100_000.0)     // → 1.0
Units.barToPascals(1.0)           // → 100 000
Units.pascalsToAtm(101_325.0)     // → 1.0
Units.atmToPascals(1.0)           // → 101 325
```

**Temperature:**

```
Units.kelvinToCelsius(273.15)          // → 0.0
Units.celsiusToKelvin(0.0)             // → 273.15
Units.kelvinToFahrenheit(255.372)      // → 0.0
Units.fahrenheitToKelvin(0.0)          // ≈ 255.372
Units.rankineToKelvin(491.67)          // → 273.15
Units.kelvinToRankine(273.15)          // → 491.67
```

**Force and mass:**

```
Units.newtonsToLbf(4.44822)       // → 1.0
Units.lbfToNewtons(1.0)           // → 4.44822
Units.kilogramsToLbm(0.453592)    // → 1.0
Units.lbmToKilograms(1.0)         // → 0.453592
```

**Specific heat (propulsion-critical):**

Conversion factor: 1 BTU/(lbm·°R) = 4186.8 J/(kg·K)

```
Units.jPerKgKToBtuPerLbmR(4186.8)  // → 1.0
Units.btuPerLbmRToJPerKgK(1.0)     // → 4186.8
Units.jPerKgKToBtuPerLbmR(1005.0)  // ≈ 0.240  (air Cp at STP)
```

**Velocity:**

```
Units.metersPerSecondToFeetPerSecond(304.8)  // → 1000.0
Units.feetPerSecondToMetersPerSecond(1000.0) // → 304.8
```

No instances are permitted — all methods are `static`.

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

**Sonic-line initial data line (Hall 1962):**

The initial data line is placed on the *curved* M = 1 surface, not a flat plane
at x = 0.  The sonic surface bows downstream near the wall according to

```
x_s(r) = coeff · r² · (1/R_cd + 1/(3·R_cu))
```

where `coeff = (γ+1)/12` for axisymmetric flow (`(γ+1)/6` for planar 2-D),
`R_cd = throatCurvatureRatio × r_t` is the downstream throat-arc radius, and
`R_cu = upstreamCurvatureRatio × r_t` is the upstream throat-arc radius.  Using
the flat plane introduces a leading-order error in the first few characteristic
rows that grows with throat curvature; the curved placement removes it.  The Rao
wall profile starts at the same `x_s(r_t)` so the wall sequence is consistent.

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

### FullNozzleGeometry

Assembles a contiguous, axially ordered wall-point sequence spanning the
injector face (x < 0) through the throat (x = 0) to the exit (x > 0).
All DXF/STEP/STL geometry-complete export methods expect this object.

**Coordinate convention:** x = 0 at the throat, x < 0 in the convergent
section, y = wall radius.

```
// Default: Rao bell divergent + ConvergentSection derived from params
FullNozzleGeometry geom = new FullNozzleGeometry(params).generate();
// equivalent to:
FullNozzleGeometry geom = new FullNozzleGeometry(params).generate(50, 100);

// Explicit convergent/divergent point counts
FullNozzleGeometry geom = new FullNozzleGeometry(params).generate(60, 150);

// MOC-derived divergent section (pass 0 to skip re-generating the divergent wall)
CharacteristicNet net = new CharacteristicNet(params).generate();
FullNozzleGeometry mocGeom = FullNozzleGeometry.fromMOC(params, net).generate(50, 0);

// Custom sections
ConvergentSection  cs  = new ConvergentSection(params);
NozzleContour      div = new NozzleContour(NozzleContour.ContourType.CONICAL, params);
FullNozzleGeometry custom = new FullNozzleGeometry(params, cs, div).generate(40, 80);
```

**Key methods:**

| Method                    | Returns                                                          |
|---------------------------|------------------------------------------------------------------|
| `generate()`              | Populate wall points (50 conv, 100 div); returns `this`          |
| `generate(nConv, nDiv)`   | Custom point counts; pass `nDiv=0` to keep existing divergent    |
| `getWallPoints()`         | Unmodifiable list, injector face → exit                          |
| `getThroatRadius()`       | r_t in metres                                                    |
| `getChamberRadius()`      | r_t · √(contractionRatio)                                        |
| `getExitRadius()`         | r_t · √(A_e / A_t)                                               |
| `getChamberFaceX()`       | x of most upstream wall point (negative)                         |
| `getExitX()`              | x of most downstream wall point                                  |
| `getTotalLength()`        | exit_x − chamber_x in metres                                     |
| `getConvergentLength()`   | axial extent of convergent section                               |
| `getDivergentLength()`    | exit_x (measured from throat)                                    |
| `getSonicLineCd()`        | geometric Cd from `params.dischargeCoefficient()`                |
| `getConvergentSection()`  | the underlying `ConvergentSection`                               |
| `getDivergentContour()`   | the underlying `NozzleContour`                                   |
| `isGenerated()`           | `true` after any `generate()` call                               |

```
// Full-nozzle DXF exports
DXFExporter dxf = new DXFExporter().setScaleFactor(1000);
dxf.exportFullNozzleProfile(geom, Path.of("nozzle.dxf"));
dxf.exportFullNozzleRevolutionProfile(geom, Path.of("profile.dxf"));

// Geometry queries
System.out.printf("Total length : %.3f m%n", geom.getTotalLength());
System.out.printf("Cd (sonic)   : %.4f%n",   geom.getSonicLineCd());
System.out.printf("Wall pts     : %d%n",      geom.getWallPoints().size());
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

Two calculation modes are available. `calculate()` processes only the divergent
section (x ≥ 0); `calculateFullProfile()` processes the complete wall (convergent
+ divergent) so the heat-flux peak can be found even when it falls upstream of
the throat.

The Bartz curvature correction `(D_t/r_c)^0.1` uses the parametric throat-arc
radii — `r_cd = throatCurvatureRatio × r_t` for the downstream arc zone
(x ∈ [0, r_cd]) and `r_cu = upstreamCurvatureRatio × r_t` for the upstream arc
zone (x ∈ [−r_cu, 0)) — instead of noisy finite differences.  This makes the
predicted peak location sensitive to both curvature ratios.

```
NozzleContour contour = NozzleContour.fromMOCWallPoints(params, net.getWallPoints());

// Divergent-section only (fast; peak is on the divergent side for standard params)
HeatTransferModel htr = new HeatTransferModel(params, contour)
        .setWallProperties(20.0, 0.003)   // conductivity W/(m·K), wall thickness m
        .setCoolantProperties(300.0, 5000.0) // coolant T (K), film h W/(m²·K)
        .setEmissivity(0.8)
        .calculate(net.getAllPoints());

double maxWallT   = htr.getMaxWallTemperature();  // K  — profile maximum
double maxFlux    = htr.getMaxHeatFlux();          // W/m² — profile maximum
double totalPower = htr.getTotalHeatLoad();        // W

// Peak-location accessors (set by both calculate() and calculateFullProfile())
HeatTransferModel.WallThermalPoint peak = htr.getPeakFluxPoint(); // null before calculation
double peakX = htr.getPeakFluxX();  // axial position in metres; NaN before calculation

List<HeatTransferModel.WallThermalPoint> profile = htr.getWallThermalProfile();
for (var p : profile) {
    // p.x(), p.y(), p.wallTemperature(), p.totalHeatFlux(),
    // p.convectiveHeatFlux(), p.radiativeHeatFlux(), p.heatTransferCoeff(),
    // p.recoveryTemperature()
}

// Full-profile mode — covers convergent section (x < 0) as well.
// For convergent points the local Mach number is estimated from the isentropic
// area-Mach relation; divergent points use the nearest MOC flow point.
FullNozzleGeometry fullGeom = new FullNozzleGeometry(params).generate(50, 100);
HeatTransferModel htrFull = new HeatTransferModel(params, contour)
        .setWallProperties(20.0, 0.003)
        .setCoolantProperties(300.0, 5000.0)
        .calculateFullProfile(fullGeom, net.getAllPoints());

HeatTransferModel.WallThermalPoint fullPeak = htrFull.getPeakFluxPoint();
System.out.printf("Peak at x = %+.3f mm%n", fullPeak.x() * 1000);
System.out.printf("Peak q   = %.3e W/m²%n", fullPeak.totalHeatFlux());
System.out.printf("Peak T_w = %.0f K%n",    fullPeak.wallTemperature());
```

**Method summary:**

| Method                                | Returns                                                                 |
|---------------------------------------|-------------------------------------------------------------------------|
| `calculate(flowPoints)`               | Run Bartz over divergent section; returns `this`                        |
| `calculateFullProfile(geom, pts)`     | Run Bartz over full wall (conv + div); returns `this`                   |
| `getPeakFluxPoint()`                  | `WallThermalPoint` at the heat-flux peak, or `null` before calculation  |
| `getPeakFluxX()`                      | Axial position of peak in metres; `NaN` before calculation              |
| `getMaxWallTemperature()`             | Maximum wall temperature in K across the computed profile               |
| `getMaxHeatFlux()`                    | Maximum total heat flux in W/m² across the computed profile             |
| `getTotalHeatLoad()`                  | Integrated heat load over the wall surface in W                         |
| `getWallThermalProfile()`             | `List<WallThermalPoint>` — all computed points                          |

### BoundaryLayerCorrection

Two calculation modes are available. The standard mode starts the BL at the
throat and covers only the divergent section. The injector-face mode starts at
the chamber face, accumulating running length through the convergent arc and
cone, so the throat displacement thickness δ* is physically larger and more
accurate.

```
// Standard mode — BL starts at throat (divergent section only)
BoundaryLayerCorrection bl = new BoundaryLayerCorrection(params, net)
        .setTransitionReynolds(5e5)    // default; set lower to force early transition
        .setForceTurbulent(false)
        .calculate();

// Injector-face mode — BL starts at chamber face (requires FullNozzleGeometry)
FullNozzleGeometry fullGeom = new FullNozzleGeometry(params).generate();
BoundaryLayerCorrection blFull = new BoundaryLayerCorrection(params, net)
        .calculateFromInjectorFace(fullGeom, net.getFlowPoints());

// Throat displacement thickness and Cd corrections
double deltaStar = blFull.getThroatDisplacementThickness(); // δ* at x ≈ 0, metres
double blCd      = blFull.getBoundaryLayerCdCorrection();   // (1 − δ*/r_t)²
double combinedCd = blFull.getCombinedCd();                 // geometric Cd × BL Cd

System.out.printf("δ* at throat : %.4f mm%n", deltaStar * 1000);
System.out.printf("BL Cd        : %.4f%n",    blCd);
System.out.printf("Combined Cd  : %.4f%n",    combinedCd);

// Both modes are accepted by PerformanceCalculator and HeatTransferModel
```

**New methods (injector-face mode):**

| Method                              | Returns                                                            |
|-------------------------------------|--------------------------------------------------------------------|
| `calculateFromInjectorFace(g, pts)` | Run BL from injector face; returns `this`                          |
| `getThroatDisplacementThickness()`  | δ* at the throat plane (x ≈ 0) in metres                           |
| `getBoundaryLayerCdCorrection()`    | (1 − δ*/r_t)² — effective area loss factor                         |
| `getCombinedCd()`                   | `params.dischargeCoefficient()` × `getBoundaryLayerCdCorrection()` |

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

Every exporter that previously accepted only `NozzleContour` (divergent section,
x ≥ 0) now also accepts `FullNozzleGeometry` to produce a geometry-complete file
covering the injector face through the throat to the nozzle exit.

### CSVExporter

```
CSVExporter csv = new CSVExporter();

// Divergent section only
csv.exportContour(contour, Path.of("wall.csv"));        // x, y, angle_deg

// Geometry-complete (convergent + divergent)
FullNozzleGeometry fullGeom = new FullNozzleGeometry(params).generate(50, 100);
csv.exportContour(fullGeom, Path.of("wall_full.csv"));  // x, y, angle_deg, section

csv.exportCharacteristicNet(net, Path.of("net.csv"));
csv.exportWallContour(net, Path.of("wall_pts.csv"));
csv.exportThermalProfile(htr, Path.of("thermal.csv"));  // covers full profile if calculateFullProfile() was used
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
DXFExporter dxf = new DXFExporter().setScaleFactor(1000);  // metres → mm

// Divergent section only
dxf.exportContour(contour, Path.of("nozzle.dxf"));
// Layers: WALL (polyline), AXIS (centerline)

dxf.exportRevolutionProfile(contour, Path.of("profile.dxf"));
// Layers: WALL (polyline), AXIS (3 closure lines)

// Full nozzle — injector face → exit (requires FullNozzleGeometry)
FullNozzleGeometry geom = new FullNozzleGeometry(params).generate();

dxf.exportFullNozzleProfile(geom, Path.of("full_nozzle.dxf"));
// Layers: WALL (polyline chamber→exit), AXIS (centerline), THROAT (x=0 marker)

dxf.exportFullNozzleRevolutionProfile(geom, Path.of("full_profile.dxf"));
// Layers: WALL, AXIS (bottom), INLET (left face), OUTLET (right face), THROAT
// Use this profile as the sketch for a revolve operation in CAD tools.

// Aerospike
dxf.exportAerospikeContour(aero, Path.of("spike.dxf"));
// Layers: SPIKE (contour polyline), COWL (annular throat face), AXIS
```

**`exportFullNozzleProfile` layers:**

| Layer    | Entity   | Description                               |
|----------|----------|-------------------------------------------|
| `WALL`   | POLYLINE | Inner wall from chamber face to exit      |
| `AXIS`   | LINE     | Centerline from x_chamber to x_exit       |
| `THROAT` | LINE     | Vertical marker at x = 0 from axis to r_t |

**`exportFullNozzleRevolutionProfile` layers:**

| Layer    | Entity   | Description                                   |
|----------|----------|-----------------------------------------------|
| `WALL`   | POLYLINE | Inner wall from chamber face to exit          |
| `AXIS`   | LINE     | Centerline (bottom closure, x_chamber→x_exit) |
| `INLET`  | LINE     | Left (chamber) face: axis to wall             |
| `OUTLET` | LINE     | Right (exit) face: wall to axis               |
| `THROAT` | LINE     | Throat plane marker at x = 0                  |

### STEPExporter

```
STEPExporter step = new STEPExporter()
        .setScaleFactor(1000)
        .setAuthor("YOUR NAME HERE")
        .setOrganization("YOUR ORG HERE");

// Divergent only
step.exportRevolvedSolid(contour, Path.of("nozzle.step"));
step.exportProfileCurve(contour,  Path.of("profile.step"));

// Geometry-complete (convergent + divergent)
step.exportRevolvedSolid(fullGeom, Path.of("nozzle_full.step"));
step.exportProfileCurve(fullGeom,  Path.of("profile_full.step"));

// Aerospike
step.exportAerospikeRevolvedSolid(aero, Path.of("spike.step"));
```

### STLExporter

```
STLExporter stl = new STLExporter()
        .setCircumferentialSegments(72)  // facets around the axis
        .setScaleFactor(1000)
        .setBinaryFormat(true);          // false for ASCII

// Divergent only
stl.exportMesh(contour, Path.of("nozzle.stl"));
stl.exportInnerSurfaceMesh(contour, Path.of("nozzle_inner.stl")); // same as exportMesh

// Geometry-complete (convergent + divergent)
stl.exportMesh(fullGeom, Path.of("nozzle_full.stl"));
stl.exportInnerSurfaceMesh(fullGeom, Path.of("nozzle_full_inner.stl"));

// Aerospike
stl.exportAerospikeMesh(aero, Path.of("spike.stl"));

int triangles = stl.estimateTriangleCount(contour.getContourPoints().size());
```

### CFDMeshExporter

Exports 2-D axisymmetric mesh input files. For a complete OpenFOAM case use
`OpenFOAMExporter` instead.

```
CFDMeshExporter cfd = new CFDMeshExporter()
        .setAxialCells(200)
        .setRadialCells(80)
        .setFirstLayerThickness(1e-5);   // y⁺-controlled grading; overrides expansionRatio

// Divergent only
cfd.export(contour, Path.of("blockMeshDict"), CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);
cfd.export(contour, Path.of("nozzle.geo"),    CFDMeshExporter.Format.GMSH_GEO);
cfd.export(contour, Path.of("nozzle.xyz"),    CFDMeshExporter.Format.PLOT3D);
cfd.export(contour, Path.of("nozzle.cgns"),   CFDMeshExporter.Format.CGNS);

// Geometry-complete (convergent + divergent — captures the transonic throat)
cfd.export(fullGeom, Path.of("blockMeshDict_full"), CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);
cfd.export(fullGeom, Path.of("nozzle_full.geo"),    CFDMeshExporter.Format.GMSH_GEO);
cfd.export(fullGeom, Path.of("nozzle_full.xyz"),    CFDMeshExporter.Format.PLOT3D);
cfd.export(fullGeom, Path.of("nozzle_full.cgns"),   CFDMeshExporter.Format.CGNS);

// Convenience overloads — build NozzleContour internally
cfd.export(new RaoNozzle(params).generate(),      Path.of("bmd"), CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);
cfd.export(new DualBellNozzle(params).generate(), Path.of("bmd"), CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);

// Aerospike — annular domain (spike inner wall, cowl outer wall)
cfd.exportAerospike(aero, Path.of("aero_bmd"), CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);
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
OpenFOAMExporter foam = new OpenFOAMExporter()
        .setAxialCells(300)
        .setRadialCells(100)
        .setWedgeAngleDeg(2.5)           // half-angle of axisymmetric wedge
        .setRadialGrading(5.0)           // wall-normal cell-size ratio
        .setFirstLayerThickness(1e-5)    // overrides radialGrading when set
        .setTurbulenceEnabled(true)      // k-ω SST; false → laminar
        .setTurbulenceIntensity(0.05);   // 5%

// Divergent only
foam.exportCase(params, contour, Path.of("nozzle_case"));

// Geometry-complete (inlet is at the injector face x_chamber)
foam.exportCase(params, fullGeom, Path.of("nozzle_case_full"));

// Convenience overloads (build NozzleContour and extract params internally)
foam.exportCase(new RaoNozzle(params).generate(),      Path.of("rao_case"));
foam.exportCase(new DualBellNozzle(params).generate(), Path.of("dualbell_case"));
```

Run the generated case:
```bash
blockMesh && rhoCentralFoam
```

The `blockMeshDict` embeds the wall profile as a `spline` edge so the mesh
faithfully follows the nozzle contour. Boundary patches: `inlet`, `outlet`,
`wall`, `axis` (empty — axisymmetric), `wedge0`, `wedge1`.

### RevolvedMeshExporter

Exports full 3-D volumetric meshes by revolving the 2-D profile around the axis.

```
RevolvedMeshExporter rev = new RevolvedMeshExporter()
        .setAxialCells(100)
        .setRadialCells(40)
        .setAzimuthalCells(16)         // 22.5° per sector
        .setExpansionRatio(4.0);       // or setFirstLayerThickness(y1)

// Divergent only
rev.export(contour, Path.of("blockMeshDict_3d"), RevolvedMeshExporter.Format.OPENFOAM_BLOCKMESH);
rev.export(contour, Path.of("nozzle_3d.geo"),    RevolvedMeshExporter.Format.GMSH_GEO);
rev.export(contour, Path.of("nozzle_3d.xyz"),    RevolvedMeshExporter.Format.PLOT3D);

// Geometry-complete (convergent + divergent)
rev.export(fullGeom, Path.of("blockMeshDict_3d_full"), RevolvedMeshExporter.Format.OPENFOAM_BLOCKMESH);
rev.export(fullGeom, Path.of("nozzle_3d_full.geo"),    RevolvedMeshExporter.Format.GMSH_GEO);
rev.export(fullGeom, Path.of("nozzle_3d_full.xyz"),    RevolvedMeshExporter.Format.PLOT3D);
```

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
