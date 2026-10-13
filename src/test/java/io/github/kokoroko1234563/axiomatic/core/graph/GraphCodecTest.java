package io.github.kokoroko1234563.axiomatic.core.graph;

import static io.github.kokoroko1234563.axiomatic.core.logic.Term.app;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.var;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;

class GraphCodecTest {

    @Test
    void numeralIsAChainOfSuccessors() {
        PointedGraph g = GraphCodec.encode(Term.numeral(3));
        assertEquals(4, g.nodes().size());
        assertEquals("S", g.pointNode().label());
        assertEquals(Term.numeral(3), GraphCodec.decodeTerm(g));
    }

    @Test
    void identicalSubtermsAreShared() {
        Term x = var("x");
        Term sq = app("mul", x, x);
        Term x4 = app("mul", sq, sq);
        // x, mul(x,x), mul(sq,sq): three nodes, not seven.
        assertEquals(3, GraphCodec.encode(x4).nodes().size());
    }

    @Test
    void statementsRoundTripThroughBytes() throws IOException {
        Formula f = Formula.implies(Formula.exists("k", Formula.eq(var("n"), app("add", var("k"), var("k")))),
                Formula.and(Formula.pred("Even", var("n")), Formula.not(Formula.eq(Term.zero(), Term.numeral(1)))));
        PointedGraph g = GraphCodec.encode(f);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        g.write(new DataOutputStream(bytes));
        PointedGraph read = PointedGraph.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));

        assertEquals(g, read);
        assertEquals(f, GraphCodec.decodeFormula(read));
    }

    @Test
    void edgesMustPointBackwards() {
        assertThrows(IllegalArgumentException.class, () -> new PointedGraph(
                List.of(new PointedGraph.Node("S", List.of(0))), 0));
    }
}
