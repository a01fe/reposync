#!/usr/bin/env bash

# Entrypoint script for reposync application

# Gradle build tasks do property expansion so dollar signs and backslashes
# intended for the shell must be quoted.

umask 077

# Set up SSH key
if [ -n "\$SSH_PRIVATE_KEY" ]; then
  eval "\$(ssh-agent -s)" > /dev/null
  mkfifo keyfile
  chmod 600 keyfile
  echo "\$SSH_PRIVATE_KEY" > keyfile &
  ssh-add keyfile
  rm keyfile
else
  echo "SSH_PRIVATE_KEY environment variable not set"
  exit 1
fi

# Set up SSH host keys
if [ -n "\$SSH_HOST_KEYS" ]; then
  mkdir -p ~/.ssh
  echo "\$SSH_HOST_KEYS" | base64 -d > ~/.ssh/known_hosts
fi

# Run application
exec "${docker.reposyncApplication.name.get()}" "\$@"
