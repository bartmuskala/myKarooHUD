package com.bartmuskala.mykaroohud.datatype

import kotlin.math.exp

/**
 * W' Prime Balance calculator — Skiba (2012) ODE model.
 *
 * Recovery model (below CP):
 *   W'(t+dt) = W'cap − (W'cap − W'(t)) × e^(−dt/τ)
 *   τ = 546 × exp(−0.01 × (CP − P̄sub)) + 316  [Skiba 2012]
 *
 * Depletion model (above CP):
 *   W'(t+dt) = W'(t) − (P − CP) × dt
 *
 * PAUSE / SILENT POWER PERIODS:
 *   Recovery MUST continue when the rider is stopped or the device is paused.
 *   Stopping is physiologically indistinguishable from riding at 0 W which is
 *   well below CP.  The reference karoo-wprimebalance extension handles this
 *   with a 0 W recovery ticker during silent periods.
 *
 *   We implement the same by accepting actual wall-clock elapsed time for
 *   recovery, with only a generous cap (30 min) to guard against multi-hour
 *   app restarts.  Depletion is capped at 5 s to absorb sensor/GPS spikes.
 */
class WPrimeCalculator {

    // Current balance (Joules). Negative = uninitialized.
    private var wPrimeBalance: Double = -1.0

    // Config shadow – detect changes so we can auto-reset.
    private var lastCp: Int     = -1
    private var lastWPrime: Int = -1

    // Model parameters (set from config on first call / config change)
    private var cP: Double             = 250.0
    private var wPrimeCapacity: Double = 16000.0

    // Running statistics for τ (Skiba sub-CP average)
    private var sumPowerBelowCp: Double  = 0.0
    private var countPowerBelowCp: Long  = 0L
    private var avgPowerBelowCp: Double  = 0.0
    private var currentTau: Double       = tauForDeltaCp(250.0)

    private var prevTimeMillis: Long = 0L

    // ------------------------------------------------------------------

    /**
     * Call once per sensor update or once per ticker beat.
     * Returns W'balance as a fraction 0.0–1.0.
     *
     * @param powerWatts      Instantaneous power in Watts.
     *                        Pass 0.0 during pauses / recovery ticks.
     * @param currentTimeMillis  System.currentTimeMillis()
     * @param cp              Critical Power in Watts (from settings)
     * @param wPrimeJoules    W' capacity in Joules (from settings)
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
            currentTau = tauForDeltaCp(cP)   // τ at zero average sub-CP power
            prevTimeMillis = currentTimeMillis
            lastCp     = cp
            lastWPrime = wPrimeJoules
            return 1.0
        }

        // First ever call
        if (prevTimeMillis == 0L) {
            prevTimeMillis = currentTimeMillis
            wPrimeBalance = wPrimeCapacity
            return 1.0
        }

        val dtMs = currentTimeMillis - prevTimeMillis
        if (dtMs <= 0L) return (wPrimeBalance / wPrimeCapacity).coerceIn(0.0, 1.0)

        val powerAboveCp = powerWatts - cP

        if (powerAboveCp > 0.0) {
            // ── Above CP: deplete linearly ─────────────────────────────────
            // Cap at 5 s to absorb power-meter dropouts / GPS spikes that
            // could otherwise produce a sudden huge depletion.
            val dtSec = dtMs.coerceAtMost(5_000L) / 1000.0
            wPrimeBalance -= powerAboveCp * dtSec
        } else {
            // ── Below CP (or 0 W during a pause): exponential recovery ─────
            // Use actual elapsed time, capped at 30 min (1 800 000 ms) to
            // prevent unrealistic jumps after multi-hour power-off gaps.
            // This is the same "0 W recovery ticker" approach used by the
            // reference karoo-wprimebalance extension.
            val dtSec = dtMs.coerceAtMost(1_800_000L) / 1000.0

            // Update running average of sub-CP power for τ
            sumPowerBelowCp   += powerWatts
            countPowerBelowCp += 1
            avgPowerBelowCp    = sumPowerBelowCp / countPowerBelowCp.toDouble()
            currentTau         = tauForDeltaCp(cP - avgPowerBelowCp)

            // Exact ODE solution for one time step
            val expFactor = exp(-dtSec / currentTau)
            wPrimeBalance = wPrimeCapacity - (wPrimeCapacity - wPrimeBalance) * expFactor
        }

        wPrimeBalance  = wPrimeBalance.coerceIn(0.0, wPrimeCapacity)
        prevTimeMillis = currentTimeMillis

        return wPrimeBalance / wPrimeCapacity
    }

    /**
     * Returns current balance without advancing time.
     * Used only for display when no new power sample is available yet.
     */
    fun currentPercent(): Double {
        val balance = if (wPrimeBalance < 0) wPrimeCapacity else wPrimeBalance
        return (balance / wPrimeCapacity).coerceIn(0.0, 1.0)
    }

    // ------------------------------------------------------------------

    companion object {
        /** τ (seconds) from Skiba (2012) equation. */
        fun tauForDeltaCp(deltaCp: Double): Double =
            546.0 * exp(-0.01 * deltaCp) + 316.0
    }
}
