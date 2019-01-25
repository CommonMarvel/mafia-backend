package org.common.marvel.mafia.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.common.marvel.mafia.component.GameProtocol

object JsonUtils {

    // TODO : refactor
    var init = false

    val mapper = ObjectMapper()

    fun init() {
        mapper.registerKotlinModule()
        init = true
    }

    inline fun <reified T> readValue(json: String) : T {
        if (!init) init()

        return mapper.readValue(json)
    }

    fun writeValueAsString(value: Any?): String {
        if (!init) init()

        return mapper.writeValueAsString(value)
    }

}