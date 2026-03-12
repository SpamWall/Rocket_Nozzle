package com.nozzle.export;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.moc.CharacteristicNet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Export Tests")
class Export_UT {
    
    private NozzleDesignParameters params;
    private NozzleContour contour;
    private CharacteristicNet net;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(2.5)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.AIR)
                .numberOfCharLines(10)
                .wallAngleInitialDegrees(25)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();
        
        contour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
        contour.generate(50);
        
        net = new CharacteristicNet(params).generate();
    }
    
    @Nested
    @DisplayName("CSV Exporter Tests")
    class CSVExporterTests {
        
        @Test
        @DisplayName("Should export wall contour to CSV")
        void shouldExportWallContourToCSV() throws IOException {
            CSVExporter exporter = new CSVExporter();
            Path outputFile = tempDir.resolve("wall_contour.csv");
            
            exporter.exportWallContour(net, outputFile);
            
            assertThat(outputFile).exists();
            String content = Files.readString(outputFile);
            assertThat(content).contains("x,y,mach");
        }
        
        @Test
        @DisplayName("Should export design parameters to CSV")
        void shouldExportDesignParametersToCSV() throws IOException {
            CSVExporter exporter = new CSVExporter();
            Path outputFile = tempDir.resolve("params.csv");
            
            exporter.exportDesignParameters(params, outputFile);
            
            assertThat(outputFile).exists();
            String content = Files.readString(outputFile);
            assertThat(content).contains("parameter,value,unit");
            assertThat(content).contains("throat_radius");
        }
        
        @Test
        @DisplayName("Should export contour to CSV")
        void shouldExportContourToCSV() throws IOException {
            CSVExporter exporter = new CSVExporter();
            Path outputFile = tempDir.resolve("contour.csv");
            
            exporter.exportContour(contour, outputFile);
            
            assertThat(outputFile).exists();
        }
    }
    
    @Nested
    @DisplayName("DXF Exporter Tests")
    class DXFExporterTests {
        
        @Test
        @DisplayName("Should export contour to DXF")
        void shouldExportContourToDXF() throws IOException {
            DXFExporter exporter = new DXFExporter();
            Path outputFile = tempDir.resolve("nozzle.dxf");
            
            exporter.exportContour(contour, outputFile);
            
            assertThat(outputFile).exists();
            String content = Files.readString(outputFile);
            assertThat(content).contains("SECTION");
            assertThat(content).contains("ENTITIES");
            assertThat(content).contains("EOF");
        }
        
        @Test
        @DisplayName("Should export revolution profile to DXF")
        void shouldExportRevolutionProfileToDXF() throws IOException {
            DXFExporter exporter = new DXFExporter();
            Path outputFile = tempDir.resolve("revolution.dxf");
            
            exporter.exportRevolutionProfile(contour, outputFile);
            
            assertThat(outputFile).exists();
        }
        
        @Test
        @DisplayName("Should respect scale factor")
        void shouldRespectScaleFactor() throws IOException {
            DXFExporter exporter = new DXFExporter().setScaleFactor(1000);
            Path outputFile = tempDir.resolve("scaled.dxf");
            
            exporter.exportContour(contour, outputFile);
            
            assertThat(outputFile).exists();
        }
    }
    
    @Nested
    @DisplayName("STEP Exporter Tests")
    class STEPExporterTests {
        
        @Test
        @DisplayName("Should export to STEP format")
        void shouldExportToSTEP() throws IOException {
            STEPExporter exporter = new STEPExporter();
            Path outputFile = tempDir.resolve("nozzle.step");
            
            exporter.exportRevolvedSolid(contour, outputFile);
            
            assertThat(outputFile).exists();
            String content = Files.readString(outputFile);
            assertThat(content).contains("ISO-10303-21");
            assertThat(content).contains("HEADER");
            assertThat(content).contains("DATA");
        }
        
        @Test
        @DisplayName("Should export profile curve to STEP")
        void shouldExportProfileCurveToSTEP() throws IOException {
            STEPExporter exporter = new STEPExporter();
            Path outputFile = tempDir.resolve("profile.step");
            
            exporter.exportProfileCurve(contour, outputFile);
            
            assertThat(outputFile).exists();
        }
    }
    
    @Nested
    @DisplayName("STL Exporter Tests")
    class STLExporterTests {
        
        @Test
        @DisplayName("Should export to binary STL")
        void shouldExportToBinarySTL() throws IOException {
            STLExporter exporter = new STLExporter()
                    .setCircumferentialSegments(36)
                    .setBinaryFormat(true);
            Path outputFile = tempDir.resolve("nozzle.stl");
            
            exporter.exportMesh(contour, outputFile);
            
            assertThat(outputFile).exists();
            assertThat(Files.size(outputFile)).isGreaterThan(80); // At least header size
        }
        
        @Test
        @DisplayName("Should export to ASCII STL")
        void shouldExportToAsciiSTL() throws IOException {
            STLExporter exporter = new STLExporter()
                    .setCircumferentialSegments(18)
                    .setBinaryFormat(false);
            Path outputFile = tempDir.resolve("nozzle_ascii.stl");
            
            exporter.exportMesh(contour, outputFile);
            
            assertThat(outputFile).exists();
            String content = Files.readString(outputFile);
            assertThat(content).contains("solid");
            assertThat(content).contains("facet normal");
            assertThat(content).contains("endsolid");
        }
        
        @Test
        @DisplayName("Should estimate triangle count")
        void shouldEstimateTriangleCount() {
            STLExporter exporter = new STLExporter().setCircumferentialSegments(36);
            
            int count = exporter.estimateTriangleCount(50);
            assertThat(count).isGreaterThan(0);
        }
    }
    
    @Nested
    @DisplayName("OpenFOAM Exporter Tests")
    class OpenFOAMExporterTests {

        @Test
        @DisplayName("Should create all required directories and files")
        void shouldCreateAllRequiredFiles() throws IOException {
            Path caseDir = tempDir.resolve("nozzle_case");
            new OpenFOAMExporter().exportCase(params, contour, caseDir);

            assertThat(caseDir.resolve("system/blockMeshDict")).exists();
            assertThat(caseDir.resolve("system/controlDict")).exists();
            assertThat(caseDir.resolve("system/fvSchemes")).exists();
            assertThat(caseDir.resolve("system/fvSolution")).exists();
            assertThat(caseDir.resolve("constant/thermophysicalProperties")).exists();
            assertThat(caseDir.resolve("constant/turbulenceProperties")).exists();
            assertThat(caseDir.resolve("0/p")).exists();
            assertThat(caseDir.resolve("0/T")).exists();
            assertThat(caseDir.resolve("0/U")).exists();
            assertThat(caseDir.resolve("0/k")).exists();
            assertThat(caseDir.resolve("0/omega")).exists();
        }

        @Test
        @DisplayName("blockMeshDict should contain FoamFile header, vertices, spline edges, and wedge patches")
        void blockMeshDictShouldContainRequiredSections() throws IOException {
            Path caseDir = tempDir.resolve("nozzle_bmd");
            new OpenFOAMExporter().exportCase(params, contour, caseDir);

            String content = Files.readString(caseDir.resolve("system/blockMeshDict"));
            assertThat(content).contains("FoamFile");
            assertThat(content).contains("vertices");
            assertThat(content).contains("spline");
            assertThat(content).contains("wedge");
            assertThat(content).contains("axis");
        }

        @Test
        @DisplayName("thermophysicalProperties should contain gas mixture data derived from params")
        void thermophysicalPropertiesShouldContainGasData() throws IOException {
            Path caseDir = tempDir.resolve("nozzle_thermo");
            new OpenFOAMExporter().exportCase(params, contour, caseDir);

            String content = Files.readString(caseDir.resolve("constant/thermophysicalProperties"));
            assertThat(content).contains("hePsiThermo");
            assertThat(content).contains("molWeight");
            assertThat(content).contains("Cp");
            assertThat(content).contains("As");
        }

        @Test
        @DisplayName("controlDict should specify rhoCentralFoam as the application")
        void controlDictShouldSpecifyRhoCentralFoam() throws IOException {
            Path caseDir = tempDir.resolve("nozzle_ctrl");
            new OpenFOAMExporter().exportCase(params, contour, caseDir);

            String content = Files.readString(caseDir.resolve("system/controlDict"));
            assertThat(content).contains("rhoCentralFoam");
        }

        @Test
        @DisplayName("pressure field should contain totalPressure inlet BC")
        void pressureFieldShouldContainTotalPressureBC() throws IOException {
            Path caseDir = tempDir.resolve("nozzle_p");
            new OpenFOAMExporter().exportCase(params, contour, caseDir);

            String content = Files.readString(caseDir.resolve("0/p"));
            assertThat(content).contains("totalPressure");
            assertThat(content).contains("waveTransmissive");
        }

        @Test
        @DisplayName("turbulenceProperties should specify kOmegaSST when turbulence is enabled")
        void turbulencePropertiesShouldSpecifyKOmegaSST() throws IOException {
            Path caseDir = tempDir.resolve("nozzle_turb");
            new OpenFOAMExporter().setTurbulenceEnabled(true)
                    .exportCase(params, contour, caseDir);

            String content = Files.readString(caseDir.resolve("constant/turbulenceProperties"));
            assertThat(content).contains("kOmegaSST");
        }

        @Test
        @DisplayName("Disabling turbulence should omit k and omega fields and use laminar model")
        void disablingTurbulenceShouldOmitKAndOmega() throws IOException {
            Path caseDir = tempDir.resolve("nozzle_lam");
            new OpenFOAMExporter().setTurbulenceEnabled(false)
                    .exportCase(params, contour, caseDir);

            assertThat(caseDir.resolve("0/k")).doesNotExist();
            assertThat(caseDir.resolve("0/omega")).doesNotExist();

            String turbContent = Files.readString(caseDir.resolve("constant/turbulenceProperties"));
            assertThat(turbContent).contains("laminar");
        }

        @Test
        @DisplayName("Should throw if contour has not been generated")
        void shouldThrowIfContourNotGenerated() {
            NozzleContour emptyContour = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
            Path caseDir = tempDir.resolve("nozzle_empty");
            assertThatThrownBy(() ->
                    new OpenFOAMExporter().exportCase(params, emptyContour, caseDir))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("CFD Mesh Exporter Tests")
    class CFDMeshExporterTests {
        
        @Test
        @DisplayName("Should export OpenFOAM blockMesh")
        void shouldExportOpenFOAMBlockMesh() throws IOException {
            CFDMeshExporter exporter = new CFDMeshExporter()
                    .setAxialCells(50)
                    .setRadialCells(25);
            Path outputFile = tempDir.resolve("blockMeshDict");
            
            exporter.export(contour, outputFile, CFDMeshExporter.Format.OPENFOAM_BLOCKMESH);
            
            assertThat(outputFile).exists();
            String content = Files.readString(outputFile);
            assertThat(content).contains("FoamFile");
            assertThat(content).contains("vertices");
            assertThat(content).contains("blocks");
        }
        
        @Test
        @DisplayName("Should export Gmsh geo file")
        void shouldExportGmshGeo() throws IOException {
            CFDMeshExporter exporter = new CFDMeshExporter();
            Path outputFile = tempDir.resolve("nozzle.geo");
            
            exporter.export(contour, outputFile, CFDMeshExporter.Format.GMSH_GEO);
            
            assertThat(outputFile).exists();
            String content = Files.readString(outputFile);
            assertThat(content).contains("Point");
            assertThat(content).contains("Physical");
        }
        
        @Test
        @DisplayName("Should export Plot3D format")
        void shouldExportPlot3D() throws IOException {
            CFDMeshExporter exporter = new CFDMeshExporter();
            Path outputFile = tempDir.resolve("nozzle.xyz");
            
            exporter.export(contour, outputFile, CFDMeshExporter.Format.PLOT3D);
            
            assertThat(outputFile).exists();
        }
    }
}
