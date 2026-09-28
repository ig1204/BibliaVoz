package com.bibliavoz.app.voz

import com.bibliavoz.app.voz.NumerosEnLetras.enLetras
import org.junit.Assert.assertEquals
import org.junit.Test

class NumerosEnLetrasTest {

    @Test
    fun `numeros de capitulo y versiculo`() {
        assertEquals("uno", enLetras(1))
        assertEquals("quince", enLetras(15))
        assertEquals("dieciséis", enLetras(16))
        assertEquals("veintiuno", enLetras(21))
        assertEquals("veintidós", enLetras(22))
        assertEquals("treinta", enLetras(30))
        assertEquals("treinta y uno", enLetras(31))
        assertEquals("noventa y nueve", enLetras(99))
        assertEquals("cien", enLetras(100))
        assertEquals("ciento uno", enLetras(101))
        assertEquals("ciento diecinueve", enLetras(119))
        assertEquals("ciento cincuenta", enLetras(150))
        assertEquals("ciento setenta y seis", enLetras(176))
        assertEquals("quinientos", enLetras(500))
        assertEquals("novecientos noventa y nueve", enLetras(999))
    }

    @Test
    fun `miles y millones`() {
        assertEquals("mil", enLetras(1_000))
        assertEquals("dos mil ciento setenta y dos", enLetras(2_172))
        assertEquals("veintiún mil", enLetras(21_000))
        assertEquals("ciento cuarenta y cuatro mil", enLetras(144_000))
        assertEquals("ciento un mil", enLetras(101_000))
        assertEquals("seiscientos tres mil quinientos cincuenta", enLetras(603_550))
        assertEquals("un millón", enLetras(1_000_000))
        assertEquals("dos millones cien mil", enLetras(2_100_000))
    }

    @Test
    fun `las cifras del texto se leen como numeros y no como decimales`() {
        assertEquals(
            "Los descendientes de Paros: dos mil ciento setenta y dos.",
            NumerosEnLetras.reemplazarCifras("Los descendientes de Paros: 2,172."),
        )
        assertEquals(
            "ciento cuarenta y cuatro mil sellados",
            NumerosEnLetras.reemplazarCifras("144.000 sellados"),
        )
        // Con espacio detrás de la coma son dos números, no un millar.
        assertEquals("tres, dieciséis", NumerosEnLetras.reemplazarCifras("3, 16"))
        assertEquals("sin números", NumerosEnLetras.reemplazarCifras("sin números"))
    }
}
