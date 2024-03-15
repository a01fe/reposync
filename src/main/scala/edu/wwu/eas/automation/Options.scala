package edu.wwu.eas.automation

import java.io.File
import java.util.Properties

import scala.collection.mutable
import scala.util.matching.Regex

import org.virtuslab.yaml.*
import scopt.Read

case class Options(
  config: Option[java.io.File] = None,
  debug: Boolean = false,
  debugGitLabApi: Boolean = false,
  force: Boolean = false,
  ignoreSnubbies: Boolean = false,
  noChange: Boolean = false,
  repoDir: os.Path = os.pwd,
  token: String = "",
  verbose: Boolean = false
)

case class Config(
  gitLabUrl: String,
  gitLabRoot: String,
  remoteGitUrl: String,
  localGitUrl: String,
  snubbies: Set[String]
) derives YamlCodec

object OptionParser:

  val program = "reposync"
  var version: Option[String] = None

  implicit val osPathRead: Read[os.Path] = Read.reads(os.Path(_, os.pwd))

  def parse(args: String*): (Options, Config) =
    val buildProperties = new Properties()
    buildProperties.load(os.read.inputStream(os.resource / "build.properties"))
    version = Option(buildProperties.get("version")).map(_.toString())
    val parser = new scopt.OptionParser[Options](program):
      head(program, OptionParser.version.getOrElse("[unknown]"))

      opt[java.io.File]('c', "config")
        .action((x, c) => c.copy(config = Some(x)))
        .text("override built-in configuration file")
        .valueName("<file>")

      opt[Unit]('d', "debug")
        .action((_, c) => c.copy(debug = true))
        .text("display debugging output")

      opt[Unit]("debug-gitlab-api")
        .action((_, c) => c.copy(debugGitLabApi = true))
        .text("enable GitLab API request and response logging")

      help('h', "help").text("display this usage message")

      opt[Unit]('f', "force")
        .action((_, c) => c.copy(force = true))
        .text("use --force on push to local repositories")

      opt[Unit]('i', "ignore-snubbies")
        .action((_, c) => c.copy(ignoreSnubbies = true))
        .text("try to mirror repositories on the snubbies list")

      opt[Unit]('n', "no-change")
        .action((_, c) => c.copy(noChange = true))
        .text("no changes, display actions only")

      opt[os.Path]('r', "repo-dir")
        .action((x, c) => c.copy(repoDir = x))
        .text("repo cache directory")
        .valueName("<directory>")

      opt[String]('t', "token")
        .action((x, c) => c.copy(token = x))
        .required()
        .text("GitLab API token")
        .valueName("<token>")

      opt[Unit]('v', "verbose")
        .action((_, c) => c.copy(verbose = true))
        .text("display verbose output")

    parser.parse(args, Options()) match
      case Some(options) =>
        val configFile = options.config
          .map(os.Path(_, os.pwd))
          .getOrElse(os.resource / "config.yaml")
        val config = os.read(configFile).as[Config] match
          case Right(c) => c
          case Left(e)  => throw new ReposyncException(s"Cannot read configuration: ${e.msg}")
        (options, config)

      case None => System.exit(1); null
