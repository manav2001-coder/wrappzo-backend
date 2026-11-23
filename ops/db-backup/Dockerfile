# PostgreSQL 18 client (prod runs 18.x; pg_dump must be at least the server's version) + the S3 command line for R2.
FROM postgres:18-alpine
RUN apk add --no-cache aws-cli
COPY backup.sh /backup.sh
RUN chmod +x /backup.sh
ENTRYPOINT ["/backup.sh"]
