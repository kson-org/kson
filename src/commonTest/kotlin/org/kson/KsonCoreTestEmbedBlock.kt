package org.kson

import kotlin.test.Test

class KsonCoreTestEmbedBlock : KsonCoreTest {
    @Test
    fun testEmbedBlockSource() {
        assertParsesTo(
            """
                %
                    this is a raw embed
                %%
            """,
            """
                %
                    this is a raw embed
                %%
            """.trimIndent(),
            """
               |2
                     this is a raw embed
                 
            """.trimIndent(),
            """
                "    this is a raw embed\n"
            """.trimIndent()
        )

        assertParsesTo(
            """
                %sql
                    select * from something
                %%
            """,
            """
                %sql
                    select * from something
                %%
            """.trimIndent(),
            """
                |2
                      select * from something
                  
            """.trimIndent(),
            """
                "    select * from something\n"
            """.trimIndent()
        )


        assertParsesTo(
            """
                %sql: database
                    select * from something
                %%
            """,
            """
                %sql: database
                    select * from something
                %%
            """.trimIndent(),
            """
                |2
                      select * from something
                  
            """.trimIndent(),
            """
                "    select * from something\n"
            """.trimIndent()
        )

        assertParsesTo(
            """
                %sql: ::::::::::::database::::::
                    select * from something
                %%
            """,
            """
                %sql: ::::::::::::database::::::
                    select * from something
                %%
            """.trimIndent(),
            """
                |2
                      select * from something
                  
            """.trimIndent(),
            """
                "    select * from something\n"
            """.trimIndent()
        )
    }

    @Test
    fun testEmbedBlockWithAlternativeDelimiter() {
        assertParsesTo(
            """
                $
                    this is a raw embed with alternative delimiter
                $$
            """.trimIndent(),
            // note that we prefer the primary %% delimiter in our transpiler output
            """
                %
                    this is a raw embed with alternative delimiter
                %%
            """.trimIndent(),
            """
                |2
                      this is a raw embed with alternative delimiter
                  
            """.trimIndent(),
            """
                "    this is a raw embed with alternative delimiter\n"
            """.trimIndent()
        )

        assertParsesTo(
            """
                $${"sql"}
                    select * from something
                $$
            """.trimIndent(),
            """
                %sql
                    select * from something
                %%
            """.trimIndent(),
            """
                |2
                      select * from something
                  
            """.trimIndent(),
            """
                "    select * from something\n"
            """.trimIndent()
        )
    }

    @Test
    fun testEmbedBlockWithEscapes() {
        assertParsesTo(
            """
            %
            this is an escaped delim %\%
            whereas in this case, this is not $\$
            %%
            """.trimIndent(),
            """
            %
            this is an escaped delim %\%
            whereas in this case, this is not $\$
            %%
            """.trimIndent(),
            """
            |
              this is an escaped delim %%
              whereas in this case, this is not $\$
              
            """.trimIndent(),
            """
            "this is an escaped delim %%\nwhereas in this case, this is not $\\$\n"
            """.trimIndent()
        )

        assertParsesTo(
            """
            %
            more %\% %\% %\% than $$ should yield a $$-delimited block
            %%
            """.trimIndent(),
            """
            $
            more %% %% %% than $\$ should yield a $\$-delimited block
            $$
            """.trimIndent(),
            """
            |
              more %% %% %% than $$ should yield a $$-delimited block
              
            """.trimIndent(),
            """
            "more %% %% %% than $$ should yield a $$-delimited block\n"
            """.trimIndent()
        )
    }

    @Test
    fun testEmbedBlockWithAlternativeDelimiterAndEscapes() {
        assertParsesTo(
            """
            $
            these double $\$ dollars are %%%% embedded but escaped
            $$
            """.trimIndent(),
            """
            $
            these double $\$ dollars are %%%% embedded but escaped
            $$
            """.trimIndent(),
            """
            |
              these double $$ dollars are %%%% embedded but escaped
              
            """.trimIndent(),
            """
            "these double $$ dollars are %%%% embedded but escaped\n"
            """.trimIndent()
        )
    }

