import Camera.CameraMovement.*
import imgui.ImGui
import imgui.flag.ImGuiConfigFlags
import imgui.gl3.ImGuiImplGl3
import imgui.glfw.ImGuiImplGlfw
import imgui.type.ImBoolean
import imgui.type.ImInt
import org.joml.*
import org.joml.Intersectionf.intersectRayPlane
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL33.*
import org.lwjgl.system.MemoryUtil.NULL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

var deltaTime = 0f
var lastFrameStartTime = 0f

var currentWindowWidth = 1024
var currentWindowHeight = 768

const val blockWidth = 5
const val blockHeight = 5
const val chunkSize = 10
const val heightScale = .02f
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

val blocks: MutableMap<Vector2i, Block> = ConcurrentHashMap()
val generatedBlocks: MutableSet<Vector2i> = ConcurrentHashMap.newKeySet()
val missingBlocks = ConcurrentLinkedQueue<Vector2i>()
val rawBlocks: MutableMap<Vector2i, Block> = ConcurrentHashMap()
val blockGLObjects: MutableMap<Vector2i, Triple<Int, Int, Int>> = HashMap()
val blockVertexArrays: MutableMap<Vector2i, FloatArray> = ConcurrentHashMap()
val blockTextures: MutableMap<Vector2i, Int> = HashMap()

var generationRadius = ImInt(32)
var displayRadius = ImInt(41)

val selectedBlocks: MutableSet<Vector2i> = ConcurrentHashMap.newKeySet()

var mouseLocked = false

val generatingBlocks = AtomicBoolean(true)
val generatingBlocksGui = ImBoolean(true)

