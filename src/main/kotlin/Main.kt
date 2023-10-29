import org.joml.Matrix4f
import kotlin.random.Random
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL33.*
import Camera.CameraMovement.*

var deltaTime = 0f
var lastFrameStartTime = 0f

var windowWidth = 800
var windowHeight = 600

val blockWidth = 5
val blockHeight = 5
val chunkSize = 100
val rng = Random(1234)

val camera = Camera(0f, 0f, 3f)
var lastX = windowWidth.toFloat()/2
var lastY = windowHeight.toFloat()/2
var firstMouse = true

val blocks = HashMap<Coords, Block>()
val blockTextures = HashMap<Coords, Int>()
val blockThreads = HashMap<Coords, Thread>()
var generateX = 0L
var generateY = 0L

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
        val newXPos = xPos.toFloat()
        val newYPos = yPos.toFloat()

        if (firstMouse) {
            lastX = newXPos
            lastY = newYPos
            firstMouse = false
        }

        val xOffset = newXPos - lastX
        val yOffset = newYPos - lastY
        lastX = newXPos
        lastY = newYPos

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
            GLFW_KEY_UP -> generateChunk(generateX, ++generateY)
            GLFW_KEY_DOWN -> generateChunk(generateX, --generateY)
        }
    }

    glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED)

    GL.createCapabilities()

    glEnable(GL_DEPTH_TEST)

    val shader = Shader("glsl/shader.vert", "glsl/shader.frag")

    val vertices = floatArrayOf(
        //  X      Y     Z  TexX  TexY
        -1.0f,  1.0f, 0.0f, 0.0f, 0.0f,
         1.0f,  1.0f, 0.0f, 1.0f, 0.0f,
        -1.0f, -1.0f, 0.0f, 0.0f, 1.0f,
         1.0f, -1.0f, 0.0f, 1.0f, 1.0f
    )
    val indices = intArrayOf(
        0, 1, 2,
        1, 3, 2
    )

    val vertexArrays = ArrayList<Int>()
    val buffers = ArrayList<Int>()

    val vao = glGenVertexArrays().apply { vertexArrays.add(this) }
    val vbo = glGenBuffers().apply { buffers.add(this) }
    val ebo = glGenBuffers().apply { buffers.add(this) }

    glBindVertexArray(vao)

    glBindBuffer(GL_ARRAY_BUFFER, vbo)
    glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW)

    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo)
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW)

    glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * Float.SIZE_BYTES, 0)
    glEnableVertexAttribArray(0)

    glVertexAttribPointer(1, 2, GL_FLOAT, false, 5 * Float.SIZE_BYTES, 3 * Float.SIZE_BYTES.toLong())
    glEnableVertexAttribArray(1)

    shader.use()
    shader["texture0"] = 0

    while (!glfwWindowShouldClose(window)) {
        val now = glfwGetTime().toFloat()
        deltaTime = now - lastFrameStartTime
        lastFrameStartTime = now

        processInput(window)

        glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        synchronized(blocks) {
            for ((coords, block) in blocks) {
                val textureId = blockTextures[coords] ?: run {
                    val texture = glGenTextures().apply { blockTextures[coords] = this }
                    glBindTexture(GL_TEXTURE_2D, texture)

                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)

                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)

                    glTexImage2D(
                        GL_TEXTURE_2D,
                        0,
                        GL_RGB,
                        block.width,
                        block.height,
                        0,
                        GL_RGB,
                        GL_UNSIGNED_BYTE,
                        block.getBrightnessGLBuffer()
                    )
                    glGenerateMipmap(GL_TEXTURE_2D)

                    return@run texture
                }
                glActiveTexture(GL_TEXTURE0)
                glBindTexture(GL_TEXTURE_2D, textureId)

                val model = Matrix4f()
                    .translate(coords.first * 2.toFloat(), -0.5f, coords.second * 2.toFloat())
                    .rotate((-90f).toRadians(), 1f, 0f, 0f)
                val projection = Matrix4f()
                    .perspective(45f.toRadians(), windowWidth.toFloat() / windowHeight.toFloat(), 0.1f, 100f)

                shader["model"] = model
                shader["view"] = camera.viewMatrix
                shader["projection"] = projection

                shader.use()
                glBindVertexArray(vao)
                glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0)
            }
        }

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
            val block = Block(
                blockWidth,
                blockHeight,
                chunkSize,
                rng.nextLong(),
                blocks[x, y-1],
                blocks[x+1, y],
                blocks[x, y+1],
                blocks[x-1, y]
            )//.generateOctaves(4, 0.75, 2.0)
            synchronized(blocks) {
                blocks[x, y] = block
            }
            blockThreads.remove(Pair(x, y))
        }
        blockThreads[x, y] = thread
        thread.start()
    }
}
