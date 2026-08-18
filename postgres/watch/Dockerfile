FROM postgres:16-alpine
RUN apk add --no-cache socat
COPY watch.sh /usr/local/bin/watch.sh
RUN chmod +x /usr/local/bin/watch.sh
ENTRYPOINT ["/usr/local/bin/watch.sh"]
