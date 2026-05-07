import {describe, it} from 'mocha';
import assert from 'assert';
import {
    CommandType,
    fromWireCommandId,
    getAllCommandIds,
    toWireCommandId,
} from '../../../core/commands/CommandType.js';

describe('CommandType wire-prefix translation', () => {
    it('toWireCommandId / fromWireCommandId round-trip every CommandType under any distribution id', () => {
        for (const id of ['kson', 'config', 'a.b']) {
            for (const cmd of getAllCommandIds()) {
                const wire = toWireCommandId(cmd, id);
                assert.strictEqual(wire, `${id}.${cmd}`);
                assert.strictEqual(fromWireCommandId(wire, id), cmd);
            }
        }
    });

    it('fromWireCommandId returns undefined for unknown ids or wrong distribution id', () => {
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