fun main() {
    GLFWErrorCallback.createPrint(System.err).set()

    if (!glfwInit()) error("Unable to initialize GLFW")

    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)

    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)

    val window = glfwCreateWindow(currentWindowWidth, currentWindowHeight, "Infiniterrain", NULL, NULL)
    if (window == NULL) {
        error("Failed to create GLFW window")
    }
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)

    glfwShowWindow(window)

    glfwMakeContextCurrent(window)
    glfwSetFramebufferSizeCallback(window) { _, cWidth, cHeight ->
        currentWindowWidth = cWidth
        currentWindowHeight = cHeight
        glViewport(0, 0, currentWindowWidth, currentWindowHeight)
    }

    glfwSetInputMode(window, GLFW_CURSOR, if (mouseLocked) GLFW_CURSOR_DISABLED else GLFW_CURSOR_NORMAL)

    GL.createCapabilities()

    val imGuiGlfw = ImGuiImplGlfw()
    val imGuiGl3 = ImGuiImplGl3()

    ImGui.setCurrentContext(ImGui.createContext())
    imGuiGlfw.init(window, true)
    imGuiGl3.init("#version 330 core")

    val imGuiIO = ImGui.getIO()

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
            GLFW_KEY_ESCAPE -> selectedBlocks.clear()
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
                generatingBlocksGui.set(generatingBlocks.get())
            }
        }
    }
    glfwSetMouseButtonCallback(window) { _, button, action, mods ->
        if (action == GLFW_PRESS) when (button) {
            GLFW_MOUSE_BUTTON_LEFT -> {
                if (!imGuiIO.wantCaptureMouse) {
                    val coords = getWorldCoordsFromWindowCoords()
                    coords?.let {
                        selectedBlocks.clear()
                        selectedBlocks.add(it.toBlockCoords())
                    }
                }
            }
            GLFW_MOUSE_BUTTON_RIGHT -> {
                if (!imGuiIO.wantCaptureMouse) {
                    selectedBlocks.clear()
                }
            }
        }
    }

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

        imGuiGlfw.newFrame()
        ImGui.newFrame()

        lightPosition.z = 2f * sin(now)

        processInput(window)

        val projection = projection
        val view = camera.viewMatrix

        glClearColor(0f, 0f, 0f, 1f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        val visibleBlocks = HashSet<Vector2i>()

        val cameraCoords = camera.position.xz().toBlockCoords()

        for (i in 0..displayRadius.get()) {
            for (y in -i..i) {
                val blockCoords = cameraCoords + Vector2i(i, y)
                if (blockCoords in blocks) {
                    visibleBlocks
                } else {
                    if (generatingBlocks.get() && i <= generationRadius.get()) {
                        missingBlocks
                    } else { null }
                }?.add(blockCoords)
            }
            for (x in i downTo -i) {
                val blockCoords = cameraCoords + Vector2i(x, i)
                if (blockCoords in blocks) {
                    visibleBlocks
                } else {
                    if (generatingBlocks.get() && i <= generationRadius.get()) {
                        missingBlocks
                    } else { null }
                }?.add(blockCoords)
            }
            for (y in i downTo -i) {
                val blockCoords = cameraCoords + Vector2i(-i, y)
                if (blockCoords in blocks) {
                    visibleBlocks
                } else {
                    if (generatingBlocks.get() && i <= generationRadius.get()) {
                        missingBlocks
                    } else { null }
                }?.add(blockCoords)
            }
            for (x in -i..i) {
                val blockCoords = cameraCoords + Vector2i(x, -i)
                if (blockCoords in blocks) {
                    visibleBlocks
                } else {
                    if (generatingBlocks.get() && i <= generationRadius.get()) {
                        missingBlocks
                    } else { null }
                }?.add(blockCoords)
            }
        }


//        for (y in 0..<currentWindowHeight step currentWindowHeight/64) {
//            for (x in 0..<currentWindowWidth step currentWindowWidth/64) {
//                val coords = getWorldCoordsFromWindowCoords(x.toFloat(), y.toFloat(), currentWindowWidth, currentWindowHeight)
//                coords?.let {
//                    val blockCoords = it.toBlockCoords()
//                    if (blockCoords in blocks) {
//                        visibleBlocks.add(blockCoords)
//                    } else if (generatingBlocks.get()) {
//                        currentMissingBlocks.add(blockCoords)
//                    } else {
//                        //TODO why this block
//                    }
//                }
//            }
//        }
//
//        missingBlocks.addAll(currentMissingBlocks)

        for (coords in visibleBlocks) {
            val block = blocks[coords] ?: continue
            val vertexArray = blockVertexArrays[coords] ?: continue
            val vao = blockGLObjects[coords]?.first ?: run {
                val vao = glGenVertexArrays().apply { vertexArrays.add(this) }
                val vbo = glGenBuffers().apply { buffers.add(this) }
                val ebo = glGenBuffers().apply { buffers.add(this) }

                glBindVertexArray(vao)

                glBindBuffer(GL_ARRAY_BUFFER, vbo)
                glBufferData(GL_ARRAY_BUFFER, vertexArray, GL_STATIC_DRAW)
                //TODO map buffer and write data in a separate thread

                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo)
                glBufferData(GL_ELEMENT_ARRAY_BUFFER, block.getIndicesArray(), GL_STATIC_DRAW)

                glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.SIZE_BYTES, 0)
                glEnableVertexAttribArray(0)
                glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * Float.SIZE_BYTES, 3 * Float.SIZE_BYTES.toLong())
                glEnableVertexAttribArray(1)

                blockGLObjects[coords] = Triple(vao, vbo, ebo)

                return@run vao
            }

            val model = Matrix4f()
                .translate(coords.x * 2f + 1f, 0f, coords.y * 2f + 1f)

            val normalMatrix = model.normal(Matrix3f())

            shader.use()
            shader["model"] = model
            shader["view"] = view
            shader["projection"] = projection
            shader["lightColor"] = Vector3f(1f, 1f, 1f)
            shader["objectColor"] = Vector3f(0f, 0.8f, 0f)
            shader["lightPosition"] = lightPosition
            shader["normalMatrix"] = normalMatrix
            shader["selected"] = coords in selectedBlocks
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

        ImGuiWindow("Infiniterrain") {
            ImGui.checkbox("Generating blocks", generatingBlocksGui)
            if (generatingBlocksGui.get() != generatingBlocks.get()) {
                generatingBlocks.set(generatingBlocksGui.get())
            }
            ImGui.inputInt("Generation radius", generationRadius)
            ImGui.inputInt("Display radius", displayRadius)
            ImGui.text("FPS: ${(1/deltaTime).toInt()}")

            ImGui.spacing()

            ImGui.text("DEBUG")
            ImGui.text("Blocks: ${blocks.size}")
            ImGui.text("Visible blocks: ${visibleBlocks.size}")
            ImGui.text("Missing blocks: ${missingBlocks.size}")
            ImGui.text("Raw (uninterpolated) blocks: ${rawBlocks.size}")
            ImGui.text("Generating blocks: ${generatedBlocks.size}")
            ImGui.text("Camera position: %.2f, %.2f, %.2f".format(camera.position.x, camera.position.y, camera.position.z))
        }

        if (selectedBlocks.size == 1) {
            ImGuiWindow("Block") {
                ImGui.text("Coordinates: (${selectedBlocks.first().x}, ${selectedBlocks.first().y})")
                ImGuiButton("Delete") {
                    deleteBlock(selectedBlocks.first())
                    selectedBlocks.clear()
                }
            }
        }

        ImGui.render()
        imGuiGl3.renderDrawData(ImGui.getDrawData())

        if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
            val backupCurrentContext = glfwGetCurrentContext()
            ImGui.updatePlatformWindows()
            ImGui.renderPlatformWindowsDefault()
            glfwMakeContextCurrent(backupCurrentContext)
        }

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
): Vector2f? {
    val position = Vector3f()
    val direction = Vector3f()
    (projectionMatrix * viewMatrix)
        .unprojectRay(windowX, windowHeight - windowY, intArrayOf(0, 0, windowWidth, windowHeight), position, direction)
    val offset = intersectRayPlane(position, direction, Vector3f(), Vector3f(0f, 1f, 0f), 0f)

    if (offset == -1f)// || offset > 200f)
        return null
    //TODO generate chunks nearest to the camera first

    position.add(direction.mul(offset))
    return Vector2f(position.x, position.z)
}

