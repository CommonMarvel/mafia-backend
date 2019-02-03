package org.common.marvel.mafia.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

object JsonUtils {

    val jsonMapper = ObjectMapper().registerKotlinModule()

}