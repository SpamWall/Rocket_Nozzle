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
}
