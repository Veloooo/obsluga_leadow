package pl.warmescape.obsluga_leadow.bitrix.model

data class BaliaTypeData(
    val shape: String,
    val dimension: String,
    val material: String,
)

object BaliaTypes {

    val BY_ID: Map<String, BaliaTypeData> = mapOf(
        "164" to BaliaTypeData(
            shape = "okrągła",
            dimension = "średnica 200 cm",
            material = "włókno szklane",
        ),
        "182" to BaliaTypeData(
            shape = "okrągła",
            dimension = "średnica 200 cm",
            material = "akryl",
        ),
        "166" to BaliaTypeData(
            shape = "okrągła",
            dimension = "średnica 225 cm",
            material = "włókno szklane",
        ),
        "168" to BaliaTypeData(
            shape = "kwadrat",
            dimension = "200 × 200 cm",
            material = "włókno szklane",
        ),
        "170" to BaliaTypeData(
            shape = "kwadrat",
            dimension = "210 × 210 cm",
            material = "włókno szklane",
        ),
    )
}
