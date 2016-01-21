# Introduction

The _reposync_ repository contains scripts to help maintain local clones of the
Ellucian BannerXE Git repositories.

## reposync.groovy

`reposync.groovy` is a Groovy script that mirrors Ellucian BannerXE git
repositories in our local GitLab instance.  It iterates over each
repository in Ellucian's BannerXE Gitolite instance and creates or updates the
corresponding repository in our GitLab instance.

`reposync.groovy` puts repositories in the _Ellucian_ group in GitLab.
It replaces `/` characters in repository names with `-` because
GitLab does not allow `/` characters in repository names.  For example,

```
banner/plugins/spring_security_cas.git
```

becomes

```
Ellucian/banner-plugins-spring_security_cas.git
```


### Environment

The `reposync` user on `tisap` runs `reposync.groovy` in a cron job.

`reposync.groovy` expects the following files to be in the home directory:

`apiKey` -- contains a GitLab user's private token used by the GitLab API to
make changes on behalf of that user.  This file must contain the private token
of a GitLab user that has the ability to create repositories and push to
protected branches in the _Ellucian_ group (master or owner).

`repos` -- a directory where `reposync.groovy` will create bare clones of the
Ellucian repositories as a staging area.  `reposync.groovy` will reuse existing
repositories in this directory if they are present, but will create them if not.

`.ssh/id_rsa`  
`.ssh/id_rsa.pub` -- Private and public ssh key pair used to access Ellucian's
Git repositories.  The public key must be registered with Ellucian and the
private key should not have a passphrase if `reposync.groovy` will be run by
cron.

Groovy 2.4 or later must be available in `$PATH`.

### Usage

To invoke `reposync.groovy`:

```
reposync.groovy [ -n | -v ]
```
Options:

`-n` dry run only, make no changes

`-v` verbose, show git commands and their output

## git-fix-ellucian-submodules

`git-fix-ellucian-submodules` is a shell script that replaces Git submodule URLs
so they point to our GitLab instance.

Each BannerXE application or plugin is in its own repository.  Ellucian uses Git
submodules (see http://git-scm.com/docs/git-submodule) in application
repositories to reference plugins.

The submodule URLs in application `.gitmodules` files point to Ellucian's
development repositories that are not accessible to us, so the URLs must be
changed to point to our repositories.  The `git-fix-ellucian-submodules` script
helps with this.

After cloning an application repository, do the following:

```bash
git submodule init               # 1
git-fix-ellucian-submodules      # 2
git submodule update             # 3
```

1. Initializes submodules referenced by the repository by copying submodule names
from `.gitmodules` into `.git/config`.

2. Changes submodule URLs that reference Ellucian development repositories to
reference the corresponding repository in our GitLab instance.

3. Clones or pulls the plugin submodules into the application.

If Ellucian adds new plugins to an application, after pulling the new version,
do the above commands again.  This will copy URLs for the new plugins into
`.git/config`, fix the URLs, and fetch the plugin repositories.

If you need to reference a locally modded plugin, you can use `git config` to
change the plugin's submodule URL and this change will be preserved in your
repository.

The advantage of this approach is that we don't have to create a new branch and
modify Ellucian's `.gitmodules`.  It also allows us to mod plugins as well and
preserve those changes outside of the actual repository.

The disadvantage is that this must be done whenever an application is cloned or
whenever an application adds plugins.
