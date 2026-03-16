import {FoldingRange, FoldingRangeKind} from 'vscode-languageserver';
import {KsonTooling, ToolingDocument} from 'kson-tooling';

/**
 * Service responsible for providing folding ranges for KSON documents.
 * Delegates to Kotlin's KsonTooling for structural range detection.
 */
export class FoldingRangeService {

    getFoldingRanges(document: ToolingDocument): FoldingRange[] {
        return KsonTooling.getInstance().getStructuralRanges(document).asJsReadonlyArrayView().map(sr => ({
            startLine: sr.startLine,
            endLine: sr.endLine,
            kind: FoldingRangeKind.Region
        }));
    }
}
