package io.github.a1mohamad.toeflvocab.core.engine

import io.github.a1mohamad.toeflvocab.core.models.VocabID
import io.github.a1mohamad.toeflvocab.core.models.VocabItem
import io.github.a1mohamad.toeflvocab.core.models.WordStats
import kotlin.math.min

/**
 * Tunable knobs for the weakness score. Exposed as a data class so the behaviour
 * can be pinned down in tests instead of being hard-coded magic numbers.
 */
data class OrderingWeights(
    /** Multiplier on the smoothed error rate — the main signal. */
    val errorWeight: Double = 1.0,
    /**
     * Added when the most recent answer was wrong. Big enough to lift a word
     * above an unseen one immediately after a mistake.
     */
    val recentMistakeBonus: Double = 0.35,
    /** Subtracted per consecutive correct answer, so mastered words sink. */
    val masteryPenaltyPerStreak: Double = 0.06,
    val maximumStreakConsidered: Int = 5,
    /**
     * Subtracted per finished cycle in this run, so words that are genuinely
     * done stop crowding the front of the queue.
     */
    val completedCyclePenalty: Double = 0.04,
    val maximumCyclesConsidered: Int = 5,
) {
    companion object {
        val Default = OrderingWeights()
    }
}

/**
 * Decides what order words are presented in.
 *
 * The rule the app promises: **open a section and the words you get wrong most
 * come first**. A section you have never touched plays in book order, because
 * every word scores identically and the tie-break is the source position.
 *
 * The score is deliberately deterministic — no randomness, no shuffling. Two
 * runs with the same history produce the same queue, which is what makes the
 * behaviour explainable to the user and testable in CI.
 */
object AdaptiveOrdering {

    /**
     * Score for a word with no history. Laplace smoothing puts an unseen word at
     * exactly 0.5, which lands it *below* anything you have actually got wrong
     * and *above* anything you have been getting right.
     */
    const val UNSEEN_SCORE: Double = 0.5

    fun weakness(stats: WordStats?, weights: OrderingWeights = OrderingWeights.Default): Double {
        if (stats == null || stats.attempts == 0) return UNSEEN_SCORE

        // (incorrect + 0.5) / (attempts + 1) — smoothed so that a single wrong
        // answer does not read as a 100% error rate forever.
        val smoothedErrorRate = (stats.incorrect + 0.5) / (stats.attempts + 1.0)
        var score = weights.errorWeight * smoothedErrorRate

        if (stats.lastAnswerWasCorrect == false) {
            score += weights.recentMistakeBonus
        }

        val streak = min(stats.consecutiveCorrect, weights.maximumStreakConsidered)
        score -= streak * weights.masteryPenaltyPerStreak

        val cycles = min(stats.completedCyclesThisRun, weights.maximumCyclesConsidered)
        score -= cycles * weights.completedCyclePenalty

        return score
    }

    /**
     * Weakest first. Ties break on source order, then on id, giving a total
     * ordering — the comparator has to be complete or the queue could shuffle
     * between launches.
     */
    fun order(
        items: List<VocabItem>,
        weights: OrderingWeights = OrderingWeights.Default,
        stats: (VocabID) -> WordStats?,
    ): List<VocabItem> =
        items
            .map { it to weakness(stats(it.id), weights) }
            .sortedWith(
                compareByDescending<Pair<VocabItem, Double>> { it.second }
                    .thenBy { it.first.orderIndex }
                    .thenBy { it.first.id.rawValue }
            )
            .map { it.first }

    /**
     * Queue for the Reports drill, which spans the whole library.
     *
     * Ranked by **main-mode** weakness, because that is where the real learning
     * history lives — the drill is meant to target what the study path has shown
     * you are bad at. Among equally weak words, the ones drilled least come
     * first, so a long session keeps moving instead of looping over the same
     * handful.
     *
     * Running the full list weakest-to-strongest is also what gives the
     * behaviour asked for: wrong words first, then the rest, then a full pass is
     * complete and the queue starts over.
     */
    fun extraPracticeQueue(
        items: List<VocabItem>,
        limit: Int?,
        weights: OrderingWeights = OrderingWeights.Default,
        mainStats: (VocabID) -> WordStats?,
        extraStats: (VocabID) -> WordStats?,
    ): List<VocabItem> {
        data class Ranked(
            val item: VocabItem,
            val main: Double,
            val drills: Int,
            val drillScore: Double,
        )

        val ranked = items
            .map { item ->
                val extra = extraStats(item.id)
                Ranked(
                    item = item,
                    main = weakness(mainStats(item.id), weights),
                    drills = extra?.attempts ?: 0,
                    drillScore = weakness(extra, weights),
                )
            }
            .sortedWith(
                compareByDescending<Ranked> { it.main }
                    .thenBy { it.drills }
                    .thenByDescending { it.drillScore }
                    .thenBy { it.item.id.rawValue }
            )
            .map { it.item }

        if (limit == null || limit <= 0 || ranked.size <= limit) return ranked
        return ranked.take(limit)
    }
}
