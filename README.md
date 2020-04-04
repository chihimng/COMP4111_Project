# COMP4111 Project

Ng Chi Him 20420921 chngax@connect.ust.hk
Wong Hiu Nam (sid) hnwongab@connect.ust.hk

Project Spec: [Link](https://course.cse.ust.hk/comp4111/project.html)

## Running the stack locally with docker

This script spins up a docker compose stack containing a mysql and a java runner instance.

Ports 8080 and 3306 are mapped from the host to respective containers for easier debug.

```sh
./run_stack.sh
```

To cleanup (remove all docker data storage and networks):

```sh
docker-compose rm -avs
```

## Running the stack on bare metal

Set the following in environment variables when running the api server:

```sh
PORT=80
DB_URL="mysql://db:3306/comp4111?user=comp4111&password=comp4111&useSSL=false"
```

## Database schema

See `schema.sql` for initializing schema and batch create user accounts.

Password column is hashed using SHA256 with UUID salt, command to recreate is `UNHEX(SHA2(CONCAT(password, salt), 256))`.
