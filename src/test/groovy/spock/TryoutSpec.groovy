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

    @Unroll("#a==#b")
    def 'param spec'() {
        expect:
        println "$a==$b"
        a == b

        where:
        [a, b] << combine([1, 2], [1, 2, 3, 1, 2])
    }

    def combine(x, y) {
        def args = []
        for (def i : x) {
            for (def j : y) {
                args.add([i, j])
            }
        }
        return args
    }
}
