#!/bin/bash

# Agent Worktree Cleanup Script
# Safely removes agent worktree and optionally merges changes

set -e

AGENT_ID=$1
if [ -z "$AGENT_ID" ]; then
    echo "Usage: $0 <agent-id>"
    echo "Available agents:"
    git worktree list | grep -E "agent/.*" || echo "  (none)"
    exit 1
fi

# Load configuration
CONFIG_FILE=".agent-config.json"
WORKTREE_BASE=$(grep '"worktree_base_path"' $CONFIG_FILE | sed 's/.*: *"\(.*\)".*/\1/')
BRANCH_PREFIX=$(grep '"branch_prefix"' $CONFIG_FILE | sed 's/.*: *"\(.*\)".*/\1/')

BRANCH_NAME="${BRANCH_PREFIX}/${AGENT_ID}"
WORKTREE_PATH="${WORKTREE_BASE}/${AGENT_ID}"

echo "🧹 Cleaning up agent workspace..."
echo "   Agent ID: $AGENT_ID"
echo "   Branch: $BRANCH_NAME"
echo "   Worktree: $WORKTREE_PATH"

# Check if worktree exists
if [ ! -d "$WORKTREE_PATH" ]; then
    echo "Error: Worktree not found at $WORKTREE_PATH"
    exit 1
fi

# Optional: Check for uncommitted changes
cd "$WORKTREE_PATH"
if ! git diff --quiet || ! git diff --staged --quiet; then
    echo "⚠️  Warning: Uncommitted changes detected!"
    read -p "Do you want to commit them? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        git add -A
        git commit -m "Auto-commit: Agent $AGENT_ID cleanup $(date +%Y-%m-%d)"
    fi
fi

# Return to main repo
cd - > /dev/null

# Remove worktree
echo "Removing worktree..."
git worktree remove "$WORKTREE_PATH" --force

# Optional: Delete branch
read -p "Delete branch $BRANCH_NAME? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    git branch -D "$BRANCH_NAME" 2>/dev/null || true
    echo "✅ Branch deleted"
fi

echo "✅ Agent workspace cleaned up"