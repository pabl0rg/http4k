package org.http4k.lens

import org.http4k.asString
import org.http4k.core.Body
import org.http4k.core.ContentType.Companion.APPLICATION_FORM_URLENCODED
import org.http4k.core.toUrlFormEncoded
import org.http4k.lens.ContentNegotiation.Companion.StrictNoDirective
import org.http4k.lens.ParamMeta.StringParam
import java.net.URLDecoder.decode

object FormField : BiDiLensSpec<WebForm, String>("form",
    StringParam,
    LensGet { name, (fields) -> fields.getOrDefault(name, listOf()) },
    LensSet { name, values, target -> values.fold(target) { m, next -> m.plus(name to next) } }
)

data class WebForm constructor(val fields: Map<String, List<String>> = emptyMap(), val errors: List<Failure> = emptyList()) {
    operator fun plus(kv: Pair<String, String>): WebForm =
        copy(fields = fields.plus(kv.first to fields.getOrDefault(kv.first, emptyList()).plus(kv.second)))
}

fun Body.Companion.webForm(validator: Validator, vararg formFields: Lens<WebForm, *>): BiDiBodyLensSpec<WebForm> =
    httpBodyRoot(formFields.map { it.meta }, APPLICATION_FORM_URLENCODED, StrictNoDirective)
        .map({ it.payload.asString() }, { it: String -> Body(it) })
        .map(
            { WebForm(formParametersFrom(it), emptyList()) },
            { (fields) -> fields.flatMap { pair -> pair.value.map { pair.key to it } }.toUrlFormEncoded() })
        .map({ it.copy(errors = validator(it, *formFields)) }, { it.copy(errors = validator(it, *formFields)) })

private fun formParametersFrom(target: String): Map<String, List<String>> = target
    .split("&")
    .filter { it.contains("=") }
    .map { it.split("=") }
    .map { decode(it[0], "UTF-8") to if (it.size > 1) decode(it[1], "UTF-8") else "" }
    .groupBy { it.first }
    .mapValues { it.value.map { it.second } }

