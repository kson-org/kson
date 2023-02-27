package org.kson.parser

import org.kson.ast.*
import org.kson.parser.ParsedElementType.*
import org.kson.parser.TokenType.*
import org.kson.parser.messages.Message

/**
 * An [AstBuilder] implementation used to produce a [KsonRoot] rooted AST tree based on the given [Token]s
 */
class KsonBuilder(private val tokens: List<Token>) :
    AstBuilder,
    MarkerBuilderContext {

    private var currentToken = 0
    private var rootMarker = KsonMarker(this, object : MarkerCreator {
        override fun forgetMe(me: KsonMarker): KsonMarker {
            throw RuntimeException("The root marker has no creator that needs to forget it")
        }

        override fun dropMe(me: KsonMarker) {
            throw RuntimeException("The root marker has no creator that needs to drop it")
        }
    })
    var hasErrors = false

    override fun getValue(firstTokenIndex: Int, lastTokenIndex: Int): String {
        return tokens.subList(firstTokenIndex, lastTokenIndex + 1).joinToString(" ") { it.value }
    }

    override fun getComments(tokenIndex: Int): List<String> {
        return tokens[tokenIndex].comments
    }

    override fun errorEncountered() {
        hasErrors = true
    }

    override fun getTokenIndex(): Int {
        return currentToken
    }

    override fun setTokenIndex(index: Int) {
        currentToken = index
    }

    override fun getTokenType(): TokenType? {
        if (currentToken < tokens.size) {
            return tokens[currentToken].tokenType
        }

        return null
    }

    override fun getTokenText(): String {
        return tokens[currentToken].lexeme.text
    }

    override fun advanceLexer() {
        currentToken++
    }

    override fun lookAhead(numTokens: Int): TokenType? {
        val aheadToken = currentToken + numTokens
        if (aheadToken < tokens.size) {
            return tokens[aheadToken].tokenType
        }
        return null
    }

    override fun eof(): Boolean {
        return currentToken > tokens.size - 1
    }

    override fun mark(): AstMarker {
        return rootMarker.addMark()
    }

    fun buildTree(messageSink: MessageSink): KsonRoot? {
        rootMarker.done(ROOT)

        return if (hasErrors) {
            walkForErrors(rootMarker, messageSink)
            return null
        } else {
            unsafeAstCast(toAst(rootMarker))
        }
    }

    /**
     * Walk the tree of [KsonMarker]s rooted at [marker] collecting the info from any error marks into [messageSink]
     */
    private fun walkForErrors(marker: KsonMarker, messageSink: MessageSink) {
        val errorMessage = marker.markedError
        if (errorMessage != null) {
            messageSink.error(
                Location.merge(
                    tokens[marker.firstTokenIndex].lexeme.location,
                    tokens[marker.lastTokenIndex].lexeme.location
                ), errorMessage
            )
        }

        if (marker.childMarkers.isNotEmpty()) {
            for (childMarker in marker.childMarkers) {
                walkForErrors(childMarker, messageSink)
            }
        }
    }

    /**
     * Transform the given [KsonMarker] tree rooted at [marker] into a full Kson [AstNode] tree.
     * This should NEVER be called if there are parse errors because:
     *
     * WARNING "UNSAFE" CODE: this is the RARE (ideally ONLY???) place where we allow unsafe/loose
     *  coding practices for simplicity and speed.  This method gets to assume it is given a well-formed [KsonMarker]
     *  tree (since it makes no sense to generate an AST tree for invalid code), and hence gets to assume things
     *  about the structure and do check-free array look-ups and casts.  We've encapsulated the unsafe operations
     *  into [unsafeAstCast] and [unsafeMarkerLookup]---if brittleness related to these operations becomes too common
     *  and onerous, we should rethink this
     */
    private fun toAst(marker: KsonMarker): AstNode {
        if (!marker.isDone()) {
            throw RuntimeException("Should have a well-formed, all-done marker tree at this point")
        }

        val comments = marker.getComments()

        return when (marker.element) {
            is TokenType -> {
                when (marker.element) {
                    BRACE_L,
                    BRACE_R,
                    BRACKET_L,
                    BRACKET_R,
                    COLON,
                    COMMA,
                    COMMENT,
                    EMBED_END,
                    EMBED_START,
                    EMBED_TAG,
                    EMBED_CONTENT,
                    ILLEGAL_TOKEN,
                    WHITESPACE -> {
                        throw RuntimeException("These tokens do not generate their own AST nodes")
                    }
                    FALSE -> {
                        FalseNode(comments)
                    }
                    IDENTIFIER -> {
                        IdentifierNode(marker.getValue(), comments)
                    }
                    NULL -> {
                        NullNode(comments)
                    }
                    NUMBER -> {
                        NumberNode(marker.getValue(), comments)
                    }
                    STRING -> {
                        StringNode(marker.getValue(), comments)
                    }
                    TRUE -> {
                        TrueNode(comments)
                    }
                    else -> {
                        // Kotlin seems to having trouble validating that our when is exhaustive here, so we
                        // add the old-school guardrail here
                        throw RuntimeException("Unexpected ${TokenType::class.simpleName}, do we need a new case?")
                    }
                }
            }
            is ParsedElementType -> {
                val childMarkers = marker.childMarkers
                when (marker.element) {
                    EMBED_BLOCK -> {
                        val embedTag =
                            unsafeMarkerLookup(childMarkers, 0).getValue()
                        val embedContent =
                            unsafeMarkerLookup(childMarkers, 1).getValue()
                        EmbedBlockNode(embedTag, embedContent, comments)
                    }
                    LIST -> {
                        val listElementNodes = childMarkers.map { unsafeAstCast<ListElementNode>(toAst(it)) }
                        ListNode(listElementNodes, comments)
                    }
                    LIST_ELEMENT -> {
                        ListElementNode(unsafeAstCast(toAst(unsafeMarkerLookup(childMarkers, 0))), comments)
                    }
                    OBJECT_DEFINITION -> {
                        val objectName = unsafeMarkerLookup(childMarkers, 0).getValue()
                        val objectInternalsNode =
                            unsafeAstCast<ObjectInternalsNode>(toAst(unsafeMarkerLookup(childMarkers, 1)))
                        ObjectDefinitionNode(objectName, objectInternalsNode, comments)
                    }
                    OBJECT_INTERNALS -> {
                        val propertyNodes = childMarkers.map {
                            unsafeAstCast<ObjectPropertyNode>(toAst(it))
                        }
                        ObjectInternalsNode(propertyNodes, comments)
                    }
                    OBJECT_PROPERTY -> {
                        ObjectPropertyNode(
                            unsafeAstCast(toAst(unsafeMarkerLookup(childMarkers, 0))),
                            unsafeAstCast(toAst(unsafeMarkerLookup(childMarkers, 1))),
                            comments
                        )
                    }
                    ROOT -> {
                        KsonRoot(toAst(unsafeMarkerLookup(childMarkers, 0)), emptyList())
                    }
                    else -> {
                        // Kotlin seems to having trouble validating that our when is exhaustive here, so we
                        // add the old-school guardrail here
                        throw RuntimeException("Unexpected ${ParsedElementType::class.simpleName}, do we need a new case?")
                    }
                }
            }
            else -> {
                throw RuntimeException(
                    "Unexpected ${ElementType::class.simpleName}.  " +
                            "Should always be one of ${TokenType::class.simpleName} or ${ParsedElementType::class.simpleName}"
                )
            }
        }
    }

    /**
     * Helper method to encapsulate the loose casts we're allowing in [KsonBuilder.toAst]
     * and give us a place to say:
     *
     * THIS SHOULD NOT BE EMULATED ELSEWHERE.  See the doc on [KsonBuilder.toAst] for rationale on why it's okay here.
     */
    private fun <A : AstNode> unsafeAstCast(nodeToCast: AstNode): A {
        @Suppress("UNCHECKED_CAST") // see method doc for suppress rationale
        return nodeToCast as A
    }

    /**
     * Helper method to encapsulate the loose array lookups we're allowing in [KsonBuilder.toAst]
     * and give us a place to say:
     *
     * THIS SHOULD NOT BE EMULATED ELSEWHERE.  See the doc on [KsonBuilder.toAst] for rationale on why it's okay here.
     */
    private fun unsafeMarkerLookup(markerList: ArrayList<KsonMarker>, index: Int): KsonMarker {
        return markerList[index]
    }
}

