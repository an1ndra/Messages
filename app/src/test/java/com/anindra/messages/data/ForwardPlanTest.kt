package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardPlanTest {

    private fun msg(
        mediaType: String = "text",
        mediaUri: String = ""
    ) = Message(1L, 2L, "caption", 3L, false, "received", mediaType, mediaUri)

    @Test
    fun textMessageForwardsAsSms() {
        val plan = ForwardPlan.of(msg(), "hello")
        assertTrue(plan is ForwardPlan.Sms)
        assertEquals("hello", (plan as ForwardPlan.Sms).body)
    }

    @Test
    fun imageMessageForwardsAsMmsCarryingTheAttachment() {
        val plan = ForwardPlan.of(msg("image", "content://media/42"), "caption")
        assertTrue(plan is ForwardPlan.Mms)
        plan as ForwardPlan.Mms
        assertEquals("content://media/42", plan.mediaUri)
        assertEquals("caption", plan.caption)
    }

    @Test
    fun imageWithoutAUriFallsBackToSms() {
        val plan = ForwardPlan.of(msg("image", ""), "caption")
        assertTrue(plan is ForwardPlan.Sms)
    }

    @Test
    fun aVoiceNoteForwardsAsMmsCarryingTheRecording() {
        val plan = ForwardPlan.of(msg("audio", "content://voice/7"), "")
        assertTrue(plan is ForwardPlan.Mms)
        plan as ForwardPlan.Mms
        assertEquals("content://voice/7", plan.mediaUri)
        assertEquals("audio", plan.mediaType)
    }

    @Test
    fun nothingThisAppCannotSendForwardsAsAnMms() {
        val plan = ForwardPlan.of(msg("video", "content://video/7"), "hi")
        assertTrue(plan is ForwardPlan.Sms)
    }

    @Test
    fun anImageWithNoCaptionStillForwardsAsMms() {
        val plan = ForwardPlan.of(msg("image", "content://media/42"), "")
        assertTrue(plan is ForwardPlan.Mms)
        assertEquals("", (plan as ForwardPlan.Mms).caption)
    }
}
