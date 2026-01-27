package com.nozzle.geometry;

/**
 * Immutable 2D point representation.
 *
 * @param x X coordinate
 * @param y Y coordinate
 */
public record Point2D(double x, double y) {
    
    /**
     * Origin point (0, 0).
     */
    public static final Point2D ORIGIN = new Point2D(0, 0);
    
    /**
     * Creates a point from polar coordinates.
     *
     * @param r     Radius
     * @param theta Angle in radians
     * @return Point2D
     */
    public static Point2D fromPolar(double r, double theta) {
        return new Point2D(r * Math.cos(theta), r * Math.sin(theta));
    }
    
    /**
     * Calculates distance to another point.
     *
     * @param other Other point
     * @return Euclidean distance
     */
    public double distanceTo(Point2D other) {
        double dx = x - other.x;
        double dy = y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
    
    /**
     * Calculates the magnitude (distance from origin).
     *
     * @return Magnitude
     */
    public double magnitude() {
        return Math.sqrt(x * x + y * y);
    }
    
    /**
     * Calculates the angle from origin.
     *
     * @return Angle in radians
     */
    public double angle() {
        return Math.atan2(y, x);
    }
    
    /**
     * Adds another point.
     *
     * @param other Point to add
     * @return New point
     */
    public Point2D add(Point2D other) {
        return new Point2D(x + other.x, y + other.y);
    }
    
    /**
     * Subtracts another point.
     *
     * @param other Point to subtract
     * @return New point
     */
    public Point2D subtract(Point2D other) {
        return new Point2D(x - other.x, y - other.y);
    }
    
    /**
     * Scales the point by a factor.
     *
     * @param factor Scale factor
     * @return New scaled point
     */
    public Point2D scale(double factor) {
        return new Point2D(x * factor, y * factor);
    }
    
    /**
     * Rotates the point around origin.
     *
     * @param angle Rotation angle in radians
     * @return New rotated point
     */
    public Point2D rotate(double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Point2D(x * cos - y * sin, x * sin + y * cos);
    }
    
    /**
     * Returns unit vector from origin to this point.
     *
     * @return Normalized point
     */
    public Point2D normalize() {
        double mag = magnitude();
        if (mag < 1e-10) {
            return ORIGIN;
        }
        return new Point2D(x / mag, y / mag);
    }
    
    /**
     * Calculates dot product with another point.
     *
     * @param other Other point
     * @return Dot product
     */
    public double dot(Point2D other) {
        return x * other.x + y * other.y;
    }
    
    /**
     * Calculates cross product (z-component).
     *
     * @param other Other point
     * @return Cross product z-component
     */
    public double cross(Point2D other) {
        return x * other.y - y * other.x;
    }
    
    /**
     * Linearly interpolates between this and another point.
     *
     * @param other Other point
     * @param t     Interpolation factor (0-1)
     * @return Interpolated point
     */
    public Point2D lerp(Point2D other, double t) {
        return new Point2D(
                x + t * (other.x - x),
                y + t * (other.y - y)
        );
    }
    
    /**
     * Calculates midpoint between this and another point.
     *
     * @param other Other point
     * @return Midpoint
     */
    public Point2D midpoint(Point2D other) {
        return lerp(other, 0.5);
    }
    
    /**
     * Reflects point across the x-axis.
     *
     * @return Reflected point
     */
    public Point2D reflectX() {
        return new Point2D(x, -y);
    }
    
    /**
     * Reflects point across the y-axis.
     *
     * @return Reflected point
     */
    public Point2D reflectY() {
        return new Point2D(-x, y);
    }
    
    @Override
    public String toString() {
        return String.format("(%.6f, %.6f)", x, y);
    }
}
