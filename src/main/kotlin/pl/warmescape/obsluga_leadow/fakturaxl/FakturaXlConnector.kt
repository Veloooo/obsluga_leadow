package pl.warmescape.obsluga_leadow.fakturaxl

import mu.two.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.xml.sax.InputSource
import pl.warmescape.obsluga_leadow.bitrix.model.ClientType
import pl.warmescape.obsluga_leadow.bitrix.model.Deal
import pl.warmescape.obsluga_leadow.bitrix.model.InvoiceType
import pl.warmescape.obsluga_leadow.bitrix.model.Product
import pl.warmescape.obsluga_leadow.bitrix.model.Seller
import pl.warmescape.obsluga_leadow.fakturaxl.model.Dokument
import pl.warmescape.obsluga_leadow.fakturaxl.model.FakturaPozycja
import pl.warmescape.obsluga_leadow.fakturaxl.model.FakturaXlCreateInvoiceResponse
import pl.warmescape.obsluga_leadow.fakturaxl.model.Nabywca
import java.io.StringReader
import java.math.BigDecimal
import java.time.LocalDate
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.text.appendLine

@Service
class FakturaXlConnector {
    @Value("\${fakturaxl.tokenStormedge}")
    lateinit var fakturaXlTokenStormedge: String

    @Value("\${fakturaxl.tokenKaras}")
    lateinit var fakturaXlTokenKaras: String
    private val logger = KotlinLogging.logger {}

    fun generateInvoice(deal: Deal, invoiceType: InvoiceType): FakturaXlCreateInvoiceResponse? {
        val positions = getPositions(deal, invoiceType)
        val dokument = prepareDokument(positions, invoiceType, deal)
        logger.info { "Faktura XL create invoice request: ${dokument.copy(apiToken = "")}" }
        return try {
            val createInvoiceResponse = WebClient.builder().build().post()
                .uri("https://program.fakturaxl.pl/api/dokument_dodaj.php")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(dokument.toXml())
                .retrieve()
                .bodyToMono(String::class.java)
                .block()!!
            logger.info { "Faktura XL create invoice response: $createInvoiceResponse" }
            return getFakturaXlCreateInvoiceResponse(createInvoiceResponse)
        } catch (ex: Exception) {
            println("Błąd generowania faktury proforma: ${ex.message}")
            null
        }
    }

    fun getPositions(
        deal: Deal,
        invoiceType: InvoiceType
    ): List<FakturaPozycja> {

        val brutto = deal.bruttoPrice
        val zaliczka = deal.firstPayment ?: BigDecimal.ZERO

        return when (invoiceType) {

            InvoiceType.PROFORMA_ZALICZKA,
            InvoiceType.ZALICZKA -> when (deal.product) {

                Product.BALIA ->
                    listOf(prepareFakturaPozycja("Balia ogrodowa", zaliczka))

                Product.SAUNA ->
                    listOf(prepareFakturaPozycja("Sauna ogrodowa", zaliczka))

                Product.BALIA_I_SAUNA -> {
                    val halfZaliczka = zaliczka.divide(BigDecimal(2))
                    listOf(
                        prepareFakturaPozycja("Sauna ogrodowa - zaliczka", halfZaliczka),
                        prepareFakturaPozycja("Balia ogrodowa - zaliczka", halfZaliczka)
                    )
                }
            }

            InvoiceType.FV -> when (deal.product) {

                Product.BALIA ->
                    listOf(prepareFakturaPozycja("Balia ogrodowa", brutto))

                Product.SAUNA ->
                    listOf(prepareFakturaPozycja("Sauna ogrodowa", brutto))

                Product.BALIA_I_SAUNA -> {
                    val halfBrutto = brutto.divide(BigDecimal(2))
                    listOf(
                        prepareFakturaPozycja("Sauna ogrodowa", halfBrutto),
                        prepareFakturaPozycja("Balia ogrodowa", halfBrutto)
                    )
                }
            }

            InvoiceType.KONCOWA -> when (deal.product) {

                Product.BALIA ->
                    listOf(
                        prepareFakturaPozycja("Balia ogrodowa - zaliczka", zaliczka),
                        prepareFakturaPozycja(
                            "Balia ogrodowa",
                            brutto.minus(zaliczka)
                        )
                    )

                Product.SAUNA ->
                    listOf(
                        prepareFakturaPozycja("Sauna ogrodowa - zaliczka", zaliczka),
                        prepareFakturaPozycja(
                            "Sauna ogrodowa",
                            brutto.minus(zaliczka)
                        )
                    )

                Product.BALIA_I_SAUNA -> {
                    val halfBrutto = brutto.divide(BigDecimal(2))
                    val halfZaliczka = zaliczka.divide(BigDecimal(2))
                    val productValue = halfBrutto.minus(halfZaliczka)

                    listOf(
                        prepareFakturaPozycja("Sauna ogrodowa - zaliczka", halfZaliczka),
                        prepareFakturaPozycja("Balia ogrodowa - zaliczka", halfZaliczka),
                        prepareFakturaPozycja("Sauna ogrodowa", productValue),
                        prepareFakturaPozycja("Balia ogrodowa", productValue)
                    )
                }
            }
        }
    }


