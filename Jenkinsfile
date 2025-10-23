pipeline {
  agent any

  tools { jdk 'jdk21'; maven 'M3' }

  environment {
    REGISTRY       = 'https://index.docker.io/v1/'
    DOCKERHUB      = 'dockerhub'                 // Jenkins credentials ID
    DOCKERHUB_USER = credentials('dockerhub').usr
    TAG            = "${env.BUILD_NUMBER}"
    SERVICES       = "accounts-service,billing-service"
  }

  options { timestamps(); ansiColor('xterm') }
  triggers { pollSCM('H/5 * * * *') } // remove if you use a webhook

  stages {
    stage('Checkout'){ steps { checkout scm } }

    stage('Unit tests (parallel)'){
      steps {
        script {
          def svc = env.SERVICES.split(',')
          parallel svc.collectEntries { s ->
            ["test-${s}": { dir("services/${s}") { sh 'mvn -B -q test' } }]
          }
        }
      }
    }

    stage('Package (parallel)'){
      steps {
        script {
          def svc = env.SERVICES.split(',')
          parallel svc.collectEntries { s ->
            ["pkg-${s}": { dir("services/${s}") { sh 'mvn -B -q -DskipTests package' } }]
          }
        }
      }
    }

    stage('Build images (parallel)'){
      steps {
        script {
          docker.withRegistry(env.REGISTRY, env.DOCKERHUB) {
            def svc = env.SERVICES.split(',')
            parallel svc.collectEntries { s ->
              ["build-${s}": {
                dir("services/${s}") {
                  sh """
                    docker build -t ${DOCKERHUB_USER}/${s}:${TAG} .
                    docker tag ${DOCKERHUB_USER}/${s}:${TAG} ${DOCKERHUB_USER}/${s}:latest
                  """
                }
              }]
            }
          }
        }
      }
    }

    stage('Push images (parallel)'){
      steps {
        script {
          docker.withRegistry(env.REGISTRY, env.DOCKERHUB) {
            def svc = env.SERVICES.split(',')
            parallel svc.collectEntries { s ->
              ["push-${s}": {
                sh """
                  docker push ${DOCKERHUB_USER}/${s}:${TAG}
                  docker push ${DOCKERHUB_USER}/${s}:latest
                """
              }]
            }
          }
        }
      }
    }

    stage('Deploy (Docker Compose)'){
      steps {
        sh """
          export DOCKERHUB_USERNAME=${DOCKERHUB_USER}
          export TAG=${TAG}
          docker compose pull
          docker compose up -d
        """
      }
    }

    stage('Verify'){
      steps {
        sh 'curl -fsS http://localhost:8091/health'
        sh 'curl -fsS http://localhost:8092/health'
      }
    }
  }

  post {
    success { echo "Deployed ${TAG}" }
    always  { sh 'docker ps || true' }
  }
}
