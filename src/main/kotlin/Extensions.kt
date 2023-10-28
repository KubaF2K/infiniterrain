import org.joml.Vector3f
import org.joml.Vector3fc
import kotlin.math.PI

operator fun Vector3fc.unaryMinus() = Vector3f().apply{ this@unaryMinus.negate(this) }

/**
 * Subtracts the second vector from the first and returns the result in a new vector.
 */
operator fun Vector3fc.minus(other: Vector3fc) = Vector3f().apply { this@minus.sub(other, this) }

/**
 * Adds the vectors together and returns the result in a new vector.
 */
operator fun Vector3fc.plus(other: Vector3fc) = Vector3f().apply { this@plus.sub(other, this) }

/**
 * Multiplies all components of the vector by the given scalar value and returns the result in a new vector.
 */
operator fun Vector3fc.times(other: Float) = Vector3f().apply { this@times.mul(other, this) }
operator fun Float.times(other: Vector3fc) = Vector3f().apply { other.mul(this@times, this) }

/**
 * Computes the cross product of these vectors and returns the result in a new vector.
 */
infix fun Vector3fc.cross(other: Vector3fc) = Vector3f().apply { this@cross.cross(other, this) }

/**
 * Converts the angle from degrees to radians.
 */
fun Float.toRadians() = this / 180 * PI.toFloat()

operator fun <T1, T2, T3> HashMap<Pair<T1, T2>, T3>.get(x: T1, y: T2) = this[Pair(x,y)]

operator fun <T1, T2, T3> HashMap<Pair<T1, T2>, T3>.set(x: T1, y: T2, value: T3) { this[Pair(x,y)] = value }

typealias Coords = Pair<Long, Long>