/**
 * Converts xz plane float coordinates to block coordinates.
 * @return block long coordinates
 */
fun Vector2f.toBlockCoords(): Vector2i {
    var blockX = floor(this.x).toInt()
    if (blockX < 0) blockX--
    var blockY = floor(this.y).toInt()
    if (blockY < 0) blockY--
    return Vector2i(blockX/2, blockY/2)
}

fun deleteBlock(blockCoords: Vector2i) {
    blocks.remove(blockCoords)
    blockVertexArrays.remove(blockCoords)
    blockGLObjects[blockCoords]?.let { glObjects ->
        glDeleteVertexArrays(glObjects.first)
        glDeleteBuffers(intArrayOf(glObjects.second, glObjects.third))
        blockGLObjects.remove(blockCoords)
    }
}

val generateChunks = Runnable {
    val executor = Executors.newFixedThreadPool(generationThreads)
    while (generatingBlocks.get()) {
        val coords = missingBlocks.poll() ?: continue
        if (coords in blocks) {
            continue
        }

        rawBlocks[coords]?.let {
            val topNeighbor = blocks[Vector2i(coords.x, coords.y-1)]
            val rightNeighbor = blocks[Vector2i(coords.x+1, coords.y)]
            val bottomNeighbor = blocks[Vector2i(coords.x, coords.y+1)]
            val leftNeighbor = blocks[Vector2i(coords.x-1, coords.y)]

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
    missingBlocks.clear()
    rawBlocks.clear()
    generatedBlocks.clear()
    //TODO sometimes there's holes
    executor.shutdownNow()
}