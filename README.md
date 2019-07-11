# ExoPlayer #

## TiVo Notes ##
This is a snapshot of the public ExoPlayer on in TiVo's public github:

  https://github.com/TiVo/ExoPlayer

As a general rule TiVo's aim is to file bugs to ExoPlayer's main Google git (https://github.com/google/ExoPlayer) and submit fixes via pull requests from TiVo's public fork.

The purpose of this repository is the portions of our trickplay implementation which we choose to keep private (at least for now) to allow the posibility of patenting parts of the implementation.

## Using This Repository ##

The code here is based maintained merged with the `dev-v2-xxx` branches (currently r2.10.2).  Changes specific to trickplay are in the `library-trickplay` module.

The default branch is the master for this module: `library-trickplay-master`.   

## Developing ##

#### Project branches ####

* Development work happens on the `library-trickplay-master` branch. Pull requests should normally be made to this branch.

#### Using Android Studio ####

To develop ExoPlayer using Android Studio, simply open the ExoPlayer project in
the root directory of the repository.
