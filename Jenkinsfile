#!groovy

svcId = 'exoplayerprvt'

def user_id
def group_id
def gradle_target


pipeline { 
  agent {
    label 'docker'
  }

  options {
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(daysToKeepStr: '15', numToKeepStr: '10'))
  }

  stages {
    stage("Setup env") {
      steps {
        script {
          user_id = sh(returnStdout: true, script: 'id -u').trim()
          group_id = sh(returnStdout: true, script: 'id -g').trim()

          // Only run debug build unit-test for pull request check build.
          if (env.CHANGE_ID) {    // CHANGE_ID is the pull request ID
            gradle_target = "check";
          } else {
            gradle_target = "build";
          }
        }
      }
    }

    stage("Perform Build") {
      agent {
        dockerfile {
          label 'docker'
          additionalBuildArgs "--build-arg UID=${user_id} --build-arg GID=${group_id}"
          registryUrl 'https://docker.tivo.com'
          reuseNode true
        }
      }

      stages {
          // Build first, everything in ExoPlayer and its clients
        stage("Build ExoPlayer common library") {
          options {
            skipDefaultCheckout()
          }

          steps {
            script {
              try {
                sh 'env'
                sh "./gradlew library-common:${gradle_target}"
              } finally {
                junit allowEmptyResults: true, testResults: 'library/common/buildout/test-results/testReleaseUnitTest/TEST-*.xml'
              }
            }
          }
        }

        // Extractor is a large library, depends on common, about a 10m build
        stage("Build ExoPlayer extractor") {
          options {
            skipDefaultCheckout()
          }

          steps {
            script {
              try {
                sh 'env'
                sh "./gradlew library-extractor:${gradle_target}"
              } finally {
                junit allowEmptyResults: true, testResults: 'library/extractor/buildout/test-results/testReleaseUnitTest/TEST-*.xml'
              }
            }
          }
        }

        // Core builds library depends on extactor and common
        stage("Build ExoPlayer core") {
          options {
            skipDefaultCheckout()
          }

          steps {
            script {
              try {
                sh "./gradlew library-core:${gradle_target}"
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
                    sh "./gradlew library-hls:${gradle_target}"
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
                    sh "./gradlew library-dash:${gradle_target}"
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
                    sh "./gradlew library-ui:${gradle_target}"
                  } finally {
                    junit allowEmptyResults: true, testResults: 'library/ui/buildout/test-results/testReleaseUnitTest/TEST-*.xml'
                  }
                }
              }
            }
            stage("Build MediaSession Extension") {
              options {
                skipDefaultCheckout()
              }

              steps {
                script {
                  try {
                    sh "./gradlew extension-mediasession:${gradle_target}"
                  } finally {
                    junit allowEmptyResults: true, testResults: 'extension/mediasession/buildout/test-results/testReleaseUnitTest/TEST-*.xml'
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
                sh "./gradlew library-tivo-ui:${gradle_target}"
                sh "./gradlew library-trickplay:${gradle_target}"
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
              tag pattern: "r\\d+.*"
            }
          }
          steps {
            sh '''
            version="$(./gradlew -q -b gradle_util.gradle resolveProperties --prop=rootProject.releaseVersion)"
            echo "Publishing release build from branch: $BRANCH_NAME - version: $version"
            ./gradlew library-core:publish library-common:publish library-extractor:publish library-hls:publish library-dash:publish library-ui:publish library-tivo-ui:publish library-trickplay:publish demo-tenfoot:publish extension-mediasession:publish -PREPO_USER_NAME=build -PREPO_PASSWORD=buildcode
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
              slackSend(channel: "#exoplayer-builds", color: 'good', message: msg, failOnError: false)
      }
    }
    failure {
      print( "Boo FAILURE ..." )
      script { 
        def msg = "${currentBuild.result}: `${env.JOB_NAME}` #${env.BUILD_NUMBER}:\n${env.BUILD_URL}"
               slackSend(channel: "#exoplayer-builds", color: 'danger', message: msg)
      }
    }
    cleanup {
      print( "Cleaning up ..." )
      //deleteDir()
    }
  }

}
