package com.nozzle.export;

import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.geometry.Point2D;
import com.nozzle.moc.AerospikeNozzle;
import com.nozzle.moc.AltitudePerformance;
import com.nozzle.moc.CharacteristicNet;
import com.nozzle.moc.CharacteristicPoint;
import com.nozzle.thermal.BoundaryLayerCorrection;
import com.nozzle.thermal.HeatTransferModel;
import com.nozzle.thermal.ThermalStressAnalysis;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Exports nozzle design data to comma-separated values (CSV) files suitable
 * for post-processing in spreadsheets or Python/MATLAB scripts.
 *
 * <p>Available export methods:
 * <ul>
 *   <li>{@link #exportCharacteristicNet} — full MOC net with flow-field data</li>
 *   <li>{@link #exportWallContour} — MOC wall points</li>
 *   <li>{@link #exportContour} — design contour with local wall angle</li>
 *   <li>{@link #exportThermalProfile} — wall temperatures and heat fluxes</li>
 *   <li>{@link #exportBoundaryLayerProfile} — boundary-layer thicknesses and skin friction</li>
 *   <li>{@link #exportDesignParameters} — scalar design parameters</li>
 *   <li>{@link #exportStressProfile} — thermal stress and fatigue data</li>
 *   <li>{@link #exportCompleteReport} — convenience wrapper for all of the above</li>
 * </ul>
 */
public class CSVExporter {

    /** Creates a {@code CSVExporter} with default settings. */
    public CSVExporter() {}

    /** Column delimiter used in all exported files. */
    private static final String DELIMITER = ",";
    /** Line terminator used in all exported files. */
    private static final String NEWLINE = "\n";
    
    /**
     * Exports characteristic net points to CSV.
     *
     * @param net      Characteristic net
     * @param filePath Output file path
     * @throws IOException If write fails
     */
    public void exportCharacteristicNet(CharacteristicNet net, Path filePath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            // Header
            writer.write("row,index,x,y,mach,theta_deg,nu_deg,mu_deg,pressure,temperature,density,velocity,type");
            writer.write(NEWLINE);
            
            List<List<CharacteristicPoint>> netPoints = net.getNetPoints();
            
            for (int row = 0; row < netPoints.size(); row++) {
                List<CharacteristicPoint> rowPoints = netPoints.get(row);
                for (int idx = 0; idx < rowPoints.size(); idx++) {
                    CharacteristicPoint point = rowPoints.get(idx);
                    writer.write(formatNetPoint(row, idx, point));
                    writer.write(NEWLINE);
                }
            }
        }
    }
    
    /**
     * Exports wall contour points to CSV.
     *
     * @param net      Characteristic net
     * @param filePath Output file path
     * @throws IOException If write fails
     */
    public void exportWallContour(CharacteristicNet net, Path filePath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("x,y,mach,theta_deg,pressure,temperature,velocity");
            writer.write(NEWLINE);
            
            for (CharacteristicPoint point : net.getWallPoints()) {
                writer.write(String.format("%.8f%s%.8f%s%.6f%s%.4f%s%.2f%s%.2f%s%.2f",
                        point.x(), DELIMITER,
                        point.y(), DELIMITER,
                        point.mach(), DELIMITER,
                        point.thetaDegrees(), DELIMITER,
                        point.pressure(), DELIMITER,
                        point.temperature(), DELIMITER,
                        point.velocity()));
                writer.write(NEWLINE);
            }
        }
    }
    
    /**
     * Exports nozzle contour to CSV.
     *
     * @param contour  Nozzle contour
     * @param filePath Output file path
     * @throws IOException If write fails
     */
    public void exportContour(NozzleContour contour, Path filePath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("x,y,angle_deg");
            writer.write(NEWLINE);
            
            for (Point2D point : contour.getContourPoints()) {
                double angle = Math.toDegrees(contour.getAngleAt(point.x()));
                writer.write(String.format("%.8f%s%.8f%s%.4f",
                        point.x(), DELIMITER,
                        point.y(), DELIMITER,
                        angle));
                writer.write(NEWLINE);
            }
        }
    }
    
    /**
     * Exports thermal profile to CSV.
     *
     * @param heatTransfer Heat transfer model
     * @param filePath     Output file path
     * @throws IOException If write fails
     */
    public void exportThermalProfile(HeatTransferModel heatTransfer, Path filePath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("x,y,wall_temp_K,total_heat_flux_W_m2,conv_heat_flux_W_m2,");
            writer.write("rad_heat_flux_W_m2,heat_transfer_coeff_W_m2K,recovery_temp_K");
            writer.write(NEWLINE);
            
            for (var point : heatTransfer.getWallThermalProfile()) {
                writer.write(String.format("%.8f%s%.8f%s%.2f%s%.2e%s%.2e%s%.2e%s%.2f%s%.2f",
                        point.x(), DELIMITER,
                        point.y(), DELIMITER,
                        point.wallTemperature(), DELIMITER,
                        point.totalHeatFlux(), DELIMITER,
                        point.convectiveHeatFlux(), DELIMITER,
                        point.radiativeHeatFlux(), DELIMITER,
                        point.heatTransferCoeff(), DELIMITER,
                        point.recoveryTemperature()));
                writer.write(NEWLINE);
            }
        }
    }
    
    /**
     * Exports boundary layer profile to CSV.
     *
     * @param boundaryLayer Boundary layer model
     * @param filePath      Output file path
     * @throws IOException If write fails
     */
    public void exportBoundaryLayerProfile(BoundaryLayerCorrection boundaryLayer, 
                                            Path filePath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("x,y,running_length,Reynolds,delta,delta_star,theta,cf,turbulent,mach");
            writer.write(NEWLINE);
            
            for (var point : boundaryLayer.getBoundaryLayerProfile()) {
                writer.write(String.format("%.8f%s%.8f%s%.8f%s%.2e%s%.6e%s%.6e%s%.6e%s%.6f%s%s%s%.4f",
                        point.x(), DELIMITER,
                        point.y(), DELIMITER,
                        point.runningLength(), DELIMITER,
                        point.reynoldsNumber(), DELIMITER,
                        point.thickness(), DELIMITER,
                        point.displacementThickness(), DELIMITER,
                        point.momentumThickness(), DELIMITER,
                        point.skinFrictionCoeff(), DELIMITER,
                        point.isTurbulent() ? "true" : "false", DELIMITER,
                        point.mach()));
                writer.write(NEWLINE);
            }
        }
    }
    
    /**
     * Exports design parameters to CSV.
     *
     * @param params   Design parameters
     * @param filePath Output file path
     * @throws IOException If write fails
     */
    public void exportDesignParameters(NozzleDesignParameters params, Path filePath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("parameter,value,unit");
            writer.write(NEWLINE);
            
            writeParameter(writer, "throat_radius", params.throatRadius(), "m");
            writeParameter(writer, "exit_radius", params.exitRadius(), "m");
            writeParameter(writer, "exit_mach", params.exitMach(), "-");
            writeParameter(writer, "exit_area_ratio", params.exitAreaRatio(), "-");
            writeParameter(writer, "chamber_pressure", params.chamberPressure(), "Pa");
            writeParameter(writer, "chamber_temperature", params.chamberTemperature(), "K");
            writeParameter(writer, "ambient_pressure", params.ambientPressure(), "Pa");
            writeParameter(writer, "gamma", params.gasProperties().gamma(), "-");
            writeParameter(writer, "molecular_weight", params.gasProperties().molecularWeight(), "kg/kmol");
            writeParameter(writer, "gas_constant", params.gasProperties().gasConstant(), "J/(kg·K)");
            writeParameter(writer, "wall_angle_initial", Math.toDegrees(params.wallAngleInitial()), "deg");
            writeParameter(writer, "length_fraction", params.lengthFraction(), "-");
            writeParameter(writer, "num_char_lines", params.numberOfCharLines(), "-");
            writeParameter(writer, "axisymmetric", params.axisymmetric() ? 1 : 0, "-");
            writeParameter(writer, "ideal_thrust_coeff", params.idealThrustCoefficient(), "-");
            writeParameter(writer, "ideal_specific_impulse", params.idealSpecificImpulse(), "s");
            writeParameter(writer, "exit_velocity", params.exitVelocity(), "m/s");
            writeParameter(writer, "cstar", params.characteristicVelocity(), "m/s");
        }
    }
    
    /**
     * Exports complete design report to CSV files.
     *
     * @param net       Characteristic net
     * @param contour   Nozzle contour (may be null)
     * @param heat      Heat transfer model (It may be null)
     * @param bl        Boundary layer model (It may be null)
     * @param outputDir Output directory
     * @throws IOException If write fails
     */
    public void exportCompleteReport(CharacteristicNet net,
                                      NozzleContour contour,
                                      HeatTransferModel heat,
                                      BoundaryLayerCorrection bl,
                                      Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        
        exportDesignParameters(net.getParameters(), outputDir.resolve("design_parameters.csv"));
        exportCharacteristicNet(net, outputDir.resolve("characteristic_net.csv"));
        exportWallContour(net, outputDir.resolve("wall_contour_moc.csv"));
        
        if (contour != null) {
            exportContour(contour, outputDir.resolve("wall_contour_design.csv"));
        }
        
        if (heat != null) {
            exportThermalProfile(heat, outputDir.resolve("thermal_profile.csv"));
        }
        
        if (bl != null) {
            exportBoundaryLayerProfile(bl, outputDir.resolve("boundary_layer.csv"));
        }
    }
    
    /**
     * Exports a thermal stress profile to CSV.
     *
     * @param analysis Completed ThermalStressAnalysis
     * @param filePath Output file path
     * @throws IOException If write fails
     */
    public void exportStressProfile(ThermalStressAnalysis analysis, Path filePath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("x_m,y_m,wall_temp_K,delta_T_K,"
                    + "sigma_thermal_MPa,sigma_hoop_pressure_MPa,sigma_axial_pressure_MPa,"
                    + "sigma_vm_MPa,safety_factor,fatigue_cycles");
            writer.write(NEWLINE);

            for (ThermalStressAnalysis.WallStressPoint p : analysis.getStressProfile()) {
                String nf = Double.isInfinite(p.estimatedCycles()) ? "Inf"
                        : String.format("%.2f", p.estimatedCycles());
                writer.write(String.format("%.8f%s%.8f%s%.2f%s%.2f%s%.4f%s%.4f%s%.4f%s%.4f%s%.4f%s%s",
                        p.x(), DELIMITER,
                        p.y(), DELIMITER,
                        p.wallTemperature(), DELIMITER,
                        p.deltaT(), DELIMITER,
                        p.thermalHoopStress()   / 1e6, DELIMITER,
                        p.pressureHoopStress()  / 1e6, DELIMITER,
                        p.pressureAxialStress() / 1e6, DELIMITER,
                        p.vonMisesStress()      / 1e6, DELIMITER,
                        p.safetyFactor(), DELIMITER,
                        nf));
                writer.write(NEWLINE);
            }
        }
    }

    /**
     * Formats a single characteristic-net point as a CSV row string (no trailing newline).
     *
     * @param row   Row index in the characteristic net (0 = initial data line)
     * @param idx   Column index within the row
     * @param point The characteristic point to format
     * @return CSV-formatted string with 13 comma-separated fields
     */
    private String formatNetPoint(int row, int idx, CharacteristicPoint point) {
        return String.format("%d%s%d%s%.8f%s%.8f%s%.6f%s%.4f%s%.4f%s%.4f%s%.2f%s%.2f%s%.4f%s%.2f%s%s",
                row, DELIMITER,
                idx, DELIMITER,
                point.x(), DELIMITER,
                point.y(), DELIMITER,
                point.mach(), DELIMITER,
                point.thetaDegrees(), DELIMITER,
                point.nuDegrees(), DELIMITER,
                point.muDegrees(), DELIMITER,
                point.pressure(), DELIMITER,
                point.temperature(), DELIMITER,
                point.density(), DELIMITER,
                point.velocity(), DELIMITER,
                point.pointType().name());
    }
    
    /**
     * Writes a single parameter row ({@code name,value,unit}) to the supplied writer,
     * followed by a newline.
     *
     * @param writer Output writer (must be open)
     * @param name   Parameter name (CSV key)
     * @param value  Numeric value formatted with 8 significant figures
     * @param unit   Physical unit string (e.g. {@code "m"}, {@code "Pa"}, {@code "-"})
     * @throws IOException If the underlying writer throws
     */
    private void writeParameter(BufferedWriter writer, String name, double value, String unit)
            throws IOException {
        writer.write(String.format("%s%s%.8g%s%s", name, DELIMITER, value, DELIMITER, unit));
        writer.write(NEWLINE);
    }

    /**
     * Exports aerospike spike contour points to CSV.
     *
     * <p>Both the full ideal spike and the truncated spike are written in a single
     * file; the {@code contour} column distinguishes them ({@code "full"} or
     * {@code "truncated"}).
     *
     * @param nozzle   Aerospike nozzle (must have been generated)
     * @param filePath Output file path
     * @throws IOException If write fails
     */
    public void exportSpikeContour(AerospikeNozzle nozzle, Path filePath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("contour,x_m,y_m");
            writer.write(NEWLINE);
            for (Point2D p : nozzle.getFullSpikeContour()) {
                writer.write(String.format("full%s%.8f%s%.8f", DELIMITER, p.x(), DELIMITER, p.y()));
                writer.write(NEWLINE);
            }
            for (Point2D p : nozzle.getTruncatedSpikeContour()) {
                writer.write(String.format("truncated%s%.8f%s%.8f",
                        DELIMITER, p.x(), DELIMITER, p.y()));
                writer.write(NEWLINE);
            }
        }
    }

    /**
     * Exports altitude-performance data to CSV.
     *
     * @param perf     Altitude performance record
     * @param filePath Output file path
     * @throws IOException If write fails
     */
    public void exportAltitudePerformance(AltitudePerformance perf, Path filePath)
            throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("ambient_pressure_Pa,aerospike_cf,bell_nozzle_cf,aerospike_isp_s");
            writer.write(NEWLINE);
            double[] pa   = perf.ambientPressures();
            double[] acf  = perf.aerospikeCf();
            double[] bcf  = perf.bellNozzleCf();
            double[] aisp = perf.aerospikeIsp();
            for (int i = 0; i < pa.length; i++) {
                writer.write(String.format("%.2f%s%.6f%s%.6f%s%.4f",
                        pa[i], DELIMITER, acf[i], DELIMITER, bcf[i], DELIMITER, aisp[i]));
                writer.write(NEWLINE);
            }
        }
    }

    /**
     * Exports a complete aerospike design report to CSV files in {@code outputDir}.
     *
     * <p>Creates three files:
     * <ul>
     *   <li>{@code aerospike_design_parameters.csv} — scalar design parameters</li>
     *   <li>{@code aerospike_spike_contour.csv} — full and truncated spike contours</li>
     *   <li>{@code aerospike_altitude_performance.csv} — per-altitude thrust and Isp</li>
     * </ul>
     *
     * @param nozzle           Aerospike nozzle (must have been generated)
     * @param ambientPressures Array of ambient pressures in Pa for the performance sweep
     * @param outputDir        Output directory (created if it does not exist)
     * @throws IOException If write fails
     */
    public void exportAerospikeReport(AerospikeNozzle nozzle, double[] ambientPressures,
                                       Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        exportDesignParameters(nozzle.getParameters(),
                outputDir.resolve("aerospike_design_parameters.csv"));
        exportSpikeContour(nozzle, outputDir.resolve("aerospike_spike_contour.csv"));
        AltitudePerformance perf = nozzle.calculateAltitudePerformance(ambientPressures);
        exportAltitudePerformance(perf, outputDir.resolve("aerospike_altitude_performance.csv"));
    }
}
