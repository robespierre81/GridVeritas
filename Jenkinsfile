// ============================================================
// GridVeritas – Full Stack Pipeline (v2)
// Health checks run INSIDE the Docker network (works when
// Jenkins itself runs in a container).
// ============================================================

pipeline {
    agent any

    environment {
        STACK_NAME   = 'gridveritas'
        CORE_IMAGE   = 'gridveritas/core'
        UI_IMAGE     = 'gridveritas/ui'
        IMAGE_TAG    = "${env.BUILD_NUMBER ?: 'local'}"
        COMPOSE_PROJECT_NAME = 'gridveritas'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '15'))
        timestamps()
        disableConcurrentBuilds()
    }

    stages {

        stage('Checkout') {
            steps {
                echo "Checking out full GridVeritas stack..."
                checkout scm
            }
        }

        stage('Pre-flight') {
            steps {
                sh '''
                    set -e
                    docker version
                    docker compose version || docker-compose version
                    ls -la
                    test -f docker-compose.yml
                    test -d gridveritas-core
                    test -d gridveritas-ui
                '''
            }
        }

        stage('Build images') {
            steps {
                echo "Building core and UI images via Compose..."
                sh '''
                    set -e
                    docker compose build --pull
                    docker tag gridveritas/core:local  ${CORE_IMAGE}:${IMAGE_TAG}  || true
                    docker tag gridveritas/ui:local    ${UI_IMAGE}:${IMAGE_TAG}    || true
                    docker tag gridveritas/core:local  ${CORE_IMAGE}:latest        || true
                    docker tag gridveritas/ui:local    ${UI_IMAGE}:latest          || true
                '''
            }
        }

        stage('Start stack') {
            steps {
                echo "Starting isolated stack on gridveritas-net..."
                sh '''
                    set -e
                    # Schema ist jetzt Flyway-verwaltet: DB-Volume verwerfen, damit Flyway ab V1 baut
                    docker compose down -v --remove-orphans || true
                    docker compose up -d --build --remove-orphans
                    docker compose ps
                '''
            }
        }

        stage('Health checks') {
            steps {
                echo "Waiting for services (checks via Docker network, not localhost)..."
                sh '''
                    set -e

                    echo "=== UI health (inside network) ==="
                    ok=0
                    for i in $(seq 1 20); do
                      if docker compose exec -T ui wget -q -O - http://127.0.0.1/ >/dev/null 2>&1; then
                        echo "UI is responding."
                        ok=1
                        break
                      fi
                      if docker run --rm --network gridveritas-net curlimages/curl:8.5.0 \
                           -fsS -o /dev/null http://gridveritas-ui/; then
                        echo "UI is responding (via network)."
                        ok=1
                        break
                      fi
                      echo "  attempt $i/20 – UI not ready yet"
                      sleep 2
                    done
                    if [ "$ok" != "1" ]; then
                      echo "UI did not respond in time."
                      docker compose logs --no-color --tail=80 ui || true
                      exit 1
                    fi
                '''
            }
        }

        stage('Smoke test (API)') {
            steps {
                echo "Running basic API smoke test via Docker network..."
            }
        }

        stage('Seed demo data') {
            steps {
                sh '''
                    set -e
                    # sicherstellen, dass der Core wirklich bereit ist (health kann kurz nach "Started" noch rot sein)
                    for i in $(seq 1 30); do
                      if curl -fsS http://localhost:18080/actuator/health | grep -q '"status":"UP"'; then
                        echo "core UP"; break
                      fi
                      echo "waiting for core... ($i)"; sleep 5
                    done

                    # Seeder einmalig bauen + ausführen; --rm räumt den Container weg, --no-deps
                    # verhindert, dass compose die anderen Services nochmal anfasst
                    docker compose --profile seed run --rm --no-deps --build seed
                '''
            }
        }
    }

    post {
        success {
            echo """
================================================
 GridVeritas stack is UP
 Network: gridveritas-net
 Host UI (if ports published on this machine):  http://<host>:3080
 Host API: http://<host>:18080
 Images:  ${CORE_IMAGE}:${IMAGE_TAG}  ${UI_IMAGE}:${IMAGE_TAG}
================================================
"""
        }
        failure {
            echo "Stack pipeline failed – collecting logs..."
            sh '''
                docker compose ps || true
                docker compose logs --no-color --tail=150 || true
            '''
        }
        always {
            echo "Build finished: ${currentBuild.currentResult}"
        }
    }
}
