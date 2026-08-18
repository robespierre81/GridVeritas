FROM postgres:16-alpine
COPY docker-entrypoint-ha.sh /usr/local/bin/docker-entrypoint-ha.sh
RUN chmod +x /usr/local/bin/docker-entrypoint-ha.sh
ENTRYPOINT ["docker-entrypoint-ha.sh"]
