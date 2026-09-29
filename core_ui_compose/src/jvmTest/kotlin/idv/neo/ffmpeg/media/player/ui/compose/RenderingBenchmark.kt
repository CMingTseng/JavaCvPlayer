package idv.neo.ffmpeg.media.player.ui.compose

import org.junit.Test
import java.awt.GraphicsEnvironment
import java.awt.Transparency
import java.awt.image.BufferedImage
import kotlin.system.measureTimeMillis

class RenderingBenchmark {

    @Test
    fun benchmark4KRendering() {
        val width = 3840
        val height = 2160
        val iterations = 100

        println("Starting 4K Rendering Benchmark ($iterations iterations)...")

        // 1. Prepare dummy data
        val bi = BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR)

        // 2. Test BufferedImage Deep Copy (Baseline)
        val t1 = measureTimeMillis {
            repeat(iterations) {
                val copy = BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR)
                val g = copy.createGraphics()
                g.drawImage(bi, 0, 0, null)
                g.dispose()
            }
        }
        println("BufferedImage Deep Copy: ${t1.toDouble() / iterations} ms/frame")

        // 3. Test VolatileImage Draw + Snapshot
        val config = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice.defaultConfiguration
        val vi = config.createCompatibleVolatileImage(width, height, Transparency.OPAQUE)

        val t2 = measureTimeMillis {
            repeat(iterations) {
                // Draw to VRAM
                val g = vi.createGraphics()
                g.drawImage(bi, 0, 0, null)
                g.dispose()

                // Read back from VRAM (The bottleneck)
                val snapshot = vi.snapshot
            }
        }
        println("VolatileImage (Draw + Snapshot): ${t2.toDouble() / iterations} ms/frame")

        // 4. Test VolatileImage Draw ONLY (VRAM to VRAM)
        val t3 = measureTimeMillis {
            repeat(iterations) {
                val g = vi.createGraphics()
                g.drawImage(bi, 0, 0, null)
                g.dispose()
            }
        }
        println("VolatileImage (Draw Only): ${t3.toDouble() / iterations} ms/frame")
    }
}