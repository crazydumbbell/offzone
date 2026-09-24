# Offzone purchase backend

`spendUnlockPass` charges exactly one `PASS` to the authenticated Firebase UID; `deleteRevenueCatCustomer` retries RevenueCat cleanup after Firebase Auth deletion. RevenueCat owns the balance. Firestore `purchaseAccounts/{uid}/spends/{hash}` stores server-only idempotent spend receipts; root rules already deny client access. A deleted-account tombstone remains to reject late requests.

Deployed and verified ACTIVE on 2026-09-09: `reconcileUnlockPass` accepts the same session-only payload and returns `cancelled`, `pending`, or `spent`. It transactionally cancels a missing request so late delivery cannot charge it. Existing pending/spent receipts remain preserved for review; reconciliation never calls RevenueCat. Deploy the updated spend handler first, verify it is serving and let old handlers drain, then deploy reconciliation. Never expose cancellation while the old spend handler is still serving. See `_workspace/UNLOCK_RECONCILIATION_STATUS.md` for scope and remaining compensation policy.

`spendUnlockPass` accepts callable JSON `{ "data": { "sessionID": "<64 lowercase hex characters>" } }`. The session ID hashes the actual local runtime generation **and current schedule occurrence**, not just a rule ID. The client persists the UID-specific request before sending, retries that exact ID after interruption, and clears it only after a locked same-session release. Purchase success by itself never releases restrictions. The stale-session compensation policy and signed-device verification are still pending; **do not enable sales yet**.

Deployment configuration:

- Firebase project `roomdns-exchip` now uses Blaze on user-authorized billing account 2 (`01FA86-230529-5132E9`). A monthly KRW 10,000 alert-only budget is active; it is not a spending cap.
- The scoped RevenueCat v2 key (Customers and Purchases read/write) is secured outside the repo and registered as Secret Manager `REVENUECAT_SECRET_KEY` version 1. Never ship it in the app.
- Currency `PASS` and consumable `com.exchip.roomdns.unlock.single` are configured to grant 1 unit without expiry. Prices/store validation and restricted sandbox tester access still need completion.
- Confirm RC customer-deletion retention policy, signed-device session race/recovery behavior and actual sandbox purchase/restore behavior.

Review/deploy only these functions:

```sh
rtk proxy env DEBUG='' firebase deploy --project roomdns-exchip --only functions:spendUnlockPass,functions:deleteRevenueCatCustomer --force --non-interactive
```

Local checks (no production writes):

```sh
rtk proxy npm --prefix functions test
rtk proxy env DEBUG='' PATH='/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin' firebase emulators:exec --project demo-roomdns --config _workspace/firebase-rules-check/firebase.json --only firestore 'npm --prefix functions test'
```

Email authentication and deployed Firestore-rule regression (demo project only):

```sh
rtk proxy env DEBUG='' PATH='/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin' firebase emulators:exec --project demo-roomdns --config firebase.auth-test.json --only auth,firestore 'npm --prefix functions test'
```

`auth-rules.test.js` checks email signup/verification/reset/deletion, explicit Google/Apple linking with the same UID, verified write access, cross-account denial, invalid goals, and unverified owners retaining read/delete access. Provider credentials and email action codes here are emulated; this does not verify OAuth UI or real mail delivery. `firestore.rules` now enforces verified identity for goal writes server-side, matching the app. A linked Google/Apple identity remains eligible without separate email verification.

The emulator test exercises the actual function/Firestore receipt code while mocking authentication lookup and the external RC API. It covers concurrent replay, lost response after remote commit, persisted success, disabled/deleted accounts and cleanup retry. It does not prove Apple purchases or Firebase callable token verification; those require integration/device tests.

References: [RC currency security/idempotency](https://www.revenuecat.com/docs/offerings/virtual-currency), [customer deletion](https://www.revenuecat.com/docs/api-v2/customer), [Firebase billing/deploy](https://firebase.google.com/docs/functions/get-started), [Auth deletion trigger](https://firebase.google.com/docs/auth/extend-with-functions).

Live RC ledger check: `_workspace/revenuecat-ledger-check.json` verifies administrative test credit, repeated same-key debit, rejection at zero balance and customer deletion (404). No Apple purchase was made.

Deployment completed 2026-09-07 09:35 UTC: both functions ACTIVE in us-central1 on Node.js 22 with secret version 1. Live missing-token and invalid-token requests returned 401 / UNAUTHENTICATED. This validates the deployed authentication boundary, not a successful purchase or authenticated debit. See `_workspace/functions-deployment-status.json` and `_workspace/functions-live-auth-verification.json`. Container images older than 1 day are automatically cleaned up.
