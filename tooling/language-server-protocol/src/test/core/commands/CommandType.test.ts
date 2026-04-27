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
    it('toWireCommandId prefixes every CommandType under the default namespace', () => {
        for (const id of getAllCommandIds()) {
            assert.strictEqual(toWireCommandId(id, DEFAULT_CONFIG_NAMESPACE), `kson.${id}`);
        }
    });

    it('toWireCommandId prefixes every CommandType under a non-default namespace', () => {
        for (const id of getAllCommandIds()) {
            assert.strictEqual(toWireCommandId(id, 'config'), `config.${id}`);
        }
    });

    it('fromWireCommandId round-trips every CommandType under any namespace', () => {
        for (const ns of [DEFAULT_CONFIG_NAMESPACE, 'config']) {
            for (const id of getAllCommandIds()) {
                assert.strictEqual(fromWireCommandId(toWireCommandId(id, ns), ns), id);
            }
        }
    });

    it('fromWireCommandId returns undefined for an unknown id under the active namespace', () => {
        assert.strictEqual(fromWireCommandId('config.bogus', 'config'), undefined);
    });

    it('fromWireCommandId returns undefined for an id outside the active namespace', () => {
        assert.strictEqual(fromWireCommandId('other.plainFormat', 'config'), undefined);
        assert.strictEqual(fromWireCommandId('plainFormat', 'config'), undefined);
    });

    it('CommandType values are unqualified (no dot)', () => {
        for (const id of getAllCommandIds()) {
            assert.ok(!id.includes('.'), `CommandType value '${id}' must not contain a dot`);
        }
        assert.strictEqual(CommandType.PLAIN_FORMAT, 'plainFormat');
    });

    it('fromWireCommandId round-trips under a dotted namespace', () => {
        for (const id of getAllCommandIds()) {
            assert.strictEqual(fromWireCommandId(toWireCommandId(id, 'a.b'), 'a.b'), id);
        }
    });
});
