package io.github.hatake716.ari

import kotlin.math.*

/** Representative tripod gait, not species-fitted biomechanics. See docs/BIOLOGY.md. */
object AntGait {
    const val STRIDE = .019 // Distance in view-width units for one complete stride.
    fun phase(distance: Double, individual: Int = 0): Double =
        (distance / STRIDE * 2 * PI + individual * 2.399963) % (2 * PI)

    /** Left front/hind + right middle move together, opposite the other tripod. */
    fun legPhase(phase: Double, side: Int, leg: Int): Double =
        phase + if ((leg + if(side < 0) 0 else 1) % 2 == 0) 0.0 else PI

    fun turn(current: Float, target: Float, seconds: Double): Float {
        val difference = ((target - current + 540f) % 360f) - 180f
        return ((current + difference * (1 - exp(-seconds * 12)).toFloat()) % 360f + 360f) % 360f
    }
}
