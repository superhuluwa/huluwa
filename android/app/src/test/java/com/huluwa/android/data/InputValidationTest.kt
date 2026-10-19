package com.huluwa.android.data

import org.junit.Assert.*
import org.junit.Test

class InputValidationTest {
    @Test fun registryPortIsNotAnImageTag() {
        assertEquals(ImageReference("localhost:5000/team/nginx", "stable"), ImageReference.parse("localhost:5000/team/nginx:stable"))
        assertEquals(ImageReference("localhost:5000/team/nginx", "latest"), ImageReference.parse("localhost:5000/team/nginx"))
    }
    @Test fun rejectsAnEmptyImageTag() { assertTrue(runCatching { ImageReference.parse("nginx:") }.isFailure) }
    @Test fun validatesDomainAndPortTunnelTypesSeparately() {
        val tcp = TunnelDraft("frp-service", localPort = 8000, remotePort = 18000)
        assertNull(tcp.validationError())
        assertNotNull(tcp.copy(remotePort = 0).validationError())
        assertNotNull(tcp.copy(proxyType = "http", remotePort = null).validationError())
        assertNull(tcp.copy(proxyType = "https", remotePort = null, customDomains = "nas.example.test").validationError())
    }
    @Test fun rejectsIniSectionBreaksInNames() {
        assertNotNull(TunnelDraft("服务\n[other]", localPort = 80, remotePort = 18080).validationError())
    }
}
