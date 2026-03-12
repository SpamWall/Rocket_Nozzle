package com.nozzle.geometry;

import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.moc.CharacteristicPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents and enforces exact nozzle wall contour.
 * Supports various contour types: conical, bell, and custom.
 */
public class NozzleContour {
    
    /**
     * Contour type enumeration.
     */
    public enum ContourType {
        CONICAL,
        RAO_BELL,
        CUSTOM_SPLINE,
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
    
    public NozzleContour(ContourType type, NozzleDesignParameters parameters) {
        this.type = type;
        this.parameters = parameters;
        this.controlPoints = new ArrayList<>();
        this.contourPoints = new ArrayList<>();
    }
    
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
    
    public NozzleContour generate(int numPoints) {
        contourPoints.clear();
        
        switch (type) {
            case CONICAL -> generateConicalContour(numPoints);
            case RAO_BELL -> generateBellContour(numPoints);
            case CUSTOM_SPLINE, MOC_GENERATED -> generateSplineContour(numPoints);
        }
        
        return this;
    }
    
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
    
    public double getSlopeAt(double x) {
        double h = 1e-6;
        return (getRadiusAt(x + h) - getRadiusAt(x - h)) / (2 * h);
    }
    
    public double getAngleAt(double x) {
        return Math.atan(getSlopeAt(x));
    }
    
    public List<Point2D> getContourPoints() {
        return Collections.unmodifiableList(contourPoints);
    }
    

    public ContourType getType() {
        return type;
    }
    
    public double getLength() {
        if (contourPoints.isEmpty()) return 0;
        return contourPoints.getLast().x() - contourPoints.getFirst().x();
    }
    
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