    @Test
    fun testEmbedBlockEndingInSlash() {
        assertParsesTo(
            """
                %
                %\%%
            """.trimIndent(),
            """
                %
                %\%%
            """.trimIndent(),
            """
                |
                  %\
            """.trimIndent(),
            """
                "%\\"
            """.trimIndent()
        )
    }

    @Test
    fun testEmbedBlockTagsRetainment() {
        val compileSettings = KsonCoreTest.CompileSettings(
            yamlSettings = CompileTarget.Yaml(retainEmbedTags = true),
            jsonSettings = Json(retainEmbedTags = true)
        )
        assertParsesTo(
            """
                %
                content%%
            """.trimIndent(),
            """
                %
                content%%
            """.trimIndent(),
            """
                embedContent: |
                  content
            """.trimIndent(),
            """
                {
                  "embedContent": "content"
                }
            """.trimIndent(), compileSettings = compileSettings
        )

        assertParsesTo(
            """
                %sql
                content%%
            """.trimIndent(),
            """
                %sql
                content%%
            """.trimIndent(),
            """
                embedTag: "sql"
                embedContent: |
                  content
            """.trimIndent(),
            """
                {
                  "embedTag": "sql",
                  "embedContent": "content"
                }
            """.trimIndent(), compileSettings = compileSettings
        )

        assertParsesTo(
            """
                %:meta
                content%%
            """.trimIndent(),
            """
                %:meta
                content%%
            """.trimIndent(),
            """
                embedTag: ":meta"
                embedContent: |
                  content
            """.trimIndent(),
            """
                {
                  "embedTag": ":meta",
                  "embedContent": "content"
                }
            """.trimIndent(), compileSettings = compileSettings
        )

        assertParsesTo(
            """
                %sql: "server=10.0.1.174;uid=root;database=company"
                content%%
            """.trimIndent(),
            """
                %sql: "server=10.0.1.174;uid=root;database=company"
                content%%
            """.trimIndent(),
            """
                embedTag: "sql: \"server=10.0.1.174;uid=root;database=company\""
                embedContent: |
                  content
            """.trimIndent(),
            """
                {
                  "embedTag": "sql: \"server=10.0.1.174;uid=root;database=company\"",
                  "embedContent": "content"
                }
            """.trimIndent(), compileSettings = compileSettings
        )
    }

    @Test
    fun testEmbedBlockFromObject() {
        val compileSettings = KsonCoreTest.CompileSettings(
            yamlSettings = CompileTarget.Yaml(retainEmbedTags = true),
            jsonSettings = Json(retainEmbedTags = true)
        )

        assertParsesTo(
            """
               embedBlock:
                 "embedContent": "content\n"
            """.trimIndent(),
            """
                embedBlock: %
                  content
                  %%
            """.trimIndent(),
            """
                embedBlock:
                  embedContent: |
                    content
                    
            """.trimIndent(),
            """
                {
                  "embedBlock": {
                    "embedContent": "content\n"
                  }
                }
            """.trimIndent(), compileSettings = compileSettings
        )

        assertParsesTo(
            """
               embedBlock:
                 "embedContent": "content\n"
                 "unrelatedKey": "is not an embed block"
            """.trimIndent(),
            """
                embedBlock:
                  embedContent: 'content\n'
                  unrelatedKey: 'is not an embed block'
            """.trimIndent(),
            """
                embedBlock:
                  embedContent: "content\n"
                  unrelatedKey: "is not an embed block"
            """.trimIndent(),
            """
                {
                  "embedBlock": {
                    "embedContent": "content\n",
                    "unrelatedKey": "is not an embed block"
                  }
                }
            """.trimIndent(), compileSettings = compileSettings
        )
    }

    @Test
    fun testEmbedBlockFromObjectWithoutStrings(){
        val compileSettings = KsonCoreTest.CompileSettings(
            yamlSettings = CompileTarget.Yaml(retainEmbedTags = true),
            jsonSettings = Json(retainEmbedTags = true)
        )

        assertParsesTo(
            """
               embedBlock:
                 "embedContent": {not: content}
                 "unrelatedKey": "is not an embed block"
            """.trimIndent(),
            """
                embedBlock:
                  embedContent:
                    not: content
                    .
                  unrelatedKey: 'is not an embed block'
            """.trimIndent(),
            """
                embedBlock:
                  embedContent:
                    not: content
                  unrelatedKey: "is not an embed block"
            """.trimIndent(),
            """
                {
                  "embedBlock": {
                    "embedContent": {
                      "not": "content"
                    },
                    "unrelatedKey": "is not an embed block"
                  }
                }
            """.trimIndent(), compileSettings = compileSettings
        )
    }

