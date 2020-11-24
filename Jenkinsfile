#!groovy

svcId = 'exoplayerprvt'


pipeline { 
  agent none

  options {
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(daysToKeepStr: '15', numToKeepStr: '10'))
  }

  stages {
    stage("Environment Setup") {
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
        '''
      }
    }

    stage("Perform Build") {
      agent { label 'linux-android' }

      environment {
        ANDROID_SDK_ROOT = "/home/build/Android/sdk"
        PATH = "$PATH:$ANDROID_SDK_ROOT/tools/bin:$ANDROID_SDK_ROOT/platform-tools"
      }

      stages {

        // Build first, everything in ExoPlayer depends on this
        stage("Build ExoPlayer core") {
          options {
            skipDefaultCheckout()
          }

          steps {
            script {
              try {
                sh './gradlew library-core:build'
              } finally {
                junit allowEmptyResults: true, testResults: 'library/core/buildout/test-results/testReleaseUnitTest/TEST-*.xml'
              }
            }
          }
        }

        // ExoPlayer internal libraries -- all depend on core, never each other
        stage("Build ExoPlayer Libraries") {
          parallel {
            stage("Build HLS Library") {
              options {
                skipDefaultCheckout()
              }

              steps {
                script {
                  try {
                    sh './gradlew library-hls:build'
                  } finally {
                    junit allowEmptyResults: true, testResults: 'library/hls/buildout/test-results/testReleaseUnitTest/TEST-*.xml'
                  }
                }
              }
            }

            stage("Build DASH Library") {
              options {
                skipDefaultCheckout()
              }

              steps {
                script {
                  try {
                    sh './gradlew library-dash:build'
                  } finally {
                    junit allowEmptyResults: true, testResults: 'library/dash/buildout/test-results/testReleaseUnitTest/TEST-*.xml'
                  }
                }
              }
            }

            stage("Build UI Library") {
              options {
                skipDefaultCheckout()
              }

              steps {
                script {
                  try {
                    sh './gradlew library-ui:build'
                  } finally {
                    junit allowEmptyResults: true, testResults: 'library/ui/buildout/test-results/testReleaseUnitTest/TEST-*.xml'
                  }
                }
              }
            }
          }
        }

        // Tivo libraries external to ExoPlayer depend on ExoPlayer core and libraries
        stage("Build TiVo Libraries") {
          options {
            skipDefaultCheckout()
          }

          steps {
            script {
              try {
                sh './gradlew library-tivo-ui:build '
                sh './gradlew library-trickplay:build'
              } finally {
                junit allowEmptyResults: true, testResults: 'library/trickplay/buildout/test-results/testReleaseUnitTest/TEST-*.xml'
                junit allowEmptyResults: true, testResults: 'library/trickplay/tivo-ui/test-results/testReleaseUnitTest/TEST-*.xml'
              }
            }
          }
        }

        stage("Publish Build") {
          options {
            skipDefaultCheckout()
          }

          when {
            beforeAgent true
            anyOf {
              branch pattern: "release-*"
              branch pattern: "streamer-*"
              branch "release"
            }
          }
          steps {
            sh '''
            version="$(./gradlew -q -b gradle_util.gradle resolveProperties --prop=rootProject.releaseVersion)"
            echo "Publishing release build from branch: $BRANCH_NAME - version: $version"
            ./gradlew publish -PREPO_USER_NAME=build -PREPO_PASSWORD=buildcode
            '''
          }
        }
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
