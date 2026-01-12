package pl.warmescape.obsluga_leadow.bitrix.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.text.toInt

data class DealDTO(
    @JsonProperty("OPPORTUNITY")
    val bruttoPrice: BigDecimal,
    @JsonProperty("UF_CRM_1767730547361")
    val clientType: String,
    @JsonProperty("CATEGORY_ID")
    val product: String,
    @JsonProperty("UF_CRM_1766326145934")
    val identifier: String,
    @JsonProperty("UF_CRM_1767730209060")
    val fullName: String,
    @JsonProperty("UF_CRM_1767730465149")
    val streetWithNumber: String,
    @JsonProperty("UF_CRM_1767730630748")
    val postalCodeWithCity: String,
    @JsonProperty("UF_CRM_1767279799065")
    val baliaType: String,
    @JsonProperty("UF_CRM_1766325508107")
    val dealDate: OffsetDateTime,
    @JsonProperty("UF_CRM_1765899925746")
    val financingType: String,
    @JsonProperty("UF_CRM_1767279864285")
    val color: String,
    @JsonProperty("UF_CRM_1767736284227")
    val firstPayment: String? = null,
) {
    fun toDomain() = Deal(
        bruttoPrice = bruttoPrice,
        product = when(product) {
            "22" -> Product.BALIA
            "20" -> Product.SAUNA
            else -> throw RuntimeException("Nieprawidłowy rodzaj produktu!")
        },
        clientType = when (clientType) {
            "416" -> ClientType.OSOBA_PRYWATNA
            "418" -> ClientType.FIRMA
            else -> throw RuntimeException("Nieprawidłowy typ klienta!")
        },
        baliaType = baliaType,
        dealDate = dealDate.toLocalDate(),
        baliaColor = color.takeIf { it.isNotBlank() }?.let {
            BaliaColor.getById(it.toInt())
        },
        identifier = identifier.takeIf { it.isNotBlank() },
        fullName = fullName,
        streetWithNumber = streetWithNumber,
        postalCodeWithCity = postalCodeWithCity,
        financingType = when (financingType) {
            "104" -> FinancingType.RATY_0
            "106" -> FinancingType.GOTOWKA
            "108" -> FinancingType.PRZELEW
            else -> throw RuntimeException("Nieprawidłowy typ finansowania!")
        },
        firstPayment = firstPayment
            ?.takeIf { it.isNotBlank() }
            ?.split("|")
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { BigDecimal(it) }
    )
}