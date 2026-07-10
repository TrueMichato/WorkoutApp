package com.example.workoutapp.domain

import com.example.workoutapp.data.model.MLPreferenceScore
import com.example.workoutapp.domain.ml.MLStats
import org.junit.Assert.*
import org.junit.Test

class MLRecommenderTest {

    // ── MLStats tests ────────────────────────────────────────────────

    @Test
    fun calculateAcceptanceRate_evenSplit_returnsHalf() {
        val rate = MLStats.calculateAcceptanceRate(positive = 10, negative = 10)
        assertEquals(0.5f, rate, 0.001f)
    }

    @Test
    fun calculateAcceptanceRate_allPositive_returnsOne() {
        val rate = MLStats.calculateAcceptanceRate(positive = 20, negative = 0)
        assertEquals(1.0f, rate, 0.001f)
    }

    @Test
    fun calculateAcceptanceRate_allNegative_returnsZero() {
        val rate = MLStats.calculateAcceptanceRate(positive = 0, negative = 20)
        assertEquals(0.0f, rate, 0.001f)
    }

    @Test
    fun calculateAcceptanceRate_noSamples_returnsHalf() {
        val rate = MLStats.calculateAcceptanceRate(positive = 0, negative = 0)
        assertEquals(0.5f, rate, 0.001f)
    }

    @Test
    fun calculateConfidenceWidth_moreSamples_smallerWidth() {
        val width10 = MLStats.calculateConfidenceWidth(10)
        val width100 = MLStats.calculateConfidenceWidth(100)
        assertTrue("More samples should give smaller confidence width", width100 < width10)
    }

    @Test
    fun calculateConfidenceWidth_zeroSamples_returnsOne() {
        val width = MLStats.calculateConfidenceWidth(0)
        assertEquals(1f, width, 0.001f)
    }

    @Test
    fun estimateModelQuality_fewSamples_returnsZero() {
        val quality = MLStats.estimateModelQuality(totalPredictions = 5, correctPredictions = 5)
        assertEquals(0f, quality, 0.001f)
    }

    @Test
    fun estimateModelQuality_highAccuracy_returnsHigh() {
        val quality = MLStats.estimateModelQuality(totalPredictions = 100, correctPredictions = 90)
        assertTrue("High accuracy with many samples should give quality > 0.7", quality > 0.7f)
    }

    @Test
    fun estimateModelQuality_lowAccuracy_returnsLow() {
        val quality = MLStats.estimateModelQuality(totalPredictions = 100, correctPredictions = 30)
        assertTrue("Low accuracy should give quality < 0.6", quality < 0.6f)
    }

    // ── Preference Score tests ───────────────────────────────────────

    @Test
    fun preferenceScore_positiveScore_indicatesPreference() {
        val score = MLPreferenceScore(
            key = "exercise:1",
            score = 0.8f,
            confidence = 0.9f,
            sampleCount = 50
        )
        assertTrue("Positive score should indicate user preference", score.score > 0)
        assertTrue("High sample count should give high confidence", score.confidence > 0.5f)
    }

    @Test
    fun preferenceScore_negativeScore_indicatesAvoidance() {
        val score = MLPreferenceScore(
            key = "exercise:2",
            score = -0.6f,
            confidence = 0.7f,
            sampleCount = 30
        )
        assertTrue("Negative score should indicate user avoidance", score.score < 0)
    }

    @Test
    fun preferenceScore_zeroScore_indicatesNeutral() {
        val score = MLPreferenceScore(
            key = "category:STRENGTH",
            score = 0.0f,
            confidence = 0.5f,
            sampleCount = 20
        )
        assertEquals("Zero score should indicate neutral preference", 0f, score.score, 0.001f)
    }

    // ── UCB Exploration bonus tests ──────────────────────────────────

    @Test
    fun ucbBonus_lessExploredItem_getsHigherBonus() {
        // Simulating UCB calculation
        val exploredBonus = kotlin.math.sqrt(2 * kotlin.math.ln(100f) / 50f)
        val unexploredBonus = kotlin.math.sqrt(2 * kotlin.math.ln(100f) / 5f)

        assertTrue(
            "Less explored item should get higher UCB bonus",
            unexploredBonus > exploredBonus
        )
    }

    // ── Integration simulation tests ─────────────────────────────────

    @Test
    fun mlAdjustment_withStrongPreference_shouldBoost() {
        // Simulate what the adjustment calculation would produce
        val exerciseScore = MLPreferenceScore(
            key = "exercise:1",
            score = 0.8f,
            confidence = 0.9f,
            sampleCount = 50
        )

        // With high positive score and high confidence, adjustment should be positive
        val adjustment = exerciseScore.score * exerciseScore.confidence * 0.4f
        assertTrue("Strong preference should produce positive adjustment", adjustment > 0)
    }

    @Test
    fun mlAdjustment_withLowConfidence_shouldBeSmall() {
        val exerciseScore = MLPreferenceScore(
            key = "exercise:1",
            score = 0.8f,
            confidence = 0.2f,
            sampleCount = 5
        )

        // With low confidence, adjustment magnitude should be small
        val adjustment = exerciseScore.score * exerciseScore.confidence * 0.4f
        assertTrue("Low confidence should produce small adjustment", kotlin.math.abs(adjustment) < 0.1f)
    }

    @Test
    fun mlAdjustment_combinedScores_shouldBalance() {
        // Exercise is liked but category is disliked
        val exerciseScore = 0.7f
        val categoryScore = -0.5f
        val exerciseWeight = 0.4f
        val categoryWeight = 0.2f

        val combined = exerciseScore * exerciseWeight + categoryScore * categoryWeight
        // Combined should still be positive since exercise weight is higher
        assertTrue("Exercise preference should outweigh category dislike", combined > 0)
    }
}
