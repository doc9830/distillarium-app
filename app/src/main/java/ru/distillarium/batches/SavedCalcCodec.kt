package ru.distillarium.batches

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import ru.distillarium.calculators.CalcOutput
import ru.distillarium.calculators.CalcResult

/**
 * Компактное JSON-представление сохранённого расчёта. Пишется и читается только
 * приложением; битые данные не должны ронять экран истории.
 */
object SavedCalcCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encodeInputs(inputs: Map<String, String>): String = buildJsonObject {
        inputs.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
    }.toString()

    fun decodeInputs(text: String): Map<String, String> = runCatching {
        json.parseToJsonElement(text).jsonObject.mapValues { (_, value) ->
            value.jsonPrimitive.contentOrNull.orEmpty()
        }
    }.getOrDefault(emptyMap())

    fun encodeResult(result: CalcResult): String = buildJsonObject {
        result.note?.let { put("note", it) }
        putJsonArray("outputs") {
            result.outputs.forEach { output ->
                addJsonObject {
                    put("label", output.label)
                    put("value", output.value)
                    put("unit", output.unit)
                    put("decimals", output.decimals)
                }
            }
        }
    }.toString()

    fun decodeResult(text: String?): CalcResult? {
        if (text.isNullOrBlank()) return null
        return runCatching {
            val root = json.parseToJsonElement(text).jsonObject
            val outputs = (root["outputs"] as? JsonArray).orEmpty().mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                CalcOutput(
                    label = item.string("label") ?: return@mapNotNull null,
                    value = item["value"]?.jsonPrimitive?.doubleOrNull
                        ?: return@mapNotNull null,
                    unit = item.string("unit").orEmpty(),
                    decimals = item["decimals"]?.jsonPrimitive?.intOrNull ?: 1,
                )
            }
            if (outputs.isEmpty()) return@runCatching null
            CalcResult(outputs = outputs, note = root.string("note"))
        }.getOrNull()
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}
