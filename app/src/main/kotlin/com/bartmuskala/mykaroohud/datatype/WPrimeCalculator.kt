package com.bartmuskala.mykaroohud.datatype

import kotlin.math.exp

/**
 * W' Prime Balance calculator using the Skiba (2012) differential equation model.
 *
 * Model rules:
 *   - Above CP: W' depletes linearly at rate (P − CP) W per second.
 *   - Below CP: W' recovers exponentially toward W'cap with time constant τ.
 *
 * τ (Skiba 2012):
 *   τ = 546 × exp(−0.01 × (CP − P̄sub)) + 316
 *   where P̄sub is the running mean of power recorded while below CP.
 *   Smaller δCP (power close to CP) → larger τ → slower recovery.
 *   Larger δCP (power far below CP) → smaller τ → faster recovery.
 *
 * Config is accepted on every call and the state is automatically reset
 * when CP or W'cap changes, so the user can adjust settings mid-ride and
 * have the model re-initialise cleanly without needing an explicit reset.
 */
class WPrimeCalculator {

    // Current balance (Joules). -1 = uninitialized / needs first-call setup.
    private var wPrimeBalance: Double = -1.0

    // Config shadow – detect changes so we can auto-reset.
    private var lastCp: Int    = -1
    private var lastWPrime: Int = -1

    // Model parameters (set from config on first call / config change)
    private var cP: Double            = 250.0
    private var wPrimeCapacity: Double = 16000.0

    // Running statistics for τ calculation (Skiba sub-CP average)
    private var sumPowerBelowCp: Double  = 0.0
    private var countPowerBelowCp: Long  = 0L
    private var avgPowerBelowCp: Double  = 0.0   // P̄sub
    private var currentTau: Double        = 546.0 * exp(-0.01 * 250.0) + 316.0

    private var prevTimeMillis: Long = 0L

    // -----------------------------------------------------------------------

    /**
     * Call once per sensor update. Returns W'balance as a fraction 0.0–1.0.
     *
     * @param powerWatts      Instantaneous power in Watts (use instant, not 3-s avg)
     * @param currentTimeMillis System.currentTimeMillis()
     * @param cp              Critical Power in Watts (from user settings)
     * @param wPrimeJoules    W' capacity in Joules (from user settings)
     */
    fun calculateWPrimeBalancePercent(
        powerWatts: Double,
        currentTimeMillis: Long,
        cp: Int,
        wPrimeJoules: Int,
    ): Double {
        // Auto-reset when the user changes CP or W'cap in Settings
        if (cp != lastCp || wPrimeJoules != lastWPrime) {
            cP             = cp.toDouble()
            wPrimeCapacity = wPrimeJoules.toDouble()
            wPrimeBalance  = wPrimeCapacity   // start at 100 %
            sumPowerBelowCp   = 0.0
            countPowerBelowCp = 0L
            avgPowerBelowCp   = 0.0
            currentTau = tauForDeltaCp(cP - avgPowerBelowCp)
            prevTimeMillis = currentTimeMillis
            lastCp     = cp
            lastWPrime = wPrimeJoules
            return 1.0
        }

        // First ever call
        if (prevTimeMillis == 0L) {
            prevTimeMillis = currentTimeMillis
            return if (wPrimeBalance < 0) 1.0 else wPrimeBalance / wPrimeCapacity
        }

        val dtMs = currentTimeMillis - prevTimeMillis
        if (dtMs <= 0L) return (wPrimeBalance / wPrimeCapacity).coerceIn(0.0, 1.0)

        // Cap sample to 5 s so GPS dropouts / display pauses don't cause
        // unrealistic depletion spikes.
        val dtSec = dtMs.coerceAtMost(5_000L) / 1000.0

        val powerAboveCp = powerWatts - cP

        if (powerAboveCp > 0.0) {
            // ── Above CP: linear depletion ────────────────────────────────
            wPrimeBalance -= powerAboveCp * dtSec
        } else {
            // ── Below CP: exponential recovery ───────────────────────────
            // Update running average of sub-CP power for τ (Skiba 2012)
            sumPowerBelowCp   += powerWatts
            countPowerBelowCp += 1
            avgPowerBelowCp    = sumPowerBelowCp / countPowerBelowCp.toDouble()
            currentTau         = tauForDeltaCp(cP - avgPowerBelowCp)

            // Exact ODE solution: W'(t+dt) = W'cap − (W'cap − W'(t)) × e^(−dt/τ)
            val expFactor = exp(-dtSec / currentTau)
            wPrimeBalance = wPrimeCapacity - (wPrimeCapacity - wPrimeBalance) * expFactor
        }

        wPrimeBalance  = wPrimeBalance.coerceIn(0.0, wPrimeCapacity)
        prevTimeMillis = currentTimeMillis

        return wPrimeBalance / wPrimeCapacity
    }

    /**
     * Returns the current W'balance percentage WITHOUT advancing time.
     * Used when the ride is paused: we show the last value but don't tick the clock.
     * Still applies the config-change reset guard so a CP/W' change takes effect.
     */
    fun frozenPercent(cp: Int, wPrimeJoules: Int): Double {
        if (cp != lastCp || wPrimeJoules != lastWPrime) {
            // Config changed while paused — reset for next recording session
            cP             = cp.toDouble()
            wPrimeCapacity = wPrimeJoules.toDouble()
            wPrimeBalance  = wPrimeCapacity
            sumPowerBelowCp   = 0.0
            countPowerBelowCp = 0L
            avgPowerBelowCp   = 0.0
            currentTau = tauForDeltaCp(cP - avgPowerBelowCp)
            prevTimeMillis = 0L
            lastCp     = cp
            lastWPrime = wPrimeJoules
            return 1.0
        }
        val balance = if (wPrimeBalance < 0) wPrimeCapacity else wPrimeBalance
        return (balance / wPrimeCapacity).coerceIn(0.0, 1.0)
    }

    /** τ (seconds) from Skiba (2012) equation. */
    private fun tauForDeltaCp(deltaCp: Double): Double =
        546.0 * exp(-0.01 * deltaCp) + 316.0
}
