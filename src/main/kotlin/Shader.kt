import org.joml.Matrix3fc
import org.joml.Matrix4fc
import org.joml.Vector3fc
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL33.*
import java.io.Closeable
import java.nio.FloatBuffer

/**
 * Represents a shader program object.
 * @param vertexPath the path to the vertex shader source file
 * @param fragmentPath the path to the fragment shader source file
 * @throws Exception if any shader compilation or linking fails
 */
class Shader(vertexPath: String, fragmentPath: String): Closeable {
    private val matrix4fBuffer: FloatBuffer = BufferUtils.createFloatBuffer(16)

    /**
     * The id of this shader program object.
     */
    val id: Int = run {
        val vertexCode = this::class.java.getResourceAsStream(vertexPath)?.bufferedReader()?.readText()
        val fragmentCode = this::class.java.getResourceAsStream(fragmentPath)?.bufferedReader()?.readText()

        val vertex = glCreateShader(GL_VERTEX_SHADER)
        glShaderSource(vertex, vertexCode!!)
        glCompileShader(vertex)

        if (glGetShaderi(vertex, GL_COMPILE_STATUS) != GL_TRUE) {
            throw Exception("Vertex shader compilation failed: ${glGetShaderInfoLog(vertex)}")
        }

        val fragment = glCreateShader(GL_FRAGMENT_SHADER)
        glShaderSource(fragment, fragmentCode!!)
        glCompileShader(fragment)


        if (glGetShaderi(fragment, GL_COMPILE_STATUS) != GL_TRUE) {
            throw Exception("Fragment shader compilation failed: ${glGetShaderInfoLog(fragment)}")
        }

        val id = glCreateProgram()
        glAttachShader(id, vertex)
        glAttachShader(id, fragment)
        glLinkProgram(id)

        if (glGetProgrami(id, GL_LINK_STATUS) != GL_TRUE) {
            throw Exception("Shader program linking failed: ${glGetProgramInfoLog(id)}")
        }

        glDeleteShader(vertex)
        glDeleteShader(fragment)

        return@run id
    }

    /**
     * Installs this program object as part of current rendering state.
     * @see glUseProgram
     */
    fun use() {
        glUseProgram(id)
    }

    /**
     * Deletes this program object.
     * @see glDeleteProgram
     */
    override fun close() {
        glDeleteProgram(id)
    }

    /**
     * Sets the value of an int uniform variable for this shader program object to the value of the given boolean.
     * @param name the name of the uniform variable
     * @param value the value of the uniform variable
     * @see glUniform1i
     * @see glGetUniformLocation
     */
    operator fun set(name: CharSequence, value: Boolean) {
        glUniform1i(glGetUniformLocation(id, name), if (value) 1 else 0)
    }

    /**
     * Specifies the value of an int uniform variable for this shader program object.
     * @param name the name of the uniform variable
     * @param value the value of the uniform variable
     * @see glUniform1i
     * @see glGetUniformLocation
     */
    operator fun set(name: CharSequence, value: Int) {
        glUniform1i(glGetUniformLocation(id, name), value)
    }

    /**
     * Specifies the value of a float uniform variable for this shader program object.
     * @param name the name of the uniform variable
     * @param value the value of the uniform variable
     * @see glUniform1f
     * @see glGetUniformLocation
     */
    operator fun set(name: CharSequence, value: Float) {
        glUniform1f(glGetUniformLocation(id, name), value)
    }

    /**
     * Specifies the value of a mat4 uniform variable for this shader program object.
     * @param name the name of the uniform variable
     * @param value the value of the uniform variable
     * @see glUniformMatrix4fv
     * @see glGetUniformLocation
     */
    operator fun set(name: CharSequence, value: Matrix4fc) {
        matrix4fBuffer.rewind()
        value.get(matrix4fBuffer)
        glUniformMatrix4fv(glGetUniformLocation(id, name), false, matrix4fBuffer)
    }

    /**
     * Specifies the value of a mat3 uniform variable for this shader program object.
     * @param name the name of the uniform variable
     * @param value the value of the uniform variable
     * @see glUniformMatrix3fv
     * @see glGetUniformLocation
     */
    operator fun set(name: CharSequence, value: Matrix3fc) {
        matrix4fBuffer.rewind()
        value.get(matrix4fBuffer)
        glUniformMatrix3fv(glGetUniformLocation(id, name), false, matrix4fBuffer)

    }

    /**
     * Specifies the value of a vec3 uniform variable for this shader program object.
     * @param name the name of the uniform variable
     * @param value the value of the uniform variable
     * @see glUniform3f
     * @see glGetUniformLocation
     */
    operator fun set(name: CharSequence, value: Vector3fc) {
        glUniform3f(glGetUniformLocation(id, name), value.x(), value.y(), value.z())
    }
}