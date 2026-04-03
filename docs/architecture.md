# Architecture

## Purpose

`lib_java_rocket_nozzle` is a pure-Java library for the aerodynamic design of
supersonic rocket nozzles. It implements the Method of Characteristics (MOC) as
its primary flow solver and wraps it with chemistry, thermal, structural,
performance, and export subsystems. All subsystems are decoupled so they can be
used individually or composed into a full design pipeline.

---

## Package Map

```
com.nozzle
├── core          NozzleDesignParameters, GasProperties, PerformanceCalculator,
│                 FlowSeparationPredictor, ShockExpansionModel
├── moc           CharacteristicNet, RaoNozzle, DualBellNozzle,
│                 AerospikeNozzle, AerospikeContour, CharacteristicPoint,
│                 AltitudePerformance
├── geometry      NozzleContour (five contour families), Point2D
├── chemistry     ChemistryModel, OFSweep, GibbsMinimizer,
│                 NasaSpeciesDatabase, SpeciesData, PropellantComposition
├── thermal       HeatTransferModel, BoundaryLayerCorrection, CoolantChannel,
│                 ThermalStressAnalysis, AblativeNozzleModel,
│                 RadiationCooledExtension
├── export        CFDMeshExporter, OpenFOAMExporter, CSVExporter, DXFExporter,
│                 STEPExporter, STLExporter, RevolvedMeshExporter
├── optimization  AltitudeAdaptiveOptimizer, MonteCarloUncertainty
├── validation    NASASP8120Validator
└── io            NozzleSerializer, DesignDocument
```

---

## Design Principles

**Immutable design intent.** `NozzleDesignParameters` is a Java record built
with a builder. It cannot be mutated after construction. Every downstream
calculation takes a snapshot of the design intent; changing a parameter always
creates a new instance.

**Explicit generate/calculate lifecycle.** Solver objects (CharacteristicNet,
RaoNozzle, HeatTransferModel, etc.) separate construction from computation.
The constructor captures configuration; `generate()` or `calculate()` performs
the work and returns `this` for chaining. Results are empty/zero until the
computation method is called.

**No shared mutable state.** `OFSweep.computeAt()` creates a fresh
`ChemistryModel` on every call. `MonteCarloUncertainty` uses virtual threads
with no shared mutable objects between samples. Exporters are stateless except
for configuration fields set before the export call.

**Records throughout.** Result types — `CharacteristicPoint`, `OFPoint`,
`AltitudePerformance`, `OffDesignResult`, `ValidationResult`, `PerformanceSummary`,
`WallThermalPoint` — are Java records. This makes them safe to cache, compare,
and pass across thread boundaries.

**Pure SI internally.** Every quantity is stored and returned in SI base units
(metres, Pascals, Kelvin, Newtons, kg). Unit conversion is the caller's
responsibility at system boundaries.

**Jakarta Validation at boundaries.** `NozzleDesignParameters` uses constraint
annotations enforced by Hibernate Validator. Parameter violations are reported as
`ConstraintViolationException` before any solver code runs.

---

## The Full Design Pipeline

```
OFSweep                              (optional: find optimum Tc from O/F curve)
    │
    ▼
NozzleDesignParameters               (immutable design intent)
    │
    ├──► CharacteristicNet.generate() ──► List<List<CharacteristicPoint>>
    │        │                               (full MOC grid, wall points)
    │        ▼
    │    NozzleContour.fromMOCWallPoints()   (MOC_GENERATED spline)
    │        │
    │        ▼
    │    BoundaryLayerCorrection.calculate() ──► List<BoundaryLayerPoint>
    │        │
    │        ▼
    │    HeatTransferModel.calculate()       ──► List<WallThermalPoint>
    │        │
    │        ▼
    │    ChemistryModel.calculateEquilibrium()
    │        │
    │        ▼
    │    PerformanceCalculator.calculate()   ──► PerformanceSummary
    │
    ├──► NASASP8120Validator.validate()      ──► ValidationResult
    │
    ├──► NozzleSerializer.save()             ──► JSON checkpoint
    │
    └──► [Exporters]
             DXFExporter, STEPExporter, STLExporter
             CFDMeshExporter, OpenFOAMExporter
             CSVExporter
```

