package pl.warmescape.obsluga_leadow.fakturaxl.model

data class FakturaXlCreateInvoiceResponse(
    val kod: String,
    val dokumentId: String,
    val dokumentNr: String,
    val unikatowyKod: String,
)