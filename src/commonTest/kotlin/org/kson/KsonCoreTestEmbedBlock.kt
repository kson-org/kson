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
                %: meta
                content%%
            """.trimIndent(),
            """
                embedMetadata: "meta"
                embedContent: |
                  content
            """.trimIndent(),
            """
                {
                  "embedMetadata": "meta",
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
                  "embedContent": "content\n"
                  "unrelatedKey": "is not an embed block"
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
                  "embedContent":
                    not: content
                  "unrelatedKey": "is not an embed block"
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
    fun testEmbeddedEmbedBlockFromObject(){
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
