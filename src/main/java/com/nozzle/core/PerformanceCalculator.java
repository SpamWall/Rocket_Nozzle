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

package com.nozzle.core;

import com.nozzle.chemistry.ChemistryModel;
import com.nozzle.geometry.ConvergentSection;
import com.nozzle.geometry.NozzleContour;
import com.nozzle.moc.CharacteristicNet;
import com.nozzle.moc.CharacteristicPoint;
import com.nozzle.thermal.BoundaryLayerCorrection;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calculates nozzle performance metrics including the ideal and actual thrust
 * coefficient, specific impulse, efficiency, thrust, and mass flow rate.
 *
 * <p>Four independent loss mechanisms are modeled:
 * <ol>
 *   <li><b>Divergence loss</b> — momentum thrust reduction from non-axial exit flow,
 *       expressed via the divergence factor λ = (1 + cos α) / 2.</li>
 *   <li><b>Boundary-layer loss</b> — integrated skin-friction drag computed either
 *       from a {@link com.nozzle.thermal.BoundaryLayerCorrection} model or from a
 *       Reynolds-number scaling estimate.</li>
 *   <li><b>Chemical loss</b> — frozen-flow or finite-rate chemistry penalty derived
 *       from the γ variation between chamber and exit conditions.</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>{@code
 * PerformanceCalculator pc = new PerformanceCalculator(params, net, contour, bl, chem);
 * pc.calculate();
 * System.out.println(pc.getSummary());
 * }</pre>
 *
 * @see PerformanceSummary
 */
public class PerformanceCalculator {

    private static final Logger LOG = LoggerFactory.getLogger(PerformanceCalculator.class);

    private final NozzleDesignParameters parameters;
    private final CharacteristicNet characteristicNet;
    private final NozzleContour contour;
    private final BoundaryLayerCorrection boundaryLayer;
    private final ChemistryModel chemistry;
    private final ConvergentSection convergentSection;

    // Calculated results
    private double idealThrustCoeff;
    private double divergenceLoss;
    private double boundaryLayerLoss;
    private double chemicalLoss;
    private double sonicLineCdCorrection = 1.0;   // Cd_geo from sonic-line curvature
    private double actualThrustCoeff;
    private double efficiency;
    private double specificImpulse;
    private double thrust;
    private double massFlowRate;
    
    /**
     * Creates a fully configured performance calculator.
     * Any optional argument may be {@code null}; when {@code null}, the
     * corresponding loss is estimated from design-parameter correlations instead.
     *
     * @param parameters        Nozzle design parameters (throat geometry, chamber
     *                          conditions, gas properties); must not be {@code null}
     * @param characteristicNet MOC characteristic net used to read the exit flow
     *                          angle for the divergence factor; may be {@code null}
     * @param contour           Nozzle wall contour used as a fallback source for the
     *                          exit flow angle; may be {@code null}
     * @param boundaryLayer     Pre-computed boundary-layer model whose integrated
     *                          skin-friction drag is used directly; may be {@code null}
     * @param chemistry         Chemistry model providing γ at chamber and exit
     *                          temperatures for the chemical loss calculation;
     *                          may be {@code null}
     */
    public PerformanceCalculator(NozzleDesignParameters parameters,
                                  CharacteristicNet characteristicNet,
                                  NozzleContour contour,
                                  BoundaryLayerCorrection boundaryLayer,
                                  ChemistryModel chemistry) {
        this(parameters, characteristicNet, contour, boundaryLayer, chemistry, null);
    }

    /**
     * Creates a fully configured performance calculator that also applies the
     * sonic-line discharge-coefficient correction from a
     * {@link ConvergentSection}.
     *
     * @param parameters        Nozzle design parameters; must not be {@code null}
     * @param characteristicNet MOC net for divergence factor; may be {@code null}
     * @param contour           Wall contour; may be {@code null}
     * @param boundaryLayer     Boundary-layer model; may be {@code null}
     * @param chemistry         Chemistry model; may be {@code null}
     * @param convergentSection Convergent section providing the sonic-line Cd
     *                          correction; may be {@code null}
     */
    public PerformanceCalculator(NozzleDesignParameters parameters,
                                  CharacteristicNet characteristicNet,
                                  NozzleContour contour,
                                  BoundaryLayerCorrection boundaryLayer,
                                  ChemistryModel chemistry,
                                  ConvergentSection convergentSection) {
        this.parameters = parameters;
        this.characteristicNet = characteristicNet;
        this.contour = contour;
        this.boundaryLayer = boundaryLayer;
        this.chemistry = chemistry;
        this.convergentSection = convergentSection;
    }

