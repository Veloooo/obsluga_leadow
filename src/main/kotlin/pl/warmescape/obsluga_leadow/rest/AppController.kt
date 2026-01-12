package pl.warmescape.obsluga_leadow.rest

import jakarta.servlet.http.HttpServletRequest
import mu.two.KotlinLogging
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import pl.warmescape.obsluga_leadow.bitrix.model.BaliaTypes
import pl.warmescape.obsluga_leadow.bitrix.model.ClientType
import pl.warmescape.obsluga_leadow.bitrix.model.InvoiceType
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
            val dealDataToUpdate = BaliaTypes.BY_ID[deal.baliaType]!!
            val amountPolish = dataProcessingService.numberToWordsPl(deal.bruttoPrice.toInt())
            val remainingPart = deal.firstPayment?.let { deal.bruttoPrice.minus(it) }
            val identifierOnDeal = deal.identifier?.let {
                when (deal.clientType) {
                    ClientType.FIRMA -> "NIP: $it"
                    ClientType.OSOBA_PRYWATNA -> "PESEL: $it"
                }
            }
            val fieldsMap = buildMap {
                putAll(
                    mapOf(
                        "UF_CRM_1768048546106" to dealDataToUpdate.shape,
                        "UF_CRM_1768048564350" to dealDataToUpdate.dimension,
                        "UF_CRM_1768048592302" to dealDataToUpdate.material,
                        "UF_CRM_1766327512939" to amountPolish,
                        "UF_CRM_1768060626878" to deal.baliaColor.label,
                    )
                )
                remainingPart?.let {
                    put("UF_CRM_1767452702411", it)
                }

                identifierOnDeal?.let {
                    put("UF_CRM_1768061569390", it)
                }
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
                val invoiceFile = fakturaXlConnector.getInvoiceContent(it)
                bitrixDealService.uploadFakturaBase64(dealId, invoiceFile, InvoiceType.PROFORMA_ZALICZKA)
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
                val invoiceFile = fakturaXlConnector.getInvoiceContent(it)
                bitrixDealService.uploadFakturaBase64(dealId, invoiceFile, InvoiceType.ZALICZKA)
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
                val invoiceFile = fakturaXlConnector.getInvoiceContent(it)
                bitrixDealService.uploadFakturaBase64(dealId, invoiceFile, InvoiceType.KONCOWA)
            }
        }

        return ResponseEntity.ok("OK")
    }
}