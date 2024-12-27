package io.github.wasabithumb.jarstrap.util;

import org.jetbrains.annotations.ApiStatus;

import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stripped down version of
 * <a href="https://gist.github.com/WasabiThumb/3854686b0999098a3ffd35afa9fad504">this gist</a>
 */
@ApiStatus.Internal
public class Optimus {

    public static Optimus generate() {
        return generate(ThreadLocalRandom.current(), 31);
    }

    public static Optimus generate(Random r, int size) {
        int max = getMax(size);
        int prime = BigInteger.probablePrime(size, r).intValue();
        int random = r.nextInt() & max;
        return new Optimus(prime, random, max);
    }

    private static int getMax(int size) {
        if (size < 3) throw new IllegalArgumentException("Optimus size (" + size + ") cannot be less than 3");
        if (size > 31) throw new IllegalArgumentException("Optimus size greater than 31 (" + size + ") not supported");
        return size == 31 ? Integer.MAX_VALUE : ((1 << size) - 1);
    }

    //

    private final long prime;
    private final int random;
    private final long max;
    protected Optimus(int prime, int random, int max) {
        this.prime = prime;
        this.random = random;
        this.max = max;
    }

    public int encode(int n) {
        return ((int) ((((long) n) * this.prime) & this.max)) ^ this.random;
    }

}
