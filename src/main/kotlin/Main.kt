import org.joml.Matrix4f
import kotlin.random.Random
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL33.*
import Camera.CameraMovement.*
import org.joml.Intersectionf.*
import org.joml.Matrix3f
import org.joml.Matrix4fc
import org.joml.Vector3f
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.*

var deltaTime = 0f
var lastFrameStartTime = 0f

var currentWindowWidth = 800
var currentWindowHeight = 600

const val blockWidth = 3
const val blockHeight = 3
const val chunkSize = 100
const val heightScale = .005f
const val generationThreads = 4

val rng = Random(1234)

val camera = Camera(-5f, 8f, 7f, yaw = -45f, pitch = -45f)
var cameraCursorLastX = currentWindowWidth.toFloat()/2
var cameraCursorLastY = currentWindowHeight.toFloat()/2
var firstMouse = true

val projection: Matrix4f
    get() = Matrix4f()
        .perspective(45f.toRadians(), currentWindowWidth.toFloat() / currentWindowHeight.toFloat(), 0.1f, 100f)

var cursorX = cameraCursorLastX
var cursorY = cameraCursorLastY

val lightPosition = Vector3f(1.2f, 2f, 2f)

val blocks: MutableMap<LCoords, Block> = ConcurrentHashMap()
val generatedBlocks: MutableSet<LCoords> = ConcurrentHashMap.newKeySet()
val missingBlocks: MutableSet<LCoords> = ConcurrentHashMap.newKeySet()
val rawBlocks: MutableMap<LCoords, Block> = ConcurrentHashMap()
val blockGLObjects: MutableMap<LCoords, Int> = HashMap()
val blockVertexArrays: MutableMap<LCoords, FloatArray> = ConcurrentHashMap()
val blockTextures: MutableMap<LCoords, Int> = HashMap()

var mouseLocked = false

var generatingBlocks = AtomicBoolean(true)

