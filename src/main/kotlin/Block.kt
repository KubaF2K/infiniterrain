import org.joml.Vector3f
import org.lwjgl.opengl.GL33.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

val indexArrays = HashMap<Pair<Int, Int>, IntArray>()

fun getIndicesArray(width: Int, height: Int): IntArray = indexArrays[width, height] ?: run {
    val intArray = IntArray((width - 1) * (height - 1) * 6)

    for (x in 0..width - 2) {
        for (y in 0..height - 2) {
            intArray[(x + y * (width - 1)) * 6] = x + y * width
            intArray[(x + y * (width - 1)) * 6 + 1] = x + (y + 1) * width
            intArray[(x + y * (width - 1)) * 6 + 2] = x + 1 + y * width
            intArray[(x + y * (width - 1)) * 6 + 3] = x + 1 + y * width
            intArray[(x + y * (width - 1)) * 6 + 4] = x + (y + 1) * width
            intArray[(x + y * (width - 1)) * 6 + 5] = x + 1 + (y + 1) * width
        }
    }

    indexArrays[width, height] = intArray

    return intArray
}

fun createPerlinBlock(
    chunksX: Int,
    chunksY: Int,
    chunkSize: Int,
    seed: Long = Random.nextLong()
): Block {
    val random = Random(seed)

    val vectors: Array<FloatArray> =
        (Array(chunksY + 1) { FloatArray(chunksX + 1) { random.nextFloat() * Math.PI.toFloat() * 2f } })

    val block = Block(chunksX * chunkSize, chunksY * chunkSize)

    for (y in 0..<chunksY*chunkSize) {
        for (x in 0..<chunksX*chunkSize) {
            val chunkX = x / chunkSize
            val chunkY = y / chunkSize

            val a = x % chunkSize
            val b = y % chunkSize

            val topLeftVector = vectors[chunkY][chunkX]
            val topRightVector = vectors[chunkY][chunkX+1]
            val bottomLeftVector = vectors[chunkY+1][chunkX]
            val bottomRightVector = vectors[chunkY+1][chunkX+1]

            val topLeftIntensity = (-a * cos(topLeftVector)) + (-b * sin(topLeftVector))
            val topRightIntensity = ((chunkSize - a) * cos(topRightVector)) + (-b * sin(topRightVector))
            val bottomLeftIntensity = (-a * cos(bottomLeftVector)) + ((chunkSize - b) * sin(bottomLeftVector))
            val bottomRightIntensity =
                ((chunkSize - a) * cos(bottomRightVector)) + ((chunkSize - b) * sin(bottomRightVector))

            val xf = fade(a.toFloat() / chunkSize)
            val yf = fade(b.toFloat() / chunkSize)

            val topLerp = lerp(xf, topLeftIntensity, topRightIntensity)
            val bottomLerp = lerp(xf, bottomLeftIntensity, bottomRightIntensity)

            block.intensities[x][y] = lerp(yf, topLerp, bottomLerp)
        }
    }
    return block
}

