# COMP4111 Project

## Running the stack locally with docker:

This script spins up a docker compose stack containing a mysql and a java runner instance.

Ports 8080 and 3306 are mapped from the host to respective containers for easier debug.

```sh
./run_stack.sh
```

To cleanup (remove all docker data storage and networks):

```sh
docker-compose rm -avs
```

## Database schema

See `schema.sql` for initializing schema and batch create user accounts.

Password column is hashed using SHA256 with UUID salt, command to recreate is `UNHEX(SHA2(CONCAT(password, salt), 256))`.
