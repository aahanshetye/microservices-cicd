pipeline {
  agent any

  // Absolute paths avoid PATH surprises in Jenkins
  environment {
    MVN      = '/opt/homebrew/bin/mvn'          // change if `which mvn` shows a different path
    DOCKER   = '/usr/local/bin/docker'          // symlink to Docker Desktop's CLI
    SERVICES = 'accounts-service,billing-service'
  }

  options { timestamps() }
  // keep pollSCM even if you use a webhook; remove it if you want only webhook
  triggers { pollSCM('H/5 * * * *') }

  stages {

    stage('Sanity: versions') {
      steps {
        sh "${MVN} -v"
        sh "${DOCKER} version"
        sh "${DOCKER} compose version"
      }
    }

    stage('Checkout') {
      steps { checkout scm }
    }

    stage('Unit tests (parallel)') {
      steps {
        script {
          def svc = SERVICES.split(',')
          parallel svc.collectEntries { s ->
            ["test-${s}": {
              dir("services/${s}") { sh "${MVN} -B -q test" }
            }]
          }
        }
      }
    }

    stage('Package (parallel)') {
      steps {
        script {
          def svc = SERVICES.split(',')
          parallel svc.collectEntries { s ->
            ["pkg-${s}": {
              dir("services/${s}") { sh "${MVN} -B -q -DskipTests package" }
            }]
          }
        }
      }
    }

    stage('Docker login (no cred helper)') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'dockerhub', usernameVariable: 'DH_USER', passwordVariable: 'DH_PASS')]) {
          // Use a job-local Docker config and disable credsStore to avoid docker-credential-desktop
          sh '''#!/bin/bash
            set -euo pipefail
            set +x
            mkdir -p "$WORKSPACE/.docker"
            export DOCKER_CONFIG="$WORKSPACE/.docker"
            echo '{"credsStore":""}' > "$DOCKER_CONFIG/config.json"
            printf "%s\n" "$DH_PASS" | /usr/local/bin/docker login -u "$DH_USER" --password-stdin
          '''
          // make username available for later stages
          script { env.DOCKERHUB_USER = env.DH_USER }
        }
      }
    }

    stage('Build images (parallel)') {
      steps {
        script {
          def svc  = SERVICES.split(',')
          def user = env.DOCKERHUB_USER
          def tag  = env.BUILD_NUMBER
          parallel svc.collectEntries { s ->
            ["build-${s}": {
              dir("services/${s}") {
                sh """
                  set -e
                  export DOCKER_CONFIG="$WORKSPACE/.docker"
                  ${DOCKER} build -t ${user}/${s}:${tag} .
                  ${DOCKER} tag  ${user}/${s}:${tag} ${user}/${s}:latest
                """
              }
            }]
          }
        }
      }
    }

    stage('Push images (parallel)') {
      steps {
        script {
          def svc  = SERVICES.split(',')
          def user = env.DOCKERHUB_USER
          def tag  = env.BUILD_NUMBER
          parallel svc.collectEntries { s ->
            ["push-${s}": {
              sh """
                set -e
                export DOCKER_CONFIG="$WORKSPACE/.docker"
                ${DOCKER} push ${user}/${s}:${tag}
                ${DOCKER} push ${user}/${s}:latest
              """
            }]
          }
        }
      }
    }

    stage('Deploy (Docker Compose)') {
      steps {
        script {
          def user = env.DOCKERHUB_USER
          def tag  = env.BUILD_NUMBER
          sh """
            set -e
            export DOCKER_CONFIG="$WORKSPACE/.docker"
            export DOCKERHUB_USERNAME=${user}
            export TAG=${tag}
            ${DOCKER} compose pull
            ${DOCKER} compose up -d
          """
        }
      }
    }

    stage('Verify') {
      steps {
        sh "curl -fsS http://localhost:8091/health"
        sh "curl -fsS http://localhost:8092/health"
      }
    }
  }

  post {
    always  { sh "${DOCKER} ps || true" }
    success { echo "Deployed ${env.DOCKERHUB_USER}/(accounts-service|billing-service):${env.BUILD_NUMBER}" }
  }
}
