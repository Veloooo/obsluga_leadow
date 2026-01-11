package pl.warmescape.obsluga_leadow.bitrix.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import pl.warmescape.obsluga_leadow.bitrix.model.BaliaTypeData
import pl.warmescape.obsluga_leadow.bitrix.model.Deal
import pl.warmescape.obsluga_leadow.bitrix.model.DealDTO
import pl.warmescape.obsluga_leadow.bitrix.model.InvoiceType
import java.util.Base64

@Service
class BitrixDealService(
    @Value("\${bitrix.url}") private val bitrixUrl: String,
    private val objectMapper: ObjectMapper,
) {
    private val client = WebClient.builder()
        .baseUrl(bitrixUrl)
        .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        .build()


    fun getDeal(dealId: Long): Deal {
        val responseString = callMethod("crm.deal.get", mapOf("ID" to dealId))
        return mapJsonToDeal(responseString).toDomain()
    }

    fun callMethod(method: String, payload: Map<String, Any>): String {
        return client.post()
            .uri(method)
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(String::class.java)
            .block()!!
    }


    /**
     * Pomocnicza metoda do aktualizacji pola deala
     */
    fun updateDealFields(dealId: Long, fields: Map<String, Any>): String? {
        val body = mapOf(
            "id" to dealId,
            "fields" to fields
        )
        return callMethod("crm.deal.update", body)
    }

    /**
     * Przykład dodania leada
     */
    fun addLead(fields: Map<String, Any>): String? {
        return callMethod("crm.lead.add", mapOf("fields" to fields))
    }


    fun uploadFakturaBase64(dealId: Long, file: ByteArray, invoiceType: InvoiceType): Boolean {
        val base64File = Base64.getEncoder().encodeToString(file)

        // Payload z użyciem fileData
        val payload = mapOf(
            "id" to dealId,
            "fields" to mapOf(
                invoiceType.bitrixField to mapOf(
                    "fileData" to listOf("${invoiceType.fileName}.pdf", base64File)
                )
            )
        )

        return try {
            val response = client.post()
                .uri("crm.deal.update") // konieczne .json
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String::class.java)
                .block()

            println("Update file response: $response")
            true
        } catch (ex: Exception) {
            println("Błąd update pliku w deal: ${ex.message}")
            false
        }
    }

    fun mapJsonToDeal(json: String): DealDTO {
        val rootNode = objectMapper.readTree(json)
        val resultNode = rootNode["result"]
            ?: throw IllegalStateException("Brak pola 'result' w odpowiedzi Bitrix")

        return objectMapper.treeToValue(resultNode, DealDTO::class.java)
    }
}