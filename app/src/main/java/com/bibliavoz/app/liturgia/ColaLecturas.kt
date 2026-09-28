package com.bibliavoz.app.liturgia

/**
 * Lecturas que el servicio debe leer en voz alta, y en qué orden.
 *
 * Se pasan por aquí y no dentro del Intent porque son varios miles de
 * caracteres de texto ya resuelto: meterlos en un Intent roza el límite de
 * tamaño de las transacciones de Android.
 */
object ColaLecturas {
    @Volatile
    var items: List<LecturaConTexto> = emptyList()
        private set

    @Volatile
    var titulo: String = ""
        private set

    fun preparar(titulo: String, items: List<LecturaConTexto>) {
        this.titulo = titulo
        this.items = items
    }

    fun limpiar() {
        items = emptyList()
        titulo = ""
    }
}
