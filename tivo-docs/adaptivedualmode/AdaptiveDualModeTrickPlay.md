### Overview 

In this next phase of implementing Dual Mode Trick-Play we introduce the concept of adaptive trick-play TrackSelection.

The general idea is allow the `IFrameAwareAdaptiveTrackSelector` to manage *all* of the available tracks as part of one adaptive set and gently transiton from regular to ROLE_TRICK_PLAY track `Format`

This requires more carefully sequencing the player through phases in Inter-TrickMode transitions:

* Any TrickMode to Normal (Exit TrickPlay)
* Normal to Any TrickMode (Enter TrickPlay)

Note there are no client (TrickPlayControl) visible changes other then the timing of the `TrickPlayEventListner.trickPlayModeChanged()`this callback will not be called until the player transitions required for any Inter-TrickMode change are completed.   The `TrickPlayControl.getCurrentMode()` and related calls will continue to return the most recently set `TrickMode`.  

A new API method `TrickPlayControl.isTrickModeChanging()` can be used now to determine the last `TrickPlayControl.setTrickMode()`is still transitioning.   Clients can (should) use this to avoid attempting to alter track selection or playback parameter (pause / play) while the transition is in progress.

To accomplish this additional internal transition states, not exposed to the  `TrickPlayControl.getCurrentMode()`, are added to manage the transitions.

Here is a state digram

````mermaid
stateDiagram
		[*] --> NORMAL
		NORMAL --> ENTER_TRICKPLAY:setCurrentMode(X), target=X
		ENTER_TRICKPLAY --> TargetMode:player transitions complete
		ENTER_TRICKPLAY --> ENTER_TRICKPLAY:setCurrentMode(Y), target=Y
		TargetMode --> EXIT_TRICKPLAY:setCurrentMode(NORMAL)
		TargetMode --> EXIT_TRICKPLAY:position boundery
		TargetMode --> TargetMode:setCurrentMode(new Z), update target
		EXIT_TRICKPLAY --> NORMAL: player cleanup complete
````

The possible TargetMode values include these TrickModes: 

````java
    enum TrickMode {
        FF1, FF2, FF3, SCRUB, FR1, FR2, FR3
    }
````





# Transition State Management

Manages all of the interactions with `ExoPlayer` required to transition into and out of trickplay mode.  

`TrickMode` transitions fall into two categories:

* *Intra-TrickMode* &mdash;  transition that require no at most one `ExoPlayer` API call to affect the transiton, so effectively synchronus.
* *Inter-TrickMode* &mdash;  reqquires multiple `ExoPlayer` API call to affect the transiton

The new `TransitionStateHelper` manages these *Inter-TrickMode* transition.   The call `TransitionManager.enterTrickMode(TrickMode target)` starts (and possible aborts any in-progress) the transition.

 

## Enter TrickPlay

Entry into trick-play begins playback using the existing video buffer where possible, during this time any buffer replinishment will use an appropriate iFrame only track. 

The enter steps are preformed in this order:

1. **TRACK_SELECT** &mdash; disable audio and CC (`TrackSelector.setParameters()`)
2. **SET_SPEED** &mdash; set playWhenReady, set playback speed fast forward (`setPlaybackParameters()`)

For **SET_SPEED** the speed settings and playWhenReady settings depend on the target trick-play mode.


## Exit TrickPlay
Forward exit requires flushing any buffered iFrame only (ROLE_TRICK_PLAY samples) and reseting the playback parameters (speed and saved playWhenReady).

The exit steps are preformed in this order:

1. **IFRAME_FLUSH** &mdash; flush any queued iFrames (`IFrameAwareAdaptiveTrackSelector`)
2. **SET_SPEED** &mdash; restore playWhenReady, set playback speed normal (`setPlaybackParameters()`)
1. **TRACK_SELECT** &mdash; restore audio and CC (`TrackSelector.setParameters()`)

## Example Sequences

The flowing set of sequence diagrams outlines the high level flow.  Application thread methods are surrounded by a <span style="color:#bfdfff">blue rectangle</span> and ExoPlayer Internal Player Thread methods are in a  <span style="color:#ffe6c8; font-weight:bold">brownish rectangle</span>.  Each rectangle represents a single `Hanlder`message event for the thread.  

