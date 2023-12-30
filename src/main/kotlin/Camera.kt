import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

class Camera(
    val position: Vector3f = Vector3f(),
    val up: Vector3f = Vector3f(0f, 1f, 0f),
    var yaw: Float = -90f,
    var pitch: Float = 0f
) {
    enum class CameraMovement {
        FORWARD,
        BACKWARD,
        LEFT,
        RIGHT,
        UP,
        DOWN
    }

    val front = Vector3f(0f, 0f, -1f)
    val right = Vector3f()

    val worldUp = Vector3f(up)
    var movementSpeed = 10f
    var mouseSensitivity = .1f


    constructor(
        posX: Float = 0f,
        posY: Float = 0f,
        posZ: Float = 0f,
        upX: Float = 0f,
        upY: Float = 1f,
        upZ: Float = 0f,
        yaw: Float = -90f,
        pitch: Float = 0f
    ): this(
        Vector3f(posX, posY, posZ),
        Vector3f(upX, upY, upZ),
        yaw,
        pitch
    )

    init {
        updateCameraVectors()
    }

    /**
     * The view matrix calculated using Euler Angles and the LookAt Matrix.
     */
    val viewMatrix: Matrix4f
        get() = Matrix4f().lookAt(position, position + front, up)

    /**
     * Processes camera movement.
     * @param direction the direction to move the camera in
     * @param deltaTime the delta time between frames
     */
    fun processKeyboard(direction: CameraMovement, deltaTime: Float) {
        val velocity = movementSpeed * deltaTime
        position.add(
            when (direction) {
                CameraMovement.FORWARD -> Vector3f(front.x, 0f, front.z).normalize() * velocity
                CameraMovement.BACKWARD -> Vector3f(front.x, 0f, front.z).normalize() * -velocity
                CameraMovement.LEFT -> Vector3f(right.x, 0f, right.z).normalize() * -velocity
                CameraMovement.RIGHT -> Vector3f(right.x, 0f, right.z).normalize() * velocity
                CameraMovement.UP -> Vector3f(0f, velocity, 0f)
                CameraMovement.DOWN -> Vector3f(0f, -velocity, 0f)
            }
        )
        if (position.y < 1f) position.y = 1f
    }

    /**
     * Processes camera angle change.
     * @param xOffset the offset of the mouse on the x-axis
     * @param yOffset the offset of the mouse on the y-axis
     * @param constrainPitch whether to constrain the pitch
     */
    fun processMouseMovement(xOffset: Float, yOffset: Float, constrainPitch: Boolean = true) {
        yaw = (yaw + xOffset * mouseSensitivity) % 360f
        pitch = (pitch - yOffset * mouseSensitivity) % 360f

        if (constrainPitch) {
            pitch = pitch.coerceIn(-89f, 89f)
        }

        updateCameraVectors()
    }

    /**
     * Processes camera zoom.
     * @param yOffset the scroll offset
     */
    fun processMouseScroll(yOffset: Float) {
        if ((position + (front * yOffset)).y < 1) return
        position.add(front * yOffset)
    }

    private fun updateCameraVectors() {
        front.set(
            cos(yaw.toRadians()) * cos(pitch.toRadians()),
            sin(pitch.toRadians()),
            sin(yaw.toRadians()) * cos(pitch.toRadians())
        ).normalize()
        right.set(front cross worldUp).normalize()
        up.set(right cross front).normalize()
    }
}