pipeline {
  agent any

  environment {
    MVN      = '/opt/homebrew/bin/mvn'
    DOCKER   = '/usr/local/bin/docker'
    TAG      = "${env.BUILD_NUMBER}"
    SERVICES = 'accounts-service,billing-service'
    CRED     = credentials('dockerhub')         // exposes CRED_USR and CRED_PSW
  }

  options { timestamps() }
  triggers { pollSCM('H/5 * * * *') } // keep or remove if only using webhook

  stages {
    stage('Sanity: paths & versions') {
      steps {
        sh "echo PATH=\$PATH"
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
          def svc = env.SERVICES.split(',')
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
          def svc = env.SERVICES.split(',')
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

    stage('Docker login') {
      steps {
        sh "echo \"${CRED_PSW}\" | ${DOCKER} login -u \"${CRED_USR}\" --password-stdin"
      }
    }

    stage('Build images (parallel)') {
      steps {
        script {
          def svc = env.SERVICES.split(',')
          parallel svc.collectEntries { s ->
            ["build-${s}": {
              dir("services/${s}") {
                sh """
                  ${DOCKER} build -t ${CRED_USR}/${s}:${TAG} .
                  ${DOCKER} tag ${CRED_USR}/${s}:${TAG} ${CRED_USR}/${s}:latest
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
          def svc = env.SERVICES.split(',')
          parallel svc.collectEntries { s ->
            ["push-${s}": {
              sh """
                ${DOCKER} push ${CRED_USR}/${s}:${TAG}
                ${DOCKER} push ${CRED_USR}/${s}:latest
              """
            }]
          }
        }
      }
    }

    stage('Deploy (Docker Compose)') {
      steps {
        sh """
          export DOCKERHUB_USERNAME=${CRED_USR}
          export TAG=${TAG}
          ${DOCKER} compose pull
          ${DOCKER} compose up -d
        """
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
    always { sh "${DOCKER} ps || true" }
    success { echo "Deployed ${TAG}" }
  }
}
