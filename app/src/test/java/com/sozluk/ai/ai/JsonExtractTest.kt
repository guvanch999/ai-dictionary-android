package com.sozluk.ai.ai

import com.sozluk.ai.data.ai.extractJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonExtractTest {

    @Test
    fun plainObject_returnedAsIs() {
        val raw = """{"a":1}"""
        assertEquals(raw, extractJson(raw))
    }

    @Test
    fun codeFences_stripped() {
        val raw = "```json\n{\"a\":1}\n```"
        assertEquals("{\"a\":1}", extractJson(raw))
    }

    @Test
    fun textAroundObject_stripped() {
        val raw = "Here you go:\n{\"a\":1}\nHope that helps!"
        assertEquals("{\"a\":1}", extractJson(raw))
    }

    @Test
    fun nestedBraces_keepOutermostObject() {
        val raw = "prefix {\"a\":{\"b\":2}} suffix"
        assertEquals("{\"a\":{\"b\":2}}", extractJson(raw))
    }

    @Test
    fun noObject_returnsNull() {
        assertNull(extractJson("no json here"))
        assertNull(extractJson("only an open brace {"))
    }
}
