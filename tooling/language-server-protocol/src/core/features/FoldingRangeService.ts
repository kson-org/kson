import {FoldingRange, FoldingRangeKind} from 'vscode-languageserver';
import {KsonTooling} from 'kson-tooling';

/**
 * Service responsible for providing folding ranges for KSON documents.
 * Delegates to Kotlin's KsonTooling for structural range detection.
 */
export class FoldingRangeService {

    getFoldingRanges(content: string): FoldingRange[] {
        return KsonTooling.getInstance().getStructuralRanges(content).asJsReadonlyArrayView().map(sr => ({
            startLine: sr.startLine,
            endLine: sr.endLine,
            kind: FoldingRangeKind.Region
        }));
    }
}