    /**
     * Creates a minimal performance calculator backed only by design parameters.
     * All optional models ({@link CharacteristicNet}, {@link NozzleContour},
     * {@link BoundaryLayerCorrection}, {@link ChemistryModel},
     * {@link ConvergentSection}) are {@code null};
     * losses are estimated from built-in correlations.
     *
     * @param parameters Nozzle design parameters; must not be {@code null}
     * @return A new {@code PerformanceCalculator} with all optional models absent
     */
    public static PerformanceCalculator simple(NozzleDesignParameters parameters) {
        return new PerformanceCalculator(parameters, null, null, null, null, null);
    }
    
    /**
     * Executes the full performance calculation pipeline in order: ideal
     * performance, divergence loss, boundary-layer loss, chemical loss, and
     * actual delivered performance.
     * Must be called before any getter is used.
     *
     * @return This instance for method chaining
     */
    public PerformanceCalculator calculate() {
        calculateIdealPerformance();
        calculateDivergenceLoss();
        calculateBoundaryLayerLoss();
        calculateChemicalLoss();
        calculateActualPerformance();
        return this;
    }
    
    /**
     * Calculates ideal (isentropic) thrust coefficient and mass flow rate.
     * The thrust coefficient is the sum of the momentum term and the pressure
     * thrust term: {@code Cf_ideal = Cf_mom + (pe − pa) / pc · (Ae/At)}.
     * Results are stored in {@link #idealThrustCoeff} and {@link #massFlowRate}.
     */
    private void calculateIdealPerformance() {
        GasProperties gas = parameters.gasProperties();
        double gamma = gas.gamma();
        double gp1 = gamma + 1;
        double gm1 = gamma - 1;
        
        double Pc = parameters.chamberPressure();
        double Pa = parameters.ambientPressure();
        double exitMach = parameters.exitMach();
        double areaRatio = parameters.exitAreaRatio();
        
        // Exit pressure
        double Pe = Pc * gas.isentropicPressureRatio(exitMach);
        
        // Ideal thrust coefficient (momentum + pressure)
        double term1 = 2 * gamma * gamma / gm1 * Math.pow(2.0 / gp1, gp1 / gm1);
        double prRatio = Pe / Pc;
        double term2 = 1 - Math.pow(prRatio, gm1 / gamma);
        
        double cfMomentum = Math.sqrt(term1 * term2);
        double cfPressure = (Pe - Pa) / Pc * areaRatio;
        
        idealThrustCoeff = cfMomentum + cfPressure;
        
        // Mass flow rate
        double At = parameters.throatArea();
        double cStar = parameters.characteristicVelocity();
        massFlowRate = Pc * At / cStar;
    }
    
    /**
     * Calculates the thrust-coefficient reduction caused by non-axial exit flow.
     * Uses the divergence factor λ = (1 + cos α) / 2, where α is the exit flow
     * angle obtained from {@link #getLambda()}.  The result is stored in
     * {@link #divergenceLoss}.
     */
    private void calculateDivergenceLoss() {
        double lambda = getLambda();

        // Divergence loss is (1 - lambda) times momentum contribution
        GasProperties gas = parameters.gasProperties();
        double gamma = gas.gamma();
        double gp1 = gamma + 1;
        double gm1 = gamma - 1;
        
        double term1 = 2 * gamma * gamma / gm1 * Math.pow(2.0 / gp1, gp1 / gm1);
        double prRatio = gas.isentropicPressureRatio(parameters.exitMach());
        double term2 = 1 - Math.pow(prRatio, gm1 / gamma);
        double cfMomentum = Math.sqrt(term1 * term2);
        
        divergenceLoss = (1 - lambda) * cfMomentum;
    }

