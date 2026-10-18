package ru.distillarium.calculators

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorsTest {
    @Test
    fun catalogHasCompleteNamesIdsAndValidDefaults() {
        assertEquals(
            listOf(
                "sugar-wash",
                "fruit-wash",
                "grain-wash",
                "sugar-addition",
                "abv",
                "heads-tails",
                "takeoff-rate",
                "reflux-ratio",
                "boiling-point",
                "wash-boiling",
                "hydrometer-temp",
                "alcoholmeter-temp",
                "brix",
                "gravity-convert",
                "mass-volume",
                "ibu",
                "dilution",
                "dilution-volume",
                "dilution-mass",
                "blend-strength",
                "blend-target",
                "liqueur",
                "mix-temperature",
                "malt",
                "spn",
                "heater",
            ),
            Calculators.all.map { it.id },
        )
        assertEquals(Calculators.all.size, Calculators.all.map { it.id }.toSet().size)
        Calculators.all.forEach { calculator ->
            assertNotNull(calculator.id, Calculators.byId(calculator.id))
            assertTrue(calculator.title.isNotBlank())
            assertTrue(calculator.short.isNotBlank())
            assertTrue(calculator.fields.isNotEmpty())
            assertTrue(
                "${calculator.id}: ${calculator.inputErrors(calculator.defaults())}",
                calculator.inputErrors(calculator.defaults()).isEmpty(),
            )
        }
        assertNull(Calculators.byId("missing"))
    }

    @Test
    fun sugarWashUsesSugarYieldAndHydromodule() {
        val result = calculate("sugar-wash", "sugar" to 5.0, "water" to 20.0)

        assertEquals(12.987012987, result.output("Максимальная крепость"), 1e-6)
        assertEquals(23.1, result.output("Объём браги"), 1e-6)
        assertEquals(4.0, result.output("Гидромодуль"), 1e-6)
        assertEquals(7.5, result.output("Дистиллят 40 %"), 1e-6)
    }

    @Test
    fun fruitAndGrainWashAddNaturalAndStarchSugar() {
        val fruit = calculate(
            "fruit-wash",
            "juice" to 20.0,
            "naturalSugar" to 100.0,
            "sugar" to 2.0,
            "water" to 5.0,
        )
        assertEquals(9.146341463, fruit.output("Спиртуозность"), 1e-6)
        assertEquals(26.24, fruit.output("Объём браги"), 1e-6)
        assertEquals(4.0, fruit.output("Сахар всего"), 1e-6)
        assertEquals(11.0, fruit.output("Гидромодуль"), 1e-6)

        val grain = calculate(
            "grain-wash",
            "grain" to 5.0,
            "starch" to 60.0,
            "water" to 20.0,
        )
        assertEquals(2.4975, grain.output("Сахар из крахмала"), 1e-6)
        assertEquals(3.74625, grain.output("Дистиллят 40 %"), 1e-6)
        assertEquals(4.0, grain.output("Гидромодуль"), 1e-6)
    }

    @Test
    fun sugarAdditionCountsSugarAlreadyInMust() {
        val result = calculate(
            "sugar-addition",
            "volume" to 20.0,
            "brix" to 12.0,
            "target" to 12.0,
        )

        assertEquals(1.563892219, result.output("Добавить сахара"), 1e-6)
        assertEquals(4.08, result.output("Всего сахара"), 1e-6)
        assertEquals(18.109509268, result.output("Конечная сахаристость"), 1e-6)
        assertEquals(12.0, result.output("Ожидаемая крепость"), 1e-6)
    }

    @Test
    fun beerAbvUsesGravityDropAndRejectsRisingGravity() {
        val result = calculate("abv", "og" to 1.050, "fg" to 1.010, "coefficient" to 131.25)

        assertEquals(5.25, result.output("Крепость"), 1e-6)
        assertEquals(80.0, result.output("Степень сбраживания"), 1e-6)
        assertEquals(40.0, result.output("Падение плотности"), 1e-6)

        assertThrows(CalculatorException::class.java) {
            calculate("abv", "og" to 1.010, "fg" to 1.020, "coefficient" to 131.25)
        }
    }

    @Test
    fun headsAndTailsAreCalculatedFromAbsoluteAlcohol() {
        val result = calculate(
            "heads-tails",
            "volume" to 20.0,
            "strength" to 40.0,
            "bodyStrength" to 90.0,
            "headsShare" to 10.0,
            "tailsShare" to 15.0,
        )

        assertEquals(8.0, result.output("Абсолютный спирт"), 1e-6)
        assertEquals(2.0, result.output("Головы"), 1e-6)
        assertEquals(6.666666667, result.output("Тело"), 1e-6)
        assertEquals(3.0, result.output("Хвосты"), 1e-6)
    }

    @Test
    fun takeoffRateConvertsMillilitersPerSecond() {
        val result = calculate(
            "takeoff-rate",
            "measured" to 100.0,
            "seconds" to 60.0,
            "total" to 20.0,
        )

        assertEquals(6.0, result.output("Скорость"), 1e-6)
        assertEquals(3.333333333, result.output("Время отбора"), 1e-6)
        assertEquals(100.0, result.output("За минуту"), 1e-6)
    }

    @Test
    fun refluxRatioUsesLatentHeatOfVaporization() {
        val result = calculate(
            "reflux-ratio",
            "strength" to 40.0,
            "power" to 2000.0,
            "takeoff" to 1.0,
        )

        assertEquals(3.250267963, result.output("Флегмовое число"), 1e-6)
        assertEquals(4.250267963, result.output("Пар"), 1e-6)
        assertEquals(1.0, result.output("Отбор"), 1e-6)
    }

    @Test
    fun boilingPointAndWashTemperatureAreConsistent() {
        val boiling = calculate("boiling-point", "strength" to 40.0, "pressure" to 760.0)
        assertEquals(80.3, boiling.output("Температура кипения"), 1e-6)
        assertEquals(80.3, boiling.output("При 760 мм рт. ст."), 1e-6)

        val wash = calculate("wash-boiling", "temperature" to 79.0, "pressure" to 760.0)
        assertEquals(55.0, wash.output("Спирт в кубе"), 1e-6)
        assertEquals(85.0, wash.output("Спирт в струе"), 1e-6)
        assertEquals(79.0, wash.output("При 760 мм рт. ст."), 1e-6)
    }

    @Test
    fun hydrometerCorrectionAddsTemperatureCoefficient() {
        val result = calculate(
            "hydrometer-temp",
            "reading" to 1.040,
            "temperature" to 30.0,
            "calibration" to 20.0,
        )

        assertEquals(1.0425, result.output("Исправленная плотность"), 1e-12)
        assertEquals(10.595892338, result.output("Plato"), 1e-6)
        assertEquals(10.594469142, result.output("Brix"), 1e-6)
    }

    @Test
    fun alcoholmeterCorrectionMovesReadingToTwentyDegrees() {
        val result = calculate(
            "alcoholmeter-temp",
            "reading" to 40.0,
            "temperature" to 30.0,
        )

        assertEquals(43.5, result.output("Крепость при 20 °C"), 1e-12)
        assertEquals(3.5, result.output("Поправка"), 1e-12)
    }

    @Test
    fun brixAndGravityConvertersAgree() {
        val brix = calculate("brix", "brix" to 12.0)
        assertEquals(1.048378242, brix.output("Плотность"), 1e-9)
        assertEquals(12.002144521, brix.output("Plato"), 1e-9)

        val gravity = calculate("gravity-convert", "sg" to 1.050)
        assertEquals(12.385323960, gravity.output("Brix"), 1e-6)
        assertEquals(12.387647125, gravity.output("Plato"), 1e-6)
    }

    @Test
    fun massToVolumeUsesSolutionDensity() {
        val result = calculate(
            "mass-volume",
            "gross" to 10.0,
            "tare" to 0.4,
            "strength" to 40.0,
        )

        assertEquals(9.6, result.output("Масса продукта"), 1e-12)
        assertEquals(10.127158131, result.output("Объём продукта"), 1e-6)
        assertEquals(0.947946095, result.output("Плотность"), 1e-9)

        assertThrows(CalculatorException::class.java) {
            calculate("mass-volume", "gross" to 0.3, "tare" to 0.4, "strength" to 40.0)
        }
    }

    @Test
    fun ibuUsesTinsethUtilization() {
        val result = calculate(
            "ibu",
            "og" to 1.050,
            "volume" to 20.0,
            "hops" to 30.0,
            "alpha" to 5.0,
            "minutes" to 60.0,
        )

        assertEquals(17.299805794, result.output("IBU"), 1e-6)
        assertEquals(23.066407725, result.output("Использование альфа-кислоты"), 1e-6)
    }

    @Test
    fun dilutionAccountsForVolumeContraction() {
        val result = calculate(
            "dilution",
            "volume" to 10.0,
            "strength" to 70.0,
            "target" to 40.0,
        )

        assertEquals(7.747503445, result.output("Добавить воды"), 1e-6)
        assertEquals(17.5, result.output("Итого объёма"), 1e-6)
        assertEquals(7.0, result.output("Абсолютный спирт"), 1e-6)

        val error = assertThrows(CalculatorException::class.java) {
            calculate("dilution", "volume" to 10.0, "strength" to 40.0, "target" to 40.0)
        }
        assertEquals("Нужная крепость должна быть ниже исходной", error.message)
    }

    @Test
    fun dilutionToVolumeCalculatesBothComponents() {
        val result = calculate(
            "dilution-volume",
            "volume" to 10.0,
            "strength" to 70.0,
            "target" to 40.0,
        )

        assertEquals(5.714285714, result.output("Спирта нужно"), 1e-6)
        assertEquals(4.427144826, result.output("Добавить воды"), 1e-6)
        assertEquals(10.0, result.output("Итого"), 1e-12)
        assertEquals(9.479460946, result.output("Масса смеси"), 1e-6)
    }

    @Test
    fun dilutionByMassKeepsEthanolMassConstant() {
        val result = calculate(
            "dilution-mass",
            "mass" to 10.0,
            "strength" to 70.0,
            "target" to 40.0,
        )

        assertEquals(8.748805339, result.output("Добавить воды"), 1e-6)
        assertEquals(19.761732863, result.output("Итого объёма"), 1e-6)
        assertEquals(18.733057490, result.output("Итого массы"), 1e-6)
    }


    @Test
    fun blendingTwoLiquidsPreservesEthanolAndMass() {
        val result = calculate(
            "blend-strength",
            "volume1" to 10.0,
            "strength1" to 70.0,
            "volume2" to 10.0,
            "strength2" to 40.0,
        )

        assertEquals(55.170680519, result.output("Крепость смеси"), 1e-6)
        assertEquals(19.938126368, result.output("Объём смеси"), 1e-6)
        assertEquals(18.334959663, result.output("Масса смеси"), 1e-6)
    }

    @Test
    fun blendingToTargetUsesMassBalance() {
        val result = calculate(
            "blend-target",
            "volume" to 20.0,
            "target" to 40.0,
            "strength1" to 70.0,
            "strength2" to 10.0,
        )

        assertEquals(9.957575211, result.output("Жидкость 1"), 1e-6)
        assertEquals(10.296973520, result.output("Жидкость 2"), 1e-6)
        assertEquals(8.817929450, result.output("Масса жидкости 1"), 1e-6)
        assertEquals(10.140992442, result.output("Масса жидкости 2"), 1e-6)
        assertEquals(20.0, result.output("Итого"), 1e-12)

        val error = assertThrows(CalculatorException::class.java) {
            calculate(
                "blend-target",
                "volume" to 20.0,
                "target" to 80.0,
                "strength1" to 70.0,
                "strength2" to 10.0,
            )
        }
        assertEquals("Нужная крепость должна быть между исходными", error.message)
    }

    @Test
    fun liqueurAccountsForSugarVolume() {
        val result = calculate(
            "liqueur",
            "volume" to 1.0,
            "strength" to 40.0,
            "water" to 0.5,
            "sugar" to 0.2,
        )

        assertEquals(24.630541872, result.output("Крепость"), 1e-6)
        assertEquals(1.624, result.output("Объём напитка"), 1e-12)
        assertEquals(123.152709360, result.output("Сахара в напитке"), 1e-6)
    }

    @Test
    fun mixingTemperaturesUsesWeightedAverage() {
        val result = calculate(
            "mix-temperature",
            "volume1" to 10.0,
            "temperature1" to 80.0,
            "volume2" to 10.0,
            "temperature2" to 20.0,
        )

        assertEquals(50.0, result.output("Температура смеси"), 1e-12)
        assertEquals(20.0, result.output("Объём смеси"), 1e-12)
    }

    @Test
    fun maltAndSpnUseEquipmentGeometry() {
        val malt = calculate(
            "malt",
            "volume" to 20.0,
            "og" to 1.050,
            "extract" to 80.0,
            "efficiency" to 75.0,
        )
        assertEquals(2.590673575, malt.output("Экстракт"), 1e-6)
        assertEquals(4.317789292, malt.output("Солод"), 1e-6)
        assertEquals(12.953367876, malt.output("Вода на затор"), 1e-6)

        val spn = calculate(
            "spn",
            "height" to 100.0,
            "diameter" to 50.0,
            "density" to 1.15,
            "hetp" to 16.0,
        )
        assertEquals(1.963495408, spn.output("Объём насадки"), 1e-6)
        assertEquals(2.258019720, spn.output("Масса насадки"), 1e-6)
        assertEquals(6.25, spn.output("Теоретических тарелок"), 1e-6)
    }

    @Test
    fun heaterUsesElectricalAndHeatBalance() {
        val result = calculate(
            "heater",
            "volume" to 30.0,
            "power" to 2000.0,
            "voltage" to 220.0,
            "start" to 20.0,
            "end" to 98.0,
            "efficiency" to 100.0,
            "tariff" to 5.0,
        )

        assertEquals(9.090909091, result.output("Сила тока"), 1e-6)
        assertEquals(24.2, result.output("Сопротивление"), 1e-9)
        assertEquals(81.627, result.output("Время нагрева"), 1e-6)
        assertEquals(2.7209, result.output("Энергия"), 1e-6)
        assertEquals(13.6045, result.output("Стоимость"), 1e-6)

        assertThrows(CalculatorException::class.java) {
            calculate(
                "heater",
                "volume" to 30.0,
                "power" to 2000.0,
                "voltage" to 220.0,
                "start" to 98.0,
                "end" to 20.0,
                "efficiency" to 100.0,
                "tariff" to 5.0,
            )
        }
    }

    @Test
    fun fieldAcceptsCommaAndDotDecimals() {
        val field = CalculatorField("value", "Значение", "л", 1.0, 0.0, 10.0)

        assertEquals(1.5, field.value("1,5")!!, 1e-12)
        assertEquals(1.5, field.value(" 1.5 ")!!, 1e-12)
        assertNull(field.error("1,5"))
    }

    @Test
    fun fieldReportsEmptyMalformedAndOutOfRangeValues() {
        val field = CalculatorField("value", "Значение", "л", 1.0, 0.0, 10.0)

        assertEquals("Введите значение", field.error("  "))
        assertEquals("Нужно число, например 12,5", field.error("abc"))
        assertEquals("Нужно конечное число", field.error("NaN"))
        assertEquals("Допустимо от 0 до 10", field.error("11"))
        assertEquals("Допустимо от 0 до 10", field.error("-0,5"))
        assertNull(field.value("Infinity"))
    }

    private fun calculate(id: String, vararg values: Pair<String, Double>): CalcResult {
        val calculator = requireNotNull(Calculators.byId(id)) { "Нет калькулятора $id" }
        return calculator.calculate(values.toMap())
    }

    private fun CalcResult.output(label: String): Double =
        requireNotNull(outputs.firstOrNull { it.label == label }) {
            "Нет результата «$label» в ${outputs.map { it.label }}"
        }.value
}
