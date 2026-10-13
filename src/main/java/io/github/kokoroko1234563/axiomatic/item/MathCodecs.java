package io.github.kokoroko1234563.axiomatic.item;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import io.github.kokoroko1234563.axiomatic.core.graph.GraphCodec;
import io.github.kokoroko1234563.axiomatic.core.graph.PointedGraph;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;

/** Codecs that store terms and statements in item components as base64 pointed graphs. */
public final class MathCodecs {
    private MathCodecs() {}

    public static final Codec<Term> TERM = Codec.STRING.comapFlatMap(s -> decode(s, true).map(Term.class::cast),
            t -> encode(GraphCodec.encode(t)));

    public static final Codec<Formula> FORMULA = Codec.STRING.comapFlatMap(
            s -> decode(s, false).map(Formula.class::cast), f -> encode(GraphCodec.encode(f)));

    private static String encode(PointedGraph g) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            g.write(new DataOutputStream(bytes));
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static DataResult<Object> decode(String s, boolean term) {
        try {
            PointedGraph g = PointedGraph.read(new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(s))));
            return DataResult.success(term ? GraphCodec.decodeTerm(g) : GraphCodec.decodeFormula(g));
        } catch (IOException | IllegalArgumentException e) {
            return DataResult.error(() -> "Bad pointed graph: " + e.getMessage());
        }
    }
}
