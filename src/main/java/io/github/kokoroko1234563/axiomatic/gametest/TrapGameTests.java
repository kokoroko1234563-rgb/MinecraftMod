package io.github.kokoroko1234563.axiomatic.gametest;

import java.math.BigInteger;
import java.util.List;

import io.github.kokoroko1234563.axiomatic.core.knowledge.Library;
import io.github.kokoroko1234563.axiomatic.core.knowledge.MathState;
import io.github.kokoroko1234563.axiomatic.core.logic.Definition;
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
import io.github.kokoroko1234563.axiomatic.machine.ConjectureBlockEntity;
import io.github.kokoroko1234563.axiomatic.machine.MachineBlockEntity;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import io.github.kokoroko1234563.axiomatic.world.AxiomaticData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Theorem cards, conjectures and the opening hour's trap, in a world. */
final class TrapGameTests {
    private TrapGameTests() {}

    private static final Term N = Term.var("n");

    /**
     * The definitions a player makes before the trap: multiplication, divides, ≤, Prime, and
     * Odd(n) = 2n + 1. Made directly here; the Drafting Chamber has its own tests.
     */
    private static void defineOpeningHour(MathState state) {
        Term a = Term.var("a");
        Term b = Term.var("b");
        Term y = Term.var("y");
        Term d = Term.var("d");
        define(state, new Definition.PrimitiveRecursive("mul", List.of("a", "b"), Term.zero(),
                Term.app("add", Term.app("mul", a, b), a), "×"));
        define(state, new Definition.Explicit("Twice", List.of("n"), Term.app("add", N, N), null));
        define(state, new Definition.Explicit("Odd", List.of("n"), Term.succ(Term.app("add", N, N)), null));
        define(state, new Definition.PrimitiveRecursive("isZero", List.of("y"), Term.numeral(1), Term.zero(), null));
        define(state, new Definition.PrimitiveRecursive("pred", List.of("y"), Term.zero(), y, null));
        define(state, new Definition.PrimitiveRecursive("sub", List.of("a", "b"), a, Term.app("pred", Term.app("sub", a, b)), "∸"));
        define(state, new Definition.Explicit("eqb", List.of("a", "b"),
                Term.app("isZero", Term.app("add", Term.app("sub", a, b), Term.app("sub", b, a))), null));
        Term next = Term.succ(Term.app("modR", d, N));
        define(state, new Definition.PrimitiveRecursive("modR", List.of("d", "n"), Term.zero(),
                Term.app("mul", next, Term.app("isZero", Term.app("eqb", next, d))), null));
        define(state, new Definition.Explicit("divides", List.of("d", "n"), Term.app("isZero", Term.app("modR", d, N)), null));
        define(state, new Definition.Predicate("le", List.of("a", "b"),
                Formula.exists("j", Formula.eq(Term.app("add", a, Term.var("j")), b)), "≤"));
        define(state, new Definition.Predicate("Prime", List.of("n"),
                Formula.and(Formula.pred("le", Term.numeral(2), N), Formula.not(Formula.exists("d", Formula.and(
                        Formula.pred("le", Term.numeral(2), d), Formula.and(Formula.pred("le", Term.succ(d), N),
                                Formula.eq(Term.app("divides", d, N), Term.numeral(1))))))), null));
    }

    /** Game tests share one world and run together, so each definition is made once. */
    private static synchronized void define(MathState state, Definition def) {
        if (state.signature().get(def.name()) == null) {
            state.kernel().define(def);
        }
    }

    private static MachineBlockEntity place(GameTestHelper h, int x, net.minecraft.world.level.block.Block block) {
        BlockPos pos = new BlockPos(x, 1, 2);
        h.setBlock(pos, block);
        MachineBlockEntity m = h.getBlockEntity(pos, MachineBlockEntity.class);
        m.receiveEnergy(m.capacity());
        return m;
    }

    private static ItemStack design(String f) {
        Term t = Term.app(f, N);
        return MathItems.circuitStack(new CircuitData(t, t.toString()));
    }

