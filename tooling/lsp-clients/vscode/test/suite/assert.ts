// Browser-compatible assert implementation
export const assert = {
    strictEqual<T>(actual: T, expected: T, message?: string): void {
        if (actual !== expected) {
            throw new Error(message || `Expected ${JSON.stringify(expected)} but got ${JSON.stringify(actual)}`);
        }
    },
    
    ok(value: any, message?: string): void {
        if (!value) {
            throw new Error(message || `Expected truthy value but got ${JSON.stringify(value)}`);
        }
    },
    
    fail(message?: string): void {
        throw new Error(message || 'Assertion failed');
    },
    
    deepStrictEqual<T>(actual: T, expected: T, message?: string): void {
        const actualStr = JSON.stringify(actual);
        const expectedStr = JSON.stringify(expected);
        if (actualStr !== expectedStr) {
            throw new Error(message || `Expected ${expectedStr} but got ${actualStr}`);
        }
    }
};