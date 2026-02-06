# Contributing to TiVo `androidx/media` Fork 

TL;DR

Our local changes to `androidx/media` are contained in this repository.   The changes and bulid/publish is restricted to the set of modules which we have local changes:

* ***demo*** &mdash; change the gradle script to build with our local version and take the rest from google() maven
* ***lib-exoplayer*** &mdash; multiple changes, see the [README-TIVO.md](libraries/exoplayer/README-TIVO.md).
* ***lib-exoplayer-hls*** &mdash; multiple changes, see the [README-TIVO.md](libraries/exoplayer_hls/README-TIVO.md)
* ***lib-extractor***  &mdash; multiple changes, see the [README-TIVO.md](libraries/extractor/README-TIVO.md)
* ***lib-ui***  &mdash; Hook in `PlayerView` to control show/hide of controls, etc.  See [README-TIVO.md](libraries/ui/README-TIVO.md)
* **test-data**  &mdash; test samples for our unit tests.

Our goal is always to share everything from this repository upstream, keeping track of the open pull requests and cherry-picks in the respective README-TIVO.md files.

## Top Level Changes

We have strictly avoided chaining files outside of modules we have changes to avoid merge conflicts when updating.  The following files are added:

* ***local_settings.gradle*** &mdash; allows us to restrict the set of modules in the full build, and apply our local versioning.

* ***publish-tivo.gradle***  &mdash; our version of `publish.gradle` that will publish to an internal artifactory and apply our versioning

And modfied:

* ***settings.gradle*** &mdash; subset the modules in the project to just our changes.  Basically include the `local_settings.gradle` in precidence over `core-settings.gradle`

## Versioning

Our versions are all based on the upstream base AndroidX Media3 version, in such a way that our local modules are "newer".  The version scheme examples are:

**Published artifacts:** `1.1.1.1-tivo` is the first iteration of TiVo's modified 1.1.1 code.

- **Base version:** `1.1.1` - Upstream AndroidX Media3 version
- **TiVo suffix:** `.1-tivo` - TiVo fork iteration number
- **Dev builds:** `1.1.1-tivo.1-dev-{RUN_NUMBER}` - Development snapshots from CI

Updating the version by simply editing the `local_settings.gradle`

## Building and Publishing

### Maven Local

Maven local build publishes the modules into the user's local filesystem maven,  typically `~/.m2/...`.  The gradle command for this is

```shell
./gradlew --parallel lib-extractor:publishToMavenLocal lib-exoplayer-hls:publishToMavenLocal :lib-exoplayer:publishToMavenLocal
```

### CI Build

T.B.S.

### TiVo Internal Jenkins Build

T.B.S.

## Using The Library

To include library modules simply reference them in your gradle build dependencies.  For example,
this includes the TiVo patch 1 version of the AndroidX 1.1.1 libraries 

```jkgroovy

dependencies {
    implementation 'androidx.annotation:annotation:1.0.2'
    implementation 'androidx.media3:media3-exoplayer-hls:1.1.1.1-tivo-dev-1'
    implementation 'androidx.media3:media3-exoplayer:1.1.1.1-tivo-dev-1'
    implementation 'androidx.media3:media3-test-utils:1.1.1'
    testImplementation 'androidx.media3:media3-common:1.1.1'
    testImplementation 'org.robolectric:robolectric:4.8.1'
    androidTestImplementation 'org.mockito:mockito-core:3.12.4'
}
```

## Contributing

### Developer Setup

##### Clone the Repository

```bash
# Clone from TiVo's public fork
git clone git@github.com:TiVo/androidx-media.git
cd androidx-media
```

##### Setup Git Remotes

Suggested remote names are used in the step by step examples.

```bash
git remote add androidx https://github.com/androidx/media.git
git remote add tivo-public https://github.com/TiVo/androidx-media.git

# Add your personal fork (replace YOUR_GITHUB_USERNAME with your GitHub username)
# Required for creating PRs to AndroidX (they require individual accounts)
git remote add personal https://github.com/YOUR_GITHUB_USERNAME/media.git
```

