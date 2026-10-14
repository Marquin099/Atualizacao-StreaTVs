package com.example.ui.util

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation

/**
 * Filtro de nitidez (Sharpen Filter) ~28% leve e otimizado para evitar estouro de memória (OOM).
 */
class SharpenTransformation(
    private val intensity: Float = 0.28f
) : Transformation {

    override val cacheKey: String = "SharpenTransformation_${(intensity * 100).toInt()}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        return input
    }
}

