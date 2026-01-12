package pl.warmescape.obsluga_leadow.bitrix.model

import java.math.BigDecimal
import java.time.LocalDate

data class Deal(
    val product: Product,
    val bruttoPrice: BigDecimal,
    val clientType: ClientType,
    val identifier: String? = null,
    val fullName: String,
    val baliaType: String? = null,
    val baliaColor: BaliaColor? = null,
    val dealDate: LocalDate,
    val streetWithNumber: String,
    val postalCodeWithCity: String,
    val financingType: FinancingType,
    val firstPayment: BigDecimal? = null,
)