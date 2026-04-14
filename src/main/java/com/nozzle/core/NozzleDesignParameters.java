/*
 * Copyright (C) 2026  Craig Walters
 *
 * This program is free software: you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free
 *  Software Foundation, either version 3 of the License, or (at your option) any
 *  later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 *  more details.
 *
 *  You should have received a copy of the GNU General Public License along with this
 *  program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *  Contact the owner via the github repository if you would like to license this software for
 *  commercial purposes outside the restrictions imposed by this copyright.
 */

package com.nozzle.core;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * Immutable record containing nozzle design parameters.
 * All dimensions are in SI units (meters, Pascals, Kelvin).
 *
 * @param throatRadius          Throat radius in meters
 * @param exitMach              Design exit Mach number
 * @param chamberPressure       Chamber (stagnation) pressure in Pa
 * @param chamberTemperature    Chamber (stagnation) temperature in K
 * @param ambientPressure       Ambient pressure in Pa
 * @param gasProperties         Gas thermodynamic properties
 * @param numberOfCharLines     Number of characteristic lines for MOC
 * @param wallAngleInitial      Initial wall angle at throat in radians
 * @param lengthFraction        Fractional length compared to 15° cone (Rao parameter)
 * @param axisymmetric          True for axisymmetric nozzle, false for 2D planar
 * @param throatWidth           Span (depth into the page) of a 2D rectangular nozzle
 *                              in metres.  Only used when {@code axisymmetric} is
 *                              {@code false}; ignored for axisymmetric nozzles.
 *                              Defaults to 1.0 m (per-unit-depth convention).
 *                              Must be positive.
 * @param throatCurvatureRatio    Downstream throat radius of curvature as a multiple
 *                                of the throat radius (r_cd = ratio × r_t).
 *                                The classical Rao value is 0.382; the valid range
 *                                is (0, 2.0].
 * @param upstreamCurvatureRatio  Upstream throat radius of curvature as a multiple
 *                                of the throat radius (r_cu = ratio × r_t).
 *                                Controls the shape of the convergent-section arc
 *                                immediately upstream of the throat.  Typical value
 *                                1.5; valid range (0, 3.0].
 * @param convergentHalfAngle     Half-angle of the convergent cone in radians.
 *                                The conical section joins the upstream circular
 *                                arc to the cylindrical combustion chamber.
 *                                Typical value 30°; valid range [5°, 60°].
 * @param contractionRatio        Combustion-chamber-to-throat area ratio (Ac/At).
 *                                Determines the chamber radius:
 *                                r_c = r_t × √(contractionRatio).
 *                                Typical value 4.0; valid range [1.5, 20].
 */
