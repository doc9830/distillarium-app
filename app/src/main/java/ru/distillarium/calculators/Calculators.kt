package ru.distillarium.calculators

import java.util.Locale
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

enum class CalculatorGroup(val title: String) {
    Fermentation("Брожение"),
    Separation("Разделение"),
    Measurement("Измерения"),
    Finish("Финиш"),
    Beer("Пиво"),
    Equipment("Оборудование"),
}

/** Числовое поле ввода. Границы проверяются до вызова формулы. */
data class CalculatorField(
    val key: String,
    val label: String,
    val unit: String,
    val default: Double,
    val min: Double,
    val max: Double,
    val hint: String? = null,
) {
    fun error(text: String): String? {
        val clean = text.trim().replace(',', '.')
        if (clean.isEmpty()) return "Введите значение"
        val value = clean.toDoubleOrNull() ?: return "Нужно число, например 12,5"
        if (!value.isFinite()) return "Нужно конечное число"
        if (value < min || value > max) {
            return "Допустимо от ${number(min)} до ${number(max)}"
        }
        return null
    }

    fun value(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    companion object {
        fun number(value: Double): String {
            val text = String.format(Locale.ROOT, "%.5f", value).trimEnd('0').trimEnd('.')
            return text.replace('.', ',')
        }
    }
}

data class CalcOutput(
    val label: String,
    val value: Double,
    val unit: String,
    val decimals: Int = 1,
)

data class CalcResult(
    val outputs: List<CalcOutput>,
    val note: String? = null,
)

class CalculatorException(message: String) : IllegalArgumentException(message)

data class Calculator(
    val id: String,
    val title: String,
    val short: String,
    val group: CalculatorGroup,
    val fields: List<CalculatorField>,
    val source: String? = null,
    val note: String? = null,
    val compute: (Map<String, Double>) -> CalcResult,
) {
    fun calculate(values: Map<String, Double>): CalcResult = compute(values)

    fun defaults(): Map<String, String> = fields.associate { field ->
        field.key to CalculatorField.number(field.default)
    }

    fun inputErrors(values: Map<String, String>): Map<String, String> = fields
        .mapNotNull { field -> field.error(values[field.key].orEmpty())?.let { field.key to it } }
        .toMap()
}


/**
 * Плотность водно-спиртовых растворов при 20 °C.
 *
 * Таблица Perry's Chemical Engineers' Handbook, 7th ed. задана по массовой доле
 * этанола. ABV переводится в массовую долю численно, поэтому объёмная крепость
 * и плотность согласованы между собой, а не берутся из двух разных таблиц.
 */
private object CalcMath {
    const val ETHANOL_DENSITY = 0.78934
    const val WATER_DENSITY = 0.9982

    private val densityByWeight = listOf(
        0.0 to 0.99823,
        5.0 to 0.98938,
        10.0 to 0.98187,
        15.0 to 0.97514,
        20.0 to 0.96864,
        25.0 to 0.96108,
        30.0 to 0.95382,
        35.0 to 0.94494,
        40.0 to 0.93518,
        45.0 to 0.92472,
        50.0 to 0.91384,
        55.0 to 0.90258,
        60.0 to 0.89113,
        65.0 to 0.87948,
        70.0 to 0.86766,
        75.0 to 0.85564,
        80.0 to 0.84344,
        85.0 to 0.83095,
        90.0 to 0.81797,
        95.0 to 0.80424,
        100.0 to 0.78934,
    )

    fun densityByWeight(weightPercent: Double): Double {
        val value = weightPercent.coerceIn(0.0, 100.0)
        for (index in 0 until densityByWeight.lastIndex) {
            val (leftWeight, leftDensity) = densityByWeight[index]
            val (rightWeight, rightDensity) = densityByWeight[index + 1]
            if (value <= rightWeight) {
                val share = (value - leftWeight) / (rightWeight - leftWeight)
                return leftDensity + (rightDensity - leftDensity) * share
            }
        }
        return ETHANOL_DENSITY
    }

    /** Массовая доля этанола (0..1) для объёмной крепости (0..100 % об.). */
    fun massFraction(abv: Double): Double {
        val target = abv.coerceIn(0.0, 100.0) / 100.0 * ETHANOL_DENSITY
        var low = 0.0
        var high = 1.0
        repeat(80) {
            val middle = (low + high) / 2.0
            if (middle * densityByWeight(middle * 100.0) < target) {
                low = middle
            } else {
                high = middle
            }
        }
        return (low + high) / 2.0
    }

    fun densityAt20(abv: Double): Double = densityByWeight(massFraction(abv) * 100.0)

    fun abvFromMassFraction(fraction: Double): Double {
        val safe = fraction.coerceIn(0.0, 1.0)
        return safe * densityByWeight(safe * 100.0) / ETHANOL_DENSITY * 100.0
    }

    fun solutionMass(volumeLiters: Double, abv: Double): Double =
        volumeLiters * densityAt20(abv)

    fun ethanolMass(volumeLiters: Double, abv: Double): Double =
        volumeLiters * abv / 100.0 * ETHANOL_DENSITY

    fun sgFromBrix(brix: Double): Double =
        1.0 + brix / (258.6 - brix / 258.2 * 227.1)

    fun brixFromSg(sg: Double): Double {
        var low = 0.0
        var high = 100.0
        repeat(80) {
            val middle = (low + high) / 2.0
            if (sgFromBrix(middle) < sg) low = middle else high = middle
        }
        return (low + high) / 2.0
    }

    fun platoFromSg(sg: Double): Double =
        -616.868 + 1111.14 * sg - 630.272 * sg * sg + 135.997 * sg * sg * sg

    // Кипение при 760 мм рт. ст. по объёмной крепости.
    private val boilingAbv = doubleArrayOf(
        0.0, 5.0, 10.0, 20.0, 30.0, 40.0, 50.0,
        60.0, 70.0, 80.0, 90.0, 95.0, 96.0,
    )
    private val boilingC = doubleArrayOf(
        100.0, 94.2, 89.5, 84.4, 81.9, 80.3, 79.3,
        78.7, 78.4, 78.25, 78.15, 78.15, 78.15,
    )

    fun boilingPoint760(abv: Double): Double {
        val value = abv.coerceIn(0.0, 96.0)
        for (index in 0 until boilingAbv.lastIndex) {
            if (value <= boilingAbv[index + 1]) {
                val share = (value - boilingAbv[index]) /
                    (boilingAbv[index + 1] - boilingAbv[index])
                return boilingC[index] + (boilingC[index + 1] - boilingC[index]) * share
            }
        }
        return 78.15
    }

    fun abvFromBoiling760(temperature: Double): Double {
        val value = temperature.coerceIn(78.15, 100.0)
        for (index in 0 until boilingC.lastIndex) {
            if (value >= boilingC[index + 1]) {
                val share = (boilingC[index] - value) / (boilingC[index] - boilingC[index + 1])
                return boilingAbv[index] + (boilingAbv[index + 1] - boilingAbv[index]) * share
            }
        }
        return 0.0
    }

    /** Уравнение Клаузиуса–Клапейрона: поправка температуры кипения на давление. */
    fun boilingAtPressure(bp760: Double, pressureMmHg: Double): Double {
        val kelvin = bp760 + 273.15
        val pressureTerm = 8.314 * ln(pressureMmHg / 760.0) / 40650.0
        return 1.0 / (1.0 / kelvin - pressureTerm) - 273.15
    }

    fun bp760FromPressure(temperatureC: Double, pressureMmHg: Double): Double {
        val kelvin = temperatureC + 273.15
        val pressureTerm = 8.314 * ln(pressureMmHg / 760.0) / 40650.0
        return 1.0 / (1.0 / kelvin + pressureTerm) - 273.15
    }

    /** Теплота парообразования смеси, кДж/кг. */
    fun latentHeat(abv: Double): Double {
        val fraction = massFraction(abv)
        return (1.0 - fraction) * 2257.0 + fraction * 846.0
    }

    // Равновесная крепость пара над кипящей жидкостью, % об.
    private val vaporLiquidAbv = doubleArrayOf(
        0.0, 5.0, 10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 96.0,
    )
    private val vaporAbvValues = doubleArrayOf(
        0.0, 32.0, 48.0, 62.0, 71.0, 78.0, 83.0, 87.0, 90.0, 93.0, 95.0, 96.0,
    )

    /** Ориентировочная крепость пара над кипящей жидкостью. */
    fun vaporAbv(liquidAbv: Double): Double {
        val value = liquidAbv.coerceIn(0.0, 96.0)
        for (index in 0 until vaporLiquidAbv.lastIndex) {
            if (value <= vaporLiquidAbv[index + 1]) {
                val share = (value - vaporLiquidAbv[index]) /
                    (vaporLiquidAbv[index + 1] - vaporLiquidAbv[index])
                return vaporAbvValues[index] +
                    (vaporAbvValues[index + 1] - vaporAbvValues[index]) * share
            }
        }
        return 96.0
    }
}



object Calculators {
    val all: List<Calculator> = listOf(
        sugarWash(),
        fruitWash(),
        grainWash(),
        sugarAddition(),
        beerAbv(),
        headsTails(),
        takeoffRate(),
        refluxRatio(),
        boilingPoint(),
        washBoiling(),
        hydrometerTemp(),
        alcoholmeterTemp(),
        brix(),
        gravityConvert(),
        massVolume(),
        ibu(),
        dilution(),
        dilutionVolume(),
        dilutionMass(),
        blendStrength(),
        blendTarget(),
        liqueur(),
        mixTemperature(),
        malt(),
        spn(),
        heater(),
    )

    val groups: List<CalculatorGroup> = CalculatorGroup.entries

    fun byId(id: String): Calculator? = all.firstOrNull { it.id == id }

    private fun sugarWash() = Calculator(
        id = "sugar-wash",
        title = "Сахарная брага",
        short = "Крепость, гидромодуль и выход дистиллята из сахара и воды.",
        group = CalculatorGroup.Fermentation,
        fields = listOf(
            CalculatorField("sugar", "Сахар", "кг", 5.0, 0.1, 500.0),
            CalculatorField("water", "Вода", "л", 20.0, 1.0, 1000.0),
        ),
        source = "Спирт = сахар × 0,6 л/кг; объём = вода + сахар × 0,62 л/кг",
        note = "0,6 л абсолютного спирта на 1 кг сахара — практический выход. " +
            "Теоретический максимум около 0,68 л/кг, остальное теряется в брожении и перегонке.",
        compute = { values ->
            val sugar = values.getValue("sugar")
            val water = values.getValue("water")
            val alcohol = sugar * 0.6
            val total = water + sugar * 0.62
            CalcResult(
                outputs = listOf(
                    CalcOutput("Максимальная крепость", alcohol / total * 100.0, "% об.", 1),
                    CalcOutput("Объём браги", total, "л", 2),
                    CalcOutput("Гидромодуль", water / sugar, "л/кг", 2),
                    CalcOutput("Дистиллят 40 %", alcohol / 0.4, "л", 2),
                ),
            )
        },
    )

    private fun fruitWash() = Calculator(
        id = "fruit-wash",
        title = "Фруктовая брага",
        short = "Учитывает природный сахар сока, добавленный сахар и воду.",
        group = CalculatorGroup.Fermentation,
        fields = listOf(
            CalculatorField("juice", "Сок", "л", 20.0, 1.0, 1000.0),
            CalculatorField(
                "naturalSugar",
                "Сахаристость сока",
                "г/л",
                100.0,
                0.0,
                300.0,
                "Яблоки — около 100, виноград — 150–180, ягоды — 60–120 г/л",
            ),
            CalculatorField("sugar", "Добавленный сахар", "кг", 2.0, 0.0, 200.0),
            CalculatorField("water", "Добавленная вода", "л", 5.0, 0.0, 500.0),
        ),
        source = "Сахар = сок × сахаристость + добавка; спирт = сахар × 0,6",
        note = "Сахаристость сока — ориентир. Кислотность и дрожжи подбирайте отдельно.",
        compute = { values ->
            val juice = values.getValue("juice")
            val naturalSugar = values.getValue("naturalSugar")
            val sugar = values.getValue("sugar")
            val water = values.getValue("water")
            val totalSugar = juice * naturalSugar / 1000.0 + sugar
            val alcohol = totalSugar * 0.6
            val total = juice + water + sugar * 0.62
            val hydromodule = (water + juice * 0.85) / max(sugar, 0.01)
            CalcResult(
                outputs = listOf(
                    CalcOutput("Спиртуозность", alcohol / total * 100.0, "% об.", 1),
                    CalcOutput("Объём браги", total, "л", 2),
                    CalcOutput("Сахар всего", totalSugar, "кг", 2),
                    CalcOutput("Гидромодуль", hydromodule, "л/кг", 2),
                    CalcOutput("Дистиллят 40 %", alcohol / 0.4, "л", 2),
                ),
            )
        },
    )

    private fun grainWash() = Calculator(
        id = "grain-wash",
        title = "Зерновая брага",
        short = "Сахар из крахмала, гидромодуль и ожидаемый выход спирта.",
        group = CalculatorGroup.Fermentation,
        fields = listOf(
            CalculatorField("grain", "Зерно", "кг", 5.0, 0.1, 500.0),
            CalculatorField("starch", "Крахмал", "%", 60.0, 30.0, 80.0),
            CalculatorField("water", "Вода", "л", 20.0, 1.0, 1000.0),
        ),
        source = "Сахар = зерно × крахмал × 1,11 × 0,75; спирт = сахар × 0,6",
        note = "Учтены перевод крахмала в глюкозу (1,11) и потери осахаривания 25 %. " +
            "Для кукурузы, пшеницы и ячменя значения близки, но выход зависит от помола и ферментов.",
        compute = { values ->
            val grain = values.getValue("grain")
            val starch = values.getValue("starch")
            val water = values.getValue("water")
            val sugar = grain * starch / 100.0 * 1.11 * 0.75
            val alcohol = sugar * 0.6
            val total = water + grain * 0.65
            CalcResult(
                outputs = listOf(
                    CalcOutput("Спиртуозность", alcohol / total * 100.0, "% об.", 1),
                    CalcOutput("Сахар из крахмала", sugar, "кг", 2),
                    CalcOutput("Объём браги", total, "л", 2),
                    CalcOutput("Гидромодуль", water / grain, "л/кг", 2),
                    CalcOutput("Дистиллят 40 %", alcohol / 0.4, "л", 2),
                ),
            )
        },
    )

    private fun sugarAddition() = Calculator(
        id = "sugar-addition",
        title = "Сахар в сусло",
        short = "Сколько сахара добавить до нужной крепости и какая будет сахаристость.",
        group = CalculatorGroup.Fermentation,
        fields = listOf(
            CalculatorField("volume", "Объём сусла", "л", 20.0, 1.0, 10_000.0),
            CalculatorField("brix", "Начальная сахаристость", "°Brix", 12.0, 0.0, 40.0),
            CalculatorField("target", "Нужная крепость", "% об.", 12.0, 1.0, 20.0),
        ),
        source = "Сахар = крепость × объём × 0,017 кг/(л·% об.)",
        note = "17 г сахара на 1 л на 1 % об. — практическая оценка для винных дрожжей. " +
            "Если сахара уже достаточно, добавка будет нулевой.",
        compute = { values ->
            val volume = values.getValue("volume")
            val brix = values.getValue("brix")
            val target = values.getValue("target")
            val sg = CalcMath.sgFromBrix(brix)
            val existingSugar = volume * sg * brix / 100.0
            val targetSugar = target * volume * 0.017
            val addition = max(0.0, targetSugar - existingSugar)
            val finalBrix = targetSugar / (volume + targetSugar * 0.62) * 100.0
            CalcResult(
                outputs = listOf(
                    CalcOutput("Добавить сахара", addition, "кг", 2),
                    CalcOutput("Всего сахара", targetSugar, "кг", 2),
                    CalcOutput("Конечная сахаристость", finalBrix, "°Brix", 1),
                    CalcOutput("Ожидаемая крепость", target, "% об.", 1),
                ),
            )
        },
    )


    private fun beerAbv() = Calculator(
        id = "abv",
        title = "Крепость пива и вина",
        short = "Алкоголь и степень сбраживания по начальной и конечной плотности.",
        group = CalculatorGroup.Beer,
        fields = listOf(
            CalculatorField("og", "Начальная плотность (OG)", "SG", 1.050, 1.000, 1.200),
            CalculatorField("fg", "Конечная плотность (FG)", "SG", 1.010, 0.980, 1.200),
            CalculatorField(
                "coefficient",
                "Коэффициент",
                "",
                131.25,
                120.0,
                140.0,
                "131,25 — стандартная оценка ABV по падению плотности",
            ),
        ),
        source = "ABV = (OG − FG) × 131,25; сбраживание = (OG − FG) / (OG − 1)",
        note = "Для пива и вина это хорошая оценка. Для крепкого дистиллята измеряйте крепость спиртометром.",
        compute = { values ->
            val og = values.getValue("og")
            val fg = values.getValue("fg")
            val coefficient = values.getValue("coefficient")
            if (fg > og) throw CalculatorException("Конечная плотность не может быть выше начальной")
            val drop = og - fg
            val attenuation = if (og > 1.0) drop / (og - 1.0) * 100.0 else 0.0
            CalcResult(
                outputs = listOf(
                    CalcOutput("Крепость", drop * coefficient, "% об.", 2),
                    CalcOutput("Степень сбраживания", attenuation, "%", 1),
                    CalcOutput("Падение плотности", drop * 1000.0, "пунктов", 1),
                ),
            )
        },
    )

    private fun headsTails() = Calculator(
        id = "heads-tails",
        title = "Головы, тело и хвосты",
        short = "Объёмы фракций от абсолютного спирта и заданных долей отбора.",
        group = CalculatorGroup.Separation,
        fields = listOf(
            CalculatorField("volume", "Спирт-сырец", "л", 20.0, 0.1, 10_000.0),
            CalculatorField("strength", "Крепость сырца", "% об.", 40.0, 1.0, 96.0),
            CalculatorField("bodyStrength", "Крепость тела", "% об.", 90.0, 40.0, 96.0),
            CalculatorField("headsShare", "Доля голов от АС", "%", 10.0, 1.0, 45.0),
            CalculatorField("tailsShare", "Доля хвостов от АС", "%", 15.0, 1.0, 45.0),
        ),
        source = "АС = V × C; головы и хвосты — заданные доли АС; тело — остаток",
        note = "Головы и хвосты пересчитаны в объём при крепости сырца, тело — при заданной крепости. " +
            "Доли — ориентир, а не команда: фракции проверяйте по запаху и спиртометру.",
        compute = { values ->
            val volume = values.getValue("volume")
            val strength = values.getValue("strength")
            val bodyStrength = values.getValue("bodyStrength")
            val headsShare = values.getValue("headsShare")
            val tailsShare = values.getValue("tailsShare")
            val alcohol = volume * strength / 100.0
            val headsAlcohol = alcohol * headsShare / 100.0
            val tailsAlcohol = alcohol * tailsShare / 100.0
            val bodyAlcohol = alcohol - headsAlcohol - tailsAlcohol
            if (bodyAlcohol <= 0.0) {
                throw CalculatorException("Сумма голов и хвостов должна быть меньше 100 %")
            }
            CalcResult(
                outputs = listOf(
                    CalcOutput("Абсолютный спирт", alcohol, "л", 2),
                    CalcOutput("Головы", headsAlcohol / (strength / 100.0), "л", 2),
                    CalcOutput("Тело", bodyAlcohol / (bodyStrength / 100.0), "л", 2),
                    CalcOutput("Хвосты", tailsAlcohol / (strength / 100.0), "л", 2),
                ),
            )
        },
    )


    private fun takeoffRate() = Calculator(
        id = "takeoff-rate",
        title = "Скорость отбора",
        short = "Переводит замеренный объём и время в литры в час и считает время перегонки.",
        group = CalculatorGroup.Separation,
        fields = listOf(
            CalculatorField("measured", "Собрано за замер", "мл", 100.0, 1.0, 10_000.0),
            CalculatorField("seconds", "Время замера", "с", 60.0, 1.0, 86_400.0),
            CalculatorField("total", "Всего продукта", "л", 20.0, 0.1, 10_000.0),
        ),
        source = "Q = V × 3600 / t; время = общий объём / Q",
        note = "Замеряйте несколько минут, а не одну каплю: так меньше ошибка реакции и секундомера.",
        compute = { values ->
            val measured = values.getValue("measured")
            val seconds = values.getValue("seconds")
            val total = values.getValue("total")
            val litersPerHour = measured / 1000.0 / (seconds / 3600.0)
            val hours = total / litersPerHour
            CalcResult(
                outputs = listOf(
                    CalcOutput("Скорость", litersPerHour, "л/ч", 2),
                    CalcOutput("Время отбора", hours, "ч", 2),
                    CalcOutput("За минуту", measured / seconds * 60.0, "мл/мин", 1),
                ),
            )
        },
    )

    private fun refluxRatio() = Calculator(
        id = "reflux-ratio",
        title = "Флегмовое число",
        short = "Оценка возврата флегмы по мощности нагрева и скорости отбора.",
        group = CalculatorGroup.Separation,
        fields = listOf(
            CalculatorField("strength", "Крепость отбора", "% об.", 40.0, 1.0, 96.0),
            CalculatorField("power", "Мощность нагрева", "Вт", 2000.0, 100.0, 20_000.0),
            CalculatorField("takeoff", "Скорость отбора", "л/ч", 1.0, 0.01, 100.0),
        ),
        source = "Пар = P / теплота парообразования; R = (пар − отбор) / отбор",
        note = "Идеальная оценка: вся мощность идёт на испарение, потерь и нагрева куба нет. " +
            "Реальное флегмовое число зависит от теплопотерь колонны.",
        compute = { values ->
            val strength = values.getValue("strength")
            val power = values.getValue("power")
            val takeoff = values.getValue("takeoff")
            val latent = CalcMath.latentHeat(strength)
            val vaporKgPerHour = power / (latent * 1000.0) * 3600.0
            val vaporLitersPerHour = vaporKgPerHour / CalcMath.densityAt20(strength)
            val reflux = (vaporLitersPerHour - takeoff) / takeoff
            if (reflux < 0.0) {
                throw CalculatorException("Мощности не хватает для такой скорости отбора")
            }
            CalcResult(
                outputs = listOf(
                    CalcOutput("Флегмовое число", reflux, "", 2),
                    CalcOutput("Пар", vaporLitersPerHour, "л/ч", 2),
                    CalcOutput("Отбор", takeoff, "л/ч", 2),
                ),
            )
        },
    )

    private fun boilingPoint() = Calculator(
        id = "boiling-point",
        title = "Кипение спирта при давлении",
        short = "Температура кипения раствора заданной крепости при выбранном давлении.",
        group = CalculatorGroup.Separation,
        fields = listOf(
            CalculatorField("strength", "Крепость", "% об.", 40.0, 0.0, 96.0),
            CalculatorField("pressure", "Давление", "мм рт. ст.", 760.0, 300.0, 800.0),
        ),
        source = "Таблица кипения этанол–вода при 760 мм; поправка Клаузиуса–Клапейрона",
        note = "Для смеси этанол–вода таблица приближённая. На практике важнее температура пара в колонне.",
        compute = { values ->
            val strength = values.getValue("strength")
            val pressure = values.getValue("pressure")
            val at760 = CalcMath.boilingPoint760(strength)
            val current = CalcMath.boilingAtPressure(at760, pressure)
            CalcResult(
                outputs = listOf(
                    CalcOutput("Температура кипения", current, "°C", 1),
                    CalcOutput("При 760 мм рт. ст.", at760, "°C", 1),
                ),
            )
        },
    )

    private fun washBoiling() = Calculator(
        id = "wash-boiling",
        title = "Крепость по температуре кипения",
        short = "Оценивает спирт в кубе и в паре по температуре кипения браги.",
        group = CalculatorGroup.Separation,
        fields = listOf(
            CalculatorField("temperature", "Температура в кубе", "°C", 79.0, 78.0, 100.0),
            CalculatorField("pressure", "Давление", "мм рт. ст.", 760.0, 300.0, 800.0),
        ),
        source = "Обратная таблица кипения этанол–вода; крепость пара — оценка по VLE",
        note = "Метод годится для ориентира во время перегонки. Точную крепость даёт спиртометр в струе.",
        compute = { values ->
            val temperature = values.getValue("temperature")
            val pressure = values.getValue("pressure")
            val at760 = CalcMath.bp760FromPressure(temperature, pressure)
            val liquid = CalcMath.abvFromBoiling760(at760)
            CalcResult(
                outputs = listOf(
                    CalcOutput("Спирт в кубе", liquid, "% об.", 1),
                    CalcOutput("Спирт в струе", CalcMath.vaporAbv(liquid), "% об.", 1),
                    CalcOutput("При 760 мм рт. ст.", at760, "°C", 1),
                ),
            )
        },
    )


    private fun hydrometerTemp() = Calculator(
        id = "hydrometer-temp",
        title = "Коррекция ареометра",
        short = "Приводит показания ареометра к температуре калибровки 20 °C.",
        group = CalculatorGroup.Measurement,
        fields = listOf(
            CalculatorField("reading", "Показание ареометра", "SG", 1.040, 0.800, 1.200),
            CalculatorField("temperature", "Температура пробы", "°C", 30.0, 0.0, 60.0),
            CalculatorField("calibration", "Температура калибровки", "°C", 20.0, 10.0, 30.0),
        ),
        source = "SG испр. = SG + 0,00025 × (T − Tкалибр.)",
        note = "Линейная поправка для бытовых измерений. Для точных работ используйте паспортную таблицу ареометра.",
        compute = { values ->
            val reading = values.getValue("reading")
            val temperature = values.getValue("temperature")
            val calibration = values.getValue("calibration")
            val corrected = reading + 0.00025 * (temperature - calibration)
            CalcResult(
                outputs = listOf(
                    CalcOutput("Исправленная плотность", corrected, "SG", 4),
                    CalcOutput("Plato", CalcMath.platoFromSg(corrected), "°P", 2),
                    CalcOutput("Brix", CalcMath.brixFromSg(corrected), "°Brix", 2),
                ),
            )
        },
    )

    private fun alcoholmeterTemp() = Calculator(
        id = "alcoholmeter-temp",
        title = "Коррекция спиртометра",
        short = "Пересчитывает показания спиртового ареометра на 20 °C.",
        group = CalculatorGroup.Measurement,
        fields = listOf(
            CalculatorField("reading", "Показание спиртометра", "% об.", 40.0, 0.0, 96.0),
            CalculatorField("temperature", "Температура спирта", "°C", 30.0, 0.0, 40.0),
        ),
        source = "ABV₂₀ ≈ ABV + 0,35 × (T − 20 °C)",
        note = "Коэффициент 0,35 %/°C — практическое приближение. Для юридических измерений нужна поверенная таблица.",
        compute = { values ->
            val reading = values.getValue("reading")
            val temperature = values.getValue("temperature")
            val correction = 0.35 * (temperature - 20.0)
            val corrected = (reading + correction).coerceIn(0.0, 96.0)
            CalcResult(
                outputs = listOf(
                    CalcOutput("Крепость при 20 °C", corrected, "% об.", 1),
                    CalcOutput("Поправка", corrected - reading, "% об.", 1),
                ),
            )
        },
    )

    private fun brix() = Calculator(
        id = "brix",
        title = "Brix, Plato и SG",
        short = "Переводит сахар по рефрактометру в плотность и градусы Plato.",
        group = CalculatorGroup.Measurement,
        fields = listOf(
            CalculatorField("brix", "Содержание сахара", "°Brix", 12.0, 0.0, 40.0),
        ),
        source = "SG = 1 + Brix / (258,6 − Brix / 258,2 × 227,1); Plato — полином по SG",
        note = "Brix и Plato близки, но не равны. Для растворов с алкоголем нужна отдельная поправка.",
        compute = { values ->
            val brix = values.getValue("brix")
            val sg = CalcMath.sgFromBrix(brix)
            CalcResult(
                outputs = listOf(
                    CalcOutput("Плотность", sg, "SG", 4),
                    CalcOutput("Plato", CalcMath.platoFromSg(sg), "°P", 2),
                ),
            )
        },
    )


    private fun gravityConvert() = Calculator(
        id = "gravity-convert",
        title = "SG → Brix и Plato",
        short = "Переводит измеренную плотность в сахаристость Brix и Plato.",
        group = CalculatorGroup.Measurement,
        fields = listOf(
            CalculatorField("sg", "Плотность", "SG", 1.050, 1.000, 1.200),
        ),
        source = "Brix — обратное уравнение Brix → SG; Plato — полином по SG",
        note = "Для пива и вина удобнее измерять SG, а рецепты часто записаны в Plato или Brix.",
        compute = { values ->
            val sg = values.getValue("sg")
            CalcResult(
                outputs = listOf(
                    CalcOutput("Brix", CalcMath.brixFromSg(sg), "°Brix", 2),
                    CalcOutput("Plato", CalcMath.platoFromSg(sg), "°P", 2),
                ),
            )
        },
    )

    private fun massVolume() = Calculator(
        id = "mass-volume",
        title = "Масса ↔ объём",
        short = "Объём продукта по массе брутто, таре и крепости.",
        group = CalculatorGroup.Measurement,
        fields = listOf(
            CalculatorField("gross", "Масса брутто", "кг", 10.0, 0.01, 10_000.0),
            CalculatorField("tare", "Масса тары", "кг", 0.4, 0.0, 1_000.0),
            CalculatorField("strength", "Крепость", "% об.", 40.0, 0.0, 96.0),
        ),
        source = "V = (масса брутто − тара) / плотность(крепость)",
        note = "Плотность берётся при 20 °C. Взвешивание на бытовых весах даёт погрешность около 10–20 мл.",
        compute = { values ->
            val gross = values.getValue("gross")
            val tare = values.getValue("tare")
            val strength = values.getValue("strength")
            val net = gross - tare
            if (net <= 0.0) throw CalculatorException("Масса брутто должна быть больше массы тары")
            val density = CalcMath.densityAt20(strength)
            CalcResult(
                outputs = listOf(
                    CalcOutput("Масса продукта", net, "кг", 3),
                    CalcOutput("Объём продукта", net / density, "л", 3),
                    CalcOutput("Плотность", density, "кг/л", 3),
                ),
            )
        },
    )


    private fun ibu() = Calculator(
        id = "ibu",
        title = "Горечь IBU",
        short = "Расчёт горечи хмеля по модели Tinseth.",
        group = CalculatorGroup.Beer,
        fields = listOf(
            CalculatorField("og", "Плотность сусла (OG)", "SG", 1.050, 1.000, 1.120),
            CalculatorField("volume", "Объём варки", "л", 20.0, 1.0, 1_000.0),
            CalculatorField("hops", "Хмель", "г", 30.0, 1.0, 2_000.0),
            CalculatorField("alpha", "Альфа-кислота", "%", 5.0, 0.5, 20.0),
            CalculatorField("minutes", "Время кипения", "мин", 60.0, 1.0, 180.0),
        ),
        source = "Tinseth: IBU = α × масса × 1000 × утилизация / объём",
        note = "Модель Tinseth даёт оценку. Реальная горечь зависит от сорта хмеля, гранул и интенсивности кипения.",
        compute = { values ->
            val og = values.getValue("og")
            val volume = values.getValue("volume")
            val hops = values.getValue("hops")
            val alpha = values.getValue("alpha")
            val minutes = values.getValue("minutes")
            val utilization = 1.65 * 0.000125.pow(og - 1.0) *
                (1.0 - exp(-0.04 * minutes)) / 4.15
            val ibu = hops * (alpha / 100.0) * 1000.0 * utilization / volume
            CalcResult(
                outputs = listOf(
                    CalcOutput("IBU", ibu, "IBU", 1),
                    CalcOutput("Использование альфа-кислоты", utilization * 100.0, "%", 1),
                ),
            )
        },
    )

    private fun dilution() = Calculator(
        id = "dilution",
        title = "Разбавление спирта",
        short = "Сколько воды добавить к известному объёму спирта до нужной крепости.",
        group = CalculatorGroup.Finish,
        fields = listOf(
            CalculatorField("volume", "Объём спирта", "л", 10.0, 0.1, 10_000.0),
            CalculatorField("strength", "Исходная крепость", "% об.", 70.0, 1.0, 96.0),
            CalculatorField("target", "Нужная крепость", "% об.", 40.0, 0.1, 95.0),
        ),
        source = "Массовый баланс этанола: m(вода) = m(этанол) / w(цель) − m(раствор)",
        note = "Учитывается контракция: объём смеси меньше суммы объёмов спирта и воды. " +
            "После смешивания измерьте крепость при 20 °C.",
        compute = { values ->
            val volume = values.getValue("volume")
            val strength = values.getValue("strength")
            val target = values.getValue("target")
            if (target >= strength) {
                throw CalculatorException("Нужная крепость должна быть ниже исходной")
            }
            val ethanol = CalcMath.ethanolMass(volume, strength)
            val initialMass = CalcMath.solutionMass(volume, strength)
            val finalMass = ethanol / CalcMath.massFraction(target)
            val waterMass = finalMass - initialMass
            CalcResult(
                outputs = listOf(
                    CalcOutput("Добавить воды", waterMass / CalcMath.WATER_DENSITY, "л", 2),
                    CalcOutput("Итого объёма", finalMass / CalcMath.densityAt20(target), "л", 2),
                    CalcOutput("Абсолютный спирт", volume * strength / 100.0, "л", 2),
                ),
            )
        },
    )


    private fun dilutionVolume() = Calculator(
        id = "dilution-volume",
        title = "Разбавление до объёма",
        short = "Сколько спирта и воды взять, чтобы получить нужный объём напитка.",
        group = CalculatorGroup.Finish,
        fields = listOf(
            CalculatorField("volume", "Нужный объём", "л", 10.0, 0.1, 10_000.0),
            CalculatorField("strength", "Исходная крепость", "% об.", 70.0, 1.0, 96.0),
            CalculatorField("target", "Нужная крепость", "% об.", 40.0, 0.1, 95.0),
        ),
        source = "m(этанол) = V₂ × C₂ × ρэт; V₁ = m(этанол) / (C₁ × ρэт)",
        note = "Считается по массе с учётом плотности и контракции. Итоговый объём точно равен заданному.",
        compute = { values ->
            val volume = values.getValue("volume")
            val strength = values.getValue("strength")
            val target = values.getValue("target")
            if (target >= strength) {
                throw CalculatorException("Нужная крепость должна быть ниже исходной")
            }
            val finalMass = CalcMath.solutionMass(volume, target)
            val ethanol = CalcMath.ethanolMass(volume, target)
            val initialMass = ethanol / CalcMath.massFraction(strength)
            val waterMass = finalMass - initialMass
            CalcResult(
                outputs = listOf(
                    CalcOutput("Спирта нужно", initialMass / CalcMath.densityAt20(strength), "л", 2),
                    CalcOutput("Добавить воды", waterMass / CalcMath.WATER_DENSITY, "л", 2),
                    CalcOutput("Итого", volume, "л", 2),
                    CalcOutput("Масса смеси", finalMass, "кг", 2),
                ),
            )
        },
    )

    private fun dilutionMass() = Calculator(
        id = "dilution-mass",
        title = "Разбавление по массе",
        short = "Сколько воды добавить к заданной массе спирта до нужной крепости.",
        group = CalculatorGroup.Finish,
        fields = listOf(
            CalculatorField("mass", "Масса спирта", "кг", 10.0, 0.1, 10_000.0),
            CalculatorField("strength", "Исходная крепость", "% об.", 70.0, 1.0, 96.0),
            CalculatorField("target", "Нужная крепость", "% об.", 40.0, 0.1, 95.0),
        ),
        source = "m(вода) = m × w₁ / w₂ − m",
        note = "Массовый баланс этанола. Объём итоговой смеси считается по плотности при 20 °C.",
        compute = { values ->
            val mass = values.getValue("mass")
            val strength = values.getValue("strength")
            val target = values.getValue("target")
            if (target >= strength) {
                throw CalculatorException("Нужная крепость должна быть ниже исходной")
            }
            val ethanol = mass * CalcMath.massFraction(strength)
            val finalMass = ethanol / CalcMath.massFraction(target)
            val waterMass = finalMass - mass
            CalcResult(
                outputs = listOf(
                    CalcOutput("Добавить воды", waterMass / CalcMath.WATER_DENSITY, "л", 2),
                    CalcOutput("Итого объёма", finalMass / CalcMath.densityAt20(target), "л", 2),
                    CalcOutput("Итого массы", finalMass, "кг", 2),
                ),
            )
        },
    )

    private fun blendStrength() = Calculator(
        id = "blend-strength",
        title = "Смешивание двух жидкостей",
        short = "Крепость и объём смеси из двух спиртосодержащих жидкостей.",
        group = CalculatorGroup.Finish,
        fields = listOf(
            CalculatorField("volume1", "Объём жидкости 1", "л", 10.0, 0.0, 10_000.0),
            CalculatorField("strength1", "Крепость жидкости 1", "% об.", 70.0, 0.0, 96.0),
            CalculatorField("volume2", "Объём жидкости 2", "л", 10.0, 0.0, 10_000.0),
            CalculatorField("strength2", "Крепость жидкости 2", "% об.", 40.0, 0.0, 96.0),
        ),
        source = "Суммируются массы этанола и раствора; ABV находится по массовой доле",
        note = "Объём смеси меньше суммы объёмов из-за контракции — это нормально.",
        compute = { values ->
            val volume1 = values.getValue("volume1")
            val strength1 = values.getValue("strength1")
            val volume2 = values.getValue("volume2")
            val strength2 = values.getValue("strength2")
            val mass1 = CalcMath.solutionMass(volume1, strength1)
            val mass2 = CalcMath.solutionMass(volume2, strength2)
            val finalMass = mass1 + mass2
            if (finalMass <= 0.0) throw CalculatorException("Нужен хотя бы один ненулевой объём")
            val ethanol = CalcMath.ethanolMass(volume1, strength1) +
                CalcMath.ethanolMass(volume2, strength2)
            val abv = CalcMath.abvFromMassFraction(ethanol / finalMass)
            CalcResult(
                outputs = listOf(
                    CalcOutput("Крепость смеси", abv, "% об.", 1),
                    CalcOutput("Объём смеси", finalMass / CalcMath.densityAt20(abv), "л", 2),
                    CalcOutput("Масса смеси", finalMass, "кг", 2),
                ),
            )
        },
    )


    private fun blendTarget() = Calculator(
        id = "blend-target",
        title = "Купаж до крепости и объёма",
        short = "Сколько двух спиртов разной крепости взять для точного объёма.",
        group = CalculatorGroup.Finish,
        fields = listOf(
            CalculatorField("volume", "Нужный объём", "л", 20.0, 0.1, 10_000.0),
            CalculatorField("target", "Нужная крепость", "% об.", 40.0, 0.1, 95.0),
            CalculatorField("strength1", "Крепость жидкости 1", "% об.", 70.0, 0.1, 96.0),
            CalculatorField("strength2", "Крепость жидкости 2", "% об.", 10.0, 0.0, 95.0),
        ),
        source = "Массовый баланс по этанолу и общей массе при заданном объёме",
        note = "Нужная крепость должна лежать между крепостями исходных жидкостей. " +
            "Объёмы исходных жидкостей могут быть больше итогового из-за контракции.",
        compute = { values ->
            val volume = values.getValue("volume")
            val target = values.getValue("target")
            val strength1 = values.getValue("strength1")
            val strength2 = values.getValue("strength2")
            if (target >= max(strength1, strength2) || target <= kotlin.math.min(strength1, strength2)) {
                throw CalculatorException("Нужная крепость должна быть между исходными")
            }
            val wTarget = CalcMath.massFraction(target)
            val w1 = CalcMath.massFraction(strength1)
            val w2 = CalcMath.massFraction(strength2)
            if (w1 == w2) throw CalculatorException("Крепости жидкостей должны различаться")
            val finalMass = CalcMath.solutionMass(volume, target)
            val mass1 = finalMass * (wTarget - w2) / (w1 - w2)
            val mass2 = finalMass - mass1
            if (mass1 < 0.0 || mass2 < 0.0) {
                throw CalculatorException("Нужная крепость не достигается этими жидкостями")
            }
            CalcResult(
                outputs = listOf(
                    CalcOutput("Жидкость 1", mass1 / CalcMath.densityAt20(strength1), "л", 2),
                    CalcOutput("Жидкость 2", mass2 / CalcMath.densityAt20(strength2), "л", 2),
                    CalcOutput("Масса жидкости 1", mass1, "кг", 2),
                    CalcOutput("Масса жидкости 2", mass2, "кг", 2),
                    CalcOutput("Итого", volume, "л", 2),
                ),
            )
        },
    )

    private fun liqueur() = Calculator(
        id = "liqueur",
        title = "Крепость настойки",
        short = "Итоговая крепость после добавления воды и сахара.",
        group = CalculatorGroup.Finish,
        fields = listOf(
            CalculatorField("volume", "Объём спирта", "л", 1.0, 0.1, 1_000.0),
            CalculatorField("strength", "Начальная крепость", "% об.", 40.0, 1.0, 96.0),
            CalculatorField("water", "Добавленная вода", "л", 0.5, 0.0, 100.0),
            CalculatorField("sugar", "Сахар", "кг", 0.2, 0.0, 100.0),
        ),
        source = "ABV = спирт / (спирт + вода + сахар × 0,62)",
        note = "0,62 л/кг — приблизительный объём растворённого сахара. Плотность и контракция не учитываются.",
        compute = { values ->
            val volume = values.getValue("volume")
            val strength = values.getValue("strength")
            val water = values.getValue("water")
            val sugar = values.getValue("sugar")
            val finalVolume = volume + water + sugar * 0.62
            val alcohol = volume * strength / 100.0
            CalcResult(
                outputs = listOf(
                    CalcOutput("Крепость", alcohol / finalVolume * 100.0, "% об.", 1),
                    CalcOutput("Объём напитка", finalVolume, "л", 2),
                    CalcOutput("Сахара в напитке", sugar * 1000.0 / finalVolume, "г/л", 1),
                ),
            )
        },
    )

    private fun mixTemperature() = Calculator(
        id = "mix-temperature",
        title = "Смешивание температур",
        short = "Итоговая температура при смешивании двух объёмов жидкости.",
        group = CalculatorGroup.Finish,
        fields = listOf(
            CalculatorField("volume1", "Объём жидкости 1", "л", 10.0, 0.1, 10_000.0),
            CalculatorField("temperature1", "Температура жидкости 1", "°C", 80.0, -20.0, 150.0),
            CalculatorField("volume2", "Объём жидкости 2", "л", 10.0, 0.1, 10_000.0),
            CalculatorField("temperature2", "Температура жидкости 2", "°C", 20.0, -20.0, 150.0),
        ),
        source = "T = (V₁T₁ + V₂T₂) / (V₁ + V₂)",
        note = "Оценка для воды и близких по теплоёмкости жидкостей. Теплопотери в баке не учитываются.",
        compute = { values ->
            val volume1 = values.getValue("volume1")
            val temperature1 = values.getValue("temperature1")
            val volume2 = values.getValue("volume2")
            val temperature2 = values.getValue("temperature2")
            val total = volume1 + volume2
            CalcResult(
                outputs = listOf(
                    CalcOutput(
                        "Температура смеси",
                        (volume1 * temperature1 + volume2 * temperature2) / total,
                        "°C",
                        1,
                    ),
                    CalcOutput("Объём смеси", total, "л", 2),
                ),
            )
        },
    )

    private fun malt() = Calculator(
        id = "malt",
        title = "Солод для сусла",
        short = "Масса солода и вода на затор для заданного объёма и плотности.",
        group = CalculatorGroup.Beer,
        fields = listOf(
            CalculatorField("volume", "Объём сусла", "л", 20.0, 1.0, 10_000.0),
            CalculatorField("og", "Начальная плотность", "SG", 1.050, 1.000, 1.120),
            CalculatorField("extract", "Экстрактивность солода", "%", 80.0, 50.0, 90.0),
            CalculatorField("efficiency", "КПД затирания", "%", 75.0, 40.0, 100.0),
        ),
        source = "Солод = (OG − 1) × 1000 × V / (386 × экстрактивность × КПД)",
        note = "386 пунктов·л/кг — экстракт 1 кг сахара в 1 л. Для солода учитываются экстрактивность и потери затирания.",
        compute = { values ->
            val volume = values.getValue("volume")
            val og = values.getValue("og")
            val extract = values.getValue("extract")
            val efficiency = values.getValue("efficiency")
            val extractKg = (og - 1.0) * 1000.0 * volume / 386.0
            val malt = extractKg / (extract / 100.0 * efficiency / 100.0)
            CalcResult(
                outputs = listOf(
                    CalcOutput("Солод", malt, "кг", 2),
                    CalcOutput("Экстракт", extractKg, "кг", 2),
                    CalcOutput("Вода на затор", malt * 3.0, "л", 1),
                ),
            )
        },
    )

    private fun spn() = Calculator(
        id = "spn",
        title = "Насадка СПН",
        short = "Объём, масса и теоретические тарелки для насадки в царге.",
        group = CalculatorGroup.Equipment,
        fields = listOf(
            CalculatorField("height", "Высота насадки", "см", 100.0, 5.0, 500.0),
            CalculatorField("diameter", "Внутренний диаметр", "мм", 50.0, 10.0, 300.0),
            CalculatorField(
                "density",
                "Насыпная плотность",
                "кг/л",
                1.15,
                0.3,
                3.0,
                "СПН 5×5×0,3 нерж. — около 1,15 кг/л",
            ),
            CalculatorField(
                "hetp",
                "ВЭТТ",
                "см",
                16.0,
                5.0,
                60.0,
                "Высота, эквивалентная теоретической тарелке. Для СПН 5×5 — 14–18 см",
            ),
        ),
        source = "V = π × (D/2)² × H; масса = V × плотность; тарелки = H / ВЭТТ",
        note = "ВЭТТ зависит от скорости пара, флегмового числа и качества укладки. Расчёт — ориентир для проектирования.",
        compute = { values ->
            val height = values.getValue("height")
            val diameter = values.getValue("diameter")
            val density = values.getValue("density")
            val hetp = values.getValue("hetp")
            val radiusCm = diameter / 20.0
            val volume = PI * radiusCm * radiusCm * height / 1000.0
            CalcResult(
                outputs = listOf(
                    CalcOutput("Объём насадки", volume, "л", 2),
                    CalcOutput("Масса насадки", volume * density, "кг", 2),
                    CalcOutput("Теоретических тарелок", height / hetp, "шт.", 1),
                ),
            )
        },
    )

    private fun heater() = Calculator(
        id = "heater",
        title = "ТЭН и нагрев",
        short = "Ток, сопротивление, время нагрева, энергия и стоимость.",
        group = CalculatorGroup.Equipment,
        fields = listOf(
            CalculatorField("volume", "Объём жидкости", "л", 30.0, 1.0, 10_000.0),
            CalculatorField("power", "Мощность ТЭНа", "Вт", 2000.0, 100.0, 20_000.0),
            CalculatorField("voltage", "Напряжение", "В", 220.0, 12.0, 400.0),
            CalculatorField("start", "Начальная температура", "°C", 20.0, 0.0, 100.0),
            CalculatorField("end", "Конечная температура", "°C", 98.0, 1.0, 100.0),
            CalculatorField("efficiency", "КПД нагрева", "%", 100.0, 50.0, 100.0),
            CalculatorField("tariff", "Цена электричества", "₽/кВт·ч", 5.0, 0.0, 100.0),
        ),
        source = "I = P/U; R = U²/P; t = m × c × ΔT / (P × КПД)",
        note = "Для воды c = 4186 Дж/(кг·К), масса 1 л = 1 кг. Потери в баке и на испарение не учитываются.",
        compute = { values ->
            val volume = values.getValue("volume")
            val power = values.getValue("power")
            val voltage = values.getValue("voltage")
            val start = values.getValue("start")
            val end = values.getValue("end")
            val efficiency = values.getValue("efficiency")
            val tariff = values.getValue("tariff")
            val delta = end - start
            if (delta <= 0.0) throw CalculatorException("Конечная температура должна быть выше начальной")
            val energyJoules = volume * 4186.0 * delta / (efficiency / 100.0)
            val seconds = energyJoules / power
            val energyKwh = energyJoules / 3_600_000.0
            CalcResult(
                outputs = listOf(
                    CalcOutput("Сила тока", power / voltage, "А", 2),
                    CalcOutput("Сопротивление", voltage * voltage / power, "Ом", 1),
                    CalcOutput("Время нагрева", seconds / 60.0, "мин", 1),
                    CalcOutput("Энергия", energyKwh, "кВт·ч", 2),
                    CalcOutput("Стоимость", energyKwh * tariff, "₽", 2),
                ),
            )
        },
    )
}
