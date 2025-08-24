#!/bin/bash

# Script to set up branch protection rules for the main branch
# This ensures code quality and prevents direct commits

echo "🔒 Setting up branch protection for main branch..."
echo "============================================="

# Enable branch protection with comprehensive rules
gh api \
  --method PUT \
  -H "Accept: application/vnd.github+json" \
  /repos/jeanfbrito/Biker-Log/branches/main/protection \
  --input - <<EOF
{
  "required_status_checks": {
    "strict": true,
    "contexts": [
      "build",
      "test",
      "ktlint",
      "detekt"
    ]
  },
  "enforce_admins": false,
  "required_pull_request_reviews": {
    "dismissal_restrictions": {},
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": false,
    "required_approving_review_count": 1,
    "require_last_push_approval": false
  },
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "block_creations": false,
  "required_conversation_resolution": true,
  "lock_branch": false,
  "allow_fork_syncing": false
}
EOF

if [ $? -eq 0 ]; then
    echo "✅ Branch protection successfully enabled!"
    echo ""
    echo "Protection rules applied:"
    echo "========================"
    echo "✓ Require pull request before merging"
    echo "✓ Require 1 approval review"
    echo "✓ Dismiss stale reviews on new commits"
    echo "✓ Require status checks to pass (build, test, ktlint, detekt)"
    echo "✓ Require branches to be up to date"
    echo "✓ Require conversation resolution"
    echo "✓ No force pushes allowed"
    echo "✓ No branch deletion allowed"
    echo ""
    echo "⚠️  Note: Admins can still bypass (enforce_admins: false)"
    echo "    Set to true for maximum protection"
else
    echo "❌ Failed to enable branch protection"
    echo "   You may need admin permissions or a paid GitHub plan"
fi

echo ""
echo "To verify protection status:"
echo "gh api repos/jeanfbrito/Biker-Log/branches/main/protection"