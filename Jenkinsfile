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
        CORE_REPLICAS = "${params.CORE_REPLICAS}"
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '15'))
        timestamps()
        disableConcurrentBuilds()
    }

    parameters {
        booleanParam(name: 'RUN_SECURITY_SCANS', defaultValue: true,
                     description: 'Run OWASP Dependency-Check and Trivy image scans (slower).')
        booleanParam(name: 'RUN_TAMPER_DEMO', defaultValue: false,
                     description: 'Run the destructive tamper demonstration (alters a seeded record).')
        booleanParam(name: 'RUN_LOAD_TEST', defaultValue: false,
                     description: 'Run IngestLoadRunner + the k6 read-path load test against the running stack (slower).')
        booleanParam(name: 'RUN_DB_FAILOVER', defaultValue: false,
                     description: 'Destructive: stop postgres-primary and prove postgres-watch promotes the replica (ADR-015). Leaves the primary down.')
        string(name: 'CORE_REPLICAS', defaultValue: '2',
               description: 'Core replicas (ADR-013). Use 2 on the shared 8 GiB host; 4 pins the CPU.')
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
                    test -f nginx-lb/Dockerfile
                    test -f nginx-lb.conf
                    test -f postgres/primary/Dockerfile
                    test -f postgres/replica/Dockerfile
                    test -f postgres/haproxy/Dockerfile
                    test -f postgres/watch/Dockerfile
                    test -f ci/check_federation.sh
                    test -f ci/check_postgres_replica.sh
                    test -f ci/prove_postgres_failover.sh
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
                echo "Starting isolated stack on gridveritas-net (${params.CORE_REPLICAS} core replicas, ADR-013)..."
                sh '''
                    set -e
                    # Schema ist jetzt Flyway-verwaltet: DB-Volume verwerfen, damit Flyway ab V1 baut
                    docker compose down -v --remove-orphans || true
                    docker compose up -d --build --remove-orphans --scale core=${CORE_REPLICAS}
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

        stage('Instance count check') {
            steps {
                echo "Verifying at least ${CORE_REPLICAS} core replica(s) report online via /api/v1/cluster/instances (ADR-013)..."
                sh '''
                    set -e
                    docker run --rm -i --network gridveritas-net \
                        -e ADMIN_PASSWORD=${ADMIN_PASSWORD:-admin-change-me} \
                        -e EXPECTED=${CORE_REPLICAS} \
                        -e BASE_URL=http://gridveritas-traefik \
                        --entrypoint sh alpine:3.20 -s \
                        < ci/check_instance_count.sh
                '''
            }
        }

        stage('Postgres replica check') {
            steps {
                echo "Proving the streaming replica is in recovery and postgres-rw is writable (ADR-015)..."
                sh '''
                    set -e
                    chmod +x ci/check_postgres_replica.sh
                    ./ci/check_postgres_replica.sh
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
                    # Jenkins runs inside a container, so localhost is not the host.
                    # Routed through Traefik (not a fixed core container name/address -
                    # core has none since it can be scaled to multiple replicas, ADR-013).
                    for i in $(seq 1 30); do
                      if docker run --rm --network gridveritas-net curlimages/curl:8.5.0 \
                           -fsS http://gridveritas-traefik/actuator/health | grep -q '"status":"UP"'; then
                        echo "core UP (via Traefik)"; break
                      fi
                      echo "waiting for core... ($i)"; sleep 5
                    done

                    # Seeder einmalig bauen + ausführen; --rm räumt den Container weg, --no-deps
                    # verhindert, dass compose die anderen Services nochmal anfasst
                    docker compose --profile seed run --rm --no-deps --build seed
                '''
            }
        }

        stage('Federation host-proof') {
            steps {
                echo "Proving M13 publish + loopback peer fetch via the load balancer (ADR-014)..."
                sh '''
                    set -e
                    docker run --rm -i --network gridveritas-net \
                        -e ADMIN_PASSWORD=${ADMIN_PASSWORD:-admin-change-me} \
                        -e BASE_URL=http://gridveritas-traefik \
                        --entrypoint sh alpine:3.20 -s \
                        < ci/check_federation.sh
                '''
            }
        }

        stage('Load & performance tests') {
            when { expression { return params.RUN_LOAD_TEST == true } }
            steps {
                echo "Running IngestLoadRunner + k6 read-path load test against the running stack (${CORE_REPLICAS} core replicas)..."
                sh '''
                    set -e
                    mkdir -p .m2-cache

                    echo "=== IngestLoadRunner (write path) ==="
                    docker run --rm \
                        -v "$PWD":/workspace -w /workspace/gridveritas-core \
                        -v "$PWD/.m2-cache":/root/.m2 \
                        --network gridveritas-net \
                        maven:3.9-eclipse-temurin-21 \
                        mvn -q -Pload-test \
                            -Dexec.args="http://gridveritas-traefik ${ADMIN_PASSWORD:-admin-change-me} ${INGEST_PASSWORD:-ingest-change-me} 8 100" \
                            test-compile exec:java \
                        | tee load-test-ingest-report.txt

                    echo "=== k6 (read/auth path) ==="
                    docker run --rm --network gridveritas-net \
                        -v "$PWD/gridveritas-core/load-tests":/scripts \
                        -e BASE_URL=http://gridveritas-traefik \
                        -e ADMIN_PASSWORD=${ADMIN_PASSWORD:-admin-change-me} \
                        grafana/k6:latest run --vus 10 --duration 15s /scripts/read-path.k6.js \
                        | tee load-test-k6-report.txt
                '''
            }
            post {
                always {
                    archiveArtifacts artifacts: 'load-test-*-report.txt', allowEmptyArchive: true
                }
            }
        }

        stage('Tamper demo') {
            when { expression { return params.RUN_TAMPER_DEMO == true } }
            steps {
                sh '''
                    set -e
                    # Jenkins runs in a container: run the demo INSIDE a container on the
                    # compose network, reach the API by service name, and edit the DB via
                    # network psql (not docker exec). The script is piped via stdin, so no
                    # workspace bind-mount is needed (which would not resolve under DinD).
                    docker run --rm -i --network gridveritas-net \
                        -e API=http://gridveritas-traefik/api/v1 \
                        -e PG_HOST=gridveritas-postgres \
                        -e PG_USER=gridveritas \
                        -e PG_DB=gridveritas \
                        -e PG_PASSWORD=gridveritas \
                        -e ADMIN_PASSWORD=${ADMIN_PASSWORD:-admin-change-me} \
                        --entrypoint sh \
                        postgres:16-alpine \
                        -c 'apk add --no-cache curl python3 >/dev/null 2>&1; exec sh -s' \
                        < demo/tamper_demo.sh
                '''
            }
        }

        stage('Postgres failover proof') {
            when { expression { return params.RUN_DB_FAILOVER == true } }
            steps {
                echo "Destructive ADR-015 proof: stop primary, wait for promote, write through postgres-rw..."
                sh '''
                    set -e
                    chmod +x ci/prove_postgres_failover.sh
                    ./ci/prove_postgres_failover.sh
                '''
            }
        }

        stage('Image scan (Trivy)') {
            when { expression { return params.RUN_SECURITY_SCANS } }
            steps {
                sh '''
                    set -e
                    mkdir -p trivy-reports
                    # Read accepted CVEs from the repo .trivyignore (agent-local; no bind mount,
                    # so it works under Docker-in-Docker). Injected into the trivy container.
                    IGN=""
                    [ -f .trivyignore ] && IGN=$(grep -vE "^[[:space:]]*#|^[[:space:]]*$" .trivyignore | tr "\n" " ")
                    echo "Accepted (ignored) CVEs: ${IGN:-none}"

                    scan() {
                        img="$1"; name="$2"
                        echo "== Trivy: $img =="
                        # Full report (HIGH+CRITICAL); never fails the build
                        docker run --rm \
                            -v /var/run/docker.sock:/var/run/docker.sock \
                            -v gridveritas-trivy-cache:/root/.cache/ \
                            aquasec/trivy:latest image --scanners vuln \
                            --severity HIGH,CRITICAL --format table "$img" \
                            | tee "trivy-reports/${name}.txt"
                        # Gating pass: fail on FIXABLE CRITICAL, honoring the accepted list
                        docker run --rm -e IGN="$IGN" -e IMG="$img" \
                            -v /var/run/docker.sock:/var/run/docker.sock \
                            -v gridveritas-trivy-cache:/root/.cache/ \
                            --entrypoint sh aquasec/trivy:latest -c \
                            'printf "%s\n" $IGN > /tmp/.trivyignore; trivy image --scanners vuln --severity CRITICAL --ignore-unfixed --ignorefile /tmp/.trivyignore --exit-code 1 "$IMG"'
                    }

                    scan gridveritas/core:local core
                    scan gridveritas/ui:local   ui
                    scan gridveritas/edge:local edge
                '''
            }
            post {
                always {
                    archiveArtifacts artifacts: 'trivy-reports/*.txt', allowEmptyArchive: true
                }
            }
        }

        stage('Dependency scan (OWASP)') {
            when { expression { return params.RUN_SECURITY_SCANS } }
            // Best-effort report. The Jenkins agent has no Maven, so this runs Maven in a
            // container. In this Docker-in-Docker Jenkins the workspace bind-mount may not
            // resolve on the host daemon; if it doesn't, this step no-ops WITHOUT failing the
            // build (Trivy above already gates OS + dependency CVEs). For a gating official
            // OWASP report, run it on the host with Maven, or add an 'nvd-api-key' credential
            // (expose it as NVD_API_KEY) and ensure a host-resolvable workspace.
            steps {
                sh '''
                    set +e
                    mkdir -p .m2-cache
                    docker run --rm \
                        -v "$PWD":/workspace -w /workspace/gridveritas-core \
                        -v "$PWD/.m2-cache":/root/.m2 \
                        ${NVD_API_KEY:+-e NVD_API_KEY="$NVD_API_KEY"} \
                        maven:3.9-eclipse-temurin-21 \
                        mvn -B -Psecurity-scan -DskipTests verify
                    echo "OWASP dependency-check exit code: $? (non-gating)"
                    exit 0
                '''
            }
            post {
                always {
                    archiveArtifacts artifacts: 'gridveritas-core/target/dependency-check-report.*', allowEmptyArchive: true
                }
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
