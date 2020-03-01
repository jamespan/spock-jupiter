package spock

import spock.lang.Spec
import spock.lang.Unroll

class TryoutSpec extends Spec {
    def 'demo spec'() {
        expect:
        true
    }

    @Unroll("#a + 1 == #b")
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
