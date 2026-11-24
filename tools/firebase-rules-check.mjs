// Run against the local Firestore emulator (no external packages required):
// firebase emulators:exec --project demo-roomdns --config firebase.auth-test.json --only firestore 'node tools/firebase-rules-check.mjs'
import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const emulatorHost = process.env.FIRESTORE_EMULATOR_HOST;
if (!emulatorHost) {
  throw new Error('Local Firestore emulator required; refusing to run without FIRESTORE_EMULATOR_HOST.');
}
const [host, port] = emulatorHost.split(':');
if (!['127.0.0.1', 'localhost', '::1'].includes(host) || !/^\d+$/.test(port ?? '')) {
  throw new Error(`Only a loopback emulator host:port is allowed (got ${emulatorHost}).`);
}

const projectId = process.env.GCLOUD_PROJECT || 'demo-roomdns';
if (!projectId.startsWith('demo-')) {
  throw new Error(`Refusing to run against non-demo project '${projectId}'.`);
}

const baseURL = `http://${emulatorHost}`;
const docsRoot = `projects/${projectId}/databases/(default)/documents`;
const dbURL = `${baseURL}/v1/${docsRoot}`;
const rulesPath = fileURLToPath(new URL('../firestore.rules', import.meta.url));
const rules = await readFile(rulesPath, 'utf8');

function fakeToken({ uid, emailVerified = false, providers = ['password'] }) {
  const now = Math.floor(Date.now() / 1000);
  const identities = Object.fromEntries(providers.map(p => [p, [`${p}-${uid}`]]));
  const payload = {
    iss: `https://securetoken.google.com/${projectId}`,
    aud: projectId,
    auth_time: now,
    user_id: uid,
    sub: uid,
    iat: now,
    exp: now + 3600,
    email: `${uid}@example.test`,
    email_verified: emailVerified,
    firebase: { identities, sign_in_provider: providers[0] ?? 'password' },
  };
  const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url');
  const body = Buffer.from(JSON.stringify(payload)).toString('base64url');
  return `${header}.${body}.`;
}

function authHeaders(token) {
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function clearFirestore() {
  const res = await fetch(`${baseURL}/emulator/v1/projects/${projectId}/databases/(default)/documents`, {
    method: 'DELETE',
  });
  if (!res.ok) throw new Error(`Failed to clear Firestore emulator: ${res.status} ${await res.text()}`);
}

async function commit(token, write) {
  return fetch(`${dbURL}:commit`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
    body: JSON.stringify({ writes: [write] }),
  });
}

function goalWrite(path, fields, withServerTime = true) {
  const write = { update: { name: `${docsRoot}/${path}`, fields } };
  if (withServerTime) {
    write.updateTransforms = [{ fieldPath: 'updatedAt', setToServerValue: 'REQUEST_TIME' }];
  }
  return write;
}

function docGet(token, path) {
  return fetch(`${dbURL}/${path}`, { headers: authHeaders(token) });
}

function docDelete(token, path) {
  return fetch(`${dbURL}/${path}`, { method: 'DELETE', headers: authHeaders(token) });
}

let passed = 0;
const checks = [];
async function check(name, action, allowed = false) {
  const res = await action();
  const ok = allowed ? res.status === 200 : res.status === 403;
  if (!ok) {
    throw new Error(`FAIL ${name}: expected ${allowed ? 200 : 403}, got ${res.status} (${await res.text()})`);
  }
  passed++;
  checks.push(name);
  console.log(`PASS ${name}`);
}

const verifiedOwner = fakeToken({ uid: 'owner', emailVerified: true });
const unverifiedOwner = fakeToken({ uid: 'owner', emailVerified: false });
const googleOwner = fakeToken({ uid: 'owner', emailVerified: false, providers: ['google.com'] });
const appleOwner = fakeToken({ uid: 'owner', emailVerified: false, providers: ['apple.com'] });
const stranger = fakeToken({ uid: 'stranger', emailVerified: true });
const anonymous = null;
const adminToken = 'owner'; // Firestore emulator bypass token for seeding server-only data.

await clearFirestore();

await check('unverified email owner cannot create profile', () =>
  commit(unverifiedOwner, goalWrite('users/owner', { goal: { stringValue: 'work' } })), false);
await check('verified email owner creates valid profile with server timestamp', () =>
  commit(verifiedOwner, goalWrite('users/owner', { goal: { stringValue: 'work' } })), true);
