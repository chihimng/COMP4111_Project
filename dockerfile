FROM openjdk:11-jre
COPY ./build/libs /usr/src/myapp
WORKDIR /usr/src/myapp
CMD ["java", "-jar", "comp4111.project-1.0-SNAPSHOT.jar"]
