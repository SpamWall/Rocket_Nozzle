package com.nozzle.export;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.geometry.NozzleContour;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

@DisplayName("OpenFOAMExporter Tests")
class OpenFOAMExporter_UT {

    private NozzleDesignParameters params;
    private NozzleContour contour;

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
    }

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
        new OpenFOAMExporter().setTurbulenceEnabled(true).exportCase(params, contour, caseDir);

        String content = Files.readString(caseDir.resolve("constant/turbulenceProperties"));
        assertThat(content).contains("kOmegaSST");
    }

    @Test
    @DisplayName("Disabling turbulence should omit k and omega fields and use laminar model")
    void disablingTurbulenceShouldOmitKAndOmega() throws IOException {
        Path caseDir = tempDir.resolve("nozzle_lam");
        new OpenFOAMExporter().setTurbulenceEnabled(false).exportCase(params, contour, caseDir);

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
        assertThatThrownBy(() -> new OpenFOAMExporter().exportCase(params, emptyContour, caseDir))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("setAxialCells controls the axial cell count in the blockMeshDict hex block")
    void setAxialCellsWritesCellCountToBlockMeshDict() throws IOException {
        Path caseDir = tempDir.resolve("axial_cells");
        new OpenFOAMExporter().setAxialCells(42).exportCase(params, contour, caseDir);

        String content = Files.readString(caseDir.resolve("system/blockMeshDict"));
        // hex block line: hex (0 1 2 3 4 5 6 7) (axialCells radialCells 1)
        assertThat(content).contains("(42 80 1)");
    }

    @Test
    @DisplayName("setRadialCells controls the radial cell count in the blockMeshDict hex block")
    void setRadialCellsWritesCellCountToBlockMeshDict() throws IOException {
        Path caseDir = tempDir.resolve("radial_cells");
        new OpenFOAMExporter().setRadialCells(77).exportCase(params, contour, caseDir);

        String content = Files.readString(caseDir.resolve("system/blockMeshDict"));
        assertThat(content).contains("(200 77 1)");
    }

    @Test
    @DisplayName("setWedgeAngleDeg changes vertex z-coordinates in blockMeshDict")
    void setWedgeAngleDegChangesVertexCoordinates() throws IOException {
        Path defaultCase = tempDir.resolve("wedge_default");
        Path wideCase    = tempDir.resolve("wedge_wide");
        new OpenFOAMExporter()                       .exportCase(params, contour, defaultCase);
        new OpenFOAMExporter().setWedgeAngleDeg(5.0) .exportCase(params, contour, wideCase);

        String defaultBmd = Files.readString(defaultCase.resolve("system/blockMeshDict"));
        String wideBmd    = Files.readString(wideCase.resolve("system/blockMeshDict"));
        assertThat(wideBmd)
                .as("vertex z-coordinates must change when wedge angle changes from 2.5° to 5.0°")
                .isNotEqualTo(defaultBmd);
    }

    @Test
    @DisplayName("setRadialGrading writes the grading ratio to the simpleGrading directive")
    void setRadialGradingWritesRatioToBlockMeshDict() throws IOException {
        Path caseDir = tempDir.resolve("grading");
        new OpenFOAMExporter().setRadialGrading(10.0).exportCase(params, contour, caseDir);

        String content = Files.readString(caseDir.resolve("system/blockMeshDict"));
        // gradingSpec(10.0) → "((0.2 0.2 10.00)(0.8 0.8 0.1000))"
        assertThat(content).contains("10.00");
    }

    @Test
    @DisplayName("setTurbulenceIntensity writes the intensity fraction to the k field")
    void setTurbulenceIntensityWritesToKField() throws IOException {
        Path caseDir = tempDir.resolve("intensity");
        new OpenFOAMExporter().setTurbulenceIntensity(0.10).exportCase(params, contour, caseDir);

        // intensity is written as %.3f at the turbulence initialisation line
        assertThat(Files.readString(caseDir.resolve("0/k"))).contains("0.100");
    }
}