fun main() {
//    generateChunk(generateX, generateY)

    glfwInit()
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)

    val window = glfwCreateWindow(currentWindowWidth, currentWindowHeight, "helo perlin", 0, 0)
    if (window == 0L) {
        glfwTerminate()
        return
    }

    glfwMakeContextCurrent(window)
    glfwSetFramebufferSizeCallback(window) { _, cWidth, cHeight ->
        currentWindowWidth = cWidth
        currentWindowHeight = cHeight
        glViewport(0, 0, currentWindowWidth, currentWindowHeight)
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
            GLFW_KEY_F -> {
                mouseLocked = !mouseLocked
                firstMouse = mouseLocked
                glfwSetInputMode(window, GLFW_CURSOR, if (mouseLocked) GLFW_CURSOR_DISABLED else GLFW_CURSOR_NORMAL)
            }
            GLFW_KEY_SPACE -> {
                if (generatingBlocks.get()) {
                    generatingBlocks.set(false)
                } else {
                    generatingBlocks.set(true)
                    Thread(generateChunks).start()
                }
            }
        }
    }
    glfwSetMouseButtonCallback(window) { _, button, action, mods ->
        if (action == GLFW_PRESS) when (button) {
            GLFW_MOUSE_BUTTON_LEFT -> {
                val coords = getWorldCoordsFromWindowCoords()
                coords?.let {
                    println(it)
                    val blockCoords = it.toBlockCoords()
                    println(blockCoords)
                    blocks.remove(blockCoords)
                    blockVertexArrays.remove(blockCoords)
                    blockGLObjects[blockCoords]?.let { vaoCoords ->
                        glDeleteVertexArrays(vaoCoords)
                        blockGLObjects.remove(blockCoords)
                        //TODO buffer memory leak?
                    }
                }
            }
        }
    }

    glfwSetInputMode(window, GLFW_CURSOR, if (mouseLocked) GLFW_CURSOR_DISABLED else GLFW_CURSOR_NORMAL)

    GL.createCapabilities()

    glEnable(GL_DEPTH_TEST)
    glEnable(GL_CULL_FACE)

    val shader = Shader("glsl/shader.vert", "glsl/shader.frag")
    val lightingShader = Shader("glsl/light.vert", "glsl/light.frag")

    val vertices = floatArrayOf(
        //  X     Y      Z
        -0.5f, -0.5f, -0.5f,
        0.5f, -0.5f, -0.5f,
        0.5f, 0.5f, -0.5f,
        0.5f, 0.5f, -0.5f,
        -0.5f, 0.5f, -0.5f,
        -0.5f, -0.5f, -0.5f,

        -0.5f, -0.5f, 0.5f,
        0.5f, -0.5f, 0.5f,
        0.5f, 0.5f, 0.5f,
        0.5f, 0.5f, 0.5f,
        -0.5f, 0.5f, 0.5f,
        -0.5f, -0.5f, 0.5f,

        -0.5f, 0.5f, 0.5f,
        -0.5f, 0.5f, -0.5f,
        -0.5f, -0.5f, -0.5f,
        -0.5f, -0.5f, -0.5f,
        -0.5f, -0.5f, 0.5f,
        -0.5f, 0.5f, 0.5f,

        0.5f, 0.5f, 0.5f,
        0.5f, 0.5f, -0.5f,
        0.5f, -0.5f, -0.5f,
        0.5f, -0.5f, -0.5f,
        0.5f, -0.5f, 0.5f,
        0.5f, 0.5f, 0.5f,

        -0.5f, -0.5f, -0.5f,
        0.5f, -0.5f, -0.5f,
        0.5f, -0.5f, 0.5f,
        0.5f, -0.5f, 0.5f,
        -0.5f, -0.5f, 0.5f,
        -0.5f, -0.5f, -0.5f,

        -0.5f, 0.5f, -0.5f,
        0.5f, 0.5f, -0.5f,
        0.5f, 0.5f, 0.5f,
        0.5f, 0.5f, 0.5f,
        -0.5f, 0.5f, 0.5f,
        -0.5f, 0.5f, -0.5f,
    )

    val vertexArrays = ArrayList<Int>()
    val buffers = ArrayList<Int>()

    val lightVao = glGenVertexArrays().apply { vertexArrays.add(this) }
    val lightVbo = glGenBuffers().apply { buffers.add(this) }

    glBindVertexArray(lightVao)
    glBindBuffer(GL_ARRAY_BUFFER, lightVbo)
    glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW)

    glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.SIZE_BYTES, 0)
    glEnableVertexAttribArray(0)

    Thread(generateChunks).start()


    while (!glfwWindowShouldClose(window)) {
        val now = glfwGetTime().toFloat()
        deltaTime = now - lastFrameStartTime
        lastFrameStartTime = now

        lightPosition.z = 2f * sin(now)

        processInput(window)

        val view = camera.viewMatrix

        glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        val visibleBlocks = HashSet<LCoords>()
        val currentMissingBlocks = HashSet<LCoords>()

        for (y in 0..<currentWindowHeight step currentWindowHeight/64) {
            for (x in 0..<currentWindowWidth step currentWindowWidth/64) {
                val coords = getWorldCoordsFromWindowCoords(x.toFloat(), y.toFloat(), currentWindowWidth, currentWindowHeight)
                coords?.let {
                    val blockCoords = it.toBlockCoords()
                    if (blockCoords in blocks) {
                        visibleBlocks.add(blockCoords)
                    } else {
                        currentMissingBlocks.add(blockCoords)
                    }
                }
            }
        }

        missingBlocks.addAll(currentMissingBlocks)

        for (coords in visibleBlocks) {
            val block = blocks[coords] ?: continue
            val vertexArray = blockVertexArrays[coords] ?: continue
            val vao = blockGLObjects[coords] ?: run {
                val vao = glGenVertexArrays().apply { vertexArrays.add(this) }
                val vbo = glGenBuffers().apply { buffers.add(this) }
                val ebo = glGenBuffers().apply { buffers.add(this) }

                glBindVertexArray(vao)

                glBindBuffer(GL_ARRAY_BUFFER, vbo)
                glBufferData(GL_ARRAY_BUFFER, vertexArray, GL_STATIC_DRAW)

                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo)
                glBufferData(GL_ELEMENT_ARRAY_BUFFER, block.getIndicesArray(), GL_STATIC_DRAW)

                glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.SIZE_BYTES, 0)
                glEnableVertexAttribArray(0)
                glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * Float.SIZE_BYTES, 3 * Float.SIZE_BYTES.toLong())
                glEnableVertexAttribArray(1)

                blockGLObjects[coords] = vao

                return@run vao
            }

            val model = Matrix4f()
                .translate(coords.first * 2f + 1f, 0f, coords.second * 2f + 1f)

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

    generatingBlocks.set(false)

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
    if (glfwGetKey(window, GLFW_KEY_Q) == GLFW_PRESS) {
        camera.processKeyboard(DOWN, deltaTime)
    }
    if (glfwGetKey(window, GLFW_KEY_E) == GLFW_PRESS) {
        camera.processKeyboard(UP, deltaTime)
    }
}

