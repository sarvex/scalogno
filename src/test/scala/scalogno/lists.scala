package scalogno

import scala.language.implicitConversions

trait InjectBase extends Base {

  trait Inject[T] {
    def toTerm(x:T): Exp[T]
  }

  implicit def inject[T:Inject](x:T) = implicitly[Inject[T]].toTerm(x)

  implicit object InjectString extends Inject[String] {
    def toTerm(s: String): Exp[String] = term(s,Nil)
  }

}


trait Ordering extends Base {

  trait Ord[T] {
    def lt(x:Exp[T],y:Exp[T]): Rel
  }

  implicit class OrdOps[T:Ord](x:Exp[T]) {
    def <(y:Exp[T]): Rel = implicitly[Ord[T]].lt(x,y)
  }

}



trait NatBase extends InjectBase with Ordering {
  implicit val injectNat = new Inject[Int] {
    def toTerm(x: Int): Exp[Int] = nat(x)
  }
  implicit val ordNat = new Ord[Int] {
    def lt(x:Exp[Int],y:Exp[Int]): Rel = lessThan(x,y)
  }

  def nat(x: Int): Exp[Int] = if (x == 0) zero else succ(x-1)

  def succ(n: Exp[Int]): Exp[Int] = term("s",List(n))
  def zero: Exp[Int] = term("z",List())

  def lessThan(a: Exp[Int], b: Exp[Int]): Rel = 
    exists[Int] { b1 => b === succ(b1) && { a === zero || exists[Int] { a1 =>
      (a === succ(a1)) && lessThan(a1,b1)
    }}}

  def add(a: Exp[Int], b: Exp[Int], c: Exp[Int]): Rel = 
    (a === zero) && (b === c) ||
    exists[Int,Int] { (a1,c1) =>
      (a === succ(a1)) && (c === succ(c1)) && add(a1,b,c1)
    }

}




trait ListBase extends InjectBase with NatBase with Ordering {
  implicit def injectList[T:Inject] = new Inject[List[T]] {
    def toTerm(x: List[T]): Exp[List[T]] = list(x:_*)
  }
  implicit def ordList[T:Ord] = new Ord[List[T]] {
    def lt(x:Exp[List[T]],y:Exp[List[T]]): Rel = lessLex[T]((a1,a2) => a1 < a2, x,y)
  }

  def list[T:Inject](xs: T*): Exp[List[T]] = if (xs.isEmpty) nil else cons(inject(xs.head),list(xs.tail:_*))

  def cons[T](hd: Exp[T], tl: Exp[List[T]]): Exp[List[T]] = term("cons",List(hd,tl))
  def nil: Exp[List[Nothing]] = term("nil",List())
  def pair[A,B](a: Exp[A], b: Exp[B]): Exp[(A,B)] = term("pair",List(a,b))

  object Cons {
    def unapply[T](x: Exp[List[T]]): Some[(Exp[T],Exp[List[T]])] = {
      val h = fresh[T]
      val t = fresh[List[T]]
      x === cons(h,t)
      Some((h,t))
    }
  }
  object Pair {
    def unapply[A,B](x: Exp[(A,B)]): Some[(Exp[A],Exp[B])] = {
      val a = fresh[A]
      val b = fresh[B]
      x === pair(a,b)
      Some((a,b))
    }
  }


  def append[T](as: Exp[List[T]], bs: Exp[List[T]], cs: Exp[List[T]]): Rel =
    (as === nil && bs === cs) ||
    exists[T,List[T],List[T]] { (h,t1,t2) =>
      (as === cons(h,t1)) && (cs === cons(h,t2)) && append(t1,bs,t2)
    }

  def map[T,U](f: (Exp[T],Exp[U]) => Rel, as: Exp[List[T]], bs: Exp[List[U]]): Rel =
    (as === nil) && (bs === nil) ||
    exists[T,U,List[T],List[U]] { (a,b,as1,bs1) =>
      (as === cons(a,as1)) && f(a,b) && (bs === cons(b,bs1)) && map[T,U](f,as1,bs1)
    }

  def flatMap[T,U](f: (Exp[T],Exp[List[U]]) => Rel, as: Exp[List[T]], cs: Exp[List[U]]): Rel =
    (as === nil) && (cs === nil) ||
    exists[T,List[U],List[T],List[U]] { (a,bs,as1,cs1) =>
      (as === cons(a,as1)) && f(a,bs) && append(bs, cs1, cs) && flatMap[T,U](f,as1,cs1)
    }


  def lessLex[T](f: (Exp[T],Exp[T]) => Rel, as: Exp[List[T]], bs: Exp[List[T]]): Rel =
    exists[T,List[T]] { (b,bs1) => (bs === cons(b,bs1)) && { (as === nil) ||
      exists[T,List[T]] { (a,as1) =>
        (as === cons(a,as1)) && { f(a,b) || (a === b) && lessLex[T](f,as1,bs1) }
      }
    }}
}

trait TreeBase extends InjectBase with NatBase with Ordering {

  trait Tree[+T,-U]

  implicit def injectPair[T:Inject,U:Inject](x: (T,U)) = (inject(x._1), inject(x._2))

  def tree[T,U](xs: (Exp[T],Exp[U])*): Exp[Tree[T,U]] = if (xs.length == 0) empty else {
    val n = xs.length/2
    val (k,v) = xs(n)
    branch(tree(xs.slice(0,n):_*),k,v,tree(xs.slice(n+1,xs.length):_*))
  }

