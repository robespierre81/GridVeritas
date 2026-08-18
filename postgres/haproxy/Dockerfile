FROM haproxy:2.9-alpine
USER root
RUN mkdir -p /ha && chmod 777 /ha
USER haproxy
COPY haproxy.cfg /usr/local/etc/haproxy/haproxy.cfg
