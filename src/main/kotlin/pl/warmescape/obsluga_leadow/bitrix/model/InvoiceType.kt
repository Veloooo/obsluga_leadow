package pl.warmescape.obsluga_leadow.bitrix.model

enum class InvoiceType(val bitrixField: String, val fileName: String, val fakturaXlType: Int) {
    PROFORMA_ZALICZKA("UF_CRM_1767562384826", "faktura-proforma", 1),
    ZALICZKA("UF_CRM_1768067750641", "faktura-vat-zaliczka", 11),
    KONCOWA("UF_CRM_1768073997766", "koncowa", 3),
    FV("UF_CRM_1768073997766", "faktura", 0),
}