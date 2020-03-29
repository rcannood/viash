package com.dataintuitive.viash.functionality

import scala.io.Source
import io.circe.yaml.parser
import java.nio.file.Paths
import java.io.File
import platforms.Platform

case class Functionality(
  name: String,
  description: Option[String],
  platform: Option[Platform], 
  inputs: List[DataObject[_]],
  outputs: List[DataObject[_]],
  resources: List[Resource],
  private var _rootDir: Option[File] = None // :/
) {
  def mainResource =
    resources.find(_.name.startsWith("main"))

  def rootDir = {
    _rootDir match {
      case Some(f) => f
      case None => throw new RuntimeException("root directory of functionality object has not been defined yet")
    }
  }
  def rootDir_= (newValue: File) = {
    _rootDir = 
      if (newValue.isFile()) {
        Some(newValue.getParentFile())
      } else {
        Some(newValue)
      }
  }

  def mainCode: Option[String] = {
    if (platform.isEmpty || platform.exists(_.`type` == "Native") || mainResource.isEmpty) {
      None
    } else if (mainResource.get.code.isDefined) {
      mainResource.get.code
    } else {
      val mainPath = Paths.get(rootDir.getPath(), mainResource.get.path.get).toFile()
      Some(Source.fromFile(mainPath).mkString(""))
    }
  }

  def mainCodeWithArgParse = {
    mainCode.map(code =>
      platform match {
        case Some(pl) if (pl.`type` == "Native") => code
        case None => code
        case Some(pl) => {
          val regex = s"""
            |${pl.commentStr}[^\n]*PORTASH START.*
            |${pl.commentStr}[^\n]*PORTASH END[^\n]*
            |""".stripMargin
          val replace = s"""
            |${pl.commentStr} PORTASH START
            |${pl.commentStr} The following code has been auto-generated by Portash.
            |${pl.generateArgparse(this)}
            |${pl.commentStr} PORTASH END
            |""".stripMargin

          import java.util.regex.Pattern
          Pattern.compile(regex, Pattern.DOTALL)
            .matcher(code)
            .replaceAll(replace)
        }
      }
    )

  }
}

object Functionality {
  def parse(file: java.io.File): Functionality = {
    val str = Source.fromFile(file).mkString
    val fun = parser.parse(str)
      .fold(throw _, _.as[Functionality])
      .fold(throw _, identity)

    // save root directory of Functionality object
    fun.rootDir = file

    require(
      fun.resources.count(_.name.startsWith("main")) == 1,
      message = "Define exactly one resource whose name begins with 'main'."
    )

    val mr = fun.mainResource.get
    require(
      fun.platform.isDefined || (mr.path.isDefined && mr.isExecutable.getOrElse(true)),
      message = "If the platform is not specified, the main resource should be a standalone executable."
    )

    fun
  }
}
