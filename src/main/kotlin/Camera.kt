import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

const val YAW = -90f
const val PITCH = 0f
const val SPEED = 2.5f
const val SENSITIVITY = 0.1f
const val ZOOM = 45f


class Camera(
    val position: Vector3f = Vector3f(),
    val up: Vector3f = Vector3f(0f, 1f, 0f),
    var yaw: Float = YAW,
    var pitch: Float = PITCH
) {
    enum class CameraMovement {
        FORWARD,
        BACKWARD,
        LEFT,
        RIGHT
    }

    val front = Vector3f(0f, 0f, -1f)
    val right = Vector3f()
    val worldUp = Vector3f(up)
    var movementSpeed = SPEED
    var mouseSensitivity = SENSITIVITY
    var zoom = ZOOM


    constructor(
        posX: Float = 0f,
        posY: Float = 0f,
        posZ: Float = 0f,
        upX: Float = 0f,
        upY: Float = 1f,
        upZ: Float = 0f,
        yaw: Float = YAW,
        pitch: Float = PITCH
    ): this(
        Vector3f(posX, posY, posZ),
        Vector3f(upX, upY, upZ),
        yaw,
        pitch
    )

    val viewMatrix: Matrix4f
        get() = Matrix4f().lookAt(position, position + front, up)

    fun processKeyboard(direction: CameraMovement, deltaTime: Float) {
        val velocity = movementSpeed * deltaTime
        position.add(
            when (direction) {
                CameraMovement.FORWARD -> front * velocity
                CameraMovement.BACKWARD -> (front * velocity).negate()
                CameraMovement.LEFT -> (right * velocity).negate()
                CameraMovement.RIGHT -> right * velocity
            }
        )
    }

    fun processMouseMovement(xOffset: Float, yOffset: Float, constrainPitch: Boolean = true) {
        yaw = (yaw + xOffset * mouseSensitivity) % 360f
        pitch = (pitch - yOffset * mouseSensitivity) % 360f

        if (constrainPitch) {
            pitch = max(-89f, min(89f, pitch))
        }

        updateCameraVectors()
    }

    fun processMouseScroll(yOffset: Float) {
        zoom -= yOffset
        zoom = max(1f, min(45f, zoom))
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