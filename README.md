# Introduction

The _reposync_ repository contains a Groovy application to help maintain local clones of the Ellucian BannerXE Git repositories.

`reposync` is a command that mirrors Ellucian BannerXE git repositories in our local GitLab instance. It iterates over each repository in Ellucian's BannerXE Gitolite instance and creates or updates the corresponding repository in our GitLab instance.

`reposync` puts repositories in the _Ellucian_ group in GitLab. It replaces `/` characters in repository names with `-` because GitLab does not allow `/` characters in repository names.  For example,

```
banner/plugins/spring_security_cas.git
```

becomes

```
Ellucian/banner-plugins-spring_security_cas.git
```

### Environment

The `reposync` user on `tisap` runs `reposync` in a cron job.

`reposync` requires the following:

1.  A GitLab user's private token used `reposync` to make changes on behalf of that user.  The `-a` option must specify the private token of a GitLab user that has the ability to create repositories and push to protected branches in the _Ellucian_ group (master or owner).

1.  A directory where `reposync` will create bare clones of the Ellucian repositories as a staging area. `reposync` will reuse existing repositories in this directory if they are present, but will create them if not. The `-r` option must specify the path to this directory.

1.  `~/.ssh/id_rsa` and `~/.ssh/id_rsa.pub` must contain the private and public ssh key pair used to access Ellucian's Git repositories.  The public key must be registered with Ellucian and the private key should not have a passphrase if `reposync` will be run by
cron.

### Usage

To invoke `reposync.groovy`:

```a 
reposync [ options ]
```
Options:

| Option | Required | Description |
| --- | --- | --- |
| `-a` _KEY_ | yes | GitLab API key |
| `-d` | no | enable debugging output |
| `-h` | no | help (usage message) |
| `-n` | no | dry run only, make no changes |
| `-r` _DIR_ | yes | Repository cache directory |
| `-v` | no | verbose, show git commands and their output |

## git-fix-ellucian-submodules

`git-fix-ellucian-submodules` is a shell script that replaces Git submodule URLs so they point to our GitLab instance.

Each BannerXE application or plugin is in its own repository.  Ellucian uses Git submodules (see http://git-scm.com/docs/git-submodule) in application repositories to reference plugins.

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