    @Test
    fun testEmbedContentWithUnrelatedKeyNotDecodedAsEmbedBlock() {
        val compileSettings = KsonCoreTest.CompileSettings(
            yamlSettings = CompileTarget.Yaml(retainEmbedTags = true),
            jsonSettings = Json(retainEmbedTags = true)
        )

        // An object with "embedContent" and a non-embed key should remain an object, not decode as an embed block
        assertParsesTo(
            """
               wrapper:
                 "embedContent": "content"
                 "other": "value"
            """.trimIndent(),
            """
                wrapper:
                  embedContent: content
                  other: value
            """.trimIndent(),
            """
                wrapper:
                  embedContent: content
                  other: value
            """.trimIndent(),
            """
                {
                  "wrapper": {
                    "embedContent": "content",
                    "other": "value"
                  }
                }
            """.trimIndent(), compileSettings = compileSettings
        )
    }

    @Test
    fun testEmbedBlockTagWithValidEscapes() {
        val compileSettings = KsonCoreTest.CompileSettings(
            yamlSettings = CompileTarget.Yaml(retainEmbedTags = true),
            jsonSettings = Json(retainEmbedTags = true)
        )

        // Tag with \t escape — raw text "my\ttag" is preserved as the tag value
        assertParsesTo(
            """
                %my\ttag
                content%%
            """.trimIndent(),
            """
                %my\ttag
                content%%
            """.trimIndent(),
            """
                embedTag: "my\ttag"
                embedContent: |
                  content
            """.trimIndent(),
            """
                {
                  "embedTag": "my\ttag",
                  "embedContent": "content"
                }
            """.trimIndent(), compileSettings = compileSettings
        )

        // Tag with unicode escape — raw text "\u0041tag" is preserved as the tag value
        assertParsesTo(
            """
                %\u0041tag
                content%%
            """.trimIndent(),
            """
                %\u0041tag
                content%%
            """.trimIndent(),
            """
                embedTag: "\u0041tag"
                embedContent: |
                  content
            """.trimIndent(),
            """
                {
                  "embedTag": "\u0041tag",
                  "embedContent": "content"
                }
            """.trimIndent(), compileSettings = compileSettings
        )

        // Tag with escaped backslash — raw text "path\\to" is preserved as the tag value
        assertParsesTo(
            """
                %path\\to
                content%%
            """.trimIndent(),
            """
                %path\\to
                content%%
            """.trimIndent(),
            """
                embedTag: "path\\to"
                embedContent: |
                  content
            """.trimIndent(),
            """
                {
                  "embedTag": "path\\to",
                  "embedContent": "content"
                }
            """.trimIndent(), compileSettings = compileSettings
        )
    }

    @Test
    fun testEmbedBlockTagWithMetadataAndEscapes() {
        val compileSettings = KsonCoreTest.CompileSettings(
            yamlSettings = CompileTarget.Yaml(retainEmbedTags = true),
            jsonSettings = Json(retainEmbedTags = true)
        )

        // Tag with metadata containing escapes — the full raw tag text including
        // the metadata portion is preserved, with backslashes and quotes intact
        assertParsesTo(
            """
                %sql: "conn\tstring"
                content%%
            """.trimIndent(),
            """
                %sql: "conn\tstring"
                content%%
            """.trimIndent(),
            """
                embedTag: "sql: \"conn\tstring\""
                embedContent: |
                  content
            """.trimIndent(),
            """
                {
                  "embedTag": "sql: \"conn\tstring\"",
                  "embedContent": "content"
                }
            """.trimIndent(), compileSettings = compileSettings
        )
    }

