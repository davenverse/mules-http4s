package io.chrisdavenport.mules.http4s

import org.specs2._
import cats.effect._

object MainSpec extends mutable.Specification {

  "Main" should {
    "run a println" in {
      Main.run(List.empty[String]).unsafeRunSync.should_===(ExitCode.Success)
    }
  }

}