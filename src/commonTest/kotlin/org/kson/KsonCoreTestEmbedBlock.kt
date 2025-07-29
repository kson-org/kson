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
} 
