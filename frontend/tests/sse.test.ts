import { test } from 'node:test';
import assert from 'node:assert/strict';
import { SseParser, type SseMessage } from '../src/app/services/sse.ts';
test('Se reconstruyen eventos y UTF-8 divididos en fragmentos de un byte', () => {
  const events: SseMessage[] = [];
  const parser = new SseParser((event) => events.push(event));
  const bytes = new TextEncoder().encode(
    'event:node\r\ndata:{"node":"revisión","state":{"status":"AWAITING_APPROVAL"}}\r\n\r\nevent:result\r\ndata:{"state":{"status":"CAPTURED"}}\r\n\r\n',
  );
  for (const byte of bytes) parser.push(new Uint8Array([byte]));
  parser.push(undefined, true);
  assert.equal(events.length, 2);
  assert.equal(events[0]?.event, 'node');
  assert.equal((events[0]?.data as { node: string }).node, 'revisión');
  assert.equal(events[1]?.event, 'result');
});
test('Se ignoran heartbeats y se unen líneas data', () => {
  const events: SseMessage[] = [];
  const parser = new SseParser((event) => events.push(event));
  parser.push(
    new TextEncoder().encode(
      ': heartbeat\n\nevent:node\ndata:{"node":\ndata:"capture"}\n\n',
    ),
    true,
  );
  assert.deepEqual(events, [{ event: 'node', data: { node: 'capture' } }]);
});
test('Se informa JSON inválido y se procesa el último evento sin separador', () => {
  const events: SseMessage[] = [];
  const parser = new SseParser((event) => events.push(event));
  parser.push(new TextEncoder().encode('event:result\ndata:{"ok":true}'), true);
  assert.equal(events.length, 1);
  assert.throws(
    () =>
      new SseParser(() => {}).push(
        new TextEncoder().encode('data:{invalid}\n\n'),
      ),
    SyntaxError,
  );
});
