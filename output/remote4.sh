echo '--- server compose ---'
cat /root/huluwa-src/compose.yaml
echo '--- env ---'
cat /root/huluwa-src/.env 2>/dev/null
echo '--- ls ---'
ls -la /root/huluwa-src | head -20
echo '---END---'
