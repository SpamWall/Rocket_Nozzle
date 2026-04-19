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

package com.nozzle.moc;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ViscousMOCSolver Tests")
class ViscousMOCSolver_UT {

    private NozzleDesignParameters params;
    private CharacteristicNet generatedNet;

    @BeforeEach
    void setUp() {
        params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(2.5)
                .chamberPressure(7e6)
                .chamberTemperature(3500)
                .ambientPressure(101325)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(15)
                .wallAngleInitialDegrees(25)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();

        generatedNet = new CharacteristicNet(params).generate();
    }

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("Should accept a generated CharacteristicNet")
        void shouldAcceptGeneratedNet() {
            ViscousMOCSolver solver = new ViscousMOCSolver(generatedNet);
            assertThat(solver).isNotNull();
        }

        @Test
        @DisplayName("Should reject un-generated CharacteristicNet")
        void shouldRejectUngeneratedNet() {
            CharacteristicNet emptyNet = new CharacteristicNet(params);

            assertThatThrownBy(() -> new ViscousMOCSolver(emptyNet))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("generated");
        }
    }

    @Nested
    @DisplayName("Solve")
    class SolveTests {

        @Test
        @DisplayName("Should produce corrected wall with same point count as inviscid net")
        void correctedWallCountMatchesInviscid() {
            ViscousMOCSolver solver = new ViscousMOCSolver(generatedNet).solve();

            assertThat(solver.getCorrectedWall())
                    .hasSameSizeAs(generatedNet.getWallPoints());
        }

        @Test
        @DisplayName("Physical radius should be >= inviscid radius at every point")
        void physicalRadiusGreaterOrEqualInviscid() {
            ViscousMOCSolver solver = new ViscousMOCSolver(generatedNet).solve();

            for (ViscousMOCSolver.ViscousWallPoint wp : solver.getCorrectedWall()) {
                assertThat(wp.rPhysical())
                        .as("Physical r >= inviscid r at x=%.4f", wp.x())
                        .isGreaterThanOrEqualTo(wp.rInviscid() - 1e-10);
            }
        }

        @Test
        @DisplayName("Displacement thickness should be non-negative everywhere")
        void displacementThicknessNonNegative() {
            ViscousMOCSolver solver = new ViscousMOCSolver(generatedNet).solve();

            solver.getCorrectedWall().forEach(wp ->
                    assertThat(wp.displacementThickness())
                            .as("δ* >= 0 at x=%.4f", wp.x())
                            .isGreaterThanOrEqualTo(0.0));
        }

        @Test
        @DisplayName("Discharge coefficient should be in physical range [0.90, 1.00]")
        void dischargeCoefficientInPhysicalRange() {
            ViscousMOCSolver solver = new ViscousMOCSolver(generatedNet).solve();

            assertThat(solver.dischargeCoefficient())
                    .isGreaterThanOrEqualTo(0.90)
                    .isLessThanOrEqualTo(1.00);
        }

        @Test
        @DisplayName("Viscous Isp loss fraction should be small (< 5%)")
        void viscousIspLossSmall() {
            ViscousMOCSolver solver = new ViscousMOCSolver(generatedNet).solve();

            assertThat(solver.viscousIspLossFraction())
                    .isGreaterThanOrEqualTo(0.0)
                    .isLessThan(0.05);
        }

        @Test
        @DisplayName("Physical exit radius should be larger than throat radius")
        void physicalExitRadiusLargerThanThroat() {
            ViscousMOCSolver solver = new ViscousMOCSolver(generatedNet).solve();

            assertThat(solver.physicalExitRadius())
                    .isGreaterThan(params.throatRadius());
        }

        @Test
        @DisplayName("Max displacement thickness should be positive")
        void maxDisplacementThicknessPositive() {
            ViscousMOCSolver solver = new ViscousMOCSolver(generatedNet).solve();

            assertThat(solver.maxDisplacementThickness()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("Wall points should preserve x-monotonicity from inviscid net")
        void correctedWallXMonotone() {
            ViscousMOCSolver solver = new ViscousMOCSolver(generatedNet).solve();
            List<ViscousMOCSolver.ViscousWallPoint> wall = solver.getCorrectedWall();

            for (int i = 1; i < wall.size(); i++) {
                assertThat(wall.get(i).x())
                        .as("x[%d] > x[%d]", i, i - 1)
                        .isGreaterThanOrEqualTo(wall.get(i - 1).x() - 1e-10);
            }
        }

        @Test
        @DisplayName("Mach numbers along corrected wall should be supersonic")
        void correctedWallMachSupersonic() {
            ViscousMOCSolver solver = new ViscousMOCSolver(generatedNet).solve();

            solver.getCorrectedWall().forEach(wp ->
                    assertThat(wp.mach())
                            .as("M >= 1 at x=%.4f", wp.x())
                            .isGreaterThanOrEqualTo(1.0));
        }
    }

    @Nested
    @DisplayName("ViscousWallPoint record")
    class RecordTests {

        @Test
        @DisplayName("ViscousWallPoint fields should be accessible")
        void recordFieldsAccessible() {
            ViscousMOCSolver solver = new ViscousMOCSolver(generatedNet).solve();
            ViscousMOCSolver.ViscousWallPoint first = solver.getCorrectedWall().getFirst();

            assertThat(first.x()).isFinite();
            assertThat(first.rInviscid()).isGreaterThan(0);
            assertThat(first.displacementThickness()).isGreaterThanOrEqualTo(0);
            assertThat(first.rPhysical()).isGreaterThan(0);
            assertThat(first.mach()).isGreaterThanOrEqualTo(1.0);
            assertThat(first.localReynolds()).isGreaterThanOrEqualTo(0);
        }
    }
}