await check('owner reads profile', () => docGet(verifiedOwner, 'users/owner'), true);
await check('unverified owner retains read access', () => docGet(unverifiedOwner, 'users/owner'), true);

for (const goal of ['rest', 'presence', 'personal']) {
  await check(`verified owner updates goal to ${goal}`, () =>
    commit(verifiedOwner, goalWrite('users/owner', { goal: { stringValue: goal } })), true);
}
await check('linked google.com owner updates goal even with email_verified=false', () =>
  commit(googleOwner, goalWrite('users/owner', { goal: { stringValue: 'work' } })), true);
await check('linked apple.com owner updates goal even with email_verified=false', () =>
  commit(appleOwner, goalWrite('users/owner', { goal: { stringValue: 'rest' } })), true);
await check('unverified email owner cannot update profile', () =>
  commit(unverifiedOwner, goalWrite('users/owner', { goal: { stringValue: 'presence' } })), false);

for (const [name, token] of [['unauthenticated', anonymous], ['another UID', stranger]]) {
  await check(`${name} cannot read`, () => docGet(token, 'users/owner'), false);
  await check(`${name} cannot overwrite`, () =>
    commit(token, goalWrite('users/owner', { goal: { stringValue: 'work' } })), false);
  await check(`${name} cannot delete`, () => docDelete(token, 'users/owner'), false);
}

await check('invalid goal rejected', () =>
  commit(verifiedOwner, goalWrite('users/owner', { goal: { stringValue: 'anything' } })), false);
await check('arbitrary client timestamp rejected', () =>
  commit(verifiedOwner, goalWrite('users/owner', {
    goal: { stringValue: 'work' },
    updatedAt: { timestampValue: '1970-01-01T00:00:00Z' },
  }, false)), false);
await check('missing required timestamp rejected', () =>
  commit(verifiedOwner, goalWrite('users/owner', { goal: { stringValue: 'work' } }, false)), false);

for (const [field, value] of [
  ['isPro', { booleanValue: true }],
  ['location', { mapValue: { fields: { latitude: { doubleValue: 37.5 }, longitude: { doubleValue: 127 } } } }],
  ['tokens', { arrayValue: { values: [{ stringValue: 'private-app-token' }] } }],
]) {
  await check(`extra ${field} field rejected`, () =>
    commit(verifiedOwner, goalWrite('users/owner', { goal: { stringValue: 'work' }, [field]: value })), false);
}

await check('unlisted collection rejected', () =>
  commit(verifiedOwner, goalWrite('purchases/owner', { goal: { stringValue: 'work' } })), false);

// Seed server-only purchaseAccounts documents using the emulator admin token.
for (const [path, fields] of [
  ['purchaseAccounts/owner', { deleted: { booleanValue: false } }],
  ['purchaseAccounts/owner/spends/test-session', { status: { stringValue: 'spent' } }],
]) {
  const seed = await commit(adminToken, goalWrite(path, fields, false));
  if (!seed.ok) throw new Error(`Failed to seed ${path}: ${seed.status} ${await seed.text()}`);
}

for (const [name, path] of [
  ['purchase account', 'purchaseAccounts/owner'],
  ['spend receipt', 'purchaseAccounts/owner/spends/test-session'],
]) {
  await check(`owner cannot read server-only ${name}`, () => docGet(verifiedOwner, path), false);
  await check(`owner cannot forge server-only ${name}`, () =>
    commit(verifiedOwner, goalWrite(path, { status: { stringValue: 'spent' }, deleted: { booleanValue: false } }, false)), false);
  await check(`owner cannot delete server-only ${name}`, () => docDelete(verifiedOwner, path), false);
}

await check('unverified owner retains delete access', () => docDelete(unverifiedOwner, 'users/owner'), true);

const outIdx = process.argv.indexOf('--out');
if (outIdx !== -1 && process.argv[outIdx + 1]) {
  const outPath = resolve(process.argv[outIdx + 1]);
  await mkdir(dirname(outPath), { recursive: true });
  await writeFile(outPath, JSON.stringify({
    checkedAt: new Date().toISOString(),
    project: projectId,
    emulator: emulatorHost,
    rulesSHA256: createHash('sha256').update(rules).digest('hex'),
    passed,
    failed: 0,
    checks,
  }, null, 2) + '\n');
}

console.log(`Firestore rules: ${passed} checks passed, 0 failed (${projectId} local emulator).`);
