package monocle

import scalaz.{Applicative, Category, Maybe, Monoid, \/}


/**
 * A [[PPrism]] can be seen as a pair of functions:
 *  - `getOrModify: S => T \/ A`
 *  - `reverseGet : B => T`
 *
 * A [[PPrism]] could also be defined as a weaker [[PIso]] where get can fail.
 *
 * Typically a [[PPrism]] or [[Prism]] encodes the relation between a Sum or
 * CoProduct type (e.g. sealed trait) and one of it is element.
 *
 * [[PPrism]] stands for Polymorphic Prism as it set and modify methods change
 * a type `A` to `B` and `S` to `T`.
 * [[Prism]] is a type alias for [[PPrism]] where the type of target cannot be modified:
 * {{{
 * type Prism[S, A] = PPrism[S, S, A, A]
 * }}}
 *
 * A [[PPrism]] is also a valid  [[Fold]], [[POptional]], [[PTraversal]] and [[PSetter]]
 *
 * @see PrismLaws in monocle-law module
 *
 * @tparam S the source of a [[PPrism]]
 * @tparam T the modified source of a [[PPrism]]
 * @tparam A the target of a [[PPrism]]
 * @tparam B the modified target of a [[PPrism]]
 */
abstract class PPrism[S, T, A, B] extends Serializable { self =>

  /** get the target of a [[PPrism]] or modify the source in case there is no target */
  def getOrModify(s: S): T \/ A

  /** get the modified source of a [[PPrism]] */
  def reverseGet(b: B): T

  /** get the target of a [[PPrism]] or nothing if there is no target */
  def getMaybe(s: S): Maybe[A]

  /** modify polymorphically the target of a [[PPrism]] with an [[Applicative]] function */
  @inline final def modifyF[F[_] : Applicative](f: A => F[B])(s: S): F[T] =
    getOrModify(s).fold(
      t => Applicative[F].point(t),
      a => Applicative[F].map(f(a))(reverseGet)
    )

  /** modify polymorphically the target of a [[PPrism]] with a function */
  @inline final def modify(f: A => B): S => T =
    getOrModify(_).fold(identity,a => reverseGet(f(a)))

  /**
   * modify polymorphically the target of a [[PPrism]] with a function.
   * return empty if the [[PPrism]] is not matching
   */
  @inline final def modifyMaybe(f: A => B): S => Maybe[T] =
    s => getMaybe(s).map(_ => modify(f)(s))

  /** set polymorphically the target of a [[PPrism]] with a value */
  @inline final def set(b: B): S => T =
    modify(_ => b)

  /**
   * set polymorphically the target of a [[PPrism]] with a value.
   * return empty if the [[PPrism]] is not matching
   */
  @inline final def setMaybe(b: B): S => Maybe[T] =
    modifyMaybe(_ => b)

  /** check if a [[PPrism]] has a target */
  @inline final def isMatching(s: S): Boolean =
    getMaybe(s).isJust

  /** create a [[Getter]] from the modified target to the modified source of a [[PPrism]] */
  @inline final def re: Getter[B, T] =
    Getter(reverseGet)

  /************************************************************/
  /** Compose methods between a [[PPrism]] and another Optics */
  /************************************************************/

  /** compose a [[PPrism]] with a [[Fold]] */
  @inline final def composeFold[C](other: Fold[A, C]): Fold[S, C] =
    asFold composeFold other

  /** compose a [[PPrism]] with a [[Getter]] */
  @inline final def composeGetter[C](other: Getter[A, C]): Fold[S, C] =
    asFold composeGetter other

  /** compose a [[PPrism]] with a [[PSetter]] */
  @inline final def composeSetter[C, D](other: PSetter[A, B, C, D]): PSetter[S, T, C, D] =
    asSetter composeSetter other

  /** compose a [[PPrism]] with a [[PTraversal]] */
  @inline final def composeTraversal[C, D](other: PTraversal[A, B, C, D]): PTraversal[S, T, C, D] =
    asTraversal composeTraversal other

  /** compose a [[PPrism]] with a [[POptional]] */
  @inline final def composeOptional[C, D](other: POptional[A, B, C, D]): POptional[S, T, C, D] =
    asOptional composeOptional other

  /** compose a [[PPrism]] with a [[PLens]] */
  @inline final def composeLens[C, D](other: PLens[A, B, C, D]): POptional[S, T, C, D] =
    asOptional composeOptional other.asOptional

