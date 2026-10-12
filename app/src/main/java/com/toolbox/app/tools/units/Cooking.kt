package com.toolbox.app.tools.units

import kotlin.math.abs

/**
 * The three kinds of unit in the Cooking category. A volume converts to a weight through an [Ingredient], and an
 * oven temperature only converts to another oven temperature.
 */
enum class UnitGroup { Volume, Weight, Oven }

// Exact US customary and imperial measures, in millilitres.
const val US_CUP_ML = US_GALLON * 1000 / 16 // 236.5882365
const val IMPERIAL_CUP_ML = UK_GALLON * 1000 / 16 // 284.130625, half an imperial pint
const val METRIC_CUP_ML = 250.0

/** A fan oven runs this much hotter than a conventional one, so recipes set it this much lower. */
const val FAN_OFFSET_C = 20.0

const val GAS_MARK_ID = "gas"
private const val FAHRENHEIT_ID = "f"

private fun cooking(id: String, name: String, symbol: String, group: UnitGroup, factor: Double) =
    UnitDef(id, name, symbol, { it * factor }, { it / factor }, group)

/**
 * Volumes are in millilitres and weights in grams, so a volume times an ingredient's grams per millilitre is its
 * weight. Oven temperatures are in °C, but go through [convertCooking], because gas marks are a chart, not a formula.
 * Spoons are metric (5 and 15 mL), as in the Volume category: US spoons are about 1.4% smaller, which a recipe won't
 * notice.
 */
val COOKING_UNITS: List<UnitDef> = listOf(
    cooking("tsp", "Teaspoons", "tsp", UnitGroup.Volume, 5.0),
    cooking("tbsp", "Tablespoons", "tbsp", UnitGroup.Volume, 15.0),
    cooking("cup_us", "Cups (US)", "cup (US)", UnitGroup.Volume, US_CUP_ML),
    cooking("cup_imperial", "Cups (imperial)", "cup (imperial)", UnitGroup.Volume, IMPERIAL_CUP_ML),
    cooking("cup_metric", "Cups (metric)", "cup (metric)", UnitGroup.Volume, METRIC_CUP_ML),
    cooking("ml", "Millilitres", "mL", UnitGroup.Volume, 1.0),
    cooking("l", "Litres", "L", UnitGroup.Volume, 1000.0),
    cooking("floz_uk", "Fluid ounces (UK)", "fl oz (UK)", UnitGroup.Volume, UK_GALLON * 1000 / 160),
    cooking("floz_us", "Fluid ounces (US)", "fl oz (US)", UnitGroup.Volume, US_GALLON * 1000 / 128),
    cooking("pt_uk", "Pints (UK)", "pt (UK)", UnitGroup.Volume, UK_GALLON * 1000 / 8),
    cooking("pt_us", "Pints (US)", "pt (US)", UnitGroup.Volume, US_GALLON * 1000 / 8),
    cooking("g", "Grams", "g", UnitGroup.Weight, 1.0),
    cooking("kg", "Kilograms", "kg", UnitGroup.Weight, 1000.0),
    cooking("oz", "Ounces", "oz", UnitGroup.Weight, POUND * 1000 / 16),
    cooking("lb", "Pounds", "lb", UnitGroup.Weight, POUND * 1000),
    UnitDef("c", "Celsius", "°C", { it }, { it }, UnitGroup.Oven),
    UnitDef("fan", "Celsius, fan oven", "°C fan", { it + FAN_OFFSET_C }, { it - FAN_OFFSET_C }, UnitGroup.Oven),
    UnitDef(FAHRENHEIT_ID, "Fahrenheit", "°F", { (it - 32) * 5 / 9 }, { it * 9 / 5 + 32 }, UnitGroup.Oven),
    // Only for completeness: convertCooking reads the chart instead.
    UnitDef(GAS_MARK_ID, "Gas mark", "gas mark", { gasToCelsius(it) }, { celsiusToGas(it) }, UnitGroup.Oven),
)

/** One ingredient's density. [id] is what gets saved in Prefs, so don't rename ids. */
class Ingredient(val id: String, val name: String, val gramsPerMl: Double)

/** Grams per millilitre, from a source that gives the weight of one US cup. */
private fun perUsCup(grams: Double) = grams / US_CUP_ML

/**
 * Cup weights are from USDA FoodData Central (SR Legacy), apart from water and caster sugar. They're averages:
 * how firmly a cup is filled changes them by 10% or so, which is why recipes in grams are better.
 */
