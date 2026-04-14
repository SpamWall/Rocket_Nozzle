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

package com.nozzle.export;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.geometry.FullNozzleGeometry;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.moc.AerospikeNozzle;
import com.nozzle.moc.CharacteristicNet;
import com.nozzle.moc.DualBellNozzle;
import com.nozzle.moc.RaoNozzle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

        // intensity is written as %.3f at the turbulence initialization line
        assertThat(Files.readString(caseDir.resolve("0/k"))).contains("0.100");
    }

    @Test
    @DisplayName("setFirstLayerThickness rejects non-positive values")
    void setFirstLayerThicknessRejectsNonPositive() {
        assertThatThrownBy(() -> new OpenFOAMExporter().setFirstLayerThickness(0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OpenFOAMExporter().setFirstLayerThickness(-1e-5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("firstLayerThickness overrides radialGrading in blockMeshDict")
    void firstLayerThicknessOverridesRadialGrading() throws IOException {
        Path defaultCase = tempDir.resolve("grading_default");
        Path yPlusCase   = tempDir.resolve("grading_yplus");

        // Default grading (radialGrading = 4.0)
        new OpenFOAMExporter().exportCase(params, contour, defaultCase);
        // y⁺-driven grading with a very thin first layer → much larger grading ratio
        new OpenFOAMExporter().setFirstLayerThickness(1e-5)
                .exportCase(params, contour, yPlusCase);

        String defaultBmd = Files.readString(defaultCase.resolve("system/blockMeshDict"));
        String yPlusBmd   = Files.readString(yPlusCase.resolve("system/blockMeshDict"));

        // Both must contain the grading spec
        assertThat(defaultBmd).contains("simpleGrading");
        assertThat(yPlusBmd).contains("simpleGrading");
        // The two grading strings must differ
        assertThat(yPlusBmd).isNotEqualTo(defaultBmd);
    }

    @Test
    @DisplayName("larger firstLayerThickness produces smaller grading ratio")
    void largerFirstLayerThicknessProducesSmallerGrading() throws IOException {
        Path caseCoarse = tempDir.resolve("grading_coarse");
        Path caseFine   = tempDir.resolve("grading_fine");

        new OpenFOAMExporter().setFirstLayerThickness(1e-3)
                .exportCase(params, contour, caseCoarse);
        new OpenFOAMExporter().setFirstLayerThickness(1e-5)
                .exportCase(params, contour, caseFine);

        double gradingCoarse = extractRadialGrading(
                Files.readString(caseCoarse.resolve("system/blockMeshDict")));
        double gradingFine   = extractRadialGrading(
                Files.readString(caseFine.resolve("system/blockMeshDict")));

        assertThat(gradingFine).isGreaterThan(gradingCoarse);
    }

    @Test
    @DisplayName("exportCase(RaoNozzle) creates all required OpenFOAM case files")
    void exportCaseRaoNozzleCreatesAllRequiredFiles() throws IOException {
        Path caseDir = tempDir.resolve("rao_case");
        RaoNozzle nozzle = new RaoNozzle(params).generate();
        NozzleContour raoContour = NozzleContour.fromPoints(params, nozzle.getContourPoints());
        new OpenFOAMExporter().exportCase(params, raoContour, caseDir);

        assertThat(caseDir.resolve("system/blockMeshDict")).exists();
        assertThat(caseDir.resolve("system/controlDict")).exists();
        assertThat(caseDir.resolve("constant/thermophysicalProperties")).exists();
        assertThat(caseDir.resolve("0/p")).exists();
        assertThat(caseDir.resolve("0/T")).exists();
        assertThat(caseDir.resolve("0/U")).exists();
    }

    @Test
    @DisplayName("exportCase(DualBellNozzle) creates all required OpenFOAM case files")
    void exportCaseDualBellNozzleCreatesAllRequiredFiles() throws IOException {
        Path caseDir = tempDir.resolve("dualbell_case");
        DualBellNozzle nozzle = new DualBellNozzle(params).generate();
        NozzleContour dualBellContour = NozzleContour.fromPoints(params, nozzle.getContourPoints());
        new OpenFOAMExporter().exportCase(params, dualBellContour, caseDir);

        assertThat(caseDir.resolve("system/blockMeshDict")).exists();
        assertThat(caseDir.resolve("system/controlDict")).exists();
        assertThat(caseDir.resolve("constant/thermophysicalProperties")).exists();
        assertThat(caseDir.resolve("0/p")).exists();
        assertThat(caseDir.resolve("0/T")).exists();
        assertThat(caseDir.resolve("0/U")).exists();
    }

    // -------------------------------------------------------------------------
    // exportCase(FullNozzleGeometry) — covers the convergent+divergent overload
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("exportCase(FullNozzleGeometry) Tests")
    class FullNozzleGeometryExportTests {

        private FullNozzleGeometry fullGeometry;

        @BeforeEach
        void setUpFullGeometry() {
            fullGeometry = new FullNozzleGeometry(params).generate();
        }

        @Test
        @DisplayName("Creates all required OpenFOAM case files")
        void createsAllRequiredFiles() throws IOException {
            Path caseDir = tempDir.resolve("full_geom_case");
            new OpenFOAMExporter().exportCase(params, fullGeometry, caseDir);

            assertThat(caseDir.resolve("system/blockMeshDict")).exists();
            assertThat(caseDir.resolve("system/controlDict")).exists();
            assertThat(caseDir.resolve("system/fvSchemes")).exists();
            assertThat(caseDir.resolve("system/fvSolution")).exists();
            assertThat(caseDir.resolve("constant/thermophysicalProperties")).exists();
            assertThat(caseDir.resolve("constant/turbulenceProperties")).exists();
            assertThat(caseDir.resolve("0/p")).exists();
            assertThat(caseDir.resolve("0/T")).exists();
            assertThat(caseDir.resolve("0/U")).exists();
        }

        @Test
        @DisplayName("Throws IllegalStateException when FullNozzleGeometry has not been generated")
        void throwsWhenNotGenerated() {
            FullNozzleGeometry ungeneratedGeometry = new FullNozzleGeometry(params);
            Path caseDir = tempDir.resolve("full_geom_empty");
            assertThatThrownBy(
                    () -> new OpenFOAMExporter().exportCase(params, ungeneratedGeometry, caseDir))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("blockMeshDict contains negative x coordinates from the convergent section")
        void blockMeshDictContainsNegativeXFromConvergentSection() throws IOException {
            Path caseDir = tempDir.resolve("full_geom_conv");
            new OpenFOAMExporter().exportCase(params, fullGeometry, caseDir);

            String bmd = Files.readString(caseDir.resolve("system/blockMeshDict"));
            // Convergent section runs from a negative x (chamber face) to x ≈ 0 (throat).
            // At least one spline point must have a negative x coordinate.
            assertThat(bmd).containsPattern("-\\d+\\.\\d+");
        }

        @Test
        @DisplayName("Wall point count includes both convergent and divergent sections")
        void wallPointCountIncludesBothSections() {
            // FullNozzleGeometry combines convergent + divergent; its total must
            // exceed a divergent-only contour generated with the same params.
            NozzleContour divergentOnly = new NozzleContour(NozzleContour.ContourType.RAO_BELL, params);
            divergentOnly.generate(100);

            assertThat(fullGeometry.getWallPoints().size())
                    .isGreaterThan(divergentOnly.getContourPoints().size());
        }

        @Test
        @DisplayName("controlDict specifies rhoCentralFoam as the application")
        void controlDictSpecifiesRhoCentralFoam() throws IOException {
            Path caseDir = tempDir.resolve("full_geom_ctrl");
            new OpenFOAMExporter().exportCase(params, fullGeometry, caseDir);

            assertThat(Files.readString(caseDir.resolve("system/controlDict")))
                    .contains("rhoCentralFoam");
        }

        @Test
        @DisplayName("Disabling turbulence omits k and omega fields")
        void disablingTurbulenceOmitsKAndOmega() throws IOException {
            Path caseDir = tempDir.resolve("full_geom_lam");
            new OpenFOAMExporter().setTurbulenceEnabled(false)
                    .exportCase(params, fullGeometry, caseDir);

            assertThat(caseDir.resolve("0/k")).doesNotExist();
            assertThat(caseDir.resolve("0/omega")).doesNotExist();
        }

        @Test
        @DisplayName("FullNozzleGeometry.fromMOC factory produces a valid case")
        void fromMocFactoryProducesValidCase() throws IOException {
            CharacteristicNet net = new CharacteristicNet(params).generate();
            FullNozzleGeometry mocGeometry = FullNozzleGeometry.fromMOC(params, net).generate();

            Path caseDir = tempDir.resolve("full_geom_moc");
            new OpenFOAMExporter().exportCase(params, mocGeometry, caseDir);

            assertThat(caseDir.resolve("system/blockMeshDict")).exists();
            assertThat(caseDir.resolve("0/p")).exists();
            // MOC wall points are real solver output — wall point count must be substantial
            assertThat(mocGeometry.getWallPoints().size()).isGreaterThan(20);
        }
    }

    // -------------------------------------------------------------------------
    // exportAerospikeCase — complete coverage
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("exportAerospikeCase Tests")
    class AerospikeExportTests {

        private AerospikeNozzle nozzle;

        @BeforeEach
        void setUpAerospike() {
            nozzle = new AerospikeNozzle(params).generate();
        }

        @Test
        @DisplayName("Creates all required OpenFOAM case files")
        void createsAllRequiredFiles() throws IOException {
            Path caseDir = tempDir.resolve("aerospike_case");
            new OpenFOAMExporter().exportAerospikeCase(nozzle, caseDir);

            assertThat(caseDir.resolve("system/blockMeshDict")).exists();
            assertThat(caseDir.resolve("system/controlDict")).exists();
            assertThat(caseDir.resolve("system/fvSchemes")).exists();
            assertThat(caseDir.resolve("system/fvSolution")).exists();
            assertThat(caseDir.resolve("constant/thermophysicalProperties")).exists();
            assertThat(caseDir.resolve("constant/turbulenceProperties")).exists();
            assertThat(caseDir.resolve("0/p")).exists();
            assertThat(caseDir.resolve("0/T")).exists();
            assertThat(caseDir.resolve("0/U")).exists();
        }

        @Test
        @DisplayName("Creates the case directory if it does not exist")
        void createsCaseDirIfAbsent() throws IOException {
            Path caseDir = tempDir.resolve("nested/aerospike_case");
            assertThat(caseDir).doesNotExist();
            new OpenFOAMExporter().exportAerospikeCase(nozzle, caseDir);
            assertThat(caseDir).isDirectory();
        }

        // ---- blockMeshDict ----

        @Test
        @DisplayName("blockMeshDict contains FoamFile header and convertToMeters")
        void blockMeshDictContainsFoamFileHeader() throws IOException {
            Path caseDir = tempDir.resolve("aero_bmd_header");
            new OpenFOAMExporter().exportAerospikeCase(nozzle, caseDir);

            String bmd = Files.readString(caseDir.resolve("system/blockMeshDict"));
            assertThat(bmd).contains("FoamFile");
            assertThat(bmd).contains("convertToMeters");
        }

        @Test
        @DisplayName("blockMeshDict contains vertices, spline edges, and blocks")
        void blockMeshDictContainsStructuralSections() throws IOException {
            Path caseDir = tempDir.resolve("aero_bmd_sections");
            new OpenFOAMExporter().exportAerospikeCase(nozzle, caseDir);

            String bmd = Files.readString(caseDir.resolve("system/blockMeshDict"));
            assertThat(bmd).contains("vertices");
            assertThat(bmd).contains("spline");
            assertThat(bmd).contains("blocks");
        }

        @Test
        @DisplayName("blockMeshDict contains all five aerospike boundary patches")
        void blockMeshDictContainsAllAerospikePatch() throws IOException {
            Path caseDir = tempDir.resolve("aero_bmd_patches");
            new OpenFOAMExporter().exportAerospikeCase(nozzle, caseDir);

            String bmd = Files.readString(caseDir.resolve("system/blockMeshDict"));
            assertThat(bmd).contains("inlet");
            assertThat(bmd).contains("outlet");
            assertThat(bmd).contains("spike");
            assertThat(bmd).contains("cowl");
            assertThat(bmd).contains("wedge0");
            assertThat(bmd).contains("wedge1");
        }

        @Test
        @DisplayName("blockMeshDict does not contain an axis patch (annular domain has no axis)")
        void blockMeshDictHasNoAxisPatch() throws IOException {
            Path caseDir = tempDir.resolve("aero_bmd_noaxis");
            new OpenFOAMExporter().exportAerospikeCase(nozzle, caseDir);

            // The bell-nozzle case writes an "axis" patch; the aerospike annular
            // domain has no axis line — neither spike nor cowl are on the axis.
            assertThat(Files.readString(caseDir.resolve("system/blockMeshDict")))
                    .doesNotContain("\"axis\"")
                    .doesNotContain("    axis");
        }

        @Test
        @DisplayName("blockMeshDict spike and cowl patches have type wall")
        void blockMeshDictSpikeAndCowlAreWalls() throws IOException {
            Path caseDir = tempDir.resolve("aero_bmd_wall");
            new OpenFOAMExporter().exportAerospikeCase(nozzle, caseDir);

            String bmd = Files.readString(caseDir.resolve("system/blockMeshDict"));
            // Both inner and outer surfaces are solid walls
            assertThat(bmd).containsPattern("spike[\\s\\S]{0,60}wall");
            assertThat(bmd).containsPattern("cowl[\\s\\S]{0,60}wall");
        }

        // ---- controlDict ----

        @Test
        @DisplayName("controlDict specifies rhoCentralFoam")
        void controlDictSpecifiesRhoCentralFoam() throws IOException {
            Path caseDir = tempDir.resolve("aero_ctrl");
            new OpenFOAMExporter().exportAerospikeCase(nozzle, caseDir);

            assertThat(Files.readString(caseDir.resolve("system/controlDict")))
                    .contains("rhoCentralFoam");
        }

        // ---- thermophysicalProperties ----

        @Test
        @DisplayName("thermophysicalProperties contains gas mixture data")
        void thermophysicalPropertiesContainsGasData() throws IOException {
            Path caseDir = tempDir.resolve("aero_thermo");
            new OpenFOAMExporter().exportAerospikeCase(nozzle, caseDir);

            String thermo = Files.readString(
                    caseDir.resolve("constant/thermophysicalProperties"));
            assertThat(thermo).contains("hePsiThermo");
            assertThat(thermo).contains("molWeight");
            assertThat(thermo).contains("Cp");
        }

        // ---- 0/p ----

        @Test
        @DisplayName("0/p uses totalPressure at inlet and waveTransmissive at outlet")
        void pressureFieldBoundaryConditions() throws IOException {
            Path caseDir = tempDir.resolve("aero_p");
            new OpenFOAMExporter().exportAerospikeCase(nozzle, caseDir);

            String p = Files.readString(caseDir.resolve("0/p"));
            assertThat(p).contains("totalPressure");
            assertThat(p).contains("waveTransmissive");
        }

        @Test
        @DisplayName("0/p applies zeroGradient to spike and cowl walls")
        void pressureFieldZeroGradientOnWalls() throws IOException {
            Path caseDir = tempDir.resolve("aero_p_walls");
            new OpenFOAMExporter().exportAerospikeCase(nozzle, caseDir);

            String p = Files.readString(caseDir.resolve("0/p"));
            assertThat(p).contains("spike");
            assertThat(p).contains("cowl");
            assertThat(p).contains("zeroGradient");
        }

        @Test
        @DisplayName("0/p does not contain an axis patch BC")
        void pressureFieldHasNoAxisPatchBC() throws IOException {
            Path caseDir = tempDir.resolve("aero_p_noaxis");
            new OpenFOAMExporter().exportAerospikeCase(nozzle, caseDir);

            assertThat(Files.readString(caseDir.resolve("0/p")))
                    .doesNotContain("    axis");
        }

        // ---- 0/T ----

        @Test
        @DisplayName("0/T uses totalTemperature at inlet")
        void temperatureFieldTotalTempAtInlet() throws IOException {
            Path caseDir = tempDir.resolve("aero_T");
            new OpenFOAMExporter().exportAerospikeCase(nozzle, caseDir);

            assertThat(Files.readString(caseDir.resolve("0/T")))
                    .contains("totalTemperature");
        }

        // ---- 0/U ----

        @Test
        @DisplayName("0/U applies noSlip to spike and cowl walls")
        void velocityFieldNoSlipOnWalls() throws IOException {
            Path caseDir = tempDir.resolve("aero_U");
            new OpenFOAMExporter().exportAerospikeCase(nozzle, caseDir);

            String u = Files.readString(caseDir.resolve("0/U"));
            assertThat(u).contains("pressureInletOutletVelocity");
            assertThat(u).contains("spike");
            assertThat(u).contains("cowl");
            assertThat(u).contains("noSlip");
        }

        // ---- turbulence toggle ----

        @Test
        @DisplayName("Turbulence enabled writes 0/k and 0/omega with aerospike wall functions")
        void turbulenceEnabledWritesKAndOmega() throws IOException {
            Path caseDir = tempDir.resolve("aero_turb_on");
            new OpenFOAMExporter().setTurbulenceEnabled(true)
                    .exportAerospikeCase(nozzle, caseDir);

            assertThat(caseDir.resolve("0/k")).exists();
            assertThat(caseDir.resolve("0/omega")).exists();

            String k = Files.readString(caseDir.resolve("0/k"));
            assertThat(k).contains("kqRWallFunction");
            assertThat(k).contains("spike");
            assertThat(k).contains("cowl");

            String omega = Files.readString(caseDir.resolve("0/omega"));
            assertThat(omega).contains("omegaWallFunction");
        }

        @Test
        @DisplayName("Turbulence disabled omits 0/k and 0/omega and uses laminar model")
        void turbulenceDisabledOmitsKAndOmega() throws IOException {
            Path caseDir = tempDir.resolve("aero_turb_off");
            new OpenFOAMExporter().setTurbulenceEnabled(false)
                    .exportAerospikeCase(nozzle, caseDir);

            assertThat(caseDir.resolve("0/k")).doesNotExist();
            assertThat(caseDir.resolve("0/omega")).doesNotExist();

            assertThat(Files.readString(caseDir.resolve("constant/turbulenceProperties")))
                    .contains("laminar");
        }
    }

    /** Extracts the first grading value from a {@code simpleGrading (1 ((0.2 0.2 <ratio>)(...)) 1)} line. */
    private static double extractRadialGrading(String blockMeshDict) {
        var matcher = java.util.regex.Pattern
                .compile("0\\.2 0\\.2 ([0-9.]+)")
                .matcher(blockMeshDict);
        if (!matcher.find()) throw new AssertionError("simpleGrading 0.2-sub-block not found");
        return Double.parseDouble(matcher.group(1));
    }
}
