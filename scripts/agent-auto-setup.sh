#!/bin/bash

# Auto Setup for Claude Code Agents
# This script automatically manages worktrees for each Claude session

set -e

# Detect if we're already in a worktree
if git rev-parse --git-dir 2>/dev/null | grep -q "\.git/worktrees"; then
    echo "✅ Already in a worktree, ready to work!"
    exit 0
fi

# Check if this is a new Claude session (no AGENT_ID set)
if [ -z "$CLAUDE_AGENT_ID" ]; then
    # Generate unique ID for this session
    export CLAUDE_AGENT_ID="claude-$(date +%Y%m%d-%H%M%S)"
    
    # Create worktree for this agent
    echo "🤖 Setting up dedicated workspace for Claude agent: $CLAUDE_AGENT_ID"
    
    # Use the setup script with the generated ID
    AGENT_ID="$CLAUDE_AGENT_ID" source ./scripts/agent-setup.sh
    
    echo ""
    echo "📝 Add this to your CLAUDE.md to auto-run:"
    echo "   export CLAUDE_AGENT_ID=$CLAUDE_AGENT_ID"
    echo "   source ./scripts/agent-auto-setup.sh"
fi