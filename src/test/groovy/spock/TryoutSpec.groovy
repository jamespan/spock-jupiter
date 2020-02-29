package spock

import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import spock.lang.Specification

class TryoutSpec extends Specification {
    def 'demo spec'() {
        expect:
        true
    }


    @Execution(ExecutionMode.CONCURRENT)
    def 'parameterized spec'() {
        expect:
        println "$a, $b"
        println Thread.currentThread().getName()
        a + 1 == b

        where:
        a | b
        1 | 2
        3 | 4
    }
}
