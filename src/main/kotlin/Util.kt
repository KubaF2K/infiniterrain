import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Vector3f
import org.joml.Vector3fc
import kotlin.math.PI

/**
 * Returns the negative of the vector.
 */
operator fun Vector3fc.unaryMinus(): Vector3f = this.negate(Vector3f())

/**
 * Subtracts the second vector from the first and returns the result in a new vector.
 */
operator fun Vector3fc.minus(other: Vector3fc): Vector3f = this.sub(other, Vector3f())

/**
 * Adds the vectors together and returns the result in a new vector.
 */
operator fun Vector3fc.plus(other: Vector3fc): Vector3f = this.add(other, Vector3f())

/**
 * Multiplies all components of the vector by the given scalar value and returns the result in a new vector.
 */
operator fun Vector3fc.times(other: Float): Vector3f = this.mul(other, Vector3f())

/**
 * Multiplies all components of the vector by the given scalar value and returns the result in a new vector.
 */
operator fun Float.times(other: Vector3fc): Vector3f = other.mul(this, Vector3f())

/**
 * Multiplies this matrix by the other matrix and returns the result in a new matrix.
 */
operator fun Matrix4fc.times(other: Matrix4fc): Matrix4f = this.mul(other, Matrix4f())

/**
 * Computes the cross product of these vectors and returns the result in a new vector.
 */
infix fun Vector3fc.cross(other: Vector3fc): Vector3f = this.cross(other, Vector3f())

/**
 * Converts the angle from degrees to radians.
 */
fun Float.toRadians() = this / 180 * PI.toFloat()

operator fun <T1, T2, T3> HashMap<Pair<T1, T2>, T3>.get(x: T1, y: T2) = this[Pair(x,y)]

operator fun <T1, T2, T3> HashMap<Pair<T1, T2>, T3>.set(x: T1, y: T2, value: T3) { this[Pair(x,y)] = value }

/**
 * A pair of long coordinates.
 */
typealias LCoords = Pair<Long, Long>

/**
 * A pair of float coordinates.
 */
typealias FCoords = Pair<Float, Float>