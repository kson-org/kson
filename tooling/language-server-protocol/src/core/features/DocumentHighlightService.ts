import {
    DocumentHighlight,
    DocumentHighlightKind,
    Position,
} from 'vscode-languageserver';
import {KsonTooling} from 'kson-tooling';

/**
 * Service responsible for providing document highlights for KSON documents.
 * Delegates to Kotlin's KsonTooling for sibling key lookup.
 */
export class DocumentHighlightService {

    getDocumentHighlights(
        content: string,
        position: Position
    ): DocumentHighlight[] {
        const ranges = KsonTooling.getInstance()
            .getSiblingKeys(content, position.line, position.character)
            .asJsReadonlyArrayView();

        return ranges.map(r => ({
            range: {
                start: {line: r.startLine, character: r.startColumn},
                end: {line: r.endLine, character: r.endColumn}
            },
            kind: DocumentHighlightKind.Read
        }));
    }
}