    fun getInvoiceContent(code: String): ByteArray {
        return WebClient
            .builder()
            .build()
            .get()
            .uri("https://program.fakturaxl.pl/api/pdf.php?k=$code&pdf=1")
            .accept(MediaType.APPLICATION_PDF)
            .retrieve()
            .bodyToMono(ByteArray::class.java)
            .block()!!
    }

    fun getFakturaXlCreateInvoiceResponse(xmlResponse: String): FakturaXlCreateInvoiceResponse {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(InputSource(StringReader(xmlResponse)))

        val unikatowyKod = document.getElementsByTagName("unikatowy_kod")
        val kod = document.getElementsByTagName("kod")
        val dokumentId = document.getElementsByTagName("dokument_id")
        val dokumentNr = document.getElementsByTagName("dokument_nr")

        return FakturaXlCreateInvoiceResponse(
            kod = kod.item(0).textContent!!,
            unikatowyKod = unikatowyKod.item(0).textContent!!,
            dokumentId = dokumentId.item(0).textContent!!,
            dokumentNr = dokumentNr.item(0).textContent!!.trim().replace("/", "_")
        )
    }


    fun prepareDokument(positions: List<FakturaPozycja>, invoiceType: InvoiceType, deal: Deal): Dokument {
        val token = when (deal.seller) {
            Seller.STORMEDGE -> fakturaXlTokenStormedge
            Seller.KARAS -> fakturaXlTokenKaras
        }
        return Dokument(
            apiToken = token,
            typFaktury = invoiceType.fakturaXlType,
            typFakturPodtyp = 0,
            obliczajSumeWartosciFakturyWg = 0,
            dataWystawienia = LocalDate.now().toString(),
            dataSprzedazy = deal.dealDate.toString(),
            terminPlatnosciData = LocalDate.now().plusDays(14).toString(),
            kwotaOplacona = 0.0,
            waluta = "PLN",
            kurs = 1.0,
            rodzajPlatnosci = "Przelew",
            jezyk = 0,
            szablon = 0,
            imieNazwiskoWystawcy = "",
            imieNazwiskoOdbiorcy = when (deal.clientType) {
                ClientType.FIRMA -> null
                ClientType.OSOBA_PRYWATNA -> deal.fullName
            },
            idDzialyFirmy = when (deal.seller) {
                Seller.STORMEDGE -> 195121
                Seller.KARAS -> 222993
            },
            wyslijDokumentDoKlientaEmailem = 0,
            obliczajWartoscFakturyOd = 0,
            pozycje = positions,
            nabywca = prepareNabywca(deal),
            fakturaZaliczkowaId = deal.fakturaZaliczkowaId,
        )
    }

    fun prepareNabywca(deal: Deal): Nabywca {

        val (imie, nazwisko) = when (deal.clientType) {
            ClientType.FIRMA -> null to null
            ClientType.OSOBA_PRYWATNA -> {
                val parts = deal.fullName.trim().split("\\s+".toRegex(), limit = 2)
                parts.getOrNull(0) to parts.getOrNull(1)
            }
        }

        val (kodPocztowy, miejscowosc) = parsePostalCodeAndCity(deal.postalCodeWithCity)

        return Nabywca(
            firmaLubOsobaPrywatna = when (deal.clientType) {
                ClientType.FIRMA -> 0
                ClientType.OSOBA_PRYWATNA -> 1
            },
            nazwa = if (deal.clientType == ClientType.FIRMA) deal.fullName else "$imie $nazwisko",
            imie = imie,
            nazwisko = nazwisko,
            dodatkowaInformacja = prepareName(deal),
            nip = if (deal.clientType == ClientType.FIRMA) deal.identifier else null,
            ulicaINumer = deal.streetWithNumber,
            kodPocztowy = kodPocztowy,
            miejscowosc = miejscowosc
        )
    }


    private fun parsePostalCodeAndCity(value: String): Pair<String?, String?> {
        val trimmed = value.trim()
        val firstSpaceIndex = trimmed.indexOf(' ')

        return if (firstSpaceIndex > 0) {
            val code = trimmed.substring(0, firstSpaceIndex)
            val city = trimmed.substring(firstSpaceIndex + 1).trim()
            code to city
        } else {
            null to trimmed.ifBlank { null }
        }
    }


    private fun prepareName(deal: Deal): String {
        return buildString {
            if (deal.clientType == ClientType.FIRMA) {
                appendLine(deal.fullName)
            }
            appendLine(deal.streetWithNumber)
            appendLine(deal.postalCodeWithCity)
            deal.identifier?.let {
                when (deal.clientType) {
                    ClientType.FIRMA -> appendLine("NIP: $it")
                    ClientType.OSOBA_PRYWATNA -> appendLine("PESEL: $it")
                }
            }
        }
    }