  def branch[T,U](l: Exp[Tree[T,U]], k: Exp[T], v: Exp[U], r: Exp[Tree[T,U]]): Exp[Tree[T,U]] = term("branch",List(l,k,v,r))
  def leaf[T,U](k:Exp[T],v: Exp[U]) = branch(empty,k,v,empty)
  def empty: Exp[Tree[Nothing,Any]] = term("nil",List())

  def lookupAll[T,U](as: Exp[Tree[T,U]], k: Exp[T], v: Exp[U]): Rel =
    exists[Tree[T,U],Tree[T,U],T,U] { (l,r,k1,v1) => 
      as === branch(l,k1,v1,r) && {
        (k === k1 && v === v1) ||
        lookupAll(l,k,v) || lookupAll(r,k,v)
      }
    }

  def lookup[T:Ord,U](as: Exp[Tree[T,U]], k: Exp[T], v: Exp[U]): Rel =
    exists[Tree[T,U],Tree[T,U],T,U] { (l,r,k1,v1) => 
      as === branch(l,k1,v1,r) && {
        (k === k1 && v === v1) ||
        (k < k1 && lookup(l,k,v)) || 
        (k1 < k && lookup(r,k,v))
      }
    }

  def lookupLess[T:Ord,U](as: Exp[Tree[T,U]], k: Exp[T], v: Exp[U]): Rel =
    exists[Tree[T,U],Tree[T,U],T,U] { (l,r,k1,v1) => 
      as === branch(l,k1,v1,r) && {
        (lookupLess(l,k,v)) || 
        (k1 < k && v === v1) ||
        (k1 < k && lookupLess(r,k,v))
      }
    }

}

trait GraphBase extends InjectBase with NatBase {

  trait Graph[T] {
    def edge(a: Exp[T], b: Exp[T]): Rel
    def path(a: Exp[T], b: Exp[T]): Rel =
      edge(a,b) || exists[T] { z => edge(a,z) && path(z,b) }
  }

}

trait MetaGraphBase extends GraphBase with ListBase with Engine {

/*
(define (patho-clause head tail) 
  (fresh (x y)
    (== head ‘(patho ,x ,y)) 
    (conde
      ((edgeo x y) (== tail ’())) 
      ((fresh (z)
        (edgeo x z)
        (== tail ‘((patho ,z ,y))))))))
*/

  trait Goal

  def pathTerm[T](a: Exp[T], b: Exp[T]) = term[Goal]("path",List(a,b))

  def pathClause1[T](g: Graph[T])(head: Exp[Goal], body: Exp[List[Goal]]) = {
    exists[T,T] { (a,b) => 
      (head === pathTerm(a,b)) && {
        (g.edge(a,b) && (body === nil)) ||
        exists[T] { z =>
          g.edge(a,z) && (body === cons(pathTerm(z,b),nil))
        }
      }
    }
  }

/*
(define (vanilla* clause)
  (define (solve* goals)
    (conde
      ((== goals ’())) 
      ((fresh (g gs body)
        (== (cons g gs) goals) 
        (clause g body) 
        (solve* body)
        (solve* gs)))))
  solve*)

(run 10 (q)
  ((vanilla* patho-clause)
   ‘((patho a ,q)))) => (b c a b c a b c a b)

*/

  def vanilla(clause: Clause)(goals: Exp[List[Goal]]): Rel = 
    goals === nil || 
    exists[Goal,List[Goal],List[Goal]] { (g, gs, body) => 
      (goals === cons(g,gs)) &&
      clause(g,body) &&
      vanilla(clause)(body) &&
      vanilla(clause)(gs)
    }

  type Clause = (Exp[Goal], Exp[List[Goal]]) => Rel

  def existsC[T,U](f: (Exp[T],Exp[U]) => Clause): Clause = {
    f(fresh[T],fresh[U])
  }


  def pathClause2[T](g: Graph[T])(a: Exp[T], b: Exp[T]) = { (head: Exp[Goal], body: Exp[List[Goal]]) =>
    (head === pathTerm(a,b)) && {
      g.edge(a,b) && (body === nil) ||
      exists[T] { z =>
        g.edge(a,z) && (body === cons(pathTerm(z,b),nil))
      }
    }
  }





  // auto reification !!!

  var allclauses = Map[String,Clause]()
  val moregoals = DVar(fresh[List[Goal]])

  def rule[A,B](s: String)(f:(Exp[A], Exp[B]) => Rel) = {
    def goalTerm(a: Exp[A], b: Exp[B]) = term[Goal](s,List(a,b))

    allclauses += s -> 
      { (head: Exp[Goal], body: Exp[List[Goal]]) =>
        exists[A,B] { (a,b) => 
          (head === goalTerm(a,b)) && reifyGoals(f(a,b))(body)
        }
      }
  
    {(a: Exp[A], b: Exp[B]) =>
      val hole = moregoals()
      moregoals := fresh
      hole === cons(goalTerm(a,b),moregoals())
    }
  }

  def path2[T](g: Graph[T]): (Exp[T],Exp[T])=> Rel = 
    rule("path"/*+g.toString*/) { (a,b) => 
      g.edge(a,b) ||
      exists[T] { z =>
        g.edge(a,z) && path2(g)(z,b)
      }
    }


  def reifyGoals(goal: => Rel)(goals: Exp[List[Goal]]): Rel = {
    moregoals := goals
    goal && moregoals() === nil
  }

