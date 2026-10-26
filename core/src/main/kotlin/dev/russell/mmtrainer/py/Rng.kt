package dev.russell.mmtrainer.py

/**
 * A bit-for-bit port of CPython's `random.Random` (MT19937 plus the 3.12 algorithms for
 * randrange/randint/choice/shuffle/uniform/choices). The same integer seed always yields
 * the same sequence, which keeps seeded tests reproducible.
 */
class Rng(seed: Long = System.nanoTime() xor seedUniquifier()) {
    private val mt = LongArray(N)
    private var mti = N + 1

    init {
        seed(seed)
    }

    fun seed(seed: Long) {
        var a = if (seed < 0) -seed else seed
        val key = ArrayList<Long>()
        if (a == 0L) key.add(0L)
        while (a != 0L) {
            key.add(a and MASK32)
            a = a ushr 32
        }
        initByArray(key)
    }

    private fun initGenrand(s: Long) {
        mt[0] = s and MASK32
        for (i in 1 until N) {
            mt[i] = (1812433253L * (mt[i - 1] xor (mt[i - 1] ushr 30)) + i) and MASK32
        }
        mti = N
    }

    private fun initByArray(key: List<Long>) {
        initGenrand(19650218L)
        var i = 1
        var j = 0
        var k = maxOf(N, key.size)
        while (k > 0) {
            mt[i] = ((mt[i] xor ((mt[i - 1] xor (mt[i - 1] ushr 30)) * 1664525L)) + key[j] + j) and MASK32
            i++; j++
            if (i >= N) { mt[0] = mt[N - 1]; i = 1 }
            if (j >= key.size) j = 0
            k--
        }
        k = N - 1
        while (k > 0) {
            mt[i] = ((mt[i] xor ((mt[i - 1] xor (mt[i - 1] ushr 30)) * 1566083941L)) - i) and MASK32
            i++
            if (i >= N) { mt[0] = mt[N - 1]; i = 1 }
            k--
        }
        mt[0] = 0x80000000L
    }

    /** One 32-bit output of the generator, as a non-negative Long. */
    fun genrandUint32(): Long {
        if (mti >= N) {
            var kk = 0
            while (kk < N - M) {
                val y = (mt[kk] and UPPER) or (mt[kk + 1] and LOWER)
                mt[kk] = mt[kk + M] xor (y ushr 1) xor (if (y and 1L != 0L) MATRIX_A else 0L)
                kk++
            }
            while (kk < N - 1) {
                val y = (mt[kk] and UPPER) or (mt[kk + 1] and LOWER)
                mt[kk] = mt[kk + (M - N)] xor (y ushr 1) xor (if (y and 1L != 0L) MATRIX_A else 0L)
                kk++
            }
            val y = (mt[N - 1] and UPPER) or (mt[0] and LOWER)
            mt[N - 1] = mt[M - 1] xor (y ushr 1) xor (if (y and 1L != 0L) MATRIX_A else 0L)
            mti = 0
        }
        var y = mt[mti++]
        y = y xor (y ushr 11)
        y = y xor ((y shl 7) and 0x9d2c5680L)
        y = y xor ((y shl 15) and 0xefc60000L)
        y = y xor (y ushr 18)
        return y and MASK32
    }

    /** random.getrandbits(k) for 0 <= k <= 63. */
    fun getrandbits(k: Int): Long {
        require(k in 0..63)
        if (k == 0) return 0L
        if (k <= 32) return genrandUint32() ushr (32 - k)
        val low = genrandUint32()
        val high = genrandUint32() ushr (64 - k)
        return (high shl 32) or low
    }

    private fun randbelow(n: Long): Long {
        if (n <= 0L) return 0L
        val k = 64 - java.lang.Long.numberOfLeadingZeros(n)
        var r = getrandbits(k)
        while (r >= n) r = getrandbits(k)
        return r
    }

    /** random.random(): a float in [0, 1). */
    fun random(): Double {
        val a = genrandUint32() ushr 5
        val b = genrandUint32() ushr 6
        return (a * 67108864.0 + b) * (1.0 / 9007199254740992.0)
    }

    /** random.randint(a, b), both ends inclusive. */
    fun randint(a: Int, b: Int): Int = randrange(a, b + 1)

    fun randint(a: Long, b: Long): Long = a + randbelow(b - a + 1)

    fun randrange(stop: Int): Int {
        require(stop > 0) { "empty range for randrange($stop)" }
        return randbelow(stop.toLong()).toInt()
    }

    fun randrange(start: Int, stop: Int): Int {
        require(stop > start) { "empty range for randrange($start, $stop)" }
        return start + randbelow((stop - start).toLong()).toInt()
    }

    /** random.randrange(start, stop, step) for a positive step. */
    fun randrange(start: Int, stop: Int, step: Int): Int {
        require(step > 0)
        val n = (stop - start + step - 1) / step
        require(n > 0) { "empty range for randrange($start, $stop, $step)" }
        return start + step * randbelow(n.toLong()).toInt()
    }

    fun <T> choice(seq: List<T>): T {
        require(seq.isNotEmpty()) { "Cannot choose from an empty sequence" }
        return seq[randbelow(seq.size.toLong()).toInt()]
    }

    fun choice(s: String): Char = s[randbelow(s.length.toLong()).toInt()]

    fun <T> choice(arr: Array<T>): T = choice(arr.asList())

    fun choice(arr: IntArray): Int = arr[randbelow(arr.size.toLong()).toInt()]

    fun uniform(a: Double, b: Double): Double = a + (b - a) * random()

    fun <T> shuffle(x: MutableList<T>) {
        for (i in x.size - 1 downTo 1) {
            val j = randbelow((i + 1).toLong()).toInt()
            val t = x[i]
            x[i] = x[j]
            x[j] = t
        }
    }

    /** random.choices(population, weights)[0] (k = 1). */
    fun <T> choices(population: List<T>, weights: List<Double>): T {
        require(population.size == weights.size && population.isNotEmpty())
        val cum = DoubleArray(weights.size)
        var acc = 0.0
        for (i in weights.indices) {
            acc += weights[i]
            cum[i] = acc
        }
        val x = random() * (cum.last() + 0.0)
        // bisect_right(cum, x, 0, n - 1)
        var lo = 0
        var hi = population.size - 1
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (x < cum[mid]) hi = mid else lo = mid + 1
        }
        return population[lo]
    }

    /** random.choices(population)[0] with no weights: floor(random() * n). */
    fun <T> choices(population: List<T>): T =
        population[Math.floor(random() * population.size).toInt()]

    private companion object {
        const val N = 624
        const val M = 397
        const val MATRIX_A = 0x9908b0dfL
        const val UPPER = 0x80000000L
        const val LOWER = 0x7fffffffL
        const val MASK32 = 0xffffffffL
        private var uniquifier = 8682522807148012L
        fun seedUniquifier(): Long {
            uniquifier *= 1181783497276652981L
            return uniquifier
        }
    }
}
