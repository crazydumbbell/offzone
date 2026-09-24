// Run with the isolated Firebase emulator config in _workspace/firebase-rules-check.
import { readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const require = createRequire(new URL('../_workspace/firebase-rules-check/package.json', import.meta.url));
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, getDoc, setDoc, updateDoc, deleteDoc, serverTimestamp, Timestamp } = require('firebase/firestore');
if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Local Firestore emulator required; refusing to run without it.');
const [host, port] = process.env.FIRESTORE_EMULATOR_HOST.split(':');
if (!['127.0.0.1', 'localhost', '::1'].includes(host)) throw new Error('Only a loopback emulator is allowed.');

const rules = await readFile(fileURLToPath(new URL('../firestore.rules', import.meta.url)), 'utf8');
const env = await initializeTestEnvironment({
  projectId: 'demo-roomdns',
  firestore: {
    host, port: Number(port),
    rules,
  },
});
let passed = 0;
const checks = [];
async function check(name, action, allowed = false) {
  await (allowed ? assertSucceeds(action()) : assertFails(action()));
  passed++;
  checks.push(name);
  console.log(`PASS ${name}`);
}
const profile = (goal = 'work') => ({ goal, updatedAt: serverTimestamp() });
try {
  await env.clearFirestore();
  const owner = doc(env.authenticatedContext('owner').firestore(), 'users/owner');
  const stranger = doc(env.authenticatedContext('stranger').firestore(), 'users/owner');
  const anonymous = doc(env.unauthenticatedContext().firestore(), 'users/owner');
  await check('owner creates a valid profile with server timestamp', () => setDoc(owner, profile()), true);
  await check('owner reads profile', () => getDoc(owner), true);
  for (const goal of ['rest', 'presence', 'personal']) {
    await check(`owner updates goal to ${goal}`, () => updateDoc(owner, profile(goal)), true);
  }
  for (const [name, ref] of [['unauthenticated', anonymous], ['another UID', stranger]]) {
    await check(`${name} cannot read`, () => getDoc(ref));
    await check(`${name} cannot overwrite`, () => setDoc(ref, profile()));
    await check(`${name} cannot delete`, () => deleteDoc(ref));
  }
  await check('invalid goal rejected', () => updateDoc(owner, profile('anything')));
  await check('arbitrary client timestamp rejected', () => updateDoc(owner, { goal: 'work', updatedAt: Timestamp.fromMillis(0) }));
  await check('missing required timestamp rejected', () => setDoc(owner, { goal: 'work' }));
  for (const [field, value] of [['isPro', true], ['location', { latitude: 37.5, longitude: 127 }], ['tokens', ['private-app-token']]]) {
    await check(`extra ${field} field rejected`, () => setDoc(owner, { ...profile(), [field]: value }));
  }
  await check('unlisted collection rejected', () => setDoc(doc(env.authenticatedContext('owner').firestore(), 'purchases/owner'), profile()));
  const purchaseAccount = doc(env.authenticatedContext('owner').firestore(), 'purchaseAccounts/owner');
  const spendReceipt = doc(env.authenticatedContext('owner').firestore(), 'purchaseAccounts/owner/spends/test-session');
  await env.withSecurityRulesDisabled(async context => {
    await setDoc(doc(context.firestore(), 'purchaseAccounts/owner'), { deleted: false });
    await setDoc(doc(context.firestore(), 'purchaseAccounts/owner/spends/test-session'), { status: 'spent' });
  });
  for (const [name, ref] of [['purchase account', purchaseAccount], ['spend receipt', spendReceipt]]) {
    await check(`owner cannot read server-only ${name}`, () => getDoc(ref));
    await check(`owner cannot forge server-only ${name}`, () => setDoc(ref, { status: 'spent', deleted: false }));
    await check(`owner cannot erase server-only ${name}`, () => deleteDoc(ref));
  }
  await check('owner deletes profile', () => deleteDoc(owner), true);
  await writeFile(new URL('../_workspace/firebase-rules-check/verification.json', import.meta.url), JSON.stringify({
    checkedAt: new Date().toISOString(), project: 'demo-roomdns', emulator: `${host}:${port}`,
    rulesSHA256: createHash('sha256').update(rules).digest('hex'), passed, failed: 0, checks,
  }, null, 2) + '\n');
  console.log(`Firestore rules: ${passed} checks passed, 0 failed (demo-roomdns local emulator).`);
} finally {
  await env.cleanup();
}
