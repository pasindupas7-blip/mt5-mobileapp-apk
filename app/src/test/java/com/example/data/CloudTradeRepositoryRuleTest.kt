package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.AccountInfo
import com.example.model.HistoryTrade
import com.example.model.TradePosition
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CloudTradeRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun saveAndObserveOpenPosition_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = CloudTradeRepository(firestore, auth)
        val ticketId = (System.currentTimeMillis() % 100000L) + 1000L
        val pos = TradePosition(
            id = ticketId,
            symbol = "XAUUSD",
            isBuy = true,
            lots = 0.10,
            openPrice = 4165.54,
            currentPrice = 4165.54,
            sl = 4150.00,
            tp = 4185.00,
            profit = 0.0,
            openTime = "2026.10.19 10:00:00"
        )

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveOpenPosition(pos, uid)
        }

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeOpenPositions(uid).first { list -> list.any { it.id == ticketId } }
        }
        assertTrue(emitted.any { it.id == ticketId && it.symbol == "XAUUSD" })

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.deleteOpenPosition(ticketId, uid)
        }
    }

    @Test
    fun addAndObserveTradeHistory_authenticatedOwner_persistsAcrossSessions() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = CloudTradeRepository(firestore, auth)
        val ticketId = (System.currentTimeMillis() % 100000L) + 2000L
        val suffix = UUID.randomUUID().toString().replace("-", "").take(8)
        val closedTrade = HistoryTrade(
            id = ticketId,
            symbol = "XAUUSD",
            isBuy = true,
            lots = 0.10,
            openPrice = 4165.54,
            closePrice = 4169.54,
            profit = 40.00,
            openTime = "2026.10.19 10:00:00",
            closeTime = "2026.10.19 10:25:00",
            commission = -0.50,
            swap = 0.00
        )

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addHistoryTrade(closedTrade, suffix, uid)
        }

        val emittedHistory = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeTradeHistory(uid).first { list -> list.any { it.id == ticketId } }
        }
        assertTrue(emittedHistory.any { it.id == ticketId && it.profit == 40.00 })
    }

    @Test
    fun saveAndUpdateAccount_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = CloudTradeRepository(firestore, auth)
        val initialAcc = AccountInfo(balance = 518.16, accountName = "REAL 21")

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveOrUpdateAccount(initialAcc, uid)
        }

        val updatedAcc = initialAcc.copy(balance = 558.16, realizedProfit = -99.83)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveOrUpdateAccount(updatedAcc, uid)
        }

        val emittedAcc = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeAccountDoc(uid).first { it != null && it.balance == 558.16 }
        }
        assertNotNull(emittedAcc)
        assertEquals(558.16, emittedAcc!!.balance, 0.001)
    }

    @Test
    fun crossUserHistoryAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = CloudTradeRepository(firestore, auth)
        val suffix = UUID.randomUUID().toString().replace("-", "").take(8)
        val closedTrade = HistoryTrade(
            id = 3005L,
            symbol = "EURUSD",
            isBuy = false,
            lots = 0.20,
            openPrice = 1.12400,
            closePrice = 1.12100,
            profit = 60.00,
            openTime = "2026.10.19 11:00:00",
            closeTime = "2026.10.19 11:30:00"
        )
        val docId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addHistoryTrade(closedTrade, suffix, aliceUid)
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = CloudTradeRepository(firestore, auth)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                bobRepo.getHistoryTradeById(aliceUid, docId)
            }
            fail("Expected FirebaseFirestoreException PERMISSION_DENIED for cross-user access")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }

    @Test
    fun unauthenticatedAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = CloudTradeRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeTradeHistory(aliceUid).first()
            }
            fail("Expected FirebaseFirestoreException PERMISSION_DENIED for unauthenticated user")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_trader@test.com"
        const val BOB_EMAIL = "bob_trader@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
