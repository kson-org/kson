package org.kson.testSupport

import org.kson.parser.LoggedMessage
import org.kson.parser.messages.Message
import kotlin.test.assertNotNull

/**
 * Assertion helper to validate the given [LoggedMessage]s are well-formed with respect to
 * [Message.expectedArgs] and [Message.format]
 */
fun assertMessageFormats(loggedMessages: List<LoggedMessage>) {
    for ((_, message, args) in loggedMessages) {
        /**
         * If a test blow-up leads here complaining about [Message] args, then whoever created this
         * [LoggedMessage] is likely not passing the correct [Message.expectedArgs]
         *
         * NOTE: this error is a bit indirect, validated long after the message is created, so that
         *   we can create errors as cheaply as possible, deferring the [Message.format] until the
         *   formatted message is needed.  Apologies to anyone troubleshooting here---hopefully it
         *   is not too hard to find the offending caller (let's refactor if it is!)
         */
        assertNotNull(
            message.format(*args),
            "should get a message from formatting this message, and should not blow up formatting it (i.e. *args are correct)"
        )
    }
}