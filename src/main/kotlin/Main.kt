import org.joml.Matrix4f
import kotlin.random.Random
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL33.*
import Camera.CameraMovement.*
import org.joml.Matrix3f
import org.joml.Vector3f
import kotlin.math.sin

var deltaTime = 0f
var lastFrameStartTime = 0f

var windowWidth = 800
var windowHeight = 600

const val blockWidth = 5
const val blockHeight = 5
const val chunkSize = 100
const val heightScale = 0.005f

val rng = Random(1234)

val camera = Camera(0f, 0f, 3f)
var cameraCursorLastX = windowWidth.toFloat()/2
var cameraCursorLastY = windowHeight.toFloat()/2
var firstMouse = true

val projection: Matrix4f
    get() = Matrix4f()
        .perspective(45f.toRadians(), windowWidth.toFloat() / windowHeight.toFloat(), 0.1f, 100f)

var cursorX = cameraCursorLastX
var cursorY = cameraCursorLastY

val lightPosition = Vector3f(1.2f, 2f, 2f)

val blocks = HashMap<LCoords, Block>()
val blockGLObjects = HashMap<LCoords, Int>()
val blockTextures = HashMap<LCoords, Int>()
val blockThreads = HashMap<LCoords, Thread>()
var generateX = 0L
var generateY = 0L

var mouseLocked = false

