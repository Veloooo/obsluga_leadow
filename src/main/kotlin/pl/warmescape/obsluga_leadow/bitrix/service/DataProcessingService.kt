package pl.warmescape.obsluga_leadow.bitrix.service

import org.springframework.stereotype.Service

@Service
class DataProcessingService {

    fun numberToWordsPl(number: Int): String {
        if (number == 0) return "zero"

        val units = arrayOf(
            "", "jeden", "dwa", "trzy", "cztery",
            "pięć", "sześć", "siedem", "osiem", "dziewięć"
        )
        val teens = arrayOf(
            "dziesięć", "jedenaście", "dwanaście", "trzynaście", "czternaście",
            "piętnaście", "szesnaście", "siedemnaście", "osiemnaście", "dziewiętnaście"
        )
        val tens = arrayOf(
            "", "", "dwadzieścia", "trzydzieści", "czterdzieści",
            "pięćdziesiąt", "sześćdziesiąt", "siedemdziesiąt",
            "osiemdziesiąt", "dziewięćdziesiąt"
        )
        val hundreds = arrayOf(
            "", "sto", "dwieście", "trzysta", "czterysta",
            "pięćset", "sześćset", "siedemset", "osiemset", "dziewięćset"
        )

        fun belowThousand(n: Int): String {
            val h = n / 100
            val t = (n % 100) / 10
            val u = n % 10

            return buildString {
                if (h > 0) append(hundreds[h]).append(" ")
                if (t == 1) {
                    append(teens[u])
                } else {
                    if (t > 1) append(tens[t]).append(" ")
                    if (u > 0) append(units[u])
                }
            }.trim()
        }

        val thousands = number / 1000
        val rest = number % 1000

        return buildString {
            if (thousands > 0) {
                when (thousands) {
                    1 -> append("jeden tysiąc")
                    in 2..4 -> append("${belowThousand(thousands)} tysiące")
                    else -> append("${belowThousand(thousands)} tysięcy")
                }
            }
            if (rest > 0) {
                if (isNotEmpty()) append(" ")
                append(belowThousand(rest))
            }

            append(" złotych")
        }
    }
}