/**
 * [MarkerBuilderContext] defines the contract for how [KsonMarker]s collaborate with the
 * [KsonBuilder] they are marking up, keeping the extent/complexity of the intentional coupling
 * of these two classes constrained and well-defined.  We also keep this complexity controlled
 * with strict encapsulation:
 *
 * This interface is private so that all implementation details of [KsonBuilder] (including
 * the [KsonMarker] implementation) are encapsulated in this file
 */
private interface MarkerBuilderContext {
    /**
     * Get the parsed [String] value for the range of tokens from [firstTokenIndex] to [lastTokenIndex], inclusive
     */
    fun getValue(firstTokenIndex: Int, lastTokenIndex: Int): String

    /**
     * Get any comments associated with the token at [tokenIndex]
     */
    fun getComments(tokenIndex: Int): List<String>

    /**
     * Register that a parsing error has been encountered
     */
    fun errorEncountered()

    /**
     * [KsonMarker]s mark token start and end indexes.  This returns the token index of the [KsonBuilder]
     * being marked
     */
    fun getTokenIndex(): Int

    /**
     * Reset the current token index of the [KsonBuilder] being marked to the given [index]
     */
    fun setTokenIndex(index: Int)
}

/**
 * [KsonMarker]s use this as part of [KsonMarker.rollbackTo] to ask their creator (generally a parent [KsonMarker])
 * to forget all references to them, removing them from the marker tree
 */