fun main() {
    generateChunk(generateX, generateY)

    glfwInit()
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)

    val window = glfwCreateWindow(windowWidth, windowHeight, "helo perlin", 0, 0)
    if (window == 0L) {
        println("Failed to create GLFW window")
        glfwTerminate()
        return
    }

    glfwMakeContextCurrent(window)
    glfwSetFramebufferSizeCallback(window) { _, cWidth, cHeight ->
        windowWidth = cWidth
        windowHeight = cHeight
        glViewport(0, 0, windowWidth, windowHeight)
    }
    glfwSetCursorPosCallback(window) { _, xPos, yPos ->
        cursorX = xPos.toFloat()
        cursorY = yPos.toFloat()

        if (!mouseLocked) return@glfwSetCursorPosCallback

        val newXPos = xPos.toFloat()
        val newYPos = yPos.toFloat()

        if (firstMouse) {
            cameraCursorLastX = newXPos
            cameraCursorLastY = newYPos
            firstMouse = false
        }

        val xOffset = newXPos - cameraCursorLastX
        val yOffset = newYPos - cameraCursorLastY
        cameraCursorLastX = newXPos
        cameraCursorLastY = newYPos

        camera.processMouseMovement(xOffset, yOffset)
    }
    glfwSetScrollCallback(window) { _, _, yOffset ->
        camera.processMouseScroll(yOffset.toFloat())
    }
    glfwSetKeyCallback(window) { _, key, scancode, action, mods ->
        if (action == GLFW_PRESS) when (key) {
            GLFW_KEY_ESCAPE -> glfwSetWindowShouldClose(window, true)
            GLFW_KEY_LEFT -> generateChunk(--generateX, generateY)
            GLFW_KEY_RIGHT -> generateChunk(++generateX, generateY)
            GLFW_KEY_UP -> generateChunk(generateX, --generateY)
            GLFW_KEY_DOWN -> generateChunk(generateX, ++generateY)
            GLFW_KEY_F -> {
                mouseLocked = !mouseLocked
                firstMouse = mouseLocked
                glfwSetInputMode(window, GLFW_CURSOR, if (mouseLocked) GLFW_CURSOR_DISABLED else GLFW_CURSOR_NORMAL)
            }
        }
    }
    glfwSetMouseButtonCallback(window) { _, button, action, mods ->
        if (action == GLFW_PRESS) when (button) {
            GLFW_MOUSE_BUTTON_LEFT -> {
                println(
                    (projection * camera.viewMatrix)
                        .unproject(cursorX, cursorY, 0f, intArrayOf(0, 0, windowWidth, windowHeight), Vector3f())
                    //TODO
                )
            }
        }
    }

    glfwSetInputMode(window, GLFW_CURSOR, if (mouseLocked) GLFW_CURSOR_DISABLED else GLFW_CURSOR_NORMAL)

    GL.createCapabilities()

    glEnable(GL_DEPTH_TEST)

    val shader = Shader("glsl/shader.vert", "glsl/shader.frag")
    val lightingShader = Shader("glsl/light.vert", "glsl/light.frag")

    val vertices = floatArrayOf(
        //  X     Y      Z
        -0.5f, -0.5f, -0.5f,
        0.5f, -0.5f, -0.5f,
        0.5f,  0.5f, -0.5f,
        0.5f,  0.5f, -0.5f,
        -0.5f,  0.5f, -0.5f,
        -0.5f, -0.5f, -0.5f,

        -0.5f, -0.5f,  0.5f,
        0.5f, -0.5f,  0.5f,
        0.5f,  0.5f,  0.5f,
        0.5f,  0.5f,  0.5f,
        -0.5f,  0.5f,  0.5f,
        -0.5f, -0.5f,  0.5f,

        -0.5f,  0.5f,  0.5f,
        -0.5f,  0.5f, -0.5f,
        -0.5f, -0.5f, -0.5f,
        -0.5f, -0.5f, -0.5f,
        -0.5f, -0.5f,  0.5f,
        -0.5f,  0.5f,  0.5f,

        0.5f,  0.5f,  0.5f,
        0.5f,  0.5f, -0.5f,
        0.5f, -0.5f, -0.5f,
        0.5f, -0.5f, -0.5f,
        0.5f, -0.5f,  0.5f,
        0.5f,  0.5f,  0.5f,

        -0.5f, -0.5f, -0.5f,
        0.5f, -0.5f, -0.5f,
        0.5f, -0.5f,  0.5f,
        0.5f, -0.5f,  0.5f,
        -0.5f, -0.5f,  0.5f,
        -0.5f, -0.5f, -0.5f,

        -0.5f,  0.5f, -0.5f,
        0.5f,  0.5f, -0.5f,
        0.5f,  0.5f,  0.5f,
        0.5f,  0.5f,  0.5f,
        -0.5f,  0.5f,  0.5f,
        -0.5f,  0.5f, -0.5f,
    )

    val vertexArrays = ArrayList<Int>()
    val buffers = ArrayList<Int>()

    val lightVao = glGenVertexArrays().apply { vertexArrays.add(this) }
    val lightVbo = glGenBuffers().apply { buffers.add(this) }

    glBindVertexArray(lightVao)
    glBindBuffer(GL_ARRAY_BUFFER, lightVbo)
    glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW)

    glVertexAttribPointer(0, 3, GL_FLOAT, false, 3*Float.SIZE_BYTES, 0)
    glEnableVertexAttribArray(0)

    while (!glfwWindowShouldClose(window)) {
        val now = glfwGetTime().toFloat()
        deltaTime = now - lastFrameStartTime
        lastFrameStartTime = now

        lightPosition.z = 2f * sin(now)

        processInput(window)

        val view = camera.viewMatrix

        glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        synchronized(blocks) {
            for ((coords, block) in blocks) {
                val vao = blockGLObjects[coords] ?: run {
                    val vao = glGenVertexArrays().apply { vertexArrays.add(this) }
                    val vbo = glGenBuffers().apply { buffers.add(this) }
                    val ebo = glGenBuffers().apply { buffers.add(this) }

                    block.setGLObjects(vao, vbo, ebo, heightScale = heightScale)
                    blockGLObjects[coords] = vao

                    return@run vao
                }

                val model = Matrix4f()
                    .translate(coords.first * 2f, -0.5f, coords.second * 2f)

                val normalMatrix = model.normal(Matrix3f())

                shader.use()
                shader["model"] = model
                shader["view"] = view
                shader["projection"] = projection
                shader["lightColor"] = Vector3f(1f, 1f, 1f)
                shader["objectColor"] = Vector3f(0f, 0.8f, 0f)
                shader["lightPosition"] = lightPosition
                shader["normalMatrix"] = normalMatrix
                glBindVertexArray(vao)
                glDrawElements(GL_TRIANGLES, block.indicesCount, GL_UNSIGNED_INT, 0)
            }
        }

        lightingShader.use()
        lightingShader["projection"] = projection
        lightingShader["view"] = view
        val model = Matrix4f().translate(lightPosition).scale(0.2f)
        lightingShader["model"] = model

        glBindVertexArray(lightVao)
        glDrawArrays(GL_TRIANGLES, 0, 36)

        glfwSwapBuffers(window)
        glfwPollEvents()

    }

    glDeleteVertexArrays(vertexArrays.toIntArray())
    glDeleteBuffers(buffers.toIntArray())
    glDeleteTextures(blockTextures.values.toIntArray())
}

fun processInput(window: Long) {
    if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
        camera.processKeyboard(FORWARD, deltaTime)
    }
    if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
        camera.processKeyboard(BACKWARD, deltaTime)
    }
    if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
        camera.processKeyboard(LEFT, deltaTime)
    }
    if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
        camera.processKeyboard(RIGHT, deltaTime)
    }
}

fun generateChunk(x: Long, y: Long) {
    if (blocks[x, y] == null && blockThreads[x, y]?.isAlive != true) {
        val thread = Thread {

            val block = createPerlinBlock(
                blockWidth,
                blockHeight,
                chunkSize,
                rng.nextLong(),
            ).addFractalPerlinNoise(
                4,
                0.75f,
                2f,
                blockWidth,
                blockHeight,
                chunkSize,
                rng.nextLong()
            ).lerpWithNeighbors(chunkSize/2, blocks[x, y-1], blocks[x+1, y], blocks[x, y+1], blocks[x-1, y])
            synchronized(blocks) {
                blocks[x, y] = block
            }
            blockThreads.remove(Pair(x, y))
        }
        blockThreads[x, y] = thread
        thread.start()
    }
}
