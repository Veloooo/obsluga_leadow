package pl.warmescape.obsluga_leadow.bitrix.model

enum class Seller(val description: String) {
    STORMEDGE(
    """
        Stormedge Spółka z ograniczoną odpowiedzialnością
        Siedziba: Ostrów 14, 23-212 Wilkołaz Pierwszy
        NIP: 7151957030, REGON: 541414330
        reprezentowaną przez Członka Zarządu Kacpra Jabłońskiego,
        zwanym dalej „Wykonawcą”,
    """.trimIndent()),
    KARAS(
        """
        WE Patryk Karasiewicz
        Siedziba: Ostrów 14, 23-212 Wilkołaz Pierwszy
        NIP: 7151959046, REGON: 543554672
    """.trimIndent()
    )
}