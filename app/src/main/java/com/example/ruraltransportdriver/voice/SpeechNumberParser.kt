package com.example.ruraltransportdriver.voice

import java.util.Locale

/**
 * Robust parser converting spoken speech transcripts into numeric seat counts.
 *
 * Supports:
 * - Direct digits: "0", "1", "2", "3", "4", "5", etc.
 * - Words: "zero", "one", "two", "three", "four", "five", "six", etc.
 * - Common homophones / speech recognition artifacts: "to", "too", "for", "ate", "won"
 * - Colloquial terms: "none", "no seats", "empty" -> 0, "full" -> 0 (no available seats)
 * - Compound phrases: "two seats available", "just 1", "we have three seats"
 * - Clamping results to 0..maxSeats
 */
object SpeechNumberParser {

    private val WORD_TO_NUMBER = mapOf(
        "zero" to 0,
        "none" to 0,
        "nil" to 0,
        "empty" to 0,
        "no" to 0,
        "full" to 0,
        "one" to 1,
        "won" to 1,
        "single" to 1,
        "two" to 2,
        "to" to 2,
        "too" to 2,
        "pair" to 2,
        "couple" to 2,
        "three" to 3,
        "tree" to 3,
        "four" to 4,
        "for" to 4,
        "fore" to 4,
        "five" to 5,
        "six" to 6,
        "seven" to 7,
        "eight" to 8,
        "ate" to 8,
        "nine" to 9,
        "ten" to 10,
        "eleven" to 11,
        "twelve" to 12
    )

    private val DIGIT_REGEX = Regex("""\b(\d+)\b""")

    /**
     * Parses a single spoken utterance or a list of recognizer hypotheses.
     *
     * @param candidates List of speech recognition hypothesis strings (in order of confidence)
     * @param maxSeats Maximum capacity of vehicle to clamp to
     * @return Parsed integer clamped within 0..maxSeats, or null if no valid number detected
     */
    fun parse(candidates: List<String>?, maxSeats: Int = Int.MAX_VALUE): Int? {
        if (candidates.isNullOrEmpty()) return null

        for (candidate in candidates) {
            val parsed = parseSingle(candidate, maxSeats)
            if (parsed != null) {
                return parsed
            }
        }
        return null
    }

    /**
     * Parses a single text string into a seat count.
     */
    fun parseSingle(text: String?, maxSeats: Int = Int.MAX_VALUE): Int? {
        if (text.isNullOrBlank()) return null

        val normalized = text.lowercase(Locale.ROOT).trim()

        // 1. Direct colloquial checks
        if (normalized.contains("no seats") || normalized.contains("no seat") ||
            normalized.contains("full") || normalized.contains("none") ||
            normalized == "zero" || normalized == "0"
        ) {
            return 0
        }

        // 2. Check for numeric digits in text (e.g. "2", "3 seats", "I have 4")
        val digitMatch = DIGIT_REGEX.find(normalized)
        if (digitMatch != null) {
            val number = digitMatch.groupValues[1].toIntOrNull()
            if (number != null) {
                return number.coerceIn(0, maxSeats)
            }
        }

        // 3. Word-by-word matching against dictionary
        // Split by whitespace and punctuation
        val tokens = normalized.split(Regex("""[\s,\.\-]+"""))
        for (token in tokens) {
            val matchedNumber = WORD_TO_NUMBER[token]
            if (matchedNumber != null) {
                return matchedNumber.coerceIn(0, maxSeats)
            }
        }

        return null
    }
}
