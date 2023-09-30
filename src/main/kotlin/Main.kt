import kotlin.random.Random
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL33.*

fun processInput(window: Long) {
    if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS) {
        glfwSetWindowShouldClose(window, true)
    }
}

fun main() {
    val width = 5
    val height = 5
    val chunkSize = 100
    val seed = 1234
    val rng = Random(seed)
    val perlin = Perlin(width, height, chunkSize, rng.nextLong())

    perlin.generateOctaves(4, 0.75, 2.0)

    val noiseTexture = perlin.getBrightnessGLBuffer()

    glfwInit()
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)

    val window = glfwCreateWindow(perlin.width, perlin.height, "helo perlin", 0, 0)
    if (window == 0L) {
        println("Failed to create GLFW window")
        glfwTerminate()
        return
    }

    glfwMakeContextCurrent(window)
    glfwSetFramebufferSizeCallback(window) { _, cWidth, cHeight ->
        glViewport(0, 0, cWidth, cHeight)
    }

    GL.createCapabilities()

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
    val textures = ArrayList<Int>()

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

    val texture = glGenTextures().apply { textures.add(this) }
    glBindTexture(GL_TEXTURE_2D, texture)

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)

//    textureBuffer.reset()

    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, perlin.width, perlin.height, 0, GL_RGB, GL_UNSIGNED_BYTE, noiseTexture)
    glGenerateMipmap(GL_TEXTURE_2D)

    shader.use()
    shader["texture0"] = 0

    while (!glfwWindowShouldClose(window)) {
        processInput(window)

        glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        glClear(GL_COLOR_BUFFER_BIT)

        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, texture)

        shader.use()
        glBindVertexArray(vao)
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0)

        glfwSwapBuffers(window)
        glfwPollEvents()
    }

    glDeleteVertexArrays(vertexArrays.toIntArray())
    glDeleteBuffers(buffers.toIntArray())
    glDeleteTextures(textures.toIntArray())
}