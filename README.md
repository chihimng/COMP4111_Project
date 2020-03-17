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
