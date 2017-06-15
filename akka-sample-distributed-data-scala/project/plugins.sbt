//resolvers += Resolver.url("bintray-sbt-plugins", url("http://dl.bintray.com/sbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)

resolvers += Resolver.url("bintray-sbt-assembly", url("https://dl.bintray.com/eed3si9n/sbt-plugins"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.3.11")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.3")

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.6.0")

//addSbtPlugin("com.dwijnand" % "sbt-dynver" % "1.1.1")
