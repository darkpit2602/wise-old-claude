#!/bin/sh
#
# SessionStart hook: the MCP server runs on the user's own Node.js, and when that is missing or too old the server
# simply fails to start, which Claude Code does not explain. This check says so in a message the user sees, and tells
# Claude the same so it can answer "why doesn't the OSRS plugin work?".
#
# It must not need Node itself, so it is plain POSIX sh using only shell builtins. On Windows, Claude Code runs hooks
# in Git Bash, which every user has: adding a GitHub marketplace needs git.

required_major=20
required_minor=11

version=$(node --version 2>/dev/null)
numbers=${version#v}
major=${numbers%%.*}
rest=${numbers#*.}
minor=${rest%%.*}

case "$major$minor" in
	'' | *[!0-9]*) ;;
	*)
		if [ "$major" -gt "$required_major" ] ||
			{ [ "$major" -eq "$required_major" ] && [ "$minor" -ge "$required_minor" ]; }; then
			exit 0
		fi
		;;
esac

case "$version" in
	'') found="not installed" ;;
	*[!v0-9.]*) found="an unrecognised version" ;;
	*) found="version $version" ;;
esac

printf '%s\n' "{\"systemMessage\":\"Wise Old Claude needs Node.js $required_major.$required_minor or newer to start (found: $found). Install it from https://nodejs.org, then restart Claude Code.\",\"hookSpecificOutput\":{\"hookEventName\":\"SessionStart\",\"additionalContext\":\"The wise-old-claude MCP server cannot start: it needs Node.js $required_major.$required_minor or newer and found $found. If the user asks about Old School RuneScape or this plugin, tell them to install Node.js from https://nodejs.org and restart Claude Code.\"}}"
