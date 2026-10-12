package com.toolbox.app

import com.toolbox.app.tools.units.COOKING_UNITS
import com.toolbox.app.tools.units.DEFAULT_INGREDIENT
import com.toolbox.app.tools.units.GAS_MARKS
import com.toolbox.app.tools.units.INGREDIENTS
import com.toolbox.app.tools.units.UnitCategory
import com.toolbox.app.tools.units.UnitGroup
import com.toolbox.app.tools.units.convertCooking
import com.toolbox.app.tools.units.gasChartLine
import com.toolbox.app.tools.units.gasMarkText
import com.toolbox.app.tools.units.gasMatch
import com.toolbox.app.tools.units.ingredient
import com.toolbox.app.tools.units.needsIngredient
import com.toolbox.app.tools.units.pairs
import com.toolbox.app.tools.units.partnerFor
import com.toolbox.app.tools.units.startValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CookingTest {
    private fun unit(id: String) = UnitCategory.Cooking.unit(id)!!
    private val flour = ingredient("plain_flour")
    private fun conv(value: Double, from: String, to: String, with: String = "water") =
        convertCooking(value, unit(from), unit(to), ingredient(with))

    @Test fun spoonsAndCupsAreExact() {
        assertEquals(5.0, conv(1.0, "tsp", "ml"), 0.0)
        assertEquals(15.0, conv(1.0, "tbsp", "ml"), 0.0)
        assertEquals(3.0, conv(1.0, "tbsp", "tsp"), 1e-12)
        assertEquals(236.5882365, conv(1.0, "cup_us", "ml"), 1e-9)
        assertEquals(284.130625, conv(1.0, "cup_imperial", "ml"), 1e-9)
        assertEquals(250.0, conv(1.0, "cup_metric", "ml"), 0.0)
        assertEquals(8.0, conv(1.0, "cup_us", "floz_us"), 1e-12)
        assertEquals(10.0, conv(1.0, "cup_imperial", "floz_uk"), 1e-12)
        assertEquals(2.0, conv(1.0, "pt_uk", "cup_imperial"), 1e-12)
        assertEquals(2.0, conv(1.0, "pt_us", "cup_us"), 1e-12)
        assertEquals(20.0, conv(1.0, "pt_uk", "floz_uk"), 1e-12)
        assertEquals(1000.0, conv(1.0, "l", "ml"), 0.0)
        assertEquals(16.0, conv(1.0, "lb", "oz"), 1e-12)
        assertEquals(453.59237, conv(1.0, "lb", "g"), 1e-9)
        assertEquals(28.349523125, conv(1.0, "oz", "g"), 1e-9)
    }

    @Test fun everyIngredientConvertsBothWays() {
        val cupWeights = mapOf(
            "water" to 236.5882365, "milk" to 244.0, "plain_flour" to 125.0, "bread_flour" to 137.0,
            "caster_sugar" to 190.0, "granulated_sugar" to 200.0, "brown_sugar" to 220.0, "icing_sugar" to 120.0,
            "butter" to 227.0, "rice" to 185.0, "oats" to 81.0, "honey" to 339.0,
        )
        assertEquals(cupWeights.keys, INGREDIENTS.map { it.id }.toSet())
        for ((id, grams) in cupWeights) {
            assertEquals(id, grams, conv(1.0, "cup_us", "g", id), 1e-9)
            assertEquals(id, 1.0, conv(grams, "g", "cup_us", id), 1e-12)
            // Every volume and weight, there and back.
            for (v in COOKING_UNITS.filter { it.group == UnitGroup.Volume }) for (w in COOKING_UNITS.filter { it.group == UnitGroup.Weight }) {
                val there = convertCooking(2.5, v, w, ingredient(id))
                assertEquals("$id ${v.id} → ${w.id}", 2.5, convertCooking(there, w, v, ingredient(id)), 1e-9)
            }
        }
        // A UK tablespoon of flour is 15 mL, about 7.9 g.
        assertEquals(7.925, convertCooking(1.0, unit("tbsp"), unit("g"), flour), 1e-3)
    }

    @Test fun unknownIngredientFallsBackToFlour() {
        assertEquals(DEFAULT_INGREDIENT, ingredient("saffron").id)
        assertEquals(DEFAULT_INGREDIENT, ingredient(null).id)
    }

    @Test fun gasMarkChart() {
        // Each mark converts to °C and °F from its own column, and back exactly.
        for (row in GAS_MARKS) {
            assertEquals(row.celsius.toDouble(), conv(row.mark, "gas", "c"), 1e-9)
            assertEquals(row.fahrenheit.toDouble(), conv(row.mark, "gas", "f"), 1e-9)
            assertEquals(row.celsius - 20.0, conv(row.mark, "gas", "fan"), 1e-9)
            assertEquals(row.mark, conv(row.celsius.toDouble(), "c", "gas"), 0.0)
            assertEquals(row.mark, conv(row.fahrenheit.toDouble(), "f", "gas"), 0.0)
            assertEquals(row.mark, conv(row.celsius - 20.0, "fan", "gas"), 0.0)
            assertTrue(gasMatch(row.celsius.toDouble(), unit("c")).exact)
        }
        assertEquals(listOf(0.25, 0.5, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0), GAS_MARKS.map { it.mark })
        assertEquals(180.0, conv(4.0, "gas", "c"), 0.0)
        assertEquals(350.0, conv(4.0, "gas", "f"), 0.0)
        assertEquals(160.0, conv(3.0, "gas", "c"), 0.0)
        // Between marks, a typed gas mark reads between the rows.
        assertEquals(185.0, conv(4.5, "gas", "c"), 1e-9)
        assertTrue(conv(12.0, "gas", "c").isNaN())
        assertTrue(conv(0.1, "gas", "c").isNaN())
    }

    @Test fun gasMarkRounding() {
        assertEquals("4", gasMarkText(180.0, unit("c")))
        assertEquals("≈ 4", gasMarkText(185.0, unit("c")))
        assertEquals("≈ 5", gasMarkText(186.0, unit("c")))
        // Halfway goes to the lower mark.
        assertEquals("≈ 3", gasMarkText(170.0, unit("c")))
        assertEquals("½", gasMarkText(120.0, unit("c")))
        assertEquals("¼", gasMarkText(225.0, unit("f")))
        assertEquals("≈ 4", gasMarkText(356.0, unit("f")))
        assertEquals("4", gasMarkText(160.0, unit("fan")))
        // Off the chart: more than half a step below ¼ (110 °C, then 120) or above 10 (260 °C, after 240).
        assertEquals("¼", gasMarkText(105.0, unit("c")).removePrefix("≈ "))
        assertEquals("—", gasMarkText(104.0, unit("c")))
        assertEquals("≈ 10", gasMarkText(270.0, unit("c")))
        assertEquals("—", gasMarkText(271.0, unit("c")))
        assertTrue(conv(50.0, "c", "gas").isNaN())

        assertEquals("Gas mark 4 = 180 °C = 160 °C fan = 350 °F", gasChartLine(180.0, unit("c")))
        assertEquals("Nearest: Gas mark 4 = 180 °C = 160 °C fan = 350 °F", gasChartLine(356.0, unit("f")))
        assertEquals("Gas mark ½ = 120 °C = 100 °C fan = 250 °F", gasChartLine(0.5, unit("gas")))
        assertEquals("Below gas mark ¼ (110 °C)", gasChartLine(50.0, unit("c")))
        assertEquals("Above gas mark 10 (260 °C)", gasChartLine(300.0, unit("c")))
        assertEquals("Gas marks run from ¼ to 10", gasChartLine(11.0, unit("gas")))
    }

    @Test fun fanIsTwentyBelowConventional() {
        assertEquals(160.0, conv(180.0, "c", "fan"), 0.0)
        assertEquals(200.0, conv(180.0, "fan", "c"), 0.0)
        // °F is worked out from °C exactly, so 180 °C is 356 °F, not the chart's 350.
        assertEquals(356.0, conv(180.0, "c", "f"), 1e-9)
        assertEquals(356.0, conv(160.0, "fan", "f"), 1e-9)
        assertEquals(160.0, conv(356.0, "f", "fan"), 1e-9)
    }

    @Test fun ovenUnitsOnlyPairWithOvenUnits() {
        val oven = COOKING_UNITS.filter { it.group == UnitGroup.Oven }
        val other = COOKING_UNITS.filter { it.group != UnitGroup.Oven }
        assertEquals(listOf("c", "fan", "f", "gas"), oven.map { it.id })
        for (a in oven) for (b in other) {
            assertFalse(pairs(a, b))
            assertFalse(pairs(b, a))
            assertTrue(convertCooking(1.0, a, b, flour).isNaN())
            assertTrue(convertCooking(1.0, b, a, flour).isNaN())
            assertFalse(needsIngredient(a, b))
            // Picking one switches the other side to a unit it pairs with.
            assertTrue(pairs(a, partnerFor(a)) && a !== partnerFor(a))
            assertTrue(pairs(b, partnerFor(b)))
        }
        assertTrue(needsIngredient(unit("cup_us"), unit("g")))
        assertFalse(needsIngredient(unit("cup_us"), unit("ml")))
        assertEquals("g", partnerFor(unit("tbsp")).id)
        assertEquals("cup_us", partnerFor(unit("oz")).id)
        assertEquals("gas", partnerFor(unit("c")).id)
        assertEquals(180.0, startValue(unit("c")), 0.0)
        assertEquals(350.0, startValue(unit("f")), 0.0)
        assertEquals(1.0, startValue(unit("cup_us")), 0.0)
    }

    @Test fun savedIdsStayPut() {
        // The category is saved by its enum name, and units and ingredients by id, so these mustn't change.
        assertEquals(UnitCategory.Cooking, UnitCategory.valueOf("Cooking"))
        assertEquals(
            listOf("tsp", "tbsp", "cup_us", "cup_imperial", "cup_metric", "ml", "l", "floz_uk", "floz_us", "pt_uk", "pt_us",
                "g", "kg", "oz", "lb", "c", "fan", "f", "gas"),
            UnitCategory.Cooking.units.map { it.id },
        )
        assertEquals(
            listOf("water", "milk", "plain_flour", "bread_flour", "caster_sugar", "granulated_sugar", "brown_sugar",
                "icing_sugar", "butter", "rice", "oats", "honey"),
            INGREDIENTS.map { it.id },
        )
        assertEquals("cup_us", UnitCategory.Cooking.defaultFrom)
        assertEquals("g", UnitCategory.Cooking.defaultTo)
        // The Volume category's 250 mL cup was relabelled "metric" but keeps its id.
        assertEquals(250.0, UnitCategory.Volume.unit("cup_uk")!!.toBase(1.0) * 1000, 1e-9)
    }
}