  def reifyClause(goal: => Rel)(head: Exp[Goal], body: Exp[List[Goal]]): Rel = {
    reifyGoals(goal)(cons(head,nil)) && { 
      val s = extractStr(head)
      val key = s.substring(0,s.indexOf("(")) // a bit hacky, but hey ...
      println(key)
      allclauses(key)(head,body)
    }
  }


  def allclausesRel: Clause = { (head: Exp[Goal], body: Exp[List[Goal]]) =>
    ((No:Rel) /: allclauses.values) ((r,c) => r || c(head,body))
  }

  def vanilla2(goal: => Rel): Rel =
    exists[List[Goal]] { goals =>
      reifyGoals(goal)(goals) && vanilla2(goals)
    }

  def vanilla2(goals: Exp[List[Goal]]): Rel = {
    goals === nil || 
    exists[Goal,List[Goal],List[Goal]] { (g, gs, body) => 
      (goals === cons(g,gs)) &&
      allclausesRel(g,body) &&
      vanilla2(body) &&
      vanilla2(gs)
    }
  }

}


trait ReifyUtilsBase extends Base with InjectBase with ListBase with Engine {
  def rule[T,U](s: String)(f: (Exp[T],Exp[U]) => Rel): (Exp[T],Exp[U]) => Rel
  def globalTrace: () => Exp[List[List[String]]]
}

trait ReifyUtilsDynVars extends ReifyUtilsBase with InjectBase with ListBase with Engine {
  val globalTrace = DVar(nil: Exp[List[List[String]]])

  def rule[T,U](s: String)(f: (Exp[T],Exp[U]) => Rel): (Exp[T],Exp[U]) => Rel = 
    { (a,b) =>
      globalTrace := cons(term(s,List(a,b)), globalTrace())
      f(a,b)
    }
}

trait ReifyUtils extends ReifyUtilsBase with InjectBase with ListBase with Engine {

  var globalTrace0: Exp[List[List[String]]] = nil

  def globalTrace = () => globalTrace0
  def globalTrace_=(x:Exp[List[List[String]]]) = globalTrace0 = x

  // inject non-std interpretation by overriding || and &&

  override def infix_||(a: => Rel, b: => Rel): Rel = {
    val localTrace = globalTrace()
    def reset(x: => Rel) = { globalTrace = localTrace; x }
    super.infix_||(reset(a),reset(b))
  }
  override def infix_&&(a: => Rel, b: => Rel): Rel = {
    val localTrace = globalTrace()
    def reset(x: => Rel) = { globalTrace = localTrace; x }
    super.infix_&&(reset(a),b) // do not reset b
  }

  def rule[T,U](s: String)(f: (Exp[T],Exp[U]) => Rel): (Exp[T],Exp[U]) => Rel = 
    { (a,b) => 
      globalTrace = cons(term(s,List(a,b)),globalTrace()); 
      f(a,b)
    }

  // inject non-std interpretation by transforming rules reified as Or and And

  var inrule: List[String] = Nil
  def rule0[T,U](s: String)(f: (Exp[T],Exp[U]) => Rel): (Exp[T],Exp[U]) => Rel = 
    { (a,b) => 
      val self = s + "(" + extractStr(a) + "," + extractStr(b) + ")"
      val local = self::inrule
      //println("enter " + self + " at " + inrule.mkString(",")); 

      val localTrace = cons(term(s,List(a,b)),globalTrace())

      def vprintln(s: Any) = () // println(s)

      try {         
        def rec(n:Int)(r: Rel): Rel = r match {
          case Or(a,b) => 
            Or(() => { inrule = local; globalTrace = localTrace; vprintln("left  in "+n+self); rec(n+1)(a()) }, 
               () => { inrule = local; globalTrace = localTrace; vprintln("right in "+n+self); rec(n+1)(b()) })
          case And(a,b) => 
            And(() => { inrule = local; globalTrace = localTrace; vprintln("fst in "+n+self); rec(n+1)(a()) }, 
                () => { inrule = local; globalTrace = localTrace; vprintln("snd in "+n+self); rec(n+1)(b()) }) // should not reset?
          case _ => r
        }
        rec(0)(f(a,b))
      } finally {
        inrule = local.tail
      }
    }

}



trait STLC extends Base with InjectBase with ListBase with Engine {

  trait LTerm
  trait LType
  trait Deriv
  trait Rule

  type Sym
  type Env

  implicit class EnvOps(x: Exp[Env]) {
    def |- (y: (Exp[LTerm],Exp[LType])) = term[Deriv]("|-", List(x,y._1,y._2))
  }

  implicit class TypeOps(x: Exp[LType]) {
    def :: (e: Exp[LTerm]) = (e,x)
    def -> (y: Exp[LType]) = term[LType]("->", List(x,y))
  }

  implicit class TermOps(x: Exp[LTerm]) {
    def app(y: Exp[LTerm]) = term[LTerm]("@", List(x,y))
  }

  def sym(x: Exp[Sym]) = term[LTerm]("var", List(x))

  def lam(x: Exp[Sym], y: Exp[LTerm]) = term[LTerm]("lam", List(x,y))

  // judgments

  def extend(g: Exp[Env], x: Exp[Sym], tp: Exp[LType], g1: Exp[Env]): Rel

  def lookup(g: Exp[Env], x: Exp[Sym], tp: Exp[LType]): Rel

