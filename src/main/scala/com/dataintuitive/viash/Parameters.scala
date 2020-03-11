package com.dataintuitive.viash

import java.io.File
import io.circe.{ Decoder, Encoder, HCursor, Json }
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

trait Parameter[Type] {
  val `type`: String
  val name: String
  val description: Option[String]
  val default: Option[Type]
  
  def validate(value: Type): Boolean = {
    true
  }
}

case class StringParameter(
    name: String,
    description: Option[String] = None,
    default: Option[String] = None
) extends Parameter[String] {
  override val `type` = "string"
}

case class IntegerParameter(
    name: String,
    description: Option[String] = None,
    default: Option[Int] = None
) extends Parameter[Int] {
  override val `type` = "integer"
}

case class DoubleParameter(
    name: String,
    description: Option[String] = None,
    default: Option[Double] = None
) extends Parameter[Double] {
  override val `type` = "double"
}

case class BooleanParameter(
    name: String,
    description: Option[String] = None,
    default: Option[Boolean] = None
) extends Parameter[Boolean] {
  override val `type` = "boolean"
}

case class FileParameter(
    name: String,
    description: Option[String] = None,
    default: Option[File] = None,
    must_exist: Boolean = false
) extends Parameter[File] {
  override val `type` = "file"
  
  override def validate(value: File) = {
    !must_exist || value.exists
  }
}

object implicits {
  implicit val encodeFile: Encoder[File] = new Encoder[File] {
    final def apply(file: File): Json = Json.obj(
      "path" → Json.fromString(file.getPath)
    )
  }
  
  implicit def encodeParameter[A <: Parameter[_]]: Encoder[A] = new Encoder[A] {
    val encodeStringParameter: Encoder[StringParameter] = deriveEncoder[StringParameter]
    val encodeIntegerParameter: Encoder[IntegerParameter] = deriveEncoder[IntegerParameter]
    val encodeDoubleParameter: Encoder[DoubleParameter] = deriveEncoder[DoubleParameter]
    val encodeBooleanParameter: Encoder[BooleanParameter] = deriveEncoder[BooleanParameter]
    val encodeFileParameter: Encoder[FileParameter] = deriveEncoder[FileParameter]
    
    final def apply(par: A): Json = {
      val typeJson = Json.obj("type" → Json.fromString(par.`type`))
      val objJson = par match {
        case s: StringParameter => encodeStringParameter(s) 
        case s: IntegerParameter => encodeIntegerParameter(s)
        case s: DoubleParameter => encodeDoubleParameter(s)
        case s: BooleanParameter => encodeBooleanParameter(s)
        case s: FileParameter => encodeFileParameter(s)
      }
      objJson deepMerge typeJson
    }
  }
  
  implicit val decodeFile: Decoder[File] = new Decoder[File] {
    final def apply(c: HCursor): Decoder.Result[File] =
      for {
        path <- c.downField("path").as[String]
      } yield {
        new File(path)
      }
  }
  implicit val decodeStringParameter: Decoder[StringParameter] = deriveDecoder[StringParameter]
  implicit val decodeIntegerParameter: Decoder[IntegerParameter] = deriveDecoder[IntegerParameter]
  implicit val decodeDoubleParameter: Decoder[DoubleParameter] = deriveDecoder[DoubleParameter]
  implicit val decodeBooleanParameter: Decoder[BooleanParameter] = deriveDecoder[BooleanParameter]
  implicit val decodeFileParameter: Decoder[FileParameter] = deriveDecoder[FileParameter]
  
  implicit def decodeParameter: Decoder[Parameter[_]] = new Decoder[Parameter[_]] {
    final def apply(c: HCursor): Decoder.Result[Parameter[_]] = {
      c.downField("type").as[String] match {
        case Right("string") =>
          decodeStringParameter(c).map(_.asInstanceOf[Parameter[_]])
        case Right("integer") => 
          decodeIntegerParameter(c).map(_.asInstanceOf[Parameter[_]])
        case Right(typ) =>
          throw new RuntimeException("Type " + typ + " is not recognised.")
        case Left(exception) =>
          throw exception
      }
    }
  }
}