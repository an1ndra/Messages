package com.anindra.messages.data

/**
 * What a provider thread means for this app's conversations.
 *
 * The platform already groups a group MMS: `TelephonyMmsStore` writes a
 * `thread_id` from the same participant set on a send and on a receive, so both
 * directions of one conversation land on one thread. That is the identity every
 * other SMS app uses — QKSMS keys its Realm `Conversation` on it, Fossify on the
 * Room `thread_id` primary key, AOSP on `Conversation.threadId` — and it is what
 * this app was missing while it routed a group by its sender (#448).
 *
 * Kept pure and free of `android.*`: addresses are canonicalised by the caller,
 * so equality here is plain string equality and the rules are unit-testable.
 */
object ThreadRouting {

    /**
     * A conversation that could be the one a provider thread belongs to.
     *
     * [providerThreadId] is 0 for a conversation started in this app before the
     * platform wrote a thread for it, or for one that predates this column.
     */
    data class Candidate(
        val conversationId: Long,
        val providerThreadId: Long,
        val recipients: List<String>
    )

    sealed interface Route {
        /**
         * The conversation already exists. [missing] are the members the thread
         * has and the conversation does not, e.g. someone was added to the
         * group.
         */
        data class Adopt(val conversationId: Long, val missing: List<String>) : Route

        /** No conversation owns this thread yet; start one with [members]. */
        data class Create(val members: List<String>) : Route
    }

    /**
     * Where a provider thread belongs.
     *
     * The thread id wins outright: it is the platform's own grouping, so two
     * groups that share a sender can no longer be fused. Failing that, a
     * conversation holding exactly the same members is adopted and the thread is
     * stamped onto it — that is a group created here before the platform had a
     * thread, and re-matching it keeps its history instead of starting a second
     * thread with the same people.
     *
     * A conversation with a different member set is never adopted, and a
     * single-recipient conversation is never a candidate: a group is a group.
     */
    fun route(threadId: Long, participants: List<String>, candidates: List<Candidate>): Route {
        val members = participants.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (threadId > 0) {
            candidates.firstOrNull { it.providerThreadId == threadId }?.let { match ->
                return Route.Adopt(match.conversationId, missing(members, match.recipients))
            }
        }
        if (members.size > 1) {
            candidates
                .filter { it.providerThreadId == 0L && it.recipients.size > 1 }
                .filter { sameMembers(it.recipients, members) }
                .minByOrNull { it.conversationId }
                ?.let { return Route.Adopt(it.conversationId, emptyList()) }
        }
        return Route.Create(members)
    }

    /** True when both sides name the same people, in any order or spelling. */
    fun sameMembers(a: List<String>, b: List<String>): Boolean {
        val left = a.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val right = b.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return left == right
    }

    private fun missing(participants: List<String>, current: List<String>): List<String> {
        val held = current.map { it.trim() }.toSet()
        return participants.filter { it !in held }
    }
}
