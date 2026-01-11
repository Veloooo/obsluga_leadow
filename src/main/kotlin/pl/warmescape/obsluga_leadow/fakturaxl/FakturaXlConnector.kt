package pl.warmescape.obsluga_leadow.fakturaxl

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.xml.sax.InputSource
import pl.warmescape.obsluga_leadow.bitrix.model.ClientType
import pl.warmescape.obsluga_leadow.bitrix.model.Deal
import pl.warmescape.obsluga_leadow.bitrix.model.InvoiceType
import pl.warmescape.obsluga_leadow.bitrix.model.Product
import pl.warmescape.obsluga_leadow.fakturaxl.model.Dokument
import pl.warmescape.obsluga_leadow.fakturaxl.model.FakturaPozycja
import pl.warmescape.obsluga_leadow.fakturaxl.model.Nabywca
import java.io.StringReader
import java.math.BigDecimal
import java.time.LocalDate
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.text.appendLine

@Service
class FakturaXlConnector {
    @Value("\${fakturaxl.token}")
    lateinit var fakturaXlToken: String

    fun generateInvoice(deal: Deal, invoiceType: InvoiceType): String? {
        val positions = getPositions(deal, invoiceType)
        val dokument = prepareDokument(positions, invoiceType, deal)

        return try {
            val createInvoiceResponse = WebClient.builder().build().post()
                .uri("https://program.fakturaxl.pl/api/dokument_dodaj.php")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(dokument.toXml())
                .retrieve()
                .bodyToMono(String::class.java)
                .block()!!
            println(createInvoiceResponse)
            return extractUnikatowyKod(createInvoiceResponse)
        } catch (ex: Exception) {
            println("Błąd generowania faktury proforma: ${ex.message}")
            null
        }
    }

    fun getPositions(deal: Deal, invoiceType: InvoiceType): List<FakturaPozycja> {
        val productName = when(deal.product) {
            Product.BALIA -> "Balia ogrodowa"
            Product.SAUNA -> "Sauna ogrodowa"
        }

        val invoiceBruttoPrice = when(invoiceType) {
            InvoiceType.PROFORMA_ZALICZKA, InvoiceType.ZALICZKA -> deal.firstPayment!!
            InvoiceType.KONCOWA -> deal.bruttoPrice.minus(deal.firstPayment ?: BigDecimal.ZERO)
            InvoiceType.FV -> deal.bruttoPrice
        }

        return buildList {
            when (invoiceType) {
                InvoiceType.KONCOWA -> {
                    add(prepareFakturaPozycja("$productName - zaliczka", deal.firstPayment!!))
                    add(prepareFakturaPozycja(productName, invoiceBruttoPrice - deal.firstPayment))
                }

                InvoiceType.PROFORMA_ZALICZKA, InvoiceType.ZALICZKA -> {
                    add(prepareFakturaPozycja("$productName - zaliczka", deal.firstPayment!!))
                }

                InvoiceType.FV -> add(prepareFakturaPozycja(productName, invoiceBruttoPrice))
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

    fun extractUnikatowyKod(xmlResponse: String): String? {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(InputSource(StringReader(xmlResponse)))

        val nodes = document.getElementsByTagName("unikatowy_kod")
        if (nodes.length == 0) return null

        return nodes.item(0).textContent!!
    }


    fun prepareDokument(positions: List<FakturaPozycja>, invoiceType: InvoiceType, deal: Deal) = Dokument(
        apiToken = fakturaXlToken,
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
        imieNazwiskoOdbiorcy = when(deal.clientType) {
            ClientType.FIRMA -> null
            ClientType.OSOBA_PRYWATNA -> deal.fullName
        },
        idDzialyFirmy = 195121,
        wyslijDokumentDoKlientaEmailem = 0,
        obliczajWartoscFakturyOd = 0,
        pozycje = positions,
        nabywca = Nabywca(
            firmaLubOsobaPrywatna = when(deal.clientType) {
                ClientType.FIRMA -> 0
                ClientType.OSOBA_PRYWATNA -> 1
            },
            nazwa = prepareName(deal),
        )
    )

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

    private fun prepareName(deal: Deal): String {
        return buildString {
            appendLine(deal.fullName)
            appendLine(deal.streetWithNumber)
            appendLine(deal.postalCodeWithCity)
            deal.identifier?.let {
                when(deal.clientType) {
                    ClientType.FIRMA -> appendLine("NIP: $it")
                    ClientType.OSOBA_PRYWATNA -> appendLine("PESEL: $it")
                }
            }
        }
    }
}