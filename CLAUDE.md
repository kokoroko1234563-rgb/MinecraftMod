# Axiomatic (Minecraft mod)

Read `HANDOFF.md` first: build commands, layout, decisions and what is next. The game design is in
`docs/design.md`. Keep `HANDOFF.md` up to date when you finish a piece of work.

- Code under `src/main/java/.../axiomatic/core/` must not import Minecraft or NeoForge classes.
- Every new kernel rule needs tests in `KernelTest`, including a test that a cheat is refused.
- Run `./gradlew build` (JDK 25) before committing.
