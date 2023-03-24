package io.viash

import java.io.{ByteArrayOutputStream, File, FileInputStream, IOException, UncheckedIOException}
import java.security.{DigestInputStream, MessageDigest}
import org.scalatest.matchers.must.Matchers.{assertThrows, intercept}
import org.scalatest.Tag

import java.nio.file.{Files, Path, Paths}
import scala.reflect.ClassTag

object TestHelper {

  case class ExceptionOutput(
    exceptionText: String,
    output: String,
    error: String,
  )

  /**
   * Method to capture the console stdout generated by Main.main() so we can analyse what's being outputted to the console
   * As the capture prevents the stdout being printed to the console, we print it after the Main.main() is finished.
   * @param args all the arguments typically passed to Main.main()
   * @return a string of all the output
   */
  def testMain(args: String*) : String = {
    val os = new ByteArrayOutputStream()
    Console.withErr(os) {
      Console.withOut(os) {
        Main.mainCLI(args.toArray)
      }
    }

    val stdout = os.toString
    // Console.print(stdout)
    stdout
  }

  /**
   * Method to capture the console stdout and stderr generated by Main.main() so we can analyse what's being outputted to the console
   * As the capture prevents the stdout and stderr being printed to the console, we print it after the Main.main() is finished.
   * @param args all the arguments typically passed to Main.main()
   * @return a tuple of stdout and stderr strings of all the output
   */
  def testMainWithStdErr(args: String*) : (String, String, Int) = {
    val outStream = new ByteArrayOutputStream()
    val errStream = new ByteArrayOutputStream()
    val exitCode = Console.withOut(outStream) {
      Console.withErr(errStream) {
        Main.mainCLI(args.toArray)
      }
    }

    val stdout = outStream.toString
    val stderr = errStream.toString
    // Console.print(stdout)
    (stdout, stderr, exitCode)
  }

  /**
   * Method to capture the console stdout generated by Main.main() so we can analyse what's being outputted to the console
   * As the capture prevents the stdout being printed to the console, we print it after the Main.main() is finished.
   * Additionally it handles a thrown RuntimeException using assertThrows
   * @param args all the arguments typically passed to Main.main()
   * @return a string of all the output
   */
  def testMainException[T <: AnyRef: ClassTag](args: String*) : String = {
    val os = new ByteArrayOutputStream()
    assertThrows[T] {
      Console.withOut(os) {
        Main.mainCLI(args.toArray)
      }
    }

    val stdout = os.toString
    // Console.print(stdout)
    stdout
  }

  /**
   * Method to capture the console stdout generated by Main.main() so we can analyse what's being outputted to the console
   * As the capture prevents the stdout being printed to the console, we print it after the Main.main() is finished.
   * Additionally it handles a thrown RuntimeException using assertThrows
   * @param args all the arguments typically passed to Main.main()
   * @return ExceptionOutput containing the exception text and the console output text
   */
  def testMainException2[T <: Exception](args: String*) : ExceptionOutput = {
    val outStream = new ByteArrayOutputStream()
    val errStream = new ByteArrayOutputStream()
    val caught = intercept[Exception] {
      Console.withOut(outStream) {
        Console.withErr(errStream) {
          Main.mainCLI(args.toArray)
        }
      }
    }

    val stdout = outStream.toString
    val stderr = errStream.toString
    // Console.print(stdout)
    ExceptionOutput(caught.getMessage, stdout, stderr)
  }

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
