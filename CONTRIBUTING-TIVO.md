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

If you are working on code that will ultimately be shared with the ExoPlayer opensource (the most likely case), it is helpful to setup additional remotes to the two public GitHub repositories we will work with

1. [TiVo Corporate ExoPlayer Fork](https://github.com/TiVo/ExoPlayer) &mdash; The *tivo-public* repository, used to publish branches for pull requests.  Request push access from Alon Rohter
2. [Google's ExoPlayer GitHub](https://github.com/google/ExoPlayer) &mdash; *upstream*, the GitHub Google publishes ExoPlayer releases too, our ultimate goal for code changes is to live here.

To add both of these, use these commands:

```
git remote add tivo-public https://github.com/TiVo/ExoPlayer
git remote add upstream https://github.com/TiVo/ExoPlayer
```

## Making Changes ##
ExoPlayer is not a TiVo product, this is the mind set we want, the vast majority of changes will come from Google.  Our goal is to push all code changes we make (that are not proprietary) to *upstream*

### Public Changes ###
To share code back with Google, use the workflow in [Making Public Changes](#making-public-changes).  These changes fall into the following categories:
 
* _Bug Fixes_ &mdash; The vast majority of what we will do.  These should first be documented and reproducable.  Follow Google's template, [bug.md](https://github.com/google/ExoPlayer/tree/release-v2/.github/ISSUE_TEMPLATE/bug.md).  See steps below in [Fixing Bugs](#fixing-bugs) for details.
* _Extension Hooks_ &mdash; these are changes to the core of ExoPlayer that allow us to extend it or to add changes that may or may not be of interest to the community (proprietary DRM like VCAS).  An example is this change to enable VCAS (c4bb819192271a3650f6764e2c485738ce05081a).  These are a long term liability if we cannot get the code to *upstream* so we should keep these to a minimum.
* _Non-Proprietary Enhancements_ &mdash; new features for ExoPlayer, for example HLS I-Frame only playlist parsing. Here we follow Google's template, [feature_request.md](https://github.com/google/ExoPlayer/tree/release-v2/.github/ISSUE_TEMPLATE/feature_request.md).  For these it is important to document the design copiously and include Google early and often in the process.  As an example, look at the enhancement for i-Frame only playlist, [Issue 474](https://github.com/google/ExoPlayer/issues/474) 

### Private Changes ###
* _TiVo Proprietary Changes_ &mdash; TiVo's own proprietary libraries and extensions.  These should be in the list in the [TiVo Proprietary Libraries](#tivo-proprietary-libraries) section below


## Branches ##

### `release` Branch ###
The `release` branch is the default branch in this private ExoPlayer repository.  `release` is the common trunk branch for all our shipping ExoPlayer code.  Some consumers of the ExoPlayer libraries will keep [Version Branches](#version-branches) as appropriate to cherry-pick a subset of the released code tree for their own specific products. 

This branch is protected from push, to incorporate your code here you need to open a pull request.  TiVo's Jenkins build for ExoPlayer will build and publish versioned artifacts from this branch.

At any time this branch will be based on a tagged ExoPlayer release from their [release-v2](https://github.com/google/ExoPlayer/tree/release-v2) branch (e.g. r2.9.6) plus:

1. Changes for features or bug fixes we are/have summited as pull requests to Google
2. Cherry-picked bug fixes from Google's dev-v2 branch that we need before they release them
3. local proprietary changes, in separate libraries or extensions, see  [TiVo Proprietary Libraries](#tivo-proprietary-libraries) section.

### Version Branches ###

TBS how to manage, for example Hydra releases where we cherry-pick changes

### Topic Branches ###
Developers cannot not push directly to `release-*` branches, you must create and push a *topic* branch with the changes then issue a pull request to merge to a `release-*` branch.  

Issue this pull request early in the process and solicit feedback from ExoPlayer project leaders to review the approach.  A pull request is more like a request to collaborate rather then a code-collab like review.  Any developer can checkout the branch from your pull request and add commits to it.

Branches must be named `t-xxx` for topic branches, where `xxx` is a *descriptive* name for exactly what you are working on, this convention allows us to find and clean these up as needed.  

If your topic branch is for a *public change* (most likely case) follow the workflow for [Making Public Changes](#making-public-changes)

## Versioning ##
Code is built by jenkins, https://builds.tivo.com/view/Android/job/b-exoplayerprvt-release/
The  published to an artifactory, https://builds.tivo.com/view/Android/job/b-exoplayerprvt-release/ via the [publish.gradle]()

## Workflows ##
### Git Best Practices ###

TBS - Steve

### Working With Pull Requests ###
Pull requests are the mechanism for sharing your changes with the community and soliciting a review.  A pull request is like a code-collab accept:

1. A pull request is backed by a branch, so any one can check out the branch make suggested changes and commit.
1. Unlike a code-collab, the code is already committed to version control, so the merge state (conflicts) is always visible

TBS - Sada
  
* -- Making comments
*  -- resolve converstion
*  -- request re-review

Link out to sections in [GitHub help](https://help.github.com/en/github/collaborating-with-issues-and-pull-requests/about-pull-requests)



### Making Public Changes ###
Always the primary goal is to get 

This is the branch to checkout and branch your topic branch from.  The basic steps to make your local git up to date with `tivo-pvt` are (on a clean working tree, use `git status`)

```
git pull tivo-pvt dev
git checkout -b t-my-project
```

Branches should be named `t-xxx` for topic branches, where `xxx` is a *descriptive* name for exactly what you are working on.


