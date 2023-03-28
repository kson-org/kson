package org.kson.jetbrains.editor

import com.intellij.lang.Commenter

/**
 * Configure a [Commenter] for Kson.  Note this returns mostly nulls since Kson does not have
 * a Block Comment construct
 */
internal class KsonCommenter : Commenter {
    override fun getLineCommentPrefix(): String {
        return "#"
    }

    override fun getBlockCommentPrefix(): String? {
        return null
    }

    override fun getBlockCommentSuffix(): String? {
        return null
    }

    override fun getCommentedBlockCommentPrefix(): String? {
        return null
    }

    override fun getCommentedBlockCommentSuffix(): String? {
        return null
    }

}