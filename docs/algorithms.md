# Algorithms

Detailed description of the numerical methods used in each subsystem.

---

## 1. Method of Characteristics (CharacteristicNet)

### Overview

The MOC solver computes the complete supersonic flow field in the divergent
section of a nozzle. It propagates Riemann invariants along characteristic lines
(Mach waves) from an initial data line at the throat to the exit plane.

### Riemann Invariants

For two-dimensional irrotational supersonic flow the two Riemann invariants are
constant along their respective characteristic families:

```
Q⁺ = θ − ν(M)   constant along a C⁺ characteristic (left-running)
Q⁻ = θ + ν(M)   constant along a C⁻ characteristic (right-running)
```

where `θ` is the local flow angle (radians, positive away from the axis) and
`ν(M)` is the Prandtl-Meyer function. At an interior point where a C⁺ and a C⁻
characteristic intersect:

```
θ = (Q⁻ + Q⁺) / 2
ν = (Q⁻ − Q⁺) / 2
```

Mach number is then recovered by inverting ν(M) (see §4).

### Axisymmetric Source-Term Correction

For axisymmetric flow the governing equation includes a radial source term.
`CharacteristicNet` handles this with a predictor-corrector (iterated) scheme.
Starting from the planar solution, the correction to θ at each interior point is:

```
Δθ = sin(θ_avg) / (y · cot(μ_avg)) · Δs
```

where `y` is the radial distance from the axis, `μ_avg` is the mean Mach angle,
and `Δs` is the arc length along the characteristic. The correction is applied
iteratively until |Δθ| < 1e-8 and |Δν| < 1e-8 (maximum 100 iterations).

### Throat Circular Arc

Before the initial data line can be placed, the geometry locates the end of the
circular-arc throat transition. The arc has radius

```
r_cd = throatCurvatureRatio × r_throat
```

and sweeps from 0° (the sonic throat) to `wallAngleInitial` (the start of the
divergent section). The arc end-point coordinates are

```
x₀ = r_cd · sin(wallAngleInitial)
y₀ = r_throat + r_cd · (1 − cos(wallAngleInitial))
```

This point `(x₀, y₀)` is the origin of the Rao wall Bézier and the radial
position of the outermost point on the initial data line. A larger
`throatCurvatureRatio` places the initial data line further downstream and at a
slightly larger radial position, producing a gentler initial expansion fan.

### Initial Data Line

The initial data line spans from the centerline to the nozzle wall at the start
of the divergent section. It contains `numberOfCharLines + 1` points. The flow
angle at the wall is set to `θ_max = wallAngleInitialDegrees`; at the centerline
`θ = 0`. Between these extremes `θ` ramps linearly. The Mach number at each
point is recovered from `ν = ν_initial + θ` via Prandtl-Meyer inversion.

### Rao Wall Profile

The nozzle wall is prescribed as a quadratic Bézier curve in (x, r)-space:

```
P(t) = (1−t)²·P0 + 2(1−t)t·P1 + t²·P2,  t ∈ [0, 1]
```

- **P0** = (x₀, r_throat) — start at the throat edge, slope = tan(θ_max)
- **P2** = (x_exit, r_exit) — exit at design Mach, slope = tan(θ_E)
- **P1** is the Bézier control point computed so that the tangents at P0 and P2
  are satisfied exactly.

The exit angle `θ_E` is the Rao correlation value for the given length fraction
and exit area ratio. The wall is sampled at 501 equally spaced Bézier-parameter
values (500 wall segments).

### Propagation

For each wall segment the solver:

1. Builds the interior row from the previous row using `computeInteriorPoint()`
   for every adjacent left-right pair.
2. Reads the next wall point from the Rao profile by finding the Mach number that
   satisfies the isentropic area-velocity relation at that radial position, then
   applying the C⁻ compatibility equation from the last interior point.
3. Assigns thermodynamic quantities (pressure, temperature, density, velocity) via
   isentropic relations from the local Mach number and the stagnation conditions
   in `NozzleDesignParameters`.

