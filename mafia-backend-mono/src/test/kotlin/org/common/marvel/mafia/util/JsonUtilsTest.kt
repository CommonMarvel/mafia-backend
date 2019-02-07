package org.common.marvel.mafia.util

import com.fasterxml.jackson.module.kotlin.readValue
import org.common.marvel.mafia.config.BindCmd
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonUtilsTest {

    @Test
    fun transform() {
        val input = BindCmd("Vincent")
        val output = JsonUtils.jsonMapper.readValue<BindCmd>(JsonUtils.jsonMapper.writeValueAsString(input))

        assertEquals(input, output)
    }

}