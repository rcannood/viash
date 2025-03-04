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

package io.viash.runners.nextflow

import scala.collection.immutable.ListMap
import io.viash.schemas._
import io.viash.helpers.data_structures.OneOrMore
import io.viash.helpers.data_structures.listToOneOrMore

@description("Allows tweaking how the Nextflow Config file is generated.")
@since("Viash 0.7.4")
case class NextflowConfig(
  @description(
    """A series of default labels to specify memory and cpu constraints.
      |
      |The default memory labels are defined as "mem1gb", "mem2gb", "mem4gb", ... upto "mem512tb" and follows powers of 2.
      |The default cpu labels are defined as "cpu1", "cpu2", "cpu5", "cpu10", ... upto "cpu1000" and follows a semi logarithmic scale (1, 2, 5 per decade).
      |
      |Conceptually it is possible for a Viash Config to overwrite the full labels parameter, however likely it is more efficient to add additional labels
      |in the Viash Package with a config mod.
      |""")
  @exampleWithDescription(
    """labels:
      |  lowmem: "memory = 4.GB"
      |  lowcpu: "cpus = 4"
      |  midmem: "memory = 25.GB"
      |  midcpu: "cpus = 10"
      |  highmem: "memory = 50.GB"
      |  highcpu: "cpus = 20"
      |  vhighmem: "memory = 100.GB"
      |  vhighcpu: "cpus = 40"
      |""",
    "yaml",
    "Replace the default labels with a different set of labels")
  @exampleWithDescription(
    """-c '.runners[.type == "nextflow"].config.labels.lowmem := "memory = 4.GB";.runners[.type == "nextflow"].config.labels.lowcpu := "cpus = 4"'""",
    "viash_config_mod",
    "Add 'lowmem' and 'lowcpu' to the default labels by using a config mod")
  @exampleWithDescription(
    """config_mods: |
      |  .runners[.type == "nextflow"].config.labels.lowmem := "memory = 4.GB"
      |  .runners[.type == "nextflow"].config.labels.lowcpu := "cpus = 4"
      |""",
    "viash_package_file",
    "Add 'lowmem' and 'lowcpu' to the default labels by using the Viash Package file"
  )
  @exampleWithDescription(
    """config_mods: |
      |  .runners[.type == "nextflow"].config.labels := { lowmem: "memory = 4.GB", lowcpu: "cpus = 4", midmem: "memory = 25.GB", midcpu: "cpus = 10", highmem: "memory = 50.GB", highcpu: "cpus = 20", vhighmem: "memory = 100.GB", vhighcpu: "cpus = 40" }
      |""",
    "viash_package_file",
    "Replace the default labels with a different set of labels by using the Viash Package file"
  )
  @default("A series of default labels to specify memory and cpu constraints")
  labels: ListMap[String, String] = ListMap(
    NextflowConfig.logarithmicIterator
      .dropWhile(_ < 1 * NextflowConfig.GB)
      .takeWhile(_ <= 500 * NextflowConfig.TB)
      .map{i =>
        val kSize = NextflowConfig.humanReadableByteSize(i, "%1.0f", 1000, s => s.stripLeading().toLowerCase()) // "1gb"
        (s"mem$kSize", s"memory = $i.B")
      } ++
    NextflowConfig.binaryIterator
      .dropWhile(_ < 1 * NextflowConfig.GiB)
      .takeWhile(_ <= 512 * NextflowConfig.TiB)
      .map{i =>
        val kSize = NextflowConfig.humanReadableByteSize(i, "%1.0f", 1024, s => s.stripLeading().toLowerCase()) // "1gib"
        (s"mem$kSize", s"memory = $i.B")
      } ++
    NextflowConfig.logarithmicIterator
      .takeWhile(_ <= 1000)
      .map(i => (s"cpu$i", s"cpus = $i")) : _*
  ),

  @description(
    """Includes a single string or list of strings into the nextflow.config file.
      |This can be used to add custom profiles or include an additional config file.
      |""")
  @example(
    """script:
      |  - |
      |    profiles {
      |    ...
      |    }
      |""",
    "yaml")
  @example("""script: includeConfig("config.config")""", "yaml")
  @default("Empty")
  script: OneOrMore[String] = Nil
)

object NextflowConfig {

  val KB  = 1000L
  val MB  = 1000L*1000
  val GB  = 1000L*1000*1000
  val TB  = 1000L*1000*1000*1000
  val PB  = 1000L*1000*1000*1000*1000
  val EB  = 1000L*1000*1000*1000*1000*1000
  val KiB = 1024L
  val MiB = 1024L*1024
  val GiB = 1024L*1024*1024
  val TiB = 1024L*1024*1024*1024
  val PiB = 1024L*1024*1024*1024*1024
  val EiB = 1024L*1024*1024*1024*1024*1024

  // Returns 1, 2, 5, 10, 20, 50, 100 ...
  def logarithmicIterator: Seq[Long] = 
    for (i <- Seq.range(0, 19); j <- Seq(1, 2, 5) )
      yield j * Math.pow(10, i).toLong

  // Returns 1, 2, 4, 8, 16, 32, ...
  def binaryIterator: Seq[Long] =
    for (i <- Seq.range(0, 63))
      yield 1L << i

  /**
    * @see https://stackoverflow.com/questions/35609587/human-readable-size-units-file-sizes-for-scala-code-like-duration
    * Long is limited to 8 ExaByte - 1 byte
    */
  def humanReadableByteSize(fileSize: Long, format: String = "%1.2f", base: Int, unitTranslator: String => String = s => s): String = {
    assert(base == 1000 || base == 1024)

    if(fileSize <= 0) return "0 B"
    val unitsSI: Array[String] = Array("B", "KB", "MB", "GB", "TB", "PB", "EB")
    val unitsIEC: Array[String] = Array("B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB")
    val units = if(base == 1000) unitsSI else unitsIEC
    val digitGroup: Int = (Math.log10(fileSize.toDouble)/Math.log10(base)).toInt

    val value = String.format(format, fileSize/Math.pow(base, digitGroup))
    val unit = unitTranslator(s" ${units(digitGroup)}")

    s"$value$unit"
  }
}