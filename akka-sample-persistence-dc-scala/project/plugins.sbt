addSbtPlugin("com.dwijnand" % "sbt-dynver" % "3.0.0")
addSbtPlugin("com.github.gseitz" % "sbt-protobuf" % "0.6.3")

// Cinnamon
addSbtPlugin("com.lightbend.cinnamon" % "sbt-cinnamon" % "2.10.11")
credentials += Credentials(Path.userHome / ".lightbend" / "commercial.credentials")
resolvers += Resolver.url("com-ivy",
  url("https://repo.lightbend.com/commercial-releases/"))(Resolver.ivyStylePatterns)