    /**
     * Determines the effective divergence angle and computes the divergence factor
     * {@code λ = (1 + cos α_eff) / 2}.
     * The angle source is selected in priority order:
     * <ol>
     *   <li>Last wall-point flow angle from the {@link com.nozzle.moc.CharacteristicNet}.</li>
     *   <li>Finite-difference slope of the last two points from the {@link com.nozzle.geometry.NozzleContour}.</li>
     *   <li>Fallback: RMS of the Rao exit angle and the initial wall angle, giving the
     *       optimizer objective sensitivity to all three design parameters
     *       ({@link NozzleDesignParameters#lengthFraction()},
     *        {@link NozzleDesignParameters#exitAreaRatio()}, and
     *        {@link NozzleDesignParameters#wallAngleInitial()}):
     *       <ul>
     *         <li>Rao exit angle: {@code θ_E = (20 − 15·L_f) − (1.3 − L_f)·ln(AR)} degrees,
     *             clamped to [1°, 15°], continuous in both L_f and AR.</li>
     *         <li>Effective divergence: {@code α_eff = √(0.1·θ_max² + 0.9·θ_E²)}, where
     *             θ_max is {@code wallAngleInitial}.  This represents the RMS angle when
     *             approximately 10% of the nozzle's effective length operates near the
     *             throat inflection angle and the remaining 90% near the Rao exit angle.</li>
     *       </ul>
     *   </li>
     * </ol>
     *
     * @return Divergence factor λ in the range (0, 1]; 1 = fully axial flow
     */
    private double getLambda() {
        double exitAngle = 0;

        if (characteristicNet != null) {
            List<CharacteristicPoint> wallPoints = characteristicNet.getWallPoints();
            if (!wallPoints.isEmpty()) {
                exitAngle = wallPoints.getLast().theta();
            }
        } else if (contour != null) {
            List<com.nozzle.geometry.Point2D> points = contour.getContourPoints();
            if (points.size() >= 2) {
                var last = points.getLast();
                var prev = points.get(points.size() - 2);
                exitAngle = Math.atan((last.y() - prev.y()) / (last.x() - prev.x()));
            }
        } else {
            // Rao bell exit-angle correlation (Huzel & Huang 1992):
            //   θ_E = (20 - 15·lf) - (1.3 - lf)·ln(AR)  [degrees]
            // Recovers 11° - 0.7·ln(AR) at lf=0.6 and 8° - 0.5·ln(AR) at lf=0.8,
            // and varies continuously so that both lengthFraction and exitMach matter.
            double ar        = parameters.exitAreaRatio();
            double lf        = parameters.lengthFraction();
            double lnAr      = Math.log(ar);
            double thetaEDeg = (20.0 - 15.0 * lf) - (1.3 - lf) * lnAr;
            double thetaE    = Math.toRadians(Math.clamp(thetaEDeg, 1.0, 15.0));
            // RMS effective angle: throat inflection region (~10% of effective nozzle
            // length) operates near wallAngleInitial; the downstream bell (~90%) near θ_E.
            double wallAngle = parameters.wallAngleInitial();
            exitAngle = Math.sqrt(0.1 * wallAngle * wallAngle + 0.9 * thetaE * thetaE);
        }

        // Divergence factor (lambda)
        return (1 + Math.cos(exitAngle)) / 2;
    }

    /**
     * Calculates the thrust-coefficient reduction caused by the viscous boundary
     * layer.  If a {@link com.nozzle.thermal.BoundaryLayerCorrection} model was
     * supplied, its integrated skin-friction drag is used directly; otherwise the
     * loss is estimated from the throat Reynolds number as
     * {@code 1% × Cf_ideal / Re_throat^0.2}, clamped to 3% of the ideal coefficient.
     * The result is stored in {@link #boundaryLayerLoss}.
     */
    private void calculateBoundaryLayerLoss() {
        if (boundaryLayer != null) {
            boundaryLayerLoss = boundaryLayer.getThrustCoefficientLoss();
        } else {
            // Estimate based on nozzle size and operating conditions
            double Re = estimateThroatReynolds();
            
            // Typical boundary layer loss is 0.5-2% of thrust
            boundaryLayerLoss = idealThrustCoeff * 0.01 / Math.pow(Re / 1e6, 0.2);
            boundaryLayerLoss = Math.min(boundaryLayerLoss, idealThrustCoeff * 0.03);
        }
    }
    
    /**
     * Estimates the throat Reynolds number based on throat conditions derived
     * from isentropic relations and Sutherland viscosity.
     * Used as a scaling input for the boundary-layer loss estimate when no
     * explicit {@link com.nozzle.thermal.BoundaryLayerCorrection} model is
     * provided.
     *
     * @return Throat Reynolds number {@code Re_t = ρ_t · a_t · D_t / μ_t}
     */
    private double estimateThroatReynolds() {
        GasProperties gas = parameters.gasProperties();
        double Tc = parameters.chamberTemperature();
        double Pc = parameters.chamberPressure();
        double rt = parameters.throatRadius();
        
        // Throat conditions
        double gamma = gas.gamma();
        double Tt = Tc * 2.0 / (gamma + 1);
        double rhot = Pc / (gas.gasConstant() * Tc) * Math.pow(2.0 / (gamma + 1), 1.0 / (gamma - 1));
        double at = gas.speedOfSound(Tt);
        double mut = gas.calculateViscosity(Tt);
        
        return rhot * at * 2 * rt / mut;
    }
    
