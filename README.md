Forked to add opinionated variations to the rectangular rule, mostly to save some vertical spaces:

* Prose-style arguments instead of one-arg-per-line. Mostly useful when you have a `Set.of(24 elments)` statement.
* Allows `setFoo(newBuilder()` without an extra line wrap and indentation.
* Allows `var foo = newBuilder()` without an extra line wrap and indentation.
* Prefer `k1, v1,` at the same line instead of forcing a newline and losing the structure.
