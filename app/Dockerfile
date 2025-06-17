FROM openjdk:21

COPY build/distributions/app.zip app.zip
RUN echo `unzip app.zip && cd app`
ENTRYPOINT ["sh", "./bin/app"]