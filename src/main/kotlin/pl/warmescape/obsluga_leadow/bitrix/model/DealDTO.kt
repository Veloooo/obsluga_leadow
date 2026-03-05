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
    @JsonProperty("UF_CRM_1768751673941")
    val fakturaZaliczkowaId: Int? = null,
    @JsonProperty("UF_CRM_1765899534426")
    val realizacjaOd: OffsetDateTime? = null,
    @JsonProperty("UF_CRM_1765899549914")
    val realizacjaDo: OffsetDateTime? = null,
    @JsonProperty("ASSIGNED_BY_ID")
    val assignedById: String? = null,
    @JsonProperty("CONTACT_ID")
    val contactId: String? = null,
    @JsonProperty("UF_CRM_1771774033335")
    val seller: String? = null,
) {
    fun toDomain() = Deal(
        bruttoPrice = bruttoPrice,
        product = when(product) {
            "24" -> Product.BALIA_I_SAUNA
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
            "506" -> FinancingType.KREDYT_PRZELEW
            "516" -> FinancingType.RATY_04
            else -> throw RuntimeException("Nieprawidłowy typ finansowania!")
        },
        firstPayment = firstPayment
            ?.takeIf { it.isNotBlank() }
            ?.split("|")
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { BigDecimal(it) },
        fakturaZaliczkowaId = fakturaZaliczkowaId,
        realizacjaOd = realizacjaOd?.toLocalDate(),
        realizacjaDo = realizacjaDo?.toLocalDate(),
        assignedById = assignedById?.takeIf { it.isNotBlank() }?.toLong(),
        contactId = contactId?.takeIf { it.isNotBlank() }?.toLong(),
        seller = when (seller?.takeIf { it.isNotBlank() }) {
            "512" -> Seller.STORMEDGE
            "514" -> Seller.KARAS
            null -> throw RuntimeException("Brak wybranej firmy sprzedającej!")
            else -> throw RuntimeException("Nieznana firma sprzedająca (ID: $seller)")
        },
    )
}