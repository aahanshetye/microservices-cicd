pipeline {
  agent any

  // Absolute paths so PATH quirks can’t bite us on macOS
  environment {
    MVN      = '/opt/homebrew/bin/mvn'      // change if `which mvn` shows a different path
    DOCKER   = '/usr/local/bin/docker'      // Docker Desktop CLI symlink
    SERVICES = 'accounts-service,billing-service'
    // Make Docker/Compose more tolerant of slow networks
    DOCKER_CLIENT_TIMEOUT = '300'
    COMPOSE_HTTP_TIMEOUT  = '300'
  }

  options { timestamps() }
  // keep pollSCM or use webhook — either is fine
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

    // Login using your Docker Hub PAT stored in Jenkins credential 'dockerhub'
    // We write a minimal $HOME/.docker/config.json that DISABLES the credential helper
    stage('Docker login (HOME config, no helper)') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'dockerhub', usernameVariable: 'DH_USER', passwordVariable: 'DH_PASS')]) {
          sh '''#!/bin/bash
            set -euo pipefail
            set +x
            mkdir -p "$HOME/.docker"
            # backup existing config once (no-op if missing)
            [ -f "$HOME/.docker/config.json" ] && cp "$HOME/.docker/config.json" "$HOME/.docker/config.json.bak" || true
            # minimal config: disable credsStore so login writes basic auth (no docker-credential-desktop needed)
            printf '%s\n' '{"credsStore":""}' > "$HOME/.docker/config.json"
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

    // Robust push with retries + exponential backoff to tolerate Docker Hub hiccups
    stage('Push images (parallel, retried)') {
      steps {
        script {
          def svc  = SERVICES.split(',')
          def user = env.DOCKERHUB_USER
          def tag  = env.BUILD_NUMBER

          def pushWithRetry = { image ->
            sh """
              set -e
              n=0
              until [ \$n -ge 5 ]; do
                echo ">> Pushing ${image} (attempt \$((n+1))/5)"
                if ${DOCKER} push ${image}; then
                  echo ">> Push ok: ${image}"
                  break
                fi
                n=\$((n+1))
                sleep \$((5 * n))   # backoff: 5s,10s,15s,20s,25s
              done
              test \$n -lt 5   # fail stage if all retries exhausted
            """
          }

          parallel svc.collectEntries { s ->
            ["push-${s}": {
              pushWithRetry("${user}/${s}:${tag}")
              pushWithRetry("${user}/${s}:latest")
            }]
          }
        }
      }
    }

    // Deploy from LOCAL images we just built (don’t depend on pull/push timing)
    stage('Deploy (Docker Compose up)') {
      steps {
        script {
          def user = env.DOCKERHUB_USER
          def tag  = env.BUILD_NUMBER
          sh """
            export DOCKERHUB_USERNAME=${user}
            export TAG=${tag}
            ${DOCKER} compose up -d --force-recreate --remove-orphans
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
