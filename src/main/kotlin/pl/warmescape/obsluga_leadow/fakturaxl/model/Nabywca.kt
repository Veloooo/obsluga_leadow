package pl.warmescape.obsluga_leadow.fakturaxl.model

data class Nabywca(
    val firmaLubOsobaPrywatna: Int,
    val nazwa: String,
    val imie: String? = null,
    val nazwisko: String? = null,
    val nip: String? = null,
    val ulicaINumer: String? = null,
    val kodPocztowy: String? = null,
    val miejscowosc: String? = null,
    val kraj: String = "PL",
    val email: String? = null,
    val telefon: String? = null,
    val fax: String? = null,
    val www: String? = null,
    val nrKontaBankowego: String? = null,
    val dodatkowaInformacja: String? = null
)