    /**
     * Calculates the thrust-coefficient reduction caused by finite-rate or
     * frozen-flow chemistry effects.  If an equilibrium
     * {@link com.nozzle.chemistry.ChemistryModel} was supplied, the loss is
     * proportional to the γ variation between chamber and exit
     * ({@code (γ_c − γ_e) / γ_c × 0.5 × Cf_ideal}).
     * Otherwise, a flat 1% frozen-flow estimate is applied.
     * The result is stored in {@link #chemicalLoss}.
     */
    private void calculateChemicalLoss() {
        if (chemistry != null && chemistry.getModelType() != ChemistryModel.ModelType.FROZEN) {
            // Equilibrium vs frozen loss
            double Tc = parameters.chamberTemperature();
            double Te = parameters.exitTemperature();
            
            double gammaChamber = chemistry.calculateGamma(Tc);
            double gammaExit = chemistry.calculateGamma(Te);
            
            // Loss due to gamma change
            double gammaLoss = (gammaChamber - gammaExit) / gammaChamber;
            chemicalLoss = idealThrustCoeff * gammaLoss * 0.5;
        } else {
            // Estimate frozen flow loss (typically 1-3%)
            chemicalLoss = idealThrustCoeff * 0.01;
        }
    }

   /**
     * Combines all loss terms, applies the sonic-line discharge-coefficient
     * correction (if a {@link ConvergentSection} was supplied), and computes
     * the delivered performance metrics.
     *
     * <p>The sonic-line correction {@code Cd_geo} scales both the throat mass
     * flow and the delivered thrust proportionally, leaving Isp unchanged
     * (Isp = F / (g₀ṁ) = Cf · c* / g₀ is geometry-independent).
     */
    private void calculateActualPerformance() {
        actualThrustCoeff = idealThrustCoeff - divergenceLoss - boundaryLayerLoss - chemicalLoss;
        actualThrustCoeff = Math.max(actualThrustCoeff, idealThrustCoeff * 0.85);

        efficiency = actualThrustCoeff / idealThrustCoeff;

        double cStar = parameters.characteristicVelocity();
        double g0 = 9.80665;

        specificImpulse = cStar * actualThrustCoeff / g0;

        // Sonic-line Cd correction: scales effective throat area, reducing both
        // thrust and mass flow by the same factor.  Isp is unaffected.
        if (convergentSection != null) {
            sonicLineCdCorrection = convergentSection.getSonicLineCdCorrection();
        }

        thrust       = actualThrustCoeff * parameters.chamberPressure()
                       * parameters.throatArea() * sonicLineCdCorrection;
        massFlowRate = massFlowRate * sonicLineCdCorrection;

        LOG.debug("Performance: Cf_ideal={} Cf_actual={} Cd_geo={} efficiency={}% Isp={} s thrust={} N",
                idealThrustCoeff, actualThrustCoeff, sonicLineCdCorrection,
                efficiency * 100, specificImpulse, thrust);
    }
    
    // -------------------------------------------------------------------------
    // Result accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the ideal (isentropic) thrust coefficient with no losses applied.
     *
     * @return Ideal Cf (dimensionless)
     */
    public double getIdealThrustCoefficient() { return idealThrustCoeff; }

    /**
     * Returns the actual delivered thrust coefficient after all losses.
     *
     * @return Actual Cf (dimensionless)
     */
    public double getActualThrustCoefficient() { return actualThrustCoeff; }

    /**
     * Returns the thrust-coefficient reduction due to non-axial exit flow
     * (divergence loss).
     *
     * @return Divergence loss ΔCf (dimensionless, positive)
     */
    public double getDivergenceLoss() { return divergenceLoss; }

    /**
     * Returns the thrust-coefficient reduction due to the viscous boundary layer.
     *
     * @return Boundary-layer loss ΔCf (dimensionless, positive)
     */
    public double getBoundaryLayerLoss() { return boundaryLayerLoss; }

    /**
     * Returns the thrust-coefficient reduction due to frozen-flow or finite-rate
     * chemistry effects.
     *
     * @return Chemical loss ΔCf (dimensionless, positive)
     */
    public double getChemicalLoss() { return chemicalLoss; }

    /**
     * Returns the nozzle efficiency: the ratio of actual to ideal thrust
     * coefficient ({@code Cf_actual / Cf_ideal}).
     *
     * @return Efficiency (dimensionless, 0–1)
     */
    public double getEfficiency() { return efficiency; }

