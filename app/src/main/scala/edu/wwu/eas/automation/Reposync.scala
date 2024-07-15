package edu.wwu.eas.automation

import scala.collection.mutable

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import org.slf4j.LoggerFactory
import org.slf4j.bridge.SLF4JBridgeHandler
import os.Path
import os.RelPath

import edu.wwu.eas.automation.GitLab.createProject
import edu.wwu.eas.automation.GitLab.isProject
import edu.wwu.eas.automation.GitOps._

/** The location of a git repository.
  *
  * @constructor
  *   create a new [[Repo]] with a git-ssh path to a repository
  * @param name
  *   [[os.RelPath]] to the repo, e.g. `banner/apps/banner_general_ssb_app.git`
  */

case class Repo(name: RelPath):
  def clonePath(using options: Options): Path = options.repoDir / name
  def gitLabPath(using config: Config): RelPath = RelPath(config.gitLabRoot) / nameWithoutExt
  def isRemoteRepoInGitLab(using Config): Boolean = isProject(gitLabPath.toString)
  def nameWithoutExt: RelPath = name / os.up / name.baseName

object Reposync:

  @main def reposyncMain(args: String*) =
    val (options, config) = OptionParser.parse(args*)
    given Options = options
    given Config = config

    val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]
    rootLogger.setLevel(Level.WARN)
    if options.verbose then rootLogger.setLevel(Level.INFO)
    if options.debug then
      rootLogger.setLevel(Level.DEBUG)
      if options.debugGitLabApi then
        SLF4JBridgeHandler.removeHandlersForRootLogger()
        SLF4JBridgeHandler.install()
        java.util.logging.Logger.getLogger("").setLevel(java.util.logging.Level.FINE)

    GitLab.connect(config.gitLabUrl)

    logger.info("Clone remote repos")
    val repos = getGitoliteRepositoryList(config.remoteGitUrl)
      // Filter repositories on our snubby list. These are repos that gitolite
      // says we can access but fail when we clone them
      .filterNot(n =>
        Option
          .unless(options.ignoreSnubbies)(config.snubbies.contains(n.toString))
          .filter(identity)
          .map(_ => logger.info(s"Skipping ${n.toString} in snubbies"))
          .nonEmpty
      )
      .map(n => Repo(n))
      // Make a local clone if one doesn't already exist
      .tapEach(r =>
        os.makeDir.all(r.clonePath / os.up)
        if !os.isDir(r.clonePath) then
          exec(
            cmd = os.proc("git", "clone", "--bare", s"${config.remoteGitUrl}:${r.name}", r.name.toString),
            dir = options.repoDir,
            changes = true,
            continueOnError = true
          )
      )
      // Ignore repos that we couldn't clone
      .filter(r => os.isDir(r.clonePath) && os.size(r.clonePath) > 0)

    if options.listRepositoryAge then
      logger.info("List repository ages")
        repos.foreach(r =>
          val age = mutable.StringBuilder()
          exec(
            cmd = os.proc("git", "log", "-1", "--format=%ct"),
            dir = r.clonePath,
            capture = Some(age),
            quiet = true
          )
          println(s"${age.toString().strip()} ${r.name.toString}")
        )
        System.exit(0)

    logger.info("Insure repos exist in GitLab")
    repos
      .filter(!_.isRemoteRepoInGitLab)
      .foreach(r =>
        logger.info(s"Create GitLab repository: ${r.gitLabPath.toString}")
        if !options.noChange then createProject(r.gitLabPath)
      )

    logger.info("Mirror repos in GitLab")
    repos
      .foreach(r =>
        // Fetch refs from Ellucian repository
        exec(
          cmd = os.proc("git", "fetch", s"${config.remoteGitUrl}:${r.name}", "+refs/*:refs/*"),
          dir = r.clonePath,
          changes = true
        )

        // Look for and fix bogus refs that can cause git push --mirror to fail
        // So far, these are always remote refs
        if options.mirror then
          val refs = mutable.StringBuilder()
          exec(
            cmd = os.proc("git", "for-each-ref", "--format", "%(refname:short)", "refs/remotes"),
            dir = r.clonePath,
            capture = Some(refs),
            quiet = true
          )
          refs
            .toString()
            .linesIterator
            .foreach(ref =>
              logger.warn(s"Found possible bogus ref $ref in ${r.name.toString}")
              if options.force then
                exec(os.proc("git", "branch", "--remote", "-d", ref), dir = r.clonePath, changes = true)
            )

        exec(
          cmd = os.proc(
            "git",
            "push",
            Option.when(options.force)("--force"),
            Option.when(options.mirror)("--mirror"),
            s"${config.localGitUrl}:${r.gitLabPath}.git",
            Option.unless(options.mirror)("+refs/heads/*:refs/heads/*"),
            Option.unless(options.mirror)("+refs/tags/*:refs/tags/*"),
            Option.unless(options.mirror)("+refs/change/*:refs/change/*")
          ),
          dir = r.clonePath,
          changes = true
        )
      )