  def typecheck(d: Exp[Deriv]): Rel = 
    exists[Env,Sym,LType] { (G,x,t1) =>
      val a = G |- sym(x) :: t1

      d === a && lookup(G,x,t1)
    } ||
    exists[Env,Env,Sym,LTerm,LType,LType] { (G,G1,x,e,t1,t2) =>
      val a = G |- lam(x,e) :: (t1 -> t2)
      val b = G1 |- e :: t2

      d === a && extend(G,x,t1,G1) && typecheck(b)
    } ||
    exists[Env,LTerm,LTerm,LType,LType] { (G,e1,e2,t1,t2) =>
      val a = G |- (e1 app e2) :: t2
      val b = G |- e1 :: (t1 -> t2)
      val c = G |- e2 :: t1

      d === a && typecheck(b) && typecheck(c)
    }
}

trait STLC_ReverseDeBruijn extends STLC {

  // env is list of types, indexed by int

  type Sym = Int
  type Env = List[LType]

  def extend(g: Exp[Env], x: Exp[Sym], tp: Exp[LType], g1: Exp[Env]): Rel =
    g1 === cons(tp,g) && freein(g,x)


  def lookup(g: Exp[Env], x: Exp[Sym], tp: Exp[LType]): Rel = 
    exists[LType,Env] { (hd,tl) => 
      g === cons(hd,tl) && {
        freein(tl,x) && (hd === tp) || 
        lookup(tl,x,tp)
      }
    }

  def freein(g: Exp[Env], x: Exp[Sym]): Rel = 
    g === nil && x === zero ||
    exists[Sym,Env] { (x1,tl) => 
      g === cons(fresh[LType],tl) && x === succ(x1) && freein(tl,x1)
    }
}

trait STLC_Nat extends STLC {

  implicit class NatTermOps(x: Exp[LTerm]) {
    def +(y: Exp[LTerm]) = term[LTerm]("+", List(x,y))
  }

  def tnat = term[LType]("nat",Nil)

  def cnat(x: Exp[Int]) = term[LTerm]("nat",List(x))

  override def typecheck(d: Exp[Deriv]): Rel = 
    exists[Env,Int] { (G,x) =>
      val a = G |- cnat(x) :: tnat

      d === a
    } ||
    exists[Env,LTerm,LTerm] { (G,e1,e2) =>
      val a = G |- (e1 + e2) :: tnat
      val b = G |- e1 :: tnat
      val c = G |- e2 :: tnat

      d === a && typecheck(b) && typecheck(c)
    } ||
    super.typecheck(d)

}




import org.scalatest._

class TestNats extends FunSuite with Base with Engine with NatBase with ListBase {

  test("lte") {
    expectResult(List("s(s(s(s(x0))))")) {
      run[Int] { q =>
        lessThan(3, q)
      }
    }
  }

}

class TestLists extends FunSuite with Base with Engine with NatBase with ListBase {

  test("append") {
    expectResult(List("cons(a,cons(b,cons(c,cons(d,cons(e,cons(f,nil))))))")) {
      run[List[String]] { q =>
        append(list("a","b","c"), list("d","e","f"), q)
      }
    }
    expectResult(List("cons(d,cons(e,cons(f,nil)))")) {
      run[List[String]] { q =>
        append(list("a","b","c"), q, list("a","b","c","d","e","f"))
      }
    }
    expectResult(List("cons(a,cons(b,cons(c,nil)))")) {
      run[List[String]] { q =>
        append(q, list("d","e","f"), list("a","b","c","d","e","f"))
      }
    }
    expectResult(List(
      "pair(nil,cons(a,cons(b,cons(c,cons(d,cons(e,cons(f,nil)))))))", 
      "pair(cons(a,nil),cons(b,cons(c,cons(d,cons(e,cons(f,nil))))))", 
      "pair(cons(a,cons(b,nil)),cons(c,cons(d,cons(e,cons(f,nil)))))", 
      "pair(cons(a,cons(b,cons(c,nil))),cons(d,cons(e,cons(f,nil))))", 
      "pair(cons(a,cons(b,cons(c,cons(d,nil)))),cons(e,cons(f,nil)))", 
      "pair(cons(a,cons(b,cons(c,cons(d,cons(e,nil))))),cons(f,nil))", 
      "pair(cons(a,cons(b,cons(c,cons(d,cons(e,cons(f,nil)))))),nil)"
    )) {
      run[(List[String],List[String])] { q =>
        val q1,q2 = fresh[List[String]]
        (q === pair(q1,q2)) &&
        append(q1, q2, list("a","b","c","d","e","f"))
      }
    }
  }

  test("pair") {
    expectResult(List(
      "pair(nil,cons(a,cons(b,cons(c,cons(d,cons(e,cons(f,nil)))))))", 
      "pair(cons(a,nil),cons(b,cons(c,cons(d,cons(e,cons(f,nil))))))", 
      "pair(cons(a,cons(b,nil)),cons(c,cons(d,cons(e,cons(f,nil)))))", 
      "pair(cons(a,cons(b,cons(c,nil))),cons(d,cons(e,cons(f,nil))))", 
      "pair(cons(a,cons(b,cons(c,cons(d,nil)))),cons(e,cons(f,nil)))", 
      "pair(cons(a,cons(b,cons(c,cons(d,cons(e,nil))))),cons(f,nil))", 
      "pair(cons(a,cons(b,cons(c,cons(d,cons(e,cons(f,nil)))))),nil)"
    )) {
      run[(List[String],List[String])] {
        case Pair(q1,q2) =>
          append(q1, q2, list("a","b","c","d","e","f"))
      }
    }
    expectResult(List("pair(x0,x0)")) {
      run[(List[String],List[String])] {
        case Pair(q1,q2) => q1 === q2
      }
    }
  }


