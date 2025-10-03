package org.kson.parser

import org.kson.ast.*
import org.kson.parser.ParsedElementType.*
import org.kson.parser.TokenType.*
import org.kson.parser.behavior.embedblock.EmbedDelim
import org.kson.parser.behavior.StringQuote
import org.kson.parser.behavior.embedblock.EmbedObjectKeys
import org.kson.parser.messages.CoreParseMessage
import org.kson.parser.messages.Message
import org.kson.stdlibx.exceptions.ShouldNotHappenException
import org.kson.stdlibx.exceptions.FatalParseException

/**
 * An [AstBuilder] implementation used to produce a [KsonRoot] rooted AST tree based on the given [Token]s
 *
 * @param tokens the [Token] stream to build into an AST
 * @param ignoreErrors skip some of the work of collecting error if the caller tells us they are going to ignore them anyway
 */
class KsonBuilder(private val tokens: List<Token>, private val ignoreErrors: Boolean = false) :
    AstBuilder,
    MarkerBuilderContext {

    /**
     * Initialize [currentToken] to the first non-[Lexer.ignoredTokens] token
     */
    private var currentToken = run {
        var firstRealTokenIdx = 0
        while (firstRealTokenIdx < tokens.size && Lexer.ignoredTokens.contains(tokens[firstRealTokenIdx].tokenType)) {
            firstRealTokenIdx++
        }
        firstRealTokenIdx
    }
    private var rootMarker = KsonMarker(this, object : MarkerCreator {
        override fun forgetMe(me: KsonMarker): KsonMarker {
            throw ShouldNotHappenException("The root marker has no creator that needs to forget it")
        }

        override fun dropMe(me: KsonMarker) {
            throw ShouldNotHappenException("The root marker has no creator that needs to drop it")
        }
    })

    override fun getValue(firstTokenIndex: Int, lastTokenIndex: Int): String {
        return tokens.subList(firstTokenIndex, lastTokenIndex + 1)
            .filter { it.tokenType != WHITESPACE && it.tokenType != COMMENT }
            .joinToString("") { it.value }
    }

    override fun getRawText(firstTokenIndex: Int, lastTokenIndex: Int): String {
        return tokens.subList(firstTokenIndex, lastTokenIndex + 1).joinToString("") { it.lexeme.text }
    }

    override fun getLocation(firstTokenIndex: Int, lastTokenIndex: Int): Location {
        return Location.merge(
            tokens[firstTokenIndex].lexeme.location,
            tokens[lastTokenIndex].lexeme.location)
    }

    override fun getComments(tokenIndex: Int): List<String> {
        return tokens[tokenIndex].comments
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
        while (currentToken < tokens.size && Lexer.ignoredTokens.contains(tokens[currentToken].tokenType)) {
            currentToken++
        }
    }

    override fun lookAhead(numTokens: Int): TokenType? {
        val aheadToken = currentToken + numTokens
        if (aheadToken < tokens.size) {
            return tokens[aheadToken].tokenType
        }
        return null
    }

    override fun eof(): Boolean {
        return currentToken >= tokens.size || tokens[currentToken].tokenType == EOF
    }

    override fun mark(): AstMarker {
        return rootMarker.addMark()
    }

    /**
     * Attempt to construct an [AstNode] tree from the [KsonMarker]s made in this builder
     *
     * @param messageSink a [MessageSink] to write any errors we encountered in parsing
     * @return the [KsonRoot] of the resulting tree.
     */
    fun buildTree(messageSink: MessageSink): KsonRoot {
        rootMarker.done(ROOT)
        if (!ignoreErrors) {
            walkForMessages(rootMarker, messageSink)
        }
        return unsafeAstCreate<KsonRoot>(rootMarker) { KsonRootError(it, rootMarker.getLocation()) }
    }

    /**
     * Walk the tree of [KsonMarker]s rooted at [marker] collecting the info from any messages (errors and warnings) into [messageSink]
     */
    private fun walkForMessages(marker: KsonMarker, messageSink: MessageSink) {
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
                walkForMessages(childMarker, messageSink)
            }
        }
    }

    /**
     * Transform the given [KsonMarker] tree rooted at [marker] into a full Kson [AstNode] tree.
     *
     * WARNING "UNSAFE" CODE: this is the RARE (ideally ONLY???) place where we allow unsafe/loose
     *  coding practices to convert our [KsonMarker] tree in a proper [AstNode]-based AST.  We allow this
     *  converter method to make assumptions about the structure of the [KsonMarker]s created in [Parser],
     *  including performing casts (in [unsafeAstCreate])
     */
    private fun toAst(marker: KsonMarker): AstNode {
        if (!marker.isDone()) {
            throw ShouldNotHappenException("Should have a well-formed, all-done marker tree at this point")
        }

        return when (marker.element) {
            is TokenType -> {
                when (marker.element) {
                    CURLY_BRACE_L,
                    CURLY_BRACE_R,
                    SQUARE_BRACKET_L,
                    SQUARE_BRACKET_R,
                    COLON,
                    COMMA,
                    COMMENT,
                    EMBED_OPEN_DELIM,
                    EMBED_CLOSE_DELIM,
                    EMBED_TAG,
                    EMBED_CONTENT,
                    OBJECT_KEY,
                    ILLEGAL_CHAR,
                    WHITESPACE -> {
                        throw ShouldNotHappenException("These tokens do not generate their own AST nodes")
                    }
                    FALSE -> {
                        FalseNode(marker.getLocation())
                    }
                    UNQUOTED_STRING -> {
                        UnquotedStringNode(marker.getValue(), marker.getLocation())
                    }
                    NULL -> {
                        NullNode(marker.getLocation())
                    }
                    NUMBER -> {
                        NumberNode(marker.getValue(), marker.getLocation())
                    }
                    TRUE -> {
                        TrueNode(marker.getLocation())
                    }
                    else -> {
                        // Kotlin seems to having trouble validating that our when is exhaustive here, so we
                        // add the old-school guardrail here
                        throw RuntimeException("Missing case for ${TokenType::class.simpleName}.${marker.element}")
                    }
                }
            }
            is ParsedElementType -> {
                val childMarkers = marker.childMarkers
                when (marker.element) {
                    EMBED_BLOCK -> {
                        /**
                         * [Parser.embedBlock] ensures we always find an [EMBED_OPEN_DELIM],
                         * here, which we use to understand which [EmbedDelim] is in use here
                         */
                        val embedDelimChar = childMarkers.find { it.element == EMBED_OPEN_DELIM }?.getValue()
                            ?: throw ShouldNotHappenException("The parser should have ensured we could find an open delim here")

                        val embedTagNode = childMarkers.find { it.element == EMBED_TAG }?.let{
                            QuotedStringNode(
                                StringQuote.SingleQuote.escapeQuotes(it.getValue()),
                                StringQuote.SingleQuote,
                                it.getLocation()
                            )
                        }

                        val metadataNode = childMarkers.find { it.element == EMBED_METADATA }?.let{
                            QuotedStringNode(
                                StringQuote.SingleQuote.escapeQuotes(it.getValue()),
                                StringQuote.SingleQuote,
                                it.getLocation()
                            )
                        }

                        val embedContentNode = childMarkers.find { it.element == EMBED_CONTENT }?.let{
                            QuotedStringNode(
                                StringQuote.SingleQuote.escapeQuotes(it.getValue()),
                                StringQuote.SingleQuote,
                                it.getLocation()
                            )
                        } ?: throw ShouldNotHappenException("Embed block should always have embed content")

                        EmbedBlockNode(
                            embedTagNode,
                            metadataNode,
                            embedContentNode,
                            EmbedDelim.fromString(embedDelimChar),
                            marker.getLocation())
                    }
                    OBJECT_KEY -> {
                        /**
                         * We assume [Parser.keyword] still structures a [OBJECT_KEY] as wrapping either
                         * an [UNQUOTED_STRING] mark or a [QUOTED_STRING] mark
                         */
                        val stringContentMark = childMarkers.first()
                        val objectKey = when (stringContentMark.element) {
                            UNQUOTED_STRING -> UnquotedStringNode(stringContentMark.getValue(), marker.getLocation())
                            QUOTED_STRING -> quoteStringToStringNode(stringContentMark)
                            else -> {
                                throw ShouldNotHappenException("unless our assumptions about keyword parsing have been invalidated")
                            }
                        }
                        return ObjectKeyNodeImpl(objectKey)
                    }
                    DASH_LIST, DASH_DELIMITED_LIST, BRACKET_LIST -> {
                        val listElementNodes = childMarkers.map { listElementMarker ->
                            unsafeAstCreate<ListElementNode>(listElementMarker) {
                                ListElementNodeError(it, listElementMarker.getLocation())
                            }
                        }
                        ListNode(listElementNodes, marker.getLocation())
                    }
                    LIST_ELEMENT -> {
                        val comments = marker.getComments()
                        val listElementValue: KsonValueNode = if (childMarkers.size == 1) {
                            unsafeAstCreate(childMarkers.first()) { KsonValueNodeError(it, childMarkers.first().getLocation()) }
                        } else {
                            throw FatalParseException("list element markers should mark exactly one value")
                        }
                        ListElementNodeImpl(
                            listElementValue,
                            comments,
                            marker.getLocation())
                    }
                    OBJECT -> {
                        val propertyNodes = childMarkers.map { property ->
                            unsafeAstCreate<ObjectPropertyNode>(property) {
                                ObjectPropertyNodeError(it, property.getLocation())
                            }
                        }

                        val embedBlockNode = decodeEmbedBlock(propertyNodes, marker.getLocation())

                        if (embedBlockNode != null) {
                            return embedBlockNode
                        }

                        ObjectNode(propertyNodes, marker.getLocation())
                    }
                    OBJECT_PROPERTY -> {
                        val comments = marker.getComments()
                        /**
                         * We assume [Parser.plainObject] still parses [OBJECT_PROPERTY]s as a keyword/value
                         * pair OR a keyword with a missing value error
                         */
                        if (childMarkers.size > 2) {
                            throw FatalParseException("unless object property parsing has changed significantly")
                        }
                        val keywordMark = childMarkers.getOrNull(0)
                            ?: throw ShouldNotHappenException("should have a keyword marker")
                        val keyNode: ObjectKeyNode = unsafeAstCreate(keywordMark) {
                            ObjectKeyNodeError(it, keywordMark.getLocation())
                        }
                        val valueMark = childMarkers.getOrNull(1)
                        val ksonValueNode: KsonValueNode = if (valueMark == null) {
                            KsonValueNodeError("", marker.getLocation())
                        } else {
                            unsafeAstCreate(valueMark) {
                                KsonValueNodeError(it, marker.getLocation())
                            }
                        }
                        if (keyNode is ObjectKeyNodeError || ksonValueNode is KsonValueNodeError) {
                            ObjectPropertyNodeError(marker.getRawText().trim(), marker.getLocation())
                        } else {
                            ObjectPropertyNodeImpl(
                                keyNode,
                                ksonValueNode,
                                comments,
                                marker.getLocation()
                            )
                        }
                    }
                    QUOTED_STRING -> {
                        quoteStringToStringNode(marker)
                    }
                    ROOT -> {
                        val comments = marker.getComments()

                        /**
                         * grab the EOF token so we can capture any document end comments that may have been
                         * anchored to it in the [Lexer]
                         */
                        val eofToken = tokens.last()
                        // sanity check this is the expected EOF token
                        if (eofToken.tokenType != EOF) {
                            throw ShouldNotHappenException("Token list must end in EOF")
                        }

                        val rootNode = unsafeAstCreate<KsonValueNode>(childMarkers[0]) {
                            KsonValueNodeError(it, marker.getLocation())
                        }

                        val erroneousTrailingContent = childMarkers.drop(1).map {
                            unsafeAstCreate<KsonValueNode>(it) {
                                KsonValueNodeError(it, marker.getLocation())
                            }
                        }

                        KsonRootImpl(rootNode,
                            erroneousTrailingContent,
                            comments,
                            eofToken.comments,
                            marker.getLocation())
                    }
                    else -> {
                        // Kotlin seems to having trouble validating that our when is exhaustive here, so we
                        // add the old-school guardrail here
                        throw RuntimeException("Missing case for ${ParsedElementType::class.simpleName}.${marker.element}")
                    }
                }
            }
            else -> {
                throw ShouldNotHappenException(
                    "Unexpected ${ElementType::class.simpleName}.  " +
                            "Should always be one of ${TokenType::class.simpleName} or ${ParsedElementType::class.simpleName}"
                )
            }
        }
    }

    /**
     * This method attempts to decode the [propertyNodes] as an [EmbedBlockNode]. An [ObjectNode] can be decoded if it
     * follows the rules specified in [EmbedObjectKeys]
     *
     * @return an embed block representation of the given properties if possible, and null otherwise
     */
    private fun decodeEmbedBlock(
        propertyNodes: List<ObjectPropertyNode>,
        location: Location
    ): EmbedBlockNode? {
        if (propertyNodes.size > 3){ return null }

        // Create a map of property keys to string values (if possible)
        val propertiesMap = propertyNodes.mapNotNull { prop ->
            (prop as? ObjectPropertyNodeImpl)?.let { property ->
                val keyString = (property.key as? ObjectKeyNodeImpl)?.key as? StringNodeImpl
                keyString?.stringContent?.let { key ->
                    key to (property.value as? StringNodeImpl)
                }
            }
        }.toMap()

        /**
         * Check whether this object follows all the rules for decoding specified in [EmbedObjectKeys]
         */
        if (!EmbedObjectKeys.canBeDecoded(propertiesMap)) { return null }

        val embedMetadataValue = propertiesMap[EmbedObjectKeys.EMBED_METADATA.key]
        val embedTagValue = propertiesMap[EmbedObjectKeys.EMBED_TAG.key]

        val embedContentProperty = propertiesMap[EmbedObjectKeys.EMBED_CONTENT.key] ?:
            throw ShouldNotHappenException("should have been validated for nullability above")
        val escapedContent = EmbedDelim.Percent.escapeEmbedContent(embedContentProperty.processedStringContent)
        val embedContentValue = QuotedStringNode(
            StringQuote.SingleQuote.escapeQuotes(escapedContent),
            StringQuote.SingleQuote,
            embedContentProperty.location
        )

        return EmbedBlockNode(
                embedTagValue,
                embedMetadataValue,
                embedContentValue,
                EmbedDelim.Percent,
                location
            )
    }

    /**
     *  Note that we do not use [unsafeAstCreate] for this transformation because both places in this [KsonBuilder]
     *  are confident they have an error-free [QUOTED_STRING] that can be transformed into a [QuotedStringNode]
     */
    private fun quoteStringToStringNode(marker: KsonMarker): QuotedStringNode {
        /**
         * [Parser.string] ensures that a [QUOTED_STRING] contains its [STRING_OPEN_QUOTE]
         * and [STRING_CLOSE_QUOTE]
         */
        val quotedString = marker.getValue()
        val stringDelim = quotedString.first()
        val stringContent = quotedString.drop(1).dropLast(1)

        return QuotedStringNode(stringContent, StringQuote.fromChar(stringDelim), marker.getLocation())
    }

    /**
     * Helper method to encapsulate the loose casts we're allowing in [KsonBuilder.toAst]
     * and give us a place to say:
     *
     * THIS SHOULD NOT BE EMULATED ELSEWHERE.  See the doc on [KsonBuilder.toAst] for rationale on why it's okay here.
     *
     * @param marker the marker to transform into its corresponding [AstNode], if possible
     * @param errorNodeGenerator a lambda to wrap an [ERROR] [marker]'s content in the appropriately typed
     *   [AstNodeError] to be used in place of the node we can't create
     */
    private fun <A : AstNode> unsafeAstCreate(marker: KsonMarker, errorNodeGenerator: (errorContent: String) -> A): A {
        if (marker.element == ERROR ) {
            return errorNodeGenerator(marker.getRawText())
        }
        val nodeToCast = toAst(marker)
        @Suppress("UNCHECKED_CAST") // see method doc for suppress rationale
        return nodeToCast as A
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
     * Get the raw underlying text for the range of tokens from [firstTokenIndex] to [lastTokenIndex], inclusive
     */
    fun getRawText(firstTokenIndex: Int, lastTokenIndex: Int): String

    /**
     * Get the location of the underlying text for the range of tokens from [firstTokenIndex] to [lastTokenIndex],
     * inclusive
     */
    fun getLocation(firstTokenIndex: Int, lastTokenIndex: Int): Location

    /**
     * Get any comments associated with the token at [tokenIndex]
     */
    fun getComments(tokenIndex: Int): List<String>

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
    var element: ElementType = INCOMPLETE
    val childMarkers = ArrayList<KsonMarker>()

    fun isDone(): Boolean {
        return element != INCOMPLETE
    }

    fun getRawText(): String {
        return context.getRawText(this.firstTokenIndex, this.lastTokenIndex)
    }

    fun getValue(): String {
        return context.getValue(this.firstTokenIndex, this.lastTokenIndex)
    }

    fun getLocation() : Location {
        return context.getLocation(this.firstTokenIndex, this.lastTokenIndex)
    }

    /**
     * Returns true if this [KsonMarker] denotes an entity that it makes sense to comment/document
     */
    private fun commentable(): Boolean {
        return when (element) {
            /**
             * These are the [ElementType]s that correspond to the [Documented] [AstNode] types,
             * and hence are the target for comments we find on tokens marked by this [TokenType]
             */
            ROOT, OBJECT_PROPERTY, LIST_ELEMENT -> true
            else -> false
        }
    }

    /**
     * Comments found in the tokens marked by a [KsonMarker] are either
     * [claimed] by the AstNode we will produce from this [KsonMarker],
     * or unclaimed (`[claimed] = false`), meaning they should be owned by some [commentable]
     * parent marker.
     */
    fun getComments(claimed: Boolean = true): List<String> {
        /**
         * We may claim comments if and only if we are [commentable]
         */
        if (claimed != commentable()) {
            return emptyList()
        }

        val comments = ArrayList<String>()

        var tokenIndex = firstTokenIndex
        var childMarkerIndex = 0

        /**
         * This token's comments are all the comments from its tokens NOT wrapped
         * in a child marker, and all "unclaimed" comments from its child markers
         */
        while (tokenIndex <= lastTokenIndex) {
            while (childMarkerIndex < childMarkers.size) {
                // grab any comments from tokens preceding this child (i.e. not wrapped in a child)
                while (tokenIndex < childMarkers[childMarkerIndex].firstTokenIndex) {
                    comments.addAll(context.getComments(tokenIndex))
                    tokenIndex++
                }

                // add all unclaimed comments from this child marker
                comments.addAll(childMarkers[childMarkerIndex].getComments(false))

                tokenIndex = childMarkers[childMarkerIndex].lastTokenIndex + 1
                childMarkerIndex++
            }

            // grab any remaining comments unwrapped by a child marker
            if (tokenIndex <= lastTokenIndex) {
                comments.addAll(context.getComments(tokenIndex))
                tokenIndex++
            }
        }

        return comments
    }

    override fun forgetMe(me: KsonMarker): KsonMarker {
        /**
         * Our [forgetMe] operation is a basic [removeLast] because [addMark] guarantees the last entry here
         * is the only unresolved mark created by us.  See [addMark] for details.
         */
        val lastChild = childMarkers.removeLast()
        if (lastChild != me) {
            throw ShouldNotHappenException(
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
        if (lastTokenIndex < firstTokenIndex) {
            /**
             * Raise an alarm if we accidentally make an empty mark. We want all markers to mark either
             * a non-empty parsed element OR a non-empty chunk of source that is in error
             */
            throw ShouldNotHappenException("Must not create empty elements.")
        }
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
        markedError = CoreParseMessage(message)
        done(ERROR)
    }
}