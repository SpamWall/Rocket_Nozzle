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
│                 FlowSeparationPredictor, ShockExpansionModel, Units
├── moc           CharacteristicNet, RaoNozzle, DualBellNozzle,
│                 AerospikeNozzle, AerospikeContour, CharacteristicPoint,
│                 AltitudePerformance
├── geometry      NozzleContour (five contour families), ConvergentSection,
│                 FullNozzleGeometry, Point2D
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
(metres, Pascals, Kelvin, Newtons, kg). The `Units` class in `com.nozzle.core`
provides static conversion methods for use at system input/output boundaries
(inches, psi, Rankine, lbf, BTU, etc.).

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
    │    FullNozzleGeometry.fromMOC()        (chamber face → exit wall profile)
    │        │   (combines ConvergentSection + NozzleContour)
    │        │
    │        ├──► BoundaryLayerCorrection.calculate()
    │        │    or .calculateFromInjectorFace()   ──► List<BoundaryLayerPoint>
    │        │        (injector-face mode gives more accurate throat δ*)
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
             DXFExporter (exportContour, exportFullNozzleProfile,
                          exportFullNozzleRevolutionProfile, exportAerospikeContour)
             STEPExporter, STLExporter
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

Units
  └─ no dependencies; pure static conversion utilities
  └─ used at system input/output boundaries only

CharacteristicNet
  └─ depends on NozzleDesignParameters
  └─ produces CharacteristicPoint grid → feeds NozzleContour, BoundaryLayerCorrection

NozzleContour
  └─ depends on NozzleDesignParameters (all types)
  └─ depends on CharacteristicNet wall points (MOC_GENERATED)
  └─ used by PerformanceCalculator, BoundaryLayerCorrection, HeatTransferModel,
            divergent-only exporters

ConvergentSection
  └─ depends on NozzleDesignParameters
  └─ generates wall contour from injector face → throat (x < 0)
  └─ used by FullNozzleGeometry

FullNozzleGeometry
  └─ depends on NozzleDesignParameters, ConvergentSection, NozzleContour
  └─ assembles complete wall: injector face → exit (x_min < 0 → x_exit)
  └─ used by BoundaryLayerCorrection.calculateFromInjectorFace(),
            DXFExporter.exportFullNozzleProfile(),
            DXFExporter.exportFullNozzleRevolutionProfile()

BoundaryLayerCorrection
  └─ depends on NozzleDesignParameters, NozzleContour or CharacteristicNet
  └─ .calculate() — BL starts at throat
  └─ .calculateFromInjectorFace(FullNozzleGeometry) — BL starts at chamber face
  └─ used by PerformanceCalculator, HeatTransferModel

HeatTransferModel
  └─ depends on NozzleDesignParameters, NozzleContour
  └─ .calculate(flowPoints) — Bartz/Eckert over divergent section (x ≥ 0)
  └─ .calculateFullProfile(FullNozzleGeometry, flowPoints) — full wall (x_min → x_exit);
     convergent points use isentropic area-Mach Mach estimation
  └─ curvature correction uses parametric r_cd / r_cu in the throat arc zones
     rather than finite differences, making peak location sensitive to both ratios
  └─ .getPeakFluxPoint() / .getPeakFluxX() — heat-flux peak location
  └─ used by ThermalStressAnalysis, CoolantChannel, PerformanceCalculator

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

Use `com.nozzle.core.Units` to convert between SI and imperial (inches, psi,
Rankine, lbf, BTU, lb/ft·s, etc.) at system input/output boundaries.
