package pascal

import org.scalatest.FunSuite

class PolyVals extends FunSuite {

  trait Forall[F[_]] {
    def apply[A]: F[A]
  }

  trait Forall2[F[_, _]] {
    def apply[A, B]: F[A, B]
  }

  trait ForallK[F[_[_]]] {
    def apply[G[_]]: F[G]
  }


  trait Semigroup[A] {
    def combine(x: A, y: A): A
  }

  def listSemigroup[A]: Semigroup[List[A]] = new Semigroup[List[A]] {
    def combine(x: List[A], y: List[A]): List[A] = x ++ y
  }

  trait Functor[F[_]]
  trait Monad[F[_]] {
    def functor: Functor[F]
  }

  final class Fun[A, B](val run: A => B)

  // universally quantified semigroup
  type SemigroupK[F[_]] = Forall[λ[α => Semigroup[F[α]]]]

  // natural transformations
  type  ~>[F[_]   , G[_]   ] = Forall [λ[α      => F[α]    => G[α]]]
  type  ≈>[F[_[_]], G[_[_]]] = ForallK[λ[α[_]   => F[α]    => G[α]]]
  type ~~>[F[_, _], G[_, _]] = Forall2[λ[(α, β) => F[α, β] => G[α, β]]]

  // Const functor and constructors
  final class Const[A, B](val getConst: A)
  type ConstA[A] = Forall[Const[A, *]]
  type ConstMaker1 = Forall[λ[α => α => ConstA[α]]]
  type ConstMaker2 = Forall2[λ[(α, β) => α => Const[α, β]]]

  // existentials via universals
  type Consumer[F[_], R] = Forall[λ[A => F[A] => R]]
  type Exists[F[_]] = Forall[λ[R => Consumer[F, R] => R]]
  def existential[F[_], A](fa: F[A]): Exists[F] = ν[Exists[F]](_[A](fa))

  // Leibniz
  sealed class Leibniz[-L, +H >: L, A >: L <: H, B >: L <: H]
  trait Refl {
    def apply[L, H >: L, A >: L <: H]: Leibniz[L, H, A, A]
  }

  test("Leibniz") {
    val refl1 = ν[Refl][L, `H >: L`, `A >: L <: H`](new Leibniz[L, H, A, A])
    val refl2 = Λ[L, `H >: L`, `A >: L <: H`](new Leibniz[L, H, A, A]): Refl

    refl1[Nothing, Any, Int]: Leibniz[Nothing, Any, Int, Int]
    refl2[Int, AnyVal, Int]: Leibniz[Int, AnyVal, Int, Int]
  }
                                      

  test("SemigroupK") {
    val listSemigroupK = ν[SemigroupK[List]](listSemigroup)
    assert(listSemigroupK[Int].combine(List(1, 2), List(3, 4)) == List(1, 2, 3, 4))
  }

  test("NaturalTransformations") {
    val headOption1 = ν[List ~> Option](_.headOption)
    val headOption2 = ν[List ~> Option].apply[A]((l: List[A]) => l.headOption)

    val monadToFunctor1 = ν[Monad ≈> Functor][F[_]](_.functor)
    val monadToFunctor2 = ν[Monad ≈> Functor].apply[F[_]]((m: Monad[F]) => m.functor)

    val fun = Λ[A, B](new Fun(_)): Function1 ~~> Fun

    val listFunctor = new Functor[List] {}
    val listMonad = new Monad[List] { def functor = listFunctor }

    assert(headOption1[Int](List(1, 2)) == Some(1))
    assert(headOption2[Int](List(1, 2)) == Some(1))
    assert(monadToFunctor1[List](listMonad) == listFunctor)
    assert(monadToFunctor2[List](listMonad) == listFunctor)
    assert(fun[String, Int](_.length).run("foo") == 3)
  }

  test("recursive natural transformation") {
    val lastOption = ν[List ~> Option](self = _ match {
      case a :: Nil => Some(a)
      case a :: as  => self.apply(as)
      case Nil      => None
    })

    assert(lastOption[Int](List(1, 2, 3, 4, 5)) == Some(5))
  }

  test("Const") {
    val const42 = ν[ConstA[Int]][B](new Const[Int, B](42))
    val constMaker  = ν[ConstMaker1][A   ](a => ν[ConstA[A]][B](new Const[A, B](a)))
    val constMaker2 = ν[ConstMaker2][A, B](a =>                 new Const[A, B](a) )

    assert(const42[String].getConst == 42)
    assert(constMaker[Int](42)[String].getConst == 42)
    assert(constMaker2[Int, String](42).getConst == 42)
  }

  test("Existential") {
    val list = existential(List("one", "two", "three"))
    val len = ν[Consumer[List, Int]](_.length)
    assert(list[Int](len) == 3)
  }
}