    static void theoremCardCertifiesOutputs(GameTestHelper h) {
        MathState state = AxiomaticData.get(h.getLevel().getServer()).state();
        defineOpeningHour(state);
        Kernel k = state.kernel();
        // Even(Twice(n)), proven as on the Witness Bench.
        Theorem dbl = k.definingStatements("Twice").get(0);
        Theorem fold = k.instantiate(k.definingStatements("Even").get(1), "n", Term.app("Twice", N));
        Formula.Exists body = (Formula.Exists) ((Formula.Implies) fold.formula()).premise();
        Theorem card = k.modusPonens(fold, k.existsIntro(body, N, dbl));

        MachineBlockEntity machine = place(h, 1, ModRegistries.COMPILED_MACHINE.get());
        machine.setItem(0, design("Twice"));
        machine.setItem(4, MathItems.statementStack(StatementData.of(card, new Printer(state.signature()))));
        machine.setItem(1, NumeralItem.stack(NumeralData.of(21), 1));
        h.succeedWhen(() -> {
            ItemStack out = machine.getItem(5);
            h.assertTrue(!out.isEmpty(), "waiting; status: " + machine.status().getString());
            NumeralData n = NumeralItem.data(out);
            h.assertValueEqual(n.value(), BigInteger.valueOf(42), "Twice(21)");
            h.assertTrue(n.hasProperty("Even", false), "Twice's outputs are certified even by the card");
        });
    }

    static void twoNPlusOneTrap(GameTestHelper h) {
        var level = h.getLevel();
        MathState state = AxiomaticData.get(level.getServer()).state();
        defineOpeningHour(state);
        Printer printer = new Printer(state.signature());

        MachineBlockEntity bench = place(h, 0, ModRegistries.CONJECTURE_BENCH.get());
        MachineBlockEntity machine = place(h, 1, ModRegistries.COMPILED_MACHINE.get());
        BlockPos blockPos = new BlockPos(3, 1, 2);
        var box = new Object() {
            ConjectureBlockEntity block;
        };
        h.startSequence()
                .thenExecute(() -> {
                    ItemStack claim = MathItems.statementStack(StatementData.unproven(
                            Formula.pred("Prime", Term.app("Odd", N)), printer));
                    claim.set(DataComponents.CUSTOM_NAME, Component.literal("oddPrimes"));
                    bench.setItem(0, claim);
                })
                .thenWaitUntil(() -> h.assertTrue(!bench.getItem(1).isEmpty() && !bench.getItem(2).isEmpty(),
                        "waiting for the conjecture; status: " + bench.status().getString()))
                .thenExecute(() -> {
                    ItemStack blockItem = bench.getItem(2);
                    h.assertValueEqual(blockItem.get(ModRegistries.CONJECTURE.get()), "oddPrimes", "block's conjecture");
                    h.setBlock(blockPos, ModRegistries.CONJECTURE_BLOCK.get());
                    box.block = h.getBlockEntity(blockPos, ConjectureBlockEntity.class);
                    box.block.bind("oddPrimes");
                    box.block.receiveEnergy(box.block.capacity());
                    machine.setItem(0, design("Odd"));
                    machine.setItem(4, bench.getItem(1).copy());
                    machine.setItem(1, NumeralItem.stack(NumeralData.of(3), 1));
                })
                .thenWaitUntil(() -> h.assertTrue(!machine.getItem(5).isEmpty(),
                        "waiting for 7; status: " + machine.status().getString()))
                .thenExecute(() -> {
                    NumeralData seven = NumeralItem.data(machine.getItem(5));
                    h.assertValueEqual(seven.value(), BigInteger.valueOf(7), "Odd(3)");
                    h.assertTrue(seven.hasProperty("Prime", true) && !seven.hasProperty("Prime", false),
                            "7 is certified prime, conjecturally");
                    machine.setItem(5, ItemStack.EMPTY);
                    machine.setItem(1, NumeralItem.stack(NumeralData.of(4), 1));
                })
                .thenWaitUntil(() -> h.assertValueEqual(
                        state.library().conjectureNamed("oddPrimes").state(), Library.ConjectureState.REFUTED, "state"))
                .thenExecute(() -> h.assertTrue(machine.getItem(5).isEmpty(), "9 must not come out certified"))
                .thenWaitUntil(() -> h.assertTrue(level.getBlockState(h.absolutePos(blockPos)).isAir(),
                        "the conjecture block should fail"))
                .thenSucceed();
    }
}
