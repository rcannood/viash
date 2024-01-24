package io.viash.project

import io.viash.schemas._
import io.viash.helpers.data_structures.OneOrMore

@description("References to external resources related to the project.")
@since("Viash 0.9.0")
case class ProjectConfigReferences(
  @description("One or multiple DOI reference(s) of the project.")
  @example("doi: 10.1000/xx.123456.789", "yaml")
  doi: OneOrMore[String] = Nil,

  @description("One or multiple BibTeX reference(s) of the project.")
  @example(
    """bibtex: |
      |  @article{foo,
      |    title={Foo},
      |    author={Bar},
      |    journal={Baz},
      |    year={2024}
      |  }
      |""".stripMargin, "yaml")
  bibtex: OneOrMore[String] = Nil,
)
