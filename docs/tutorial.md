# Usage Tutorial

A worked, task-oriented guide. Each section builds a real design from scratch
and explains *why* each step is taken, not just *what* API to call. The
API reference is in `api-guide.md`; the architecture overview is in
`architecture.md`.

All quantities are SI throughout (metres, Pascals, Kelvin, Newtons, kg).

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Tutorial 1 — Basic bell nozzle (LOX/RP-1)](#2-tutorial-1--basic-bell-nozzle-loxrp-1)
3. [Tutorial 2 — Finding the optimum O/F ratio first](#3-tutorial-2--finding-the-optimum-of-ratio-first)
4. [Tutorial 3 — Thermal analysis and cooling strategy](#4-tutorial-3--thermal-analysis-and-cooling-strategy)
5. [Tutorial 4 — Aerospike nozzle](#5-tutorial-4--aerospike-nozzle)
6. [Tutorial 5 — Dual-bell altitude-compensating nozzle](#6-tutorial-5--dual-bell-altitude-compensating-nozzle)
7. [Tutorial 6 — Validation and uncertainty analysis](#7-tutorial-6--validation-and-uncertainty-analysis)
8. [Tutorial 7 — Saving and reloading design checkpoints](#8-tutorial-7--saving-and-reloading-design-checkpoints)
9. [Tutorial 8 — CFD and CAD export](#9-tutorial-8--cfd-and-cad-export)

---

## 1. Prerequisites

Add the library to your `pom.xml`:

```xml
<dependency>
    <groupId>com.nozzle</groupId>
    <artifactId>lib_java_rocket_nozzle</artifactId>
    <version>1.0.0</version>
</dependency>
```

The library requires Java 21 with preview features enabled. Add to your
compiler and surefire configuration:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <release>21</release>
        <compilerArgs><arg>--enable-preview</arg></compilerArgs>
    </configuration>
</plugin>
```

No other runtime dependencies are needed beyond those declared in the library's
own POM (Jackson for serialization, Jakarta Validation / Hibernate Validator for
parameter checking).

---

## 2. Tutorial 1 — Basic bell nozzle (LOX/RP-1)

### Goal

Design a LOX/RP-1 bell nozzle, compute its performance, and confirm the
numbers are physically sensible.

### Step 1 — Define the design intent

`NozzleDesignParameters` is the single root input for every calculation. It is
a Java record (immutable after construction) built through a fluent builder.

```
NozzleDesignParameters params = NozzleDesignParameters.builder()
        .throatRadius(0.05)               // 50 mm — a medium-scale engine
        .exitMach(3.5)                    // supersonic exit condition
        .chamberPressure(7e6)             // 7 MPa chamber pressure
        .chamberTemperature(3500)         // K adiabatic flame temp (LOX/RP-1 ≈ 3500 K at OF=2.4)
        .ambientPressure(101_325)         // sea-level ambient, Pa
        .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
        .numberOfCharLines(30)            // MOC grid density; 20–50 is typical
        .wallAngleInitialDegrees(30)      // initial wall angle at throat
        .lengthFraction(0.8)              // 80% of ideal (conical) nozzle length
        .axisymmetric(true)
        // .throatCurvatureRatio(0.382)        // downstream arc (default 0.382)
        // .upstreamCurvatureRatio(1.5)        // upstream arc (default 1.5)
        // .convergentHalfAngleDegrees(30)     // convergent cone half-angle (default 30°)
        // .contractionRatio(4.0)              // Ac/At (default 4.0)
        .build();
```

`Tc` and `Pc` are design inputs here. In Tutorial 2 you will see how to derive
`Tc` from the propellant chemistry instead of specifying it by hand.

`lengthFraction` controls how aggressively the Rao bell contour is shortened
relative to a 15° conical reference nozzle. Values between 0.7 and 0.9 are
typical for flight engines; 0.8 is a good starting point.

`throatCurvatureRatio` sets the downstream throat radius of curvature as a
multiple of the throat radius (`r_cd = ratio × r_throat`). The default of
0.382 is the classical Rao value and is appropriate for most designs.

`upstreamCurvatureRatio` sets the upstream (convergent-side) throat arc radius
as a multiple of r_t. The default 1.5 is standard for liquid-propellant bell
nozzles. Together with `throatCurvatureRatio`, it determines the curvature of
the sonic line and hence the discharge coefficient.

`convergentHalfAngleDegrees` sets the half-angle of the straight conical section
that connects the upstream arc to the combustion chamber. Default 30°.

`contractionRatio` is the chamber-to-throat area ratio Ac/At. It determines the
chamber radius `r_c = r_t × √(contractionRatio)`. Default 4.0.

The discharge coefficient `Cd` is a derived property of `NozzleDesignParameters`
and is always applied automatically by `PerformanceCalculator`:

```
// Cd is available directly from params — no ConvergentSection required
double cd = params.dischargeCoefficient();   // e.g. 0.9962 for defaults

// PerformanceCalculator always applies Cd to thrust and mass flow
PerformanceCalculator pc = PerformanceCalculator.simple(params).calculate();
System.out.printf("Cd  = %.5f%n", params.dischargeCoefficient());
System.out.printf("Isp = %.1f s  (Cd does not affect Isp)%n", pc.getSpecificImpulse());
```

To also generate the full convergent-section geometry:

```
// Build the convergent section geometry
ConvergentSection cs = new ConvergentSection(params).generate(60);

// Build a geometry-complete wall contour (chamber face → exit)
NozzleContour divergent = NozzleContour.fromMOCWallPoints(params, net.getWallPoints());
NozzleContour full      = divergent.withConvergentSection(cs);

// BL integration now starts at the chamber face
BoundaryLayerCorrection bl = new BoundaryLayerCorrection(params, full).calculate(null);

// Cd still comes from params; pass the full contour for BL and geometry exports
PerformanceCalculator pc = new PerformanceCalculator(params, net, full, bl, null).calculate();

// All exporters produce geometry-complete output automatically
new DXFExporter().exportRevolutionProfile(full, outputDir.resolve("full_nozzle.dxf"));
new STLExporter().exportMesh(full,             outputDir.resolve("full_nozzle.stl"));
```

### Step 2 — Run the Method of Characteristics solver

The MOC solver computes the full characteristic net — the grid of left-running
and right-running characteristics that fill the supersonic region of the nozzle.
It also extracts the wall-point sequence that defines the optimum bell contour.

```
CharacteristicNet net = new CharacteristicNet(params).generate();

System.out.printf("Wall points    : %d%n",    net.getWallPoints().size());
System.out.printf("Total net pts  : %d%n",    net.getTotalPointCount());
System.out.printf("Exit A/A*      : %.4f%n",  net.calculateExitAreaRatio());
```

The MOC solver is the authoritative geometry source. Every downstream
calculation (boundary layer, heat transfer, performance) that needs wall-point
data should use this net, not an approximation.

### Step 3 — Build the smooth wall contour

The MOC produces a set of discrete wall points. `NozzleContour` fits a smooth
spline through them so that exporters and thermal solvers can query the radius
and wall angle at any axial position.

```
NozzleContour contour = NozzleContour.fromMOCWallPoints(params, net.getWallPoints());
contour.generate(200);   // 200 discrete evaluation points along the wall

System.out.printf("Contour length  : %.4f m%n",  contour.getLength());
System.out.printf("Exit radius     : %.4f m%n",
        contour.getRadiusAt(contour.getLength()));
```

`generate(n)` controls the resolution of the output point list used by
exporters. It does not affect solver accuracy — the spline is fitted once and
queried at arbitrary positions internally.

### Step 4 — Calculate performance

`PerformanceCalculator` accounts for four loss mechanisms: divergence loss
(non-axial exit flow), boundary layer loss (reduced effective throat area),
chemistry loss (frozen vs. equilibrium recombination), and two-phase loss
(droplets/particles — zero here because we are not modeling particles).

```
// Boundary layer correction — needed for both performance and heat transfer
BoundaryLayerCorrection bl = new BoundaryLayerCorrection(params, contour).calculate();

// Equilibrium chemistry model for the LOX/RP-1 propellant at O/F = 2.4
ChemistryModel chem = ChemistryModel.equilibrium(params.gasProperties());
chem.setLoxRp1Composition(2.4);
chem.calculateEquilibrium(params.chamberTemperature(), params.chamberPressure());

// Performance with all available loss mechanisms
PerformanceCalculator perf =
        new PerformanceCalculator(params, net, contour, bl, chem).calculate();

System.out.printf("Ideal Isp      : %.1f s%n",   perf.getIdealSpecificImpulse());
System.out.printf("Actual Isp     : %.1f s%n",   perf.getSpecificImpulse());
System.out.printf("Ideal Cf       : %.4f%n",     perf.getIdealThrustCoefficient());
System.out.printf("Actual Cf      : %.4f%n",     perf.getActualThrustCoefficient());
System.out.printf("Efficiency     : %.2f %%%n",  perf.getEfficiency() * 100);
System.out.printf("Divergence loss: %.4f%n",     perf.getDivergenceLoss());
System.out.printf("BL loss        : %.4f%n",     perf.getBoundaryLayerLoss());
System.out.printf("Chemistry loss : %.4f%n",     perf.getChemicalLoss());
```

For a well-designed LOX/RP-1 nozzle at M=3.5 you should expect an actual Isp
in the range 290–310 s sea-level and a total efficiency of 96–98%.

### Step 5 — Check for flow separation

At sea-level the nozzle is operating at its design ambient pressure, so
separation is unlikely. It is good practice to confirm this and to know the
margin.

```
FlowSeparationPredictor fsp = new FlowSeparationPredictor(params);

// Check all three criteria; they give slightly different answers
for (var crit : FlowSeparationPredictor.Criterion.values()) {
    var r = fsp.predict(crit);
    System.out.printf("%-12s mode=%-15s  x/L=%.2f%n",
            crit, r.mode(), r.axialFraction());
}
```

If `mode()` returns `NO_SEPARATION`, the nozzle is fine at the design
condition. If you are checking off-design (e.g. at high altitude or during a
throttle transient), reduce `ambientPressure` in the parameters and re-run.

---

## 3. Tutorial 2 — Finding the optimum O/F ratio first

### Goal

Use the O/F sweep to find the mixture ratio that maximizes sea-level Isp, then
feed that result into the nozzle design.

### Why this matters

The adiabatic flame temperature of a bipropellant depends on the O/F ratio. The
optimum Isp O/F is not the stoichiometric ratio — for LOX/RP-1 it is typically
around 2.3–2.7 depending on the exit condition. Specifying `Tc` by hand (as in
Tutorial 1) skips this step; `OFSweep` automates it.

### Step 1 — Sweep the O/F range

```
OFSweep sweep = OFSweep.adiabatic(
        OFSweep.Propellant.LOX_RP1,
        7e6,      // Pc, Pa
        3.5,      // exit Mach
        101_325   // Pa ambient (sea level)
);

// Tabulate 40 points between O/F = 1.5 and 4.0
List<OFSweep.OFPoint> curve = sweep.sweep(1.5, 4.0, 40);
for (OFSweep.OFPoint p : curve) {
    System.out.printf("OF=%4.2f  Tc=%5.0f K  Isp=%5.1f s  c*=%6.1f m/s%n",
            p.of(), p.chamberTemperature(), p.isp(), p.cStar());
}
```

### Step 2 — Find the optimum

```
OFSweep.OFPoint best = sweep.optimumIsp(1.5, 4.0);

System.out.printf("Optimum O/F    : %.3f%n",   best.of());
System.out.printf("Adiabatic Tc   : %.0f K%n", best.chamberTemperature());
System.out.printf("Optimum Isp    : %.1f s%n", best.isp());
System.out.printf("c*             : %.1f m/s%n", best.cStar());
```

`optimumIsp` uses golden-section search over the swept range so it is exact
rather than limited by the tabulation step size.

### Step 3 — Build parameters from the sweep result

```
NozzleDesignParameters params = NozzleDesignParameters.builder()
        .throatRadius(0.05)
        .exitMach(3.5)
        .chamberPressure(7e6)
        .chamberTemperature(best.chamberTemperature())  // <-- from sweep
        .ambientPressure(101_325)
        .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
        .numberOfCharLines(30)
        .wallAngleInitialDegrees(30)
        .lengthFraction(0.8)
        .axisymmetric(true)
        .build();
```

From here proceed exactly as in Tutorial 1. The O/F ratio used in the sweep
(`best.of()`) should also be passed to `chem.setLoxRp1Composition()` in the
chemistry model so the equilibrium solver uses the same mixture.

### Comparing frozen vs. equilibrium Isp

```
ChemistryModel chem = ChemistryModel.equilibrium(GasProperties.LOX_RP1_PRODUCTS);
chem.setLoxRp1Composition(best.of());
chem.calculateEquilibrium(best.chamberTemperature(), 7e6);

ChemistryModel.IspComparison cmp =
        chem.compareIsp(best.chamberTemperature(), 7e6, 3.5, 101_325);

System.out.printf("Frozen Isp     : %.1f s%n",      cmp.frozenIsp());
System.out.printf("Equilibrium Isp: %.1f s%n",      cmp.equilibriumIsp());
System.out.printf("Gain           : %.1f s (%.2f %%)%n",
        cmp.delta(), cmp.deltaPercent());
```

The equilibrium gain is typically 5–15 s for LOX/RP-1 at these conditions.

---

## 4. Tutorial 3 — Thermal analysis and cooling strategy

### Goal

Compute wall temperatures and heat fluxes, evaluate three cooling strategies
(regenerative, ablative, radiation-cooled extension), and check structural
margins.

### Step 1 — Set up heat transfer

`HeatTransferModel` implements the Bartz/Eckert convective correlation and adds
a radiation term. It needs the boundary layer correction to get the correct
recovery temperature and displacement-thickness correction to the effective
throat area.

```
// Assume params, net, and contour are already computed (Tutorial 1).
BoundaryLayerCorrection bl = new BoundaryLayerCorrection(params, contour)
        .setTransitionReynolds(5e5)   // laminar-to-turbulent transition
        .calculate();

HeatTransferModel htr = new HeatTransferModel(params, contour)
        .setWallProperties(390, 0.002)    // copper alloy: k=390 W/(m·K), t=2 mm
        .setEmissivity(0.6)               // oxidised copper surface
        .calculate();

System.out.printf("Peak wall temp : %.0f K%n",    htr.getPeakWallTemperature());
System.out.printf("Peak heat flux : %.2e W/m²%n", htr.getPeakHeatFlux());
System.out.printf("Total heat load: %.1f kW%n",   htr.getTotalHeatLoad() / 1000);
```

The peak always occurs near the throat where the local heat-transfer coefficient
is highest. For a 7 MPa LOX/RP-1 combustor expect peak fluxes on the order of
10–50 MW/m².

### Step 2 — Print the axial thermal profile

```
for (HeatTransferModel.WallThermalPoint p : htr.getWallProfile()) {
    System.out.printf("x=%6.3f m  T_wall=%5.0f K  q_conv=%6.2e  q_rad=%6.2e%n",
            p.x(), p.wallTemperature(),
            p.convectiveFlux(), p.radiativeFlux());
}
```

Use this profile to identify whether the throat or some other point governs
the thermal design. A large radiative contribution (>10% of total) suggests
combustion products with significant H₂O or CO₂ emissivity.

### Step 3 — Option A: regenerative coolant channel

Regenerative cooling passes the fuel through channels in the nozzle wall before
injection. It is the standard approach for reusable or high-performance engines.

```
CoolantChannel channel = new CoolantChannel(params, htr, CoolantChannel.Coolant.RP1)
        .calculate();

// Inspect the coolant hydraulics
System.out.printf("Coolant outlet temp    : %.0f K%n",
        channel.getOutletTemperature());
System.out.printf("Coolant mass flow      : %.3f kg/s%n",
        channel.getMassFlowRate());
System.out.printf("Pressure drop          : %.1f kPa%n",
        channel.getPressureDrop() / 1000);
```

If the outlet temperature exceeds the coking temperature of RP-1 (~620 K), you
need to increase the flow rate (more channels, larger cross-section) or switch
to a higher-thermal-capacity coolant.

### Step 4 — Option B: ablative liner

Ablative cooling is simpler mechanically and appropriate for short-duration
motors (solid rockets, expendable upper stages). The material chars and
recesses, absorbing heat by pyrolysis.

```
AblativeNozzleModel ablative = new AblativeNozzleModel(
        params, htr, AblativeNozzleModel.Material.CARBON_PHENOLIC).calculate();

double burnTime = 120; // seconds

System.out.printf("Peak recession rate    : %.3f mm/s%n",
        ablative.getPeakRecessionRate() * 1000);
System.out.printf("Mechanical erosion     : %.3f mm/s%n",
        ablative.getMechanicalErosionRate() * 1000);
System.out.printf("Total ablated mass     : %.2f kg%n",
        ablative.getTotalAblatedMass(burnTime));
```

Multiply the peak recession rate by burn time to get the minimum liner
thickness required. Add a safety margin of 2–3× for scatter in material
properties and hot-gas composition.

### Step 5 — Option C: radiation-cooled nozzle extension

For the nozzle extension downstream of the throat region, radiation cooling is
feasible if the local heat flux is low enough and the material temperature
limit is not exceeded.

```
RadiationCooledExtension rad = new RadiationCooledExtension(
        params, htr, RadiationCooledExtension.Material.RHENIUM).calculate();

System.out.printf("Equilibrium wall temp  : %.0f K%n",
        rad.getEquilibriumWallTemperature());
System.out.printf("Material limit         : %.0f K%n",
        rad.getMaterialLimit());
System.out.printf("Over-temperature       : %b%n",
        rad.isOverTemperature());
```

Rhenium is suitable for very high temperatures (limit ~2200 K) but is expensive.
For less demanding conditions `CARBON_CARBON` or `NIOBIUM_C103` are alternatives.

### Step 6 — Structural margin

Once you know the wall temperature and heat flux, check whether the wall
material can sustain the combined thermal and pressure stress over the expected
life.

```
ThermalStressAnalysis stress = new ThermalStressAnalysis(
        params, htr, ThermalStressAnalysis.Material.CU_CR_ZR).calculate();

System.out.printf("Peak von Mises stress  : %.1f MPa%n",
        stress.getPeakVonMisesStress() / 1e6);
System.out.printf("Safety factor          : %.2f%n",
        stress.getSafetyFactor());
System.out.printf("Fatigue life           : %.0f cycles%n",
        stress.getFatigueLife());
```

A safety factor below 1.5 against yield strength warrants a material or
geometry change. The fatigue life (Basquin + Coffin-Manson combined) should
exceed the design mission life with adequate margin.

Available materials: `CU_CR_ZR` (copper alloy), `INCONEL_718`, `STAINLESS_304`,
`TITANIUM_6AL4V`.

---

## 5. Tutorial 4 — Aerospike nozzle

### Goal

Design an Aerospike (plug) nozzle, characterize its altitude compensation, and
compare it to an equivalent bell nozzle.

### Background

An Aerospike nozzle has no fixed outer wall in the divergent section. The flow
expands from an annular throat over a central spike; the outer boundary is the
ambient pressure. As altitude increases the plume naturally adjusts, giving
near-optimum performance across a wide altitude range — unlike a fixed bell that
is optimized for a single pressure.

### Step 1 — Define the Aerospike

```
// Use the same NozzleDesignParameters as before.
// spikeRadiusRatio: inner radius of annular throat / throat radius (0.4–0.7)
// truncationFraction: 0.0 = full spike (theoretical), 1.0 = no truncation
//   practical values 0.7–0.85 balance performance and spike mass
AerospikeNozzle aero = new AerospikeNozzle(params, 0.4, 0.8, 150).generate();

System.out.printf("Full spike length    : %.3f m%n", aero.getFullSpikeLength());
System.out.printf("Truncated length     : %.3f m%n", aero.getTruncatedLength());
System.out.printf("Annular throat area  : %.5f m²%n", aero.getAnnularThroatArea());
System.out.printf("Annular exit area    : %.5f m²%n", aero.getAnnularExitArea());
```

### Step 2 — Evaluate altitude performance

```
double[] ambientPressures = {101_325, 70_000, 40_000, 10_000, 1_000, 0};
AltitudePerformance ap = aero.calculateAltitudePerformance(ambientPressures);

double[] ispValues = ap.aerospikeIsp();
double[] cfValues  = ap.aerospikeCf();
double[] bellRef   = ap.bellNozzleCf(); // equivalent bell at same AR

System.out.printf("%-12s  %-10s  %-10s  %-10s%n",
        "Pa (Pa)", "Aero Cf", "Bell Cf", "Advantage");
for (int i = 0; i < ambientPressures.length; i++) {
    System.out.printf("%-12.0f  %-10.4f  %-10.4f  %+.4f%n",
            ambientPressures[i], cfValues[i], bellRef[i],
            cfValues[i] - bellRef[i]);
}

System.out.printf("Mean Cf advantage    : %.4f%n", ap.averageAltitudeAdvantage());
```

The Aerospike should show a positive Cf advantage at intermediate altitudes
where a bell would be over-expanded or under-expanded. At sea level and vacuum
the advantage narrows.

### Step 3 — Evaluate at a specific condition

```
double cfSeaLevel = aero.calculateThrustCoefficient(101_325);
double ispVacuum  = aero.calculateIsp(0);

System.out.printf("Cf @ sea level       : %.4f%n", cfSeaLevel);
System.out.printf("Isp @ vacuum         : %.1f s%n", ispVacuum);
```

---

## 6. Tutorial 5 — Dual-bell altitude-compensating nozzle

### Goal

Design a dual-bell nozzle and understand its Isp advantage profile vs. a
single-bell equivalent.

### Background

A dual-bell nozzle has two bell sections joined at a kink. At low altitude
(high ambient pressure), the flow separates at the kink and the engine operates
on the short inner bell. At high altitude (low ambient), the flow attaches to
the full extension and the engine operates on the outer bell. This gives a
two-point Isp profile at the cost of a slightly heavier, longer nozzle.

### Step 1 — Define the dual-bell

```
// transitionAreaRatio: A/A* at the kink location
// Rule of thumb: √(exit AR) is a good starting point
double transitionAR = Math.sqrt(params.exitAreaRatio());

DualBellNozzle db = new DualBellNozzle(params, transitionAR).generate();

System.out.printf("Base length          : %.3f m%n",  db.getBaseLength());
System.out.printf("Total length         : %.3f m%n",  db.getTotalLength());
System.out.printf("Kink radius          : %.3f m%n",  db.getTransitionRadius());
System.out.printf("Mach at kink         : %.3f%n",    db.getTransitionMach());
System.out.printf("Inflection angle     : %.2f°%n",
        Math.toDegrees(db.getInflectionAngle()));
```

### Step 2 — Read the performance summary

```
DualBellNozzle.PerformanceSummary s = db.getPerformanceSummary();

System.out.printf("Sea-level Isp        : %.1f s%n",  s.seaLevelIsp());
System.out.printf("High-altitude Isp    : %.1f s%n",  s.highAltitudeIsp());
System.out.printf("Isp gain             : %.1f s%n",  s.ispGain());
System.out.printf("Transition pressure  : %.0f Pa%n", s.transitionPressure());
System.out.printf("Sea-level Cf         : %.4f%n",    s.seaLevelCf());
System.out.printf("High-altitude Cf     : %.4f%n",    s.highAltitudeCf());
```

`transitionPressure()` is the ambient pressure at which the flow switches from
the inner to the outer bell (Summerfield criterion). A typical value is
30–60 kPa, corresponding to altitudes of roughly 8–15 km.

### Step 3 — Export for CFD

Dual-bell nozzles benefit from CFD validation of the kink-region flow and the
mode-switching dynamics. The exporter handles the geometry automatically.

```
new OpenFOAMExporter()
        .setAxialCells(300)
        .setRadialCells(100)
        .setTurbulenceEnabled(true)
        .exportCase(db, java.nio.file.Path.of("dualbell_case"));
```

Run the generated case at two ambient pressures (above and below
`transitionPressure()`) to confirm mode switching in CFD.

---

## 7. Tutorial 6 — Validation and uncertainty analysis

### Goal

Check the design against NASA SP-8120 bounds and quantify the sensitivity of
performance to manufacturing tolerances.

### Step 1 — NASA SP-8120 validation

SP-8120 specifies limits on area-ratio error, thrust-coefficient error, Isp
error, exit Mach number, area ratio, and wall angle. The validator codifies
these checks.

```
NASASP8120Validator v = new NASASP8120Validator(params, net, contour);
NASASP8120Validator.ValidationResult result = v.validate();

if (result.isValid()) {
    System.out.println("All SP-8120 checks passed.");
} else {
    System.out.println("Hard violations:");
    result.errors().forEach(e -> System.out.println("  ERROR: " + e));
}

result.warnings().forEach(w -> System.out.println("  WARN:  " + w));

// Numeric results for reporting
result.metrics().forEach((k, val) ->
        System.out.printf("  %-40s %.4f%n", k, val));
```

Address hard violations (errors) before proceeding. Warnings are advisory —
you may accept them with documented justification.

### Step 2 — Monte Carlo uncertainty analysis

Manufacturing tolerances on throat radius, chamber pressure, and wall angle
translate into scatter in Cf and Isp. Monte Carlo analysis quantifies this
scatter statistically.

```
MonteCarloUncertainty mc = new MonteCarloUncertainty(params, 10_000, 42L);

// Register the full set of typical manufacturing uncertainties in one call:
// ±0.5% throatRadius (1σ normal), ±2% chamberPressure,
// ±3% chamberTemperature, ±2% gamma, ±0.5° wallAngle
mc.addTypicalUncertainties();

// Or register individual uncertainties:
// mc.addNormalParameter("throatRadius",    params.throatRadius(),    0.0002);
// mc.addNormalParameter("chamberPressure", params.chamberPressure(), 70_000);
// mc.addUniformParameter("wallAngle", 28.0, 32.0);

mc.run();

MonteCarloUncertainty.StatisticalSummary s = mc.getSummary();

System.out.printf("Cf  mean=%.4f  1σ=%.5f  P05=%.4f  P95=%.4f%n",
        s.cfStats().mean(),   s.cfStats().stddev(),
        s.cfStats().p05(),    s.cfStats().p95());

System.out.printf("Isp mean=%.1f s  1σ=%.2f s  P05=%.1f s  P95=%.1f s%n",
        s.ispStats().mean(),  s.ispStats().stddev(),
        s.ispStats().p05(),   s.ispStats().p95());

// Identify which parameter drives scatter the most
System.out.println("\nSensitivity (Pearson r vs Cf):");
mc.getSensitivities().entrySet().stream()
        .filter(e -> e.getKey().contains("_Cf_"))
        .sorted(Map.Entry.<String, Double>comparingByValue(
                Comparator.comparingDouble(Math::abs)).reversed())
        .forEach(e -> System.out.printf("  %-40s r=%.4f%n",
                e.getKey(), e.getValue()));
```

The parameter with the largest |r| against Cf drives performance scatter the
most. For typical bell nozzles, throat radius is usually dominant because
thrust scales with throat area (∝ r²).

### Step 3 — Altitude off-design sweep

To understand performance across the operational envelope rather than at a
single design point, use `ShockExpansionModel`:

```
ShockExpansionModel model = new ShockExpansionModel(params);

double[] altitudes_m = {0, 5_000, 10_000, 20_000, 40_000, 80_000};
for (double alt : altitudes_m) {
    // Convert geometric altitude to ISA pressure internally
    double pa = altitudeToPressure(alt); // see note below
    ShockExpansionModel.OffDesignResult r = model.compute(pa);
    System.out.printf("Alt=%6.0f m  Pa=%7.0f Pa  regime=%-18s  Cf=%.4f  Isp=%.1f s%n",
            alt, pa, r.regime(), r.thrustCoefficient(), r.specificImpulse());
}
```

Note: `ShockExpansionModel.compute()` takes ambient pressure in Pa, not altitude
in metres. Use an ISA table or the formula `Pa = 101325 × (1 - 2.2558e-5 × h)^5.2559`
for altitudes below 11 km, or the full ISA stratosphere formulation above.

---

## 8. Tutorial 7 — Saving and reloading design checkpoints

### Goal

Save intermediate results to JSON so that expensive computations (the MOC net,
for example) do not need to be repeated in every session.

### Overview

`DesignDocument` is a checkpoint record that can hold any subset of the solved
state: parameters only, parameters + MOC net, or the fully solved state
including the contour. `NozzleSerializer` writes and reads JSON.

### Step 1 — Save the design intent only

Do this at the beginning of a session before running any solver. It records
your input choices before any computation has been done.

```
DesignDocument doc = DesignDocument.parametersOnly(params);
NozzleSerializer.save(doc, java.nio.file.Path.of("nozzle.json"));

System.out.println("Saved design intent to nozzle.json");
System.out.println("Schema version: " + doc.schemaVersion());
System.out.println("Created at:     " + doc.createdAt());
```

### Step 2 — Load, solve, and save with the MOC net

In a later session (or a separate build pipeline stage), load the saved
parameters, run the MOC solver, and save the extended checkpoint.

```
DesignDocument loaded = NozzleSerializer.load(java.nio.file.Path.of("nozzle.json"));
NozzleDesignParameters params = loaded.parameters();

CharacteristicNet net = new CharacteristicNet(params).generate();
NozzleContour contour = NozzleContour.fromMOCWallPoints(params, net.getWallPoints());
contour.generate(200);

NozzleSerializer.save(
        loaded.withNet(net).withContour(contour),
        java.nio.file.Path.of("nozzle.json"));

System.out.println("MOC solution saved.");
```

### Step 3 — Load the fully solved document

In the analysis or export session, load the checkpoint and skip re-running the
solvers.

```
DesignDocument solved = NozzleSerializer.load(java.nio.file.Path.of("nozzle.json"));

NozzleDesignParameters params  = solved.parameters();
List<CharacteristicPoint> wall = solved.wallPoints();
List<Point2D> contourPts       = solved.contourPoints();

System.out.printf("Parameters loaded. Throat radius: %.3f m%n",
        params.throatRadius());
System.out.printf("Wall points: %d%n",    wall.size());
System.out.printf("Contour pts: %d%n",    contourPts.size());
```

### Serializing to a JSON string

If you need to transmit the design document over HTTP or log it, serialize to
and from a string:

```
String json = NozzleSerializer.toJson(solved);
DesignDocument reconstructed = NozzleSerializer.fromJson(json);
```

---

## 9. Tutorial 8 — CFD and CAD export

### Goal

Export the nozzle geometry to DXF, STEP, STL, and OpenFOAM formats.

### DXF (2D wall profile)

DXF is suitable for import into CAD tools and for manufacturing drawings.
The exporter writes the wall contour and nozzle axis as separate layers.

```
new DXFExporter()
        .setScaleFactor(1000)             // convert metres to mm
        .exportContour(contour, java.nio.file.Path.of("nozzle.dxf"));
```

For an Aerospike nozzle, use `exportAerospikeContour()` instead; it writes
`SPIKE` and `COWL` layers in addition to the axis.

### STEP (3D revolved solid)

STEP is the standard neutral format for solid geometry. The exporter revolves
the 2D wall profile 360° around the nozzle axis to produce a solid of
revolution compliant with ISO 10303-21.

```
new STEPExporter()
        .setScaleFactor(1000)
        .setAuthor("Craig Walters")
        .setOrganization("My Organisation")
        .exportRevolvedSolid(contour, java.nio.file.Path.of("nozzle.step"));
```

### STL (3D triangulated mesh)

STL is used for 3-D printing and visualization. The exporter triangulates the
revolved surface.

```
new STLExporter()
        .setCircumferentialSegments(72)   // 5° per facet; 36 for coarse preview
        .setScaleFactor(1000)
        .setBinaryFormat(true)            // binary is smaller; use false for ASCII
        .exportMesh(contour, java.nio.file.Path.of("nozzle.stl"));
```

### CFD mesh files (blockMesh, Gmsh, Plot3D, CGNS)

`CFDMeshExporter` writes mesh input files only — it does not write boundary
conditions or solver settings. For a complete OpenFOAM case use
`OpenFOAMExporter` (see below).

```
CFDMeshExporter cfd = new CFDMeshExporter()
        .setAxialCells(200)
        .setRadialCells(80)
        .setFirstLayerThickness(1e-5);    // y⁺-controlled wall-normal grading

// OpenFOAM blockMesh input
cfd.export(contour, java.nio.file.Path.of("blockMeshDict"),
        CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);

// Gmsh .geo transfinite structured mesh
cfd.export(contour, java.nio.file.Path.of("nozzle.geo"),
        CFDMeshExporter.Format.GMSH_GEO);

// Plot3D structured grid
cfd.export(contour, java.nio.file.Path.of("nozzle.xyz"),
        CFDMeshExporter.Format.PLOT3D);

// CGNS structured grid
cfd.export(contour, java.nio.file.Path.of("nozzle.cgns"),
        CFDMeshExporter.Format.CGNS);
```

`setFirstLayerThickness()` overrides the expansion ratio and instead derives
the grading from the desired first-cell height. Set it to achieve y⁺ ≈ 1 for
wall-resolved simulations or y⁺ ≈ 30–100 for wall-function simulations.

### Complete OpenFOAM rhoCentralFoam case

`OpenFOAMExporter` writes the full case directory: mesh, boundary conditions,
thermophysical properties, turbulence model settings, and initial fields.

```
java.nio.file.Path caseDir = java.nio.file.Path.of("nozzle_case");

new OpenFOAMExporter()
        .setAxialCells(300)
        .setRadialCells(100)
        .setWedgeAngleDeg(2.5)            // half-angle of axisymmetric wedge
        .setFirstLayerThickness(1e-5)     // y⁺-controlled grading
        .setTurbulenceEnabled(true)       // k-ω SST; false for laminar
        .setTurbulenceIntensity(0.05)     // 5% at inlet
        .exportCase(params, contour, caseDir);
```

The exported case is immediately runnable:

```bash
cd nozzle_case
blockMesh
rhoCentralFoam
```

The `blockMeshDict` embeds the wall contour as a `spline` edge so the mesh
faithfully follows the nozzle wall without faceting. Boundary patches are:
`inlet`, `outlet`, `wall`, `axis` (empty — axisymmetric), `wedge0`, `wedge1`.

Initialisation in `0/p` and `0/T` is set from `params.chamberPressure()` and
`params.chamberTemperature()` for the inlet, and `params.ambientPressure()` for
the outlet. Adjust these and `0/U` to match your specific inflow conditions.

### Exporting RaoNozzle and DualBellNozzle directly

Both `RaoNozzle` and `DualBellNozzle` can be passed directly to
`CFDMeshExporter` and `OpenFOAMExporter` without first wrapping them in a
`NozzleContour`:

```
RaoNozzle rao = new RaoNozzle(params).generate();
cfd.export(rao, java.nio.file.Path.of("blockMeshDict"),
        CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);

new OpenFOAMExporter().exportCase(
        new DualBellNozzle(params, 4.0).generate(),
        java.nio.file.Path.of("dualbell_case"));
```

The exporter internally calls `getParameters()` and reconstructs the geometry
from the nozzle's own contour points, so no information is lost.

### CSV export for post-processing

All major result types can be written to CSV for import into Excel, Python, or
MATLAB:

```
CSVExporter csv = new CSVExporter();

csv.exportContour(contour,        java.nio.file.Path.of("wall.csv"));
csv.exportCharacteristicNet(net,  java.nio.file.Path.of("net.csv"));
csv.exportThermalProfile(htr,     java.nio.file.Path.of("thermal.csv"));
csv.exportBoundaryLayerProfile(bl,java.nio.file.Path.of("bl.csv"));
csv.exportStressProfile(stress,   java.nio.file.Path.of("stress.csv"));
csv.exportDesignParameters(params,java.nio.file.Path.of("params.csv"));

// Aerospike reports
csv.exportSpikeContour(aero,              java.nio.file.Path.of("spike.csv"));
csv.exportAltitudePerformance(altPerf,    java.nio.file.Path.of("altitude.csv"));
csv.exportAerospikeReport(aero, ambientPressures, java.nio.file.Path.of("report/"));
```
