package com.nuvio.tv.core.auth

import com.nuvio.tv.core.auth.diagnostics.authDiagnosticFilteredBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthDiagnosticRedactionTest {
    @Test
    fun redactsDeviceCredentialsAndPreservesTheServerMessage() {
        val body = """{"code":"PGRST202","message":"Missing RPC","device_code":"device-secret","p_device_nonce":"nonce-secret","verification_uri_complete":"https://example.invalid/link?code=portal-secret"}"""
        val filtered = checkNotNull(authDiagnosticFilteredBody(body))
        assertTrue(filtered.contains("Missing RPC"))
        assertFalse(filtered.contains("device-secret"))
        assertFalse(filtered.contains("nonce-secret"))
        assertFalse(filtered.contains("portal-secret"))
    }
}
