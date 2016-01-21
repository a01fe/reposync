#!/bin/env groovy

@Grab(group='org.gitlab', module='java-gitlab-api', version='1.2.0')
import org.gitlab.api.GitlabAPI

import java.nio.file.Paths

ellucianUrl = "git@banner-src.ellucian.com"
ellucianRemote = "origin"

wwuUrl = "https://git.eas.wwu.edu"
wwuSshUrl = "git@git.eas.wwu.edu"
wwuGroup = "Ellucian"

wwuApiKey = new File("~/apiKey".replaceFirst('^~', System.getProperty('user.home'))).readLines()[0]
repoDir = new File("~/repos".replaceFirst('^~', System.getProperty('user.home')))

GitlabAPI api = GitlabAPI.connect(wwuUrl, wwuApiKey)
wwuGroupId = api.groups.find { group -> group.name == wwuGroup }?.id


def gitoliteRepos(url) {
	def process = "ssh ${url} info".execute()
	process.consumeProcessErrorStream(System.err)
	def repos = process.in.readLines()
	process.waitFor()
	if (process.exitValue() != 0) {
		println "Fetching repo list failed"
		System.exit(1)
	}
	return repos.grep(~/\s[CRW ]+\s*[^*]*/)*.replaceFirst(/\s[CRW ]+\s*([^\s]+)\s*/, '$1').collect { new File(it + ".git") }
}


def run(args) {
	def result = 0

	if (options.n && !args.quiet) {
		println args.cmd
	}
	else {
		if (options.v && !args.quiet) println args.cmd
		def process = args.cmd.execute(null, args.dir)
		(options.v && !args.quiet) ? process.consumeProcessOutput(System.out, System.err) : process.consumeProcessOutput()
		process.waitFor()
		result = process.exitValue()
	}
	if (result != 0) {
		if (options.v) println "command failed: ${args.cmd}"
		if (!args.continueOnError) System.exit(1)
	}
	return result
}


name = this.class.name
cli = new CliBuilder(usage: "${name} [options]",
                         stopAtNonOption: false)
cli.n('no run, display only')
cli.v('verbose')
options = cli.parse(args)

if (options == null || options.arguments().size() != 0 ) {
	cli.usage()
	System.exit(1)
}

gitoliteRepos(ellucianUrl).each { repo ->
	def repoPath = repoDir.toPath().resolve(repo.toPath()).toFile()
	if (!repoPath.exists()) {
		repoPath.getParentFile().mkdirs()
		run(cmd: "git clone --bare ${ellucianUrl}:${repo} ${repo}", dir: repoDir, continueOnError: true)
	}
	// git clone may fail, only process repos that actually exist
	if (repoPath.exists()) {

		// Create WWU repository if necessary
		def wwuRepoName = repo.toString().replace("/", "-").replaceFirst(/\.git$/, "")
		def wwuRepoPath = "$wwuGroup/$wwuRepoName"
		def wwuRepoExists = false
		try {
			api.getProject(wwuRepoPath.toString())
			wwuRepoExists = true
		}
		catch (Exception e) {
		}
		if (! wwuRepoExists) {
			if (options.v) println "Creating wwu repository: $wwuRepoPath"
			if (!options.n) {
				api.createProject(wwuRepoName, wwuGroupId, repo.toString(), false, false, false, false, false, false, null, null)
			}
		}

		// Fetch from Ellucian
		run(cmd: "git fetch ${ellucianUrl}:${repo} +refs/*:refs/*", dir: repoPath)

		// Push to WWU
		run(cmd: "git push ${wwuSshUrl}:${wwuRepoPath}.git +refs/*:refs/*", dir: repoPath)
	}
}
