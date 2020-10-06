package com.dataintuitive.viash

import config.Config

object Main {
  private val pkg = getClass.getPackage
  val name: String = if (pkg.getImplementationTitle != null) pkg.getImplementationTitle else "viash"
  val version: String = if (pkg.getImplementationVersion != null) pkg.getImplementationVersion else "test"

  def main(args: Array[String]) {
    val (viashArgs, runArgs) = args.span(_ != "--")

    val conf = new CLIConf(viashArgs)

    conf.subcommands match {
      case List(conf.run) =>
        val config = readConfigFromArgs(conf.run)
        ViashRun(config, args = runArgs.dropWhile(_ == "--"), keepFiles = conf.run.keep.toOption.map(_.toBoolean))
      case List(conf.build) =>
        val config = readConfigFromArgs(conf.build)
        ViashBuild(config, output = conf.build.output(), printMeta = conf.build.meta(), setup = conf.build.setup())
      case List(conf.test) =>
        val config = readConfigFromArgs(conf.test, modifyFun = false)
        ViashTest(config, keepFiles = conf.test.keep.toOption.map(_.toBoolean))
      case List(conf.namespace, conf.namespace.build) =>
        ViashNamespace.build(
          source = conf.namespace.build.src(),
          target = conf.namespace.build.target(),
          platform = conf.namespace.build.platform.toOption,
          platformID = conf.namespace.build.platformid.toOption,
          namespace = conf.namespace.build.namespace.toOption,
          setup = conf.namespace.build.setup(),
          parallel = conf.namespace.build.parallel()
        )
      case List(conf.namespace, conf.namespace.test) => // todo: allow tsv output
        ViashNamespace.test(
          source = conf.namespace.test.src(),
          platform = conf.namespace.test.platform.toOption,
          platformID = conf.namespace.test.platformid.toOption,
          namespace = conf.namespace.test.namespace.toOption,
          parallel = conf.namespace.test.parallel(),
          keepFiles = conf.namespace.test.keep.toOption.map(_.toBoolean),
          tsv = conf.namespace.test.tsv.toOption,
        )
      case _ =>
        println("No subcommand was specified. See `viash --help` for more information.")
    }
  }

  def readConfigFromArgs(
    subcommand: ViashCommand,
    modifyFun: Boolean = true
  ): Config = {
    Config.readSplitOrJoined(
      joined = subcommand.config.toOption,
      functionality = subcommand.functionality.toOption,
      platform = subcommand.platform.toOption,
      platformID = subcommand.platformid.toOption,
      modifyFun = modifyFun
    )
  }
}
