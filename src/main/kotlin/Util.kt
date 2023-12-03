import imgui.ImGui
import org.joml.*
import kotlin.math.PI
import kotlin.math.pow

fun fade(x: Float) = 6 * x.pow(5) - 15 * x.pow(4) + 10 * x.pow(3)

/**
 * Linearly interpolates between two values.
 */
fun lerp(t: Float, a1: Float, a2: Float) = (1-t) * a1 + t * a2

/**
 * Returns the negative of the vector.
 */
operator fun Vector3fc.unaryMinus(): Vector3f = this.negate(Vector3f())

/**
 * Subtracts the second vector from the first and returns the result in a new vector.
 */
operator fun Vector3fc.minus(other: Vector3fc): Vector3f = this.sub(other, Vector3f())

/**
 * Subtracts the second vector from the first and returns the result in a new vector.
 */
operator fun Vector2fc.minus(other: Vector2fc): Vector2f = this.sub(other, Vector2f())

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
 * Swizzles the x and z of the vector to a Vector2f.
 * @see Vector2f
 */
fun Vector3fc.xz() = Vector2f(x(), z())

/**
 * Converts the angle from degrees to radians.
 */
fun Float.toRadians() = this / 180 * PI.toFloat()

operator fun <T1, T2, T3> MutableMap<Pair<T1, T2>, T3>.get(x: T1, y: T2) = this[Pair(x,y)]

operator fun <T1, T2, T3> MutableMap<Pair<T1, T2>, T3>.set(x: T1, y: T2, value: T3) { this[Pair(x,y)] = value }

fun <T1, T2, T3> Map<Pair<T1, T2>, T3>.contains(x: T1, y: T2) = this.contains(Pair(x,y))

fun <T1, T2, T3> MutableMap<Pair<T1, T2>, T3>.remove(x: T1, y: T2) = this.remove(Pair(x,y))

/**
 * Creates an ImGui Window with the given title, executes the given function, and then calls ImGui.end().
 * @param title the title of the window
 * @param function the function to execute between ImGui.begin() and ImGui.end()
 * @see ImGui.begin
 * @see ImGui.end
 */
fun ImGuiWindow(title: String, function: () -> Unit) {
    ImGui.begin(title)
    function()
    ImGui.end()
}

/**
 * Creates an ImGui Button with the given label and executes the given function when the button is pressed.
 * @param label the label of the button
 * @param function the function to execute when the button is pressed
 * @see ImGui.button
 */
fun ImGuiButton(label: String, function: () -> Unit) {
    if(ImGui.button(label)) function()
}