# Troubleshooting and FAQ

Organised by symptom. Each entry describes the root cause and the fix.

---

## Table of Contents

1. [Setup and construction errors](#1-setup-and-construction-errors)
2. [MOC solver problems](#2-moc-solver-problems)
3. [Chemistry and O/F sweep problems](#3-chemistry-and-of-sweep-problems)
4. [Performance results that look wrong](#4-performance-results-that-look-wrong)
5. [Thermal analysis problems](#5-thermal-analysis-problems)
6. [Serialization and I/O errors](#6-serialization-and-io-errors)
7. [Export problems](#7-export-problems)
8. [Optimization and Monte Carlo problems](#8-optimization-and-monte-carlo-problems)
9. [General FAQ](#9-general-faq)

---

## 1. Setup and construction errors

### `ConstraintViolationException` thrown from `NozzleDesignParameters.builder().build()`

**Cause:** One or more field values violate the Jakarta Validation constraints
on `NozzleDesignParameters`. Common mistakes:

| Field                     | Constraint | Typical mistake                                        |
|---------------------------|------------|--------------------------------------------------------|
| `throatRadius`            | > 0        | Passed in mm instead of metres                         |
| `exitMach`                | > 1.0      | Passed a subsonic Mach number                          |
| `chamberPressure`         | > 0        | Passed in MPa instead of Pa                            |
| `chamberTemperature`      | > 0        | Left at default 0 or passed in °C                      |
| `ambientPressure`         | > 0        | Passed 0 (vacuum); use a small value like 1 Pa instead |
| `numberOfCharLines`       | ≥ 5        | Passed too small a value for a quick test              |
| `wallAngleInitialDegrees` | 0 < θ ≤ 45 | Passed in radians (~0.52) instead of degrees           |
| `lengthFraction`          | 0 < lf ≤ 1 | Passed a percentage (80) instead of a fraction (0.8)   |

**Fix:** Read the exception message — it names the violated constraint and the
rejected value. The most common root cause is a units' error. Remember: every
input is SI (metres, Pascals, Kelvin).

---

### `IllegalArgumentException` from `DualBellNozzle`

**Cause:** `transitionAreaRatio` is outside the physically valid range. It must
satisfy `1 < transitionAR < exitAreaRatio`. If it equals or exceeds the exit
area ratio there is no room for the extension bell; if it is ≤ 1 it is in the
subsonic section.

**Fix:**

```
double exitAR = params.exitAreaRatio();   // derived from exitMach
double transAR = Math.sqrt(exitAR);       // typical starting point: geometric mean

// Clamp to a safe range
transAR = Math.max(1.5, Math.min(transAR, exitAR * 0.85));
DualBellNozzle db = new DualBellNozzle(params, transAR).generate();
```

---

### `IllegalArgumentException`: "firstLayerThickness must be positive"

**Cause:** `CFDMeshExporter.setFirstLayerThickness()` or
`OpenFOAMExporter.setFirstLayerThickness()` was called with zero or a negative
value.

**Fix:** If you do not want y⁺-controlled grading, simply do not call
`setFirstLayerThickness()`. The exporter will fall back to a uniform expansion
ratio.

---

### `NozzleContour.getContourPoints()` returns an empty list

**Cause:** `generate()` was never called on the contour.

**Fix:** Always call `generate(n)` before querying the contour:

```
NozzleContour contour = NozzleContour.fromMOCWallPoints(params, net.getWallPoints());
contour.generate(200);   // <-- required before any get*() call
```

This is by design — the library separates construction (captures configuration)
from computation (performs the work). See the architecture document for the
rationale.

---

## 2. MOC solver problems

### WARN log: "CharacteristicNet convergence failure" — wall points are incomplete

**Cause:** The predictor-corrector iteration for the axisymmetric correction
failed to converge within the allowed iteration limit (default 100) at one or
more interior points. This usually means the solver is trying to propagate
characteristics through a region where the flow angle changes faster than the
grid can resolve.

**Fixes, in order of preference:**

1. Increase `numberOfCharLines`. The convergence tolerance is fixed; more lines
   mean smaller steps and better-conditioned corrections. Try doubling the value.

2. Reduce `wallAngleInitialDegrees`. Very large initial wall angles (> 35°)
   create steep gradients near the throat that are hard to resolve with a coarse
   grid.

3. Reduce `exitMach`. Very high Mach numbers (> 5) require many characteristic
   lines to resolve the large Prandtl-Meyer turning angle.

4. Tighten the convergence tolerance by using the three-argument constructor:

```
// Default tolerance is 1e-8; try 1e-10 for better accuracy,
// or 1e-6 to accept a coarser solution when speed matters
CharacteristicNet net = new CharacteristicNet(params, 1e-10, 500).generate();
```

---

### `net.getWallPoints()` contains fewer points than expected

**Cause:** One of two things: (a) a convergence failure caused early termination
(see above), or (b) `numberOfCharLines` is very small (< 10), which limits how
many wall intercepts can be generated.

**Fix:** Call `net.validate()` first. It returns a list of diagnostic strings
that describe any inconsistencies between the solver result and the design
intent:

```
List<String> issues = net.validate();
if (!issues.isEmpty()) {
    issues.forEach(System.err::println);
}
```

If the list is empty but point count is still lower than expected, increase
`numberOfCharLines`.

---

### `net.calculateExitAreaRatio()` differs significantly from `params.exitAreaRatio()`

**Cause:** The MOC solver's computed exit area ratio depends on how many
characteristic lines are used and how well they capture the turning. With a
coarse grid (few characteristic lines) the computed A/A* can differ from the
isentropic target by 1–3%.

**Fix:** Increase `numberOfCharLines` until the difference falls below your
acceptable threshold. For most engineering purposes a difference below 0.5%
is adequate. The `NASASP8120Validator` checks this and will report it as an
error if the deviation exceeds 1%.

---

## 3. Chemistry and O/F sweep problems

### `ChemistryModel.calculateEquilibrium()` produces species with negative mass fractions or NaN

**Cause:** The Gibbs free energy minimizer diverged. This can happen when:

- A species is included in the solver whose constituent elements are not present
  in the propellant mixture (for example, CO₂ in a LOX/LH₂ system contains
  carbon, which is absent). Such species grow unbounded and corrupt the element
  balance.
- The initial guess for the species distribution is too far from the equilibrium
  solution, which happens at extreme conditions (very low Tc, very high or very
  low Pc).

**Fix:** Make sure you call the correct composition setter for your propellant
*before* calling `calculateEquilibrium()`:

```
ChemistryModel eq = ChemistryModel.equilibrium(GasProperties.LOX_LH2_PRODUCTS);
eq.setLoxLh2Composition();          // <-- must match the gas properties
eq.calculateEquilibrium(3000, 7e6);
```

Mixing a `LOX_RP1_PRODUCTS` gas with `setLoxLh2Composition()`, or vice versa,
is the most common cause of divergence. The gas properties determine which
species database entries are loaded; the composition setter initializes the
element balance. They must be consistent.

---

### `OFSweep.optimumIsp()` returns an O/F value at the boundary of the search range

**Cause:** The true optimum lies outside the range you supplied. For LOX/RP-1
the Isp optimum is typically near O/F = 2.3–2.7; for LOX/LH₂ it is near 4–5;
for LOX/CH₄ near 3–3.5.

**Fix:** Widen the search range, or first call `sweep()` to tabulate the curve
and visually confirm where the peak lies:

```
List<OFSweep.OFPoint> curve = sweep.sweep(1.0, 6.0, 50);
curve.forEach(p -> System.out.printf("OF=%.2f  Isp=%.1f%n", p.of(), p.isp()));
```

If the printed Isp values are monotonically increasing or decreasing across the
entire range, the optimum is outside that range.

---

### `OFSweep` produces unrealistically high Tc (> 4500 K)

**Cause:** The adiabatic flame temperature calculation has been given a
propellant combination or O/F ratio that does not match any of the built-in
combustion models. The solver may be extrapolating.

**Fix:** Use only the built-in `OFSweep.Propellant` values with their
corresponding `GasProperties` constants. Custom propellants require a
`GasProperties` instance constructed with your measured γ and molecular weight —
the library does not currently model arbitrary propellant thermochemistry from
first principles.

---

### `ChemistryModel.compareIsp()` returns a frozen Isp that is *higher* than equilibrium

**Cause:** This should not happen physically (equilibrium always ≥ frozen) and
indicates a setup error. Most commonly: the equilibrium model was not converged
before `compareIsp()` was called, so it used the unconverged (wrong) species
distribution.

**Fix:** Always call `calculateEquilibrium()` before `compareIsp()`:

```
eq.setLoxRp1Composition(2.4);
eq.calculateEquilibrium(Tc, Pc);    // <-- must come first
ChemistryModel.IspComparison cmp = eq.compareIsp(Tc, Pc, Me, Pa);
```

---

## 4. Performance results that look wrong

### `getEfficiency()` returns a value greater than 1.0

**Cause:** The actual thrust coefficient exceeded the ideal value. This should
not be physically possible and indicates a calculation error, almost always a
units mismatch. Common sources:

- `chamberPressure` was supplied in MPa instead of Pa (e.g. `7` instead of
  `7e6`). The ideal Cf formula uses Pc; if Pc is 10⁶× too small, Cf is
  computed against a far lower baseline and the "actual" value appears larger.
- `ambientPressure` was supplied in kPa or bar instead of Pa.

**Fix:** Verify units. Print the raw values to confirm:

```
System.out.printf("Pc = %.0f Pa (%.2f MPa)%n",
        params.chamberPressure(), params.chamberPressure() / 1e6);
System.out.printf("Pa = %.0f Pa%n", params.ambientPressure());
System.out.printf("Ideal Cf = %.4f%n", perf.getIdealThrustCoefficient());
System.out.printf("Actual Cf = %.4f%n", perf.getActualThrustCoefficient());
```

---

### Isp is far below expected (e.g. 50 s instead of 300 s)

**Cause:** Almost always a units error in one of the gas properties.
`GasProperties` requires molecular weight in kg/kmol. If you constructed a
custom instance and accidentally supplied kg/mol (22 instead of 22000 for N₂),
the gas constant R is 1000× too large, which inflates the speed of sound and
distorts all derived quantities.

**Fix:** Use the built-in `GasProperties` constants wherever possible. If you
must construct a custom instance, double-check that molecular weight is in
kg/kmol (the same convention as NASA-7 polynomial data).

---

### Isp from `PerformanceCalculator` is higher than the `OFSweep` optimum

**Cause:** `OFSweep` uses equilibrium chemistry to compute Isp; if you pass
`null` for the chemistry model to `PerformanceCalculator` it defaults to ideal
(no chemistry loss), which can produce a slightly higher number.

This is expected — the two are computing different things. `OFSweep` gives the
chemically limited equilibrium Isp; `PerformanceCalculator` with `chem=null`
gives the ideal isentropic Isp. Always use a consistent chemistry model in both
if you are comparing the numbers.

---

### Divergence loss is reported as zero

**Cause:** `PerformanceCalculator` was constructed without the MOC `net`
argument (or `net` was passed as `null`). The divergence loss calculation
requires the exit flow-angle distribution from the characteristic net.

**Fix:**

```
// Wrong — net is null, divergence loss defaults to zero
PerformanceCalculator pc = new PerformanceCalculator(params, null, contour, bl, chem);

// Correct
PerformanceCalculator pc = new PerformanceCalculator(params, net, contour, bl, chem);
```

If you genuinely only have a `NozzleContour` and no `CharacteristicNet`, use
the simple mode and accept that the divergence loss will not be computed:

```
PerformanceCalculator pc = PerformanceCalculator.simple(params).calculate();
```

---

### `FlowSeparationPredictor` reports `FSS` at the design altitude

**Cause:** The nozzle is over-expanded at the ambient pressure supplied in
`params.ambientPressure()`. The design exit Mach is too high for the nozzle to
flow full at that pressure.

This is a genuine physical result, not a bug. The nozzle will separate in
service at that condition. Options:

1. Reduce `exitMach` to lower the area ratio and raise the design exit pressure.
2. Raise the design `ambientPressure` to reflect a higher operating altitude.
3. Switch to a dual-bell or Aerospike nozzle, which tolerate a wider ambient
   pressure range.

---

## 5. Thermal analysis problems

### `HeatTransferModel.getPeakWallTemperature()` is unrealistically high (> 3000 K)

**Cause:** The wall conductivity or thickness was not set, so the model used its
defaults, which may not match your material. The wall temperature depends
strongly on `setWallProperties(conductivity, thickness)`.

**Fix:** Always set wall properties explicitly:

```
HeatTransferModel htr = new HeatTransferModel(params, contour)
        .setWallProperties(390, 0.002)    // copper alloy: 390 W/(m·K), 2 mm
        .setEmissivity(0.6)
        .calculate();
```

Typical conductivities: copper alloy ~380–400 W/(m·K); Inconel 718 ~11 W/(m·K);
stainless 304 ~16 W/(m·K). A low-conductivity wall (stainless) will show a
much higher hot-gas-side temperature than a high-conductivity wall (copper) for
the same heat flux.

---

### `ThermalStressAnalysis.getSafetyFactor()` returns a value below 1.0

**Cause:** The computed von Mises stress exceeds the material yield strength at
the predicted peak wall temperature. This is a genuine design violation.

Common causes:
- The chamber pressure is very high (> 10 MPa) and the wall is thin.
- The peak wall temperature has driven the material well into its creep regime,
  where the effective yield strength is much lower than the room-temperature
  value.

**Fix:** Try one or more of:
- Increase wall thickness (`setWallProperties(k, thicker)`).
- Switch to a higher-strength material (`CU_CR_ZR` → `INCONEL_718`).
- Add a coolant side to the heat transfer model to reduce the wall temperature
  (see `CoolantChannel`).
- Accept a lower chamber pressure.

---

### `CoolantChannel.getOutletTemperature()` exceeds the coolant decomposition limit

**Cause:** The coolant is absorbing more heat than it can carry without coking
(RP-1) or decomposing (liquid methane). This means the channel geometry is
inadequate for the heat load.

**Fix:** The `CoolantChannel` API currently models a single equivalent channel.
To represent multiple parallel channels (the typical engine design), divide the
heat load accordingly — in practice this means reducing `params.throatRadius()`
by the square root of the number of channels when sizing, or post-processing the
outlet temperature by assuming the flow is split across N channels.

---

## 6. Serialization and I/O errors

### `JsonProcessingException` on `NozzleSerializer.load()`

**Cause:** The JSON file is malformed, was written by an incompatible version of
the library, or the schema version field is missing.

**Fix:**
1. Check that the file was written by `NozzleSerializer.save()` in this library.
   External JSON files are not compatible unless they follow the exact schema.
2. Check the `"schemaVersion"` field in the file. The current library expects
   `"1.0"`. Files from a different version may need manual migration.
3. If the file was partially written (e.g. the process was killed during
   `save()`), it will be truncated and invalid. Keep a backup before overwriting
   a checkpoint with a new `save()` call.

---

### `NozzleSerializer.load()` succeeds but `solved.wallPoints()` is empty

**Cause:** The document was saved at the `parametersOnly` stage, before the MOC
solver was run. The `wallPoints`, `netPoints`, and `contourPoints` fields are
only present after `withNet()` and `withContour()` are called.

**Fix:** Check which stage of the pipeline was saved:

```
DesignDocument doc = NozzleSerializer.load(path);

boolean hasNet     = doc.wallPoints() != null && !doc.wallPoints().isEmpty();
boolean hasContour = doc.contourPoints() != null && !doc.contourPoints().isEmpty();

System.out.printf("Has MOC net   : %b%n", hasNet);
System.out.printf("Has contour   : %b%n", hasContour);
```

If the net is missing, run the MOC solver and save again with `withNet()`.

---

## 7. Export problems

### DXF file opens in CAD but has no visible geometry

**Cause:** The contour points are in metres and the CAD tool is interpreting
them as millimetres (or vice versa). A 50 mm throat radius becomes 0.05 units,
which is invisible at the default zoom level.

**Fix:** Set the scale factor to match your CAD tool's unit convention:

```
new DXFExporter()
        .setScaleFactor(1000)      // metres → mm; use 100 for cm, 39.37 for inches
        .exportContour(contour, path);
```

Alternatively, set scale factor to 1 and change the units in your CAD tool.

---

### OpenFOAM `blockMesh` fails with "face 0 vertices not coplanar"

**Cause:** The wedge angle is too large. The axisymmetric wedge approximation
breaks down for wedge half-angles above ~5°. `OpenFOAMExporter` defaults to
2.5°; do not increase it significantly.

**Fix:** Use the default wedge angle or reduce it:

```
new OpenFOAMExporter()
        .setWedgeAngleDeg(2.5)    // default; do not exceed 5°
        ...
```

---

### OpenFOAM `rhoCentralFoam` diverges immediately

**Cause:** The initial conditions in `0/p`, `0/T`, and `0/U` are inconsistent
with the mesh. The exporter initializes with chamber conditions at the inlet
and ambient at the outlet. If the solver tries to resolve a Mach 3+ flow from
a uniform initial field in one step, it will diverge.

**Fix:** Before running `rhoCentralFoam`, run one of:

1. Use `potentialFoam` to generate a smooth initial velocity field:
   ```bash
   potentialFoam -initialiseUBCs
   ```

2. Or ramp the inlet pressure from ambient to chamber over the first 0.1 ms by
   editing `0/p` to use a `uniformFixedValue` with a `tableFile` time-series.

Also check that the Courant number in `system/controlDict` is set to 0.3 or
lower for `rhoCentralFoam`.

---

### STEP file is rejected by CAD software as invalid

**Cause:** Some CAD tools are strict about the `ORGANIZATION` and `AUTHOR`
fields in the STEP header. Leaving them at the default placeholder values
triggers rejection in certain importers.

**Fix:** Always set `setAuthor()` and `setOrganization()` before exporting:

```
new STEPExporter()
        .setScaleFactor(1000)
        .setAuthor("Craig Walters")
        .setOrganization("My Organisation")
        .exportRevolvedSolid(contour, path);
```

---

### STL file has a very large file size

**Cause:** `setCircumferentialSegments()` controls how many triangulated facets
are generated around the axis. The default (72 segments = 5° per facet) produces
a moderately large file for a long nozzle. For a rough preview 36 segments is
adequate; for manufacturing-quality mesh 180 segments may be needed.

**Fix:** Choose segments based on purpose:

| Use case              | Segments | Facet angle |
|-----------------------|----------|-------------|
| Preview / 3-D print   | 36       | 10°         |
| Standard export       | 72       | 5°          |
| High-fidelity CAD/CAM | 180      | 2°          |

---

## 8. Optimization and Monte Carlo problems

### `AltitudeAdaptiveOptimizer.optimize()` returns a result identical to the input parameters

**Cause:** No altitude conditions were registered before calling `optimize()`.
Without conditions the optimizer has no objective function to maximize and
returns the baseline.

**Fix:** Register at least two altitude conditions (or call `addStandardProfile()`):

```
opt.addAltitudeCondition(0,      1.0, 30)
   .addAltitudeCondition(20_000, 1.0, 120)
   .addAltitudeCondition(80_000, 0.5, 60);
opt.optimize();
```

---

### `MonteCarloUncertainty.run()` is very slow

**Cause:** The default sample count is 10,000. Each sample constructs and runs
a `PerformanceCalculator`, which in turn re-runs the MOC solver. At 10,000
samples this can take tens of seconds on a single-core machine.

**Fix:** The samples run on Java virtual threads and scale well with CPU cores.
Ensure you are running on a machine with multiple cores and that the JVM is not
artificially constrained. Alternatively, reduce the sample count for rapid
exploration:

```
MonteCarloUncertainty mc = new MonteCarloUncertainty(params, 1_000, 42L);
mc.addTypicalUncertainties();
mc.run();
// Statistics are less precise but adequate for sensitivity ranking
```

For production uncertainty budgets, use 10,000 or more samples.

---

### Monte Carlo `cfStats().stddev()` is zero

**Cause:** No uncertainty parameters were registered. If `addTypicalUncertainties()`
and no `addNormalParameter()` / `addUniformParameter()` calls were made, all
samples use identical parameters and the standard deviation is exactly zero.

**Fix:** Always register at least one uncertainty source before calling `run()`:

```
mc.addTypicalUncertainties();   // registers five typical tolerances
mc.run();
```

---

## 9. General FAQ

### Which nozzle type should I use?

| Application                                  | Recommended type                                     |
|----------------------------------------------|------------------------------------------------------|
| Single-stage rocket, fixed altitude          | MOC bell (via `CharacteristicNet`)                   |
| Quick estimate without running the full MOC  | Rao bell (`RaoNozzle`)                               |
| Multi-stage rocket, optimize one stage       | MOC bell optimised for that stage's altitude         |
| Single-stage-to-orbit or wide altitude range | Aerospike or dual-bell                               |
| Short burn (< 60 s), expendable              | Ablative-lined conical or Rao bell                   |
| Upper-stage vacuum nozzle                    | High area-ratio MOC bell, radiation-cooled extension |

### How many characteristic lines do I need?

For most engineering analyses 30–50 lines gives well-converged results (wall
contour and exit area ratio accurate to 0.1–0.5%). Increasing beyond 100 lines
gives diminishing returns and slows the solver quadratically (O(n²) interior
points). Use 20 lines for rapid trade studies and 50–80 lines for final design
verification.

### Why does the library use SI everywhere?

Aerospace engineering databases (NASA-7 polynomials, material properties,
atmospheric models) are all tabulated in SI. Using a single unit system
internally eliminates the conversion errors that are a common source of bugs in
mixed-unit codebases. The README and architecture document both note that unit
conversion at system boundaries is the caller's responsibility.

### Can I use a custom propellant not in the built-in list?

For the chemistry model (`ChemistryModel`, `OFSweep`): not directly. The Gibbs
minimizer is parameterized on the built-in `OFSweep.Propellant` enum and the
corresponding `GasProperties` constants. You can approximate a custom propellant
by constructing a `GasProperties` instance with measured γ, molecular weight,
and viscosity, and passing it to `NozzleDesignParameters`. This gives correct
isentropic flow calculations but does not model the combustion chemistry.

For equilibrium chemistry you would need to add species data to
`NasaSpeciesDatabase` and a new composition setter to `ChemistryModel`.

### Why do `PerformanceCalculator.simple()` and the full constructor give different Isp?

`simple()` computes ideal isentropic performance from `NozzleDesignParameters`
only — no loss mechanisms. The full constructor applies divergence, boundary
layer, and chemistry losses when the corresponding inputs are provided. The
difference is the total loss, typically 2–5% for a well-designed nozzle.

### The NASA SP-8120 validator reports a wall angle warning. Is this serious?

The SP-8120 guideline recommends a wall angle ≤ 35° at the throat. Exceeding
this does not mean the nozzle will not work — it means the flow turning is more
aggressive than the guideline assumes, which can lead to stronger oblique shocks
near the throat and a less uniform exit profile. For a first design it is
advisory; for a flight design it warrants MOC validation and possibly CFD
confirmation.

### Can I run multiple designs in parallel?

Yes, as long as each design uses its own set of instances. `NozzleDesignParameters`
and `GasProperties` are immutable records and can be shared safely. All solver
classes (`CharacteristicNet`, `ChemistryModel`, `HeatTransferModel`, etc.) are
not thread-safe and must not be shared across threads. Construct one complete
pipeline per thread. See the threading section of `api-guide.md` for details.
