# P∀scal
Concise syntax for **p**olymorphic, a.k.a. universally quantified (**∀**), values in **Scal**a.

## Introduction: Polymorphic values

A _polymorphic_ (also _universally quantified_) value `a` of type `∀A. F[A]` is a value that qualifies
as a value of type `F[A]` for _any_ type `A`. That is, a _single_ value that is an instance of
`F[Int]`, `F[String]`, `F[List[Boolean]]`, ... simultaneously.

Parametric polymorhpism was introduced in [System F](https://en.wikipedia.org/wiki/System_F) with the following syntax:

```
Λα. t : ∀α. T
```
where `t` is a _term_, `T` is a _type_, and `α` is a _type variable_ that can occur in both `t` and `T`.
(Τhe expression after the colon is the type of the lambda expression preceding it.)

For example, the following is the identity function:

```
Λα. λ(x:α). x : ∀α. α -> α
```

## Encoding polymorphic values in Scala

Scala lacks direct support for polymorphic values. It has generic types (`class Foo[A]`) and methods (`def foo[A]`).
These are sufficient for some use cases. For example, the above polymorphic identity function can be written as a polymorphic method:

```scala
def identity[α](x: α): α = x
```

or, alternatively

```scala
def identity[α]: (α => α) = (x: α) => x
```

The shortcoming is that a method cannot be passed as an argument (i.e. value) to another method.
The usual solution is to wrap the method in an object, which can be passed around as a value:

```scala
trait ForAll[F[_]] {
  def apply[A]: F[A]
}

type IdentityFun[A] = A => A

val identity: ForAll[IdentityFun] = new ForAll[IdentityFun] {
  def apply[A]: IdentityFun[A] = x => x
}

// usage
identity[Int](42)
```

Now `identity` is a _value_ that can be freely passed to other methods or functions.

This encoding, however, has several drawbacks:
 1. **Verbose syntax** for creating polymorphic values (an anonymous class implementing the interface).
 1. Requires a **dedicated wrapper type for each arity** of type parameters.
   In the example above, we used `ForAll[F[_]]`, but elsewhere we might also need
   `ForAll2[F[_, _]]`, `ForAllH[F[_[_]]]`, etc.
 1. Specialization of polymorphic values (`ForAll[F]`) to a specific type (e.g. `F[Int]`) may in general **allocate new objects.**

This project addresses (only) the first problem, namely the verbosity of polymorphic value creation.

The second problem would be addressed by kind-polymorphism (which, unfortunately, Scala also lacks).

The third problem can be addressed by other methods, e.g. https://github.com/scalaz/scalaz/pull/1417.

## More concise syntax

This project provides more concise syntax for creation of polymorphic values (in the usual encoding (see above)).
It tries to approximate the System F syntax mentioned above (`Λα. t : ∀α. T`).

It works as a compiler plugin that performs the following rewrites:

```scala
Λ[α](t): T
```
is analogous to System F's
```
Λα. t : T
```
(where `T` is of the form `∀α. U`)
and is rewritten to 
```scala
new T { def apply[α] = t }
```
For example
```scala
Λ[α](x => x): ForAll[IdentityFun]
```
is rewritten to
```scala
new ForAll[IdentityFun] { def apply[α] = x => x }
```

Generalizing to multiple type parameters of arbitrary kinds,
```scala
Λ[A, B[_], ...](t): T
```
is rewritten to
```scala
new T { def apply[A, B[_], ...] = t }
```

Note that the type ascription (`: T`) of the Λ-expression cannot be omitted
(it cannot be inferred, since P∀scal runs before typer).

We see that we are basically just providing a more concise syntax for instantiating types with
a single abstract generic parameterless method named `apply`.

In addition to the `Λ`-syntax above, we provide an alternative `ν`-syntax that reads more like
the expression that it is rewritten to:

```scala
ν[T][A, B[_], ...](t)
```
is rewritten to
```scala
new T { def apply[A, B[_], ...] = t }
```
"ν" is the Greek lowercase letter "Nu", pronounced "new".

In the common case when the generic method has a single monomorphic (i.e. of kind `*`) type parameter
which is not referenced in the method body (`t`), the `ν`-syntax allows one to omit the type parameter:

```scala
ν[T](t)
```
is rewritten to
```scala
new T { def apply[A] = t }
```
where `A` is a fresh name.

The `ν`-syntax also allows one to specify the method name in case it is different from `apply`:

```scala
ν[T].foo[A, B[_], ...](t)
```
is rewritten to
```scala
new T { def foo[A, B[_], ...] = t }
```

See the [test cases](https://github.com/TomasMikula/pascal/blob/master/src/test/scala/pascal/polyval.scala) for some examples.

### Self-references

It is possible for a polymorphic value to reference itself. For this, add a self-identifier and '`=`' before the polymorphic body. For example:

```scala
ν[T].foo[A](self = t)

```

where the term `t` can use the identifier `self` to refer to itself. It is rewritten to

```scala
new T { self =>
  def foo[A] = t
}
```

## Using the plugin

To use this plugin in your project, add the following line to your `build.sbt` file:

```scala
addCompilerPlugin("com.github.tomasmikula" %% "pascal" % "0.3.1")
```

If your project uses multiple Scala versions, use this for cross building instead

```scala
addCompilerPlugin("com.github.tomasmikula" % "pascal" % "0.3.1" cross CrossVersion.binary)
```

If your project uses Scala 2.10, also add

```scala
libraryDependencies ++= (scalaBinaryVersion.value match {
  case "2.10" =>
    compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full) :: Nil
  case _ =>
    Nil
})
```

## Relation to `kind-projector`

`kind-projector`'s [polymorphic lambdas](https://github.com/non/kind-projector#polymorphic-lambda-values)
provide similar functionality to this plugin. Our approach is more general in the following respects:
 - Polymorphic values generalize polymorphic functions.
 - We support quantification over:
   - multiple type parameters;
   - type parameters of arbitrary kinds.
 - We support referencing type parameters from the method body.
 
 Actually, this work started as a [PR at `kind-projector`](https://github.com/non/kind-projector/pull/54).
 For the lack of interest and for the sake of separation of concerns,
 I eventually published this as a separate project. This project borrows some code directly from
 `kind-projector` and is distributed under the same license (MIT) and original author's copyright notice.
