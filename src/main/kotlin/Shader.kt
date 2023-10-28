import org.joml.Matrix4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL33.*
import java.io.Closeable

class Shader(vertexPath: String, fragmentPath: String): Closeable {
    val id: Int
    val matrix4fBuffer = BufferUtils.createFloatBuffer(16)
    init {
        val vertexCode = this::class.java.getResourceAsStream(vertexPath)?.bufferedReader()?.readText()
        val fragmentCode = this::class.java.getResourceAsStream(fragmentPath)?.bufferedReader()?.readText()

        val vertex = glCreateShader(GL_VERTEX_SHADER)
        glShaderSource(vertex, vertexCode!!)
        glCompileShader(vertex)

        if (glGetShaderi(vertex, GL_COMPILE_STATUS) != GL_TRUE) {
            println("Vertex shader comp failed: ${glGetShaderInfoLog(vertex)}")
        }

        val fragment = glCreateShader(GL_FRAGMENT_SHADER)
        glShaderSource(fragment, fragmentCode!!)
        glCompileShader(fragment)


        if (glGetShaderi(fragment, GL_COMPILE_STATUS) != GL_TRUE) {
            println("Fragment shader comp failed: ${glGetShaderInfoLog(fragment)}")
        }

        id = glCreateProgram()
        glAttachShader(id, vertex)
        glAttachShader(id, fragment)
        glLinkProgram(id)

        if (glGetProgrami(id, GL_LINK_STATUS) != GL_TRUE) {
            println("Program link failed: ${glGetProgramInfoLog(id)}")
        }

        glDeleteShader(vertex)
        glDeleteShader(fragment)
    }

    fun use() {
        glUseProgram(id)
    }

    override fun close() {
        glDeleteProgram(id)
    }

    operator fun set(name: CharSequence, value: Boolean) {
        glUniform1i(glGetUniformLocation(id, name), if (value) 1 else 0)
    }
    operator fun set(name: CharSequence, value: Int) {
        glUniform1i(glGetUniformLocation(id, name), value)
    }
    operator fun set(name: CharSequence, value: Float) {
        glUniform1f(glGetUniformLocation(id, name), value)
    }
    operator fun set(name: CharSequence, value: Matrix4f) {
        matrix4fBuffer.rewind()
        value.get(matrix4fBuffer)
        glUniformMatrix4fv(glGetUniformLocation(id, name), false, matrix4fBuffer)
    }
}