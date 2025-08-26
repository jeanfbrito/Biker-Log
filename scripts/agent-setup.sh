#!/bin/bash

# Agent Worktree Setup Script
# Automatically creates an isolated worktree for each agent session

set -e

# Load configuration
CONFIG_FILE=".agent-config.json"
if [ ! -f "$CONFIG_FILE" ]; then
    echo "Error: $CONFIG_FILE not found"
    exit 1
fi

# Parse config (using basic grep/sed for simplicity)
WORKTREE_BASE=$(grep '"worktree_base_path"' $CONFIG_FILE | sed 's/.*: *"\(.*\)".*/\1/')
BRANCH_PREFIX=$(grep '"branch_prefix"' $CONFIG_FILE | sed 's/.*: *"\(.*\)".*/\1/')
BASE_BRANCH=$(grep '"default_base_branch"' $CONFIG_FILE | sed 's/.*: *"\(.*\)".*/\1/')

# Generate unique agent ID
AGENT_ID="${AGENT_ID:-$(date +%Y%m%d-%H%M%S)-$$}"
BRANCH_NAME="${BRANCH_PREFIX}/${AGENT_ID}"
WORKTREE_PATH="${WORKTREE_BASE}/${AGENT_ID}"

echo "🤖 Setting up agent workspace..."
echo "   Agent ID: $AGENT_ID"
echo "   Branch: $BRANCH_NAME"
echo "   Worktree: $WORKTREE_PATH"

# Ensure we're on latest main
git fetch origin $BASE_BRANCH

# Create new branch and worktree
git branch $BRANCH_NAME origin/$BASE_BRANCH 2>/dev/null || true
git worktree add "$WORKTREE_PATH" $BRANCH_NAME

# Create agent session file
cat > "$WORKTREE_PATH/.agent-session" <<EOF
{
  "agent_id": "$AGENT_ID",
  "branch": "$BRANCH_NAME",
  "created": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "base_branch": "$BASE_BRANCH",
  "worktree_path": "$WORKTREE_PATH"
}
EOF

echo "✅ Agent workspace ready at: $WORKTREE_PATH"
echo ""
echo "To start working:"
echo "  cd $WORKTREE_PATH"
echo ""
echo "To clean up when done:"
echo "  ./scripts/agent-cleanup.sh $AGENT_ID"

# Export for use in shell
export AGENT_WORKTREE="$WORKTREE_PATH"
export AGENT_BRANCH="$BRANCH_NAME"

# Change to worktree directory
cd "$WORKTREE_PATH"