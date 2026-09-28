package com.bibliavoz.app.voz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VocesIaTest {

    @Test
    fun `la voz es la que eligio el usuario`() {
        assertEquals("937314c424504d10912e5eef334993a7", VocesIa.VOZ_ID)
        assertEquals("s2.1-pro-free", VocesIa.MODELO)
    }

    @Test
    fun `la clave de una lectura depende de titulo, libro y tramos`() {
        val base = ClaveLectura.de("Evangelio", 48, listOf(intArrayOf(16, 15, 16, 18)))
        assertEquals(base, ClaveLectura.de(" Evangelio ", 48, listOf(intArrayOf(16, 15, 16, 18))))
        assertNotEquals(base, ClaveLectura.de("Primera lectura", 48, listOf(intArrayOf(16, 15, 16, 18))))
        assertNotEquals(base, ClaveLectura.de("Evangelio", 49, listOf(intArrayOf(16, 15, 16, 18))))
        assertNotEquals(base, ClaveLectura.de("Evangelio", 48, listOf(intArrayOf(16, 15, 16, 20))))
        assertNotEquals(
            base,
            ClaveLectura.de("Evangelio", 48, listOf(intArrayOf(16, 15, 16, 16), intArrayOf(16, 18, 16, 18))),
        )
        assertEquals(16, base.length)
    }

    @Test
    fun `los tramos saltan los huecos y recortan lo que se sale`() {
        val libro = listOf(
            listOf("1:1", "1:2", "", "1:4"),
            listOf("2:1", "2:2"),
        )
        assertEquals(listOf("1:2", "1:4", "2:1"), Tramos.versos(libro, 1, 2, 2, 1))
        assertEquals(listOf("2:1", "2:2"), Tramos.versos(libro, 2, 1, 9, 99))
        assertEquals(listOf("1:1"), Tramos.versos(libro, 0, 0, 1, 1))
        assertEquals(emptyList<String>(), Tramos.versos(emptyList(), 1, 1, 1, 1))
    }
}
