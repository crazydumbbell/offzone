import { initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';
import { onCall, HttpsError } from 'firebase-functions/v2/https';
import { defineSecret } from 'firebase-functions/params';
import * as functionsV1 from 'firebase-functions/v1';
import { chargePass, deleteCustomer, spendRequest } from './purchases.js';

initializeApp();
const db = getFirestore();
const secret = defineSecret('REVENUECAT_SECRET_KEY');
const project = 'projb8a02e7b';

export const spendUnlockPass = onCall({
  region: 'us-central1', secrets: [secret], timeoutSeconds: 30,
  maxInstances: 3, concurrency: 10, memory: '256MiB'
}, async request => {
  try {
    const uid = request.auth?.uid;
    const { key, sessionID } = spendRequest(uid, request.data);
    // Callable authentication verifies the token; also reject deleted/disabled
    // accounts whose otherwise-valid token has not expired yet.
    const user = await getAuth().getUser(uid);
    if (user.disabled) throw new HttpsError('unauthenticated', 'Sign in again.');
    const account = db.collection('purchaseAccounts').doc(uid);
    const receipt = account.collection('spends').doc(key);
    const state = await db.runTransaction(async tx => {
      const [owner, existing] = await Promise.all([tx.get(account), tx.get(receipt)]);
      if (owner.get('deleted')) throw new HttpsError('unauthenticated', 'Account deleted.');
      if (existing.exists) return existing.data();
      const initial = { sessionID, status: 'pending', createdAt: Date.now() };
      tx.set(receipt, initial);
      return initial;
    });
    if (state.status === 'cancelled') return { sessionID, spent: false };
    if (!['pending', 'spent'].includes(state.status)) {
      throw new HttpsError('unavailable', 'The pass result needs review.');
    }
    if (state.status !== 'spent') {
      // RevenueCat documents at-most-once execution for the same idempotency key.
      // Pending requests always reuse that key, including after process death.
      await chargePass({ uid, key, secret: secret.value(), project });
      await receipt.update({ status: 'spent', completedAt: Date.now() });
    }
    return { sessionID, spent: true };
  } catch (error) {
    if (error instanceof HttpsError) throw error;
    const code = ['unauthenticated', 'invalid-argument', 'failed-precondition'].includes(error.code)
      ? error.code : 'unavailable';
    throw new HttpsError(code, code === 'failed-precondition'
      ? 'No unlock pass is available. Refresh your balance.' : 'Please retry the same request.');
  }
});

// Never replay a debit for a session the client can no longer release. A missing
// receipt is cancelled transactionally so a delayed spend cannot debit it later.
// Existing pending receipts stay uncertain; only a trusted ledger result may
// resolve them. Client claims about session expiry cannot authorize a refund.
export const reconcileUnlockPass = onCall({
  region: 'us-central1', timeoutSeconds: 30,
  maxInstances: 3, concurrency: 10, memory: '256MiB'
}, async request => {
  try {
    const uid = request.auth?.uid;
    const { key, sessionID } = spendRequest(uid, request.data);
    const user = await getAuth().getUser(uid);
    if (user.disabled) throw new HttpsError('unauthenticated', 'Sign in again.');
    const account = db.collection('purchaseAccounts').doc(uid);
    const receipt = account.collection('spends').doc(key);
    const status = await db.runTransaction(async tx => {
      const [owner, existing] = await Promise.all([tx.get(account), tx.get(receipt)]);
      if (owner.get('deleted')) throw new HttpsError('unauthenticated', 'Account deleted.');
      if (!existing.exists) {
        tx.set(receipt, { sessionID, status: 'cancelled', createdAt: Date.now() });
        return 'cancelled';
      }
      const status = existing.get('status');
      if (!['cancelled', 'pending', 'spent'].includes(status)) {
        throw new HttpsError('unavailable', 'The pass result needs review.');
      }
      if (status !== 'cancelled') tx.update(receipt, { reviewRequestedAt: Date.now() });
      return status;
    });
    return { sessionID, status };
  } catch (error) {
    if (error instanceof HttpsError) throw error;
    const code = ['unauthenticated', 'invalid-argument'].includes(error.code) ? error.code : 'unavailable';
    throw new HttpsError(code, 'Could not check the previous pass. Please retry.');
  }
});

// Auth deletion is supported by the first-generation trigger. Retry failures;
// never put a RevenueCat secret in the iOS client.
export const deleteRevenueCatCustomer = functionsV1.region('us-central1')
  .runWith({ secrets: ['REVENUECAT_SECRET_KEY'], failurePolicy: true, maxInstances: 2 })
  .auth.user().onDelete(async user => {
    const account = db.collection('purchaseAccounts').doc(user.uid);
    await account.set({ deleted: true });
    await deleteCustomer({ uid: user.uid, secret: process.env.REVENUECAT_SECRET_KEY, project });
    // Preserve the tombstone so late requests cannot create another customer.
    await db.recursiveDelete(account.collection('spends'));
  });
