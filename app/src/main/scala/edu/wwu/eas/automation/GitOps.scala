package edu.wwu.eas.automation

import java.io.ByteArrayOutputStream

import scala.collection.mutable

import org.slf4j.LoggerFactory
import os.Path
import os.RelPath

extension (s: String)
  def shellEscape(): String =
    val needsQuotes = s.exists(GitOps.shellSpecials.contains(_))
    val b = mutable.StringBuilder()
    if needsQuotes then b += '"'
    s.foreach(_ match
      case '$'  => b ++= "\\$"
      case '`'  => b ++= "\\`"
      case '\\' => b ++= "\\\\"
      case '!'  => b ++= "\\!"
      case '"'  => b ++= "\\\""
      case '\n' => b ++= "\\\n"
      case x    => b += x
    )
    if needsQuotes then b += '"'
    b.toString()

object GitOps:

  val logger = LoggerFactory.getLogger("edu.wwu.eas.dba.banner.GitOps")

  val shellSpecials = " \t\r\n|&;()<>$`\\\"'*?[]#~%!"

  // val matchGitoliteRepository = raw"\s[CRW ]+\s*[^*]*".r
  val extractGitoliteRepository = raw"\s[CRW ]+\s*([^\s]+)\s*".r

  def getGitoliteRepositoryList(url: String): Seq[RelPath] =
    os.proc("ssh", url, "info")
      .call()
      .out
      .lines()
      .filter(!_.contains("..*"))
      .collect(_ match
        case extractGitoliteRepository(r) => RelPath(r + ".git")
      )

  def escapeCommand(p: os.proc): String =
    p.command
      .map(_.value)
      .flatten
      .map(_.shellEscape())
      .map(_.replaceAll("oauth2:\\w+@", "<token>"))
      .mkString(" ")

  def exec(
    cmd: os.proc,
    dir: Path = os.pwd,
    capture: Option[mutable.StringBuilder] = None,
    changes: Boolean = false,
    continueOnError: Boolean = false,
    quiet: Boolean = false,
    verbose: Boolean = false
  )(using options: Options): Int =
    if options.debug then logger.debug(escapeCommand(cmd))
    else if !quiet && (options.verbose || options.noChange) then logger.info(escapeCommand(cmd))
    else if !quiet && verbose then logger.warn(escapeCommand(cmd))

    if !(changes && options.noChange) then
      logger.debug(s"exec dir = $dir")
      val result = cmd.call(
        cwd = dir,
        stdin = os.Inherit,
        stdout = if capture.isDefined then os.Pipe else os.Inherit,
        stderr = os.Inherit,
        check = false
      )
      capture.foreach(_ ++= result.out.text())
      if result.exitCode != 0 then
        if continueOnError then
          if options.debug then logger.debug(s"command failed: ${escapeCommand(cmd)}")
          else if !quiet && options.verbose then logger.info(s"command failed: ${escapeCommand(cmd)}")
          else if !quiet && verbose then logger.warn(s"command failed: ${escapeCommand(cmd)}")
        else throw new ReposyncException(s"command failed: ${escapeCommand(cmd)}")
      result.exitCode
    else 0