private interface MarkerCreator {
    /**
     * Used to eliminate a [KsonMarker] this instance has created from its tree of children
     */
    fun forgetMe(me: KsonMarker): KsonMarker

    /**
     * Used to edit a [KsonMarker] this instance has created out of its tree of children, preserving the dropped
     * marker's children by stitching them into the tree in its place.
     */
    fun dropMe(me: KsonMarker)
}

/**
 * [KsonMarker] is the [AstMarker] implementation designed to collaborate with [KsonBuilder]
 * (through [MarkerBuilderContext])
 */
private class KsonMarker(private val context: MarkerBuilderContext, private val creator: MarkerCreator) : AstMarker,
    MarkerCreator {
    val firstTokenIndex = context.getTokenIndex()
    var lastTokenIndex = firstTokenIndex
    var markedError: Message? = null
    var element: ElementType? = null
    val childMarkers = ArrayList<KsonMarker>()

    fun isDone(): Boolean {
        return element != null
    }

    fun getValue(): String {
        return context.getValue(this.firstTokenIndex, this.lastTokenIndex)
    }

    fun getComments(): List<String> {
        /**
         * Choosing which [KsonMarker] to anchor a token's comments to is a bit tricky, so over-documenting a bit:
         * We anchor comments to the most nested marker that starts with the commented token so that they will be
         * on the most nested AST node when this [KsonMarker] is converted to an [AstNode]
         *
         * To do that, we check...
         */
        if (// ... if this marker has no children ...
            this.childMarkers.isEmpty()
            // ... OR its first child does not also mark its first token ...
            || this.firstTokenIndex != this.childMarkers[0].firstTokenIndex
        ) {
            // ... then this marker owns the comments from its first token
            return context.getComments(this.firstTokenIndex)
        }

        // otherwise this marker has no comments
        return emptyList()
    }

    override fun forgetMe(me: KsonMarker): KsonMarker {
        /**
         * Our [forgetMe] operation is a basic [removeLast] because [addMark] guarantees the last entry here
         * is the only unresolved mark created by us.  See [addMark] for details.
         */
        val lastChild = childMarkers.removeLast()
        if (lastChild != me) {
            throw RuntimeException(
                "Bug: This should be an impossible `forgetMe` call since " +
                        "the order of resolving markers should ensure that calls to `forgetMe` are always " +
                        "on the last added marker"
            )
        }
        return lastChild
    }

    override fun dropMe(me: KsonMarker) {
        /**
         * Delegate removing this child from the tree to [forgetMe] so we don't need to duplicate its
         * carefully doc'ing and validating of the operation
         */
        val droppedChild = forgetMe(me)
        childMarkers.addAll(droppedChild.childMarkers)
    }

    /**
     * Adds a mark (recursively) nested within this mark.  NOTE: this is the linchpin of how the [KsonMarker]
     * tree is built up.  This will create a new direct descendent of this mark only if the last mark created
     * by this one has been resolved, otherwise we ask the currently unresolved mark to create the mark.
     *
     * This means there is always only ONE unresolved mark for whom _this_ mark is the [MarkerCreator]:
     * the last entry in [childMarkers] (hence [forgetMe] being implemented as a simple [removeLast])
     */
    fun addMark(): KsonMarker {
        return if (childMarkers.isNotEmpty() && !childMarkers.last().isDone()) {
            childMarkers.last().addMark()
        } else {
            val newMarker = KsonMarker(context, this)
            childMarkers.add(newMarker)
            newMarker
        }
    }

    override fun done(elementType: ElementType) {
        // the last token we advanced past is our last token
        lastTokenIndex = context.getTokenIndex() - 1
        element = elementType
    }

    override fun drop() {
        creator.dropMe(this)
    }

    override fun rollbackTo() {
        context.setTokenIndex(firstTokenIndex)
        creator.forgetMe(this)
    }

    override fun toString(): String {
        return element.toString()
    }

    override fun error(message: Message) {
        markedError = message
        context.errorEncountered()
        done(ERROR)
    }
}
