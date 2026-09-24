import XCTest
import StoreKit
import StoreKitTest

// Xcode's local StoreKit service only: no RevenueCat, Firebase or App Store Sandbox proof.
final class StoreKitLocalTests: XCTestCase {
    private func latest(_ id: String, matching predicate: (Transaction) -> Bool) async throws -> Transaction? {
        for _ in 0..<50 {
            if case .verified(let transaction) = await Transaction.latest(for: id), predicate(transaction) {
                return transaction
            }
            try await Task.sleep(for: .milliseconds(100))
        }
        return nil
    }

    func testLocalPurchaseLifecycle() async throws {
        guard ProcessInfo.processInfo.environment["OFFZONE_LOCAL_STOREKIT"] == "1" else {
            throw XCTSkip("Run RoomDNS-StoreKit scheme to enable the local StoreKit environment")
        }
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "Offzone", withExtension: "storekit"))
        let session = try SKTestSession(contentsOf: url)
        session.resetToDefaultState()
        session.disableDialogs = true
        guard session.disableDialogs else {
            return XCTFail("Local StoreKit service did not accept configuration; refusing Sandbox fallback")
        }
        session.timeRate = .realTime
        session.clearTransactions()
        defer { session.clearTransactions(); session.resetToDefaultState() }

        let prices: [String: Decimal] = [
            "com.exchip.roomdns.pro.monthly": Decimal(string: "4.99")!,
            "com.exchip.roomdns.pro.annual": Decimal(string: "29.99")!,
            "com.exchip.roomdns.unlock.single": Decimal(string: "0.99")!
        ]
        let products = try await Product.products(for: Array(prices.keys))
        XCTAssertEqual(Set(products.map(\.id)), Set(prices.keys))
        guard products.count == prices.count else { return }
        for product in products.sorted(by: { $0.id < $1.id }) {
            session.clearTransactions()
            XCTAssertEqual(product.price, prices[product.id])
            let isPass = product.id.hasSuffix("unlock.single")
            XCTAssertEqual(product.type, isPass ? .consumable : .autoRenewable)
            if !isPass {
                let period = try XCTUnwrap(product.subscription?.subscriptionPeriod)
                XCTAssertEqual(period.value, 1)
                XCTAssertEqual(period.unit, product.id.hasSuffix("monthly") ? .month : .year)
            }

            try await session.setSimulatedError(.generic(.userCancelled), forAPI: .purchase)
            let cancelled = try await product.purchase()
            guard case .userCancelled = cancelled else { return XCTFail("Expected cancellation: \(product.id)") }
            // StoreKit keeps failed attempts in its test history; cancellation must grant no purchase.
            XCTAssertTrue(session.allTransactions().allSatisfy { $0.state == .failed })
            for await case .verified(let entitlement) in Transaction.currentEntitlements {
                XCTAssertNotEqual(entitlement.productID, product.id)
            }
            try await session.setSimulatedError(nil, forAPI: .purchase)
            let purchaseError = try await session.simulatedError(forAPI: .purchase)
            XCTAssertNil(purchaseError)
            session.resetToDefaultState()
            session.disableDialogs = true
            session.timeRate = .realTime
            session.clearTransactions()

            let result = try await product.purchase()
            guard case .success(.verified(let transaction)) = result else {
                return XCTFail("Expected verified local purchase: \(product.id)")
            }
            XCTAssertEqual(transaction.productID, product.id)
            XCTAssertEqual(transaction.environment, .xcode)
            await transaction.finish()
            try await AppStore.sync()
            var restoredIDs = Set<String>()
            for await case .verified(let entitlement) in Transaction.currentEntitlements {
                restoredIDs.insert(entitlement.productID)
            }
            XCTAssertEqual(restoredIDs.contains(product.id), !isPass,
                           "Finished consumables are not restored; active subscriptions are")

            if !isPass {
                try session.expireSubscription(productIdentifier: product.id)
                guard let expired = try await latest(product.id, matching: {
                    ($0.expirationDate ?? .distantFuture) <= Date()
                }) else { return XCTFail("Expiry did not propagate within 5 seconds: \(product.id)") }
                XCTAssertLessThanOrEqual(try XCTUnwrap(expired.expirationDate), Date())
                for await case .verified(let entitlement) in Transaction.currentEntitlements {
                    XCTAssertNotEqual(entitlement.productID, product.id)
                }
                // Start a fresh purchase to isolate refund from the expired subscription.
                session.clearTransactions()
                _ = try await product.purchase()
            }
            let local = try XCTUnwrap(session.allTransactions().last)
            try session.refundTransaction(identifier: local.identifier)
            let refunded = try XCTUnwrap(session.allTransactions().first { $0.identifier == local.identifier })
            XCTAssertNotNil(refunded.cancelDate)
            // Finished consumables are absent from StoreKit 2 history by default.
            if !isPass {
                let revoked = try await latest(product.id, matching: { $0.revocationDate != nil })
                XCTAssertNotNil(revoked, "Refund did not propagate within 5 seconds")
            }
        }
    }
}
