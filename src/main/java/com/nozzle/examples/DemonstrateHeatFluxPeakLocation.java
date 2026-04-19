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
import com.nozzle.core.Units;
import com.nozzle.geometry.FullNozzleGeometry;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.thermal.HeatTransferModel;

/** Demonstrates how upstream curvature ratio shifts the peak heat-flux location. */
public class DemonstrateHeatFluxPeakLocation {

    private DemonstrateHeatFluxPeakLocation() {}

    /**
     * Entry point.
     * @param ignoredArgs unused
     */
    public static void main(String[] ignoredArgs) {
        System.out.println("\n--- HEAT FLUX PEAK LOCATION ---\n");

        NozzleDesignParameters standardParams = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500.0)
                .ambientPressure(101325.0)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20)
                .wallAngleInitialDegrees(25.0)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .throatCurvatureRatio(0.382)
                .upstreamCurvatureRatio(1.5)
                .contractionRatio(4.0)
                .build();

        NozzleDesignParameters tightUpstreamParams = NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500.0)
                .ambientPressure(101325.0)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(20)
                .wallAngleInitialDegrees(25.0)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .throatCurvatureRatio(0.382)
                .upstreamCurvatureRatio(0.2)
                .contractionRatio(4.0)
                .build();

        NozzleContour standardContour =
                new NozzleContour(NozzleContour.ContourType.RAO_BELL, standardParams);
        standardContour.generate(80);

        NozzleContour tightContour =
                new NozzleContour(NozzleContour.ContourType.RAO_BELL, tightUpstreamParams);
        tightContour.generate(80);

        HeatTransferModel stdDivOnly = new HeatTransferModel(standardParams, standardContour)
                .setWallProperties(20.0, 0.003)
                .setCoolantProperties(300.0, 5000.0)
                .calculate(null);

        System.out.println("Divergent-section calculate() — standard Rao params:");
        System.out.printf("  Peak x position : %+.3f mm  (positive = downstream of throat)%n",
                Units.metersToMillimeters(stdDivOnly.getPeakFluxX()));
        System.out.printf("  Peak heat flux  : %.3e W/m²%n",
                stdDivOnly.getPeakFluxPoint().totalHeatFlux());
        System.out.printf("  Peak wall temp  : %.0f K%n",
                stdDivOnly.getPeakFluxPoint().wallTemperature());
        System.out.printf("  Max wall temp   : %.0f K%n", stdDivOnly.getMaxWallTemperature());

        FullNozzleGeometry stdFullGeom = new FullNozzleGeometry(standardParams).generate(40, 80);
        HeatTransferModel stdFull = new HeatTransferModel(standardParams, standardContour)
                .setWallProperties(20.0, 0.003)
                .setCoolantProperties(300.0, 5000.0)
                .calculateFullProfile(stdFullGeom, null);

        System.out.println("\nFull-profile calculateFullProfile() — standard Rao params:");
        System.out.printf("  Wall points     : %d (conv + div)%n",
                stdFull.getWallThermalProfile().size());
        System.out.printf("  Peak x position : %+.3f mm%n",
                Units.metersToMillimeters(stdFull.getPeakFluxX()));
        System.out.printf("  Peak heat flux  : %.3e W/m²%n",
                stdFull.getPeakFluxPoint().totalHeatFlux());

        FullNozzleGeometry tightFullGeom = new FullNozzleGeometry(tightUpstreamParams).generate(40, 80);
        HeatTransferModel tightFull = new HeatTransferModel(tightUpstreamParams, tightContour)
                .setWallProperties(20.0, 0.003)
                .setCoolantProperties(300.0, 5000.0)
                .calculateFullProfile(tightFullGeom, null);

        System.out.println("\nFull-profile calculateFullProfile() — tight upstream (r_cu = 0.2·r_t):");
        System.out.printf("  Peak x position : %+.3f mm  (negative = on convergent side)%n",
                Units.metersToMillimeters(tightFull.getPeakFluxX()));
        System.out.printf("  Peak heat flux  : %.3e W/m²%n",
                tightFull.getPeakFluxPoint().totalHeatFlux());

        System.out.println("\nPhysical insight:");
        System.out.println("  The Bartz curvature correction (D_t/r_c)^0.1 is evaluated using");
        System.out.println("  the parametric throat-arc radii r_cd (downstream) and r_cu (upstream)");
        System.out.println("  rather than finite differences on the contour.  When r_cu < r_cd the");
        System.out.println("  upstream correction factor exceeds the downstream value and the peak");
        System.out.println("  shifts to the convergent side — visible only with calculateFullProfile().");
    }
}