This first example seqeunce is when the `TrickPlayControl` *Client* calls to switch to NORMAL mode from any trickplay mode.




````mermaid
    sequenceDiagram
    		participant Client
        participant TrickPlayController
        participant TransitionHelper
        participant EPII
        participant ExoPlayer
        rect rgb(191, 223, 255)
            Client->>+TrickPlayController:setTrickMode()
            TrickPlayController->>+TrickPlayController:switchTrickModeToNormal(true)
            Note right of TrickPlayController: find current transitionHelper and trigger the exit

            TrickPlayController->>+TransitionHelper:exitTrickPlay()
						deactivate TransitionHelper
            TrickPlayController-->>-Client:return
        end
        rect rgb(255, 230, 200)
            EPII->>+EPII:maybeUpdateLoadingPeriod()
            Note left of TransitionHelper:holding in state EXIT_STARTED
            EPII->>+IAATrackSelection:evaluateQueueSize()
            IAATrackSelection->>+TrickPlayController:onQueueSizeUpdated()
            TrickPlayController->>+TransitionHelper:updateState(IFRAME_FLUSHED)
            TransitionHelper->>+TransitionHelper:sendMessage(STATE_CHANGE)
            deactivate TransitionHelper
            deactivate TransitionHelper                		
            deactivate TrickPlayController
            IAATrackSelection-->>-EPII:return
            deactivate EPII
        end 
        rect rgb(191, 223, 255)
        		TransitionHelper->+TransitionHelper:handleMessage()
            Note left of TransitionHelper:with IFrames flushed, now update speed and track selection
            TransitionHelper->>+ExoPlayer:setPlaybackParameters()
						TransitionHelper->>TrickPlayController:restoreSavedTrackSelection()
            deactivate TransitionHelper                		
        end
        rect rgb(255, 230, 200)
            EPII->>+EPII:handlePlaybackParameters()
            EPII->>+IAATrackSelection:onPlaybackSpeed()
            IAATrackSelection->>+TrickPlayController:onParametersSet()
            TrickPlayController->>+TransitionHelper:updateState(SPEED_SET)
            deactivate TransitionHelper
            deactivate TrickPlayController
            IAATrackSelection-->>-EPII:return
            deactivate EPII
        end
        rect rgb(191, 223, 255)
        	ExoPlayer->>+TransitionHelper:onTracksChanged()
        	TransitionHelper->>+TrickPlayController:dispatchTrickModeChanged()
        	TrickPlayController->>+Client:trickPlayModeChanged()
        	deactivate TrickPlayController
        	deactivate TransitionHelper
        end
````


# Enter/Exit Step Internals

The following flows document the ExoPlayer API's and extended classes that are used to implement each of the steps for trick-play enter and exit.

## Set Playback Parameters

This sequence is used both entering (set to 15/30/60x) and exiting (set to 1x) forward trickplay.  The client (caller of `TrickPlayControl` interface methods) intiates the operation by calling `setTrickMode()`. Application thread methods are surrounded by a <span style="color:#bfdfff">blue rectangle</span> and ExoPlayer Internal Player Thread methods are in a  <span style="color:#ffe6c8; font-weight:bold">brownish rectangle</span>.

In sequence calls 1-7 the API calls to `ExoPlayer` send messages to the `ExoPlayerImplInternal` class to set playWhenReady state and change the current `PlaybackParameters`.  The second call triggers a message which calls back in the Player Thread (step 8)

The actual state change is driven by the TiVo extension of `AdaptiveTrackSelection`, the `IFrameAwareAdaptiveTrackSelection` class, which `ExoPlayerImplInternal` calls in the Player Thread in step 9

