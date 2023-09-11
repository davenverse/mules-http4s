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


ThisBuild / crossScalaVersions := Seq("2.12.15", "2.13.12", "3.2.2")
ThisBuild / scalaVersion := "3.2.2"


ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("11"))
ThisBuild / tlJdkRelease := Some(8)

val catsV = "2.9.0"
val catsEffectV = "3.4.9"
val fs2V = "3.6.1"
val scodecCatsV = "1.2.0"
val http4sV = "0.23.18"
val circeV = "0.14.5"
val specs2V = "4.20.0"

val mulesV = "0.7.0"

// Projects
lazy val `mules-http4s` = tlCrossRootProject
  .aggregate(core, scodec)

lazy val core = crossProject(JVMPlatform, JSPlatform, NativePlatform)
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

      "io.chrisdavenport"           %%% "mules"                      % mulesV,
      
      "org.specs2"                  %%% "specs2-core"                % specs2V       % Test,
      "org.specs2"                  %%% "specs2-scalacheck"          % specs2V       % Test,
      "io.chrisdavenport"           %%% "cats-scalacheck"            % "0.3.2"       % Test,
      "org.typelevel"               %%% "cats-effect-testing-specs2" % "1.5.0"       % Test,
      "org.http4s"                  %%% "http4s-dsl"                 % http4sV       % Test,
      "com.comcast"                 %%% "ip4s-test-kit"              % "3.3.0"       % Test
    )
  ).jsSettings(
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule))
  )

lazy val scodec = crossProject(JVMPlatform, JSPlatform, NativePlatform)
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
      "com.comcast"                 %%% "ip4s-test-kit"              % "3.3.0"       % Test
    )
  ).jsSettings(
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule))
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