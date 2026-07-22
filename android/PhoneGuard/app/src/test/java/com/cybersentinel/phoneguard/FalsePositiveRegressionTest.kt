package com.cybersentinel.phoneguard

import com.cybersentinel.phoneguard.monitor.SystemAnalyzer
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Blocca la reintroduzione di falsi positivi già corretti una volta:
 * store open-source legittimi devono restare tra gli installer fidati,
 * altrimenti ogni utente F-Droid/Aurora verrebbe segnalato come "sideload".
 */
class FalsePositiveRegressionTest {

    @Test
    fun `F-Droid e Aurora Store sono installer fidati`() {
        assertTrue("org.fdroid.fdroid" in SystemAnalyzer.TRUSTED_INSTALLERS)
        assertTrue("com.aurora.store" in SystemAnalyzer.TRUSTED_INSTALLERS)
    }

    @Test
    fun `Google Play resta tra gli installer fidati`() {
        assertTrue("com.android.vending" in SystemAnalyzer.TRUSTED_INSTALLERS)
    }
}
