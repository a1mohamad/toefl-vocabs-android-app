package io.github.a1mohamad.toeflvocab.core.content

/**
 * Finds the `'quoted'` words inside a usage tip.
 *
 * The source data uses single quotes to mark the part of a tip that actually has
 * to be remembered: in `"followed by 'of'"` the whole value of the note is the
 * word `of`, and in `"cast is 'singular', in some other varieties it is
 * 'plural'"` there are two such words. Highlighting them is the difference
 * between a tip being read and a tip being skipped.
 *
 * Kept here, away from Compose, for two reasons: the rule below is the kind of
 * thing that needs unit tests, and it has to match the iOS original character
 * for character or the two apps would highlight different words.
 *
 * The rule, in one line: a `'` opens a highlight only at a word boundary.
 * Without that, the apostrophe in `"someone's 'ability' to"` would open a quote,
 * swallow `s ` and colour the wrong run. Contractions in the data use a
 * typographic apostrophe (`’`) and never match at all, but plain `'` shows up
 * too, so the boundary test — not the character — is what keeps this honest.
 */
object TipMarkup {

    /** One run of a tip: either prose, or a quoted word with its position. */
    data class Segment(
        val text: String,
        /**
         * `null` for prose. Otherwise the index of this quoted word within the
         * tip, which the design system turns into one of its highlight colours
         * — first quote, first colour.
         */
        val highlightIndex: Int? = null,
    ) {
        val isHighlighted: Boolean get() = highlightIndex != null
    }

    /**
     * Splits [tip] into prose and quoted runs, in order, with the quotes
     * themselves dropped. Joining every [Segment.text] back together returns the
     * tip without its quote marks.
     */
    fun segments(tip: String): List<Segment> {
        val segments = mutableListOf<Segment>()
        val prose = StringBuilder()
        var highlightCount = 0
        var index = 0

        fun flushProse() {
            if (prose.isEmpty()) return
            segments.add(Segment(prose.toString()))
            prose.setLength(0)
        }

        while (index < tip.length) {
            val close = if (tip[index] == QUOTE && opensHighlight(index, tip)) {
                closingQuote(index, tip)
            } else {
                null
            }

            if (close == null) {
                prose.append(tip[index])
                index += 1
                continue
            }

            flushProse()
            segments.add(Segment(tip.substring(index + 1, close), highlightCount))
            highlightCount += 1
            index = close + 1
        }

        flushProse()
        return segments
    }

    /**
     * The tip with its quote marks removed and nothing else changed. This is
     * what TalkBack reads, since a spoken apostrophe carries no meaning.
     */
    fun plainText(tip: String): String = segments(tip).joinToString("") { it.text }

    // MARK: - Private

    private const val QUOTE = '\''

    private fun isWordCharacter(character: Char): Boolean = character.isLetterOrDigit()

    private fun opensHighlight(index: Int, tip: String): Boolean =
        index == 0 || !isWordCharacter(tip[index - 1])

    /**
     * The matching close quote, or null — in which case the `'` is prose and the
     * tip renders unchanged rather than losing its tail to a half-open quote.
     */
    private fun closingQuote(openIndex: Int, tip: String): Int? {
        var index = openIndex + 1
        var sawContent = false

        while (index < tip.length) {
            val character = tip[index]
            if (character == QUOTE) {
                val insideWord = index + 1 < tip.length && isWordCharacter(tip[index + 1])
                if (sawContent && !insideWord) return index
            } else if (!character.isWhitespace()) {
                sawContent = true
            }
            index += 1
        }

        return null
    }
}
