package junit;

import com.google.common.util.concurrent.RateLimiter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@SuppressWarnings("UnstableApiUsage")
class JavaTest {

    private static RateLimiter limiter;

    @BeforeAll
    static void beforeAll() {
        limiter = RateLimiter.create(1);
    }

    @Test
    void basicTest() {
        Assertions.assertTrue(true);
    }

    @Execution(ExecutionMode.CONCURRENT)
    @CsvSource({"1,2", "3,4"})
    @ParameterizedTest
    void param(int input, int expect) {
        limiter.acquire();
        System.out.println(Thread.currentThread());
        Assertions.assertEquals(expect, input + 1);
    }
}