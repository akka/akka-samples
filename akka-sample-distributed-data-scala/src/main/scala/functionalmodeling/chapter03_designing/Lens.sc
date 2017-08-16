
import functionalmodeling.chapter03_designing.Smarter._

val a = Address(no ="B-12", street = "Monroe Stree",
  city = "Denver", state = "CO", zip = "80231")

addressNoLens.get(a)
addressNoLens.set(a, "B-24")

val c = Customer(id = 12, name = "James", address = a)

custAddressLens.get(c)
custAddressLens.set(c, a.copy(no = "B-24"))

val custAddrNoLens: Lens[Customer, String] = compose(custAddressLens, addressNoLens)

custAddrNoLens.get(c)
custAddrNoLens.set(c, "B88")