/**
 * Returns the world coordinates of the point where the ray from the given window coordinates intersects the xz-plane.
 * @param projectionMatrix the projection matrix (default: the current projection matrix)
 * @param viewMatrix the view matrix (default: the camera view matrix)
 * @param windowX the x window coordinate (default: current cursor x-coordinate)
 * @param windowY the y window coordinate (default: current cursor y-coordinate)
 * @param windowWidth the window width (default: current window width)
 * @param windowHeight the window height (default: current window height)
 * @return the world xz coordinates of the point where the ray from the given window coordinates intersects the xz-plane
 * or null if the ray doesn't intersect the xz-plane
 */
fun getWorldCoordsFromWindowCoords(
    windowX: Float = cursorX,
    windowY: Float = cursorY,
    windowWidth: Int = currentWindowWidth,
    windowHeight: Int = currentWindowHeight,
    projectionMatrix: Matrix4fc = projection,
    viewMatrix: Matrix4fc = camera.viewMatrix
): FCoords? {
    val position = Vector3f()
    val direction = Vector3f()
    (projectionMatrix * viewMatrix)
        .unprojectRay(windowX, windowHeight - windowY, intArrayOf(0, 0, windowWidth, windowHeight), position, direction)
    val offset = intersectRayPlane(position, direction, Vector3f(), Vector3f(0f, 1f, 0f), 0f)

    if (offset == -1f)// || offset > 200f)
        return null
    //TODO generate chunks nearest to the camera first

    position.add(direction.mul(offset))
    return Pair(position.x, position.z)
}

/**
 * Converts xz plane float coordinates to block coordinates.
 * @return block long coordinates
 */
fun FCoords.toBlockCoords(): LCoords {
    var blockX = floor(this.first).toLong()
    if (blockX < 0) blockX--
    var blockY = floor(this.second).toLong()
    if (blockY < 0) blockY--
    return Pair(blockX/2, blockY/2)
}

val generateChunks = Runnable {
    val executor = Executors.newFixedThreadPool(generationThreads)
    while (generatingBlocks.get()) {
        for (coords in missingBlocks) {
            if (coords in blocks) {
                missingBlocks.remove(coords)
                continue
            }

            rawBlocks[coords]?.let {
                val topNeighbor = blocks[coords.first, coords.second-1]
                val rightNeighbor = blocks[coords.first+1, coords.second]
                val bottomNeighbor = blocks[coords.first, coords.second+1]
                val leftNeighbor = blocks[coords.first-1, coords.second]

                it.lerpWithNeighbors(
                    chunkSize/2,
                    topNeighbor,
                    rightNeighbor,
                    bottomNeighbor,
                    leftNeighbor
                )

                executor.submit {
                    blockVertexArrays[coords] = it.getVertexArrayWithFaceNormals(heightScale)
                }

                blocks[coords] = it

                rawBlocks.remove(coords)
                missingBlocks.remove(coords)

            }

            if (coords in blocks || coords in generatedBlocks) continue

            generatedBlocks.add(coords)
            executor.submit {
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
                )
                rawBlocks[coords] = block
                generatedBlocks.remove(coords)
            }
        }
    }
    executor.shutdownNow()
}