### Characteristic Slopes

The C⁺ and C⁻ slopes used to locate the intersection point are the average of
the slopes at the two end-points:

```
C⁺ slope = tan((θ_L + θ)/2 + (μ_L + μ)/2)
C⁻ slope = tan((θ_R + θ)/2 − (μ_R + μ)/2)
```

This second-order averaging (the "method of characteristics with averaging")
provides O(h²) accuracy with a single corrector pass.

---

## 2. Prandtl-Meyer Function (GasProperties)

### Forward (M → ν)

```
ν(M) = √((γ+1)/(γ-1)) · arctan(√((γ-1)/(γ+1) · (M²-1))) − arctan(√(M²-1))
```

Evaluated directly; no approximation.

### Inverse (ν → M)

Newton-Raphson iteration starting from an initial guess derived from the
approximation M ≈ 1 + ν^(2/3):

```
M_{n+1} = M_n − (ν(M_n) − ν_target) / (dν/dM)
```

where `dν/dM = √(M²-1) / (M · (1 + (γ-1)/2 · M²))`.

Convergence tolerance: |ν(M_n) − ν_target| < 1e-10, maximum 50 iterations.

### Isentropic Area Ratio (M → A/A*)

```
A/A* = (1/M) · ((2/(γ+1)) · (1 + (γ-1)/2 · M²))^((γ+1)/(2(γ-1)))
```

### Inverse Area Ratio (A/A* → M)

Newton-Raphson with the same derivative as the Prandtl-Meyer inversion.
Convergence tolerance: 1e-10, maximum 50 iterations.
The supersonic branch is selected by starting from M = 2.0 when A/A* > 1.

---

## 3. Gibbs Free Energy Minimization (GibbsMinimizer)

### Problem Statement

Find mole numbers n₁, …, nₛ ≥ 0 that minimize total Gibbs free energy:

```
G = Σⱼ nⱼ · (μ°ⱼ(T)/RT + ln(nⱼ/n_total) + ln(P/P_ref))
```

subject to element-conservation constraints:

```
Σⱼ aᵢⱼ · nⱼ = bᵢ   for each element i (H, C, O, N)
```

where `aᵢⱼ` is the number of atoms of element `i` in species `j`, and `bᵢ` is the
total moles of element `i` supplied by the reactants.

### Species Set

Ten species with NASA-7 polynomial data (McBride et al., NASA TP-2002-211556):
H₂O, CO₂, H₂, CO, OH, O₂, N₂, H, O, NO.

A boolean `speciesActive[]` array gates each species into the iteration. A
species is active only when all of its constituent elements are present in the
mixture (e.g., CO₂ and CO are inactive in C-free LOX/LH₂ combustion). This
prevents unconstrained growth of inactive species from corrupting the element
balance.

### Newton-Lagrange Iteration

The first-order optimality conditions (KKT) are:

```
ln(nⱼ/n) + μ°ⱼ/RT = Σᵢ λᵢ · aᵢⱼ
```

Linearizing around the current iterate gives a reduced linear system in the
Lagrange multipliers λ:

```
A · λ = v
```

where A is the symmetric element-element matrix `Aᵢₖ = Σⱼ aᵢⱼ · aₖⱼ · nⱼ / n`
and v is the residual of the element balance. The 4×4 system is solved by
Gaussian elimination with partial pivoting.

### Damping and Projection

Newton steps are damped by `MAX_STEP = 5.0` applied uniformly across all species:

```
Δ(ln n) = clamp(correction, −MAX_STEP, +MAX_STEP)
```

After each damped Newton step, a projection pass (`enforceElementConstraints`)
applies multiplicative corrections to restore exact element balance (up to 5
passes until max relative residual < 1e-12). This two-stage approach — damped
Newton for the bulk update, projection for exact constraint satisfaction —
prevents the linear approximation from violating the element balance.

