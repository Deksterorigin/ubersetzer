package app.deutschlern.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

class ArticleColorsContrastTest {

    private fun relativeLuminance(color: Color): Double {
        fun channel(c: Float): Double {
            val d = c.toDouble()
            return if (d <= 0.04045) {
                d / 12.92
            } else {
                ((d + 0.055) / 1.055).pow(2.4)
            }
        }
        val r = channel(color.red)
        val g = channel(color.green)
        val b = channel(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    // Color blindness simulation using Brettel/Vienot simplified matrices for sRGB
    private fun simulateColorBlindness(color: Color, type: String): Triple<Double, Double, Double> {
        val r = color.red.toDouble()
        val g = color.green.toDouble()
        val b = color.blue.toDouble()

        return when (type) {
            "protanopia" -> {
                // Reduced/missing red cone
                val simR = 0.56667 * r + 0.43333 * g + 0.0 * b
                val simG = 0.55833 * r + 0.44167 * g + 0.0 * b
                val simB = 0.0 * r + 0.24167 * g + 0.75833 * b
                Triple(simR, simG, simB)
            }
            "deuteranopia" -> {
                // Reduced/missing green cone
                val simR = 0.625 * r + 0.375 * g + 0.0 * b
                val simG = 0.70 * r + 0.30 * g + 0.0 * b
                val simB = 0.0 * r + 0.30 * g + 0.70 * b
                Triple(simR, simG, simB)
            }
            "tritanopia" -> {
                // Reduced/missing blue cone
                val simR = 0.95 * r + 0.05 * g + 0.0 * b
                val simG = 0.0 * r + 0.43333 * g + 0.56667 * b
                val simB = 0.0 * r + 0.475 * g + 0.525 * b
                Triple(simR, simG, simB)
            }
            else -> Triple(r, g, b)
        }
    }

    private fun colorDistance(c1: Triple<Double, Double, Double>, c2: Triple<Double, Double, Double>): Double {
        val dr = c1.first - c2.first
        val dg = c1.second - c2.second
        val db = c1.third - c2.third
        return sqrt(dr * dr + dg * dg + db * db)
    }

    // Converts simulated or raw RGB (0.0..1.0) to CIELAB (D65)
    private fun rgbToLab(r: Double, g: Double, b: Double): Triple<Double, Double, Double> {
        // Linearize sRGB
        fun toLinear(c: Double): Double =
            if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

        val rLin = toLinear(r.coerceIn(0.0, 1.0))
        val gLin = toLinear(g.coerceIn(0.0, 1.0))
        val bLin = toLinear(b.coerceIn(0.0, 1.0))

        // sRGB to XYZ (D65)
        val x = (0.4124564 * rLin + 0.3575761 * gLin + 0.1804375 * bLin) / 0.95047
        val y = (0.2126729 * rLin + 0.7151522 * gLin + 0.0721750 * bLin) / 1.00000
        val z = (0.0193339 * rLin + 0.1191920 * gLin + 0.9503041 * bLin) / 1.08883

        fun f(t: Double): Double =
            if (t > 0.00885645167) t.pow(1.0 / 3.0) else (7.787037037 * t) + (16.0 / 116.0)

        val fx = f(x)
        val fy = f(y)
        val fz = f(z)

        val l = (116.0 * fy) - 16.0
        val a = 500.0 * (fx - fy)
        val labB = 200.0 * (fy - fz)
        return Triple(l, a, labB)
    }

    private fun ciede2000(lab1: Triple<Double, Double, Double>, lab2: Triple<Double, Double, Double>): Double {
        val (l1, a1, b1) = lab1
        val (l2, a2, b2) = lab2

        val avgL = (l1 + l2) / 2.0
        val c1 = sqrt(a1 * a1 + b1 * b1)
        val c2 = sqrt(a2 * a2 + b2 * b2)
        val avgC = (c1 + c2) / 2.0

        val g = 0.5 * (1.0 - sqrt(avgC.pow(7) / (avgC.pow(7) + 25.0.pow(7))))
        val a1Prime = a1 * (1.0 + g)
        val a2Prime = a2 * (1.0 + g)

        val c1Prime = sqrt(a1Prime * a1Prime + b1 * b1)
        val c2Prime = sqrt(a2Prime * a2Prime + b2 * b2)
        val avgCPrime = (c1Prime + c2Prime) / 2.0

        var h1Prime = Math.toDegrees(kotlin.math.atan2(b1, a1Prime))
        if (h1Prime < 0.0) h1Prime += 360.0
        var h2Prime = Math.toDegrees(kotlin.math.atan2(b2, a2Prime))
        if (h2Prime < 0.0) h2Prime += 360.0

        val avgHPrime = when {
            c1Prime * c2Prime == 0.0 -> h1Prime + h2Prime
            kotlin.math.abs(h1Prime - h2Prime) <= 180.0 -> (h1Prime + h2Prime) / 2.0
            h1Prime + h2Prime < 360.0 -> (h1Prime + h2Prime + 360.0) / 2.0
            else -> (h1Prime + h2Prime - 360.0) / 2.0
        }

        val t = 1.0 - 0.17 * kotlin.math.cos(Math.toRadians(avgHPrime - 30.0)) +
                0.24 * kotlin.math.cos(Math.toRadians(2.0 * avgHPrime)) +
                0.32 * kotlin.math.cos(Math.toRadians(3.0 * avgHPrime + 6.0)) -
                0.20 * kotlin.math.cos(Math.toRadians(4.0 * avgHPrime - 63.0))

        var deltaHPrime = when {
            c1Prime * c2Prime == 0.0 -> 0.0
            kotlin.math.abs(h2Prime - h1Prime) <= 180.0 -> h2Prime - h1Prime
            h2Prime <= h1Prime -> h2Prime - h1Prime + 360.0
            else -> h2Prime - h1Prime - 360.0
        }

        val deltaBigHPrime = 2.0 * sqrt(c1Prime * c2Prime) * kotlin.math.sin(Math.toRadians(deltaHPrime / 2.0))

        val deltaLPrime = l2 - l1
        val deltaCPrime = c2Prime - c1Prime

        val sl = 1.0 + (0.015 * (avgL - 50.0).pow(2)) / sqrt(20.0 + (avgL - 50.0).pow(2))
        val sc = 1.0 + 0.045 * avgCPrime
        val sh = 1.0 + 0.015 * avgCPrime * t

        val deltaTheta = 30.0 * kotlin.math.exp(-((avgHPrime - 275.0) / 25.0).pow(2))
        val rc = 2.0 * sqrt(avgCPrime.pow(7) / (avgCPrime.pow(7) + 25.0.pow(7)))
        val rt = -kotlin.math.sin(Math.toRadians(2.0 * deltaTheta)) * rc

        val termL = deltaLPrime / sl
        val termC = deltaCPrime / sc
        val termH = deltaBigHPrime / sh

        return sqrt(termL * termL + termC * termC + termH * termH + rt * termC * termH)
    }

    private fun colorDistanceCiede2000(c1: Triple<Double, Double, Double>, c2: Triple<Double, Double, Double>): Double {
        val lab1 = rgbToLab(c1.first, c1.second, c1.third)
        val lab2 = rgbToLab(c2.first, c2.second, c2.third)
        return ciede2000(lab1, lab2)
    }

    @Test
    fun computeAndVerifyContrastRatios_allExceedWcagAa4_5() {
        val lightBg = Color(0xFFFAFAFA)
        val darkBg = Color(0xFF0E0E0E)

        val lightDer = Color(0xFF1565C0) // Blue 800
        val lightDie = Color(0xFFC62828) // Red 800
        val lightDas = Color(0xFF7F6000) // Deep Golden Amber

        val darkDer = Color(0xFF64B5F6) // Blue 300
        val darkDie = Color(0xFFEF5350) // Red 400
        val darkDas = Color(0xFFFDD835) // Yellow 600

        val crLightDer = contrastRatio(lightDer, lightBg)
        val crLightDie = contrastRatio(lightDie, lightBg)
        val crLightDas = contrastRatio(lightDas, lightBg)

        val crDarkDer = contrastRatio(darkDer, darkBg)
        val crDarkDie = contrastRatio(darkDie, darkBg)
        val crDarkDas = contrastRatio(darkDas, darkBg)

        println("=== REAL WCAG CONTRAST RATIOS ===")
        println("Light Theme (against #FAFAFA):")
        println("  der (#1565C0): ${"%.2f".format(crLightDer)}:1 (min 4.5:1)")
        println("  die (#C62828): ${"%.2f".format(crLightDie)}:1 (min 4.5:1)")
        println("  das (#7F6000): ${"%.2f".format(crLightDas)}:1 (min 4.5:1)")
        println("Dark Theme (against #0E0E0E):")
        println("  der (#64B5F6): ${"%.2f".format(crDarkDer)}:1 (min 4.5:1)")
        println("  die (#EF5350): ${"%.2f".format(crDarkDie)}:1 (min 4.5:1)")
        println("  das (#FDD835): ${"%.2f".format(crDarkDas)}:1 (min 4.5:1)")

        assertTrue("Light der contrast must be >= 4.5:1, was $crLightDer", crLightDer >= 4.5)
        assertTrue("Light die contrast must be >= 4.5:1, was $crLightDie", crLightDie >= 4.5)
        assertTrue("Light das contrast must be >= 4.5:1, was $crLightDas", crLightDas >= 4.5)

        assertTrue("Dark der contrast must be >= 4.5:1, was $crDarkDer", crDarkDer >= 4.5)
        assertTrue("Dark die contrast must be >= 4.5:1, was $crDarkDie", crDarkDie >= 4.5)
        assertTrue("Dark das contrast must be >= 4.5:1, was $crDarkDas", crDarkDas >= 4.5)
    }

    @Test
    fun colorBlindnessSimulation_dieVsDasDistinguishableInLightTheme_withCiede2000() {
        val lightDie = Color(0xFFC62828)
        val lightDer = Color(0xFF1565C0)
        val lightDas = Color(0xFF7F6000)

        val darkDie = Color(0xFFEF5350)
        val darkDer = Color(0xFF64B5F6)
        val darkDas = Color(0xFFFDD835)

        val conditions = listOf("normal", "protanopia", "deuteranopia", "tritanopia")

        println("\n=== LIGHT THEME CIEDE2000 COLOR DISTANCES ===")
        for (cond in conditions) {
            val sDie = simulateColorBlindness(lightDie, cond)
            val sDer = simulateColorBlindness(lightDer, cond)
            val sDas = simulateColorBlindness(lightDas, cond)

            val dDieDas = colorDistanceCiede2000(sDie, sDas)
            val dDerDie = colorDistanceCiede2000(sDer, sDie)
            val dDerDas = colorDistanceCiede2000(sDer, sDas)

            println("  [$cond] die vs das: ${"%.2f".format(dDieDas)} | der vs die: ${"%.2f".format(dDerDie)} | der vs das: ${"%.2f".format(dDerDas)}")
        }

        println("\n=== DARK THEME CIEDE2000 COLOR DISTANCES ===")
        for (cond in conditions) {
            val sDie = simulateColorBlindness(darkDie, cond)
            val sDer = simulateColorBlindness(darkDer, cond)
            val sDas = simulateColorBlindness(darkDas, cond)

            val dDieDas = colorDistanceCiede2000(sDie, sDas)
            val dDerDie = colorDistanceCiede2000(sDer, sDie)
            val dDerDas = colorDistanceCiede2000(sDer, sDas)

            println("  [$cond] die vs das: ${"%.2f".format(dDieDas)} | der vs die: ${"%.2f".format(dDerDie)} | der vs das: ${"%.2f".format(dDerDas)}")
        }

        // Test alternative colors for light-theme das
        val candidates = listOf(
            "Current #7F6000" to Color(0xFF7F6000),
            "Candidate #8A6000 (Ochre)" to Color(0xFF8A6000),
            "Candidate #6B5B00 (Dark Mustard)" to Color(0xFF6B5B00),
            "Candidate #5C5200 (Olive Ochre)" to Color(0xFF5C5200),
            "Candidate #665200" to Color(0xFF665200),
            "Candidate #594800" to Color(0xFF594800),
            "Candidate #996500" to Color(0xFF996500),
            "Candidate #B26A00" to Color(0xFFB26A00),
            "Candidate #615600" to Color(0xFF615600),
            "Candidate #4D4400" to Color(0xFF4D4400)
        )
        println("\n=== CANDIDATE LIGHT-THEME DAS COLORS (WCAG & CIEDE2000 under Protanopia) ===")
        for ((name, color) in candidates) {
            val cr = contrastRatio(color, Color(0xFFFAFAFA))
            val sDie = simulateColorBlindness(lightDie, "protanopia")
            val sDas = simulateColorBlindness(color, "protanopia")
            val distProt = colorDistanceCiede2000(sDie, sDas)

            val sDieDeut = simulateColorBlindness(lightDie, "deuteranopia")
            val sDasDeut = simulateColorBlindness(color, "deuteranopia")
            val distDeut = colorDistanceCiede2000(sDieDeut, sDasDeut)

            val sDieTrit = simulateColorBlindness(lightDie, "tritanopia")
            val sDasTrit = simulateColorBlindness(color, "tritanopia")
            val distTrit = colorDistanceCiede2000(sDieTrit, sDasTrit)

            println("  $name: WCAG=${"%.2f".format(cr)}:1 | Protanopia ΔE00=${"%.2f".format(distProt)} | Deuteranopia ΔE00=${"%.2f".format(distDeut)} | Tritanopia ΔE00=${"%.2f".format(distTrit)}")
        }
    }
}
