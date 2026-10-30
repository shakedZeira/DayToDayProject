package com.daytoday.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItalianGradingTest {

    @Test
    fun `exact phrase matches ignoring case and punctuation`() {
        assertTrue(ItalianGrading.speakingMatches("Quanto costa?", "quanto costa"))
    }

    @Test
    fun `trailing ellipsis in the target is ignored`() {
        assertTrue(ItalianGrading.speakingMatches("Mi chiamo…", "mi chiamo"))
    }

    @Test
    fun `accents are ignored`() {
        assertTrue(ItalianGrading.speakingMatches("Dov'è", "dove"))
    }

    @Test
    fun `one slip in a longer phrase is forgiven`() {
        assertTrue(ItalianGrading.speakingMatches("Piacere di conoscerti", "piacere di conoscerti"))
        assertTrue(ItalianGrading.speakingMatches("Piacere di conoscerti", "piacere di conoscerli"))
    }

    @Test
    fun `a different phrase does not match`() {
        assertFalse(ItalianGrading.speakingMatches("Quanto costa?", "che ore sono"))
    }

    @Test
    fun `silence never matches`() {
        assertFalse(ItalianGrading.speakingMatches("Ciao", ""))
    }

    @Test
    fun `speaking exercise is graded from what was heard`() {
        val ex = ItalianExerciseDto.Speak(id = "u1l1-speak-0", prompt = "hello", target = "Ciao")

        assertTrue(ItalianGrading.grade(ex, ItalianAnswerDto.Speak(ex.id, "ciao")))
        assertFalse(ItalianGrading.grade(ex, ItalianAnswerDto.Speak(ex.id, "")))
        assertFalse(ItalianGrading.grade(ex, ItalianAnswerDto.Choice(ex.id, 0)))
    }
}