Pull request should come from individual accounts, see the [androidx/media/CONTRIUBTING.md](https://github.com/androidx/media/blob/release/CONTRIBUTING.md#push-access-to-pr-branches) for why.

## Updating Our Fork

Keeping with our goal, that everything moves upstream, we maintain mirrors of the upstream branches (`main` and `release`) and our own `release-tivo` mainline.  The upstream branches are fast-forward updated via a github action and never modified on our fork.

We keep `release-tivo` up to date based from a tagged version of the upstream `release` branch.   This started with `1.1.1` from our ExoPlayer `2.19.1` codebase (Our ExoPlayer [release](https://github.com/tivocorp/exoplayerprvt/tree/release) branch).

Each update to a new AndroidX Media version will follow the merge and resolve pattern, using a shared git [Record Resolve](https://git-scm.com/docs/git-rerere) cache as some of our changes may never be shared and we wish to avoid repeated identical resovles.  The high level steps are

1. Create a release topic branch, e.g. `release-tivo-1.4.0`
2. Merge with the release tag `git merge 1.4.1`
3. Resolve conflicts (other developers can help by reproducing the merge and sharing the resolve patch)
4. Run all unit test cases, fix breaks
5. Update each README-TIVO.md
6. Commit the merge with notes about conflict resolutions 
7. Update the shared rerere cache

### Update Step by Step Example

#### Step 0 - Clone and Configure Rerere

One time setup to get the rerere cache repo on your developer workstation

```bash
# Clone the rerere cache repository (alongside your main repo)
cd ..
git clone https://github.com/TiVo/androidx-rerere-cache.git
cd androidx-media

# Configure rerere for your local repository
git config rerere.enabled true
git config rerere.autoUpdate true
git config rerere.rereres "$(cd .. && pwd)/androidx-rerere-cache/rr-cache"

# Verify configuration
git config --get-regexp rerere
```

#### Step 1 - Get the latest Rerere Cache

```bash
# Pull latest rerere resolutions from the team
cd ../androidx-rerere-cache
git pull origin main
cd ../androidx-media
```

#### Step 2 - Create the branch and perform the merge

Example assumes we are updating from 1.1.1 to 1.5.1.

```bash
declare -x CURRENT_BASE=1.1.1
declare -x TARGET_BASE=1.5.1

# Create integration branch from current release-tivo
git checkout release-tivo
git checkout -b release-tivo-${TARGET_BASE}-merge

# Push it so team can see/collaborate
git push -u tivo-public release-tivo-${TARGET_BASE}-merge
```

#### Step 3 - Merge Target to Integration Branch

This step performs the initial merge on the merge integration branch.   Note conflicts that were previously resolved and the `preimage` in the Rerere cache matches are automatically resolved and staged.  

```bash
git merge ${TARGET_BASE} -m "Merge upstream ${TARGET_BASE}"
# Git output shows:
# - "Auto-merging <file>" - no conflicts
# - "CONFLICT (content): Merge conflict in <file>" - has conflicts
# - "Recorded preimage for '<file>'" - rerere is recording the conflict
# - Staged '<file>'' using previous resolution.
```

#### Step 4 - Resolve Conflicts

This is a complex set requiring input from original developers and multiple tools.   Some things to look our for are:

1. `build.gradle` and `settings.gradle` &mdash; our changes will almost always conflict with these files.  Look for new dependencies added and project modules (which should be converted to a maven dependency)
1. The Rerere cache simply replays previous resolutions, these may not always be correct or there may have been fixes made after the resolution on a previous merge branch you'll need to pickup.

##### Useful Git Commands

Show changes from upstream for a file.

```bash
git diff ${CURRENT_BASE}:settings.gradle ${TARGET_BASE}:settings.gradle
```

Show our changes for a file:
```bash
git diff ${TARGET_BASE}..HEAD -- settings.gradle
```

#### Step 5 - Build, Test, Fix

Run unit tests and fix any issues.

```bash
./gradlew --no-daemon --parallel \
						:lib-extractor:check \
            :lib-exoplayer:check \
            :lib-exoplayer-hls:check \
            :lib-ui:check 
```

**NOTE** any issues found with the rerere cache resolutions, must be purged and updated.   To do this, while your merge is active do:

```bash
git rerere forget -- <path-with-resolve-issues>
```

The fix the resolution to correct the issue.

Lastly, don't try to add other fixes besides issues cause by merge unit **after** you commit the initial merge.   This allows them to be cherry-picked by later updates.

#### Step 6 - Save the Rerere Cache

Add and commit the new rerere entires.

```bash
cd ../androidx-rerere-cache
git add .
git commit -m "Rerere: resolutions from ${CURRENT_BASE} to ${TARGET_BASE} merge ($(date +%Y-%m-%d))"
git push origin main
cd ../androidx-media
```

#### Step 6 - Commit Your Merge

```bash
git commit -m "Merged AndroidX ${TARGET_BASE}" --edit
```

Edit the commit body and note any resolved conflicts as well as pending issues after the initial merge to address.