val INGREDIENTS: List<Ingredient> = listOf(
    // The gram was defined as a millilitre of water, and it's within 0.3% of that at kitchen temperatures.
    Ingredient("water", "Water", 1.0),
    // USDA "Milk, whole, 3.25% milkfat": 1 cup = 244 g.
    Ingredient("milk", "Milk", perUsCup(244.0)),
    // USDA "Wheat flour, white, all-purpose, enriched, unbleached": 1 cup = 125 g.
    Ingredient("plain_flour", "Plain flour", perUsCup(125.0)),
    // USDA "Wheat flour, white, bread, enriched": 1 cup = 137 g.
    Ingredient("bread_flour", "Bread flour", perUsCup(137.0)),
    // King Arthur Baking's ingredient weight chart, "Baker's Special Sugar" (superfine): 1 cup = 190 g.
    // USDA doesn't list caster sugar.
    Ingredient("caster_sugar", "Caster sugar", perUsCup(190.0)),
    // USDA "Sugars, granulated": 1 cup = 200 g.
    Ingredient("granulated_sugar", "Granulated sugar", perUsCup(200.0)),
    // USDA "Sugars, brown": 1 cup, packed = 220 g.
    Ingredient("brown_sugar", "Brown sugar (packed)", perUsCup(220.0)),
    // USDA "Sugars, powdered": 1 cup, unsifted = 120 g.
    Ingredient("icing_sugar", "Icing sugar", perUsCup(120.0)),
    // USDA "Butter, salted": 1 cup = 227 g.
    Ingredient("butter", "Butter", perUsCup(227.0)),
    // USDA "Rice, white, long-grain, regular, raw, enriched": 1 cup = 185 g.
    Ingredient("rice", "Rice (uncooked)", perUsCup(185.0)),
    // USDA "Cereals, oats, regular and quick, not fortified, dry": 1 cup = 81 g.
    Ingredient("oats", "Oats", perUsCup(81.0)),
    // USDA "Honey": 1 cup = 339 g.
    Ingredient("honey", "Honey", perUsCup(339.0)),
)

const val DEFAULT_INGREDIENT = "plain_flour"

fun ingredient(id: String?): Ingredient =
    INGREDIENTS.firstOrNull { it.id == id } ?: INGREDIENTS.first { it.id == DEFAULT_INGREDIENT }

/** One row of the gas mark chart. */
class GasMark(val mark: Double, val celsius: Int, val fahrenheit: Int) {
    val fanCelsius: Int get() = celsius - FAN_OFFSET_C.toInt()
}

/**
 * The UK chart printed in recipes. Marks 1 to 9 are Which?'s chart. ¼, ½ and 10 are 225, 250 and 500 °F by the gas
 * mark's definition (275 °F for mark 1, then 25 °F a mark), rounded to the nearest 10 °C as the chart does. The chart's
 * °C and °F don't match exactly (180 °C is 356 °F), so a gas mark converts to each from its own column.
 */
val GAS_MARKS: List<GasMark> = listOf(
    GasMark(0.25, 110, 225),
    GasMark(0.5, 120, 250),
    GasMark(1.0, 140, 275),
    GasMark(2.0, 150, 300),
    GasMark(3.0, 160, 325),
    GasMark(4.0, 180, 350),
    GasMark(5.0, 190, 375),
    GasMark(6.0, 200, 400),
    GasMark(7.0, 220, 425),
    GasMark(8.0, 230, 450),
    GasMark(9.0, 240, 475),
    GasMark(10.0, 260, 500),
)

/** The nearest chart row to a temperature. [offChart] is -1 below ¼, 1 above 10 and 0 on the chart. */
class GasMatch(val row: GasMark, val exact: Boolean, val offChart: Int)

/**
 * The chart row nearest to [value] in [from] (°C, fan °C, °F or a gas mark). A temperature halfway between two marks
 * goes to the lower one. It's off the chart when it's further below ¼ or above 10 than half the step to the next mark.
 */
fun gasMatch(value: Double, from: UnitDef): GasMatch {
    val column: (GasMark) -> Double = when (from.id) {
        GAS_MARK_ID -> { r -> r.mark }
        FAHRENHEIT_ID -> { r -> r.fahrenheit.toDouble() }
        else -> { r -> r.celsius.toDouble() }
    }
    val x = if (from.id == GAS_MARK_ID || from.id == FAHRENHEIT_ID) value else from.toBase(value)
    val nearest = GAS_MARKS.minBy { abs(column(it) - x) } // minBy keeps the first, so ties go to the lower mark
    val first = column(GAS_MARKS[0])
    val last = column(GAS_MARKS.last())
    val offChart = when {
        x < first - (column(GAS_MARKS[1]) - first) / 2 -> -1
        x > last + (last - column(GAS_MARKS[GAS_MARKS.size - 2])) / 2 -> 1
        else -> 0
    }
    return GasMatch(nearest, abs(column(nearest) - x) < 0.05, offChart)
}

/** A gas mark between ¼ and 10 as °C, reading between the chart's rows; NaN off the chart. */
fun gasToCelsius(mark: Double): Double = alongChart(mark) { it.celsius.toDouble() }

/** As [gasToCelsius], in the chart's °F column. */
fun gasToFahrenheit(mark: Double): Double = alongChart(mark) { it.fahrenheit.toDouble() }

/** The inverse of [gasToCelsius], unrounded. Only used to complete the gas mark's [UnitDef]. */
private fun celsiusToGas(celsius: Double): Double {
    if (celsius < GAS_MARKS.first().celsius || celsius > GAS_MARKS.last().celsius) return Double.NaN
    val i = GAS_MARKS.indexOfLast { it.celsius <= celsius }.coerceAtMost(GAS_MARKS.size - 2)
    val a = GAS_MARKS[i]
    val b = GAS_MARKS[i + 1]
    return a.mark + (celsius - a.celsius) / (b.celsius - a.celsius) * (b.mark - a.mark)
}

