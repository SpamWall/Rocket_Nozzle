# Input Selection Guide

How to choose values for every configurable input in the library. Each section
explains the physical meaning of the parameter, gives typical ranges, describes
the trade-offs, and flags common mistakes.

The API reference (`api-guide.md`) documents *what* each parameter does.
This guide documents *how to choose it*.

---

## Table of Contents

1. [NozzleDesignParameters](#1-nozzledesignparameters)
   - [throatRadius](#throatradius)
   - [chamberPressure](#chamberpressure)
   - [chamberTemperature](#chambertemperature)
   - [exitMach](#exitmach)
   - [ambientPressure](#ambientpressure)
   - [gasProperties](#gasproperties)
   - [numberOfCharLines](#numberofcharlines)
   - [wallAngleInitialDegrees](#wallangleinitialdegrees)
   - [lengthFraction](#lengthfraction)
   - [axisymmetric](#axisymmetric)
   - [throatCurvatureRatio](#throatcurvatureratio)
2. [Nozzle type selection](#2-nozzle-type-selection)
3. [AerospikeNozzle parameters](#3-aerospikenozzle-parameters)
4. [DualBellNozzle parameters](#4-dualbellnozzle-parameters)
5. [Chemistry and O/F ratio](#5-chemistry-and-of-ratio)
6. [Thermal analysis inputs](#6-thermal-analysis-inputs)
7. [CFD export settings](#7-cfd-export-settings)
8. [Uncertainty analysis inputs](#8-uncertainty-analysis-inputs)

---

## 1. NozzleDesignParameters

### throatRadius

**What it controls:** The physical scale of the engine. Every linear dimension
in the output (contour length, exit radius, spike length) scales with throat
radius. Performance coefficients (Cf, Isp, c*) do not scale with throat radius
— they are dimensionless or mass-specific.

**How to choose it:** Work backwards from the required thrust:

```
F = Cf · Pc · A_throat
A_throat = π · r_throat²

r_throat = sqrt(F / (Cf · Pc · π))
```

Use the ideal Cf as a first estimate (`params.idealThrustCoefficient()` after
building a trial set of parameters). Cf for a well-designed sea-level nozzle is
typically 1.5–1.8.

**Example:** 10 kN thrust at 7 MPa chamber pressure, Cf ≈ 1.65:

```
A* = 10_000 / (1.65 × 7e6) = 8.66 × 10⁻⁴ m²
r* = sqrt(8.66e-4 / π) ≈ 0.0166 m  (16.6 mm)
```

**Typical ranges:**

| Engine class                | Throat radius |
|-----------------------------|---------------|
| Small experimental (< 1 kN) | 3–10 mm       |
| Medium research (1–50 kN)   | 10–50 mm      |
| Large flight (50–500 kN)    | 50–200 mm     |
| Heavy-lift (> 500 kN)       | 200–500 mm    |

**Constraint:** Must be > 0. The library enforces this; no upper bound is set.

---

### chamberPressure

**What it controls:** Combustion chamber stagnation pressure. Higher pressure
increases c* (characteristic exhaust velocity), which raises Isp — but also
requires a heavier, thicker chamber and feed-system components.

**How to choose it:** Chamber pressure is usually set by the propulsion system
architecture (pump-fed vs. pressure-fed) and structural mass budget, not by
nozzle aerodynamics alone.

| Feed system        | Typical Pc range |
|--------------------|------------------|
| Pressure-fed       | 0.5–3 MPa        |
| Pump-fed, moderate | 3–10 MPa         |
| High-performance   | 10–25 MPa        |
| Staged-combustion  | 20–30 MPa+       |

For a first design, 5–7 MPa is a reasonable choice for a pump-fed engine. It is
high enough to give good Isp without requiring exotic materials or very tight
manufacturing tolerances.

**Effect on Isp:** Isp scales weakly with Pc because c* ∝ sqrt(Tc), which is
nearly independent of pressure for most propellants in the 3–20 MPa range. The
bigger Pc effect is on the nozzle area ratio needed to expand to a given exit
pressure: higher Pc allows the same Isp with a smaller nozzle (lower A/A*).

**Constraint:** Must be > 0 (Pa).

---

### chamberTemperature

**What it controls:** Adiabatic flame temperature of the combustion products.
Together with γ and molecular weight, it sets c* and therefore Isp.

**How to choose it:** Do not choose it by hand unless you have measured
calorimetric data for your specific propellant at your specific O/F ratio. Use
`OFSweep.adiabatic()` to derive it from the combustion chemistry:

```
OFSweep sweep = OFSweep.adiabatic(OFSweep.Propellant.LOX_RP1, 7e6, 3.5, 101_325);
OFSweep.OFPoint best = sweep.optimumIsp(1.5, 4.0);
double Tc = best.chamberTemperature();
```

If you must estimate Tc manually, the following are approximate values at
optimal O/F for common propellants at Pc ≈ 7 MPa:

| Propellant    | Approx. Tc (K) | Approx. optimum O/F |
|---------------|----------------|---------------------|
| LOX / RP-1    | 3500–3600      | 2.3–2.6             |
| LOX / LH₂     | 2900–3100      | 4.5–5.5             |
| LOX / CH₄     | 3400–3550      | 3.0–3.4             |
| N₂O / Ethanol | 2700–2900      | 1.8–2.4             |
| N₂O / Propane | 2750–2950      | 2.0–2.6             |

These values decrease by 100–200 K as Pc drops from 7 MPa to 1 MPa and
increase slightly at higher pressures due to equilibrium shifts.

**Constraint:** Must be > 0 (K).

---

### exitMach

**What it controls:** The design supersonic exit Mach number. This uniquely
determines the exit-to-throat area ratio (A/A*) through the isentropic
area-velocity relation, which in turn determines the nozzle length and exit
radius.

**How to choose it:** The optimum exit Mach for a nozzle operated at a fixed
altitude satisfies:

```
P_exit = P_ambient
```

where P_exit = Pc × (isentropic pressure ratio at Me). Solving for Me given
Pc and Pa:

```
(Pc / Pa) = ((γ+1)/2)^(γ/(γ-1)) · (1 + (γ-1)/2 · Me²)^(γ/(γ-1))
```

You can use `GasProperties.machFromPressureRatio(Pc/Pa)` to compute this
directly. For typical values:

| Pc (MPa) | Sea-level (Pa=101 kPa) | 10 km (Pa=26 kPa) | Vacuum (Pa≈0) |
|----------|------------------------|-------------------|---------------|
| 3        | M ≈ 2.3                | M ≈ 3.2           | —             |
| 7        | M ≈ 2.9                | M ≈ 4.0           | —             |
| 15       | M ≈ 3.4                | M ≈ 4.6           | —             |

For a vacuum nozzle, exit Mach is limited by nozzle mass and length rather than
by pressure matching. Values of 4–6 are typical for upper stages; beyond 6 the
area ratio becomes very large (A/A* > 50) and the nozzle mass penalty usually
outweighs the Isp gain.

**Trade-offs:**

- Higher Me → higher ideal Isp, but larger nozzle (longer, heavier), and more
  severe flow separation risk at low altitude.
- Lower Me → shorter, lighter nozzle, tolerates sea-level operation without
  separation, but leaves Isp on the table.

**Constraint:** Must be > 1.0 (supersonic). Values above 6 are unusual outside
of vacuum upper stages.

---

### ambientPressure

**What it controls:** The back pressure against which the nozzle exhausts. It
sets the design-point pressure matching condition and is used by
`FlowSeparationPredictor`, `PerformanceCalculator`, and `ShockExpansionModel`.

**How to choose it:**

| Design scenario                  | Value to use                     |
|----------------------------------|----------------------------------|
| Sea-level launch booster         | 101 325 Pa                       |
| Altitude-start (e.g. 10 km)      | ISA pressure at that altitude    |
| Upper-stage (vacuum operation)   | 100–1000 Pa (near-vacuum)        |
| Vacuum (theoretical limit)       | 1 Pa (do not use 0 — see below)  |

**Do not use 0 Pa.** The library's `ConstraintViolationException` requires
`ambientPressure > 0`. For vacuum analysis use a small positive value such as
1–100 Pa, which has a negligible effect on any result while satisfying the
constraint.

**ISA pressure at altitude h (metres), valid below 11 km:**

```
Pa = 101_325 × (1 - 2.2558e-5 × h)^5.2559
```

For 11–25 km (stratosphere): Pa = 22_632 × exp(-1.576e-4 × (h - 11_000)).

---

### gasProperties

**What it controls:** The frozen thermodynamic properties of the combustion
products — γ (ratio of specific heats), molecular weight, and viscosity
coefficients. Every isentropic flow calculation uses these values.

**Which constant to use:**

| Propellant combination | Use                                  |
|------------------------|--------------------------------------|
| LOX + RP-1 (kerosene)  | `GasProperties.LOX_RP1_PRODUCTS`     |
| LOX + LH₂              | `GasProperties.LOX_LH2_PRODUCTS`     |
| LOX + CH₄ (methane)    | `GasProperties.LOX_CH4_PRODUCTS`     |
| N₂O + Ethanol          | `GasProperties.N2O_ETHANOL_PRODUCTS` |
| N₂O + Propane          | `GasProperties.N2O_PROPANE_PRODUCTS` |
| Air (wind tunnel)      | `GasProperties.AIR`                  |
| Pure hydrogen          | `GasProperties.HYDROGEN`             |
| Pure nitrogen          | `GasProperties.NITROGEN`             |

The built-in propellant constants use γ and molecular weight averaged over the
supersonic expansion (between combustion products at Tc and the cooled exit
gas). They are not a substitute for a full equilibrium calculation but are
adequate for preliminary design.

**Custom gas:** If none of the built-in constants match your propellant,
construct a custom instance:

```
// Example: a UDMH/N₂O₄ product gas
GasProperties custom = new GasProperties(
        1.24,     // γ — from CEA or similar
        21.5,     // molecular weight, kg/kmol
        2.1e-5,   // reference viscosity, Pa·s (at reference temperature)
        273.0,    // reference temperature for viscosity, K
        100.0     // Sutherland constant, K
);
```

Obtain γ and molecular weight from a combustion code (NASA CEA, RPA, or
`OFSweep` itself). Viscosity coefficients can be taken from the dominant
species (H₂O for hydrogen-rich propellants, CO₂/CO for carbon-rich ones) if
mixture viscosity data is unavailable.

---

### numberOfCharLines

**What it controls:** The density of the MOC characteristic grid. More lines
mean more interior points (O(n²) total), higher geometric accuracy, and slower
computation.

**How to choose it:**

| Purpose                          | Recommended value | Notes                            |
|----------------------------------|-------------------|----------------------------------|
| Quick trade study                | 15–20             | ~1% area-ratio error acceptable  |
| Standard engineering design      | 30–50             | < 0.5% area-ratio error          |
| High-accuracy / final design     | 60–80             | < 0.1% area-ratio error          |
| Research / algorithm validation  | 100+              | Diminishing returns above 80     |

The convergence of exit area ratio with `numberOfCharLines` is roughly:

```
error ≈ k / n²
```

where n is `numberOfCharLines` and k is a constant that depends on γ and exit
Mach. Doubling n quarters the error. If the `NASASP8120Validator` reports an
area-ratio error above 0.5%, increase n.

**Interaction with wallAngleInitialDegrees:** Large wall angles (> 30°) require
more lines to resolve the steep flow gradient near the throat. If you use a
large wall angle, increase n to compensate.

---

### wallAngleInitialDegrees

**What it controls:** The wall angle (in degrees) at the start of the diverging
section, immediately downstream of the throat. This is the maximum wall angle
in the nozzle and determines how aggressively the flow turns away from the axis
at the throat.

**Physical meaning:** A larger angle makes the initial expansion steeper,
shortening the nozzle for a given exit area ratio but increasing the strength of
the leading characteristic and the oblique disturbance it sends to the axis.

**How to choose it:**

| Design priority                 | Typical range |
|---------------------------------|---------------|
| Minimum length (aggressive)     | 35–45°        |
| Balanced performance/length     | 25–35°        |
| Smooth expansion (conservative) | 15–25°        |
| SP-8120 compliant (< 35°)       | ≤ 35°         |

**28–32° is the most common choice** for flight engines. It satisfies SP-8120,
gives a good balance between nozzle length and divergence loss, and the MOC
solver converges reliably in this range.

Values above 35° are valid inputs but will trigger a NASA SP-8120 advisory
warning. Values above 45° produce strong leading oblique shocks and are not
recommended for bell nozzles; they are more appropriate for spike nozzles where
the geometry is different.

---

### lengthFraction

**What it controls:** The length of the nozzle relative to a 15° half-angle
conical reference nozzle with the same throat radius and exit area ratio.
`lengthFraction = 0.8` means the bell nozzle is 80% of the conical reference
length. This is the primary handle on the performance vs. length trade-off.

**How to choose it:**

| Priority                   | Typical value | Effect                                       |
|----------------------------|---------------|----------------------------------------------|
| Minimum length             | 0.60–0.70     | Significant divergence loss (1–3%)           |
| Flight-weight (typical)    | 0.75–0.85     | < 1% divergence loss, good length savings    |
| Near-ideal performance     | 0.90–1.00     | Approaches full Rao optimal bell             |

**0.8 is the standard starting point.** It gives a nozzle about 50% shorter
than the equivalent 15° conical nozzle with a divergence loss of roughly 0.3–
0.8% compared to a theoretically perfect contour. The marginal gain from
increasing beyond 0.85 is usually not worth the mass and packaging penalty.

**Effect on exit angle:** Shorter length fractions require a higher exit wall
angle to reach the same area ratio in a shorter axial distance. The Rao Bézier
approximation handles this automatically; there is no separate exit angle
parameter to set.

**Constraint:** Must be in the range (0, 1]. Values below 0.6 produce nozzle
contours that are too short to deliver meaningful performance improvement over
a conical nozzle and are physically unrealistic.

---

### axisymmetric

**What it controls:** Whether the nozzle is treated as axisymmetric (round
cross-section, which is the default and normal case) or as a 2-D planar
nozzle (rectangular cross-section with parallel side walls).

**When to use `axisymmetric = false`:**
- Wind tunnel nozzles with rectangular test sections.
- 2-D academic validation cases.
- Nozzles with a rectangular throat (rare in propulsion).

**For all rocket propulsion applications, use `axisymmetric = true` (the
default).** Nearly all liquid and solid rocket nozzles are round in cross-section. 
Setting `false` changes the source term in the MOC equations and
produces a contour that is only correct for a 2-D geometry.

### throatCurvatureRatio

**What it controls:** The radius of the circular arc used to blend the sonic
throat into the divergent section, expressed as a multiple of the throat radius.
Specifically, `r_cd = throatCurvatureRatio × r_throat`, where `r_cd` is the
downstream radius of curvature.

**Where it appears:** The circular-arc throat region is shared by every contour
generator in the library — `CharacteristicNet`, `RaoNozzle`,
`NozzleContour` (CONICAL, RAO\_BELL, TRUNCATED\_IDEAL), and `DualBellNozzle`.
Changing this value therefore affects all of them consistently.

**Typical values:**

| Ratio | Characterisation |
|-------|-----------------|
| 0.25  | Very tight arc. Produces a strong initial expansion fan; shortest throat arc length. Use only when packaging is severely constrained. Risk of flow non-uniformity near the throat. |
| 0.382 | **Default (Rao classical value).** Recommended minimum for bell nozzles. Balances arc length against downstream flow quality. |
| 0.5   | Slightly gentler arc. A safe choice when exit flow uniformity matters more than minimum length. |
| 0.75  | Noticeably longer throat arc. Common in high-performance wind-tunnel or research nozzles. |
| 1.0   | Arc radius equals the throat radius. Very uniform flow entering the divergent section; adds measurable nozzle length and mass. |

**Effect on performance:** Ideal Cf and A/A\* are purely functions of Mach
number and gamma; they do not depend on this ratio. What changes is the geometry
of the wall immediately downstream of the throat, which affects:
- The location and strength of the initial expansion wave fan (important for the
  MOC initial data line).
- The axial starting position of the Bézier or parabolic bell section.
- Physical nozzle length (larger ratio → longer arc → longer nozzle for the same
  `lengthFraction`).

**Interaction with wallAngleInitialDegrees:** Both parameters together determine
the shape of the first part of the divergent section. The arc sweeps from 0° to
`wallAngleInitial`; its arc length is `r_cd × wallAngleInitial`. A large wall
angle combined with a large curvature ratio produces a long throat arc and a
gentle expansion. A small wall angle with a small curvature ratio produces the
shortest possible throat transition.

**Validation range:** `(0, 2.0]`. Values above 2.0 are rejected by the compact
constructor with an `IllegalArgumentException`.

**When to change from the default:**
- Reduce toward 0.25 only if the nozzle must fit in a very tight axial envelope
  and flow uniformity is a secondary concern.
- Increase toward 0.75–1.0 for laboratory or wind-tunnel nozzles where the
  uniformity of the exit profile is the primary design objective.
- For all flight propulsion designs, the default 0.382 is appropriate.

---

## 2. Nozzle type selection

Choose the nozzle class based on the mission profile and the available
complexity budget.

| Type      | Class                                 | When to use                                                                                                                                                     |
|-----------|---------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| MOC bell  | `CharacteristicNet` + `NozzleContour` | Primary choice for all flight designs. Most accurate; gives the full wall contour and all derived data.                                                         |
| Rao bell  | `RaoNozzle`                           | When you need a quick answer without running the full MOC, or when comparing against the empirical Rao correlation. Accuracy is slightly lower (~0.5–1% in Cf). |
| Conical   | `NozzleContour(CONICAL)`              | Baseline reference or when manufacturing simplicity matters more than performance.                                                                              |
| TIC       | `NozzleContour(TRUNCATED_IDEAL)`      | When you want a contour that is theoretically uniform at the exit but do not need the full Rao optimisation.                                                    |
| Aerospike | `AerospikeNozzle`                     | Single-stage-to-orbit or any application with a large altitude range and no staging. Higher mechanical complexity.                                              |
| Dual-bell | `DualBellNozzle`                      | Two-stage-equivalent performance in a single nozzle; best where altitude range is moderate and the mission has two distinct phases (e.g. boost + sustain).      |

---

## 3. AerospikeNozzle parameters

### spikeRadiusRatio

**What it is:** The ratio of the inner radius of the annular throat (the base
of the spike) to the overall throat radius parameter. Determines the annular
gap and therefore the mass-flow distribution.

**Typical range:** 0.4–0.7.

- Lower values (0.4–0.5) give a thicker annular gap, better for high mass-flow
  engines, but produce a longer, heavier spike.
- Higher values (0.6–0.7) give a narrower gap and shorter spike, at the cost
  of a steeper expansion and slightly higher divergence loss.

**Starting point:** 0.5–0.6 for most designs.

---

### truncationFraction

**What it is:** The fraction of the full (theoretical) spike length that is
retained. A full spike (fraction = 1.0) theoretically gives perfect altitude
compensation but is impractically long and heavy. Truncation trades a small
performance loss for a large reduction in spike mass.

**Typical range:** 0.7–0.85.

| Value | Effect                                                                                    |
|-------|-------------------------------------------------------------------------------------------|
| 1.0   | Full spike. Maximum performance, maximum mass. Rarely used.                               |
| 0.85  | Minor truncation. < 0.5% Isp loss at design altitude.                                     |
| 0.75  | Standard truncation. ~1% Isp loss. Good mass saving.                                      |
| 0.60  | Aggressive truncation. 2–3% Isp loss. Base-bleed sometimes added to fill the base cavity. |

Below 0.65, the exposed base cavity at the truncated end can create a low-pressure 
wake that partially negates the truncation savings. Base bleed (routing
a small amount of propellant through the base) can recover some of this, but
the library does not model base bleed.

---

## 4. DualBellNozzle parameters

### transitionAreaRatio

**What it is:** The exit area ratio (A/A*) of the inner (base) bell section,
at the kink point where the outer extension begins. Flow separates at the kink
at low altitude; at high altitude it attaches and the full nozzle operates.

**How to choose it:** A good starting point is the geometric mean of 1 and the
full exit area ratio:

```
transitionAR = Math.sqrt(params.exitAreaRatio());
```

This balances the two performance modes. Adjusting upward increases the inner
bell performance but delays transition to a higher altitude (lower ambient
pressure). Adjusting downward does the opposite.

**Constraint:** Must satisfy `1 < transitionAR < exitAreaRatio`. Values very
close to either bound produce a degenerate geometry (nearly no inner bell, or
nearly no outer extension).

**Practical range:** 0.4 × exitAR to 0.7 × exitAR. Below 0.4 the inner bell
is too short to be useful; above 0.7 the extension adds little Isp at high
altitude.

---

### kinkAngle

**What it is:** The inward re-inflection angle at the kink, in radians. A small
negative tuck at the kink promotes flow reattachment on the outer bell when
ambient pressure drops.

**Default:** The constructor default (approximately 3°) is appropriate for most
designs. Increasing the kink angle makes reattachment more reliable but
introduces a local compression that can cause a weak internal shock.

**Typical range:** 2°–5° (0.035–0.087 rad). Values above 5° create a visually
obvious kink and can cause local flow separation inside the outer bell in some
operating regimes.

---

## 5. Chemistry and O/F ratio

### Choosing the O/F ratio

The O/F ratio controls the adiabatic flame temperature and the molecular weight
of the products, both of which affect Isp. The optimum Isp O/F (slightly
fuel-rich for most propellants) differs from the stoichiometric O/F.

Always use `OFSweep.optimumIsp()` rather than assuming a value:

```
OFSweep.OFPoint best = OFSweep.adiabatic(propellant, Pc, Me, Pa)
        .optimumIsp(lowerBound, upperBound);
```

Guidance on search bounds for `optimumIsp`:

| Propellant    | Search range |
|---------------|--------------|
| LOX / RP-1    | 1.5 – 4.0    |
| LOX / LH₂     | 3.0 – 7.0    |
| LOX / CH₄     | 2.0 – 5.0    |
| N₂O / Ethanol | 1.0 – 4.0    |
| N₂O / Propane | 1.0 – 4.0    |

If `optimumIsp` returns a value at the edge of the search range, widen the
range — the true optimum lies outside it (see troubleshooting).

### Frozen vs. equilibrium flow

Use `ChemistryModel.frozen()` when:
- The nozzle is short and the residence time in the diverging section is very
  small (< 1 ms), so recombination reactions do not have time to reach
  equilibrium.
- You are matching data from a test where the flow was quenched quickly.
- You want a conservative (lower) Isp estimate.

Use `ChemistryModel.equilibrium()` when:
- The nozzle has a moderate-to-long diverging section (most flight nozzles).
- You want the best achievable Isp estimate.
- You are comparing against CEA output (which also computes equilibrium Isp).

The equilibrium Isp is always ≥ the frozen Isp. The gap is typically 5–15 s
for LOX/RP-1 and larger (15–30 s) for LOX/LH₂ because H₂O and H recombination
releases significant energy.

---

## 6. Thermal analysis inputs

### Wall properties (`setWallProperties(conductivity, thickness)`)

`conductivity` is the thermal conductivity of the structural wall in W/(m·K).
`thickness` is the wall thickness in metres.

**Common wall materials:**

| Material            | k (W/(m·K)) | Typical thickness | Notes                                  |
|---------------------|-------------|-------------------|----------------------------------------|
| CuCrZr copper alloy | 380–400     | 1–3 mm            | Best for regeneratively cooled         |
| Inconel 718         | 11–13       | 2–5 mm            | High strength; high wall temperature   |
| Stainless 304       | 15–17       | 2–5 mm            | Common for lower-performance engines   |
| Titanium 6Al-4V     | 6–7         | 3–6 mm            | Low density; poor thermal conductivity |

Low-conductivity walls (Inconel, stainless) produce higher hot-gas-side wall
temperatures for the same heat flux. If the predicted wall temperature exceeds
the material's operational limit, switch to copper alloy or reduce the wall
thickness and add active cooling.

### Emissivity (`setEmissivity(ε)`)

Emissivity affects the radiative heat flux from the hot gas to the wall. It
is a property of the hot gas/soot combination, not the wall itself.

| Combustion product mixture    | Approximate ε |
|-------------------------------|---------------|
| Fuel-lean, H₂O-rich (LOX/LH₂) | 0.3–0.5       |
| Moderate (LOX/RP-1)           | 0.5–0.7       |
| Sooty / carbon-rich           | 0.7–0.9       |

Radiation is typically 5–15% of total heat flux for most propellants. Err on the
high side (0.6–0.7) for a conservative thermal margin.

### Coolant selection (`CoolantChannel.Coolant`)

| Coolant | Max outlet temp | Notes                                                     |
|---------|-----------------|-----------------------------------------------------------|
| `RP1`   | ~600–620 K      | Coking limit; common for LOX/RP-1 engines                 |
| `LH2`   | ~250 K          | Excellent heat capacity; used in high-performance engines |
| `CH4`   | ~500 K          | Good balance; growing use in modern engines               |

Use the same propellant as the fuel wherever possible — it is already on board.

### Material for structural analysis (`ThermalStressAnalysis.Material`)

Match the material to the wall properties you set in `setWallProperties()`:

| `Material` enum  | Use when                                              |
|------------------|-------------------------------------------------------|
| `CU_CR_ZR`       | Regeneratively cooled copper-alloy chamber wall       |
| `INCONEL_718`    | High-temperature structural shell or film-cooled wall |
| `STAINLESS_304`  | Low-cost chamber or test article                      |
| `TITANIUM_6AL4V` | Mass-critical structure where cooling is adequate     |

If you set `setWallProperties(390, 0.002)` (copper alloy) but then pass
`ThermalStressAnalysis.Material.INCONEL_718` to the stress analysis, the
thermal and structural material properties will be inconsistent. Keep them
aligned.

### Material for ablative liner (`AblativeNozzleModel.Material`)

| `Material` enum   | Char rate | Density  | Best for                             |
|-------------------|-----------|----------|--------------------------------------|
| `CARBON_PHENOLIC` | Low       | High     | High-performance solid/hybrid motors |
| `SILICA_PHENOLIC` | Moderate  | Moderate | Cost-effective for moderate fluxes   |
| `CARBON_CARBON`   | Very low  | Low      | High-temperature, long burns         |
| `GRAPHITE`        | Moderate  | High     | Simple geometry, throat inserts      |
| `EPDM`            | High      | Low      | Low-temperature, short burns         |

A low char rate means the material recedes slowly — this is desirable for a
long-burn motor. Multiply peak recession rate by burn time to get the minimum
required liner thickness.

---

## 7. CFD export settings

### axialCells and radialCells

These control the resolution of the structured mesh. Minimum recommended values
for different purposes:

| Purpose                  | Axial cells | Radial cells |
|--------------------------|-------------|--------------|
| Quick flow visualisation | 100         | 40           |
| Engineering accuracy     | 200–300     | 60–100       |
| High-fidelity / research | 400+        | 120+         |

The aspect ratio of cells near the throat should stay below ~10:1. If the
throat region is coarsely resolved in the axial direction while radial cells
are very fine (from a small `firstLayerThickness`), the solver may struggle to
converge. Keep the axial and radial counts in rough proportion.

### firstLayerThickness

Controls the height of the first cell off the wall, for y⁺-targeted grading.

| Target regime           | y⁺          | Approx. first-layer thickness (Pc=7 MPa, r*=50mm) |
|-------------------------|-------------|---------------------------------------------------|
| Wall-resolved (DNS/LES) | y⁺ ≈ 1      | ~1–5 × 10⁻⁶ m                                     |
| Low-Re turbulence       | y⁺ ≈ 1–5    | ~5 × 10⁻⁶ – 2 × 10⁻⁵ m                            |
| Wall function           | y⁺ ≈ 30–100 | ~1 × 10⁻⁴ – 5 × 10⁻⁴ m                            |

For `rhoCentralFoam` (the default OpenFOAM solver written by the exporter), a
y⁺ of 30–100 with a k-ω SST turbulence model is a practical starting point.
Full wall resolution (y⁺ ≈ 1) requires a significantly finer mesh and a
correspondingly smaller time step, which greatly increases run time.

A reasonable default for an engineering nozzle CFD study:

```
.setAxialCells(200)
.setRadialCells(80)
.setFirstLayerThickness(1e-5)      // y⁺ ≈ 5–30 for typical conditions
.setTurbulenceEnabled(true)
```

### turbulenceEnabled and turbulenceIntensity

Set `turbulenceEnabled(true)` for all flows with Re > 10⁵ at the throat, which
is virtually every rocket nozzle. Laminar flow inside a nozzle is only
appropriate for very small engines (throat diameter < ~5 mm) or very low
chamber pressures (< 0.2 MPa).

`turbulenceIntensity` (fraction, not percent) represents the inlet turbulence
level. For combustion chamber exit conditions, 3–7% is typical. Use 0.05
(5%) as a default. This affects the initial k-ω boundary condition; for a
well-converged solution the result is usually not sensitive to values in the
3–10% range.

---

## 8. Uncertainty analysis inputs

### Sample count (MonteCarloUncertainty)

| Purpose                         | Samples  | Typical runtime |
|---------------------------------|----------|-----------------|
| Quick sensitivity ranking       | 1 000    | ~5–15 s         |
| Engineering uncertainty budget  | 10 000   | ~60–180 s       |
| Statistical publication quality | 100 000  | ~10–30 min      |

The statistical error in the standard deviation scales as 1/√N. At 1 000
samples the relative error in σ is ~3%; at 10 000 samples it is ~1%. For
sensitivity rankings (Pearson r), 1 000 samples is usually sufficient; for
tail percentiles (P05, P95) use at least 10 000.

### Uncertainty magnitudes

When registering individual uncertainties with `addNormalParameter()`, the
third argument is the 1-sigma standard deviation (not the ±3σ tolerance or the
±2σ tolerance that is often quoted in engineering drawings). Convert:

```
// Drawing tolerance ±δ at 3σ → 1σ = δ / 3
// Drawing tolerance ±δ at 2σ → 1σ = δ / 2
mc.addNormalParameter("throatRadius", r_t, drawingTolerance / 3.0);
```

**Typical manufacturing tolerances for rocket nozzles:**

| Parameter           | Typical 3σ tolerance | 1σ for `addNormalParameter` |
|---------------------|----------------------|-----------------------------|
| Throat radius       | ±0.5%                | 0.0017 × r_throat           |
| Chamber pressure    | ±2%                  | 0.0067 × Pc                 |
| Chamber temperature | ±3%                  | 0.010 × Tc                  |
| Wall angle          | ±1°                  | 0.33°                       |
| γ (gas model error) | ±1.5%                | 0.005 × γ                   |

`addTypicalUncertainties()` registers values close to these defaults. Use
individual registration when you have measured data that differs significantly
from the defaults.

### Random seed (MonteCarloUncertainty)

The third constructor argument is the seed for the random number generator.
Using a fixed seed (e.g. `42L`) makes the result exactly reproducible across
runs. Use a fixed seed during development and verification. For a final
published uncertainty budget, consider running with several different seeds
and confirming that the statistics are stable (< 1% variation in σ and P05/P95
across seeds at 10 000 samples).
