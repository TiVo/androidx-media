#!groovy

svcId = 'exoplayerprvt'

def user_id
def group_id
node {
  label 'docker'
  user_id = sh(returnStdout: true, script: 'id -u').trim()
  group_id = sh(returnStdout: true, script: 'id -g').trim()
}
pipeline { 
  agent none

  options {
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(daysToKeepStr: '15', numToKeepStr: '10'))
  }

  stages {

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

        // Build first, everything in ExoPlayer depends on this.  Note this is the only 'clean' build
        stage("Build ExoPlayer core") {
          options {
            skipDefaultCheckout()
          }

          steps {
            script {
              try {
                sh 'env'
                sh './gradlew clean library-core:build'
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
              slackSend(channel: "#exoplayer-builds", color: 'good', message: msg)
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
