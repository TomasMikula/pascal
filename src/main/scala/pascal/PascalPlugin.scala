package pascal

import scala.tools.nsc
import nsc.Global
import nsc.plugins.{Plugin, PluginComponent}
import nsc.reporters.StoreReporter
import nsc.transform.{Transform, TypingTransformers}
import nsc.symtab.Flags._

import scala.reflect.NameTransformer

class PascalPlugin(val global: Global) extends Plugin {
  val name = "pascal"
  val description = "Concise syntax for polymorphic values"
  val components = new Rewriter(global) :: Nil
}

class Rewriter(val global: Global) extends PluginComponent with Transform with TypingTransformers with Compat {
  import global._

  val phaseName = "universals"
  val runsAfter = "parser" :: Nil
  override val runsBefore = "namer" :: Nil

  def newTransformer(unit: CompilationUnit): TypingTransformer = new TypingTransformer(unit) {

    private var cnt = -1 // So we actually start naming from 0

    @inline
    final def freshName(s: String): String = {
      cnt += 1
      s + cnt + "$"
    }

    val NothingLower = gen.rootScalaDot(tpnme.Nothing)
    val AnyUpper = gen.rootScalaDot(tpnme.Any)
    val DefaultBounds = TypeBoundsTree(NothingLower, AnyUpper)

    def parse(code: String): Option[Tree] = {
      val oldReporter = global.reporter
      try {
        val r = new StoreReporter()
        global.reporter = r
        val tree = newUnitParser(code).templateStats().headOption
        if (r.infos.isEmpty) tree else None
      } finally {
        global.reporter = oldReporter
      }
    }

    // Handy way to make a TypeName from a Name.
    def makeTypeName(name: Name): TypeName =
      newTypeName(name.toString)

    // Given a name, e.g. A or `A <: Foo`, build a type
    // parameter tree using the given name, bounds, etc.
    def makeTypeParamFromName(ident: Ident): TypeDef = {
      val decoded = NameTransformer.decode(ident.name.toString)
      val src = s"type _X_[$decoded] = Unit"
      parse(src) match {
        case Some(TypeDef(_, _, List(tpe), _)) => tpe.duplicate
        case None => reporter.error(ident.pos, s"Can't parse param: ${ident.name}"); null
      }
    }

    def typeArgToTypeParam(t: Tree): TypeDef = t match {
      case id @ Ident(_) =>
        makeTypeParamFromName(id)

      case AppliedTypeTree(Ident(name), ps) =>
        val tparams = ps.map(typeArgToTypeParam)
        TypeDef(Modifiers(PARAM), makeTypeName(name), tparams, DefaultBounds)

      case ExistentialTypeTree(AppliedTypeTree(Ident(name), ps), _) =>
        val tparams = ps.map(typeArgToTypeParam)
        TypeDef(Modifiers(PARAM), makeTypeName(name), tparams, DefaultBounds)

      case x =>
        reporter.error(x.pos, "Can't parse %s (%s)" format (x, x.getClass.getName))
        null.asInstanceOf[TypeDef]
    }

    def polyVal(tree: Tree): Tree = tree match {
      case PolyVal(targetType, methodName, tArgs, selfRef, body) =>
        val self = selfRef match {
          case Some(name) => ValDef(Modifiers(), name, TypeTree(), EmptyTree)
          case None       => ValDef(Modifiers(), nme.WILDCARD, TypeTree(), EmptyTree)
        }

        atPos(tree.pos.makeTransparent)(tArgs match {
          case Nil =>
            val tParam = newTypeName(freshName("A"))
            q"new $targetType { $self => def $methodName[$tParam] = $body }"
          case _ =>
            val tParams = tArgs.map(typeArgToTypeParam)
            q"new $targetType { $self => def $methodName[..$tParams] = $body }"
        })
      case _ => tree
    }

    override def transform(tree: Tree): Tree = {
      // first recursively transform children,
      // then rewrite the current tree
      polyVal(super.transform(tree))
    }
  }


  // Extractors

  object TermLambda {
    private val LambdaName = newTermName("Λ")

    def unapply(tree: Tree): Option[(List[Tree], Tree)] = tree match {
      case Apply(TypeApply(Ident(name), tParams), body :: Nil) if name == LambdaName => Some((tParams, body))
      case _                                                                         => None
    }
  }
  object TermNuType {
    private val NuName = newTermName("ν")

    def unapply(tree: Tree): Option[Tree] = tree match {
      case TypeApply(Ident(name), tpe :: Nil) if name == NuName => Some(tpe)
      case _                                                    => None
    }
  }
  object Body {
    def unapply(tree: Tree): Option[(Option[TermName], Tree)] = tree match {
      case AssignOrNamedArg(Ident(name), body) => Some((Some(name.toTermName), body))
      case body                                => Some((None,                  body))
    }
  }
  object PolyVal {
    //                               type, method  ,type params, self reference  , body
    def unapply(tree: Tree): Option[(Tree, TermName, List[Tree], Option[TermName], Tree)] = tree match {

      // Λ[A, B, ...](e) : T
      case Typed(TermLambda(tParams, Body(self, body)), tpe)                                   =>
        Some((tpe, nme.apply, tParams, self, body))

      // ν[T].method[A, B, ...](e)
      case Apply(TypeApply(Select(TermNuType(tpe), method), tParams), Body(self, body) :: Nil) =>
        Some((tpe, method.toTermName, tParams, self, body))

      // ν[T][A, B, ...](e)
      case Apply(TypeApply(TermNuType(tpe), tParams), Body(self, body) :: Nil)                 =>
        Some((tpe, nme.apply, tParams, self, body))

      // ν[T].method(e)
      case Apply(Select(TermNuType(tpe), method), Body(self, body) :: Nil)                     =>
        Some((tpe, method.toTermName, Nil, self, body))

      // ν[T](e)
      case Apply(TermNuType(tpe), Body(self, body) :: Nil)                                     =>
        Some((tpe, nme.apply, Nil, self, body))

      case _                                                                                   =>
        None
    }
  }
}
