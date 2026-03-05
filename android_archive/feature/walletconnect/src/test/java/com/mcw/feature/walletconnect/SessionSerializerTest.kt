package com.mcw.feature.walletconnect

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SessionSerializer JSON serialization/deserialization.
 */
class SessionSerializerTest {

    @Test
    fun testSerializeAndDeserialize_singleSession() {
        val session = WalletConnectSession(
            topic = "topic1",
            peerName = "My dApp",
            peerUrl = "https://mydapp.com",
            peerIcon = "https://mydapp.com/icon.png",
            chains = listOf("eip155:1", "eip155:137"),
            methods = listOf("eth_sendTransaction", "personal_sign"),
            createdAt = 1709654400000L
        )

        val json = SessionSerializer.toJson(listOf(session))
        val restored = SessionSerializer.fromJson(json)

        assertEquals(1, restored.size)
        val result = restored[0]
        assertEquals("topic1", result.topic)
        assertEquals("My dApp", result.peerName)
        assertEquals("https://mydapp.com", result.peerUrl)
        assertEquals("https://mydapp.com/icon.png", result.peerIcon)
        assertEquals(listOf("eip155:1", "eip155:137"), result.chains)
        assertEquals(listOf("eth_sendTransaction", "personal_sign"), result.methods)
        assertEquals(1709654400000L, result.createdAt)
    }

    @Test
    fun testSerializeAndDeserialize_multipleSessions() {
        val sessions = listOf(
            WalletConnectSession(
                topic = "t1", peerName = "D1", peerUrl = "https://d1.com",
                createdAt = 1000L
            ),
            WalletConnectSession(
                topic = "t2", peerName = "D2", peerUrl = "https://d2.com",
                peerIcon = "https://d2.com/icon.png",
                chains = listOf("eip155:56"),
                methods = listOf("personal_sign"),
                createdAt = 2000L
            )
        )

        val json = SessionSerializer.toJson(sessions)
        val restored = SessionSerializer.fromJson(json)

        assertEquals(2, restored.size)
        assertEquals("t1", restored[0].topic)
        assertEquals("t2", restored[1].topic)
        assertEquals("https://d2.com/icon.png", restored[1].peerIcon)
    }

    @Test
    fun testSerialize_emptyList() {
        val json = SessionSerializer.toJson(emptyList())
        assertEquals("[]", json)
    }

    @Test
    fun testDeserialize_emptyArray() {
        val result = SessionSerializer.fromJson("[]")
        assertTrue(result.isEmpty())
    }

    @Test
    fun testDeserialize_null() {
        val result = SessionSerializer.fromJson(null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun testDeserialize_emptyString() {
        val result = SessionSerializer.fromJson("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun testDeserialize_blankString() {
        val result = SessionSerializer.fromJson("   ")
        assertTrue(result.isEmpty())
    }

    @Test
    fun testDeserialize_invalidJson() {
        val result = SessionSerializer.fromJson("not json at all{{{")
        assertTrue("Invalid JSON should return empty list", result.isEmpty())
    }

    @Test
    fun testSerialize_sessionWithNullIcon() {
        val session = WalletConnectSession(
            topic = "t1", peerName = "D1", peerUrl = "https://d1.com",
            peerIcon = null, createdAt = 1000L
        )

        val json = SessionSerializer.toJson(listOf(session))
        val restored = SessionSerializer.fromJson(json)

        assertEquals(1, restored.size)
        // null icon should be preserved as null (not "null" string)
        assertNull(restored[0].peerIcon)
    }

    @Test
    fun testSerialize_sessionWithEmptyChains() {
        val session = WalletConnectSession(
            topic = "t1", peerName = "D1", peerUrl = "https://d1.com",
            chains = emptyList(), methods = emptyList(), createdAt = 1000L
        )

        val json = SessionSerializer.toJson(listOf(session))
        val restored = SessionSerializer.fromJson(json)

        assertEquals(1, restored.size)
        assertTrue(restored[0].chains.isEmpty())
        assertTrue(restored[0].methods.isEmpty())
    }

    @Test
    fun testDeserialize_missingOptionalFields() {
        // JSON without chains, methods, peerIcon
        val json = """[{"topic":"t1","peerName":"D1","peerUrl":"https://d1.com","createdAt":1000}]"""

        val result = SessionSerializer.fromJson(json)

        assertEquals(1, result.size)
        assertNull(result[0].peerIcon)
        assertTrue(result[0].chains.isEmpty())
        assertTrue(result[0].methods.isEmpty())
    }

    @Test
    fun testRoundTrip_preservesCreatedAtPrecision() {
        // Verify Long precision is maintained through JSON serialization
        val preciseTime = 1709654412345L // arbitrary millisecond-precision timestamp
        val session = WalletConnectSession(
            topic = "t1", peerName = "D1", peerUrl = "https://d1.com",
            createdAt = preciseTime
        )

        val json = SessionSerializer.toJson(listOf(session))
        val restored = SessionSerializer.fromJson(json)

        assertEquals(preciseTime, restored[0].createdAt)
    }
}
