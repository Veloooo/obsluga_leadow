package pl.warmescape.obsluga_leadow.bitrix.util

import jakarta.servlet.http.HttpServletRequest
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import java.net.URLDecoder

object BitrixWebhookUtils {

    /**
     * Pobiera DEAL_ID z webhooka Bitrix24 (form-urlencoded)
     * Zwraca null jeśli nie znaleziono.
     */
    fun extractDealId(request: HttpServletRequest): Long? {
        // 1. Pobierz całe body
        val body = request.reader.readText()

        // 2. Sparsuj parametry key=value do MultiValueMap
        val params: MultiValueMap<String, String> = LinkedMultiValueMap()
        body.split("&").forEach { pair ->
            val (key, value) = pair.split("=").let { it[0] to it.getOrElse(1) { "" } }
            params.add(URLDecoder.decode(key, "UTF-8"), URLDecoder.decode(value, "UTF-8"))
        }

        // 3. Filtruj klucze zaczynające się od document_id
        val documentIds = params.filterKeys { it.startsWith("document_id") }
            .toSortedMap(compareBy { it }) // sortujemy po kluczu
            .values
            .flatten()

        // 4. Znajdź pierwszy element zaczynający się od DEAL_
        val dealIdString = documentIds.firstOrNull { it.startsWith("DEAL_") }?.removePrefix("DEAL_")

        // 5. Zwróć jako Long lub null
        return dealIdString?.toLongOrNull()
    }
}