import test from 'node:test';
import assert from 'node:assert/strict';

test('Firestore receipts survive concurrent retries, response loss and account deletion', {
  skip: !process.env.FIRESTORE_EMULATOR_HOST
}, async () => {
  assert.match(process.env.FIRESTORE_EMULATOR_HOST, /^(127\.0\.0\.1|localhost):\d+$/);
  assert.equal(process.env.GCLOUD_PROJECT, 'demo-roomdns');
  process.env.REVENUECAT_SECRET_KEY = 'emulator-only';
  const { spendUnlockPass, reconcileUnlockPass, deleteRevenueCatCustomer } = await import('./index.js');
  const { getAuth } = await import('firebase-admin/auth');
  const { getFirestore } = await import('firebase-admin/firestore');
  const { spendRequest } = await import('./purchases.js');
  const auth = getAuth();
  const originalUser = auth.getUser;
  const originalFetch = globalThis.fetch;
  const uid = `emulator-${Date.now()}`;
  const data = { sessionID: 'c'.repeat(64) };
  const request = { auth: { uid }, data };
  let calls = 0, charges = 0, loseResponse = true, failDelete = true;
  const keys = new Set();
  auth.getUser = async id => ({ uid: id, disabled: id === 'disabled' });
  globalThis.fetch = async (url, init) => {
    assert.match(url, /^https:\/\/api\.revenuecat\.com\/v2\/projects\/projb8a02e7b\/customers\//);
    if (init.method === 'DELETE') {
      if (failDelete) return new Response('{}', { status: 503 });
      return new Response('{}', { status: 202 });
    }
    calls++;
    const key = init.headers['Idempotency-Key'];
    if (!keys.has(key)) { keys.add(key); charges++; }
    if (loseResponse) { loseResponse = false; throw new Error('lost response'); }
    return new Response('{}', { status: 200 });
  };
  try {
    await assert.rejects(spendUnlockPass.run({ data }), { code: 'unauthenticated' });
    await assert.rejects(spendUnlockPass.run({ auth: { uid: 'disabled' }, data }), { code: 'unauthenticated' });
    await assert.rejects(spendUnlockPass.run({ ...request, data: { ...data, uid: 'other' } }), { code: 'invalid-argument' });
    assert.equal(calls, 0);
    await assert.rejects(spendUnlockPass.run(request), { code: 'unavailable' });
    const beforeReconcile = calls;
    assert.deepEqual(await reconcileUnlockPass.run(request), { sessionID: data.sessionID, status: 'pending' });
    assert.equal(calls, beforeReconcile, 'uncertain reconciliation must never issue another debit');
    const results = await Promise.all([spendUnlockPass.run(request), spendUnlockPass.run(request)]);
    assert.deepEqual(results, [{ sessionID: data.sessionID, spent: true }, { sessionID: data.sessionID, spent: true }]);
    assert.equal(charges, 1);
    const beforeReplay = calls;
    await spendUnlockPass.run(request);
    assert.equal(calls, beforeReplay);
    const account = getFirestore().collection('purchaseAccounts').doc(uid);
    const receipt = account.collection('spends').doc(spendRequest(uid, data).key);
    assert.equal((await receipt.get()).get('status'), 'spent');
    assert.deepEqual(await reconcileUnlockPass.run(request), { sessionID: data.sessionID, status: 'spent' });
    assert.equal(calls, beforeReplay, 'confirmed reconciliation must never replay a debit');
    assert.equal(typeof (await receipt.get()).get('reviewRequestedAt'), 'number');

    const neverSent = { ...request, data: { sessionID: 'd'.repeat(64) } };
    assert.deepEqual(await reconcileUnlockPass.run(neverSent), { sessionID: neverSent.data.sessionID, status: 'cancelled' });
    assert.deepEqual(await spendUnlockPass.run(neverSent), { sessionID: neverSent.data.sessionID, spent: false });
    assert.equal(calls, beforeReplay, 'late delivery of a cancelled request must not debit');

    const raced = { ...request, data: { sessionID: 'e'.repeat(64) } };
    const [resolved, spent] = await Promise.all([
      reconcileUnlockPass.run(raced), spendUnlockPass.run(raced)
    ]);
    if (resolved.status === 'cancelled') {
      assert.equal(spent.spent, false);
      assert.equal(calls, beforeReplay);
    } else {
      assert.ok(['pending', 'spent'].includes(resolved.status));
      assert.equal(spent.spent, true);
      assert.deepEqual(await reconcileUnlockPass.run(raced), { sessionID: raced.data.sessionID, status: 'spent' });
    }
    await assert.rejects(reconcileUnlockPass.run({ data }), { code: 'unauthenticated' });
    await assert.rejects(reconcileUnlockPass.run({ auth: { uid: 'disabled' }, data }), { code: 'unauthenticated' });
    await assert.rejects(reconcileUnlockPass.run({ ...request, data: { ...data, uid: 'other' } }), { code: 'invalid-argument' });
    const corrupt = { ...request, data: { sessionID: 'f'.repeat(64) } };
    await account.collection('spends').doc(spendRequest(uid, corrupt.data).key)
      .set({ sessionID: corrupt.data.sessionID, status: 'unknown' });
    const beforeCorrupt = calls;
    await assert.rejects(spendUnlockPass.run(corrupt), { code: 'unavailable' });
    await assert.rejects(reconcileUnlockPass.run(corrupt), { code: 'unavailable' });
    assert.equal(calls, beforeCorrupt, 'unknown receipt states must fail closed without a debit');
    await assert.rejects(deleteRevenueCatCustomer.run({ uid }, {}));
    assert.equal((await account.get()).get('deleted'), true);
    await assert.rejects(spendUnlockPass.run(request), { code: 'unauthenticated' });
    await assert.rejects(reconcileUnlockPass.run(request), { code: 'unauthenticated' });
    failDelete = false;
    await deleteRevenueCatCustomer.run({ uid }, {});
    assert.equal((await receipt.get()).exists, false);
    assert.equal((await account.get()).get('deleted'), true);
    await getFirestore().recursiveDelete(account);
  } finally {
    auth.getUser = originalUser;
    globalThis.fetch = originalFetch;
    await getFirestore().terminate();
  }
});
