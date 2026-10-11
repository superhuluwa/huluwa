echo '--- huluwa net ---'
docker inspect huluwa-console --format '{{.HostConfig.NetworkMode}} ports={{json .NetworkSettings.Ports}}'
echo '--- find compose dirs ---'
ls -la /root/huluwa 2>/dev/null | head
find /root -maxdepth 3 -name 'compose.yaml' -o -maxdepth 3 -name 'docker-compose.yml' 2>/dev/null | head -20
echo '--- frpc container detail ---'
docker inspect frpc --format 'image={{.Config.Image}} restart={{.HostConfig.RestartPolicy.Name}} net={{.HostConfig.NetworkMode}} created={{.Created}}'
echo '--- frpc logs tail ---'
docker logs --tail 20 frpc 2>&1
echo '--- tools in frpc image ---'
docker exec frpc sh -c 'command -v sh; command -v nc; command -v ss; command -v netstat; command -v cat; cat /etc/os-release 2>/dev/null | head -3' 2>&1
echo '--- listening ports on host (ss) ---'
ss -ltnp 2>/dev/null | head -60
echo '---END---'
