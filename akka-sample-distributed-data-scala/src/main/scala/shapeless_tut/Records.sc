import shapeless._
import record._
import shapeless_tut.IceCream

val sundae = LabelledGeneric[IceCream].to(IceCream("Sundae", 2, false))

sundae.get('name)
sundae.get('numCherries)

val su = sundae.updated('numCherries, 20)
su.get('numCherries)

val sr = sundae.remove('inCone)
sr._1

val suw = sundae.updateWith('name)("MASSIVE " + _)
suw.get('name)

sundae.toMap.keys.map(_.name)


