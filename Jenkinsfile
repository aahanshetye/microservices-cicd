pipeline {
  agent any

  environment {
    REGISTRY = 'https://index.docker.io/v1/'
    // Bind the credentials; will expose CRED_USR and CRED_PSW automatically
    CRED = credentials('dockerhub')
    TAG = "${env.BUILD_NUMBER}"
    SERVICES = "accounts-service,billing-service"
  }

  // Keep timestamps; remove ansiColor to avoid plugin requirement
  options { timestamps() }

  // If you're using GitHub webhook, you can remove pollSCM.
  triggers { pollSCM('H/5 * * * *') }

  stages {

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
                sh 'mvn -B -q test'
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
                sh 'mvn -B -q -DskipTests package'
              }
            }]
          }
        }
      }
    }

    stage('Build images (parallel)') {
      steps {
        script {
          // Login is handled by withRegistry; use credentialsId = 'dockerhub'
          docker.withRegistry(env.REGISTRY, 'dockerhub') {
            def svc = env.SERVICES.split(',')
            parallel svc.collectEntries { s ->
              ["build-${s}": {
                dir("services/${s}") {
                  sh """
                    docker build -t ${CRED_USR}/${s}:${TAG} .
                    docker tag ${CRED_USR}/${s}:${TAG} ${CRED_USR}/${s}:latest
                  """
                }
              }]
            }
          }
        }
      }
    }

    stage('Push images (parallel)') {
      steps {
        script {
          docker.withRegistry(env.REGISTRY, 'dockerhub') {
            def svc = env.SERVICES.split(',')
            parallel svc.collectEntries { s ->
              ["push-${s}": {
                sh """
                  docker push ${CRED_USR}/${s}:${TAG}
                  docker push ${CRED_USR}/${s}:latest
                """
              }]
            }
          }
        }
      }
    }

    stage('Deploy (Docker Compose)') {
      steps {
        sh """
          export DOCKERHUB_USERNAME=${CRED_USR}
          export TAG=${TAG}
          docker compose pull
          docker compose up -d
        """
      }
    }

    stage('Verify') {
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
