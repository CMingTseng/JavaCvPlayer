package idv.neo.ffmpeg.media.player.core.audio

import java.nio.ShortBuffer
import java.util.*
import kotlin.math.roundToInt

/**
 * Ported and simplified version of Sonic audio speed/pitch processor.
 * Optimized for 16-bit short PCM.
 */
class Sonic(
    private val sampleRate: Int,
    private val channelCount: Int
) {
    private var speed: Float = 1.0f
    private var pitch: Float = 1.0f
    private var rate: Float = 1.0f

    private val minPeriod = sampleRate / 400
    private val maxPeriod = sampleRate / 65
    private val maxRequiredFrames = 2 * maxPeriod

    private var inputBuffer = ShortArray(maxRequiredFrames * channelCount)
    private var inputFrameCount = 0
    private var outputBuffer = ShortArray(maxRequiredFrames * channelCount)
    private var outputFrameCount = 0
    private var pitchBuffer = ShortArray(maxRequiredFrames * channelCount)
    private var pitchFrameCount = 0

    private var oldRatePosition = 0
    private var newRatePosition = 0
    private var remainingInputToCopyFrameCount = 0
    private var prevPeriod = 0
    private var prevMinDiff = 0
    private var minDiff = 0
    private var maxDiff = 0

    fun setSpeed(speed: Float) {
        this.speed = speed
    }

    fun setPitch(pitch: Float) {
        this.pitch = pitch
    }

    fun queueInput(buffer: ShortArray, offset: Int, count: Int) {
        if (count == 0) return
        val framesToWrite = count / channelCount
        ensureInputCapacity(framesToWrite)
        System.arraycopy(buffer, offset, inputBuffer, inputFrameCount * channelCount, count)
        inputFrameCount += framesToWrite
        processStreamInput()
    }

    fun getOutput(buffer: ShortArray): Int {
        val framesToRead = minOf(outputFrameCount, buffer.size / channelCount)
        if (framesToRead == 0) return 0
        System.arraycopy(outputBuffer, 0, buffer, 0, framesToRead * channelCount)
        removeOutputFrames(framesToRead)
        return framesToRead * channelCount
    }

    fun getOutputSize(): Int = outputFrameCount * channelCount

    fun flush() {
        inputFrameCount = 0
        outputFrameCount = 0
        pitchFrameCount = 0
        oldRatePosition = 0
        newRatePosition = 0
        remainingInputToCopyFrameCount = 0
        prevPeriod = 0
        prevMinDiff = 0
        minDiff = 0
        maxDiff = 0
    }

    private fun processStreamInput() {
        val originalOutputFrameCount = outputFrameCount
        val s = speed / pitch
        val r = rate * pitch
        if (s > 1.00001f || s < 0.99999f) {
            changeSpeed(s)
        } else {
            copyToOutput(inputBuffer, 0, inputFrameCount)
            inputFrameCount = 0
        }
        if (r != 1.0f) {
            adjustRate(r, originalOutputFrameCount)
        }
    }

    private fun changeSpeed(speed: Float) {
        if (inputFrameCount < maxRequiredFrames) return
        var positionFrames = 0
        do {
            if (remainingInputToCopyFrameCount > 0) {
                positionFrames += copyInputToOutput(positionFrames)
            } else {
                val period = findPitchPeriod(inputBuffer, positionFrames)
                if (speed > 1.0) {
                    positionFrames += period + skipPitchPeriod(positionFrames, speed, period)
                } else {
                    positionFrames += insertPitchPeriod(positionFrames, speed, period)
                }
            }
        } while (positionFrames + maxRequiredFrames <= inputFrameCount)
        removeInputFrames(positionFrames)
    }

    private fun findPitchPeriod(samples: ShortArray, positionFrames: Int): Int {
        var bestPeriod = 0
        var worstPeriod = 255
        var minDiff = 1
        var maxDiff = 0
        val pos = positionFrames * channelCount
        for (period in minPeriod..maxPeriod) {
            var diff = 0
            for (i in 0 until period) {
                val sVal = samples[pos + i].toInt()
                val pVal = samples[pos + period + i].toInt()
                diff += Math.abs(sVal - pVal)
            }
            if (diff * bestPeriod < minDiff * period) {
                minDiff = diff
                bestPeriod = period
            }
            if (diff * worstPeriod > maxDiff * period) {
                maxDiff = diff
                worstPeriod = period
            }
        }
        this.minDiff = minDiff / bestPeriod
        this.maxDiff = maxDiff / worstPeriod
        
        val retPeriod = if (isPreviousPeriodBetter()) prevPeriod else bestPeriod
        prevMinDiff = this.minDiff
        prevPeriod = bestPeriod
        return retPeriod
    }

    private fun isPreviousPeriodBetter(): Boolean {
        if (minDiff == 0 || prevPeriod == 0) return false
        if (maxDiff > minDiff * 3) return false
        if (minDiff * 2 <= prevMinDiff * 3) return false
        return true
    }

    private fun skipPitchPeriod(position: Int, speed: Float, period: Int): Int {
        val newFrameCount: Int
        if (speed >= 2.0f) {
            newFrameCount = (period / (speed - 1.0f)).toInt()
        } else {
            newFrameCount = period
            remainingInputToCopyFrameCount = (period * (2.0f - speed) / (speed - 1.0f)).toInt()
        }
        ensureOutputCapacity(newFrameCount)
        overlapAdd(newFrameCount, outputBuffer, outputFrameCount, inputBuffer, position, inputBuffer, position + period)
        outputFrameCount += newFrameCount
        return newFrameCount
    }

    private fun insertPitchPeriod(position: Int, speed: Float, period: Int): Int {
        val newFrameCount: Int
        if (speed < 0.5f) {
            newFrameCount = (period * speed / (1.0f - speed)).toInt()
        } else {
            newFrameCount = period
            remainingInputToCopyFrameCount = (period * (2.0f * speed - 1.0f) / (1.0f - speed)).toInt()
        }
        ensureOutputCapacity(period + newFrameCount)
        System.arraycopy(inputBuffer, position * channelCount, outputBuffer, outputFrameCount * channelCount, period * channelCount)
        overlapAdd(newFrameCount, outputBuffer, outputFrameCount + period, inputBuffer, position + period, inputBuffer, position)
        outputFrameCount += period + newFrameCount
        return newFrameCount
    }

    private fun overlapAdd(frameCount: Int, out: ShortArray, outPos: Int, rampDown: ShortArray, rampDownPos: Int, rampUp: ShortArray, rampUpPos: Int) {
        for (i in 0 until channelCount) {
            var o = outPos * channelCount + i
            var u = rampUpPos * channelCount + i
            var d = rampDownPos * channelCount + i
            for (t in 0 until frameCount) {
                out[o] = ((rampDown[d] * (frameCount - t) + rampUp[u] * t) / frameCount).toShort()
                o += channelCount
                d += channelCount
                u += channelCount
            }
        }
    }

    private fun copyInputToOutput(positionFrames: Int): Int {
        val frameCount = minOf(maxRequiredFrames, remainingInputToCopyFrameCount)
        copyToOutput(inputBuffer, positionFrames, frameCount)
        remainingInputToCopyFrameCount -= frameCount
        return frameCount
    }

    private fun copyToOutput(from: ShortArray, positionFrames: Int, frameCount: Int) {
        ensureOutputCapacity(frameCount)
        System.arraycopy(from, positionFrames * channelCount, outputBuffer, outputFrameCount * channelCount, frameCount * channelCount)
        outputFrameCount += frameCount
    }

    private fun adjustRate(rate: Float, originalOutputFrameCount: Int) {
        if (outputFrameCount == originalOutputFrameCount) return
        val newSampleRate = (sampleRate / rate).toLong()
        val oldSampleRate = sampleRate.toLong()
        
        moveNewSamplesToPitchBuffer(originalOutputFrameCount)
        
        for (position in 0 until pitchFrameCount - 1) {
            while ((oldRatePosition + 1) * newSampleRate > newRatePosition * oldSampleRate) {
                ensureOutputCapacity(1)
                interpolateFrame(position, oldSampleRate, newSampleRate)
                newRatePosition++
                outputFrameCount++
            }
            oldRatePosition++
            if (oldRatePosition.toLong() == oldSampleRate) {
                oldRatePosition = 0
                newRatePosition = 0
            }
        }
        removePitchFrames(pitchFrameCount - 1)
    }

    private fun interpolateFrame(position: Int, oldSampleRate: Long, newSampleRate: Long) {
        for (i in 0 until channelCount) {
            val left = pitchBuffer[position * channelCount + i].toInt()
            val right = pitchBuffer[(position + 1) * channelCount + i].toInt()
            val pos = newRatePosition * oldSampleRate
            val leftPos = oldRatePosition * newSampleRate
            val rightPos = (oldRatePosition + 1) * newSampleRate
            val ratio = rightPos - pos
            val width = rightPos - leftPos
            outputBuffer[outputFrameCount * channelCount + i] = ((ratio * left + (width - ratio) * right) / width).toShort()
        }
    }

    private fun moveNewSamplesToPitchBuffer(originalOutputFrameCount: Int) {
        val frameCount = outputFrameCount - originalOutputFrameCount
        ensurePitchCapacity(frameCount)
        System.arraycopy(outputBuffer, originalOutputFrameCount * channelCount, pitchBuffer, pitchFrameCount * channelCount, frameCount * channelCount)
        outputFrameCount = originalOutputFrameCount
        pitchFrameCount += frameCount
    }

    private fun removeInputFrames(positionFrames: Int) {
        val remainingFrames = inputFrameCount - positionFrames
        System.arraycopy(inputBuffer, positionFrames * channelCount, inputBuffer, 0, remainingFrames * channelCount)
        inputFrameCount = remainingFrames
    }

    private fun removeOutputFrames(frameCount: Int) {
        val remainingFrames = outputFrameCount - frameCount
        System.arraycopy(outputBuffer, frameCount * channelCount, outputBuffer, 0, remainingFrames * channelCount)
        outputFrameCount = remainingFrames
    }

    private fun removePitchFrames(frameCount: Int) {
        if (frameCount == 0) return
        val remainingFrames = pitchFrameCount - frameCount
        System.arraycopy(pitchBuffer, frameCount * channelCount, pitchBuffer, 0, remainingFrames * channelCount)
        pitchFrameCount = remainingFrames
    }

    private fun ensureInputCapacity(additionalFrameCount: Int) {
        if (inputFrameCount + additionalFrameCount > inputBuffer.size / channelCount) {
            inputBuffer = inputBuffer.copyOf((inputFrameCount + additionalFrameCount) * 2 * channelCount)
        }
    }

    private fun ensureOutputCapacity(additionalFrameCount: Int) {
        if (outputFrameCount + additionalFrameCount > outputBuffer.size / channelCount) {
            outputBuffer = outputBuffer.copyOf((outputFrameCount + additionalFrameCount) * 2 * channelCount)
        }
    }

    private fun ensurePitchCapacity(additionalFrameCount: Int) {
        if (pitchFrameCount + additionalFrameCount > pitchBuffer.size / channelCount) {
            pitchBuffer = pitchBuffer.copyOf((pitchFrameCount + additionalFrameCount) * 2 * channelCount)
        }
    }
}
