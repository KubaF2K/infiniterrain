import org.lwjgl.BufferUtils
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

fun fade(x: Double) = 6 * x.pow(5) - 15*x.pow(4) + 10*x.pow(3)
fun lerp(t: Double, a1: Double, a2: Double) = (1-t) * a1 + t * a2

class Perlin(val chunksX: Int, val chunksY: Int, val chunkSize: Int, seed: Long) {
    private val random = Random(seed)
    val vectors = Array(chunksY + 1) { DoubleArray(chunksX + 1) { random.nextDouble() * Math.PI * 2 } }
    val intensities = Array(chunksY * chunkSize) { y ->
        DoubleArray(chunksX * chunkSize) { x ->
            val chunkX = x / chunkSize
            val chunkY = y / chunkSize

            val a = x % chunkSize
            val b = y % chunkSize

            val topLeftVector = vectors[chunkX][chunkY]
            val topRightVector = vectors[chunkX + 1][chunkY]
            val bottomLeftVector = vectors[chunkX][chunkY + 1]
            val bottomRightVector = vectors[chunkX + 1][chunkY + 1]

            val topLeftIntensity = (-a * cos(topLeftVector)) + (-b * sin(topLeftVector))
            val topRightIntensity = ((chunkSize - a) * cos(topRightVector)) + (-b * sin(topRightVector))
            val bottomLeftIntensity = (-a * cos(bottomLeftVector)) + ((chunkSize - b) * sin(bottomLeftVector))
            val bottomRightIntensity =
                ((chunkSize - a) * cos(bottomRightVector)) + ((chunkSize - b) * sin(bottomRightVector))

            val xf = fade(a.toDouble() / chunkSize)
            val yf = fade(b.toDouble() / chunkSize)

            val topLerp = lerp(xf, topLeftIntensity, topRightIntensity)
            val bottomLerp = lerp(xf, bottomLeftIntensity, bottomRightIntensity)

            return@DoubleArray lerp(yf, topLerp, bottomLerp)
        }
    }

    val width
        get() = chunksX * chunkSize
    val height
        get() = chunksY * chunkSize

    fun getNormalizedIntensities(): Array<DoubleArray> {
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
                         0.5
                     } else {
                         (intensity-minIntensity)/(maxIntensity-minIntensity)
                     }
                 }.toDoubleArray()
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
    fun generateOctaves(count: Int, persistence: Double, lacunarity: Double) {
        var newChunksX = chunksX
        var newChunksY = chunksY

        var newChunkSize = chunkSize

        var strength = 1.0

        for (i in 0..<count) {
            newChunksX = (newChunksX.toDouble() * lacunarity).toInt()
            newChunksY = (newChunksY.toDouble() * lacunarity).toInt()

            newChunkSize = (newChunkSize.toDouble() / lacunarity).toInt()

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

            val octave = Perlin(newChunksX, newChunksY, newChunkSize, random.nextLong())

            add(octave.intensities, strength)
        }
    }

    fun add(intensities: Array<DoubleArray>, strength: Double) {
        for (x in 0..<width) {
            for (y in 0..<height) {
                this.intensities[x][y] += intensities[x][y] * strength
            }
        }
    }
}