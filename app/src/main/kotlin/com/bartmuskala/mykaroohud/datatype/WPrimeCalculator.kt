package com.bartmuskala.mykaroohud.datatype

import kotlin.math.exp
import kotlin.math.max

class WPrimeCalculator {

    private var wPrimeBalance: Double = 0.0
    private var cP: Double = 250.0
    private var wPrimeCapacity: Double = 20000.0

    private var countPowerBelowCP: Long = 0
    private var sumPowerBelowCP: Double = 0.0
    private var averagePowerBelowCP: Double = 0.0
    
    private var timeSpent: Double = 0.0
    private var runningSum: Double = 0.0
    private var currentTau: Double = 862.0
    
    private var prevReadingTime: Long = 0

    fun resetRideState(initialTimestampMillis: Long, cp: Int, wPrimeJoules: Int) {
        cP = cp.toDouble()
        wPrimeCapacity = wPrimeJoules.toDouble()
        wPrimeBalance = wPrimeCapacity
        
        countPowerBelowCP = 0L
        sumPowerBelowCP = 0.0
        averagePowerBelowCP = 0.0
        
        timeSpent = 0.0
        runningSum = 0.0
        currentTau = 862.0
        
        prevReadingTime = initialTimestampMillis
    }

    private fun calculateAveragePowerBelowCP(iPower: Double) {
        sumPowerBelowCP += iPower
        countPowerBelowCP++
        averagePowerBelowCP = if (countPowerBelowCP > 0) {
            sumPowerBelowCP / countPowerBelowCP.toDouble()
        } else {
            0.0
        }
    }

    private fun tauWPrimeBalance() {
        val deltaCp = (cP - averagePowerBelowCP)
        currentTau = 546.00 * exp(-0.01 * deltaCp) + 316.00
    }

    /**
     * Calculates W' Prime Balance based on power and time.
     * Returns the balance as a percentage (0.0 to 1.0)
     */
    fun calculateWPrimeBalancePercent(instantaneousPower: Double, currentTimeMillis: Long): Double {
        if (prevReadingTime == 0L) {
            prevReadingTime = currentTimeMillis
            return 1.0
        }
        
        val rawSampleTime = currentTimeMillis - prevReadingTime
        // Limit sample time to 1 second to avoid huge jumps
        val normalizedSampleTime = rawSampleTime.coerceIn(0L, 1000L)
        val sampleTimeSec = normalizedSampleTime / 1000.0

        timeSpent += sampleTimeSec

        val powerAboveCp = (instantaneousPower - cP)
        if (powerAboveCp > 0) {
            tauWPrimeBalance()
        } else {
            calculateAveragePowerBelowCP(instantaneousPower)
            tauWPrimeBalance()
        }

        val wPrimeExpended = max(0.0, powerAboveCp) * sampleTimeSec

        val expTerm1 = exp(timeSpent / currentTau)
        val expTerm2 = exp(-timeSpent / currentTau)

        runningSum += (wPrimeExpended * expTerm1)
        wPrimeBalance = wPrimeCapacity - (runningSum * expTerm2)

        prevReadingTime = currentTimeMillis

        return (wPrimeBalance / wPrimeCapacity).coerceIn(0.0, 1.0)
    }
}
