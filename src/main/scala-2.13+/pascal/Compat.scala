package pascal

private[pascal] trait Compat { self: Rewriter =>
  import self.global._

  // https://github.com/scala/scala/commit/870131bf4b
  val AssignOrNamedArg = NamedArg

}
