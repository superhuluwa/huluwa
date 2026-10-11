echo '--- frpc container inspect ---'
docker inspect frpc --format '{{json .Config.Cmd}} {{json .HostConfig.Binds}} {{json .HostConfig.NetworkMode}}'
echo '--- frpc-caiji inspect ---'
docker inspect frpc-caiji --format '{{json .Config.Cmd}} {{json .HostConfig.Binds}} {{json .HostConfig.NetworkMode}}'
echo '--- huluwa container mounts ---'
docker inspect huluwa-console --format '{{json .Mounts}}'
echo '--- huluwa user/env ---'
docker inspect huluwa-console --format '{{.Config.User}} :: {{json .Config.Env}}'
echo '--- is there frp panel/dashboard ---'
docker ps --format '{{.Names}} {{.Ports}}' | grep -Ei 'frp|panel' || true
echo '--- which docker in huluwa ---'
docker exec huluwa-console sh -c 'id; which docker || echo NO_DOCKER_CLI; ls -la /app; ls -la /app/data | head -20' 2>&1 | head -40
echo '---END---'
