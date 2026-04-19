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

package com.nozzle.examples;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.moc.CharacteristicNet;
import com.nozzle.thermal.TwoPhaseFlowModel;

/** Demonstrates two-phase flow Isp loss modeling with Al2O3 particles (Lagrangian tracking). */
public class DemonstrateTwoPhaseFlowModel {

    public static void main(String[] ignoredArgs) {
        System.out.println("\n--- TWO-PHASE FLOW MODEL (Al\u2082O\u2083 PARTICLES) ---\n");

        NozzleDesignParameters params = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500.0)
                .ambientPressure(101325.0)
                .gasProperties(GasProperties.APCP_HTPB_PRODUCTS)
                .numberOfCharLines(25)
                .wallAngleInitialDegrees(30.0)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();

        CharacteristicNet net = new CharacteristicNet(params).generate();

        System.out.println("Gas phase (APCP/HTPB products):");
        System.out.printf("  Exit Mach:          %.2f%n", params.exitMach());
        System.out.printf("  Ideal Isp:          %.1f s%n", params.idealSpecificImpulse());
        System.out.printf("  MOC wall points:    %d%n", net.getWallPoints().size());

        double particleMassFraction = 0.30;
        TwoPhaseFlowModel model = new TwoPhaseFlowModel(params, particleMassFraction)
                .solve(net.getWallPoints());

        System.out.printf("%nTwo-phase model (α = %.0f%% Al\u2082O\u2083, d_p = %.1f μm):%n",
                particleMassFraction * 100, TwoPhaseFlowModel.AL2O3_DIAMETER_M * 1e6);
        System.out.printf("  Trajectory points:  %d%n", model.getParticleTrajectory().size());
        System.out.printf("  Two-phase η:        %.4f%n", model.twoPhaseEfficiency());
        System.out.printf("  Isp loss fraction:  %.3f%%  (Δ Isp = %.1f s)%n",
                model.ispLossFraction() * 100.0,
                params.idealSpecificImpulse() * model.ispLossFraction());
        System.out.printf("  Delivered Isp:      %.1f s%n",
                params.idealSpecificImpulse() * model.twoPhaseEfficiency());

        var trajectory = model.getParticleTrajectory();
        System.out.println("\nParticle trajectory (selected stations):");
        System.out.printf("  %-8s  %-12s  %-10s  %-10s  %-10s%n",
                "x (mm)", "V_p (m/s)", "T_p (K)", "V lag", "T lag");
        System.out.println("  " + "-".repeat(54));
        int step = Math.max(1, trajectory.size() / 6);
        for (int i = 0; i < trajectory.size(); i += step) {
            var ps = trajectory.get(i);
            System.out.printf("  %-8.2f  %-12.1f  %-10.0f  %-10.4f  %-10.4f%n",
                    ps.x() * 1000, ps.velocity(), ps.temperature(),
                    ps.velocityLag(), ps.thermalLag());
        }

        System.out.println("\nParticle mass fraction sensitivity:");
        System.out.printf("  %-8s  %-10s  %-12s  %-12s%n",
                "α (%)", "η_2φ", "Loss (%)", "Isp (s)");
        System.out.println("  " + "-".repeat(44));
        for (double alpha : new double[]{0.05, 0.10, 0.20, 0.30, 0.40}) {
            TwoPhaseFlowModel m = new TwoPhaseFlowModel(params, alpha)
                    .solve(net.getWallPoints());
            System.out.printf("  %-8.0f  %-10.4f  %-12.3f  %-12.1f%n",
                    alpha * 100, m.twoPhaseEfficiency(),
                    m.ispLossFraction() * 100.0,
                    params.idealSpecificImpulse() * m.twoPhaseEfficiency());
        }

        System.out.println("\nParticle diameter sensitivity (α = 30%):");
        System.out.printf("  %-10s  %-10s  %-12s%n", "d_p (μm)", "η_2φ", "Loss (%)");
        System.out.println("  " + "-".repeat(34));
        for (double dp : new double[]{1e-6, 2e-6, 4e-6, 8e-6, 15e-6}) {
            TwoPhaseFlowModel m = new TwoPhaseFlowModel(
                    params, 0.30, dp,
                    TwoPhaseFlowModel.AL2O3_DENSITY,
                    TwoPhaseFlowModel.AL2O3_SPECIFIC_HEAT)
                    .solve(net.getWallPoints());
            System.out.printf("  %-10.1f  %-10.4f  %-12.3f%n",
                    dp * 1e6, m.twoPhaseEfficiency(), m.ispLossFraction() * 100.0);
        }

        System.out.println("\nPhysical insight:");
        System.out.println("  Smaller particles equilibrate faster (lower velocity and thermal lag).");
        System.out.println("  Higher particle mass fraction increases losses proportionally.");
        System.out.println("  Typical APCP with 18% Al → α ≈ 0.30, η ≈ 0.95–0.97.");
    }
}
