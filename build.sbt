
inThisBuild(List(
  organization := "io.github.alexarchambault",
  homepage := Some(url("https://github.com/alexarchambault/missing-link-cli")),
  licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  developers := List(
    Developer(
      "alexarchambault",
      "Alexandre Archambault",
      "",
      url("https://github.com/alexarchambault")
    )
  )
))

enablePlugins(PackPlugin)

libraryDependencies ++= Seq(
  "com.spotify" % "missinglink-core" % "0.2.1",
  "com.github.alexarchambault" %% "case-app" % "2.0.4"
)
