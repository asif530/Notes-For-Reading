sudo pkill -f docker-proxy
When ports are still bound even when the container is down.

Down all the running container with volume
docker-compose down -v

https://docs.docker.com/reference/cli/docker/container/prune/

Run a docker file with specific name (https://stackoverflow.com/questions/48717646/docker-compose-down-with-a-non-default-yml-file-name)
docker-compose -f <file-name> up -d
Example:
 file-name = docker-compose-multiple-database.yml
 docker-compose -f docker-compose-multiple-database.yml up -d

Remove all the containers
docker stop $(docker ps -a -q)

To stop all running Docker containers
docker stop $(docker ps -q)

Remove all containers
docker rm $(docker ps -a -q)

docker exec -it <container-name> env
docker logs -f <container-name>
docker build -t fas . --no-cache
docker rmi fas
docker system prune -a
docker stop $(docker ps -q)
docker ps -o (-a => all. -o=> ?)
docker images -o (-a => all. -o=> ?)