  test("map") {
    expectResult(List("cons(a,cons(b,cons(c,cons(d,cons(e,cons(f,nil))))))")) {
      run[List[String]] { q =>
        map[String,String]((q1,q2) => q1 === q2 , list("a","b","c","d","e","f"), q)

      }
    }
    def elems = (List("a","b","c","d","e","f") map (term(_,Nil)),List("A","B","C","A","B","C") map (term(_,Nil))).zipped map (pair(_,_))
    def f(xs:List[Exp[(String,String)]])(e1:Exp[String],e2:Exp[String]): Rel = xs match {
      case Nil => No
      case x::xs => 
        //println(extractStr(x))
        (pair(e1,e2) === x) || f(xs)(e1,e2)
    }
    expectResult(List("cons(A,cons(B,cons(C,cons(A,cons(B,cons(C,nil))))))")) {
      run[List[String]] { q =>
        map[String,String](f(elems), list("a","b","c","d","e","f"), q)
      }
    }
    expectResult(List(
      "cons(a,cons(b,cons(a,nil)))", 
      "cons(a,cons(b,cons(d,nil)))", 
      "cons(a,cons(e,cons(a,nil)))", 
      "cons(a,cons(e,cons(d,nil)))", 
      "cons(d,cons(b,cons(a,nil)))", 
      "cons(d,cons(b,cons(d,nil)))", 
      "cons(d,cons(e,cons(a,nil)))", 
      "cons(d,cons(e,cons(d,nil)))"
    )){
      run[List[String]] { q =>
        map[String,String](f(elems), q, list("A","B","A"))
      }
    }
  }


  test("flatMap") {
    expectResult(List("cons(a,cons(a,cons(b,cons(b,cons(c,cons(c,nil))))))")) {
      run[List[String]] { q =>
        flatMap[String,String]((q1,q2) => q2 === cons(q1,cons(q1,nil)), list("a","b","c"), q)
      }
    }
    expectResult(List("cons(a,cons(b,cons(c,nil)))")) {
      run[List[String]] { q =>
        flatMap[String,String]((q1,q2) => q2 === cons(q1,cons(q1,nil)), q, list("a","a","b","b","c","c"))
      }
    }
  }

  test("lessThan") {
    expectResult(List(
      "nil", 
      "cons(z,nil)", 
      "cons(z,cons(z,x0))", 
      "cons(z,cons(s(z),nil))", 
      "cons(z,cons(s(z),cons(z,x0)))", 
      "cons(z,cons(s(z),cons(s(z),x0)))"
    )) {
      run[List[Int]] { q =>
        lessLex[Int]((q1,q2) => lessThan(q1,q2), q, list(0,1,2))
      }
    }
  }
  test("lessThanOrd") {
    expectResult(List(
      "nil", 
      "cons(z,nil)", 
      "cons(z,cons(z,x0))", 
      "cons(z,cons(s(z),nil))", 
      "cons(z,cons(s(z),cons(z,x0)))", 
      "cons(z,cons(s(z),cons(s(z),x0)))"
    )) {
      run[List[Int]] { q =>
        q < List(0,1,2)
      }
    }
  }
}


class TestTrees extends FunSuite with Base with Engine with NatBase with ListBase with TreeBase {

  test("tree") {
    expectResult(List(
      "branch(branch(branch(nil,s(z),a,nil),s(s(z)),b,nil),s(s(s(z))),c,branch(nil,s(s(s(s(z)))),d,nil))"
    )) {
      run[List[String]] { q =>
        q === tree(1 -> "a", 2 -> "b", 3 -> "c", 4 -> "d")
      }
    }
  }

  test("lookupAll") {
    expectResult(List("c")) {
      run[String] { q =>
        val t = tree(1 -> "a", 2 -> "b", 3 -> "c", 4 -> "d")
        lookupAll(t,3,q)
      }
    }
  }

  test("lookupOrd") {
    expectResult(List("c")) {
      run[String] { q =>
        val t = tree(1 -> "a", 2 -> "b", 3 -> "c", 4 -> "d")
        lookup(t,3,q)
      }
    }
    expectResult(List("b")) {
      run[String] { q =>
        val t = tree(List(1,1,1) -> "a", List(1,2,2) -> "b", List(2,1,1) -> "c", List(3,2,2) -> "d")
        lookup(t,List(1,2,2),q)
      }
    }
    expectResult(List("c")) {
      run[String] { q =>
        val t = tree(List(1,1,1) -> "a", List(1,2,2) -> "b", List(2,1,1) -> "c", List(3,2,2) -> "d")
        lookup(t,List(2,1,1),q)
      }
    }
    expectResult(Nil) {
      run[String] { q =>
        val t = tree(List(1,1,1) -> "a", List(1,2,2) -> "b", List(2,1,1) -> "c", List(3,2,2) -> "d")
        lookup(t,List(2,2,2),q)
      }
    }
    expectResult(List("a","b","c")) {
      run[String] { q =>
        val t = tree(List(1,1,1) -> "a", List(1,2,2) -> "b", List(2,1,1) -> "c", List(3,2,2) -> "d")
        lookupLess(t,List(2,2,2),q)
      }
    }
  }

