package com.example.workoutapp.ui.workout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratorActionGuardTest {

    @Test
    fun allIdle_allowsDispatch() {
        assertTrue(canDispatchGeneratorAction(isGenerating = false, isPreviewing = false, isSavingPlan = false))
    }

    @Test
    fun generating_blocksDispatch() {
        assertFalse(canDispatchGeneratorAction(isGenerating = true, isPreviewing = false, isSavingPlan = false))
    }

    @Test
    fun previewing_blocksDispatch() {
        assertFalse(canDispatchGeneratorAction(isGenerating = false, isPreviewing = true, isSavingPlan = false))
    }

    @Test
    fun savingPlan_blocksDispatch() {
        assertFalse(canDispatchGeneratorAction(isGenerating = false, isPreviewing = false, isSavingPlan = true))
    }

    @Test
    fun multipleBusyFlags_stillBlocksDispatch() {
        assertFalse(canDispatchGeneratorAction(isGenerating = true, isPreviewing = true, isSavingPlan = true))
    }
}
