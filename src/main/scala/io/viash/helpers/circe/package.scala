/*
 * Copyright (C) 2020  Data Intuitive
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.viash.helpers

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.extras.Configuration
import java.net.URI
import data_structures.OneOrMore

package object circe {
  implicit val customConfig: Configuration =
    Configuration.default.withDefaults.withStrictDecoding

  // encoder and decoder for Either
  implicit def encodeEither[A,B](implicit ea: Encoder[A], eb: Encoder[B]): Encoder[Either[A,B]] = {
    _.fold(ea(_), eb(_))
  }

  implicit def decodeEither[A,B](implicit a: Decoder[A], b: Decoder[B]): Decoder[Either[A,B]] = {
    a either b
  }

  // NOTE: might not need encoders or decoders for OneOrMore because of the convertors from/to List[A]

  implicit def encodeOneOrMore[A](implicit enc: Encoder[List[A]]): Encoder[OneOrMore[A]] = { 
    oom: OneOrMore[A] => if (oom == null) enc(Nil) else enc(oom.toList)
  }

  implicit def decodeOneOrMore[A](implicit da: Decoder[A], dl: Decoder[List[A]]): Decoder[OneOrMore[A]] = {
    val l: Decoder[OneOrMore[A]] = da.map(OneOrMore(_))
    val r: Decoder[OneOrMore[A]] = dl.map(OneOrMore(_: _*))
    l or r
  }
  
  implicit def enrichJson(json: Json) = new RichJson(json)

  implicit val decodeStringLike: Decoder[String] =
    Decoder.decodeString or
      Decoder.decodeInt.map(_.toString) or
      Decoder.decodeFloat.map(_.toString) or
      Decoder.decodeBoolean.map(_.toString)
}