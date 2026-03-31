import {Position, Range, SelectionRange} from 'vscode-languageserver';
import {KsonTooling, ToolingDocument, Range as KtRange} from 'kson-tooling';

/**
 * Service responsible for providing selection ranges (smart expand/shrink selection)
 * for KSON documents. Delegates to Kotlin's KsonTooling for AST walking.
 */
export class SelectionRangeService {

    getSelectionRanges(document: ToolingDocument, positions: Position[]): SelectionRange[] {
        const tooling = KsonTooling.getInstance();

        return positions.map(position => {
            const ktRanges = tooling.getEnclosingRanges(document, position.line, position.character)
                .asJsReadonlyArrayView();

            const ancestors: Range[] = ktRanges.map(r => toRange(r));

            return buildChain(ancestors);
        });
    }
}

function toRange(r: KtRange): Range {
    return {
        start: {line: r.startLine, character: r.startColumn},
        end: {line: r.endLine, character: r.endColumn}
    };
}

function buildChain(ranges: Range[]): SelectionRange {
    // ranges[0] = innermost, ranges[last] = outermost
    // Build linked list from outermost to innermost
    let current: SelectionRange = {range: ranges[ranges.length - 1]};
    for (let i = ranges.length - 2; i >= 0; i--) {
        current = {range: ranges[i], parent: current};
    }
    return current;
}
