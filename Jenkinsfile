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

                    echo "=== Core health (inside network) ==="
                    # Prefer docker compose exec so we hit the service on the private network
                    ok=0
                    for i in $(seq 1 40); do
                      if docker compose exec -T core curl -fsS http://127.0.0.1:8080/actuator/health >/tmp/core-health.json 2>/dev/null; then
                        echo "Core is healthy:"
                        cat /tmp/core-health.json || true
                        echo
                        ok=1
                        break
                      fi
                      # fallback: curl from a throwaway container on the same network
                      if docker run --rm --network gridveritas-net curlimages/curl:8.5.0 \
                           -fsS http://gridveritas-core:8080/actuator/health >/tmp/core-health.json 2>/dev/null; then
                        echo "Core is healthy (via network):"
                        cat /tmp/core-health.json || true
                        echo
                        ok=1
                        break
                      fi
                      echo "  attempt $i/40 – not ready yet"
                      sleep 3
                    done
                    if [ "$ok" != "1" ]; then
                      echo "Core did not become healthy in time."
                      docker compose ps || true
                      docker compose logs --no-color --tail=120 core || true
                      exit 1
                    fi

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
                sh '''
                    set -e
                    # Call API from inside the network (avoids host port / Jenkins-in-container issues)
                    RESP=$(docker run --rm --network gridveritas-net curlimages/curl:8.5.0 \
                      -fsS -X POST http://gridveritas-core:8080/api/v1/sources \
                      -H "Content-Type: application/json" \
                      -d '{"name":"jenkins-smoke","publicKey":"smoke-key"}')
                    echo "Create source response: $RESP"
                    echo "$RESP" | grep -q '"id"'
                    echo "Smoke test OK"
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
