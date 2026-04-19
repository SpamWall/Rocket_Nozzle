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

package com.nozzle;

import com.nozzle.examples.*;

/**
 * Entry point — delegates to individual Demonstrate* classes in com.nozzle.examples.
 *
 * <p>Run any demonstration directly via its own {@code main} method, or pass a
 * name on the command line (e.g. {@code BasicDesign}) to run just that one.
 * With no arguments all demonstrations run in sequence.
 */
public class Main {

    private Main() {}

    /**
     * Dispatcher entry point.
     * @param args optional single argument: lower-case demo name (e.g. {@code solidmotor});
     *             omit or pass any unrecognised value to run all demonstrations in sequence
     * @throws Exception if any demonstration fails
     */
    public static void main(String[] args) throws Exception {
        String target = args.length > 0 ? args[0].toLowerCase() : "all";

        System.out.println("=".repeat(70));
        System.out.println("  SUPERSONIC NOZZLE DESIGN — METHOD OF CHARACTERISTICS");
        System.out.println("  Each Demonstrate* class in com.nozzle.examples is independently");
        System.out.println("  runnable via its own public static void main(String[] args).");
        System.out.println("=".repeat(70));

        switch (target) {
            case "basicdesign"                   -> DemonstrateBasicDesign.main(new String[0]);
            case "raocomparison"                 -> DemonstrateRaoComparison.main(new String[0]);
            case "truncatedidealcontour"         -> DemonstrateTruncatedIdealContour.main(new String[0]);
            case "thermalanalysis"               -> DemonstrateThermalAnalysis.main(new String[0]);
            case "chemistrymodeling"             -> DemonstrateChemistryModeling.main(new String[0]);
            case "validation"                    -> DemonstrateValidation.main(new String[0]);
            case "optimization"                  -> DemonstrateOptimization.main(new String[0]);
            case "uncertaintyanalysis"           -> DemonstrateUncertaintyAnalysis.main(new String[0]);
            case "flowseparationandshockexpansion" -> DemonstrateFlowSeparationAndShockExpansion.main(new String[0]);
            case "thermalstressanalysis"         -> DemonstrateThermalStressAnalysis.main(new String[0]);
            case "exports"                       -> DemonstrateExports.main(new String[0]);
            case "aerospikenozzle"               -> DemonstrateAerospikeNozzle.main(new String[0]);
            case "ablativeliner"                 -> DemonstrateAblativeLiner.main(new String[0]);
            case "radiationcooledextension"      -> DemonstrateRadiationCooledExtension.main(new String[0]);
            case "serialization"                 -> DemonstrateSerialization.main(new String[0]);
            case "yplusgrading"                  -> DemonstrateYPlusGrading.main(new String[0]);
            case "revolvedmesh"                  -> DemonstrateRevolvedMesh.main(new String[0]);
            case "ofsweep"                       -> DemonstrateOFSweep.main(new String[0]);
            case "dualbellnozzle"                -> DemonstrateDualBellNozzle.main(new String[0]);
            case "throatcurvatureratio"           -> DemonstrateThroatCurvatureRatio.main(new String[0]);
            case "convergentsection"             -> DemonstrateConvergentSection.main(new String[0]);
            case "unitsconversion"               -> DemonstrateUnitsConversion.main(new String[0]);
            case "fullnozzlegeometry"            -> DemonstrateFullNozzleGeometry.main(new String[0]);
            case "boundarylayerfrominjectorface" -> DemonstrateBoundaryLayerFromInjectorFace.main(new String[0]);
            case "heatfluxpeaklocation"          -> DemonstrateHeatFluxPeakLocation.main(new String[0]);
            case "planarwindtunnelnozzle"        -> DemonstratePlanarWindTunnelNozzle.main(new String[0]);
            case "solidmotor"                    -> DemonstrateSolidMotor.main(new String[0]);
            case "raspimport"                    -> DemonstrateRaspImport.main(new String[0]);
            case "minimumlengthnozzle"           -> DemonstrateMinimumLengthNozzle.main(new String[0]);
            case "viscousmocsolver"              -> DemonstrateViscousMOCSolver.main(new String[0]);
            case "twophaseflowmodel"             -> DemonstrateTwoPhaseFlowModel.main(new String[0]);
            case "nozzleperformancemap"          -> DemonstrateNozzlePerformanceMap.main(new String[0]);
            case "erosiveburning"                -> DemonstrateErosiveBurning.main(new String[0]);
            default -> runAll();
        }
    }

    private static void runAll() throws Exception {
        DemonstrateBasicDesign.main(new String[0]);
        DemonstrateRaoComparison.main(new String[0]);
        DemonstrateTruncatedIdealContour.main(new String[0]);
        DemonstrateThermalAnalysis.main(new String[0]);
        DemonstrateChemistryModeling.main(new String[0]);
        DemonstrateValidation.main(new String[0]);
        DemonstrateOptimization.main(new String[0]);
        DemonstrateUncertaintyAnalysis.main(new String[0]);
        DemonstrateFlowSeparationAndShockExpansion.main(new String[0]);
        DemonstrateThermalStressAnalysis.main(new String[0]);
        DemonstrateExports.main(new String[0]);
        DemonstrateAerospikeNozzle.main(new String[0]);
        DemonstrateAblativeLiner.main(new String[0]);
        DemonstrateRadiationCooledExtension.main(new String[0]);
        DemonstrateSerialization.main(new String[0]);
        DemonstrateYPlusGrading.main(new String[0]);
        DemonstrateRevolvedMesh.main(new String[0]);
        DemonstrateOFSweep.main(new String[0]);
        DemonstrateDualBellNozzle.main(new String[0]);
        DemonstrateThroatCurvatureRatio.main(new String[0]);
        DemonstrateConvergentSection.main(new String[0]);
        DemonstrateUnitsConversion.main(new String[0]);
        DemonstrateFullNozzleGeometry.main(new String[0]);
        DemonstrateBoundaryLayerFromInjectorFace.main(new String[0]);
        DemonstrateHeatFluxPeakLocation.main(new String[0]);
        DemonstratePlanarWindTunnelNozzle.main(new String[0]);
        DemonstrateSolidMotor.main(new String[0]);
        DemonstrateRaspImport.main(new String[0]);
        DemonstrateMinimumLengthNozzle.main(new String[0]);
        DemonstrateViscousMOCSolver.main(new String[0]);
        DemonstrateTwoPhaseFlowModel.main(new String[0]);
        DemonstrateNozzlePerformanceMap.main(new String[0]);
        DemonstrateErosiveBurning.main(new String[0]);
    }
}
