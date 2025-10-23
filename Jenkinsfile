pipeline {
  agent any

  // Use absolute paths so PATH quirks can't bite
  environment {
    MVN      = '/opt/homebrew/bin/mvn'        // change if `which mvn` shows a different path
    DOCKER   = '/usr/local/bin/docker'        // you verified this path
    SERVICES = 'accounts-service,billing-service'
  }

  options { timestamps() }
  triggers { pollSCM('H/5 * * * *') } // keep or remove if you rely solely on webhook

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
              dir("services/${s}") {
                sh "${MVN} -B -q test"
              }
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
              dir("services/${s}") {
                sh "${MVN} -B -q -DskipTests package"
              }
            }]
          }
        }
      }
    }

    // Write credentials to $HOME/.docker/config.json with credsStore disabled
    // so docker login works without docker-credential-desktop,
    // and keep Compose plugin visible.
    stage('Docker login (HOME config, no helper)') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'dockerhub', usernameVariable: 'DH_USER', passwordVariable: 'DH_PASS')]) {
          sh '''#!/bin/bash
            set -euo pipefail
            set +x
            mkdir -p "$HOME/.docker"
            # Backup existing config just in case
            if [ -f "$HOME/.docker/config.json" ]; then
              cp "$HOME/.docker/config.json" "$HOME/.docker/config.json.bak" || true
            fi
            # Minimal config: disable credsStore so login writes plaintext auth
            cat > "$HOME/.docker/config.json" <<'JSON'
{"credsStore":""}
JSON
            printf "%s\n" "$DH_PASS" | /usr/local/bin/docker login -u "$DH_USER" --password-stdin
          '''
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