@JsonDeserialize(builder = NozzleDesignParameters.Builder.class)
public record NozzleDesignParameters(
        @Positive double throatRadius,
        @Min(1) double exitMach,
        @Positive double chamberPressure,
        @Positive double chamberTemperature,
        @Positive double ambientPressure,
        GasProperties gasProperties,
        @Min(5) int numberOfCharLines,
        @Positive double wallAngleInitial,
        @Positive double lengthFraction,
        boolean axisymmetric,
        @Positive double throatWidth,
        @Positive double throatCurvatureRatio,
        @Positive double upstreamCurvatureRatio,
        @Positive double convergentHalfAngle,
        @Positive double contractionRatio
) {
    
    /**
     * Default number of characteristic lines used when no explicit value is
     * supplied to the builder (50 lines gives a good balance of accuracy and
     * computation time for typical nozzle designs).
     */
    public static final int DEFAULT_CHAR_LINES = 50;
    
    /**
     * Default throat width for 2D planar nozzles (1.0 m = per-unit-depth convention).
     * Has no effect on axisymmetric nozzles.
     */
    public static final double DEFAULT_THROAT_WIDTH = 1.0;

    /**
     * Default downstream throat radius-of-curvature ratio (r_cd / r_t).
     * This is the classical Rao value for bell nozzles.
     */
    public static final double DEFAULT_THROAT_CURVATURE_RATIO = 0.382;

    /**
     * Default upstream throat radius-of-curvature ratio (r_cu / r_t).
     * Typical value for liquid rocket bell nozzles.
     */
    public static final double DEFAULT_UPSTREAM_CURVATURE_RATIO = 1.5;

    /**
     * Default convergent-section half-angle in radians (30°).
     */
    public static final double DEFAULT_CONVERGENT_HALF_ANGLE = Math.toRadians(30.0);

    /**
     * Default combustion-chamber-to-throat area ratio.
     * A 4:1 contraction ratio is common for small-to-medium liquid engines.
     */
    public static final double DEFAULT_CONTRACTION_RATIO = 4.0;

    /**
     * Compact canonical constructor that validates all design parameters.
     *
     * @throws IllegalArgumentException if {@code exitMach} &lt; 1, {@code throatRadius} ≤ 0,
     *         {@code chamberPressure} ≤ {@code ambientPressure},
     *         {@code numberOfCharLines} &lt; 5,
     *         {@code wallAngleInitial} outside (0, π/4],
     *         {@code lengthFraction} outside (0, 1], or
     *         {@code throatCurvatureRatio} outside (0, 2]
     */
    public NozzleDesignParameters {
        if (exitMach < 1.0) {
            throw new IllegalArgumentException("Exit Mach number must be >= 1.0 for supersonic flow");
        }
        if (throatRadius <= 0) {
            throw new IllegalArgumentException("Throat radius must be positive");
        }
        if (chamberPressure <= ambientPressure) {
            throw new IllegalArgumentException("Chamber pressure must exceed ambient pressure");
        }
       //noinspection ConstantValue
       if (numberOfCharLines < 5) {
            throw new IllegalArgumentException("At least 5 characteristic lines required");
        }
        if (wallAngleInitial <= 0 || wallAngleInitial > Math.PI / 4) {
            throw new IllegalArgumentException("Initial wall angle must be between 0 and 45 degrees");
        }
        if (lengthFraction <= 0 || lengthFraction > 1.0) {
            throw new IllegalArgumentException("Length fraction must be between 0 and 1");
        }
        if (throatWidth <= 0) {
            throw new IllegalArgumentException(
                    "Throat width must be positive; got " + throatWidth);
        }
        if (throatCurvatureRatio <= 0 || throatCurvatureRatio > 2.0) {
            throw new IllegalArgumentException(
                    "Throat curvature ratio must be in (0, 2.0]; got " + throatCurvatureRatio);
        }
        if (upstreamCurvatureRatio <= 0 || upstreamCurvatureRatio > 3.0) {
            throw new IllegalArgumentException(
                    "Upstream curvature ratio must be in (0, 3.0]; got " + upstreamCurvatureRatio);
        }
        double minHalfAngle = Math.toRadians(5.0);
        double maxHalfAngle = Math.toRadians(60.0);
        if (convergentHalfAngle < minHalfAngle || convergentHalfAngle > maxHalfAngle) {
            throw new IllegalArgumentException(
                    "Convergent half-angle must be in [5°, 60°]; got "
                    + Math.toDegrees(convergentHalfAngle) + "°");
        }
        if (contractionRatio < 1.5 || contractionRatio > 20.0) {
            throw new IllegalArgumentException(
                    "Contraction ratio must be in [1.5, 20]; got " + contractionRatio);
        }
    }

    /**
     * Computes the combustion-chamber radius derived from the throat radius
     * and the contraction ratio.
     *
     * @return Chamber radius r_c = r_throat × √(contractionRatio) in metres
     */
    public double chamberRadius() {
        return throatRadius * Math.sqrt(contractionRatio);
    }

    /**
     * Converts {@link #convergentHalfAngle} to degrees for display purposes.
     *
     * @return Convergent half-angle in degrees
     */
    public double convergentHalfAngleDegrees() {
        return Math.toDegrees(convergentHalfAngle);
    }

    /**
     * Computes the axial length of the convergent section (chamber face to throat)
     * using the same closed-form geometry as {@link com.nozzle.geometry.ConvergentSection}.
     *
     * <p>The convergent wall consists of a conical section (half-angle θ_c) tangent
     * to an upstream circular arc (radius r_cu = upstreamCurvatureRatio × r_t).
     * The arc end-point coordinates are:
     * <pre>
     *   x_arc = −r_cu · sin(θ_c)
     *   y_arc =  r_t  + r_cu · (1 − cos(θ_c))
     * </pre>
     * and the chamber face sits at:
     * <pre>
     *   x_chamber = x_arc − (r_c − y_arc) / tan(θ_c)
     * </pre>
     * so the convergent length is {@code L_conv = −x_chamber}.
     *
     * <p>This is a pure function of the existing fields — no geometry object is
     * required. Longer convergent sections improve flow uniformity and reduce
     * boundary-layer thickness at the throat but increase engine length and mass.
     *
     * @return Convergent section axial length L_conv in metres (always positive)
     */
    public double convergentLength() {
        double rcu   = upstreamCurvatureRatio * throatRadius;
        double tc    = convergentHalfAngle;
        double rc    = chamberRadius();
        double yArc  = throatRadius + rcu * (1.0 - Math.cos(tc));
        double xArc  = -rcu * Math.sin(tc);
        double xChamber = xArc - (rc - yArc) / Math.tan(tc);
        return -xChamber;
    }

    /**
     * Returns the convergent section length normalized by the throat diameter
     * ({@code L_conv / D_t = convergentLength() / (2 × throatRadius)}).
     *
     * <p>This dimensionless ratio is the standard engineering metric for convergent
     * section sizing.  Representative values:
     * <ul>
     *   <li>L/D_t ≈ 1–2 — compact rocket engines (minimal mass, thicker BL)</li>
     *   <li>L/D_t ≈ 3–5 — typical liquid rocket engines</li>
     *   <li>L/D_t ≈ 6–10 — wind-tunnel settling chambers (thinnest BL, best
     *       flow uniformity at the throat)</li>
     * </ul>
     *
     * @return L_conv / D_t (dimensionless, always positive)
     */
    public double convergentLengthRatio() {
        return convergentLength() / (2.0 * throatRadius);
    }

    /**
     * Geometric discharge coefficient (Cd) based on sonic-line curvature.
     *
     * <p>The sonic line at the throat is curved rather than flat, so the
     * effective (mass-flow) throat area is slightly smaller than the geometric
     * throat area.  The correction depends on the harmonic mean of the upstream
     * and downstream throat radii of curvature:
     *
     * <pre>
     *   κ  = (1/r_cd + 1/r_cu) / (2 r_t)
     *      = (throatCurvatureRatio + upstreamCurvatureRatio)
     *        / (2 · throatCurvatureRatio · upstreamCurvatureRatio · r_t / r_t)
     *      simplifies to a dimensionless ratio independent of r_t
     *
     *   Cd = max(0.98,  1 − 0.0023 · √((γ+1)/2.4) · κ)
     * </pre>
     *
     * <p>Coefficient calibrated to the axisymmetric Kliegel &amp; Levine (1969)
     * nozzle data.  For typical bell-nozzle parameters
     * (r_cd/r_t = 0.382, r_cu/r_t = 1.5, γ = 1.4) this gives Cd ≈ 0.9962.
     *
     * @return Cd ∈ [0.98, 1.0]
     */
    public double dischargeCoefficient() {
        // κ is independent of r_t because rcd and rcu scale with r_t
        double kappa = (throatCurvatureRatio + upstreamCurvatureRatio)
                       / (2.0 * throatCurvatureRatio * upstreamCurvatureRatio);
        double coeff = 0.0023 * Math.sqrt((gasProperties.gamma() + 1.0) / 2.4);
        return Math.max(0.98, 1.0 - coeff * kappa);
    }

    /**
     * Creates a builder for NozzleDesignParameters.
     *
     * @return New Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Calculates the design exit area ratio.
     *
     * @return Exit area ratio (Ae/At)
     */
    public double exitAreaRatio() {
        return gasProperties.areaRatio(exitMach);
    }
    
    /**
     * Calculates the exit radius.
     *
     * @return Exit radius in meters
     */
    public double exitRadius() {
        return throatRadius * Math.sqrt(exitAreaRatio());
    }
    
    /**
     * Calculates the throat area.
     *
     * @return Throat area in m²
     */
    public double throatArea() {
        if (axisymmetric) {
            return Math.PI * throatRadius * throatRadius;
        } else {
            return 2.0 * throatRadius * throatWidth; // height × span
        }
    }
    
    /**
     * Calculates the exit area.
     *
     * @return Exit area in m²
     */
    public double exitArea() {
        return throatArea() * exitAreaRatio();
    }
    
    /**
     * Calculates the nozzle pressure ratio.
     *
     * @return Pressure ratio (Pc/Pa)
     */
    public double pressureRatio() {
        return chamberPressure / ambientPressure;
    }
    
    /**
     * Calculates ideal exit pressure.
     *
     * @return Exit pressure in Pa
     */
    public double idealExitPressure() {
        return chamberPressure * gasProperties.isentropicPressureRatio(exitMach);
    }
    
    /**
     * Calculates exit temperature.
     *
     * @return Exit temperature in K
     */
    public double exitTemperature() {
        return chamberTemperature * gasProperties.isentropicTemperatureRatio(exitMach);
    }
    
    /**
     * Calculates exit velocity.
     *
     * @return Exit velocity in m/s
     */
    public double exitVelocity() {
        double te = exitTemperature();
        double ae = gasProperties.speedOfSound(te);
        return exitMach * ae;
    }
    
    /**
     * Calculates the characteristic exhaust velocity c* using the isentropic
     * throat relation: {@code c* = √(γRT_c) / (γ · (2/(γ+1))^((γ+1)/(2(γ−1))))}.
     *
     * @return Characteristic velocity c* in m/s
     */
    @SuppressWarnings("UnnecessaryLocalVariable")
    public double characteristicVelocity() {
        double gamma = gasProperties.gamma();
        double R = gasProperties.gasConstant();
        double Tc = chamberTemperature;
        double gp1 = gamma + 1;
        double gm1 = gamma - 1;
        return Math.sqrt(gamma * R * Tc) / gamma 
               / Math.pow(2.0 / gp1, gp1 / (2.0 * gm1));
    }
    
    /**
     * Calculates the ideal (isentropic) thrust coefficient including both the
     * momentum term and the pressure thrust term:
     * {@code Cf = √(2γ²/(γ−1) · (2/(γ+1))^((γ+1)/(γ−1)) · (1−(pe/pc)^((γ−1)/γ)))
     *           + (pe−pa)/pc · Ae/At}.
     *
     * @return Ideal thrust coefficient Cf (dimensionless)
     */
    public double idealThrustCoefficient() {
        double gamma = gasProperties.gamma();
        double gp1 = gamma + 1;
        double gm1 = gamma - 1;
        double pr = idealExitPressure() / chamberPressure;
        double term1 = 2.0 * gamma * gamma / gm1 * Math.pow(2.0 / gp1, gp1 / gm1);
        double term2 = 1.0 - Math.pow(pr, gm1 / gamma);
        double Cf = Math.sqrt(term1 * term2);
        // Add pressure thrust term
        Cf += (idealExitPressure() - ambientPressure) / chamberPressure * exitAreaRatio();
        return Cf;
    }
    
    /**
     * Calculates ideal specific impulse.
     *
     * @return Isp in seconds
     */
    public double idealSpecificImpulse() {
        return characteristicVelocity() * idealThrustCoefficient() / 9.80665;
    }
    
    /**
     * Calculates maximum turning angle (Prandtl-Meyer limit).
     *
     * @return Maximum Prandtl-Meyer angle in radians
     */
    public double maxPrandtlMeyerAngle() {
        double gamma = gasProperties.gamma();
        return (Math.PI / 2) * (Math.sqrt((gamma + 1) / (gamma - 1)) - 1);
    }
    
    /**
     * Fluent builder for {@link NozzleDesignParameters}.
     * All fields are pre-initialized to reasonable defaults (throat radius 50 mm,
     * exit Mach 3, chamber pressure 7 MPa, chamber temperature 3500 K,
     * sea-level ambient pressure, {@link GasProperties#AIR}, 30° initial wall
     * angle, length fraction 0.8, throat curvature ratio 0.382, axisymmetric
     * geometry).
     * Overriding any subset before calling {@link #build()} is sufficient.
     */
    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        /** Creates a {@code Builder} preloaded with all default parameter values. */
        public Builder() {}

        private double throatRadius = 0.05;
        private double exitMach = 3.0;
        private double chamberPressure = 7e6;
        private double chamberTemperature = 3500.0;
        private double ambientPressure = 101325.0;
        private GasProperties gasProperties = GasProperties.AIR;
        private int numberOfCharLines = DEFAULT_CHAR_LINES;
        private double wallAngleInitial = Math.toRadians(30.0);
        private double lengthFraction = 0.8;
        private boolean axisymmetric = true;
        private double throatWidth             = DEFAULT_THROAT_WIDTH;
        private double throatCurvatureRatio    = DEFAULT_THROAT_CURVATURE_RATIO;
        private double upstreamCurvatureRatio  = DEFAULT_UPSTREAM_CURVATURE_RATIO;
        private double convergentHalfAngle     = DEFAULT_CONVERGENT_HALF_ANGLE;
        private double contractionRatio        = DEFAULT_CONTRACTION_RATIO;

        /**
         * Sets the throat radius.
         *
         * @param throatRadius Throat radius in metres (must be positive)
         * @return This builder
         */
        public Builder throatRadius(double throatRadius) {
            this.throatRadius = throatRadius;
            return this;
        }

        /**
         * Sets the design exit Mach number.
         *
         * @param exitMach Supersonic exit Mach number (must be ≥ 1)
         * @return This builder
         */
        public Builder exitMach(double exitMach) {
            this.exitMach = exitMach;
            return this;
        }

        /**
         * Sets the chamber (stagnation) pressure.
         *
         * @param chamberPressure Chamber pressure in Pa (must exceed ambient pressure)
         * @return This builder
         */
        public Builder chamberPressure(double chamberPressure) {
            this.chamberPressure = chamberPressure;
            return this;
        }

        /**
         * Sets the chamber (stagnation) temperature.
         *
         * @param chamberTemperature Chamber temperature in K (must be positive)
         * @return This builder
         */
        public Builder chamberTemperature(double chamberTemperature) {
            this.chamberTemperature = chamberTemperature;
            return this;
        }

        /**
         * Sets the ambient back-pressure at the nozzle exit plane.
         *
         * @param ambientPressure Ambient pressure in Pa (must be positive)
         * @return This builder
         */
        public Builder ambientPressure(double ambientPressure) {
            this.ambientPressure = ambientPressure;
            return this;
        }

        /**
         * Sets the gas thermodynamic properties.
         *
         * @param gasProperties {@link GasProperties} instance for the propellant gas
         * @return This builder
         */
        public Builder gasProperties(GasProperties gasProperties) {
            this.gasProperties = gasProperties;
            return this;
        }

        /**
         * Sets the number of characteristic lines used in the MOC solution.
         *
         * @param numberOfCharLines Number of characteristic lines (must be ≥ 5)
         * @return This builder
         */
        public Builder numberOfCharLines(int numberOfCharLines) {
            this.numberOfCharLines = numberOfCharLines;
            return this;
        }

        /**
         * Sets the initial wall angle at the throat in radians.
         *
         * @param wallAngleInitial Initial wall half-angle in radians (must be in (0, π/4])
         * @return This builder
         */
        public Builder wallAngleInitial(double wallAngleInitial) {
            this.wallAngleInitial = wallAngleInitial;
            return this;
        }

        /**
         * Sets the initial wall angle at the throat in degrees (converted to radians
         * internally).
         *
         * @param degrees Initial wall half-angle in degrees (must be in (0°, 45°])
         * @return This builder
         */
        public Builder wallAngleInitialDegrees(double degrees) {
            this.wallAngleInitial = Math.toRadians(degrees);
            return this;
        }

        /**
         * Sets the nozzle length fraction relative to a 15° reference cone
         * (Rao optimization parameter).
         *
         * @param lengthFraction Fractional length in (0, 1]; 0.8 is typical for
         *                       a Rao bell nozzle
         * @return This builder
         */
        public Builder lengthFraction(double lengthFraction) {
            this.lengthFraction = lengthFraction;
            return this;
        }

        /**
         * Selects between axisymmetric and 2-D planar nozzle geometry.
         *
         * @param axisymmetric {@code true} for a body-of-revolution nozzle;
         *                     {@code false} for a 2-D planar nozzle (throat treated
         *                     as half-width per unit depth)
         * @return This builder
         */
        public Builder axisymmetric(boolean axisymmetric) {
            this.axisymmetric = axisymmetric;
            return this;
        }

        /**
         * Configures the builder for a 2-D planar nozzle geometry.
         * Equivalent to {@code axisymmetric(false)}.
         *
         * @return This builder
         */
        public Builder planar() {
            this.axisymmetric = false;
            return this;
        }

        /**
         * Sets the span (depth into the page) of a 2D rectangular nozzle.
         * Only meaningful when {@link #axisymmetric(boolean)} is set to
         * {@code false}; ignored for axisymmetric nozzles.
         *
         * <p>Used to compute the physical throat area
         * {@code At = 2 × throatRadius × throatWidth} and therefore the mass
         * flow rate and thrust for wind-tunnel or linear-aerospike designs.
         *
         * @param throatWidth Span in metres (must be positive); default 1.0 m
         *                    (per-unit-depth convention)
         * @return This builder
         */
        public Builder throatWidth(double throatWidth) {
            this.throatWidth = throatWidth;
            return this;
        }

        /**
         * Sets the downstream throat radius of curvature as a multiple of the
         * throat radius ({@code r_cd = ratio × r_t}).  Controls how sharply the
         * flow turns through the throat and directly seeds the initial expansion
         * wave fan used by the MOC solver.
         *
         * <p>Typical values:
         * <ul>
         *   <li>0.382 — classical Rao bell nozzle default (minimum recommended
         *       for uniform exit flow)</li>
         *   <li>0.5–1.0 — shorter nozzles or designs with tighter length budgets</li>
         *   <li>&gt; 1.0 — very gentle throat transition, used in high-performance
         *       research nozzles where exit uniformity is paramount</li>
         * </ul>
         *
         * @param throatCurvatureRatio dimensionless ratio r_cd / r_t; must be in (0, 2]
         * @return This builder
         */
        public Builder throatCurvatureRatio(double throatCurvatureRatio) {
            this.throatCurvatureRatio = throatCurvatureRatio;
            return this;
        }

        /**
         * Sets the upstream throat radius of curvature as a multiple of the
         * throat radius ({@code r_cu = ratio × r_t}).  Controls the shape of the
         * circular arc on the convergent side of the throat and determines the
         * curvature of the sonic line together with
         * {@link #throatCurvatureRatio(double)}.
         *
         * <p>Typical values:
         * <ul>
         *   <li>1.5 — standard bell nozzle design (default)</li>
         *   <li>0.8–1.0 — compact designs with a tight convergent arc</li>
         *   <li>2.0–3.0 — research nozzles requiring a very flat sonic line</li>
         * </ul>
         *
         * @param upstreamCurvatureRatio dimensionless ratio r_cu / r_t; must be in (0, 3]
         * @return This builder
         */
        public Builder upstreamCurvatureRatio(double upstreamCurvatureRatio) {
            this.upstreamCurvatureRatio = upstreamCurvatureRatio;
            return this;
        }

        /**
         * Sets the half-angle of the convergent cone in radians.
         * Used by the Jackson JSON deserializer to restore values written by
         * the record's {@code convergentHalfAngle()} accessor.
         * Prefer {@link #convergentHalfAngleDegrees(double)} in application code.
         *
         * @param convergentHalfAngle Half-angle in radians; must be in [5°, 60°]
         * @return This builder
         */
        public Builder convergentHalfAngle(double convergentHalfAngle) {
            this.convergentHalfAngle = convergentHalfAngle;
            return this;
        }

        /**
         * Sets the half-angle of the convergent cone in degrees (converted to
         * radians internally).
         *
         * @param degrees Half-angle in degrees; must be in [5°, 60°]
         * @return This builder
         */
        public Builder convergentHalfAngleDegrees(double degrees) {
            this.convergentHalfAngle = Math.toRadians(degrees);
            return this;
        }

        /**
         * Sets the combustion-chamber-to-throat area ratio (Ac/At).
         * Determines the chamber radius: {@code r_c = r_t × √(contractionRatio)}.
         *
         * @param contractionRatio Contraction ratio; must be in [1.5, 20]
         * @return This builder
         */
        public Builder contractionRatio(double contractionRatio) {
            this.contractionRatio = contractionRatio;
            return this;
        }

        /**
         * Builds and returns a validated {@link NozzleDesignParameters} instance
         * from the current builder state.
         *
         * @return A new, immutable {@link NozzleDesignParameters}
         * @throws IllegalArgumentException if any parameter fails the compact
         *         constructor's validation checks
         */
        public NozzleDesignParameters build() {
            return new NozzleDesignParameters(
                    throatRadius, exitMach, chamberPressure, chamberTemperature,
                    ambientPressure, gasProperties, numberOfCharLines,
                    wallAngleInitial, lengthFraction, axisymmetric, throatWidth,
                    throatCurvatureRatio, upstreamCurvatureRatio,
                    convergentHalfAngle, contractionRatio
            );
        }
    }
}