Each stage is optional. A caller may use only the MOC solver, only the chemistry
model, or run the complete chain. The `DesignDocument` record captures an
arbitrary checkpoint: parameters only, parameters + MOC net, or the full solved
state including contour.

---

## Component Dependencies

```
NozzleDesignParameters
  └─ used by everything

GasProperties
  └─ used by NozzleDesignParameters, CharacteristicNet, RaoNozzle,
            GibbsMinimizer, OFSweep, PerformanceCalculator,
            FlowSeparationPredictor, ShockExpansionModel

CharacteristicNet
  └─ depends on NozzleDesignParameters
  └─ produces CharacteristicPoint grid → feeds NozzleContour, BoundaryLayerCorrection

NozzleContour
  └─ depends on NozzleDesignParameters (all types)
  └─ depends on CharacteristicNet wall points (MOC_GENERATED)
  └─ used by PerformanceCalculator, BoundaryLayerCorrection, HeatTransferModel,
            all exporters

GibbsMinimizer
  └─ depends on NasaSpeciesDatabase, PropellantComposition
  └─ called by ChemistryModel.calculateEquilibrium()

ChemistryModel
  └─ wraps GibbsMinimizer
  └─ used by PerformanceCalculator, OFSweep

PerformanceCalculator
  └─ depends on NozzleDesignParameters, CharacteristicNet (optional),
              NozzleContour (optional), BoundaryLayerCorrection (optional),
              ChemistryModel (optional)

AltitudeAdaptiveOptimizer
  └─ depends on NozzleDesignParameters, ShockExpansionModel, PerformanceCalculator
  └─ runs candidates in parallel via virtual threads

MonteCarloUncertainty
  └─ depends on NozzleDesignParameters
  └─ runs samples in parallel via virtual threads
```

---

## Parallelism Model

Two classes use Java virtual threads:

**AltitudeAdaptiveOptimizer** — evaluates each parameter combination (up to 175
candidates in fine mode) as an independent task. Each task constructs its own
`ShockExpansionModel` and `PerformanceCalculator`. No shared mutable state.

**MonteCarloUncertainty** — evaluates each Monte Carlo sample (default 10,000)
as an independent task. Each task constructs its own `NozzleDesignParameters` and
`PerformanceCalculator`. Results are collected into a thread-safe list, then
statistics are computed serially on the collected array.

Both use `Executors.newVirtualThreadPerTaskExecutor()` and `Future.get()` for
result collection. Neither class is itself thread-safe — create one instance per
logical analysis.

---

## Coordinate System

All geometry uses a right-handed coordinate system with:

- **x** — axial direction, increasing toward the exit, origin at the throat
  center (x = 0 at the throat plane)
- **y** (or **r**) — radial direction, origin at the nozzle axis
- **z** — third axis, unused in 2-D analyses (present only in 3-D exports)

Angles (θ, flow angle; μ, Mach angle; ν, Prandtl-Meyer function) are in radians
internally. Display helpers in `CharacteristicPoint` return degrees.

---

## Unit System

| Quantity           | Unit                   |
|--------------------|------------------------|
| Length             | metre (m)              |
| Pressure           | Pascal (Pa)            |
| Temperature        | Kelvin (K)             |
| Force              | Newton (N)             |
| Mass               | kilogram (kg)          |
| Mass flow          | kg/s                   |
| Specific impulse   | seconds (s) — = N·s/kg |
| Molecular weight   | kg/kmol                |
| Enthalpy, entropy  | J/kg, J/(kg·K)         |
| Angle (internal)   | radians                |
| Area ratio         | dimensionless          |
| Thrust coefficient | dimensionless          |
