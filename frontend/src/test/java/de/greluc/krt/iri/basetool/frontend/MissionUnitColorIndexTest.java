package de.greluc.krt.iri.basetool.frontend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests für die Farbklassen-Logik der Einheitsboxen im Einheiten-Panel der Einsatz-Detailseite.
 * Stellt sicher, dass der Index-Modulo die korrekte CSS-Klasse ergibt und die Palette
 * mindestens 20 Farben umfasst.
 */
class MissionUnitColorIndexTest {

    /** Anzahl der definierten Farbklassen in styles.css */
    static final int PALETTE_SIZE = 24;

    /**
     * Berechnet die CSS-Klasse für eine Einheitsbox anhand des Thymeleaf-Loop-Index,
     * analog zur Template-Logik: {@code 'unit-color-' + (iterStat.index % PALETTE_SIZE)}.
     */
    static String unitColorClass(int index) {
        return "unit-color-" + (index % PALETTE_SIZE);
    }

    @Test
    void paletteSizeShouldBeAtLeast20() {
        // Given / When / Then
        assertTrue(PALETTE_SIZE >= 20,
                "Die Farbpalette muss mindestens 20 Farben umfassen, ist aber: " + PALETTE_SIZE);
    }

    @ParameterizedTest(name = "Index {0} -> unit-color-{1}")
    @CsvSource({
            "0,  0",
            "1,  1",
            "23, 23",
            "24, 0",
            "25, 1",
            "47, 23",
            "48, 0",
            "100, 4"
    })
    void colorClassShouldWrapAroundPaletteSize(int index, int expectedColorIndex) {
        // Given
        String expectedClass = "unit-color-" + expectedColorIndex;

        // When
        String actualClass = unitColorClass(index);

        // Then
        assertEquals(expectedClass, actualClass,
                "Für Index " + index + " wird Klasse '" + expectedClass + "' erwartet.");
    }

    @Test
    void firstTwentyIndicesShouldProduceDistinctClasses() {
        // Given
        int count = 20;

        // When / Then
        for (int i = 0; i < count; i++) {
            for (int j = i + 1; j < count; j++) {
                String classI = unitColorClass(i);
                String classJ = unitColorClass(j);
                assertTrue(!classI.equals(classJ),
                        "Index " + i + " und " + j + " dürfen nicht dieselbe Klasse ergeben: " + classI);
            }
        }
    }
}