    fun prepareFakturaPozycja(name: String, wartoscBrutto: BigDecimal) = FakturaPozycja(
        nazwa = name,
        kodProduktu = null,
        produktId = null,
        pkwiu = null,
        symbolGtu = null,
        ilosc = 1,
        jm = "szt.",
        vat = 23,
        wartoscBrutto = wartoscBrutto,
    )

    fun Dokument.toXml(): String =
        buildString {
            appendLine("<dokument>")
            appendLine("<api_token>$apiToken</api_token>")
            appendLine("<typ_faktury>$typFaktury</typ_faktury>")
            appendLine("<typ_faktur_podtyp>$typFakturPodtyp</typ_faktur_podtyp>")
            appendLine("<obliczaj_sume_wartosci_faktury_wg>$obliczajSumeWartosciFakturyWg</obliczaj_sume_wartosci_faktury_wg>")
            appendLine("<numer_faktury>${numerFaktury ?: ""}</numer_faktury>")
            appendLine("<data_wystawienia>$dataWystawienia</data_wystawienia>")
            appendLine("<data_sprzedazy>$dataSprzedazy</data_sprzedazy>")
            appendLine("<termin_platnosci_data>$terminPlatnosciData</termin_platnosci_data>")
            appendLine("<data_oplacenia>${dataOplacenia ?: ""}</data_oplacenia>")
            appendLine("<kwota_oplacona>$kwotaOplacona</kwota_oplacona>")
            appendLine("<uwagi>${uwagi ?: ""}</uwagi>")
            appendLine("<waluta>$waluta</waluta>")
            appendLine("<kurs>$kurs</kurs>")
            appendLine("<rodzaj_platnosci>$rodzajPlatnosci</rodzaj_platnosci>")
            appendLine("<jezyk>$jezyk</jezyk>")
            appendLine("<szablon>$szablon</szablon>")
            appendLine("<imie_nazwisko_wystawcy>$imieNazwiskoWystawcy</imie_nazwisko_wystawcy>")
            appendLine("<imie_nazwisko_odbiorcy>${imieNazwiskoOdbiorcy ?: ""}</imie_nazwisko_odbiorcy>")
            appendLine("<nr_zamowienia>${nrZamowienia ?: ""}</nr_zamowienia>")
            appendLine("<dodatkowe_uwagi>${dodatkoweUwagi ?: ""}</dodatkowe_uwagi>")
            appendLine("<id_dzialy_firmy>$idDzialyFirmy</id_dzialy_firmy>")
            appendLine("<wyslij_dokument_do_klienta_emailem>$wyslijDokumentDoKlientaEmailem</wyslij_dokument_do_klienta_emailem>")
            appendLine("<obliczaj_wartosc_faktury_od>$obliczajWartoscFakturyOd</obliczaj_wartosc_faktury_od>")
            appendLine("<notatka_prywatna>${notatkaPrywatna ?: ""}</notatka_prywatna>")
            appendLine("<dokument_rel_id>${fakturaZaliczkowaId ?: ""}</dokument_rel_id>")

            appendLine("<nabywca>")
            appendLine("<firma_lub_osoba_prywatna>${nabywca.firmaLubOsobaPrywatna}</firma_lub_osoba_prywatna>")
            appendLine("<nazwa>${nabywca.nazwa}</nazwa>")
            appendLine("<imie>${nabywca.imie ?: ""}</imie>")
            appendLine("<nazwisko>${nabywca.nazwisko ?: ""}</nazwisko>")
            appendLine("<nip>${nabywca.nip ?: ""}</nip>")
            appendLine("<ulica_i_numer>${nabywca.ulicaINumer ?: ""}</ulica_i_numer>")
            appendLine("<kod_pocztowy>${nabywca.kodPocztowy ?: ""}</kod_pocztowy>")
            appendLine("<miejscowosc>${nabywca.miejscowosc ?: ""}</miejscowosc>")
            appendLine("<kraj>${nabywca.kraj}</kraj>")
            appendLine("<email>${nabywca.email ?: ""}</email>")
            appendLine("<telefon>${nabywca.telefon ?: ""}</telefon>")
            appendLine("</nabywca>")

            pozycje.forEach {
                appendLine("<faktura_pozycje>")
                appendLine("<nazwa>${it.nazwa}</nazwa>")
                appendLine("<kod_produktu>${it.kodProduktu ?: ""}</kod_produktu>")
                appendLine("<produkt_id>${it.produktId ?: ""}</produkt_id>")
                appendLine("<pkwiu>${it.pkwiu ?: ""}</pkwiu>")
                appendLine("<symbol_gtu>${it.symbolGtu ?: ""}</symbol_gtu>")
                appendLine("<ilosc>${it.ilosc}</ilosc>")
                appendLine("<jm>${it.jm}</jm>")
                appendLine("<vat>${it.vat}</vat>")
                appendLine("<wartosc_brutto>${it.wartoscBrutto}</wartosc_brutto>")
                appendLine("</faktura_pozycje>")
            }

            tagi.forEach { appendLine("<tag>$it</tag>") }
            jpkV7.forEach { appendLine("<JPK_V7>$it</JPK_V7>") }

            appendLine("</dokument>")
        }
}