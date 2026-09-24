import test from 'node:test';
import assert from 'node:assert/strict';
import { chargePass, deleteCustomer, spendRequest } from './purchases.js';

test('unlock trust boundary, stable retries, ledger failures and deletion retries', async () => {
  const data = { sessionID: 'a'.repeat(64) };
  assert.throws(() => spendRequest(undefined, data), { code: 'unauthenticated' });
  for (const bad of [null, [], {}, { ...data, uid: 'other' }, { ...data, amount: 100 },
    { ...data, currency: 'PASS' }, { sessionID: '../another-session' }]) {
    assert.throws(() => spendRequest('alice', bad), { code: 'invalid-argument' });
  }
  const first = spendRequest('alice', data);
  assert.deepEqual(first, spendRequest('alice', data));
  assert.notEqual(first.key, spendRequest('bob', data).key);
  assert.notEqual(first.key, spendRequest('alice', { sessionID: 'b'.repeat(64) }).key);

  const options = { uid: 'alice', key: first.key, secret: 'test-secret', project: 'test-project' };
  let calls = 0;
  const seen = new Set();
  let balance = 2;
  const ledger = async (url, request) => {
    calls++;
    assert.equal(url, 'https://api.revenuecat.com/v2/projects/test-project/customers/alice/virtual_currencies/transactions');
    assert.equal(request.headers.Authorization, 'Bearer test-secret');
    assert.equal(request.method, 'POST');
    assert.deepEqual(JSON.parse(request.body), { adjustments: { PASS: -1 } });
    const key = request.headers['Idempotency-Key'];
    if (!seen.has(key)) { seen.add(key); balance--; }
    // Simulate a committed charge whose response was lost. The same key must be
    // sent for the retry, regardless of whether the first response arrived.
    if (calls === 1) throw new Error('connection lost after commit');
    return new Response('{}', { status: 200 });
  };
  await assert.rejects(chargePass({ ...options, fetchImpl: ledger }));
  await Promise.all([chargePass({ ...options, fetchImpl: ledger }), chargePass({ ...options, fetchImpl: ledger })]);
  assert.equal(balance, 1);
  for (const status of [401, 403, 409, 422, 429, 500, 503]) {
    await assert.rejects(chargePass({ ...options, fetchImpl: async () => new Response('{}', { status }) }),
      { code: status === 422 ? 'failed-precondition' : 'unavailable' });
  }
  for (const status of [200, 202, 404]) {
    await deleteCustomer({ ...options, fetchImpl: async (url, request) => {
      assert.equal(request.method, 'DELETE');
      assert.ok(url.endsWith('/customers/alice'));
      return new Response('{}', { status });
    } });
  }
  await assert.rejects(deleteCustomer({ ...options, fetchImpl: async () => new Response('{}', { status: 503 }) }));
});
