import { createHash } from 'node:crypto';

export class PurchaseError extends Error {
  constructor(code) { super(code); this.code = code; }
}

// Only a session identifier crosses the trust boundary. UID, currency and quantity
// are determined by authentication and server code, never by a client payload.
export function spendRequest(uid, data) {
  if (typeof uid !== 'string' || !uid.length) throw new PurchaseError('unauthenticated');
  if (!data || typeof data !== 'object' || Array.isArray(data) ||
      Object.keys(data).length !== 1 ||
      typeof data.sessionID !== 'string' || !/^[a-f0-9]{64}$/.test(data.sessionID)) {
    throw new PurchaseError('invalid-argument');
  }
  const key = createHash('sha256').update(JSON.stringify([uid, data.sessionID])).digest('hex');
  return { key, sessionID: data.sessionID };
}

export async function chargePass({ uid, key, secret, project, fetchImpl = fetch }) {
  const response = await fetchImpl(
    `https://api.revenuecat.com/v2/projects/${encodeURIComponent(project)}/customers/${encodeURIComponent(uid)}/virtual_currencies/transactions`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${secret}`, 'Content-Type': 'application/json', 'Idempotency-Key': key },
      body: JSON.stringify({ adjustments: { PASS: -1 } }),
      signal: AbortSignal.timeout(15000)
    });
  if (response.status === 422) throw new PurchaseError('failed-precondition');
  if (!response.ok) throw new PurchaseError('unavailable');
  // A 2xx is the trusted ledger acknowledgement. The local receipt prevents a
  // later retry from depending on RevenueCat's idempotency retention period.
}

export async function deleteCustomer({ uid, secret, project, fetchImpl = fetch }) {
  const response = await fetchImpl(
    `https://api.revenuecat.com/v2/projects/${encodeURIComponent(project)}/customers/${encodeURIComponent(uid)}`, {
      method: 'DELETE', headers: { Authorization: `Bearer ${secret}` },
      signal: AbortSignal.timeout(15000)
    });
  if (!response.ok && response.status !== 404) throw new PurchaseError('unavailable');
}
