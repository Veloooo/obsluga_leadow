package pl.warmescape.obsluga_leadow.fakturaxl.model

import java.math.BigDecimal

data class FakturaPozycja(
    val nazwa: String,
    val kodProduktu: String? = null,
    val produktId: String? = null,
    val pkwiu: String? = null,
    val symbolGtu: String? = null,
    val ilosc: Int,
    val jm: String,
    val vat: Int,
    val wartoscBrutto: BigDecimal,
)