private fun alongChart(mark: Double, column: (GasMark) -> Double): Double {
    if (mark.isNaN() || mark < GAS_MARKS.first().mark || mark > GAS_MARKS.last().mark) return Double.NaN
    val i = GAS_MARKS.indexOfLast { it.mark <= mark }.coerceAtMost(GAS_MARKS.size - 2)
    val a = GAS_MARKS[i]
    val b = GAS_MARKS[i + 1]
    return column(a) + (mark - a.mark) / (b.mark - a.mark) * (column(b) - column(a))
}

/** Whether two Cooking units can be converted: an oven temperature only pairs with another. */
fun pairs(a: UnitDef, b: UnitDef): Boolean = (a.group == UnitGroup.Oven) == (b.group == UnitGroup.Oven)

/** Whether converting between [a] and [b] needs an ingredient: one is a volume and the other a weight. */
fun needsIngredient(a: UnitDef, b: UnitDef): Boolean =
    a.group != b.group && a.group != UnitGroup.Oven && b.group != UnitGroup.Oven

/** The unit the other side switches to when [picked] can't pair with it. */
fun partnerFor(picked: UnitDef): UnitDef = UnitCategory.Cooking.unit(
    when (picked.id) {
        "c" -> GAS_MARK_ID
        "fan", FAHRENHEIT_ID, GAS_MARK_ID -> "c"
        else -> if (picked.group == UnitGroup.Weight) "cup_us" else "g"
    },
)!!

/**
 * Converts between two Cooking units. A volume and a weight go through [ingredient]. Anything converted to a gas mark
 * snaps to the nearest mark ([gasMatch] says whether that was exact), and a gas mark converts to each temperature
 * from the chart. NaN means it can't be converted: an oven unit and a non-oven one, or off the gas mark chart.
 */
fun convertCooking(value: Double, from: UnitDef, to: UnitDef, ingredient: Ingredient): Double {
    if (from === to) return value
    if (!pairs(from, to)) return Double.NaN
    if (to.id == GAS_MARK_ID) return gasMatch(value, from).takeIf { it.offChart == 0 }?.row?.mark ?: Double.NaN
    if (from.id == GAS_MARK_ID) {
        return if (to.id == FAHRENHEIT_ID) gasToFahrenheit(value) else to.fromBase(gasToCelsius(value))
    }
    val base = from.toBase(value)
    val converted = when {
        from.group == to.group -> base
        from.group == UnitGroup.Volume -> base * ingredient.gramsPerMl
        else -> base / ingredient.gramsPerMl
    }
    return to.fromBase(converted)
}

/** A gas mark as recipes write it: ¼ and ½ as fractions. */
fun formatGasMark(mark: Double, decimal: Char = '.'): String = when (mark) {
    0.25 -> "¼"
    0.5 -> "½"
    else -> formatNumber(mark, decimal = decimal)
}

/** [value] in [from] as a gas mark: "4", "≈ 4" between marks, or "—" off the chart. */
fun gasMarkText(value: Double, from: UnitDef, decimal: Char = '.'): String {
    if (from.id == GAS_MARK_ID) return formatGasMark(value, decimal)
    val match = gasMatch(value, from)
    if (match.offChart != 0) return "—"
    return (if (match.exact) "" else "≈ ") + formatGasMark(match.row.mark, decimal)
}

/**
 * The line under an oven conversion: the chart row nearest to [value] in [from], such as
 * "Gas mark 4 = 180 °C = 160 °C fan = 350 °F", starting "Nearest: " when it's between marks.
 */
fun gasChartLine(value: Double, from: UnitDef): String {
    if (from.id == GAS_MARK_ID && (value < GAS_MARKS.first().mark || value > GAS_MARKS.last().mark)) {
        return "Gas marks run from ¼ to 10"
    }
    val match = gasMatch(value, from)
    val row = match.row
    return when (match.offChart) {
        -1 -> "Below gas mark ¼ (${row.celsius} °C)"
        1 -> "Above gas mark 10 (${row.celsius} °C)"
        else -> (if (match.exact) "" else "Nearest: ") +
            "Gas mark ${formatGasMark(row.mark)} = ${row.celsius} °C = ${row.fanCelsius} °C fan = ${row.fahrenheit} °F"
    }
}

/** A typical amount to start from in [unit]: gas mark 4 for an oven temperature (as °C, fan °C or °F), or 1. */
fun startValue(unit: UnitDef): Double {
    val row = GAS_MARKS.first { it.mark == 4.0 }
    return when (unit.id) {
        GAS_MARK_ID -> row.mark
        "c" -> row.celsius.toDouble()
        "fan" -> row.fanCelsius.toDouble()
        FAHRENHEIT_ID -> row.fahrenheit.toDouble()
        else -> 1.0
    }
}
