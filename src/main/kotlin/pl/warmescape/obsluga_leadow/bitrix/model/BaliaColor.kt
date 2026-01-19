package pl.warmescape.obsluga_leadow.bitrix.model

enum class BaliaColor(val id: Int, val label: String, compatibility: String) {
    GRAFIT(172, "Grafit", "niecka akrylowa"),
    BIALY(174, "Biały", "niecka akrylowa, niecka kwadratowa"),
    NIEBIESKI(176, "Niebieski", "niecka akrylowa"),
    SIWY(178, "Siwy", "niecka akrylowa"),
    BEACH_PEARL(180, "Beach Pearl", "niecka akrylowa"),
    QUARTZ_PEARL(478, "Quartz Pearl", "niecka akrylowa"),
    SHADOW_PEARL(480, "Shadow Pearl", "niecka akrylowa"),
    NAVY_PEARL(482, "Navy Pearl", "niecka akrylowa"),
    OCEAN_PEARL(484, "Ocean Pearl", "niecka akrylowa"),
    SOLIDLY_PEARL(486, "Solidly Pearl", "niecka akrylowa"),
    BIALY_METALIK(420, "Biały metalik", "niecka okrągła"),
    SZARY_METALIK(422, "Szary metalik", "niecka okrągła"),
    BEZOWY_METALIK(424, "Beżowy metalik", "niecka okrągła");

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