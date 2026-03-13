package com.nozzle.geometry;

import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.moc.CharacteristicPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the divergent wall contour of a rocket nozzle and provides
 * geometry services (radius, slope, surface area) used by heat-transfer
 * and performance analyses.
 *
 * <p>Four contour families are supported:
 * <ul>
 *   <li>{@link ContourType#CONICAL} — straight conical wall, the simplest baseline.</li>
 *   <li>{@link ContourType#RAO_BELL} — cubic Bézier bell contour that approximates the
 *       Rao thrust-optimized shape without a full MOC solve.</li>
 *   <li>{@link ContourType#CUSTOM_SPLINE} — user-supplied control points fitted with a
 *       natural cubic spline.</li>
 *   <li>{@link ContourType#MOC_GENERATED} — wall points produced by {@link
 *       com.nozzle.moc.CharacteristicNet} and smoothed via
 *       {@link #fromMOCWallPoints(NozzleDesignParameters, java.util.List)}.</li>
 * </ul>
 *
 * <p>Usage example:
 * <pre>{@code
 * NozzleContour contour = new NozzleContour(ContourType.RAO_BELL, params);
 * contour.generate(200);
 * double r = contour.getRadiusAt(0.05); // radius at x = 50 mm
 * }</pre>
 */
public class NozzleContour {

    /**
     * Classification of the mathematical method used to define the divergent
     * wall profile.
     */
    public enum ContourType {
        /** Straight-walled conical nozzle with a circular-arc throat transition. */
        CONICAL,
        /** Rao-style bell nozzle approximated with a cubic Bézier curve. */
        RAO_BELL,
        /**
         * Nozzle wall defined by user-supplied control points and interpolated
         * with a natural cubic spline.
         */
        CUSTOM_SPLINE,
        /**
         * Wall points generated directly by the Method-of-Characteristics solver
         * ({@link com.nozzle.moc.CharacteristicNet}) and smoothed with a cubic
         * spline.  Use {@link NozzleContour#fromMOCWallPoints} to construct.
         */
        MOC_GENERATED
    }
    
    private final ContourType type;
    private final NozzleDesignParameters parameters;
    private final List<Point2D> controlPoints;
    private final List<Point2D> contourPoints;
    
    private double[] splineCoeffA;
    private double[] splineCoeffB;
    private double[] splineCoeffC;
    private double[] splineCoeffD;
    private double[] splineX;
    
    /**
     * Creates an empty contour of the given type.
     * Call {@link #generate(int)} (or {@link #fromMOCWallPoints} for
     * {@link ContourType#MOC_GENERATED}) to populate the contour points.
     *
     * @param type       Contour family to generate
     * @param parameters Nozzle design parameters that define throat radius,
     *                   exit radius, wall angle, and length fraction
     */
    public NozzleContour(ContourType type, NozzleDesignParameters parameters) {
        this.type = type;
        this.parameters = parameters;
        this.controlPoints = new ArrayList<>();
        this.contourPoints = new ArrayList<>();
    }
    
    /**
     * Factory method that builds a {@link ContourType#MOC_GENERATED} contour from
     * the wall-point sequence produced by a {@link com.nozzle.moc.CharacteristicNet}.
     *
     * <p>Consecutive points closer than {@code 0.05 × r_throat} are skipped to keep
     * the underlying cubic-spline system well-conditioned (closely spaced knots cause
     * the tridiagonal h-coefficients to approach zero).
     *
     * @param parameters  Nozzle design parameters (used to determine the minimum
     *                    knot spacing and as the reference for downstream queries)
     * @param wallPoints  Ordered list of wall characteristic points from the MOC
     *                    solver; must contain at least two points after filtering
     * @return A fully initialised {@code NozzleContour} with its cubic spline fitted
     *         to the filtered MOC wall points
     */
    public static NozzleContour fromMOCWallPoints(NozzleDesignParameters parameters,
                                                   List<CharacteristicPoint> wallPoints) {
        NozzleContour contour = new NozzleContour(ContourType.MOC_GENERATED, parameters);
        // Minimum x-spacing to keep cubic spline well-conditioned.
        // Closely-spaced control points (h -> 0) cause 3/h terms to explode.
        double minSpacing = parameters.throatRadius() * 0.05;
        double lastX = Double.NEGATIVE_INFINITY;
        for (CharacteristicPoint point : wallPoints) {
            if (point.x() - lastX >= minSpacing) {
                contour.controlPoints.add(new Point2D(point.x(), point.y()));
                lastX = point.x();
            }
        }
        contour.generateSpline();
        return contour;
    }
    
    /**
     * Generates (or regenerates) the discrete wall-contour point list.
     * The generation algorithm is selected by the {@link ContourType} supplied
     * at construction time.
     *
     * @param numPoints Total number of contour points to generate, including
     *                  both the throat arc region and the divergent bell or
     *                  cone section
     * @return This instance for method chaining
     */
    public NozzleContour generate(int numPoints) {
        contourPoints.clear();
        
        switch (type) {
            case CONICAL -> generateConicalContour(numPoints);
            case RAO_BELL -> generateBellContour(numPoints);
            case CUSTOM_SPLINE, MOC_GENERATED -> generateSplineContour(numPoints);
        }
        
        return this;
    }
    
    /**
     * Populates {@code contourPoints} with a conical divergent section preceded
     * by a 0.382 × r_throat circular-arc throat transition.
     * The cone half-angle is taken from {@link NozzleDesignParameters#wallAngleInitial()}.
     *
     * @param numPoints Total points to distribute between the throat arc and cone
     */
    private void generateConicalContour(int numPoints) {
        double rt = parameters.throatRadius();
        double re = parameters.exitRadius();
        double halfAngle = parameters.wallAngleInitial();
        double length = (re - rt) / Math.tan(halfAngle);
        
        double rThroat = 0.382 * rt;
        int throatPoints = numPoints / 5;
        
        for (int i = 0; i <= throatPoints; i++) {
            double angle = i * halfAngle / throatPoints;
            double x = rThroat * Math.sin(angle);
            double y = rt + rThroat * (1 - Math.cos(angle));
            contourPoints.add(new Point2D(x, y));
        }
        
        double xStart = contourPoints.getLast().x();
        double yStart = contourPoints.getLast().y();
        int conePoints = numPoints - throatPoints - 1;
        
        for (int i = 1; i <= conePoints; i++) {
            double x = xStart + i * (length - xStart) / conePoints;
            double y = yStart + (x - xStart) * Math.tan(halfAngle);
            contourPoints.add(new Point2D(x, y));
        }
    }
    
    /**
     * Populates {@code contourPoints} with an approximate Rao bell contour.
     * A circular-arc throat section is followed by a cubic Bézier curve that
     * blends from the throat inflection angle ({@link NozzleDesignParameters#wallAngleInitial()})
     * to a small exit angle derived from the area ratio.
     *
     * @param numPoints Total points to distribute between the throat arc and the
     *                  Bézier bell
     */
    private void generateBellContour(int numPoints) {
        double rt = parameters.throatRadius();
        double re = parameters.exitRadius();
        double thetaN = parameters.wallAngleInitial();
        double lengthFrac = parameters.lengthFraction();
        
        double coneLength = (re - rt) / Math.tan(Math.toRadians(15));
        double actualLength = coneLength * lengthFrac;
        
        double thetaE = Math.toRadians(8.0 - 0.5 * Math.log(parameters.exitAreaRatio()));
        thetaE = Math.max(thetaE, Math.toRadians(1));
        
        double rcd = 0.382 * rt;
        int throatPoints = numPoints / 5;
        
        for (int i = 0; i <= throatPoints; i++) {
            double angle = i * thetaN / throatPoints;
            double x = rcd * Math.sin(angle);
            double y = rt + rcd * (1 - Math.cos(angle));
            contourPoints.add(new Point2D(x, y));
        }
        
        Point2D p0 = contourPoints.getLast();
        Point2D p3 = new Point2D(actualLength, re);
        double dx = p3.x() - p0.x();
        Point2D p1 = new Point2D(p0.x() + dx / 3, p0.y() + dx / 3 * Math.tan(thetaN));
        Point2D p2 = new Point2D(p3.x() - dx / 3, p3.y() - dx / 3 * Math.tan(thetaE));
        
        int bellPoints = numPoints - throatPoints - 1;
        for (int i = 1; i <= bellPoints; i++) {
            double t = (double) i / bellPoints;
            double u = 1 - t;
            
            double x = u*u*u*p0.x() + 3*u*u*t*p1.x() + 3*u*t*t*p2.x() + t*t*t*p3.x();
            double y = u*u*u*p0.y() + 3*u*u*t*p1.y() + 3*u*t*t*p2.y() + t*t*t*p3.y();
            contourPoints.add(new Point2D(x, y));
        }
    }
    
    /**
     * Fits a natural cubic spline through {@code controlPoints} and stores the
     * piecewise polynomial coefficients in {@code splineCoeffA/B/C/D}.
     * The algorithm follows the standard tridiagonal-system formulation
     * (Burden &amp; Faires, §3.5) with not-a-knot boundary conditions
     * ({@code c[0] = c[n] = 0}).
     * Has no effect if fewer than two control points are present.
     */
    private void generateSpline() {
        if (controlPoints.size() < 2) return;
        
        int n = controlPoints.size() - 1;
        splineX = new double[n + 1];
        double[] y = new double[n + 1];
        
        for (int i = 0; i <= n; i++) {
            splineX[i] = controlPoints.get(i).x();
            y[i] = controlPoints.get(i).y();
        }
        
        double[] h = new double[n];
        for (int i = 0; i < n; i++) {
            h[i] = splineX[i + 1] - splineX[i];
        }
        
        double[] alpha = new double[n];
        for (int i = 1; i < n; i++) {
            alpha[i] = 3.0/h[i] * (y[i+1] - y[i]) - 3.0/h[i-1] * (y[i] - y[i-1]);
        }
        
        double[] l = new double[n + 1];
        double[] mu = new double[n + 1];
        double[] z = new double[n + 1];
        
        l[0] = 1; mu[0] = 0; z[0] = 0;
        
        for (int i = 1; i < n; i++) {
            l[i] = 2*(splineX[i+1] - splineX[i-1]) - h[i-1]*mu[i-1];
            mu[i] = h[i] / l[i];
            z[i] = (alpha[i] - h[i-1]*z[i-1]) / l[i];
        }
        
        l[n] = 1; z[n] = 0;
        
        splineCoeffA = new double[n];
        splineCoeffB = new double[n];
        splineCoeffC = new double[n + 1];
        splineCoeffD = new double[n];
        
        splineCoeffC[n] = 0;
        
        for (int j = n - 1; j >= 0; j--) {
            splineCoeffC[j] = z[j] - mu[j]*splineCoeffC[j+1];
            splineCoeffB[j] = (y[j+1] - y[j])/h[j] - h[j]*(splineCoeffC[j+1] + 2*splineCoeffC[j])/3;
            splineCoeffD[j] = (splineCoeffC[j+1] - splineCoeffC[j]) / (3*h[j]);
            splineCoeffA[j] = y[j];
        }
    }
    
    /**
     * Populates {@code contourPoints} by sampling the fitted cubic spline at
     * {@code numPoints} uniformly spaced axial positions spanning the range of
     * the control points.
     * If the spline coefficients have not yet been computed, calls
     * {@link #generateSpline()} first.
     * Has no effect if fewer than two control points are present.
     *
     * @param numPoints Number of sample points to generate
     */
    private void generateSplineContour(int numPoints) {
        if (controlPoints.size() < 2) return;
        if (splineCoeffA == null) generateSpline();
        
        double xMin = controlPoints.getFirst().x();
        double xMax = controlPoints.getLast().x();
        
        for (int i = 0; i < numPoints; i++) {
            double x = xMin + i * (xMax - xMin) / (numPoints - 1);
            double y = evaluateSpline(x);
            contourPoints.add(new Point2D(x, y));
        }
    }
    
    /**
     * Evaluates the fitted cubic spline at axial position {@code x}.
     * Performs a linear search for the enclosing interval and applies the
     * Horner-form polynomial {@code a + b·dx + c·dx² + d·dx³}.
     * Returns {@code 0} if the spline has not been initialized.
     *
     * @param x Axial position in metres (clamped to the spline domain by the
     *          interval search — out-of-range values use the last valid interval)
     * @return Interpolated wall radius in metres
     */
    private double evaluateSpline(double x) {
        if (splineX == null || splineX.length < 2) return 0;
        
        int i = 0;
        for (int j = 0; j < splineX.length - 1; j++) {
            if (x >= splineX[j] && x <= splineX[j + 1]) {
                i = j;
                break;
            }
        }
        
        double dx = x - splineX[i];
        return splineCoeffA[i] + splineCoeffB[i]*dx + splineCoeffC[i]*dx*dx + splineCoeffD[i]*dx*dx*dx;
    }
    
    /**
     * Returns the wall radius at the given axial position by linear interpolation
     * of the discrete contour points (or spline evaluation for spline-based types).
     * If no contour points have been generated yet, {@link #generate(int)} is
     * called with 100 points automatically.
     *
     * @param x Axial position in metres
     * @return Wall radius in metres; returns the exit radius if {@code x} is beyond
     *         the last contour point
     */
    public double getRadiusAt(double x) {
        if (contourPoints.isEmpty()) {
            generate(100);
        }
        
        if (type == ContourType.CUSTOM_SPLINE || type == ContourType.MOC_GENERATED) {
            return evaluateSpline(x);
        }
        
        for (int i = 1; i < contourPoints.size(); i++) {
            Point2D prev = contourPoints.get(i - 1);
            Point2D curr = contourPoints.get(i);
            if (x >= prev.x() && x <= curr.x()) {
                double t = (x - prev.x()) / (curr.x() - prev.x());
                return prev.y() + t * (curr.y() - prev.y());
            }
        }
        return parameters.exitRadius();
    }
    
    /**
     * Returns the wall slope (dr/dx) at axial position {@code x} using
     * a central-difference approximation with step {@code h = 1 µm}.
     *
     * @param x Axial position in metres
     * @return Wall slope (dimensionless, dr/dx)
     */
    public double getSlopeAt(double x) {
        double h = 1e-6;
        return (getRadiusAt(x + h) - getRadiusAt(x - h)) / (2 * h);
    }
    
    /**
     * Returns the local wall half-angle (in radians) at axial position {@code x}.
     * Computed as {@code atan(dr/dx)} via {@link #getSlopeAt(double)}.
     *
     * @param x Axial position in metres
     * @return Wall half-angle in radians
     */
    public double getAngleAt(double x) {
        return Math.atan(getSlopeAt(x));
    }
    
    /**
     * Returns an unmodifiable view of the discrete contour point list.
     * Each {@link Point2D} holds the axial ({@code x}) and radial ({@code y})
     * coordinates in metres.
     *
     * @return Unmodifiable list of contour points; empty if {@link #generate(int)}
     *         has not been called
     */
    public List<Point2D> getContourPoints() {
        return Collections.unmodifiableList(contourPoints);
    }

    /**
     * Returns the contour type used to construct this instance.
     *
     * @return The {@link ContourType} of this contour
     */
    public ContourType getType() {
        return type;
    }
    
    /**
     * Returns the axial length of the contour from its first to its last
     * discrete point.
     *
     * @return Contour axial length in metres, or {@code 0} if no points have
     *         been generated
     */
    public double getLength() {
        if (contourPoints.isEmpty()) return 0;
        return contourPoints.getLast().x() - contourPoints.getFirst().x();
    }
    
    /**
     * Calculates the inner wetted surface area of the axisymmetric nozzle wall
     * using the trapezoidal rule over the discrete contour segments:
     * {@code dA = 2π · r_avg · ds}.
     *
     * @return Wetted surface area in m²; returns {@code 0} if fewer than two
     *         contour points are available
     */
    public double calculateSurfaceArea() {
        if (contourPoints.size() < 2) return 0;
        
        double area = 0;
        for (int i = 1; i < contourPoints.size(); i++) {
            Point2D p1 = contourPoints.get(i - 1);
            Point2D p2 = contourPoints.get(i);
            double ds = p1.distanceTo(p2);
            double rAvg = (p1.y() + p2.y()) / 2;
            area += 2 * Math.PI * rAvg * ds;
        }
        return area;
    }
}
