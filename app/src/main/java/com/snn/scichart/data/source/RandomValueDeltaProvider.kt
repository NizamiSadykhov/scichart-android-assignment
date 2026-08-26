package com.snn.scichart.data.source

import kotlin.random.Random

/** Генерирует равномерно распределённые приращения в диапазоне `[-1, 1)`. */
class RandomValueDeltaProvider(
    private val random: Random,
) : ValueDeltaProvider {

    override fun nextDelta(): Double = random.nextDouble(from = -1.0, until = 1.0)
}