class Block(
    /**
     * The width of this block in vertices.
     */
    val width: Int,
    /**
     * The height of this block in vertices.
     */
    val height: Int
) {
    val intensities: Array<FloatArray> = Array(height) { FloatArray(width) }

    /**
     * The number of indices required to draw all the triangles in this block.
     */
    val indicesCount
        get() = (width-1) * (height-1) * 6

    /**
     * Adds the given intensities to this block. The intensities are required to be the same size or larger than this
     * block.
     * @param intensities The intensities to add
     * @param strength The strength to add the intensities with
     * @return This block
     * @throws IllegalArgumentException if the intensities are the wrong size
     */
    fun add(intensities: Array<FloatArray>, strength: Float = 1f): Block {
        if (intensities.size < width || intensities[0].size < height)
            throw IllegalArgumentException("Intensities must be the same size or larger than this block")

        for (x in 0..<width) {
            for (y in 0..<height) {
                this.intensities[x][y] += intensities[x][y] * strength
            }
        }

        return this
    }

    /**
     * Multiplies all intensities in this block by the given strength.
     * @param strength The strength to multiply by
     * @return This block
     */
    fun multiply(strength: Float): Block {
        for (x in 0..<width) {
            for (y in 0..<height) {
                this.intensities[x][y] *= strength
            }
        }

        return this
    }

    /**
     * Adds fractal Perlin noise to this block. The parameters determine the size of the noise.
     * @param count The number of octaves of noise to add
     * @param persistence The persistence of the noise
     * @param lacunarity The lacunarity of the noise
     * @param startChunksX The number of chunks of noise to start with in the x direction
     * @param startChunksY The number of chunks of noise to start with in the y direction
     * @param startChunkSize The size of the chunks of noise to start with
     * @param seed The seed for the random number generator (default: random)
     * @return This block
     * @throws IllegalArgumentException if lacunarity is 0
     */
    fun addFractalPerlinNoise(
        count: Int,
        persistence: Float,
        lacunarity: Float,
        startChunksX: Int,
        startChunksY: Int,
        startChunkSize: Int,
        seed: Long = Random.nextLong()
    ): Block {
        if (lacunarity == 0f)
            throw IllegalArgumentException("Lacunarity cannot be 0")

        val random = Random(seed)

        var newChunksX = startChunksX
        var newChunksY = startChunksY

        var newChunkSize = startChunkSize

        var strength = 1f

        for (i in 0..<count) {
            newChunksX = (newChunksX.toFloat() * lacunarity).toInt()
            newChunksY = (newChunksY.toFloat() * lacunarity).toInt()

            newChunkSize = (newChunkSize.toFloat() / lacunarity).toInt()

            if (newChunkSize == 0) {
                break
            }

            strength *= persistence

            while (newChunksX * newChunkSize < width) {
                newChunksX++
            }
            while (newChunksY * newChunkSize < height) {
                newChunksY++
            }

            val octave = createPerlinBlock(newChunksX, newChunksY, newChunkSize, random.nextLong())

            add(octave.intensities, strength)
        }

        return this
    }

    /**
     * Linearly interpolates the intensities of this block with the intensities of its neighbors for smooth transitions
     * between them. The neighbors are required to be the same size on the touching edge as this block. The depth
     * parameter determines how deep into the block to lerp. TODO: fix corners
     * @param depth How deep into the block to lerp
     * @param topNeighbor The block above this one
     * @param rightNeighbor The block to the right of this one
     * @param bottomNeighbor The block below this one
     * @param leftNeighbor The block to the left of this one
     * @return This block
     * @throws IllegalArgumentException if depth is less than 1 or more than the width or height of the block
     * @throws IllegalArgumentException if any of the neighbors are the wrong size (or smaller than the depth)
     */
    fun lerpWithNeighbors(
        depth: Int,
        topNeighbor: Block? = null,
        rightNeighbor: Block? = null,
        bottomNeighbor: Block? = null,
        leftNeighbor: Block? = null
    ): Block {
        if (depth < 1 || depth > width || depth > height)
            throw IllegalArgumentException("Depth must be between 1 and the width/height of the block")
        topNeighbor?.let {
            if (topNeighbor.width != width || topNeighbor.height < depth)
                throw IllegalArgumentException(
                    "Top neighbor must be the same width as this block and at least as tall as the depth"
                )
        }
        rightNeighbor?.let {
            if (rightNeighbor.height != height || rightNeighbor.width < depth)
                throw IllegalArgumentException(
                    "Right neighbor must be the same height as this block and at least as wide as the depth"
                )
        }
        bottomNeighbor?.let {
            if (bottomNeighbor.width != width || bottomNeighbor.height < depth)
                throw IllegalArgumentException(
                    "Bottom neighbor must be the same width as this block and at least as tall as the depth"
                )
        }
        leftNeighbor?.let {
            if (leftNeighbor.height != height || leftNeighbor.width < depth)
                throw IllegalArgumentException(
                    "Left neighbor must be the same height as this block and at least as wide as the depth"
                )
        }

        for (x in 0..<width) {
            for (y in 0..<height) {
                if (y < depth) topNeighbor?.let { neighbor ->
                    intensities[x][y] = lerp(
                        fade(y/(depth-1).toFloat()),
                        neighbor.intensities[x][neighbor.height-1 - y],
                        intensities[x][y]
                    )
                }
                if (x > width-depth) rightNeighbor?.let { neighbor ->
                    intensities[x][y] = lerp(
                        fade((width-x)/(depth-1).toFloat()),
                        neighbor.intensities[-x + width + 1][y],
                        intensities[x][y]
                    )
                }
                if (y > height-depth) bottomNeighbor?.let { neighbor ->
                    intensities[x][y] = lerp(
                        fade((height-y)/(depth-1).toFloat()),
                        neighbor.intensities[x][-y + height + 1],
                        intensities[x][y]
                    )
                }
                if (x < depth) leftNeighbor?.let { neighbor ->
                    intensities[x][y] = lerp(
                        fade(x/(depth-1).toFloat()),
                        neighbor.intensities[neighbor.width-1 - x][y],
                        intensities[x][y]
                    )
                }
            }
        }
        return this
    }

    private fun getVerticesArrayWithVertexNormals(heightScale: Float = 1f): FloatArray {
        val floatArray = FloatArray(width * height * 6)

        for (x in 0..<width) {
            for (y in 0..<height) {
            // TODO
            }
        }

        return floatArray
    }

    private fun getVerticesArrayWithFaceNormals(heightScale: Float = 1f): FloatArray {
        val floatArray = FloatArray(width * height * 6)

        for (x in 0..<width step 2) {
            for (y in 0..<height step 2) {
                val topLeft = Vector3f(x/(width-1).toFloat()*2f - 1f, intensities[x][y] * heightScale, y/(height-1).toFloat()*2f - 1f)
                val topRight = Vector3f((x+1)/(width-1).toFloat()*2f - 1f, intensities[x+1][y] * heightScale, y/(height-1).toFloat()*2f - 1f)
                val bottomLeft = Vector3f(x/(width-1).toFloat()*2f - 1f, intensities[x][y+1] * heightScale, (y+1)/(height-1).toFloat()*2f - 1f)
                val bottomRight = Vector3f((x+1)/(width-1).toFloat()*2f - 1f, intensities[x+1][y+1] * heightScale, (y+1)/(height-1).toFloat()*2f - 1f)

                val u = topLeft - topRight
                val v = bottomLeft - topRight

                val normal = (u cross v).normalize()

                floatArray[(x + y * width) * 6] = topLeft.x()
                floatArray[(x + y * width) * 6 + 1] = topLeft.y()
                floatArray[(x + y * width) * 6 + 2] = topLeft.z()
                floatArray[(x + y * width) * 6 + 3] = normal.x()
                floatArray[(x + y * width) * 6 + 4] = normal.y()
                floatArray[(x + y * width) * 6 + 5] = normal.z()

                floatArray[((x+1) + y * width) * 6] = topRight.x()
                floatArray[((x+1) + y * width) * 6 + 1] = topRight.y()
                floatArray[((x+1) + y * width) * 6 + 2] = topRight.z()
                floatArray[((x+1) + y * width) * 6 + 3] = normal.x()
                floatArray[((x+1) + y * width) * 6 + 4] = normal.y()
                floatArray[((x+1) + y * width) * 6 + 5] = normal.z()

                floatArray[(x + (y+1) * width) * 6] = bottomLeft.x()
                floatArray[(x + (y+1) * width) * 6 + 1] = bottomLeft.y()
                floatArray[(x + (y+1) * width) * 6 + 2] = bottomLeft.z()
                floatArray[(x + (y+1) * width) * 6 + 3] = normal.x()
                floatArray[(x + (y+1) * width) * 6 + 4] = normal.y()
                floatArray[(x + (y+1) * width) * 6 + 5] = normal.z()

                floatArray[((x+1) + (y+1) * width) * 6] = bottomRight.x()
                floatArray[((x+1) + (y+1) * width) * 6 + 1] = bottomRight.y()
                floatArray[((x+1) + (y+1) * width) * 6 + 2] = bottomRight.z()
                floatArray[((x+1) + (y+1) * width) * 6 + 3] = normal.x()
                floatArray[((x+1) + (y+1) * width) * 6 + 4] = normal.y()
                floatArray[((x+1) + (y+1) * width) * 6 + 5] = normal.z()
            }
        }

        return floatArray
    }

    private fun getIndicesArray(): IntArray = getIndicesArray(width, height)

    /**
     * Sets the GL objects for this block. If any of the parameters are null, new objects will be created.
     * @return A Triple(vao, vbo, ebo) containing IDs of the GL objects
     * @param vao Vertex Array Object ID
     * @param vbo Vertex Buffer Object ID
     * @param ebo Element Buffer Object ID
     * @param vertexNormals Whether to generate vertex normals or face normals
     * @param heightScale How much to scale the height by
     */
    fun setGLObjects(vao: Int? = null, vbo: Int? = null, ebo: Int? = null, vertexNormals: Boolean = false, heightScale: Float = 1f): Triple<Int, Int, Int> {
        val lVao = vao ?: glGenVertexArrays()
        val lVbo = vbo ?: glGenBuffers()
        val lEbo = ebo ?: glGenBuffers()

        glBindVertexArray(lVao)

        glBindBuffer(GL_ARRAY_BUFFER, lVbo)
        val vertices =
            if (vertexNormals)
                getVerticesArrayWithVertexNormals(heightScale)
            else
                getVerticesArrayWithFaceNormals(heightScale)

        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW)

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, lEbo)
        val indices = getIndicesArray()
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW)

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.SIZE_BYTES, 0)
        glEnableVertexAttribArray(0)
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * Float.SIZE_BYTES, 3 * Float.SIZE_BYTES.toLong())
        glEnableVertexAttribArray(1)

        glBindVertexArray(0)

        return Triple(lVao, lVbo, lEbo)
    }
}