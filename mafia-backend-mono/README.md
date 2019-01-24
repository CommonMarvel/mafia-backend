# Mafia-Backend

# gradle build fat jar
```bash
./gradlew build -x test
```

# gradle build docker and push to jianminhuang's docker hub
```bash
./gradlew buildDocker -x test
docker tag common-marvel/mafia-backend:0.0.1 jianminhuang/mafia-backend:0.0.1
docker push jianminhuang/mafia-backend:0.0.1
docker rmi $(docker images -f "dangling=true" -q)
```

# docker run
```bash
docker run -d -p 8080:8080 --privileged=true -e activeProfiles=local -e "TZ=Asia/Taipei" --name=mafia-backend jianminhuang/mafia-backend:0.0.1
```