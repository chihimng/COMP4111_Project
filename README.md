# COMP4111 Project: RESTful Web API in Java

Ng Chi Him 20420921 chngax@connect.ust.hk

Wong Hiu Nam (sid) hnwongab@connect.ust.hk

Project Spec: [Link](https://course.cse.ust.hk/comp4111/project.html)

Test Cases: [Link](https://course.cse.ust.hk/comp4111/test_cases.html)

## Running the stack locally with docker

> Prerequisite: Docker and Docker Compose already installed

Demo Video: [Link](https://drive.google.com/open?id=1e3nWmrLkJCzyXsRoxVB4x3LUnt7ImoVm)

This script spins up a docker compose stack containing a mysql and a java runner instance.

Remember to initialize database schema (`schema.sql`) with a database client (e.g. DataGrip) before testing.

Database `comp4111` is auto-created on first launch. If db is missing please try to clean docker data and try again.

Database Account: username `comp4111`, password `comp4111`

Ports 8080 and 3306 are mapped to the host for easy debug / testing.

```sh
./run_stack.sh
```

To cleanup (remove all docker data storage and networks):

```sh
docker-compose rm -avs
```

## Running the stack on bare metal

> Prerequisite: Install Java 11 and MySQL 5.7, with database already configured

Set the following in environment variables when running the api server:

(update values as needed)

```sh
PORT=80
DB_URL="mysql://db:3306/comp4111?user=comp4111&password=comp4111&useSSL=false"
```

## Database schema

See `schema.sql` for initializing schema and batch create user accounts.

Password column is hashed using SHA256 with UUID salt, command to recreate is `UNHEX(SHA2(CONCAT(password, salt), 256))`.
