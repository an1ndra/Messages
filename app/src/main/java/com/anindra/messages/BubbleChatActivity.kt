package com.anindra.messages

/**
 * The activity a conversation bubble launches into (#354).
 *
 * It is [MainActivity] itself, declared separately so it can own a launch mode
 * and a task affinity of its own. The bubble's launch used to target
 * MainActivity, whose singleTop handed it to the instance already on screen:
 * the app navigated and the expanded bubble had no task to host, so it opened
 * blank instead of showing the chat.
 */
class BubbleChatActivity : MainActivity()