  test("lookup with holes") {
    expectResult(List("s(s(z))")) {
      run[Int] { q =>
        val t = tree(List(1,1,1) -> "a", List(1,2,2) -> "b", cons(q,List(1,1)) -> inject("c"), List(3,2,2) -> "d")
        lookup(t,List(2,1,1),"c")
      }
    }
    expectResult(List("s(z)")) {
      run[Int] { q =>
        val t = tree(List(1,1,1) -> "a", cons(q,List(2,2)) -> inject("b"), List(2,2,2) -> "c", List(3,2,2) -> "d")
        lookup(t,List(1,2,2),"b")
      }
    }
  }


}



trait TestGraphsBase extends FunSuite with Base with Engine with NatBase with ListBase with GraphBase with ReifyUtilsBase {

  test("graph") {

    val g = new Graph[String] {
      def edge(x:Exp[String],y:Exp[String]) = 
        (x === "a") && (y === "b") ||
        (x === "b") && (y === "c") ||
        (x === "c") && (y === "a")
    }

    expectResult(List(
      "pair(a,b)", "pair(b,c)", "pair(c,a)"
    )) {
      run[(String,String)] { case Pair(q1,q2) =>
        g.edge(q1,q2)
      }
    }
    expectResult(List(
      "b", "c", "a", "b", "c", "a", "b", "c", "a", "b"
    )) {
      runN[String](10) { q =>
        g.path("a",q)
      }
    }
  }

  test("trace") {
    val traceG = new Graph[String] {
      def edge(x:Exp[String],y:Exp[String]) = 
        (x === "a") && (y === "b") ||
        (x === "b") && (y === "c") ||
        (x === "c") && (y === "a")

      override def path(x:Exp[String],y:Exp[String]) = rule("path")(super.path)(x,y)
    }

    expectResult(List(
      "pair(b,cons(path(a,b),nil))", 
      "pair(c,cons(path(b,c),cons(path(a,c),nil)))", 
      "pair(a,cons(path(c,a),cons(path(b,a),cons(path(a,a),nil))))", 
      "pair(b,cons(path(a,b),cons(path(c,b),cons(path(b,b),cons(path(a,b),nil)))))", 
      "pair(c,cons(path(b,c),cons(path(a,c),cons(path(c,c),cons(path(b,c),cons(path(a,c),nil))))))"
    )) {
      runN[(String,List[String])](5) { case Pair(q1,q2) =>
        traceG.path("a",q1) && globalTrace() === q2
      }
    }
  }

}

class TestGraphs extends TestGraphsBase with ReifyUtils

class TestGraphsDynVars extends TestGraphsBase with ReifyUtilsDynVars


class TestMetaGraphs extends FunSuite with Base with Engine with MetaGraphBase {

  val g = new Graph[String] {
    def edge(x:Exp[String],y:Exp[String]) = 
      (x === "a") && (y === "b") ||
      (x === "b") && (y === "c") ||
      (x === "c") && (y === "a")
  }

  test("meta graph") {

    expectResult(List(
      "cons(to prove,cons(path(a,b),cons(prove,cons(nil,nil))))", 
      "cons(to prove,cons(path(b,c),cons(prove,cons(nil,nil))))", 
      "cons(to prove,cons(path(c,a),cons(prove,cons(nil,nil))))", 
      "cons(to prove,cons(path(a,x0),cons(prove,cons(cons(path(b,x0),nil),nil))))", 
      "cons(to prove,cons(path(b,x0),cons(prove,cons(cons(path(c,x0),nil),nil))))", 
      "cons(to prove,cons(path(c,x0),cons(prove,cons(cons(path(a,x0),nil),nil))))"
    )) {
      run[List[Any]] { q =>
        exists[Goal,List[Goal]] { (head,body) =>
          q === cons("to prove", cons(head, cons("prove", cons(body, nil)))) && 
          pathClause1(g)(head,body)
        }
      }
    }

    expectResult(List(
      "cons(to prove,cons(path(a,b),cons(prove,cons(nil,nil))))", 
      "cons(to prove,cons(path(b,c),cons(prove,cons(nil,nil))))", 
      "cons(to prove,cons(path(c,a),cons(prove,cons(nil,nil))))", 
      "cons(to prove,cons(path(a,x0),cons(prove,cons(cons(path(b,x0),nil),nil))))", 
      "cons(to prove,cons(path(b,x0),cons(prove,cons(cons(path(c,x0),nil),nil))))", 
      "cons(to prove,cons(path(c,x0),cons(prove,cons(cons(path(a,x0),nil),nil))))"
    )) {
      run[List[Any]] { q =>
        val clause = existsC[String,String] { (a,b) => pathClause2(g)(a,b) }        
        exists[Goal,List[Goal]] { (head,body) =>
          clause(head,body) && q === cons("to prove", cons(head, cons("prove", cons(body, nil))))
        }
      }
    }

    expectResult(List(
      "cons(to prove,cons(path(a,b),cons(prove,cons(nil,nil))))", 
      "cons(to prove,cons(path(b,c),cons(prove,cons(nil,nil))))", 
      "cons(to prove,cons(path(c,a),cons(prove,cons(nil,nil))))", 
      "cons(to prove,cons(path(a,x0),cons(prove,cons(cons(path(b,x0),nil),nil))))", 
      "cons(to prove,cons(path(b,x0),cons(prove,cons(cons(path(c,x0),nil),nil))))", 
      "cons(to prove,cons(path(c,x0),cons(prove,cons(cons(path(a,x0),nil),nil))))"
    )) {
      run[List[Any]] { q =>
        exists[Goal,List[Goal]] { (head,body) =>
          q === cons("to prove", cons(head, cons("prove", cons(body, nil)))) && 
          reifyClause(path2(g)(fresh,fresh))(head,body)
        }
      }
    }

  }


