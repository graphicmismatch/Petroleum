# Expression (`Petroleum.Expression`)

A recursive-descent parser and evaluator for user-supplied mathematical
expressions, e.g. `"sin(x) + 0.3*sin(3*x)"`. Built as its own package (not
under `Oscillators`) so it's reusable anywhere a formula needs parsing, not
just for defining waveforms - `Oscillators.Implementations.ExpressionOscillator`
and `Effects.WaveshaperNode` are two independent consumers of it, one per
package, neither aware of the other.

## `FormulaSource`

```java
public interface FormulaSource {
    String getSource();
    void setSource(String source);
}
```

The minimal surface [Gui](gui.md) needs to give a live text editor to
*anything* backed by a `MathExpression`, regardless of what kind of `Node`
(or `IOscillator`) it otherwise is. `ExpressionOscillator` (an `IOscillator`,
evaluated with `x` bound to phase) and `WaveshaperNode` (a plain `Node`,
evaluated with `x` bound to an incoming audio sample) both implement it, and
neither knows the other exists - `Gui.NodeView` is the only code that treats
them as the same kind of thing, via one `formulaSourceOf(Node)` check instead
of a separate `TextArea`-wiring method per consumer. A future third
formula-driven node (a control-rate LFO shape, a per-step sequencer value)
gets the same editor for free by implementing this interface, with no `Gui`
change required.

## Entry point: `MathExpression`

```java
MathExpression expr = new MathExpression("sin(x) + 0.3*sin(3*x)");
double y = expr.evaluate(phaseInRadians);
```

The expression is parsed once, at construction, into an AST that's cheap to
re-evaluate per sample. `evaluate(double x)` binds `x` to the raw phase
(radians) and `t` to the phase normalized to a single cycle in `[0, 1)`.

Evaluation is allocation-free, matching the rest of the codebase's
realtime-safe hot path convention. Variables are passed as a fixed-size
`double[] slots` array rather than a `Map<String, Double>`: `slots[0]` is
`x`, `slots[1]` is `t`. `MathExpression` holds one reusable `slots` array
(a field, not a per-call allocation) and writes into it before each
`evaluate(double x)` call. A **consequence of this reuse is that a single
`MathExpression` instance is not safe to evaluate concurrently from
multiple threads** - only one evaluation of a given instance may be in
flight at a time, since concurrent calls would race on the same scratch
array. This matches how `MathExpression` is actually used (one instance
per oscillator, evaluated once per sample from a single audio thread).

`VariableExpr` resolves what a name refers to once, at parse time, rather
than on every evaluation:
- `x` and `t` resolve to `slots[0]`/`slots[1]` respectively - `evaluate`
  just indexes into the array.
- Any other name is looked up in `Constants` immediately at construction;
  if found, its value is folded into the node as a plain `double` field
  (no map lookup, no boxing, at evaluation time).
- Anything else throws `ExpressionException` immediately at construction
  (i.e. at parse time, not the first time the expression is evaluated) -
  this is strictly earlier than before, but the error message and reported
  source position are unchanged.

`FunctionCallExpr` similarly avoids a per-call allocation: the `double[]`
holding evaluated argument values is allocated once, at construction
(sized to the call's fixed argument count), and reused - and reentrancy is
safe because a given AST node is evaluated at most once per top-level
`evaluate` call, so nothing ever holds two live evaluations of the same
node's argument array at once.

## Supported syntax

- Literals: `1`, `3.14`, `.5`, `2e-3` (decimal and scientific notation).
- Operators: `+ - * / % ^` (right-associative power), unary `+`/`-`.
- Implicit multiplication: `2x`, `3sin(x)`, `(x+1)(x-1)` - a factor
  immediately followed by another atom with no explicit operator between
  them is treated as multiplication.
- Constants: `pi`, `tau` (`2*pi`), `e`.
- Functions: `sin cos tan asin acos atan sinh cosh tanh sqrt abs exp ln log
  log10 floor ceil round sign frac`, `min`/`max` (2 or more args), `pow`,
  `mod`, `atan2`, `clamp` (3 args), `rand` (0 args, uniform noise in `[-1,1]`).
- Arbitrary variables, resolved by name at parse time (`x`/`t` bind to the
  slot array, anything else is looked up in `Constants`; unknown variables
  throw `ExpressionException` immediately when the expression is parsed).

## Grammar

```
expression := term (('+' | '-') term)*
term       := unary (('*' | '/' | '%') unary | implicit-unary)*
unary      := ('+' | '-') unary | power
power      := atom ('^' unary)?
atom       := NUMBER
            | IDENTIFIER ('(' (expression (',' expression)*)? ')')?
            | '(' expression ')'
```

## Class map

- **Lexer** / **Token** / **TokenType** - tokenizes the raw string into
  numbers, identifiers, operators, parens, and commas; whitespace is ignored.
- **Parser** - the recursive-descent parser above, producing an `Expr` tree.
  `Parser.parse(source)` is the static entry point used by `MathExpression`.
- **Expr** - AST node interface (`evaluate(double[] slots)`, `slots[0]` = `x`,
  `slots[1]` = `t`), implemented by:
  - **NumberExpr** - a literal.
  - **VariableExpr** - resolved once at construction: `x`/`t` become a slot
    index, any other name is folded to a constant looked up in `Constants`
    (`pi`/`tau`/`e`) at construction time, or the constructor throws if
    neither applies.
  - **UnaryExpr** - unary `+`/`-`.
  - **BinaryExpr** - `+ - * / % ^`; division and modulo by zero throw.
  - **FunctionCallExpr** - resolves a name via `Functions`, validates arg
    count against the function's declared arity, then evaluates into a
    preallocated `double[]` argument buffer sized once at construction.
- **Functions** / **FunctionSpec** / **MathFunction** - the builtin function
  registry: each entry has a min/max arity and a `double[] -> double`
  implementation. `rand` draws from a package-private `java.util.Random`
  instance rather than `Math.random()` (unsynchronized, since a
  `MathExpression` is already single-threaded by design).
- **Constants** - the `pi` / `tau` / `e` lookup table consulted by
  `VariableExpr` at construction time.
- **ExpressionException** - thrown for any lexing, parsing, or evaluation
  failure, with the offending source position included where known.
