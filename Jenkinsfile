pipeline {
  agent any

  environment {
    MVN      = '/opt/homebrew/bin/mvn'        // change if `which mvn` is different
    DOCKER   = '/usr/local/bin/docker'        // you already verified this path
    SERVICES = 'accounts-service,billing-service'
  }

  options { timestamps() }
  triggers { pollSCM('H/5 * * * *') } // keep or remove if webhook only

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

    stage('Docker login') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'dockerhub', usernameVariable: 'DH_USER', passwordVariable: 'DH_PASS')]) {
          sh "echo \"${DH_PASS}\" | ${DOCKER} login -u \"${DH_USER}\" --password-stdin"
          script { env.DOCKERHUB_USER = DH_USER }
        }
      }
    }

    stage('Build images (parallel)') {
      steps {
        script {
          def svc = SERVICES.split(',')
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
          def svc = SERVICES.split(',')
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
