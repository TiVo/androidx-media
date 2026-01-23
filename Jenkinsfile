svcId = 'androidx-media'

def user_id
def group_id

// List of the AndroidX Media3 modules we build and publish
def getArtifactsList(target) {
    return """
        :libraries:common:${target} \\
        :libraries:extractor:${target} \\
        :libraries:exoplayer:${target} \\
        :libraries:exoplayer-hls:${target} \\
        :libraries:ui:${target}
    """.stripIndent().trim()
}

pipeline { 
  agent {
    label 'docker'
  }

  options {
    timeout(time: 2, unit: 'HOURS')
    buildDiscarder(logRotator(daysToKeepStr: '15', numToKeepStr: '10'))
  }

  stages {
    stage("Setup env") {
      steps {
        script {
          user_id = sh(returnStdout: true, script: 'id -u').trim()
          group_id = sh(returnStdout: true, script: 'id -g').trim()

          // Determine build behavior based on branch/PR
          if (env.CHANGE_ID) {
            echo "Pull request build ${CHANGE_ID}, running 'check' target"
          } else if (env.BRANCH_NAME ==~ /^(release-tivo.*)$/ || 
                     env.BRANCH_NAME == "release-tivo" || 
                     env.TAG_NAME ==~ /^v\d+.*/) {
            echo "Release build ${BRANCH_NAME}, running 'publish' target"
          } else {
            echo "Non-PR, non-release build ${BRANCH_NAME}, running 'build' target"
          }
        }
      }
    }

    stage("Check (PR)") {
      when {
        changeRequest()
      }
      agent {
        dockerfile {
          label 'docker'
          additionalBuildArgs "--build-arg UID=${user_id} --build-arg GID=${group_id}"
          registryUrl 'https://docker.tivo.com'
          reuseNode true
        }
      }
      steps {
        script {
          sh 'env'
          sh """
            ./gradlew --no-daemon clean
            ./gradlew --no-daemon --parallel ${getArtifactsList('check')}
            """
        }
      }
    }

    stage("Build") {
      when {
        not { changeRequest() }
        not { 
          anyOf {
            branch pattern: "release-tivo.*", comparator: "REGEXP"
            branch "release-tivo"
            tag pattern: "v\\d+.*", comparator: "REGEXP"
          }
        }
      }
      agent {
        dockerfile {
          label 'docker'
          additionalBuildArgs "--build-arg UID=${user_id} --build-arg GID=${group_id}"
          registryUrl 'https://docker.tivo.com'
          reuseNode true
        }
      }
      steps {
        script {
          sh 'env'
          sh """
            ./gradlew --no-daemon clean
            ./gradlew --no-daemon --parallel ${getArtifactsList('build')}
            """
        }
      }
    }

    stage("Build and Publish") {
      when {
        anyOf {
          branch pattern: "release-tivo.*", comparator: "REGEXP"
          branch "release-tivo"
          tag pattern: "v\\d+.*", comparator: "REGEXP"
        }
      }
      agent {
        dockerfile {
          label 'docker'
          additionalBuildArgs "--build-arg UID=${user_id} --build-arg GID=${group_id}"
          registryUrl 'https://docker.tivo.com'
          reuseNode true
        }
      }
      steps {
        script {
          sh 'env'
          sh '''
          version="$(./gradlew -q -b gradle_util.gradle resolveProperties --prop=rootProject.releaseVersion)"
          echo "Publishing release build from branch: ${BRANCH_NAME} - version: $version"
          '''
          try {
            sh """
              ./gradlew --no-daemon clean
              ./gradlew --no-daemon --parallel ${getArtifactsList('build')}
              """
            echo "Build and tests successful, proceeding with publish..."
            
            // Jenkins injects REPO credentials via build variables
            sh """
              ./gradlew --no-daemon --parallel ${getArtifactsList('publishReleasePublicationToTiVoArtifactoryRepository')} -PREPO_USER_NAME=${build_username} -PREPO_PASSWORD=${build_password}
              """
            echo "Publish completed successfully"
          } catch (Exception e) {
            echo "ERROR: Build/tests failed - artifacts were not published"
            throw e
          }
        }
      }
    }
  }

  post {
    always {
      // Tests: unit and integration
      junit allowEmptyResults: true, testResults: '''
        **/build/test-results/test*/TEST-*.xml,
        **/build/test-results/*UnitTest/TEST-*.xml,
        **/build/outputs/androidTest-results/connected/*.xml
      '''

      // Archive reports
      archiveArtifacts fingerprint: true, allowEmptyArchive: true, artifacts: '''
        **/build/reports/tests/**/*.html,
        **/build/reports/lint-results*.html
      '''
    }
  }
}