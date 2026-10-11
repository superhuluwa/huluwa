uname -a
echo ---DOCKER-PS---
docker ps -a --format '{{.Names}} | {{.Image}} | {{.Status}}'
echo ---FRP-DIR---
ls -la /root/frp/ 2>/dev/null
echo ---FRPC-INI---
cat /root/frp/frpc.ini 2>/dev/null
echo ---FRPS-INI---
cat /root/frp/frps.ini 2>/dev/null
echo ---END---
