import {FormattingStyle} from 'kson';

/**
 * Wire/display id for a formatting style: the Kotlin enum's stable name lowercased
 * ("plain", "delimited", ...), rather than its minified internal fields. Derived
 * directly from {@link FormattingStyle} so it can never drift from the enum's own
 * set of names, and matches the lowercase values the VS Code config surfaces.
 */
export type FormattingStyleId = Lowercase<FormattingStyle['name']>;

/**
 * The wire/display id for a {@link FormattingStyle} — its lowercased enum name.
 */
export function formattingStyleId(style: FormattingStyle): FormattingStyleId {
    return style.name.toLowerCase() as FormattingStyleId;
}

/**
 * Resolve a style id back to the {@link FormattingStyle} enum via its canonical
 * {@link FormattingStyle.valueOf}. Case-insensitive and defaults to
 * {@link FormattingStyle.PLAIN} for anything absent or unrecognized.
 */
export function formattingStyleFromId(id?: string): FormattingStyle {
    if (!id) {
        return FormattingStyle.PLAIN;
    }
    try {
        return FormattingStyle.valueOf(id.toUpperCase());
    } catch {
        return FormattingStyle.PLAIN;
    }
}