### Convergence

The iteration terminates when the maximum KKT residual is below 1e-8, or after
200 iterations. Final mole fractions are converted to mass fractions and normalized.

### NASA-7 Polynomials

The chemical potential of species `j` is computed from NASA-7 coefficients. For
a given temperature range (low: T < 1000 K, high: T ≥ 1000 K):

```
Cp/R = a₀ + a₁T + a₂T² + a₃T³ + a₄T⁴
H/RT = a₀ + a₁T/2 + a₂T²/3 + a₃T³/4 + a₄T⁴/5 + a₅/T
S/R  = a₀·ln(T) + a₁T + a₂T²/2 + a₃T³/3 + a₄T⁴/4 + a₆
G/RT = H/RT − S/R
```

Data source: McBride, Zehe, Gordon, NASA TP-2002-211556.

---

## 4. Equilibrium Gamma (ChemistryModel)

The equilibrium isentropic exponent γ_s differs from the frozen γ because
composition changes with temperature. `ChemistryModel.calculateEquilibriumGamma()`
estimates it using a finite-difference perturbation of the Gibbs solver:

```
γ_s = Cp_eq / (−β · Cp_eq − (R/M̄) · α²)
```

where:
- `Cp_eq` — equilibrium specific heat at constant pressure (J/kg·K)
- `α = (∂ ln M̄ / ∂ ln T)_P` — fractional change in mean molecular weight with
  temperature (captures dissociation lightening), estimated by finite difference
- `β = (∂ ln M̄ / ∂ ln P)_T` — fractional change in mean molecular weight with
  pressure (captures pressure-driven recombination), estimated by finite difference

The perturbation step is 1% of the nominal T or P.

---

## 5. Adiabatic Flame Temperature (OFSweep)

`OFSweep.adiabaticTemperature(of)` uses Newton iteration to find Tc such that:

```
H_products(Tc) = H_reactants(298 K)
```

Both sides use NASA-7 absolute enthalpies (including heats of formation). Reactant
enthalpies come from the `Propellant` enum using NIST-JANAF / NASA TP-2002-211556
standard-state values. Product enthalpies are evaluated via `ChemistryModel` at the
current iteration Tc.

The Newton step is:

```
Tc_{n+1} = Tc_n − (H_products(Tc_n) − H_reactants) / Cp_products(Tc_n)
```

Convergence: |ΔTc| < 0.1 K, maximum 100 iterations, starting from 3000 K.

---

## 6. Natural Cubic Spline (NozzleContour)

Used for `CUSTOM_SPLINE` and `MOC_GENERATED` contour types.

Given n+1 control points (xᵢ, yᵢ), the spline on each interval [xᵢ, xᵢ₊₁] is:

```
S(x) = aᵢ + bᵢ(x − xᵢ) + cᵢ(x − xᵢ)² + dᵢ(x − xᵢ)³
```

Coefficients are computed from the standard tridiagonal system (Burden & Faires
§3.5) with not-a-knot boundary conditions (S‴ is continuous at the second and
second-to-last knot). The tridiagonal system is solved by forward elimination with
partial pivoting in O(n) time.

Evaluation uses Horner's method to minimize floating-point operations:

```
S(x) = aᵢ + dx·(bᵢ + dx·(cᵢ + dx·dᵢ))    where dx = x − xᵢ
```

`getRadiusAt(x)` evaluates the spline; `getSlopeAt(x)` uses a central-difference
approximation with h = 1 µm. For `MOC_GENERATED` contours, control points closer
than `0.05 × r_throat` apart are filtered before fitting (closely spaced knots cause
the tridiagonal h-coefficients to approach zero and the system becomes ill-conditioned).

---

## 7. Aerospike Contour (AerospikeContour)

The spike contour is the inner streamline of a centred Prandtl-Meyer expansion
fan emanating from the cowl lip.

At angular step i (sweeping from 0 to ν(M_exit) in `numSpikePoints` increments),
the next contour point is found at the intersection of:

1. **C⁻ characteristic from the cowl lip:**
   ```
   r = r_throat − x · tan(δᵢ + μᵢ)
   ```
2. **Streamline from the previous spike point:**
   ```
   r = r_{i-1} − (x − x_{i-1}) · tan(δ_avg)
   ```

where δᵢ is the flow-deflection angle at angular step i and μᵢ is the
corresponding Mach angle.

Monotonicity constraints `x_i ≥ x_{i-1}` and `r_i ≤ r_{i-1}` are enforced after
each step to prevent floating-point drift from producing non-physical geometry.

The spike can be truncated at any fraction of the full length. The full contour
and the truncated sub-list are both stored and exposed separately.

---

## 8. Thrust Coefficient

The ideal thrust coefficient is computed at multiple points in the library
(NozzleDesignParameters, OFSweep, AerospikeNozzle, DualBellNozzle) using the
same isentropic formula:

```
Ae/At  = (1/Me) · ((2/(γ+1)) · (1 + (γ-1)/2 · Me²))^((γ+1)/(2(γ-1)))
pe/pc  = (1 + (γ-1)/2 · Me²)^(−γ/(γ-1))
Cf     = √(2γ²/(γ-1) · (2/(γ+1))^((γ+1)/(γ-1)) · (1 − (pe/pc)^((γ-1)/γ)))
         + (pe − pa) / pc · Ae/At
```

The first term is the momentum-thrust contribution; the second is the
pressure-thrust correction.

`PerformanceCalculator` applies additional corrections for divergence loss
(λ = (1 + cos α)/2 where α is the half-angle at the exit), boundary-layer
displacement-thickness loss (scaled by Re^−0.2), and chemical non-equilibrium
loss (from the frozen vs. equilibrium γ difference).

---

## 9. Off-Design Flow Regimes (ShockExpansionModel)

`ShockExpansionModel.compute(pa)` classifies the flow into one of five regimes:

| Regime                   | Condition                                     |
|--------------------------|-----------------------------------------------|
| `IDEALLY_EXPANDED`       |                                               |pe − pa| / pa < 0.01 |
| `UNDEREXPANDED`          | pe > pa                                       |
| `OVEREXPANDED_OBLIQUE`   | pe < pa and M_sep > 0.7 · M_exit              |
| `OVEREXPANDED_MACH_DISK` | pe < pa and M_sep ≤ 0.7 · M_exit              |
| `SEPARATED`              | ambient pressure exceeds separation threshold |

For the oblique-shock regime, the shock angle β is solved from:

```
sin²β = ((γ+1) · p_ratio + (γ-1)) / (2γ · M₁²)
```

and the deflection angle from the θ-β-M relation. For normal shocks, the
Rankine-Hugoniot relations are applied directly.

The model uses a standard ISA atmosphere (troposphere: T lapse 6.5 K/km;
lower stratosphere: isothermal at 216.65 K; linear interpolation for the
intermediate region) to convert altitude to ambient pressure.

---

## 10. Monte Carlo Uncertainty (MonteCarloUncertainty)

Each of the N samples (default 10,000) draws from the registered parameter
distributions independently:

- **NORMAL:** Box-Muller transform
- **UNIFORM:** scaled from U(0,1)
- **TRIANGULAR:** inverse CDF method
- **LOGNORMAL:** exp(μ + σ·Z) where Z ~ N(0,1)

Each sample is evaluated by constructing a perturbed `NozzleDesignParameters`
and running `PerformanceCalculator.simple()`. Output statistics (mean, standard
deviation, percentiles p5, p95) are computed on the collected array. Pearson
correlation coefficients between each input parameter and each output metric are
computed as:

```
r(X, Y) = cov(X, Y) / (σ_X · σ_Y)
```

Sensitivities are reported as a `Map<String, Double>` keyed by
`"paramName_Cf_correlation"` and `"paramName_Isp_correlation"`.
