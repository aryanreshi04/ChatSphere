package com.jos.myapplication.ml

import kotlin.math.exp
import kotlin.math.ln

data class CategoryPrediction(val category:String,val score:Double,val matchedWords:Int)

/** Small multinomial Naive Bayes demo, fitted from authored examples. Not a safety/urgency detector. */
class MessageCategories(samples: List<Pair<String, String>> = examples) {
    private val groups = samples.groupBy({ it.first }, { tokens(it.second) })
    private val vocabulary = groups.values.flatten().flatten().toSet()
    private val counts = groups.mapValues { (_, texts) -> texts.flatten().groupingBy { it }.eachCount() }
    private val totals = counts.mapValues { it.value.values.sum() }
    fun classify(text:String):String? = predict(text)?.category
    fun predict(text: String): CategoryPrediction? {
        val input = tokens(text).take(200).filter { it in vocabulary }
        if (input.isEmpty()) return null
        val scores = groups.mapValues { (category, documents) ->
            ln(documents.size.toDouble() / groups.values.sumOf { it.size }) + input.sumOf { word ->
                ln(((counts[category]?.get(word) ?: 0) + 1.0) / (totals.getValue(category) + vocabulary.size))
            }
        }
        val best = scores.maxByOrNull { it.value } ?: return null
        val probability = 1.0 / scores.values.sumOf { exp(it - best.value) }
        return CategoryPrediction(best.key,probability,input.size).takeIf { probability >= 0.65 }
    }
    companion object {
        private fun tokens(text: String) = Regex("[a-z]{2,}").findAll(text.lowercase(java.util.Locale.ROOT)).map { it.value }.toList()
        // Synthetic classroom examples, not collected private messages or a benchmark dataset.
        val examples = listOf(
            "Study" to "assignment homework deadline submit class course",
            "Study" to "exam revision study notes lecture tomorrow",
            "Study" to "library research project report laboratory",
            "Study" to "professor class lecture assignment submission",
            "Study" to "exam syllabus revision semester course",
            "Study" to "study homework notes project deadline",
            "Meetup" to "meet lunch canteen cafe coffee",
            "Meetup" to "meet tomorrow gate outside walk",
            "Meetup" to "lunch dinner cafe evening friends",
            "Meetup" to "meeting campus gate coffee canteen",
            "Meetup" to "join lunch outside meet later",
            "Meetup" to "walk cafe dinner meeting evening",
            "Conversation" to "hello hi thanks welcome good morning",
            "Conversation" to "okay great fine thanks you",
            "Conversation" to "happy birthday congratulations nice wonderful",
            "Conversation" to "hello good evening how are you",
            "Conversation" to "yes okay welcome thank you",
            "Conversation" to "great fine nice good bye"
        )
    }
}
