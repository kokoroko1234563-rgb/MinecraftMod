package io.github.kokoroko1234563.axiomatic.gametest;

import java.math.BigInteger;
import java.util.Map;

import io.github.kokoroko1234563.axiomatic.core.knowledge.MathState;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Kernel;
import io.github.kokoroko1234563.axiomatic.core.logic.Printer;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;
import io.github.kokoroko1234563.axiomatic.core.logic.Theorem;
import io.github.kokoroko1234563.axiomatic.item.CircuitData;
import io.github.kokoroko1234563.axiomatic.item.MathItems;
import io.github.kokoroko1234563.axiomatic.item.NumeralData;
import io.github.kokoroko1234563.axiomatic.item.NumeralItem;
import io.github.kokoroko1234563.axiomatic.item.StatementData;
import io.github.kokoroko1234563.axiomatic.machine.CompiledMachineBlockEntity;
import io.github.kokoroko1234563.axiomatic.machine.MachineBlockEntity;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import io.github.kokoroko1234563.axiomatic.world.AxiomaticData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

/** add_comm as a speed card: the Compiled Machine recurses on the smaller input. */
final class SpeedCardGameTest {
    private SpeedCardGameTest() {}

    private static final Term A = Term.var("a");
    private static final Term B = Term.var("b");

    /** a + b = b + a, from the definition of + by three inductions. */
    static Theorem addComm(Kernel k) {
        Theorem addZero = k.definingStatements("add").get(0);                      // a + 0 = a
        Theorem addSucc = k.definingStatements("add").get(1);                      // a + S(b) = S(a + b)
        // 0 + a = a, by induction on a.
        Theorem zh = k.assume(Formula.eq(Term.app("add", Term.zero(), A), A));
        Theorem zs = k.rewrite(k.instantiate(addSucc, Map.of("a", Term.zero(), "b", A)), zh, false, -1);
        Theorem zeroAdd = k.induction(k.instantiate(addZero, "a", Term.zero()), k.discharge(zs, zh.hypotheses().get(0)), "a");
        // S(a) + b = S(a + b), by induction on b.
        Theorem sBase = k.rewrite(k.instantiate(addZero, "a", Term.succ(A)), addZero, true, 1);
        Theorem sh = k.assume(Formula.eq(Term.app("add", Term.succ(A), B), Term.succ(Term.app("add", A, B))));
        Theorem ss = k.rewrite(k.instantiate(addSucc, Map.of("a", Term.succ(A), "b", B)), sh, false, -1);
        ss = k.rewrite(ss, addSucc, true, -1);
        Theorem succAdd = k.induction(sBase, k.discharge(ss, sh.hypotheses().get(0)), "b");
        // a + b = b + a, by induction on b.
        Theorem cBase = k.rewrite(addZero, zeroAdd, true, 1);
        Theorem ch = k.assume(Formula.eq(Term.app("add", A, B), Term.app("add", B, A)));
        Theorem cs = k.rewrite(addSucc, ch, false, -1);
        cs = k.rewrite(cs, k.instantiate(succAdd, Map.of("a", B, "b", A)), true, -1);
        return k.induction(cBase, k.discharge(cs, ch.hypotheses().get(0)), "b");
    }

    static void addCommSwapsInputs(GameTestHelper h) {
        MathState state = AxiomaticData.get(h.getLevel().getServer()).state();
        Kernel k = state.kernel();
        Printer printer = new Printer(state.signature());
        Theorem comm = addComm(k);
        h.assertTrue(comm.isClosed() && comm.formula().equals(Formula.eq(Term.app("add", A, B), Term.app("add", B, A))),
                "add_comm proven: " + printer.statement(comm.formula()));

        BlockPos pos = new BlockPos(2, 1, 2);
        h.setBlock(pos, ModRegistries.COMPILED_MACHINE.get());
        MachineBlockEntity m = h.getBlockEntity(pos, MachineBlockEntity.class);
        // 3 + 2000 recursing on 2000 costs over 2000 steps; the buffer only holds enough for 3 + small.
        m.receiveEnergy(200);
        m.setItem(CompiledMachineBlockEntity.DESIGN, MathItems.circuitStack(new CircuitData(Term.app("add", A, B), "a + b")));
        m.setItem(1, NumeralItem.stack(NumeralData.of(3), 1));
        m.setItem(2, NumeralItem.stack(NumeralData.of(2000), 1));
        m.setItem(CompiledMachineBlockEntity.CARD, MathItems.statementStack(StatementData.of(comm, printer)));
        h.succeedWhen(() -> {
            var out = m.getItem(CompiledMachineBlockEntity.OUTPUT);
            h.assertTrue(!out.isEmpty(), "waiting for 3 + 2000 on 200 energy; status: " + m.status().getString());
            h.assertValueEqual(NumeralItem.data(out).value(), BigInteger.valueOf(2003), "the sum is unchanged");
            h.assertTrue(m.status().getString().contains("swapped"), "status: " + m.status().getString());
        });
    }

    static void conjecturedSymmetryIsRefused(GameTestHelper h) {
        MathState state = AxiomaticData.get(h.getLevel().getServer()).state();
        Kernel k = state.kernel();
        Printer printer = new Printer(state.signature());
        // Even a true symmetry may not swap inputs while it is only conjectured.
        Theorem claim = k.conjecture("speedClaim", Formula.eq(Term.app("add", A, B), Term.app("add", B, A)));
        BlockPos pos = new BlockPos(2, 1, 2);
        h.setBlock(pos, ModRegistries.COMPILED_MACHINE.get());
        MachineBlockEntity m = h.getBlockEntity(pos, MachineBlockEntity.class);
        m.receiveEnergy(m.capacity());
        m.setItem(CompiledMachineBlockEntity.DESIGN, MathItems.circuitStack(new CircuitData(Term.app("add", A, B), "a + b")));
        m.setItem(1, NumeralItem.stack(NumeralData.of(3), 1));
        m.setItem(2, NumeralItem.stack(NumeralData.of(5), 1));
        m.setItem(CompiledMachineBlockEntity.CARD, MathItems.statementStack(StatementData.of(claim, printer)));
        h.succeedWhen(() -> {
            h.assertTrue(m.status().getString().contains("fully proven"), "status: " + m.status().getString());
            h.assertTrue(m.getItem(CompiledMachineBlockEntity.OUTPUT).isEmpty(), "nothing computed with a conjectured symmetry");
        });
    }
}
