package functionalmodeling.chapter03_designing.lens

/**
  *
  */
case class Lens[O, V](
                     get: O => V,
                     set: (O, V) => O
                     )

object Lens {
  def compose[Outer, Inner, Value](
                                  outer: Lens[Outer, Inner],
                                  inner: Lens[Inner, Value]
                                  ) = Lens[Outer, Value](
    get = outer.get andThen inner.get,
    set = (obj, value) => outer.set(obj, inner.set(outer.get(obj), value))
  )
}