````mermaid
    sequenceDiagram
    		participant Client
        participant TrickPlayController
        participant ExoPlayer
        participant EPII
        participant IAATrackSelection
        rect rgb(191, 223, 255)
            Client->>+TrickPlayController:setTrickMode()
            TrickPlayController->>+ExoPlayer:setPlayWhenReady(true)
            ExoPlayer->>EPII:sendMessage()
            deactivate ExoPlayer
            TrickPlayController->>+ExoPlayer:setPlaybackParameters()
            ExoPlayer->>+EPII:setPlaybackParameters()
            EPII->>+EPII:sendMessage()
            deactivate ExoPlayer
            deactivate EPII
            deactivate EPII
            TrickPlayController-->>-Client:return
        end
        rect rgb(255, 230, 200)
            EPII->>+EPII:handlePlaybackParameters()
            EPII->>+IAATrackSelection:onPlaybackSpeed()
            IAATrackSelection->>+TrickPlayController:onParametersSet()
            TrickPlayController->>+TrickPlayController:sendMessage(SPEED_SET)
            deactivate TrickPlayController
            deactivate TrickPlayController
            IAATrackSelection-->>-EPII:return
            EPII->>+ExoPlayer:onPlaybackInfoChanged()
            deactivate EPII
        end
````

## iFrame Flush

This sequence is performed first on exit from forward trick-play.  It is gated by the internal state `IFRAME_FLUSH_STATE` set from user or internal action to exit trickplay.

The actual flush is performed when ExoPlayer Player by the TiVo extension of `AdaptiveTrackSelection`, the `IFrameAwareAdaptiveTrackSelection` class


````mermaid
  sequenceDiagram
        autonumber
        participant TrickPlayController
        participant EPII
        participant IAATrackSelection
        rect rgb(191, 223, 255)
            TrickPlayController->>+TrickPlayController:stopForwardTrickPlay()
            TrickPlayController->>+TrickPlayController:setState(IFRAME_FLUSH)
            deactivate TrickPlayController
            deactivate TrickPlayController
        end
        rect rgb(255, 230, 200)
            EPII->>+EPII:maybeUpdateLoadingPeriod()
            loop each renderer
                EPII->>+IAATrackSelection:evaluateQueueSize()
                note right of IAATrackSelection:discards loaded iframe samples
                IAATrackSelection->>+TrickPlayController:onSamplesDiscarded(renderer)
                alt video renderer flushed
                TrickPlayController->>+TrickPlayController:stateUpdated)
                end
                deactivate TrickPlayController
                deactivate TrickPlayController
                IAATrackSelection-->>-EPII:return
            end 
            deactivate EPII
        end    
````



## Track Select

This sequence is first for a transition into forward playback and last when exiting forward trickplay to normal playback.

When entering trickplay it disables audio and exiting it re-enables audio.   It is important on exit that the iFrame Flush process sequence is completed first, otherwise the way ExoPlayer `TrackSelection` works will make it impossible to find and flush the iFrame samples.

In step 2 of the sequence set the state, either `SELECT_FOR_ENTER` or `SELECT_FOR_EXIT`apropraite for the expect final track state so when `onTracksChanged()` is called back it can transition correctly to the next state for trickplay enter or exit.



````mermaid
  sequenceDiagram
        autonumber
        participant TrickPlayController
        participant ExoPlayer
        participant EPII
        participant TrackSelector
        participant IAATrackSelection
        rect rgb(191, 223, 255)
          TrickPlayController->>+TrickPlayController:setTrackSelectionForTrickPlay()
          TrickPlayController->>+TrickPlayController:setState(...)
          deactivate TrickPlayController
          TrickPlayController->>+TrackSelector:setParameters()
          TrackSelector->>+EPII:onTrackSelectionsInvalidated() 
          deactivate TrackSelector
          deactivate TrickPlayController
        end

        rect rgb(255, 230, 200)
          EPII->>+EPII:reselectTracksInternal()
          EPII->>+IAATrackSelection:<<init>>
          IAATrackSelection-->+IAATrackSelection:<<super>>
          deactivate IAATrackSelection
          deactivate EPII
          EPII->>+EPII:updatePlaybackInfo()
          EPII->>ExoPlayer:post(handlePlaybackInfo)
          deactivate EPII
          EPII->>+EPII:maybeNotifyPlaybackInfoChanged()
          deactivate EPII
        end   
        rect rgb(191, 223, 255)
        	ExoPlayer->>+ExoPlayer:handlePlaybackInfo()
        	ExoPlayer->>+TrickPlayController:onTracksChanged()
        	deactivate ExoPlayer
        	deactivate TrickPlayController
        end
````

