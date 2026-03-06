package com.nozzle.export;

import com.nozzle.geometry.NozzleContour;
import com.nozzle.geometry.Point2D;
import com.nozzle.moc.CharacteristicNet;
import com.nozzle.moc.CharacteristicPoint;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Exports nozzle geometry to DXF format for CAD import.
 */
public class DXFExporter {
    
    private static final String DXF_HEADER = """
            0
            SECTION
            2
            HEADER
            9
            $ACADVER
            1
            AC1015
            9
            $INSUNITS
            70
            4
            0
            ENDSEC
            0
            SECTION
            2
            ENTITIES
            """;
    
    private static final String DXF_FOOTER = """
            0
            ENDSEC
            0
            EOF
            """;
    
    private double scaleFactor = 1000.0;
    
    public DXFExporter setScaleFactor(double scale) {
        this.scaleFactor = scale;
        return this;
    }
    
    public void exportContour(NozzleContour contour, Path filePath) throws IOException {
        List<Point2D> points = contour.getContourPoints();
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Contour has no points");
        }
        
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write(DXF_HEADER);
            writePolyline(writer, points, "0");
            writeLine(writer, new Point2D(points.getFirst().x(), 0),
                    new Point2D(points.getLast().x(), 0), "0");
            writer.write(DXF_FOOTER);
        }
    }
    
    public void exportCharacteristicNet(CharacteristicNet net, Path filePath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write(DXF_HEADER);
            
            List<CharacteristicPoint> wallPoints = net.getWallPoints();
            if (!wallPoints.isEmpty()) {
                writeCharacteristicPolyline(writer, wallPoints, "0");
            }
            
            double xMax = wallPoints.isEmpty() ? 1.0 : wallPoints.getLast().x();
            writeLine(writer, new Point2D(0, 0), new Point2D(xMax, 0), "0");
            
            writer.write(DXF_FOOTER);
        }
    }
    
    public void exportRevolutionProfile(NozzleContour contour, Path filePath) throws IOException {
        List<Point2D> points = contour.getContourPoints();
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Contour has no points");
        }
        
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write(DXF_HEADER);
            writePolyline(writer, points, "0");
            
            Point2D exitTop = points.getLast();
            Point2D exitBottom = new Point2D(exitTop.x(), 0);
            Point2D throatBottom = new Point2D(points.getFirst().x(), 0);
            Point2D throatTop = points.getFirst();
            
            writeLine(writer, exitTop, exitBottom, "0");
            writeLine(writer, exitBottom, throatBottom, "0");
            writeLine(writer, throatBottom, throatTop, "0");
            
            writer.write(DXF_FOOTER);
        }
    }
    
    private void writePolyline(BufferedWriter writer, List<Point2D> points, String layer) 
            throws IOException {
        writer.write("0\nPOLYLINE\n8\n" + layer + "\n66\n1\n70\n0\n");
        for (Point2D point : points) {
            writeVertex(writer, point, layer);
        }
        writer.write("0\nSEQEND\n");
    }
    
    private void writeCharacteristicPolyline(BufferedWriter writer, 
                                              List<CharacteristicPoint> points, String layer) 
            throws IOException {
        writer.write("0\nPOLYLINE\n8\n" + layer + "\n66\n1\n70\n0\n");
        for (CharacteristicPoint point : points) {
            writeVertex(writer, new Point2D(point.x(), point.y()), layer);
        }
        writer.write("0\nSEQEND\n");
    }
    
    private void writeVertex(BufferedWriter writer, Point2D point, String layer) 
            throws IOException {
        writer.write("0\nVERTEX\n8\n" + layer + "\n");
        writer.write(String.format("10\n%.6f\n20\n%.6f\n30\n0.0\n", 
                point.x() * scaleFactor, point.y() * scaleFactor));
    }
    
    private void writeLine(BufferedWriter writer, Point2D start, Point2D end, String layer) 
            throws IOException {
        writer.write("0\nLINE\n8\n" + layer + "\n");
        writer.write(String.format("10\n%.6f\n20\n%.6f\n30\n0.0\n", 
                start.x() * scaleFactor, start.y() * scaleFactor));
        writer.write(String.format("11\n%.6f\n21\n%.6f\n31\n0.0\n", 
                end.x() * scaleFactor, end.y() * scaleFactor));
    }
}
