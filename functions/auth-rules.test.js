import test from 'node:test';
import assert from 'node:assert/strict';

test('email verification, password reset, linked identity and goal ownership', {
  skip: !process.env.FIREBASE_AUTH_EMULATOR_HOST || !process.env.FIRESTORE_EMULATOR_HOST
}, async () => {
  for (const host of [process.env.FIREBASE_AUTH_EMULATOR_HOST, process.env.FIRESTORE_EMULATOR_HOST]) {
    assert.match(host, /^(127\.0\.0\.1|localhost):\d+$/);
  }
  assert.equal(process.env.GCLOUD_PROJECT, 'demo-roomdns');
  const authURL = `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}`;
  const dbURL = `http://${process.env.FIRESTORE_EMULATOR_HOST}/v1/projects/demo-roomdns/databases/(default)/documents`;
  async function auth(method, data, status = 200) {
    const r = await fetch(`${authURL}/identitytoolkit.googleapis.com/v1/accounts:${method}?key=emulator-only`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data)
    });
    const result = await r.json();
    assert.equal(r.status, status, `${method}: ${result.error?.message ?? r.status}`);
    return result;
  }
  async function goal(user, uid = user.localId, status = 200, goal = 'work') {
    const r = await fetch(`${dbURL}:commit`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${user.idToken}` },
      body: JSON.stringify({ writes: [{
        update: { name: `projects/demo-roomdns/databases/(default)/documents/users/${uid}`, fields: { goal: { stringValue: goal } } },
        updateTransforms: [{ fieldPath: 'updatedAt', setToServerValue: 'REQUEST_TIME' }]
      }] })
    });
    assert.equal(r.status, status, `goal write expected ${status}: ${await r.text()}`);
  }
  async function code(email, requestType) {
    const r = await fetch(`${authURL}/emulator/v1/projects/demo-roomdns/oobCodes`);
    const { oobCodes } = await r.json();
    return oobCodes.findLast(c => c.email === email && c.requestType === requestType).oobCode;
  }
  const email = `auth-${Date.now()}@example.test`, password = 'Emulator-only-password1!';
  let user = await auth('signUp', { email, password, returnSecureToken: true });
  await goal(user, user.localId, 403); // Must fail before verification, even outside the app.
  await auth('sendOobCode', { requestType: 'VERIFY_EMAIL', idToken: user.idToken });
  await auth('update', { oobCode: await code(email, 'VERIFY_EMAIL') });
  user = await auth('signInWithPassword', { email, password, returnSecureToken: true });
  await goal(user);
  await goal(user, 'someone-else', 403);
  await goal(user, user.localId, 403, 'invalid-goal');
  await auth('sendOobCode', { requestType: 'PASSWORD_RESET', email });
  const newPassword = `${password}2`;
  await auth('resetPassword', { oobCode: await code(email, 'PASSWORD_RESET'), newPassword });
  await auth('signInWithPassword', { email, password, returnSecureToken: true }, 400);
  user = await auth('signInWithPassword', { email, password: newPassword, returnSecureToken: true });
  const uid = user.localId;
  for (const providerId of ['google.com', 'apple.com']) {
    // Unsigned provider tokens are accepted only by the local Auth emulator.
    const idToken = ['{"alg":"none"}', JSON.stringify({ sub: `${providerId}-${uid}`, email, email_verified: true })]
      .map(s => Buffer.from(s).toString('base64url')).join('.') + '.';
    user = await auth('signInWithIdp', {
      idToken: user.idToken, postBody: new URLSearchParams({ providerId, id_token: idToken }).toString(),
      requestUri: 'http://localhost', returnSecureToken: true
    });
    assert.equal(user.localId, uid, 'explicit linking must preserve UID');
    await goal(user);
  }
  const { initializeApp, deleteApp } = await import('firebase-admin/app');
  const { getAuth } = await import('firebase-admin/auth');
  const app = initializeApp({ projectId: 'demo-roomdns' }, 'auth-rules');
  try {
    await getAuth(app).updateUser(uid, { emailVerified: false });
    user = await auth('signInWithPassword', { email, password: newPassword, returnSecureToken: true });
    assert.equal(JSON.parse(Buffer.from(user.idToken.split('.')[1], 'base64url')).email_verified, false);
    await goal(user); // Linked Apple/Google remains a trusted identity.
    await getAuth(app).updateUser(uid, { providersToUnlink: ['google.com', 'apple.com'] });
    user = await auth('signInWithPassword', { email, password: newPassword, returnSecureToken: true });
    await goal(user, uid, 403);
    const read = await fetch(`${dbURL}/users/${uid}`, { headers: { Authorization: `Bearer ${user.idToken}` } });
    assert.equal(read.status, 200, 'unverified owners retain read/delete access');
  } finally {
    await deleteApp(app);
  }
  const r = await fetch(`${dbURL}/users/${uid}`, { method: 'DELETE', headers: { Authorization: `Bearer ${user.idToken}` } });
  assert.equal(r.status, 200);
  await auth('delete', { idToken: user.idToken });
  await auth('signInWithPassword', { email, password: newPassword, returnSecureToken: true }, 400);
});
