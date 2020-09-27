#!groovy

svcId = 'exoplayerprvt'


pipeline { 
  agent none

  options {
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(daysToKeepStr: '15', numToKeepStr: '10'))
  }

  stages {
    stage("Build") {
      agent { label 'linux-android' }

      steps { 
        sh '''
        #	Bootstrap
        p4 print -q //d-alviso/buildism/cpiogz/tools/ism/scripts/bootstrap_android_sdk.sh#1 > bootstrap_android_sdk.sh
        chmod +x bootstrap_android_sdk.sh

        if [ -n "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
	        export ANDROID_SDK_ROOT=$ANDROID_HOME
        fi

        . ./bootstrap_android_sdk.sh
        printenv

        $ANDROID_SDK_ROOT/tools/bin/sdkmanager --list | sed -e '/Available Packages/q'
        pwd

        ./gradlew clean
        ./gradlew demo-tenfoot:build library-core:build library-dash:build library-hls:build library-tivo-ui:build library-trickplay:build library-ui:build

        if [[ $BRANCH_NAME =~ streamer-*  ||  $BRANCH_NAME =~ release-* ]] ; then
          echo "Publishing Build - $BRANCH_NAME"
          ./gradlew publish -PREPO_USER_NAME=build -PREPO_PASSWORD=buildcode
        fi
        '''
      }
    }
  }

  post { 
    success {
      print( "Yay SUCCESS ..." )
      script { 
	      def msg = "${currentBuild.result}: `${env.JOB_NAME}` #${env.BUILD_NUMBER}:\n${env.BUILD_URL}"
              slackSend(channel: "#exoplayer-dev", color: 'good', message: msg)
      }
    }
    failure {
      print( "Boo FAILURE ..." )
      script { 
        def msg = "${currentBuild.result}: `${env.JOB_NAME}` #${env.BUILD_NUMBER}:\n${env.BUILD_URL}"
               slackSend(channel: "#exoplayer-dev", color: 'danger', message: msg)
      }
    }
    cleanup {
      print( "Cleaning up ..." )
      //deleteDir()
    }
  }

}
