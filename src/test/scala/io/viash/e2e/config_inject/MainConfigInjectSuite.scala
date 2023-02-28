package io.viash.e2e.config_inject

import io.viash._

import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.{Files, Paths, StandardCopyOption}

import io.viash.config.Config

import scala.io.Source
import io.viash.helpers.{IO, Exec}

class MainConfigInjectSuite extends AnyFunSuite with BeforeAndAfterAll {
  private val temporaryFolder = IO.makeTemp("viash_tester")

  test("config inject works") {
    val srcPath = Paths.get(getClass.getResource(s"/testpython/").getPath())
    val destPath = temporaryFolder.resolve("inject_test")
    destPath.toFile().mkdirs()
    
    // check source file exists
    val srcConfigFile = srcPath.resolve("config.vsh.yaml")
    val functionality = Config.read(srcConfigFile.toString()).functionality
    assert(srcConfigFile.toFile().exists, "Check source config exists")

    // copy to destination
    TestHelper.copyFolder(srcPath, destPath)
    val configFile = destPath.resolve("config.vsh.yaml")
    val scriptFile = destPath.resolve(functionality.mainScript.get.path.get) // assume all of these things exist
    assert(configFile.toFile().exists, "Check dest config exists")

    // inject script
    TestHelper.testMain(
      "config", "inject",
      configFile.toString(),
    )

    assert(scriptFile.toFile().exists, "Check dest script still exists")

    val code = Source.fromFile(scriptFile.toString()).getLines().mkString("\n")
    assert(code.contains("# The following code has been auto-generated by Viash"), "Script has been injected with a Viash header")
    assert(code.contains("# The following code has been auto-generated by Viash"), "'input': '/path/to/file'") // check required param has a value
  }

  override def afterAll(): Unit = {
    IO.deleteRecursively(temporaryFolder)
  }
}