  test("graph interp") {

    expectResult(List(
      "b", "c", "a", "b", "c", "a", "b", "c", "a", "b"
    )) {
      runN[List[Any]](10) { q =>
        vanilla(pathClause1(g))(cons(pathTerm("a",q),nil))
      }
    }

  }

  test("graph interp2") {

    expectResult(List(
      "b", "c", "a", "b", "c", "a", "b", "c", "a", "b"
    )) {
      runN[String](10) { q =>
        vanilla2(path2(g)("a",q))
      }
    }

  }


}



class TestSTLC extends FunSuite with Base with Engine with STLC with STLC_ReverseDeBruijn with STLC_Nat {

  test("stlc1") {
    expectResult(List(
      "pair(cons(x0,nil),pair(z,x0))", 
      "pair(cons(x0,cons(x1,nil)),pair(s(z),x0))", 
      "pair(cons(x0,cons(x1,cons(x2,nil))),pair(s(s(z)),x0))"
    )) {
      runN[(Env,(Sym,LType))](3) { case Pair(g,Pair(x,tp)) =>
        lookup(g,x,tp)
      }
    }

    expectResult(List(
      "pair(cons(x0,nil),pair(z,x0))", 
      "pair(cons(x0,cons(x1,nil)),pair(s(z),x0))", 
      "pair(cons(x0,cons(x1,cons(x2,nil))),pair(s(s(z)),x0))"
    )) {
      runN[(Env,(Sym,LType))](3) { case Pair(g,Pair(x,tp)) =>
        val d1 = g |- sym(x) :: tp
        typecheck(d1)
      }
    }

    expectResult(List(
      "|-(cons(x0,cons(->(x0,x1),nil)),@(var(z),var(s(z))),x1)", 
      "|-(cons(x0,cons(x1,cons(->(x1,x2),nil))),@(var(z),var(s(z))),x2)", 
      "|-(cons(x0,cons(x1,cons(x2,cons(->(x2,x3),nil)))),@(var(z),var(s(z))),x3)"
    )) {
      runN[Deriv](3) { d =>
        val a = fresh[Env] |- (sym(0) app sym(1)) :: fresh[LType]
        d === a && typecheck(d)
      }
    }

    expectResult(List(
      "|-(nil,lam(z,lam(s(z),@(var(z),var(s(z))))),->(->(x0,x1),->(x0,x1)))"
    )) {
      runN[Deriv](3) { d =>
        val x,y = fresh[Sym]
        val a = nil |- lam(x, lam(y, (sym(x) app sym(y)))) :: fresh[LType]
        d === a && typecheck(d)
      }
    }

    expectResult(List(
      "|-(nil,lam(z,lam(s(z),+(var(z),var(s(z))))),->(nat,->(nat,nat)))"
    )) {
      runN[Deriv](3) { d =>
        val x,y = fresh[Sym]
        val a = nil |- lam(x, lam(y, (sym(x) + sym(y)))) :: fresh[LType]
        d === a && typecheck(d)
      }
    }

  }
}


class TestXX extends FunSuite with ListBase with NatBase with Engine with MetaGraphBase {

  trait LF

  val typ = term[LF]("type",Nil)

  def lf(x:Exp[LF],y:Exp[LF]) = term[Goal]("lf",List(x,y))

  val nat = term[LF]("nat",Nil)

  val z = term[LF]("z",Nil)

  def s(x:Exp[LF]) = term[LF]("s",List(x))

  def lte(x:Exp[LF],y:Exp[LF]) = term[LF]("lte",List(x,y))

  def lte_z = term[LF]("lte_z",Nil)
  def lte_s(x:Exp[LF]) = term[LF]("lte_s",List(x))

  def clauses(head: Exp[Goal], body: Exp[List[Goal]]): Rel = 
    (head === lf(nat,typ)) && (body === nil) || // nat
    (head === lf(z,nat))   && (body === nil) ||
    exists[LF] { x => 
      (head === lf(s(x),nat)) && (body === cons(lf(x,nat),nil))
    } || // lte
    exists[LF,LF] { (x,y) => 
      (head === lf(lte(x,y),typ)) && (body === cons(lf(x,nat),cons(lf(y,nat),nil)))
    } ||
    exists[LF,LF] { (x,y) => 
      (head === lf(lte_z,lte(z,x))) && (body === nil)
    } ||
    exists[LF,LF,LF] { (x,y,u) => 
      (head === lf(lte_s(u),lte(s(x),s(y)))) && (body === cons(lf(u,lte(x,y)),nil))
    }

/*
lte-trans : lte A B -> lte B C -> lte A C -> type.
%mode lte-trans +A +B -C.

-/z : lte-trans lte/z B lte/z.
-/s : lte-trans (lte/s A) (lte/s B) (lte/s C)
     <- lte-trans A B C.

%worlds () (lte-trans _ _ _).
%total A (lte-trans A _ _).
*/

