package com.anindra.messages.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.anindra.messages.R

object DemoData {
    private val DEMO_NUMBERS = setOf(
        "+15551230010", "+15551230020", "+15551230030",
        "+15551230040", "+15551230050", "+15551230060",
        "+15551230070", "+15551230080", "+15551230090",
        "+15551230100"
    )

    val AVATAR_MAP: Map<String, Int> = mapOf(
        "+15551230010" to R.drawable.avatar_sarah,
        "+15551230020" to R.drawable.avatar_mom,
        "+15551230030" to R.drawable.avatar_work,
        "+15551230040" to R.drawable.avatar_jake,
        "+15551230050" to R.drawable.avatar_emma,
        "+15551230060" to R.drawable.avatar_dad,
    )

    fun seedIfNeeded(db: SQLiteDatabase) {
        val cursor = db.rawQuery("SELECT COUNT(*) FROM conversations", null)
        val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        if (count > 0) return

        val now = System.currentTimeMillis()
        val hour = 3600_000L
        val day = 86400_000L

        data class SeedConvo(
            val address: String, val name: String, val snippet: String,
            val timestamp: Long, val unread: Int = 0, val lastIsMe: Boolean = false,
            val pinned: Boolean = false, val archived: Boolean = false,
            val draft: String = ""
        )

        val conversations = listOf(
            SeedConvo("+15551230010", "Sarah", "See you at 7! Don't forget wine \uD83C\uDF77", now - 2 * hour, unread = 2),
            SeedConvo("+15551230020", "Mom", "Call me when you get a chance sweetie", now - 5 * hour, unread = 1),
            SeedConvo("+15551230030", "Work", "Meeting rescheduled to 3pm tomorrow", now - 1 * day, lastIsMe = true),
            SeedConvo("+15551230040", "Jake", "haha that's hilarious \uD83D\uDE02", now - 1 * day - 3 * hour, lastIsMe = true),
            SeedConvo("+15551230050", "Emma", "Happy birthday! \uD83C\uDF82\uD83C\uDF89", now - 2 * day),
            SeedConvo("+15551230060", "Dad", "Fixed the lawnmower. Can you bring the tools back?", now - 3 * day, unread = 1),
            SeedConvo("+15551230070", "Pizza Palace", "Your order is on the way! \uD83C\uDF55", now - 4 * day),
            SeedConvo("+15551230080", "Alex", "Running 10 min late, start without me", now - 5 * day, lastIsMe = true),
            SeedConvo("+15551230090", "Dr. Patel", "Your appointment is confirmed for Friday at 2pm", now - 6 * day),
            SeedConvo("+15551230100", "Gym Buddy", "Leg day tomorrow \uD83D\uDCAA no excuses!", now - 7 * day, pinned = true),
        )

        conversations.forEach { c ->
            val cv = ContentValues().apply {
                put("address", c.address)
                put("name", c.name)
                put("snippet", c.snippet)
                put("timestamp", c.timestamp)
                put("unread_count", c.unread)
                put("last_is_me", if (c.lastIsMe) 1 else 0)
                put("pinned", if (c.pinned) 1 else 0)
                put("archived", if (c.archived) 1 else 0)
                put("draft", c.draft)
            }
            val convoId = db.insert("conversations", null, cv)

            val messages = seedMessagesFor(convoId, c.address, c.timestamp)
            messages.forEach { m ->
                db.execSQL(
                    "INSERT INTO messages(conversation_id,body,timestamp,is_me,status) VALUES(?,?,?,?,?)",
                    arrayOf(convoId, m.first, m.second, if (m.third) 1 else 0, "sent")
                )
            }
        }
    }

    private fun seedMessagesFor(convoId: Long, address: String, lastTs: Long): List<Triple<String, Long, Boolean>> {
        val hour = 3600_000L
        val day = 86400_000L
        return when (address) {
            "+15551230010" -> listOf(
                Triple("Hey! Are you free tonight?", lastTs - 4 * hour, false),
                Triple("Yeah, what do you have in mind?", lastTs - 3 * hour, true),
                Triple("Dinner at that new Italian place?", lastTs - 3 * hour + 120_000, false),
                Triple("Sounds perfect! 7pm?", lastTs - 2 * hour - 600_000, true),
                Triple("See you at 7! Don't forget wine \uD83C\uDF77", lastTs - 2 * hour, false),
            )
            "+15551230020" -> listOf(
                Triple("Hi honey, how was your day?", lastTs - 8 * hour, false),
                Triple("It was great! Had a productive meeting", lastTs - 7 * hour, true),
                Triple("That's wonderful! Proud of you", lastTs - 6 * hour, false),
                Triple("Call me when you get a chance sweetie", lastTs - 5 * hour, false),
            )
            "+15551230030" -> listOf(
                Triple("Team standup at 10am sharp tomorrow", lastTs - 2 * day, false),
                Triple("Got it, I'll prepare the sprint review", lastTs - 2 * day + hour, true),
                Triple("Meeting rescheduled to 3pm tomorrow", lastTs - 1 * day, false),
            )
            "+15551230040" -> listOf(
                Triple("Dude check this out", lastTs - 1 * day - 5 * hour, false),
                Triple("lol what is that", lastTs - 1 * day - 4 * hour, true),
                Triple("haha that's hilarious \uD83D\uDE02", lastTs - 1 * day - 3 * hour, true),
            )
            "+15551230050" -> listOf(
                Triple("Guess what today is!", lastTs - 2 * day - 2 * hour, false),
                Triple("Your birthday!! \uD83C\uDF82", lastTs - 2 * day - hour, true),
                Triple("Happy birthday! \uD83C\uDF82\uD83C\uDF89", lastTs - 2 * day, true),
            )
            "+15551230060" -> listOf(
                Triple("Can you come help me Saturday?", lastTs - 5 * day, false),
                Triple("Sure, what do you need?", lastTs - 4 * day, true),
                Triple("Just some help in the garden", lastTs - 4 * day + 2 * hour, false),
                Triple("Fixed the lawnmower. Can you bring the tools back?", lastTs - 3 * day, false),
            )
            "+15551230070" -> listOf(
                Triple("Order #4521 confirmed. Preparing your pizza!", lastTs - 5 * hour, false),
                Triple("Your order is on the way! \uD83C\uDF55", lastTs - 4 * day, false),
            )
            "+15551230080" -> listOf(
                Triple("Are we still on for 2pm?", lastTs - 6 * day, false),
                Triple("Yes! Meet at the usual spot", lastTs - 5 * day - 2 * hour, true),
                Triple("Running 10 min late, start without me", lastTs - 5 * day, true),
            )
            "+15551230090" -> listOf(
                Triple("Reminder: annual checkup Friday 2pm", lastTs - 7 * day, false),
                Triple("I'll be there. Do I need to fast?", lastTs - 6 * day - hour, true),
                Triple("Your appointment is confirmed for Friday at 2pm", lastTs - 6 * day, false),
            )
            "+15551230100" -> listOf(
                Triple("Great session today!", lastTs - 8 * day, true),
                Triple("Tomorrow is leg day, you in?", lastTs - 7 * day - hour, false),
                Triple("Leg day tomorrow \uD83D\uDCAA no excuses!", lastTs - 7 * day, false),
            )
            else -> emptyList()
        }
    }
}
