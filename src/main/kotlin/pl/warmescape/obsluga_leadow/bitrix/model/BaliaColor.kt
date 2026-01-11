package pl.warmescape.obsluga_leadow.bitrix.model

enum class BaliaColor(val id: Int, val label: String, compatibility: String) {
    GRAFIT(172, "Grafit", "niecka akrylowa"),
    BIALY(174, "Biały", "niecka akrylowa, niecka kwadratowa"),
    NIEBIESKI(176, "Niebieski", "niecka akrylowa"),
    SIWY(178, "Siwy", "niecka akrylowa"),
    BEACH_PEARL(180, "Beach Pearl", "niecka akrylowa"),
    BIALY_METALIK(420, "Biały metalik", "niecka okrągła"),
    SZARY_METALIK(422, "Szary metalik", "niecka okrągła"),
    BEZOWY_METALIK(424, "Beżowy metalik", "niecka okrągła"),
    SZARY(182, "Szary", "niecka kwadratowa");

    val value: String?
    private val compatibility: String?

    init {
        this.value = label
        this.compatibility = compatibility
    }

    val fullLabel: String
        get() = this.value + " (" + compatibility + ")"

    companion object {
        // Metoda do pobierania na podstawie ID
        fun getById(id: Int): BaliaColor {
            for (color in entries) {
                if (color.id == id) {
                    return color
                }
            }
            throw IllegalArgumentException("Nie znaleziono koloru o ID: " + id)
        }
    }
}