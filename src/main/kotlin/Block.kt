import org.lwjgl.BufferUtils
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

fun fade(x: Float) = 6 * x.pow(5) - 15 * x.pow(4) + 10 * x.pow(3)
fun lerp(t: Float, a1: Float, a2: Float) = (1-t) * a1 + t * a2

class Block(
    val chunksX: Int,
    val chunksY: Int,
    val chunkSize: Int,
    seed: Long = Random.nextLong(),
    topNeighbor: Block? = null,
    rightNeighbor: Block? = null,
    bottomNeighbor: Block? = null,
    leftNeighbor: Block? = null
) {
    private val random = Random(seed)
    
    val vectors: Array<FloatArray> =
        (Array(chunksY + 1) { FloatArray(chunksX + 1) { random.nextFloat() * Math.PI.toFloat() * 2f } }).apply {
            topNeighbor?.let {
                for (x in 0..chunksX) {
                    this[0][x] = it.vectors[chunksY][x]
                }
            }
            rightNeighbor?.let {
                for (y in 0..chunksY) {
                    this[y][chunksX] = it.vectors[y][0]
                }
            }
            bottomNeighbor?.let {
                for (x in 0..chunksX) {
                    this[chunksY][x] = it.vectors[0][x]
                }
            }
            leftNeighbor?.let {
                for (y in 0..chunksY) {
                    this[y][0] = it.vectors[y][chunksY]
                }
            }
        }
    
    val intensities: Array<FloatArray> = Array(chunksY * chunkSize) { y ->
        FloatArray(chunksX * chunkSize) { x ->
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

            var finalValue = lerp(yf, topLerp, bottomLerp)

            leftNeighbor?.let {
                if (chunkX != 0) return@let

                finalValue = lerp(
                    xf,
                    it.intensities[y][it.width-1 - a],
                    finalValue
                )
            }
            rightNeighbor?.let {
                if (chunkX != chunksX-1) return@let

                finalValue = lerp(
                    xf,
                    finalValue,
                    it.intensities[y][chunkSize-1 - a]
                )
            }
            topNeighbor?.let {
                if (chunkY != chunksY-1) return@let

                finalValue = lerp(
                    yf,
                    finalValue,
                    it.intensities[chunkSize-1 - b][x]
                )
            }
            bottomNeighbor?.let {
                if (chunkY != 0) return@let

                finalValue = lerp(
                    yf,
                    it.intensities[it.height-1 - b][x],
                    finalValue
                )
            }
            //TODO corners, ungenerated neighbors

            return@FloatArray finalValue
        }
    }

    val width
        get() = chunksX * chunkSize
    val height
        get() = chunksY * chunkSize

    fun getNormalizedIntensities(): Array<FloatArray> {
        var maxIntensity = intensities[0][0]
        var minIntensity = intensities[0][0]

        for (row in intensities) {
            for (intensity in row) {
                if (intensity > maxIntensity) {
                    maxIntensity = intensity
                }
                if (intensity < minIntensity) {
                    minIntensity = intensity
                }
            }
        }

        return intensities.map { column ->
             column.map { intensity ->
                 if (minIntensity == maxIntensity) {
                     0.5f
                 } else {
                     (intensity-minIntensity)/(maxIntensity-minIntensity)
                 }
             }.toFloatArray()
        }.toTypedArray()
    }

    fun getBrightnessGLBuffer(): ByteBuffer {
        val buffer = BufferUtils.createByteBuffer(width * height * 3)
        for (row in getNormalizedIntensities()) {
            for (intensity in row) {
                val intensityByte = (intensity * 255).toInt().toByte()
                buffer.put(intensityByte)
                buffer.put(intensityByte)
                buffer.put(intensityByte)
            }
        }
        buffer.rewind()
        return buffer
    }
    fun generateOctaves(count: Int, persistence: Float, lacunarity: Float): Block {
        var newChunksX = chunksX
        var newChunksY = chunksY

        var newChunkSize = chunkSize

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

            val octave = Block(newChunksX, newChunksY, newChunkSize, random.nextLong())

            add(octave.intensities, strength)
        }

        return this
    }

    fun add(intensities: Array<FloatArray>, strength: Float) {
        for (x in 0..<width) {
            for (y in 0..<height) {
                this.intensities[x][y] += intensities[x][y] * strength
            }
        }
    }
}