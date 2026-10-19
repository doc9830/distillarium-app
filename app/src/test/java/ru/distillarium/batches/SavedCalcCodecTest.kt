package ru.distillarium.batches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.distillarium.calculators.CalcOutput
import ru.distillarium.calculators.CalcResult

class SavedCalcCodecTest {
    @Test
    fun inputsAndResultSurviveRoundTrip() {
        val inputs = mapOf("volume" to "10,5", "strength" to "70", "target" to "40")
        val result = CalcResult(
            outputs = listOf(
                CalcOutput("Добавить воды", 5.25, "л", 2),
                CalcOutput("Итого", 15.75, "л", 2),
            ),
            note = "Проверка",
        )

        val decodedInputs = SavedCalcCodec.decodeInputs(SavedCalcCodec.encodeInputs(inputs))
        val decodedResult = SavedCalcCodec.decodeResult(SavedCalcCodec.encodeResult(result))

        assertEquals(inputs, decodedInputs)
        assertEquals(result, decodedResult)
        assertTrue(SavedCalcCodec.decodeInputs("не JSON").isEmpty())
        assertNull(SavedCalcCodec.decodeResult("не JSON"))
        assertNull(SavedCalcCodec.decodeResult(null))
    }
}
