# Introduction

This repository contains a Groovy application to help maintain local clones of Ellucian's Banner9 Git repositories.

`reposync` is a command that mirrors Ellucian Banner9 git repositories in our local GitLab instance. It iterates over each repository in Ellucian's Banner9 Gitolite instance and creates or updates the corresponding repository in our GitLab instance.

`reposync` puts these repositories in the _eas/ellucian_ group in our GitLab instance. For example, `git@banner-src.ellucian.com:banner/plugins/banner_student_attendance_tracking.git` in Ellucian's repository becomes `git@git.eas.wwu.edu:eas/ellucian/banner/plugins/banner_student_attendance_tracking.git` in our GitLab instance.

# Scheduling

`reposync` is run daily at 4am by a GitLab scheduled CI pipeline. The pipeline has two jobs: the `run` job is configured to be run by the schedule and the `run_force` is configured to be manually run.

The `run` job will fail if Ellucian revises history in their repositories. If this happens, manually run the `run_force` job to force the pushes.

The CI pipeline depends on these secret variables in GitLab CI settings:

1.  A GitLab user's private token used by `reposync` to make changes on behalf of that user.  The `GITLAB_API_KEY` CI variable must specify the private token of a GitLab user that has the ability to create repositories and push to protected branches in the _Ellucian_ group (master or owner).

1.  An SSH key pair registered with Ellucian to access the Ellucian Gitolite instance. The `SSH_PRIVATE_KEY` CI variable must specify the private ssh key and it must not have a passphrase.

1.  SSH host keys for local and Ellucian git remotes in the `SSH_SERVER_HOSTKEYS` CI variable.

# Building

`reposync` is built as a Gradle groovy application. To build a distribution, run:

```
./gradlew distTar
```

# Usage

To invoke `reposync`:

```a 
reposync [ options ]
```
`reposync` options are:

| Option | Required | Description |
| --- | --- | --- |
| `-a` _KEY_ | yes | GitLab API key |
| `-d` | no | enable debugging output |
| `-f` | no | git push --force to local repos |
| `-h` | no | help (usage message) |
| `-n` | no | dry run only, make no changes |
| `-r` _DIR_ | yes | Repository cache directory |
| `-v` | no | verbose, show git commands and their output |

# Using Ellucian repositories at WWU

This section contains notes about issues with using Ellucian repositories at WWU.

## git-fix-ellucian-submodules

`git-fix-ellucian-submodules` is a shell script that replaces Git submodule URLs so they point to our GitLab instance.

Each Banner9 application or plugin is in its own repository. Ellucian uses Git submodules (see http://git-scm.com/docs/git-submodule) in application repositories to reference plugins.

The submodule URLs in application `.gitmodules` files point to Ellucian's development repositories that are not accessible to us, so the URLs must be changed to point to our repositories.  The `git-fix-ellucian-submodules` script helps with this.

After cloning an application repository, do the following:

```bash
git submodule init               # 1
git-fix-ellucian-submodules      # 2
git submodule update             # 3
```

1. Initializes submodules referenced by the repository by copying submodule names from `.gitmodules` into `.git/config`.

2. Changes submodule URLs that reference Ellucian development repositories to reference the corresponding repository in our GitLab instance.

3. Clones or pulls the plugin submodules into the application.

If Ellucian adds new plugins to an application, after pulling the new version, do the above commands again.  This will copy URLs for the new plugins into `.git/config`, fix the URLs, and fetch the plugin repositories.

If you need to reference a locally modded plugin, you can use `git config` to change the plugin's submodule URL and this change will be preserved in your repository.

The advantage of this approach is that we don't have to create a new branch and modify Ellucian's `.gitmodules`.  It also allows us to mod plugins as well and preserve those changes outside of the actual repository.

The disadvantage is that this must be done whenever an application is cloned or whenever an application adds plugins.
