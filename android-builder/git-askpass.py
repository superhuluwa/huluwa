#!/usr/bin/env python3
import json
import os
from pathlib import Path
import re
import sys
from urllib.parse import urlsplit

prompt = ' '.join(sys.argv[1:])
credential_path = Path('/credentials/git-credentials.json')
if credential_path.is_file():
    credential = json.loads(credential_path.read_text(encoding='utf-8'))
    url = re.search(r"https://[^'\s]+", prompt)
    parsed = urlsplit(url.group(0)) if url else None
    # Never offer a saved Gitee password to another Git host or to plain HTTP.
    if not parsed or parsed.hostname != credential.get('host') or parsed.scheme != 'https':
        raise SystemExit(1)
    print(credential['username'] if 'username' in prompt.lower() else credential['password'])
else:
    token = Path('/credentials/git-token')
    if not token.is_file():
        raise SystemExit(1)
    print(os.environ.get('HULUWA_GIT_USERNAME', 'oauth2') if 'username' in prompt.lower() else token.read_text().strip())
