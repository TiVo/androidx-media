# How to contribute at TiVo #
## Background ##
The private github repository for ExoPlayer is internal to TiVo, here we stage possible contributions to the ExoPlayer Open Source project on [ExoPlayer GitHub](https://github.com/google/ExoPlayer).  

All contributions to ExoPlayer must go to lengths to share as much as is legally and businesswise possible with the open source community. It is not our goal to maintain a deviant fork of ExoPlayer.

## Initial Setup ##
Start by cloning this repository, then setup remotes for the GitHub repositories we track for ExoPlayer development.  From the command-line.

```
mkdir ExoPlayer
cd ExoPlayer
git clone -o tivo-pvt https://github.com/TiVo/ExoPlayer .
```
At this point you will have one *origin* remmote that is named `tivo-pvt` as upstream to this repository.

If you are working on code that will ultimately be shared with the ExoPlayer opensource (the most likely case), it is helpful to setup additional remotes to the two public GitHub repositories:

1. TiVo Corporate ExoPlayer Fork - (https://github.com/TiVo/ExoPlayer)
2. ExoPlayer Google Upstream - (https://github.com/google/ExoPlayer)

To add both of these, use these commands:

```
git remote add tivo-public https://github.com/TiVo/ExoPlayer
git remote add upstream https://github.com/TiVo/ExoPlayer
```

## Branches And Structure ##
The two principle branches in this private ExoPlayer repository are simply 

1. `release`
2. `dev`

### `release ` Branch ###
`release` is the default branch, TiVo's Jenkins build for ExoPlayer will build and publish versioned artifacts from this branch.

At any time this branch will be:

1. Based from a tagged ExoPlayer release (current is r2.9.6)
2. Include two sets of changes from the tagged release:
    - changes we are/have summited as pull request upstream
    - local proprietary changes

### `dev` Branch ###
The `dev` branch tracks the latest stable branch of the ExoPlayer V2 development branch [ExoPlayer dev-v2](https://github.com/google/ExoPlayer/tree/dev-v2).  Specifically we will periodically test release candidate branches from dev-v2 (these follow the pattern, dev-v2-rx.y.z, eg: [dev-v2-r2.10.3](https://github.com/google/ExoPlayer/tree/dev-v2-r2.10.3)) and merge these into `development`.  Our goal is not to match their development (we are *not* doing ExoPlayer development), but simply to pick up bug fixes earlier then their release branch.

This is the branch ExoPlayer requires all pull requests must merge into conflict free, (see the ExoPlayer [CONTRIBUTING doc](https://github.com/google/ExoPlayer/blob/dev-v2/CONTRIBUTING.md))

### Topic Branches ###
Most developers will not push to the `dev` or `release` branches directly, you must create and push a *topic* branch with the changes desired and contact one of the ExoPlayer project leaders to review and merge the changes into the `dev` mainline branch.

Feel free to branch, checkin and push to topic branches and branches based from them often, git encourages collaboration in this way.

Branches must be named `t-xxx` for topic branches, where `xxx` is a *descriptive* name for exactly what you are working on, this convention allows us to find and clean these up as needed.

## Workflows ##
### Working on a Feature/Bug
This is the branch to checkout and branch your topic branch from.  The basic steps to make your local git up to date with `tivo-pvt` are (on a clean working tree, use `git status`)

```
git pull tivo-pvt dev
git checkout -b t-my-project
```

Branches should be named `t-xxx` for topic branches, where `xxx` is a *descriptive* name for exactly what you are working on.
git 

