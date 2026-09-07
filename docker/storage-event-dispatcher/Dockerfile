FROM alpine:3.21
RUN apk add --no-cache curl jq
WORKDIR /app
COPY dispatcher.sh /app/dispatcher.sh
RUN chmod +x /app/dispatcher.sh
ENTRYPOINT ["/app/dispatcher.sh"]

