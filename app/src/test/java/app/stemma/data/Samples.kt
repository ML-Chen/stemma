package app.stemma.data

/** Real API responses saved from en.wiktionary.org on 12 October 2026, used as test fixtures. */
object Samples {
    fun read(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResource("samples/$name")) { "missing sample $name" }.readText()
}
