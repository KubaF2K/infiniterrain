import org.lwjgl.opengl.GL33.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

val indexArrays = HashMap<Pair<Int, Int>, IntArray>()

fun getIndicesArray(width: Int, height: Int): IntArray {
    indexArrays[width, height]?.let {
        return it
    }

    val intArray = IntArray((width-1) * (height-1) * 6)

    for (x in 0..width-2) {
        for (y in 0..height-2) {
            intArray[(x + y * (width-1)) * 6] = x + y * width
            intArray[(x + y * (width-1)) * 6 + 1] = x + (y+1) * width
            intArray[(x + y * (width-1)) * 6 + 2] = x+1 + y * width
            intArray[(x + y * (width-1)) * 6 + 3] = x+1 + y * width
            intArray[(x + y * (width-1)) * 6 + 4] = x + (y+1) * width
            intArray[(x + y * (width-1)) * 6 + 5] = x+1 + (y+1) * width
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
    val width: Int,
    val height: Int
) {
    val intensities: Array<FloatArray> = Array(height) { FloatArray(width) }

    val indicesCount
        get() = (width-1) * (height-1) * 6

    fun add(intensities: Array<FloatArray>, strength: Float = 1f): Block {
        for (x in 0..<width) {
            for (y in 0..<height) {
                this.intensities[x][y] += intensities[x][y] * strength
            }
        }

        return this
    }

    fun multiply(strength: Float): Block {
        for (x in 0..<width) {
            for (y in 0..<height) {
                this.intensities[x][y] *= strength
            }
        }

        return this
    }

    fun addFractalPerlinNoise(
        count: Int,
        persistence: Float,
        lacunarity: Float,
        startChunksX: Int,
        startChunksY: Int,
        startChunkSize: Int,
        seed: Long = Random.nextLong()
    ): Block {
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

    fun lerpWithNeighbors(
        depth: Int,
        topNeighbor: Block? = null,
        rightNeighbor: Block? = null,
        bottomNeighbor: Block? = null,
        leftNeighbor: Block? = null
    ): Block {
        //TODO check depth

        for (x in 0..<width) {
            for (y in 0..<height) {
                if (y < depth) topNeighbor?.let { neighbor ->
                    intensities[y][x] = lerp(
                        fade(y/(depth-1).toFloat()),
                        neighbor.intensities[neighbor.height-1 - y][x],
                        intensities[y][x]
                    )
                }
                if (x > width-depth) rightNeighbor?.let { neighbor ->
                    intensities[y][x] = lerp(
                        fade((width-x)/(depth-1).toFloat()),
                        intensities[y][x],
                        neighbor.intensities[y][x - width + depth-1]
                    )
                }
                if (y > height-depth) bottomNeighbor?.let { neighbor ->
                    intensities[y][x] = lerp(
                        fade((height-y)/(depth-1).toFloat()),
                        intensities[y][x],
                        neighbor.intensities[y - height + depth-1][x]
                    )
                }
                if (x < depth) leftNeighbor?.let { neighbor ->
                    intensities[y][x] = lerp(
                        fade(x/(depth-1).toFloat()),
                        neighbor.intensities[y][neighbor.width-1 - x],
                        intensities[y][x]
                    )
                }
            }
        }
        return this
    }

    fun getVerticesArray(heightScale: Float = 1f): FloatArray {
        val floatArray = FloatArray(width * height * 3)

        for (x in 0..<width) {
            for (y in 0..<height) {
                floatArray[(x + y * width) * 3] = x/width.toFloat()*2f - 1f
                floatArray[(x + y * width) * 3 + 1] = intensities[x][y] * heightScale
                floatArray[(x + y * width) * 3 + 2] = y/height.toFloat()*2f - 1f
            }
        }

        return floatArray
    }

    fun getIndicesArray(): IntArray = getIndicesArray(width, height)

    fun setGLObjects(vao: Int, vbo: Int, ebo: Int, heightScale: Float = 1f) {
        glBindVertexArray(vao)

        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        val vertices = getVerticesArray(heightScale)
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW)

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo)
        val indices = getIndicesArray()
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW)

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.SIZE_BYTES, 0)
        glEnableVertexAttribArray(0)

        glBindVertexArray(0)
    }
}