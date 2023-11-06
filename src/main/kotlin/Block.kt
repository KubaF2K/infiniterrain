import org.lwjgl.BufferUtils
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

fun fade(x: Double) = 6 * x.pow(5) - 15*x.pow(4) + 10*x.pow(3)
fun lerp(t: Double, a1: Double, a2: Double) = (1-t) * a1 + t * a2

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
    
    val vectors: Array<DoubleArray> =
        (Array(chunksY + 1) { DoubleArray(chunksX + 1) { random.nextDouble() * Math.PI * 2 } }).apply {
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
    
    val intensities: Array<DoubleArray> = Array(chunksY * chunkSize) { y ->
        DoubleArray(chunksX * chunkSize) { x ->
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

            val xf = fade(a.toDouble() / chunkSize)
            val yf = fade(b.toDouble() / chunkSize)

            val topLerp = lerp(xf, topLeftIntensity, topRightIntensity)
            val bottomLerp = lerp(xf, bottomLeftIntensity, bottomRightIntensity)

            var finalValue = lerp(yf, topLerp, bottomLerp)

            leftNeighbor?.let {
                if (chunkX != 0) return@let

                println("left neighbor")
                println("x: $x")
                println("y: $y")
                println("other x: ${it.width-1 - x}")
                println("other y: $y")
                println("value: $finalValue")
                println("other value: ${it.intensities[y][it.width-1 - x]}")
                finalValue = lerp(
                    xf,
                    it.intensities[y][it.width-1 - x],
                    finalValue
                )
                println("final value: $finalValue")
            }
            rightNeighbor?.let {
                if (chunkX != chunksX-1) return@let

                finalValue = lerp(
                    xf,
                    finalValue,
                    it.intensities[y][x - (width-1) + chunkSize]
                )
            }
            topNeighbor?.let {
                if (chunkY != 0) return@let

                finalValue = lerp(
                    yf,
                    it.intensities[it.height-1 - y][x],
                    finalValue
                )
            }
            bottomNeighbor?.let {
                if (chunkY != chunksY-1) return@let

                finalValue = lerp(
                    yf,
                    finalValue,
                    it.intensities[y - (height-1) + chunkSize][x]
                )
            }
            //TODO corners, ungenerated neighbors

            return@DoubleArray finalValue
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
    fun generateOctaves(count: Int, persistence: Double, lacunarity: Double): Block {
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

            val octave = Block(newChunksX, newChunksY, newChunkSize, random.nextLong())

            add(octave.intensities, strength)
        }

        return this
    }

    fun add(intensities: Array<DoubleArray>, strength: Double) {
        for (x in 0..<width) {
            for (y in 0..<height) {
                this.intensities[x][y] += intensities[x][y] * strength
            }
        }
    }
}