    @Test
    fun testEmbedBlockTagFromObjectWithEscapes() {
        val compileSettings = KsonCoreTest.CompileSettings(
            yamlSettings = CompileTarget.Yaml(retainEmbedTags = true),
            jsonSettings = Json(retainEmbedTags = true)
        )

        // Verify bijection: object with embedTag containing escapes roundtrips through embed block form
        assertParsesTo(
            """
               embedBlock:
                 "embedTag": "my\ttag"
                 "embedContent": "content"
            """.trimIndent(),
            """
                embedBlock: %my\ttag
                  content%%
            """.trimIndent(),
            """
                embedBlock:
                  embedTag: "my\ttag"
                  embedContent: |
                    content
            """.trimIndent(),
            """
                {
                  "embedBlock": {
                    "embedTag": "my\ttag",
                    "embedContent": "content"
                  }
                }
            """.trimIndent(), compileSettings = compileSettings
        )
    }

    @Test
    fun testEmbedBlockTagFromObjectWithUnquotedTag() {
        val compileSettings = KsonCoreTest.CompileSettings(
            yamlSettings = CompileTarget.Yaml(retainEmbedTags = true),
            jsonSettings = Json(retainEmbedTags = true)
        )

        // Verify that an object with an unquoted embedTag value decodes as an embed block
        assertParsesTo(
            """
                embedBlock:
                  embedTag: sql
                  "embedContent": "content"
            """.trimIndent(),
            """
                embedBlock: %sql
                  content%%
            """.trimIndent(),
            """
                embedBlock:
                  embedTag: "sql"
                  embedContent: |
                    content
            """.trimIndent(),
            """
                {
                  "embedBlock": {
                    "embedTag": "sql",
                    "embedContent": "content"
                  }
                }
            """.trimIndent(), compileSettings = compileSettings
        )
    }

    @Test
    fun testEmbedBlockFromObjectWithLiteralNewlineInTag() {
        val compileSettings = KsonCoreTest.CompileSettings(
            yamlSettings = CompileTarget.Yaml(retainEmbedTags = true),
            jsonSettings = Json(retainEmbedTags = true)
        )

        assertParsesTo(
            """
                embedBlock:
                  embedTag: "line1
                line2"
                  embedContent: content""".trimIndent(),
            """
                embedBlock: %line1\nline2
                  content%%""".trimIndent(),
            """
                embedBlock:
                  embedTag: "line1\nline2"
                  embedContent: |
                    content
            """.trimIndent(),
            """
                {
                  "embedBlock": {
                    "embedTag": "line1\nline2",
                    "embedContent": "content"
                  }
                }
            """.trimIndent(), compileSettings = compileSettings
        )
    }

    @Test
    fun testEmbedBlockTagWithLiteralTabInJsonObjectRendering() {
        val compileSettings = KsonCoreTest.CompileSettings(
            yamlSettings = CompileTarget.Yaml(retainEmbedTags = true),
            jsonSettings = Json(retainEmbedTags = true)
        )

        assertParsesTo(
            "%my\ttag\ncontent%%",
            """
                %my\ttag
                content%%
            """.trimIndent(),
            """
                  embedTag: "my\ttag"
                  embedContent: |
                    content
            """.trimIndent(),
            """
                {
                  "embedTag": "my\ttag",
                  "embedContent": "content"
                }
            """.trimIndent(), compileSettings = compileSettings
        )
    }

