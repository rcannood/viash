package com.dataintuitive.viash.functionality.dataobjects

case class DoubleObject(
    name: String,
    alternatives: Option[List[String]] = None,
    description: Option[String] = None,
    default: Option[Double] = None,
    required: Option[Boolean] = None,
    tag: Option[String] = None,
    direction: Direction = Input,
    passthrough: Boolean = false
) extends DataObject[Double] {
  override val `type` = "double"
}
