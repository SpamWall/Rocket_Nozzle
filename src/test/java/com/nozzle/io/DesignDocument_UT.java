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

package com.nozzle.io;

import com.nozzle.core.GasProperties;
import com.nozzle.core.NozzleDesignParameters;
import com.nozzle.geometry.Point2D;
import com.nozzle.moc.CharacteristicPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link DesignDocument}.
 *
 * <p>{@code DesignDocument} is a record whose compiler-generated accessors,
 * {@code equals}, {@code hashCode}, and {@code toString} are validated here
 * independently of the serializer.  The single handwritten method —
 * {@link DesignDocument#parametersOnly} — is tested for correct field
 * assignment and for generating a parseable ISO-8601 timestamp.
 *
 * <p>Serialization round-trip behavior is covered by
 * {@link NozzleSerializer_UT}; this class tests the record itself in
 * isolation.
 */
@DisplayName("DesignDocument Tests")
class DesignDocument_UT {

    private static NozzleDesignParameters baseParams() {
        return NozzleDesignParameters.builder()
                .throatRadius(0.05)
                .exitMach(3.0)
                .chamberPressure(7e6)
                .chamberTemperature(3500.0)
                .ambientPressure(101325.0)
                .gasProperties(GasProperties.LOX_RP1_PRODUCTS)
                .numberOfCharLines(10)
                .wallAngleInitialDegrees(30.0)
                .lengthFraction(0.8)
                .axisymmetric(true)
                .build();
    }

    /** Minimal CharacteristicPoint for use as list data in constructor tests. */
    private static CharacteristicPoint samplePoint() {
        return new CharacteristicPoint(
                0.1, 0.05, 2.5, 0.1, 0.4, 0.3,
                500_000.0, 2000.0, 0.8, 1500.0,
                0, 1, CharacteristicPoint.PointType.WALL);
    }

    // -----------------------------------------------------------------------
    // Canonical constructor
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Canonical Constructor Tests")
    class CanonicalConstructorTests {

        @Test
        @DisplayName("All six fields are stored and returned by accessors")
        void allFieldsStoredAndReturned() {
            NozzleDesignParameters params = baseParams();
            CharacteristicPoint wall = samplePoint();
            Point2D contour = new Point2D(0.2, 0.07);

            DesignDocument doc = new DesignDocument(
                    "1.0",
                    "2026-01-01T00:00:00Z",
                    params,
                    List.of(wall),
                    List.of(List.of(wall)),
                    List.of(contour));

            assertThat(doc.version()).isEqualTo("1.0");
            assertThat(doc.createdAt()).isEqualTo("2026-01-01T00:00:00Z");
            assertThat(doc.parameters()).isSameAs(params);
            assertThat(doc.wallPoints()).hasSize(1);
            assertThat(doc.netPoints()).hasSize(1);
            assertThat(doc.contourPoints()).hasSize(1);
        }

        @Test
        @DisplayName("wallPoints list contents are preserved exactly")
        void wallPointsPreserved() {
            CharacteristicPoint pt = samplePoint();
            DesignDocument doc = new DesignDocument(
                    "1.0", "2026-01-01T00:00:00Z", baseParams(),
                    List.of(pt), List.of(), List.of());

            CharacteristicPoint stored = doc.wallPoints().getFirst();
            assertThat(stored.x())    .isEqualTo(pt.x());
            assertThat(stored.y())    .isEqualTo(pt.y());
            assertThat(stored.mach()) .isEqualTo(pt.mach());
        }

        @Test
        @DisplayName("netPoints nested list structure is preserved")
        void netPointsPreserved() {
            CharacteristicPoint pt = samplePoint();
            List<List<CharacteristicPoint>> net = List.of(List.of(pt), List.of(pt, pt));
            DesignDocument doc = new DesignDocument(
                    "1.0", "2026-01-01T00:00:00Z", baseParams(),
                    List.of(), net, List.of());

            assertThat(doc.netPoints()).hasSize(2);
            assertThat(doc.netPoints().get(0)).hasSize(1);
            assertThat(doc.netPoints().get(1)).hasSize(2);
        }

        @Test
        @DisplayName("contourPoints list coordinates are preserved exactly")
        void contourPointsPreserved() {
            Point2D pt = new Point2D(0.15, 0.06);
            DesignDocument doc = new DesignDocument(
                    "1.0", "2026-01-01T00:00:00Z", baseParams(),
                    List.of(), List.of(), List.of(pt));

            assertThat(doc.contourPoints().getFirst().x()).isEqualTo(0.15);
            assertThat(doc.contourPoints().getFirst().y()).isEqualTo(0.06);
        }

        @Test
        @DisplayName("Empty lists are valid for all three computed-data fields")
        void emptyListsAreValid() {
            DesignDocument doc = new DesignDocument(
                    "1.0", "2026-01-01T00:00:00Z", baseParams(),
                    List.of(), List.of(), List.of());

            assertThat(doc.wallPoints())    .isEmpty();
            assertThat(doc.netPoints())     .isEmpty();
            assertThat(doc.contourPoints()) .isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // parametersOnly() factory
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("parametersOnly() Factory Tests")
    class ParametersOnlyFactoryTests {

        @Test
        @DisplayName("version is passed through to the document")
        void versionPassedThrough() {
            DesignDocument doc = DesignDocument.parametersOnly("2.0", baseParams());
            assertThat(doc.version()).isEqualTo("2.0");
        }

        @Test
        @DisplayName("parameters are the same instance that was supplied")
        void parametersAreSameInstance() {
            NozzleDesignParameters params = baseParams();
            DesignDocument doc = DesignDocument.parametersOnly("1.0", params);
            assertThat(doc.parameters()).isSameAs(params);
        }

        @Test
        @DisplayName("wallPoints is empty")
        void wallPointsEmpty() {
            assertThat(DesignDocument.parametersOnly("1.0", baseParams()).wallPoints()).isEmpty();
        }

        @Test
        @DisplayName("netPoints is empty")
        void netPointsEmpty() {
            assertThat(DesignDocument.parametersOnly("1.0", baseParams()).netPoints()).isEmpty();
        }

        @Test
        @DisplayName("contourPoints is empty")
        void contourPointsEmpty() {
            assertThat(DesignDocument.parametersOnly("1.0", baseParams()).contourPoints()).isEmpty();
        }

        @Test
        @DisplayName("createdAt is a valid ISO-8601 Instant string")
        void createdAtIsValidIso8601() {
            DesignDocument doc = DesignDocument.parametersOnly("1.0", baseParams());
            assertThat(doc.createdAt()).isNotBlank();
            assertThatCode(() -> Instant.parse(doc.createdAt())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("createdAt is close to the current time (within 5 seconds)")
        void createdAtIsRecent() {
            Instant before = Instant.now();
            DesignDocument doc = DesignDocument.parametersOnly("1.0", baseParams());
            Instant after = Instant.now();

            Instant ts = Instant.parse(doc.createdAt());
            assertThat(ts).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
        }
    }

    // -----------------------------------------------------------------------
    // Record contract: equals, hashCode, toString
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Record Contract Tests")
    class RecordContractTests {

        @Test
        @DisplayName("Two documents with identical fields are equal")
        void equalWhenFieldsIdentical() {
            NozzleDesignParameters params = baseParams();
            CharacteristicPoint pt = samplePoint();
            String ts = "2026-01-01T00:00:00Z";

            DesignDocument a = new DesignDocument("1.0", ts, params,
                    List.of(pt), List.of(), List.of());
            DesignDocument b = new DesignDocument("1.0", ts, params,
                    List.of(pt), List.of(), List.of());

            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("Documents differing in version are not equal")
        void notEqualWhenVersionDiffers() {
            NozzleDesignParameters params = baseParams();
            String ts = "2026-01-01T00:00:00Z";

            DesignDocument a = new DesignDocument("1.0", ts, params,
                    List.of(), List.of(), List.of());
            DesignDocument b = new DesignDocument("2.0", ts, params,
                    List.of(), List.of(), List.of());

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("hashCode is consistent with equals")
        void hashCodeConsistentWithEquals() {
            NozzleDesignParameters params = baseParams();
            String ts = "2026-01-01T00:00:00Z";

            DesignDocument a = new DesignDocument("1.0", ts, params,
                    List.of(), List.of(), List.of());
            DesignDocument b = new DesignDocument("1.0", ts, params,
                    List.of(), List.of(), List.of());

            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("toString contains the version string")
        void toStringContainsVersion() {
            DesignDocument doc = new DesignDocument("42.0", "2026-01-01T00:00:00Z",
                    baseParams(), List.of(), List.of(), List.of());
            assertThat(doc.toString()).contains("42.0");
        }

        @Test
        @DisplayName("toString contains the createdAt string")
        void toStringContainsCreatedAt() {
            String ts = "2026-06-15T12:00:00Z";
            DesignDocument doc = new DesignDocument("1.0", ts,
                    baseParams(), List.of(), List.of(), List.of());
            assertThat(doc.toString()).contains(ts);
        }
    }
}
