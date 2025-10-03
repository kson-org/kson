import {DocumentSymbol, Range, SymbolKind} from 'vscode-languageserver';
import {Analysis, KsonValue, KsonValueType} from 'kson';

// Extended DocumentSymbol that includes parent reference
export class DocumentSymbolWithParent implements DocumentSymbol {
    name: string;
    detail?: string;
    kind: SymbolKind;
    range: Range;
    selectionRange: Range;
    children?: DocumentSymbolWithParent[];
    parent?: DocumentSymbolWithParent;
    
    constructor(
        name: string,
        kind: SymbolKind,
        range: Range,
        detail?: string,
        selectionRange?: Range,
        parent?: DocumentSymbolWithParent
    ) {
        this.name = name;
        this.kind = kind;
        this.range = range;
        this.detail = detail;
        this.selectionRange = selectionRange || range;
        this.children = [];
        this.parent = parent;
    }
}

export class DocumentSymbolService {
    /**
     * Create the document symbols from an {@link Analysis}
     * @param ksonValue
     */
    getDocumentSymbols(ksonValue: KsonValue): DocumentSymbolWithParent[] {
        let documentSymbols: DocumentSymbol[];

        const documentSymbol = this.createSymbol('root', ksonValue)
        if (!documentSymbol){
            documentSymbols =  []
        }else{
            documentSymbols =  [documentSymbol]
        }
        return documentSymbols
    }
    
    /**
     * Recursively create a {@link DocumentSymbol} from a {@link KsonValue}
     */
    private createSymbol(name: string, value?: KsonValue, parent?: DocumentSymbolWithParent): DocumentSymbolWithParent | null {
        if(!value){
            return null
        }

        const range = this.ksonValueToRange(value);
        switch (value.type){
            case KsonValueType.OBJECT:
                return this.createObjectSymbol(value as KsonValue.KsonObject, name, range, parent);
            case KsonValueType.ARRAY:
                return this.createArraySymbol(value as KsonValue.KsonArray, name, range, parent);
            case KsonValueType.EMBED:
                return this.createEmbedSymbol(value as KsonValue.KsonEmbed, name, range, parent);
            case KsonValueType.STRING:
                return this.createPrimitiveSymbol(name, range, SymbolKind.String, (value as KsonValue.KsonString).value, parent);
            case KsonValueType.INTEGER:
                return this.createPrimitiveSymbol(name, range, SymbolKind.Number,
                    (value as KsonValue.KsonNumber.Integer).value.toString(), parent);
            case KsonValueType.DECIMAL:
                return this.createPrimitiveSymbol(name, range, SymbolKind.Number,
                    (value as KsonValue.KsonNumber.Decimal).value.toString(), parent);
            case KsonValueType.BOOLEAN:
                return this.createPrimitiveSymbol(name, range, SymbolKind.Boolean,
                    (value as KsonValue.KsonBoolean).value.toString(), parent);
            case KsonValueType.NULL:
                return this.createPrimitiveSymbol(name, range, SymbolKind.Null,
                    'null', parent);
        }
    }
    
    /**
     * Create a DocumentSymbol for an object
     */
    private createObjectSymbol(obj: KsonValue.KsonObject, name: string, range: Range, parent?: DocumentSymbolWithParent): DocumentSymbolWithParent {
        const propertyMap = obj.properties.asJsReadonlyMapView();

        const symbol = new DocumentSymbolWithParent(
            name,
            SymbolKind.Object,
            range,
            `{${propertyMap.size} properties}`,
            range,
            parent
        );

        for (const [key, value] of propertyMap) {
            const childSymbol = this.createObjectPropertySymbol(key, value, symbol);

            if (childSymbol) {
                symbol.children!.push(childSymbol);
            }
        }

        return symbol;
    }

    private createObjectPropertySymbol(objectKey: KsonValue.KsonString, objectValue: KsonValue, parent?: DocumentSymbolWithParent): DocumentSymbolWithParent {
        // Get the range for the entire property (use value range for now)

        const keyRange = this.ksonValueToRange(objectKey)
        const propertyKeySymbol = new DocumentSymbolWithParent(
            objectKey.value,
            SymbolKind.Key,
            keyRange,
            "key",
            keyRange,
            parent
        )
        
        const objectPropertyValue = this.createSymbol(objectKey.value, objectValue, propertyKeySymbol);
        if (objectPropertyValue) {
            propertyKeySymbol.children!.push(objectPropertyValue);
        }
        
        return propertyKeySymbol;
    }
    
    /**
     * Create a DocumentSymbol for an array
     */
    private createArraySymbol(array: KsonValue.KsonArray, name: string, range: Range, parent?: DocumentSymbolWithParent): DocumentSymbolWithParent {
        const elements = array.elements.asJsReadonlyArrayView();

        const symbol = new DocumentSymbolWithParent(
            name,
            SymbolKind.Array,
            range,
            `[${elements.length} items]`,
            range,
            parent
        );

        elements.forEach((element, index) => {
            const childSymbol = this.createSymbol(`[${index}]`, element, symbol);
            if (childSymbol) {
                symbol.children!.push(childSymbol);
            }
        });

        return symbol;
    }
    
    /**
     * Create a DocumentSymbol for primitive values
     */
    private createPrimitiveSymbol(
        name: string, 
        range: Range, 
        kind: SymbolKind, 
        detail: string,
        parent?: DocumentSymbolWithParent
    ): DocumentSymbolWithParent {
        return new DocumentSymbolWithParent(
            name,
            kind,
            range,
            detail,
            range,
            parent
        );
    }
    
    /**
     * Create a DocumentSymbol for an embed block
     */
    private createEmbedSymbol(embed: KsonValue.KsonEmbed, name: string, range: Range, parent?: DocumentSymbolWithParent): DocumentSymbolWithParent {
        const tag = embed.tag || 'embed';
        const detail = embed.metadata ? `<<<${tag} ${embed.metadata}>>>` : `<<<${tag}>>>`;

        return new DocumentSymbolWithParent(
            name,
            SymbolKind.Module,
            range,
            detail,
            range,
            parent
        );
    }

    /**
     * Convert a KsonValue to an LSP Range
     */
    private ksonValueToRange(value: KsonValue): Range {
        return {
            start: {
                line: value.start.line,
                character: value.start.column
            },
            end: {
                line: value.end.line,
                character: value.end.column
            }
        };
    }

}