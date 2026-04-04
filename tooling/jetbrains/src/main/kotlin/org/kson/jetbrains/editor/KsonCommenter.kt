package org.kson.jetbrains.editor

import com.intellij.lang.Commenter

/**
 * Configure a [Commenter] for Kson.  Note this returns mostly nulls since Kson does not have
 * a Block Comment construct
 */
internal class KsonCommenter : Commenter {
    override fun getLineCommentPrefix(): String = "#"

    override fun getBlockCommentPrefix(): String? = null

    override fun getBlockCommentSuffix(): String? = null

    override fun getCommentedBlockCommentPrefix(): String? = null

    override fun getCommentedBlockCommentSuffix(): String? = null
}
