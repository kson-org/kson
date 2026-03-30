package org.kson

import org.kson.value.EmbedBlock
import org.kson.value.KsonBoolean
import org.kson.value.KsonList
import org.kson.value.KsonNull
import org.kson.value.KsonNumber
import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonValue

/**
 * Builds [DocumentSymbol] trees from [KsonValue] AST nodes.
 */
internal object DocumentSymbolBuilder {

    fun build(ksonValue: KsonValue): List<DocumentSymbol> {
        return listOf(createSymbol("root", ksonValue))
    }

    private fun createSymbol(name: String, value: KsonValue): DocumentSymbol {
        val range = value.toRange()
        return when (value) {
            is KsonObject -> createObjectSymbol(value, name, range)
            is KsonList -> createArraySymbol(value, name, range)
            is KsonString -> createPrimitiveSymbol(name, range, DocumentSymbolKind.STRING, value.value)
            is KsonNumber -> createPrimitiveSymbol(name, range, DocumentSymbolKind.NUMBER, value.value.asString)
            is KsonBoolean -> createPrimitiveSymbol(name, range, DocumentSymbolKind.BOOLEAN, value.value.toString())
            is KsonNull -> createPrimitiveSymbol(name, range, DocumentSymbolKind.NULL, "null")
            is EmbedBlock -> createEmbedSymbol(value, name, range)
        }
    }

    private fun createObjectSymbol(obj: KsonObject, name: String, range: Range): DocumentSymbol {
        val children = obj.propertyMap.map { (_, prop) ->
            createPropertySymbol(prop.propName, prop.propValue)
        }
        return DocumentSymbol(
            name = name,
            kind = DocumentSymbolKind.OBJECT,
            range = range,
            selectionRange = range,
            detail = "{${obj.propertyMap.size} properties}",
            children = children
        )
    }

    private fun createPropertySymbol(keyString: KsonString, value: KsonValue): DocumentSymbol {
        val keyRange = keyString.toRange()
        return DocumentSymbol(
            name = keyString.value,
            kind = DocumentSymbolKind.KEY,
            range = keyRange,
            selectionRange = keyRange,
            detail = "key",
            children = listOf(createSymbol(keyString.value, value))
        )
    }

    private fun createArraySymbol(array: KsonList, name: String, range: Range): DocumentSymbol {
        val children = array.elements.mapIndexed { index, element ->
            createSymbol("[${index}]", element)
        }
        return DocumentSymbol(
            name = name,
            kind = DocumentSymbolKind.ARRAY,
            range = range,
            selectionRange = range,
            detail = "[${array.elements.size} items]",
            children = children
        )
    }

    private fun createEmbedSymbol(embed: EmbedBlock, name: String, range: Range): DocumentSymbol {
        val tag = embed.embedTag?.value ?: "embed"
        return DocumentSymbol(
            name = name,
            kind = DocumentSymbolKind.EMBED,
            range = range,
            selectionRange = range,
            detail = tag,
            children = emptyList()
        )
    }

    private fun createPrimitiveSymbol(name: String, range: Range, kind: DocumentSymbolKind, detail: String): DocumentSymbol {
        return DocumentSymbol(
            name = name,
            kind = kind,
            range = range,
            selectionRange = range,
            detail = detail,
            children = emptyList()
        )
    }

}
