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

### Published Artifacts ###
The Gradle publish tasks publish the ExoPlayer libraries as Android libraries (aar files), along with source.  These are published to the local [TiVo artifactory](http://repo-vip.tivo.com:8081/artifactory/libs-release-local/).  The workflows section, [Publishing New Versions](#publishing-versions)

The libraries are described in the [README.md](https://github.com/tivocorp/exoplayerprvt/blob/release/README.md).  The TiVo internal library, [library-tivo-ui](https://github.com/tivocorp/exoplayerprvt/tree/release/library/tivo-ui) has an example of how to use ExoPlayer in TiVo and the libraries to include in your project, as well as TiVO specific libraries.

Code is built by Jenkins, [https://builds.tivo.com/view/Android/job/b-exoplayerprvt-release/](https://builds.tivo.com/view/Android/job/b-exoplayerprvt-release/)

TBS - versioning and publishing apk for demo-tenfoot?

### Version Numbering Scheme ###

The ExoPlayer libraries are integrated into other products as binary, so it is imperative that all releases are versioned.  TiVo will take a released version of ExoPlayer and include our proprietary changes along with any cherry-picked changes that are not yet released by Google.  Any version identification scheme must:

1. Identify the released ExoPlayer version that is base
1. Mark any TiVo additions as a patch version

ExoPlayer uses `major.minor.micro`, [ExoPlayer Releases](https://github.com/google/ExoPlayer/releases)
TiVo versions will add a `-` patch number and/or name to this. 

Some valid examples are:

* 2.11.4-1.0 &mdash; presumably first "released" version of TiVo's variant of ExoPlayer 2.11.4
* 2.11.4-hydra-1.6 &mdash; one example of how we might branch with a patch set specific to Hydra

In all cases, if the versions have a time ordering it should match up to the logic for [Gradle Versioning](https://docs.gradle.org/current/userguide/single_versions.html).  In the two examples above, the `-1.0` version is considered higher so clients would not pickup the hydra patch unless explicitly requested

It is assumed that most (if not all TiVo products will request explicit versions from the artifactory, e.g.:

```groovy
dependencies {
    implementation('library-tivo-ui:2.11.4-1.0')
}
```

Library APIs locked down to the major release.  So it is safe to wild card the minor release number, e.g.

```groovy
dependencies {
    implementation('library-tivo-ui:2.11.4-1.+')
}
```

## Workflows ##
### Publishing New Versions ###

TBS - Steve

### Git Best Practices ###

TBS - Steve

### Working With Pull Requests ###
Pull requests are the mechanism for sharing your changes with the community and soliciting a review.  A pull request is like a code-collab accept:

1. A pull request is backed by a branch, so any one can check out the branch make suggested changes and commit.
1. Unlike a code-collab, the code is already committed to version control, so the merge state (conflicts) is always visible

In pull request any team member can add a general comment and or file level comments. General comments are usually questions, clarifications or top level discussions. 
The pull requester can select one or more member of the team as reviewer/s. In Git all are optional reviewers. To merge the pull request at least one reviewer should approve the pull request.

### Navigating to pull request ###
In Github, got to the repository and find the `Pull Request` tab. All the open pull requests are listed there. Then click on the pull request to be reviewed.
Pull request page has below tabs -
* `Conversation`
*	`Commits`
*	`Checks`
*	`Files Changed`

### Making comments ###
To add general comment, go to `Conversation` tab. Write the comment in the comment editor box under `Write` tab. The comment can be directed to a member using "@<user>".  Then press `Comment` button for the comment to be visible to author and others.

To comment on a line, go to `Files Changed` tab. Hover over the line and click on highlighted Blue “+” icon. To add a comment on multiple lines click and drag to select multiple lines and then click the Blue “+” icon. This brings up the comment editor, add the comments and then `Start the review`. If the review is already started click on `Add single comment`.  
Reviewer can submit the review by choosing either one of radio button `Comment`, `Approve` or `Request changes`. This can be done form - 
1. `Files changed` tab and clicking `Review changes` drop down button.
2. `Conversation` tab and clicking `Submit Review` button
Any member of the team can replay to the comments by typing the comment in `Replay…` comment box. Use "@<user>” to direct the replay.
 
### Resolve conversation ###
Anybody can replay to the comment or replay to others comments. But only the member who started the pull request can resolve the conversation. “Resolve conversation” button is visible only to the person created the pull request. After addressing the reviewers comment or suggestion the conversation can be resolved by clicking 'Resolve conversation' button.

### Request re-review ###
If reviewer requested for changes by 'Request changes', author has to request for the re-review. The review status remains 'Changes Requested' with red circle in the pull request’s 'Conversation' section. Author of the pull request has to scroll down to each change request and request for re-review. This can be done by clicking '…' at the end of the '<user> requested changes' line. The 're-review' option is visible only to the member who created the pull request. The status of the reviewer after this will change to “Awaiting requested review from <user>”.  

Link out to sections in [GitHub help](https://help.github.com/en/github/collaborating-with-issues-and-pull-requests/about-pull-requests)



### Making Public Changes ###
Always the primary goal is to get 

This is the branch to checkout and branch your topic branch from.  The basic steps to make your local git up to date with `tivo-pvt` are (on a clean working tree, use `git status`)

```
git pull tivo-pvt dev
git checkout -b t-my-project
```

Branches should be named `t-xxx` for topic branches, where `xxx` is a *descriptive* name for exactly what you are working on.