    /**
     * Returns the delivered specific impulse.
     *
     * @return Isp = {@code c* · Cf_actual / g₀} in seconds
     */
    public double getSpecificImpulse() { return specificImpulse; }

    /**
     * Returns the delivered thrust.
     *
     * @return Thrust = {@code Cf_actual · Pc · At} in N
     */
    public double getThrust() { return thrust; }

    /**
     * Returns the propellant mass flow rate through the throat, corrected for
     * the sonic-line discharge coefficient when a {@link ConvergentSection} was
     * supplied.
     *
     * @return Mass flow rate = {@code Cd_geo · Pc · At / c*} in kg/s
     */
    public double getMassFlowRate() { return massFlowRate; }

    /**
     * Returns the geometric discharge-coefficient correction from sonic-line
     * curvature.  This is 1.0 if no {@link ConvergentSection} was supplied.
     *
     * @return Cd_geo ∈ [0.98, 1.0]
     */
    public double getSonicLineCdCorrection() { return sonicLineCdCorrection; }
    
    /**
     * Returns the sum of all three thrust-coefficient loss terms.
     *
     * @return Sum of divergence, boundary-layer, and chemical loss ΔCf
     *         (dimensionless, positive)
     */
    public double getTotalLoss() {
        return divergenceLoss + boundaryLayerLoss + chemicalLoss;
    }
    
    /**
     * Creates an immutable snapshot of all computed performance metrics.
     *
     * @return A new {@link PerformanceSummary} containing all metrics produced
     *         by the most recent {@link #calculate()} call
     */
    public PerformanceSummary getSummary() {
        return new PerformanceSummary(
                idealThrustCoeff, actualThrustCoeff,
                divergenceLoss, boundaryLayerLoss, chemicalLoss,
                efficiency, specificImpulse, thrust, massFlowRate
        );
    }
    
    /**
     * Immutable snapshot of all computed performance metrics for a nozzle design.
     *
     * @param idealCf               Ideal (isentropic) thrust coefficient Cf — momentum
     *                              plus pressure term, no losses applied
     * @param actualCf              Actual thrust coefficient after subtracting divergence,
     *                              boundary-layer, and chemical losses from {@code idealCf}
     * @param divergenceLoss        Thrust-coefficient reduction due to non-axial exit flow;
     *                              computed from the divergence factor λ = (1 + cos α) / 2
     * @param boundaryLayerLoss     Thrust-coefficient reduction due to the viscous boundary
     *                              layer displacing the effective throat area
     * @param chemicalLoss          Thrust-coefficient reduction due to frozen-flow or
     *                              finite-rate chemistry effects (γ variation along the nozzle)
     * @param efficiency            Ratio of actual to ideal thrust coefficient:
     *                              {@code actualCf / idealCf} (dimensionless, 0–1)
     * @param specificImpulseSeconds Delivered specific impulse Isp in seconds:
     *                              {@code c* · actualCf / g₀}
     * @param thrustNewtons         Delivered thrust in N:
     *                              {@code actualCf · Pc · At}
     * @param massFlowRateKgPerSec  Propellant mass flow rate in kg/s:
     *                              {@code Pc · At / c*}
     */
    public record PerformanceSummary(
            double idealCf,
            double actualCf,
            double divergenceLoss,
            double boundaryLayerLoss,
            double chemicalLoss,
            double efficiency,
            double specificImpulseSeconds,
            double thrustNewtons,
            double massFlowRateKgPerSec
    ) {
        @SuppressWarnings("NullableProblems")
        @Override
        public String toString() {
            return String.format("""
                    Performance Summary:
                      Ideal Cf:        %.4f
                      Actual Cf:       %.4f
                      Divergence Loss: %.4f (%.2f%%)
                      BL Loss:         %.4f (%.2f%%)
                      Chemical Loss:   %.4f (%.2f%%)
                      Efficiency:      %.2f%%
                      Isp:             %.1f s
                      Thrust:          %.2f kN
                      Mass Flow:       %.2f kg/s
                    """,
                    idealCf, actualCf,
                    divergenceLoss, divergenceLoss / idealCf * 100,
                    boundaryLayerLoss, boundaryLayerLoss / idealCf * 100,
                    chemicalLoss, chemicalLoss / idealCf * 100,
                    efficiency * 100,
                    specificImpulseSeconds,
                    thrustNewtons / 1000,
                    massFlowRateKgPerSec);
        }
    }
}