    @Test
    fun testEmbedBlockFromObjectWithDelimiterSequenceInTag() {
        val compileSettings = KsonCoreTest.CompileSettings(
            yamlSettings = CompileTarget.Yaml(retainEmbedTags = true),
            jsonSettings = Json(retainEmbedTags = true)
        )

        assertParsesTo(
            """
               embedBlock:
                 "embedTag": "has %% embed $$ delims"
                 "embedContent": "content"
            """.trimIndent(),
            """
                embedBlock: %has %% embed $$ delims
                  content%%
            """.trimIndent(),
            """
                embedBlock:
                  embedTag: "has %% embed $$ delims"
                  embedContent: |
                    content
            """.trimIndent(),
            """
                {
                  "embedBlock": {
                    "embedTag": "has %% embed $$ delims",
                    "embedContent": "content"
                  }
                }
            """.trimIndent(), compileSettings = compileSettings
        )

        // alternate delimter
        assertParsesTo(
            """
               embedBlock:
                 "embedTag": "has %% embed $$ delims"
                 "embedContent": "content with %% to force dollar-delimiters"
            """.trimIndent(),
            $$"""
                embedBlock: $has %% embed $$ delims
                  content with %% to force dollar-delimiters$$
            """.trimIndent(),
            """
                embedBlock:
                  embedTag: "has %% embed $$ delims"
                  embedContent: |
                    content with %% to force dollar-delimiters
            """.trimIndent(),
            """
                {
                  "embedBlock": {
                    "embedTag": "has %% embed $$ delims",
                    "embedContent": "content with %% to force dollar-delimiters"
                  }
                }
            """.trimIndent(), compileSettings = compileSettings
        )

        // tag is entirely a delimiter sequence
        assertParsesTo(
            """
               embedBlock:
                 "embedTag": "%%"
                 "embedContent": "content"
            """.trimIndent(),
            """
                embedBlock: %%%
                  content%%
            """.trimIndent(),
            """
                embedBlock:
                  embedTag: "%%"
                  embedContent: |
                    content
            """.trimIndent(),
            """
                {
                  "embedBlock": {
                    "embedTag": "%%",
                    "embedContent": "content"
                  }
                }
            """.trimIndent(), compileSettings = compileSettings
        )

        // tag ends with a delimiter sequence
        assertParsesTo(
            """
               embedBlock:
                 "embedTag": "tag%%"
                 "embedContent": "content"
            """.trimIndent(),
            """
                embedBlock: %tag%%
                  content%%
            """.trimIndent(),
            """
                embedBlock:
                  embedTag: "tag%%"
                  embedContent: |
                    content
            """.trimIndent(),
            """
                {
                  "embedBlock": {
                    "embedTag": "tag%%",
                    "embedContent": "content"
                  }
                }
            """.trimIndent(), compileSettings = compileSettings
        )

        // both delimiters in both tag and content, requiring escaping
        assertParsesTo(
            """
               embedBlock:
                 "embedTag": "%%$$"
                 "embedContent": "has %% and $$"
            """.trimIndent(),
            $$"""
                embedBlock: %%%$$
                  has %\% and $$%%
            """.trimIndent(),
            """
                embedBlock:
                  embedTag: "%%$$"
                  embedContent: |
                    has %% and $$
            """.trimIndent(),
            """
                {
                  "embedBlock": {
                    "embedTag": "%%$$",
                    "embedContent": "has %% and $$"
                  }
                }
            """.trimIndent(), compileSettings = compileSettings
        )
    }

    @Test
    fun testEmbeddedEmbedBlockFromObject() {
        val compileSettings = KsonCoreTest.CompileSettings(
            yamlSettings = CompileTarget.Yaml(retainEmbedTags = true),
            jsonSettings = Json(retainEmbedTags = true)
        )

        assertParsesTo(
            """
               embedBlock:
                 "embedContent": "embeddedEmbed: %\nEMBED CONTENT\n%%\n"
            """.trimIndent(),
            """
                embedBlock: $
                  embeddedEmbed: %
                  EMBED CONTENT
                  %%
                  $$
            """.trimIndent(),
            """
                embedBlock:
                  embedContent: |
                    embeddedEmbed: %
                    EMBED CONTENT
                    %%
                    
            """.trimIndent(),
            """
                {
                  "embedBlock": {
                    "embedContent": "embeddedEmbed: %\nEMBED CONTENT\n%%\n"
                  }
                }
            """.trimIndent(), compileSettings = compileSettings
        )

        assertParsesTo(
            """
               embedBlock:
                 "embedContent": "embeddedEmbed: $\nEMBED WITH %\\% CONTENT\n$$\n"
            """.trimIndent(),
            """
                embedBlock: %
                  embeddedEmbed: $
                  EMBED WITH %\\% CONTENT
                  $$
                  %%
            """.trimIndent(),
            """
                embedBlock:
                  embedContent: |
                    embeddedEmbed: ${'$'}
                    EMBED WITH %\% CONTENT
                    ${'$'}${'$'}
                    
            """.trimIndent(),
            """
                {
                  "embedBlock": {
                    "embedContent": "embeddedEmbed: ${'$'}\nEMBED WITH %\\% CONTENT\n${'$'}${'$'}\n"
                  }
                }
            """.trimIndent(), compileSettings = compileSettings
        )
    }
} 
