package com.nozzle.core;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * Immutable record containing nozzle design parameters.
 * All dimensions are in SI units (meters, Pascals, Kelvin).
 *
 * @param throatRadius        Throat radius in meters
 * @param exitMach            Design exit Mach number
 * @param chamberPressure     Chamber (stagnation) pressure in Pa
 * @param chamberTemperature  Chamber (stagnation) temperature in K
 * @param ambientPressure     Ambient pressure in Pa
 * @param gasProperties       Gas thermodynamic properties
 * @param numberOfCharLines   Number of characteristic lines for MOC
 * @param wallAngleInitial    Initial wall angle at throat in radians
 * @param lengthFraction      Fractional length compared to 15° cone (Rao parameter)
 * @param axisymmetric        True for axisymmetric nozzle, false for 2D planar
 */
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
        boolean axisymmetric
) {
    
    /**
     * Default number of characteristic lines.
     */
    public static final int DEFAULT_CHAR_LINES = 50;
    
    /**
     * Compact constructor with validation.
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
        if (numberOfCharLines < 5) {
            throw new IllegalArgumentException("At least 5 characteristic lines required");
        }
        if (wallAngleInitial <= 0 || wallAngleInitial > Math.PI / 4) {
            throw new IllegalArgumentException("Initial wall angle must be between 0 and 45 degrees");
        }
        if (lengthFraction <= 0 || lengthFraction > 1.0) {
            throw new IllegalArgumentException("Length fraction must be between 0 and 1");
        }
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
            return 2.0 * throatRadius; // Per unit depth for 2D
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
     * Calculates characteristic exhaust velocity.
     *
     * @return C* in m/s
     */
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
     * Calculates ideal thrust coefficient.
     *
     * @return Ideal thrust coefficient
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
     * Builder class for NozzleDesignParameters.
     */
    public static class Builder {
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
        
        public Builder throatRadius(double throatRadius) {
            this.throatRadius = throatRadius;
            return this;
        }
        
        public Builder exitMach(double exitMach) {
            this.exitMach = exitMach;
            return this;
        }
        
        public Builder chamberPressure(double chamberPressure) {
            this.chamberPressure = chamberPressure;
            return this;
        }
        
        public Builder chamberTemperature(double chamberTemperature) {
            this.chamberTemperature = chamberTemperature;
            return this;
        }
        
        public Builder ambientPressure(double ambientPressure) {
            this.ambientPressure = ambientPressure;
            return this;
        }
        
        public Builder gasProperties(GasProperties gasProperties) {
            this.gasProperties = gasProperties;
            return this;
        }
        
        public Builder numberOfCharLines(int numberOfCharLines) {
            this.numberOfCharLines = numberOfCharLines;
            return this;
        }
        
        public Builder wallAngleInitial(double wallAngleInitial) {
            this.wallAngleInitial = wallAngleInitial;
            return this;
        }
        
        public Builder wallAngleInitialDegrees(double degrees) {
            this.wallAngleInitial = Math.toRadians(degrees);
            return this;
        }
        
        public Builder lengthFraction(double lengthFraction) {
            this.lengthFraction = lengthFraction;
            return this;
        }
        
        public Builder axisymmetric(boolean axisymmetric) {
            this.axisymmetric = axisymmetric;
            return this;
        }
        
        public Builder planar() {
            this.axisymmetric = false;
            return this;
        }
        
        public NozzleDesignParameters build() {
            return new NozzleDesignParameters(
                    throatRadius, exitMach, chamberPressure, chamberTemperature,
                    ambientPressure, gasProperties, numberOfCharLines,
                    wallAngleInitial, lengthFraction, axisymmetric
            );
        }
    }
}
