{
  "env": {
    "DISABLE_AUTOUPDATER": "1",
    "ENABLE_TOOL_SEARCH": "true"
  },
  "attribution": {
    "commit": "",
    "pr": ""
  },
  "permissions": {
    "allow": [
      "Read(~/.claude)",
      "Read(//tmp/**)",
      "Bash(node:*)",
      "Bash(duckdb:*)"
    ],
    "defaultMode": "plan"
  },
  "model": "haiku",
  "hooks": {
    "Notification": [
      {
        "matcher": "*",
        "hooks": [
          {
            "type": "command",
            "command": "~/.claude/hooks/notify-sound.sh",
            "timeout": 5
          }
        ]
      }
    ]
  },
  "statusLine": {
    "type": "command",
    "command": "~/.claude/scripts/context-bar.sh"
  },
  "enabledPlugins": {
    "dx@ykdojo": true,
    "skill-creator@claude-plugins-official": true,
   
  },
  "extraKnownMarketplaces": {
    "ykdojo": {
      "source": {
        "source": "github",
        "repo": "ykdojo/claude-code-tips"
      }
    },
    "pm-skills": {
      "source": {
        "source": "github",
        "repo": "phuryn/pm-skills"
      }
    }
  },
  "language": "portugues br",
  "voiceEnabled": true,
  "skipDangerousModePermissionPrompt": true
}