  /** compose a [[PPrism]] with a [[PPrism]] */
  @inline final def composePrism[C, D](other: PPrism[A, B, C, D]): PPrism[S, T, C, D] =
    new PPrism[S, T, C, D]{
      def getOrModify(s: S): T \/ C =
        self.getOrModify(s).flatMap(a => other.getOrModify(a).bimap(self.set(_)(s), identity))

      def reverseGet(d: D): T =
        self.reverseGet(other.reverseGet(d))

      def getMaybe(s: S): Maybe[C] =
        self.getMaybe(s) flatMap other.getMaybe
    }

  /** compose a [[PPrism]] with a [[PIso]] */
  @inline final def composeIso[C, D](other: PIso[A, B, C, D]): PPrism[S, T, C, D] =
    composePrism(other.asPrism)

  /********************************************/
  /** Experimental aliases of compose methods */
  /********************************************/

  /** alias to composeTraversal */
  @inline final def ^|->>[C, D](other: PTraversal[A, B, C, D]): PTraversal[S, T, C, D] =
    composeTraversal(other)

  /** alias to composeOptional */
  @inline final def ^|-?[C, D](other: POptional[A, B, C, D]): POptional[S, T, C, D] =
    composeOptional(other)

  /** alias to composePrism */
  @inline final def ^<-?[C, D](other: PPrism[A, B, C, D]): PPrism[S, T, C, D] =
    composePrism(other)

  /** alias to composeLens */
  @inline final def ^|->[C, D](other: PLens[A, B, C, D]): POptional[S, T, C, D] =
    composeLens(other)

  /** alias to composeIso */
  @inline final def ^<->[C, D](other: PIso[A, B, C, D]): PPrism[S, T, C, D] =
    composeIso(other)

  /******************************************************************/
  /** Transformation methods to view a [[PPrism]] as another Optics */
  /******************************************************************/

  /** view a [[PPrism]] as a [[Fold]] */
  @inline final def asFold: Fold[S, A] = new Fold[S, A]{
    def foldMap[M: Monoid](f: A => M)(s: S): M =
      getMaybe(s) map f getOrElse Monoid[M].zero
  }

  /** view a [[PPrism]] as a [[Setter]] */
  @inline final def asSetter: PSetter[S, T, A, B] =
    new PSetter[S, T, A, B]{
      def modify(f: A => B): S => T =
        self.modify(f)

      def set(b: B): S => T =
        self.set(b)
    }

  /** view a [[PPrism]] as a [[PTraversal]] */
  @inline final def asTraversal: PTraversal[S, T, A, B] =
    new PTraversal[S, T, A, B] {
      def modifyF[F[_]: Applicative](f: A => F[B])(s: S): F[T] =
        self.modifyF(f)(s)
    }

  /** view a [[PPrism]] as a [[POptional]] */
  @inline final def asOptional: POptional[S, T, A, B] =
    new POptional[S, T, A, B]{
      def getOrModify(s: S): T \/ A =
        self.getOrModify(s)

      def set(b: B): S => T =
        self.set(b)

      def getMaybe(s: S): Maybe[A] =
        self.getMaybe(s)

      def modify(f: A => B): S => T =
        self.modify(f)

      def modifyF[F[_]: Applicative](f: A => F[B])(s: S): F[T] =
        self.modifyF(f)(s)
    }
}

object PPrism extends PrismInstances {
  /** create a [[PPrism]] using the canonical functions: getOrModify and reverseGet */
  def apply[S, T, A, B](_getOrModify: S => T \/ A)(_reverseGet: B => T): PPrism[S, T, A, B] =
    new PPrism[S, T, A, B]{
      def getOrModify(s: S): T \/ A =
        _getOrModify(s)

      def reverseGet(b: B): T =
        _reverseGet(b)

      def getMaybe(s: S): Maybe[A] =
        getOrModify(s).toMaybe
    }
}

object Prism {
  /** alias for [[PPrism]] apply restricted to monomorphic update */
  def apply[S, A](_getMaybe: S => Maybe[A])(_reverseGet: A => S): Prism[S, A] =
    new Prism[S, A]{
      def getOrModify(s: S): S \/ A =
        _getMaybe(s) \/> s

      def reverseGet(b: A): S =
        _reverseGet(b)

      def getMaybe(s: S): Maybe[A] =
        _getMaybe(s)
    }
}

sealed abstract class PrismInstances {
  implicit val prismCategory: Category[Prism] = new Category[Prism] {
    def id[A]: Prism[A, A] =
      Iso.id[A].asPrism

    def compose[A, B, C](f: Prism[B, C], g: Prism[A, B]): Prism[A, C] =
      g composePrism f
  }
}