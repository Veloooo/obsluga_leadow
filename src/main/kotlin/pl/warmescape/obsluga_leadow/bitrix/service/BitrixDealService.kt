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
import java.time.LocalDate
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
        val responseString = callMethod("/crm.deal.get", mapOf("ID" to dealId))
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
        return callMethod("/crm.deal.update", body)
    }


    fun uploadFakturaBase64(dealId: Long, file: ByteArray, invoiceType: InvoiceType, invoiceName: String): Boolean {
        val base64File = Base64.getEncoder().encodeToString(file)

        // Payload z użyciem fileData
        val payload = mapOf(
            "id" to dealId,
            "fields" to mapOf(
                invoiceType.bitrixField to mapOf(
                    "fileData" to listOf("$invoiceName.pdf", base64File)
                )
            )
        )

        return try {
            val response = client.post()
                .uri("/crm.deal.update") // konieczne .json
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

    fun createCalendarEvent(
        name: String,
        from: LocalDate,
        to: LocalDate,
        description: String,
        dealId: Long,
        attendees: List<Long> = emptyList(),
        color: String? = null,
    ): String {
        val sectionId = getCompanyCalendarSectionId()
        val payload = buildMap {
            put("type", "company_calendar")
            put("ownerId", 0)
            put("section", sectionId)
            put("name", name)
            put("description", description)
            put("from", from.toString())
            put("to", to.toString())
            put("skip_time", "Y")
            put("crm_fields", listOf("D_$dealId"))
            if (attendees.isNotEmpty()) {
                put("is_meeting", "Y")
                put("host", attendees.first())
                put("attendees", attendees)
            }
            color?.let { put("color", it) }
        }
        return callMethod("/calendar.event.add", payload)
    }

    fun getCurrentUserId(): Long {
        val response = callMethod("/user.current", emptyMap())
        val user = objectMapper.readTree(response)["result"]
            ?: throw IllegalStateException("Nie można pobrać aktualnego użytkownika")
        return user["ID"].asLong()
    }

    fun getContactFullName(contactId: Long): String {
        val response = callMethod("/crm.contact.get", mapOf("ID" to contactId))
        val contact = objectMapper.readTree(response)["result"]
            ?: throw IllegalStateException("Brak kontaktu o ID $contactId")
        val name = contact["NAME"]?.asText("") ?: ""
        val lastName = contact["LAST_NAME"]?.asText("") ?: ""
        return "$name $lastName".trim()
    }

    private fun getCompanyCalendarSectionId(): Int {
        val response = callMethod(
            "/calendar.section.get",
            mapOf("type" to "company_calendar", "ownerId" to 0)
        )
        val sections = objectMapper.readTree(response)["result"]
            ?: throw IllegalStateException("Brak sekcji w kalendarzu firmowym")
        return sections.first()["ID"].asInt()
    }

    fun mapJsonToDeal(json: String): DealDTO {
        val rootNode = objectMapper.readTree(json)
        val resultNode = rootNode["result"]
            ?: throw IllegalStateException("Brak pola 'result' w odpowiedzi Bitrix")

        return objectMapper.treeToValue(resultNode, DealDTO::class.java)
    }
}