addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.4.0")
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "3.0.0")

addSbtPlugin("com.lightbend.cinnamon" % "sbt-cinnamon" % "2.11.1-SNAPSHOT")

credentials += Credentials(
  Path.userHome / ".lightbend" / "commercial.credentials"
)

resolvers += Resolver.url(
  "lightbend-commercial",
  url("https://repo.lightbend.com/commercial-releases")
)(Resolver.ivyStylePatterns)
