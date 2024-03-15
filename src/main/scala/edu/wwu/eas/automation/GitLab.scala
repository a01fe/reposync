package edu.wwu.eas.automation

import scala.jdk.CollectionConverters.*
import scala.util.Failure
import scala.util.Try

import org.gitlab4j.api.GitLabApi
import org.gitlab4j.api.RepositoryApi
import org.gitlab4j.api.models.AccessLevel
import org.gitlab4j.api.models.Project
import org.gitlab4j.api.models.Visibility
import org.slf4j.LoggerFactory
import os.RelPath

object GitLab:
  var api: Option[GitLabApi] = None
  val logger = LoggerFactory.getLogger("edu.wwu.eas.automation.GitLab")

  def connect(url: String)(using options: Options) =
    api = Some(new GitLabApi(url, options.token))
    logger.debug(s"GitLab.connect($url)")
    if options.debugGitLabApi then
      logger.debug("enabling GitLab API request and response logging")
      GitLab.enableDebugging()

  def createBranch(path: String, name: String, ref: String): Unit =
    api.get.getRepositoryApi().createBranch(path, name, ref)

  def deleteBranch(path: String, name: String)(using options: Options): Unit =
    if options.noChange || options.verbose then logger.info(s"Delete branch $name in project $path")
    if !options.noChange then api.get.getRepositoryApi().deleteBranch(path, name)

  def enableDebugging(): Unit =
    api.get.enableRequestResponseLogging(java.util.logging.Level.FINE, 4096)

  def isBranch(path: String, name: String): Boolean =
    Try(api.get.getRepositoryApi().getBranch(path, name) != null)
      .getOrElse(false)

  def isProject(path: String): Boolean =
    Try(api.get.getProjectApi().getProject(path) != null)
      .recover({ e =>
        logger.debug(s"isProject($path) failed", e); throw e
      })
      .getOrElse(false)

  def protectBranch(path: String, name: String, pushAccessLevel: String, mergeAccessLevel: String)(using
    options: Options
  ): Unit =
    if options.noChange || options.verbose then
      logger.info(s"Protect branch $name in project $path to push:$pushAccessLevel merge:$mergeAccessLevel")
    if !options.noChange then
      Try(
        api.get
          .getProtectedBranchesApi()
          .protectBranch(path, name, AccessLevel.valueOf(pushAccessLevel), AccessLevel.valueOf(mergeAccessLevel))
      )
        .getOrElse(logger.warn(s"Protect branch $name failed, assuming it was already protected"))

  def setDefaultBranch(path: String, name: String)(using options: Options): Unit =
    if (options.noChange || options.verbose) then logger.info(s"Set default branch to $name in project $path")
    if !options.noChange then
      val project = api.get.getProjectApi().getProject(path)
      project.setDefaultBranch(name)
      api.get.getProjectApi().updateProject(project)

  def unprotectBranch(path: String, name: String)(using options: Options): Unit =
    if options.noChange || options.verbose then logger.info(s"Unprotect branch $name in project $path")
    if !options.noChange then
      Try(api.get.getProtectedBranchesApi().unprotectBranch(path, name))
        .getOrElse(logger.warn(s"Unprotect branch $name failed, assuming it was not protected"))

  def createProject(name: RelPath)(using options: Options, config: Config): Unit =
    if options.verbose || options.noChange then logger.info(s"Creating $name")
    if !options.noChange then
      Try {
        val group = api.get
          .getGroupApi()
          .getGroups((name / os.up).toString)
          .asScala
          .find(_.getFullPath() == (name / os.up).toString)
          .get
        val project = (new Project())
          .withName(name.last)
          .withNamespaceId(group.getId())
          .withVisibility(Visibility.PRIVATE)
        api.get.getProjectApi().createProject(project)
      }
        .recover(e => ReposyncException(s"createProject: cannot create project $name: ${e.getMessage()}", e))
