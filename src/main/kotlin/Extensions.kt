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