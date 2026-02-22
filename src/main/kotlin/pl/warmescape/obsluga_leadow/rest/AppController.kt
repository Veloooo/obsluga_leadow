package pl.warmescape.obsluga_leadow.rest

import jakarta.servlet.http.HttpServletRequest
import mu.two.KotlinLogging
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import pl.warmescape.obsluga_leadow.bitrix.model.BaliaTypeData
import pl.warmescape.obsluga_leadow.bitrix.model.BaliaTypes
import pl.warmescape.obsluga_leadow.bitrix.model.ClientType
import pl.warmescape.obsluga_leadow.bitrix.model.InvoiceType
import pl.warmescape.obsluga_leadow.bitrix.model.Product
import pl.warmescape.obsluga_leadow.bitrix.service.BitrixDealService
import pl.warmescape.obsluga_leadow.bitrix.service.DataProcessingService
import pl.warmescape.obsluga_leadow.fakturaxl.FakturaXlConnector
import pl.warmescape.obsluga_leadow.bitrix.util.BitrixWebhookUtils

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/bitrix")
class DealWebhookController(
    private val bitrixDealService: BitrixDealService,
    private val dataProcessingService: DataProcessingService,
    private val fakturaXlConnector: FakturaXlConnector
) {
    @PostMapping("/przygotuj-dane-do-umowy", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun prepareData(
        request: HttpServletRequest
    ): ResponseEntity<String> {
        val dealId = BitrixWebhookUtils.extractDealId(request)
        if (dealId != null) {
            val deal = bitrixDealService.getDeal(dealId)
            var baliaDataToUpdate: BaliaTypeData? = null
            if(deal.product == Product.BALIA || deal.product == Product.BALIA_I_SAUNA) {
                baliaDataToUpdate = BaliaTypes.BY_ID[deal.baliaType]!!
            }
            val amountPolish = dataProcessingService.numberToWordsPl(deal.bruttoPrice.toInt())
            val remainingPart = deal.firstPayment?.let { deal.bruttoPrice.minus(it) }
            val identifierOnDeal = deal.identifier?.let {
                when (deal.clientType) {
                    ClientType.FIRMA -> "NIP: $it"
                    ClientType.OSOBA_PRYWATNA -> "PESEL: $it"
                }
            }
            val fieldsMap = buildMap {
                put("UF_CRM_1766327512939", amountPolish)

                baliaDataToUpdate?.let {
                    putAll(
                        mapOf(
                            "UF_CRM_1768048546106" to it.shape,
                            "UF_CRM_1768048564350" to it.dimension,
                            "UF_CRM_1768048592302" to it.material,
                        )
                    )
                }

                deal.baliaColor?.let {
                    put("UF_CRM_1768060626878", it.label)
                }

                remainingPart?.let {
                    put("UF_CRM_1767452702411", it)
                }

                identifierOnDeal?.let {
                    put("UF_CRM_1768061569390", it)
                }

                put("UF_CRM_1771775169946", deal.seller.description)
            }
            bitrixDealService.updateDealFields(
                dealId,
                fieldsMap
            )
        }

        return ResponseEntity.ok("OK")
    }

    @PostMapping("/generuj-fz-pf", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun prepareFzPf(
        request: HttpServletRequest
    ): ResponseEntity<String> {
        val dealId = BitrixWebhookUtils.extractDealId(request)
        if (dealId != null) {
            val deal = bitrixDealService.getDeal(dealId)
            val invoiceCode = deal.firstPayment?.let {
                logger.info { "First payment, generating invoice pf" }
                fakturaXlConnector.generateInvoice(
                    deal, InvoiceType.PROFORMA_ZALICZKA
                )
            }
            logger.info { "Invoice code: $invoiceCode" }
            invoiceCode?.let {
                val invoiceFile = fakturaXlConnector.getInvoiceContent(it.unikatowyKod)
                bitrixDealService.uploadFakturaBase64(dealId, invoiceFile, InvoiceType.PROFORMA_ZALICZKA, it.dokumentNr)
            }
        }

        return ResponseEntity.ok("OK")
    }

    @PostMapping("/generuj-fz", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun prepareFz(
        request: HttpServletRequest
    ): ResponseEntity<String> {
        val dealId = BitrixWebhookUtils.extractDealId(request)
        if (dealId != null) {
            val deal = bitrixDealService.getDeal(dealId)
            val invoiceCode = deal.firstPayment?.let {
                logger.info { "First payment, generating invoice" }
                fakturaXlConnector.generateInvoice(
                    deal, InvoiceType.ZALICZKA
                )
            }
            invoiceCode?.let {
                val invoiceFile = fakturaXlConnector.getInvoiceContent(it.unikatowyKod)
                bitrixDealService.uploadFakturaBase64(dealId, invoiceFile, InvoiceType.ZALICZKA, it.dokumentNr)
                bitrixDealService.updateDealFields(
                    dealId,
                    mapOf(
                        "UF_CRM_1768751673941" to it.dokumentId
                    )
                )
            }
        }

        return ResponseEntity.ok("OK")
    }

    @PostMapping("/generuj-wydarzenie", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun generujWydarzenie(
        request: HttpServletRequest
    ): ResponseEntity<String> {
        val dealId = BitrixWebhookUtils.extractDealId(request)
        if (dealId != null) {
            val deal = bitrixDealService.getDeal(dealId)
            val od = deal.realizacjaOd
            val doo = deal.realizacjaDo
            if (od != null && doo != null) {
                // TODO: testowe – pobieranie uczestników po imionach
                val currentUserId = bitrixDealService.getCurrentUserId()
                val attendees = listOf(currentUserId) + listOf(8L, 1L, 38L, 10L, 14L).filter { it != currentUserId }
                val contactName = deal.contactId?.let { bitrixDealService.getContactFullName(it) } ?: deal.fullName
                val description = "#$dealId | ${deal.streetWithNumber}, ${deal.postalCodeWithCity}"
                val eventsToCreate = when (deal.product) {
                    Product.BALIA -> listOf("Realizacja Balia | $contactName" to "#1E90FF")
                    Product.SAUNA -> listOf("Realizacja Sauna | $contactName" to "#FFD700")
                    Product.BALIA_I_SAUNA -> listOf(
                        "Realizacja Balia | $contactName" to "#1E90FF",
                        "Realizacja Sauna | $contactName" to "#FFD700",
                    )
                }
                eventsToCreate.forEach { (eventName, color) ->
                    logger.info { "Tworzenie wydarzenia '$eventName' dla deala $dealId: $od – $doo, kolor: $color" }
                    bitrixDealService.createCalendarEvent(
                        name = eventName,
                        from = od,
                        to = doo,
                        description = description,
                        dealId = dealId,
                        attendees = attendees,
                        color = color,
                    )
                }
            } else {
                logger.warn { "Brak dat realizacji dla deala $dealId (od=$od, do=$doo)" }
            }
        }
        return ResponseEntity.ok("OK")
    }

    @PostMapping("/generuj-fk", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun prepareFk(
        request: HttpServletRequest
    ): ResponseEntity<String> {
        val dealId = BitrixWebhookUtils.extractDealId(request)
        println("DEAL ID: $dealId")
        if (dealId != null) {
            val deal = bitrixDealService.getDeal(dealId)
            val invoiceType = when(deal.firstPayment != null) {
                true -> InvoiceType.KONCOWA
                false -> InvoiceType.FV
            }
            val invoiceCode = fakturaXlConnector.generateInvoice(
                deal, invoiceType
            )

            invoiceCode?.let {
                val invoiceFile = fakturaXlConnector.getInvoiceContent(it.unikatowyKod)
                bitrixDealService.uploadFakturaBase64(dealId, invoiceFile, InvoiceType.KONCOWA, it.dokumentNr)
            }
        }

        return ResponseEntity.ok("OK")
    }
}