  test("clause interp") {

    expectResult(List(
      "z", "s(z)", "s(s(z))"
    )) {
      runN[LF](3) { q =>
        vanilla(clauses)(cons(lf(q,nat),nil))
      }
    }

    expectResult(List(
      "pair(lte_s(lte_s(lte_z)),s(s(x0)))"
    )) {
      runN[(LF,LF)](3) { case Pair(q,n) =>
        vanilla(clauses)(cons(lf(q,lte(s(s(z)),n)),nil))
      }
    }


  }



}


class TestProb extends FunSuite with ListBase with NatBase with Engine {

  implicit val injectBool = new Inject[Boolean] {
    def toTerm(x: Boolean): Exp[Boolean] = term(x.toString,Nil)
  }
  implicit val injectDouble = new Inject[Double] {
    def toTerm(x: Double): Exp[Double] = term(x.toString,Nil)
  }

  val theprob = DVar(1.0)

  def flip(p: Double, x: Exp[Boolean]): Rel =
    { theprob := theprob() * p; x === true } || 
    { theprob := theprob() * (1.0 - p); x === false }

  def flip2(p: Double)(a: => Rel)(b: => Rel): Rel =
    { theprob := theprob() * p; a } || 
    { theprob := theprob() * (1.0 - p); b }


  test("prob1") {

    expectResult(List(
      "pair(true,0.2)", 
      "pair(false,0.8)"
    )) {
      runN[(Boolean,Double)](3) { case Pair(c,p) =>
        flip(0.2,c) && { p === theprob() }
      }
    }

    expectResult(List(
      s"pair(true,${1.0*0.2*0.2})"
    )) {
      runN[(Boolean,Double)](3) { case Pair(c,p) =>
        // what about call-time choice???
        flip(0.2,c) && flip(0.2,c) && { c === true } && { p === theprob() }
      }
    }

    expectResult(List(
      "pair(true,0.2)"
    )) {
      runN[(Boolean,Double)](3) { case Pair(c,p) =>
        flip2(0.2) { c === true } { c === false } && { c === true } && { p === theprob() }
      }
    }

  }

}


class TestTabling extends FunSuite with ListBase with NatBase with Engine {

  type Entry = Exp[Any]

  var table = new scala.collection.mutable.HashMap[String, Entry]

  var enabled = true

  def memo(goal: Exp[Any])(a: => Rel): Rel = new Custom("memo") {
    override def run(rec: (() => Rel) => (() => Unit) => Unit)(k: () => Unit): Unit = {
      val key = extractStr(goal)
      table.get(key) match {
        case Some(goal1) if enabled => 
          println(key + " seen: " + extractStr(goal1))
          goal === goal1 // FIXME: not general enough!!!
          // TODO: invoke continuation with all stored answers
          // store continuation so that it can be called for future answers
          k()
        case _ => 
          println(key)
          table(key) = goal
          rec(() => a) { () => 
            if (enabled) println("answer for "+key+": " + extractStr(goal)) 
            // TODO: memoize answer (if exists ignore?)
            // invoke all stored continuations with new answer
            k()
          }
      }
    }
  }


  def fib(x:Exp[Int], y:Exp[Int]): Rel = memo(term("fib",List(x,y))) {
    (x === 0) && (y === 1) ||
    (x === 1) && (y === 1) || {
      val x1,x2,y1,y2 = fresh[Int]
      (x === succ(x1)) && (x === (succ(succ(x2)))) &&
      fib(x1,y1) && fib(x2,y2) &&
      add(y1,y2,y)
    }
  }


  test("fib1") {
    table.clear; enabled = false

    expectResult(List(
      "s(s(s(s(s(s(s(s(z))))))))"
    )) {
      runN[Int](3) { q =>
        fib(5,q)
      }
    }
    println("done")
  }

  test("fib2") {
    table.clear; enabled = true 

    expectResult(List(
      "s(s(s(s(s(s(s(s(z))))))))"
    )) {
      runN[Int](3) { q =>
        fib(5,q)
      }
    }
    println("done")

  }
}

// TODO: finish it up!
class TestTabling2 extends FunSuite with ListBase with NatBase with Engine {

  type Answer = (Exp[Any] => Unit)
  type Cont = (() => Unit)

  val ansTable = new scala.collection.mutable.HashMap[String, scala.collection.mutable.HashMap[String, Answer]]
  val contTable = new scala.collection.mutable.HashMap[String, scala.collection.mutable.Set[Cont]] with scala.collection.mutable.MultiMap[String, Cont]

  def constrainAs(g: Exp[Any]): Answer = ??? // TODO!

  def memo(goal: Exp[Any])(a: => Rel): Rel = new Custom("memo") {
    override def run(rec: (() => Rel) => (() => Unit) => Unit)(k: () => Unit): Unit = {
      val key = extractStr(goal)
      contTable.addBinding(key, k)
      ansTable.get(key) match {
        case Some(answers) => 
          println("found" + key)
          for ((ansKey, ansConstr) <- answers) {
            rec{() => ansConstr(goal); Yes}(k) // clean it up to ansConstr(goal, k)
          }
        case _ => 
          println("not found: " + key)
          val ansMap = new scala.collection.mutable.HashMap[String, Answer]
          ansTable(key) = ansMap
          rec(() => a) { () => 
            val ansKey = extractStr(goal)
            ansMap.get(ansKey) match {
              case None => println("answer for "+key+": " + ansKey) 
                ansMap(ansKey) = constrainAs(goal)
                for (cont <- contTable(key)) {
                  cont()
                }
              case Some(_) => // fail
            }
          }
      }
    }
  }
}
