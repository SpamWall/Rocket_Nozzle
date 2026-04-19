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
    public Point2D linearInterpolate(Point2D other, double t) {
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
        return linearInterpolate(other, 0.5);
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

    @SuppressWarnings("NullableProblems")
    @Override
    public String toString() {
        return String.format("(%.6f, %.6f)", x, y);
    }
}
