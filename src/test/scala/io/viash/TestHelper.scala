package io.viash

import java.io.{ByteArrayOutputStream, File, FileInputStream, IOException, UncheckedIOException}
import java.security.{DigestInputStream, MessageDigest}
import org.scalatest.matchers.must.Matchers.{assertThrows, intercept}
import org.scalatest.Tag

import java.nio.file.{Files, Path, Paths}
import scala.reflect.ClassTag

object TestHelper {

  case class TestMainOutput(
    stdout: String,
    stderr: String,
    exitCode: Option[Int],
    exceptionText: Option[String],
  )

  /**
   * Method to capture the console stdout and stderr generated by Main.main() so we can analyse what's being outputted to the console
   * @param args all the arguments typically passed to Main.main()
   * @return TestMainOutput containing the console output text and exit code
   */
  def testMain(args: String*): TestMainOutput = testMain(None, args: _*)

  /**
   * Method to capture the console stdout and stderr generated by Main.main() so we can analyse what's being outputted to the console
   * @param workingDir the working directory to run the command in
   * @param args all the arguments typically passed to Main.main()
   * @return TestMainOutput containing the console output text and exit code
   */
  def testMain(workingDir: Option[Path], args: String*): TestMainOutput = {
    val outStream = new ByteArrayOutputStream()
    val errStream = new ByteArrayOutputStream()
    val exitCode = Console.withOut(outStream) {
      Console.withErr(errStream) {
        Main.mainCLI(args.toArray, workingDir)
      }
    }

    TestMainOutput(outStream.toString, errStream.toString, Some(exitCode), None)
  }

  /**
   * Method to capture the console stdout and stderr generated by Main.main() so we can analyse what's being outputted to the console.
   * Additionally it handles a thrown Exception/Throwable and returns the exception text.
   * @param args all the arguments typically passed to Main.main()
   * @return TestMainOutput containing the exception text and the console output text
   */
  def testMainException[T <: Throwable](args: String*)(implicit classTag: ClassTag[T]) : TestMainOutput = testMainException(None, args: _*)

  /**
   * Method to capture the console stdout and stderr generated by Main.main() so we can analyse what's being outputted to the console.
   * Additionally it handles a thrown Exception/Throwable and returns the exception text.
   * @param workingDir the working directory to run the command in
   * @param args all the arguments typically passed to Main.main()
   * @return TestMainOutput containing the exception text and the console output text
   */
  def testMainException[T <: Throwable](workingDir: Option[Path], args: String*)(implicit classTag: ClassTag[T]) : TestMainOutput = {
    val outStream = new ByteArrayOutputStream()
    val errStream = new ByteArrayOutputStream()
    val caught = intercept[T] {
      Console.withOut(outStream) {
        Console.withErr(errStream) {
          Main.mainCLI(args.toArray, workingDir)
        }
      }
    }

    TestMainOutput(outStream.toString, errStream.toString, None, Some(caught.getMessage))
  }


  // Removes console control sequences (e.g. colors) from a string
  def cleanConsoleControls(str: String): String = 
    str.replaceAll("(?:(?:\\x{001b}\\[)|\\x{009b})(?:(?:[0-9]{1,3})?(?:(?:;[0-9]{0,3})*)?[A-M|f-m])|\\x{001b}[A-M]", "")

  // Code borrowed from https://stackoverflow.com/questions/41642595/scala-file-hashing
  // Compute a hash of a file
  // The output of this function should match the output of running "md5 -q <file>"
  def computeHash(path: String): String = {
    val buffer = new Array[Byte](8192)
    val md5 = MessageDigest.getInstance("MD5")

    val dis = new DigestInputStream(new FileInputStream(new File(path)), md5)
    try { while (dis.read(buffer) != -1) { } } finally { dis.close() }

    md5.digest.map("%02x".format(_)).mkString
  }

}
