package com.nozzle.moc;

import java.util.Objects;

/**
 * Represents a point in the characteristic network.
 * Stores position, flow properties, and characteristic line information.
 *
 * @param x          Axial coordinate in meters
 * @param y          Radial coordinate in meters (r for axisymmetric)
 * @param mach       Local Mach number
 * @param theta      Flow angle in radians (relative to axis)
 * @param nu         Prandtl-Meyer angle in radians
 * @param mu         Mach angle in radians
 * @param pressure   Local static pressure in Pa
 * @param temperature Local static temperature in K
 * @param density    Local density in kg/m³
 * @param velocity   Local velocity magnitude in m/s
 * @param leftIndex  Index of left-running characteristic
 * @param rightIndex Index of right-running characteristic
 * @param pointType  Type of characteristic point
 */
public record CharacteristicPoint(
        double x,
        double y,
        double mach,
        double theta,
        double nu,
        double mu,
        double pressure,
        double temperature,
        double density,
        double velocity,
        int leftIndex,
        int rightIndex,
        PointType pointType
) {
    
    /**
     * Enumeration of characteristic point types.
     */
    public enum PointType {
        /** Point on the initial data line (throat) */
        INITIAL,
        /** Interior point (intersection of characteristics) */
        INTERIOR,
        /** Point on the wall boundary */
        WALL,
        /** Point on the centerline (axis of symmetry) */
        CENTERLINE,
        /** Point on the exit plane */
        EXIT
    }
    
    /**
     * Creates a point with basic flow properties.
     *
     * @param x     Axial coordinate
     * @param y     Radial coordinate
     * @param mach  Mach number
     * @param theta Flow angle in radians
     * @param nu    Prandtl-Meyer angle
     * @param mu    Mach angle
     * @return New CharacteristicPoint
     */
    public static CharacteristicPoint of(double x, double y, double mach, 
                                          double theta, double nu, double mu) {
        return new CharacteristicPoint(x, y, mach, theta, nu, mu, 
                                       0, 0, 0, 0, -1, -1, PointType.INTERIOR);
    }
    
    /**
     * Creates a point with full properties.
     *
     * @param x           Axial coordinate
     * @param y           Radial coordinate
     * @param mach        Mach number
     * @param theta       Flow angle
     * @param nu          Prandtl-Meyer angle
     * @param mu          Mach angle
     * @param pressure    Static pressure
     * @param temperature Static temperature
     * @param density     Density
     * @param velocity    Velocity magnitude
     * @param pointType   Point type
     * @return New CharacteristicPoint
     */
    public static CharacteristicPoint create(double x, double y, double mach,
                                              double theta, double nu, double mu,
                                              double pressure, double temperature,
                                              double density, double velocity,
                                              PointType pointType) {
        return new CharacteristicPoint(x, y, mach, theta, nu, mu,
                                       pressure, temperature, density, velocity,
                                       -1, -1, pointType);
    }
    
    /**
     * Returns a new point with updated indices.
     *
     * @param leftIdx  Left characteristic index
     * @param rightIdx Right characteristic index
     * @return New point with updated indices
     */
    public CharacteristicPoint withIndices(int leftIdx, int rightIdx) {
        return new CharacteristicPoint(x, y, mach, theta, nu, mu,
                                       pressure, temperature, density, velocity,
                                       leftIdx, rightIdx, pointType);
    }
    
    /**
     * Returns a new point with updated type.
     *
     * @param type New point type
     * @return New point with updated type
     */
    public CharacteristicPoint withType(PointType type) {
        return new CharacteristicPoint(x, y, mach, theta, nu, mu,
                                       pressure, temperature, density, velocity,
                                       leftIndex, rightIndex, type);
    }
    
    /**
     * Returns a new point with updated thermodynamic properties.
     *
     * @param p   Pressure
     * @param t   Temperature
     * @param rho Density
     * @param v   Velocity
     * @return New point with properties
     */
    public CharacteristicPoint withThermodynamics(double p, double t, double rho, double v) {
        return new CharacteristicPoint(x, y, mach, theta, nu, mu,
                                       p, t, rho, v, leftIndex, rightIndex, pointType);
    }
    
    /**
     * Calculates the left-running characteristic slope.
     *
     * @return Slope dy/dx for C- characteristic
     */
    public double leftCharacteristicSlope() {
        return Math.tan(theta - mu);
    }
    
    /**
     * Calculates the right-running characteristic slope.
     *
     * @return Slope dy/dx for C+ characteristic
     */
    public double rightCharacteristicSlope() {
        return Math.tan(theta + mu);
    }
    
    /**
     * Calculates the Riemann invariant for left-running characteristic.
     * Q- = theta + nu (constant along C- in simple case)
     *
     * @return Q- invariant
     */
    public double leftRiemannInvariant() {
        return theta + nu;
    }
    
    /**
     * Calculates the Riemann invariant for right-running characteristic.
     * Q+ = theta - nu (constant along C+ in simple case)
     *
     * @return Q+ invariant
     */
    public double rightRiemannInvariant() {
        return theta - nu;
    }
    
    /**
     * Calculates distance to another point.
     *
     * @param other Other point
     * @return Euclidean distance
     */
    public double distanceTo(CharacteristicPoint other) {
        double dx = x - other.x;
        double dy = y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
    
    /**
     * Checks if this point is on the axis (y approximately zero).
     *
     * @param tolerance Tolerance for axis check
     * @return True if point is on axis
     */
    public boolean isOnAxis(double tolerance) {
        return Math.abs(y) < tolerance;
    }
    
    /**
     * Returns flow angle in degrees.
     *
     * @return Flow angle in degrees
     */
    public double thetaDegrees() {
        return Math.toDegrees(theta);
    }
    
    /**
     * Returns Mach angle in degrees.
     *
     * @return Mach angle in degrees
     */
    public double muDegrees() {
        return Math.toDegrees(mu);
    }
    
    /**
     * Returns Prandtl-Meyer angle in degrees.
     *
     * @return Prandtl-Meyer angle in degrees
     */
    public double nuDegrees() {
        return Math.toDegrees(nu);
    }
    
    /**
     * Calculates local area ratio based on radial position.
     *
     * @param throatRadius Throat radius
     * @return Local area ratio
     */
    public double localAreaRatio(double throatRadius) {
        return (y * y) / (throatRadius * throatRadius);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CharacteristicPoint that)) return false;
        return Double.compare(x, that.x) == 0 &&
               Double.compare(y, that.y) == 0 &&
               Double.compare(mach, that.mach) == 0 &&
               Double.compare(theta, that.theta) == 0;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(x, y, mach, theta);
    }
    
    @Override
    public String toString() {
        return String.format("Point[x=%.6f, y=%.6f, M=%.4f, θ=%.2f°, ν=%.2f°, type=%s]",
                x, y, mach, thetaDegrees(), nuDegrees(), pointType);
    }
}
