ThisBuild / tlBaseVersion := "0.4" // your current series x.y

ThisBuild / organization := "io.chrisdavenport"
ThisBuild / organizationName := "Christopher Davenport"
ThisBuild / licenses := Seq(License.MIT)
ThisBuild / developers := List(
  // your GitHub handle and name
  tlGitHubDev("christopherdavenport", "Christopher Davenport")
)

ThisBuild / tlCiReleaseBranches := Seq("main")

// true by default, set to false to publish to s01.oss.sonatype.org
ThisBuild / tlSonatypeUseLegacyHost := true


ThisBuild / crossScalaVersions := Seq("2.12.15", "2.13.8", "3.2.2")
ThisBuild / scalaVersion := "3.2.2"

val catsV = "2.9.0"
val catsEffectV = "3.4.8"
val fs2V = "3.2.7"
val scodecCatsV = "1.2.0"
val http4sV = "0.23.18"
val circeV = "0.14.5"
val specs2V = "4.19.2"

val mulesV = "0.7.0"

// Projects
lazy val `mules-http4s` = tlCrossRootProject
  .aggregate(core, scodec)

lazy val core = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(
    name := "mules-http4s",
    libraryDependencies ++= Seq(
      "org.typelevel"               %%% "cats-core"                  % catsV,
      "org.typelevel"               %%% "cats-effect"                % catsEffectV,

      "co.fs2"                      %%% "fs2-core"                   % fs2V,
      "co.fs2"                      %%% "fs2-io"                     % fs2V,

      "org.http4s"                  %%% "http4s-server"              % http4sV,
      "org.http4s"                  %%% "http4s-client"              % http4sV,

      "io.chrisdavenport"            %%% "mules"                      % mulesV,
      "io.chrisdavenport"            %%% "cats-effect-time"           % "0.2.0",
      "org.specs2"                  %%% "specs2-core"                % specs2V       % Test,
      "org.specs2"                  %%% "specs2-scalacheck"          % specs2V       % Test,
      "io.chrisdavenport"           %%% "cats-scalacheck"            % "0.3.2"       % Test,
      "org.typelevel"               %%% "cats-effect-testing-specs2" % "1.5.0"       % Test,
      "org.http4s"                  %%% "http4s-dsl"                 % http4sV       % Test,
      "com.comcast"                 %%% "ip4s-test-kit"              % "3.1.3"       % Test
    )
  )

lazy val scodec = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("scodec"))
  .dependsOn(core % "compile->compile, test->test")
  .settings(
    name := "mules-http4s-scodec",
    libraryDependencies ++= Seq(
      "org.scodec" %%% "scodec-core" % (
        if (scalaVersion.value.startsWith("2.")) "1.11.10" else "2.2.1"
      ),
      "org.scodec"                  %%% "scodec-cats"                % scodecCatsV,
      "org.specs2"                  %%% "specs2-core"                % specs2V       % Test,
      "org.specs2"                  %%% "specs2-scalacheck"          % specs2V       % Test,
      "io.chrisdavenport"           %%% "cats-scalacheck"            % "0.3.2"       % Test,
      "org.typelevel"               %%% "cats-effect-testing-specs2" % "1.5.0"       % Test,
      "org.http4s"                  %%% "http4s-dsl"                 % http4sV       % Test,
      "com.comcast"                 %%% "ip4s-test-kit"              % "3.1.3"       % Test
    )
  )

lazy val site = project.in(file("site"))
  .enablePlugins(TypelevelSitePlugin)
  .dependsOn(core.jvm)
  .settings{
    Seq(
      libraryDependencies ++= Seq(
        "io.chrisdavenport" %%% "mules-caffeine" % mulesV,
        "org.http4s" %%% "http4s-ember-client" % http4sV,
      )
    )
  }