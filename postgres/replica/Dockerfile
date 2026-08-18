FROM postgres:16-alpine
COPY docker-entrypoint-replica.sh /usr/local/bin/docker-entrypoint-replica.sh
RUN chmod +x /usr/local/bin/docker-entrypoint-replica.sh
ENTRYPOINT ["docker-entrypoint-replica.sh"]
