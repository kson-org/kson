import {describe, it} from 'mocha';
import assert from 'assert';
import {
    CommandType,
    DEFAULT_CONFIG_NAMESPACE,
    fromWireCommandId,
    getAllCommandIds,
    toWireCommandId,
} from '../../../core/commands/CommandType.js';

describe('CommandType wire-namespace translation', () => {
    it('toWireCommandId / fromWireCommandId round-trip every CommandType under any namespace', () => {
        for (const ns of [DEFAULT_CONFIG_NAMESPACE, 'config', 'a.b']) {
            for (const id of getAllCommandIds()) {
                const wire = toWireCommandId(id, ns);
                assert.strictEqual(wire, `${ns}.${id}`);
                assert.strictEqual(fromWireCommandId(wire, ns), id);
            }
        }
    });

    it('fromWireCommandId returns undefined for unknown ids or wrong namespace', () => {
        assert.strictEqual(fromWireCommandId('config.bogus', 'config'), undefined);
        assert.strictEqual(fromWireCommandId('other.plainFormat', 'config'), undefined);
        assert.strictEqual(fromWireCommandId('plainFormat', 'config'), undefined);
    });

    it('CommandType values are unqualified (no dot)', () => {
        for (const id of getAllCommandIds()) {
            assert.ok(!id.includes('.'), `CommandType value '${id}' must not contain a dot`);
        }
        assert.strictEqual(CommandType.PLAIN_FORMAT, 'plainFormat');
    });
});
