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

package com.nozzle.geometry;

import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.moc.CharacteristicNet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Assembles the complete internal nozzle wall profile from the injector face to
 * the nozzle exit.
 *
 * <p>Combines a {@link ConvergentSection} (chamber face → just upstream of throat)
 * with a {@link NozzleContour} (throat → exit) into a single, contiguous, axially
 * ordered wall-point sequence.  This is the geometry object required by CFD/CAD
 * exporters — every exporter that writes blockMesh, STEP, or DXF needs the full
 * nozzle profile, not just the divergent section.
 *
 * <h2>Coordinate convention</h2>
 * <ul>
 *   <li>x = 0 at the throat (positive downstream)</li>
 *   <li>x &lt; 0 in the convergent section</li>
 *   <li>y = wall radius at each axial station</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * FullNozzleGeometry geom = new FullNozzleGeometry(params).generate(50, 100);
 * List<Point2D> wall = geom.getWallPoints(); // chamber face → exit
 * }</pre>
 *
 * <h2>MOC-derived divergent section</h2>
 * Use {@link #fromMOC(NozzleDesignParameters, CharacteristicNet)} to build
 * the geometry when the divergent section was computed by a MOC solver:
 * <pre>{@code
 * CharacteristicNet net = new CharacteristicNet(params).generate();
 * FullNozzleGeometry geom = FullNozzleGeometry.fromMOC(params, net).generate(50, 0);
 * }</pre>
 * (Pass {@code numDivergentPoints = 0} to skip regenerating the MOC contour.)
 */
public class FullNozzleGeometry {

    private static final Logger LOG = LoggerFactory.getLogger(FullNozzleGeometry.class);

    private static final int DEFAULT_CONVERGENT_POINTS = 50;
    private static final int DEFAULT_DIVERGENT_POINTS  = 100;

    private final NozzleDesignParameters parameters;
    private final ConvergentSection convergentSection;
    private final NozzleContour divergentContour;
    private final List<Point2D> wallPoints = new ArrayList<>();

    private boolean generated = false;

    // -------------------------------------------------------------------------
    // Constructors / factory methods
    // -------------------------------------------------------------------------

    /**
     * Creates full nozzle geometry backed by a Rao-bell divergent contour.
     * Both sections are derived from {@code parameters}.
     *
     * @param parameters Nozzle design parameters
     */
    public FullNozzleGeometry(NozzleDesignParameters parameters) {
        this(parameters,
             new ConvergentSection(parameters),
             new NozzleContour(NozzleContour.ContourType.RAO_BELL, parameters));
    }

    /**
     * Creates full nozzle geometry from pre-built section objects.
     * The caller is responsible for ensuring both sections are consistent
     * with the supplied {@code parameters}.
     *
     * @param parameters       Nozzle design parameters (used for geometry queries)
     * @param convergentSection Convergent section (chamber face → throat)
     * @param divergentContour  Divergent contour (throat → exit)
     */
    public FullNozzleGeometry(NozzleDesignParameters parameters,
                               ConvergentSection convergentSection,
                               NozzleContour divergentContour) {
        this.parameters        = parameters;
        this.convergentSection = convergentSection;
        this.divergentContour  = divergentContour;
    }

    /**
     * Factory method for use when the divergent section was designed by a
     * {@link CharacteristicNet} MOC solver.
     *
     * <p>The divergent contour is built from the net's wall points via
     * {@link NozzleContour#fromMOCWallPoints}.  Call {@link #generate(int, int)}
     * with {@code numDivergentPoints = 0} after construction to use these MOC
     * wall points as-is (no re-sampling).
     *
     * @param parameters Nozzle design parameters
     * @param net        Fully generated characteristic net
     * @return New {@code FullNozzleGeometry} backed by the MOC wall
     */
    public static FullNozzleGeometry fromMOC(NozzleDesignParameters parameters,
                                              CharacteristicNet net) {
        NozzleContour mocContour = NozzleContour.fromMOCWallPoints(
                parameters, net.getWallPoints());
        return new FullNozzleGeometry(
                parameters, new ConvergentSection(parameters), mocContour);
    }

    // -------------------------------------------------------------------------
    // Generation
    // -------------------------------------------------------------------------

    /**
     * Generates the full nozzle wall from chamber face to exit using default point
     * counts ({@value #DEFAULT_CONVERGENT_POINTS} convergent,
     * {@value #DEFAULT_DIVERGENT_POINTS} divergent).
     *
     * @return This instance for method chaining
     */
    public FullNozzleGeometry generate() {
        return generate(DEFAULT_CONVERGENT_POINTS, DEFAULT_DIVERGENT_POINTS);
    }

    /**
     * Generates the full nozzle wall from chamber face to exit.
     *
     * <p>The convergent section is re-generated with {@code numConvergentPoints}
     * points.  If {@code numDivergentPoints > 0} the divergent contour is also
     * re-generated; pass {@code 0} to keep an existing (e.g. MOC-derived)
     * divergent contour unchanged.
     *
     * <p>The two sections are concatenated with strict axial monotonicity: any
     * divergent point whose x-coordinate does not exceed the last convergent
     * point's x-coordinate is silently skipped to prevent duplicate entries at
     * the throat.
     *
     * @param numConvergentPoints Number of wall points in the convergent section (≥ 10)
     * @param numDivergentPoints  Number of wall points in the divergent section, or
     *                            {@code 0} to leave the divergent contour as-is
     * @return This instance for method chaining
     */
    public FullNozzleGeometry generate(int numConvergentPoints, int numDivergentPoints) {
        wallPoints.clear();

        // Generate convergent section
        convergentSection.generate(numConvergentPoints);
        List<Point2D> convPoints = convergentSection.getContourPoints();
        wallPoints.addAll(convPoints);

        LOG.debug("FullNozzleGeometry: {} convergent points, x=[{}, {}]",
                convPoints.size(),
                String.format("%.5f", convPoints.isEmpty() ? 0.0 : convPoints.getFirst().x()),
                String.format("%.5f", convPoints.isEmpty() ? 0.0 : convPoints.getLast().x()));

        // Optionally regenerate divergent section
        if (numDivergentPoints > 0) {
            divergentContour.generate(numDivergentPoints);
        }

        // Append divergent points, enforcing strict x-monotonicity
        double lastX = wallPoints.isEmpty() ? Double.NEGATIVE_INFINITY
                                            : wallPoints.getLast().x();
        List<Point2D> divPoints = divergentContour.getContourPoints();
        int added = 0;
        for (Point2D p : divPoints) {
            if (p.x() > lastX) {
                wallPoints.add(p);
                lastX = p.x();
                added++;
            }
        }

        LOG.debug("FullNozzleGeometry: {} divergent points appended, total wall points: {}",
                added, wallPoints.size());

        generated = true;
        return this;
    }

    // -------------------------------------------------------------------------
    // Accessors — wall geometry
    // -------------------------------------------------------------------------

    /**
     * Returns an unmodifiable view of the complete wall profile, ordered from
     * the chamber face (most negative x) to the exit (most positive x).
     *
     * @return Wall point list; empty if {@link #generate} has not been called
     */
    public List<Point2D> getWallPoints() {
        return Collections.unmodifiableList(wallPoints);
    }

    /**
     * Returns the convergent section component.
     *
     * @return Convergent section; never {@code null}
     */
    public ConvergentSection getConvergentSection() { return convergentSection; }

    /**
     * Returns the divergent contour component.
     *
     * @return Divergent nozzle contour; never {@code null}
     */
    public NozzleContour getDivergentContour() { return divergentContour; }

    /**
     * Returns the nozzle design parameters.
     *
     * @return Design parameters; never {@code null}
     */
    public NozzleDesignParameters getParameters() { return parameters; }

    // -------------------------------------------------------------------------
    // Accessors — scalar geometry
    // -------------------------------------------------------------------------

    /**
     * Returns the total axial length from chamber face to exit.
     * Only valid after {@link #generate} has been called.
     *
     * @return Total nozzle length in metres; 0 if not yet generated
     */
    public double getTotalLength() {
        if (!generated || wallPoints.isEmpty()) return 0.0;
        return wallPoints.getLast().x() - wallPoints.getFirst().x();
    }

    /**
     * Returns the axial length of the convergent section.
     * Only valid after {@link #generate} has been called.
     *
     * @return Convergent length in metres; 0 if not yet generated
     */
    public double getConvergentLength() {
        return convergentSection.getLength();
    }

    /**
     * Returns the axial length of the divergent section (throat to exit).
     * Only valid after {@link #generate} has been called.
     *
     * @return Divergent length in metres; 0 if not yet generated or empty
     */
    public double getDivergentLength() {
        if (!generated || wallPoints.isEmpty()) return 0.0;
        return wallPoints.getLast().x(); // x_exit, measured from throat at x=0
    }

    /**
     * Returns the throat radius (r_t).
     *
     * @return Throat radius in metres
     */
    public double getThroatRadius() { return parameters.throatRadius(); }

    /**
     * Returns the combustion-chamber radius r_c = r_t × √(contractionRatio).
     *
     * @return Chamber radius in metres
     */
    public double getChamberRadius() { return parameters.chamberRadius(); }

    /**
     * Returns the exit radius r_e = r_t × √(A_e / A_t).
     *
     * @return Exit radius in metres
     */
    public double getExitRadius() { return parameters.exitRadius(); }

    /**
     * Returns the axial x-coordinate of the chamber face (most upstream point).
     * Negative value (x &lt; 0 by convention).
     *
     * @return x_chamber in metres; 0 if not yet generated
     */
    public double getChamberFaceX() {
        return generated && !wallPoints.isEmpty() ? wallPoints.getFirst().x() : 0.0;
    }

    /**
     * Returns the axial x-coordinate of the nozzle exit plane.
     *
     * @return x_exit in metres; 0 if not yet generated
     */
    public double getExitX() {
        return generated && !wallPoints.isEmpty() ? wallPoints.getLast().x() : 0.0;
    }

    /**
     * Returns the combined sonic-line discharge coefficient.
     * Delegates to {@link NozzleDesignParameters#dischargeCoefficient()},
     * which accounts for throat curvature on both the convergent and divergent sides.
     *
     * @return Cd_geo ∈ [0.98, 1.0]
     */
    public double getSonicLineCd() {
        return parameters.dischargeCoefficient();
    }

    /**
     * Returns whether {@link #generate} has been called successfully.
     *
     * @return {@code true} if the geometry has been generated
     */
    public boolean isGenerated() { return generated; }

    /**
     * Returns a concise summary string for logging and diagnostics.
     *
     * @return Human-readable summary
     */
    @Override
    public String toString() {
        if (!generated) return "FullNozzleGeometry[not generated]";
        return String.format(
                "FullNozzleGeometry[r_c=%.1f mm, r_t=%.1f mm, r_e=%.1f mm, "
                + "L_conv=%.1f mm, L_div=%.1f mm, pts=%d]",
                getChamberRadius()    * 1000,
                getThroatRadius()     * 1000,
                getExitRadius()       * 1000,
                getConvergentLength() * 1000,
                getDivergentLength()  * 1000,
                wallPoints.size());
    }
}
