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

package com.dataintuitive.viash.functionality.resources

import com.dataintuitive.viash.functionality.Functionality

import java.net.URI

trait Script extends Resource {
  val meta: ScriptObject

  def generatePlaceholder(functionality: Functionality): String

  def readWithPlaceholder(implicit functionality: Functionality): Option[String] = {
    read.map(code => {
      val lines = code.split("\n")
      val startIndex = lines.indexWhere(_.contains("VIASH START"))
      val endIndex = lines.indexWhere(_.contains("VIASH END"))

      val li =
        Array(
          meta.commentStr + " The following code has been auto-generated by Viash.",
          generatePlaceholder(functionality)
        ) ++ {
          if (startIndex >= 0 && endIndex >= 0) {
            lines.slice(0, startIndex + 1) ++ lines.slice(endIndex, lines.length)
          } else {
            lines
          }
        }

      li.mkString("\n")
    })
  }
}

trait ScriptObject {
  val commentStr: String
  val extension: String
  val oType: String
  def command(script: String): String
  def commandSeq(script: String): Seq[String]
  def apply(
    path: Option[String] = None,
    text: Option[String] = None,
    dest: Option[String] = None,
    is_executable: Option[Boolean] = Some(true),
    parent: Option[URI] = None,
    oType: String = oType
  ): Script
}

object Script {
  val extensions =
    List(BashScript, PythonScript, RScript, JavaScriptScript, ScalaScript)
      .map(x => (x.extension.toLowerCase, x))
      .toMap

  def fromExt(extension: String): ScriptObject = {
    new RuntimeException("Unrecognised script extension: " + extension)
    extensions(extension.toLowerCase)
  }
}
