# throw-voice
> A voice channel recording bot for Discord.

[![Build Status](https://travis-ci.org/guacamoledragon/throw-voice.svg?branch=master)](https://travis-ci.org/guacamoledragon/throw-voice)
[![Coverage Status](https://coveralls.io/repos/github/guacamoledragon/throw-voice/badge.svg)](https://coveralls.io/github/guacamoledragon/throw-voice)
[![Docker Pulls](https://img.shields.io/docker/pulls/gdragon/throw-voice.svg)](https://hub.docker.com/r/gdragon/throw-voice/)

**Full Disclosure: This is a highly modified fork of [ajm1996/DiscordEcho](https://github.com/ajm1996/DiscordEcho).**

# Supported tags and respective `Dockerfile` links

- [`1.0-beta.1`, `latest` (*Dockerfile*)]()

# How to use this image

The following environment variables are necessary:

  - `PORT`: Port on which the bot will run it's HTTP server on, strictly speaking not necessary,
  but it's convenient.
  - `CLIENT_ID`: Your Discord App Client ID
  - `BOT_TOKEN`: Your Discord App's App Bot User Token (what a mouthful!)

The container can be run in interactive mode like so:

    docker run -it -e PORT=8080 -e CLIENT_ID=... -e BOT_TOKEN=... -p 8080:8080 gdragon/throw-voice
    
Or in the background:

    docker run -d -e PORT=8080 -e CLIENT_ID=... -e BOT_TOKEN=... -p 8080:8080 gdragon/throw-voice

The bot will automatically connect to your Guild if it has connected before, otherwise visit `http://localhost:8080` to
add to your Guild.

## Attributions

- Japanese Dragon icon made by [Freepik](http://www.freepik.com) from [www.flaticon.com](http://www.flaticon.com) is licensed by [CC 3.0 BY](http://creativecommons.org/licenses/by/3.0/)
- Original codebase by [ajm1996](https://github.com/ajm1996)
