#!/bin/bash

# Script to check the current status of GitHub project automation setup
# This helps verify that all components are properly configured

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== GitHub Project Automation Status Check ===${NC}\n"

# Check GitHub CLI authentication
echo -e "${BLUE}1. GitHub CLI Authentication:${NC}"
if gh auth status &> /dev/null; then
    echo -e "${GREEN}✓ Authenticated with GitHub CLI${NC}"
    gh auth status | grep -E "(Logged in|Token scopes)" | sed 's/^/  /'
else
    echo -e "${RED}✗ Not authenticated with GitHub CLI${NC}"
    echo "  Please run: gh auth login"
fi
echo ""

# Check for project automation workflow
echo -e "${BLUE}2. GitHub Actions Workflow:${NC}"
if [[ -f ".github/workflows/project-automation.yml" ]]; then
    echo -e "${GREEN}✓ Project automation workflow exists${NC}"
    echo "  File: .github/workflows/project-automation.yml"
else
    echo -e "${RED}✗ Project automation workflow not found${NC}"
    echo "  Expected: .github/workflows/project-automation.yml"
fi
echo ""

# Check for required labels
echo -e "${BLUE}3. Repository Labels:${NC}"
if gh auth status &> /dev/null; then
    required_labels=("in-progress-ai" "needs-qa")
    
    for label in "${required_labels[@]}"; do
        if gh label list --json name --jq '.[].name' | grep -q "^${label}$"; then
            echo -e "${GREEN}✓ Label '${label}' exists${NC}"
        else
            echo -e "${RED}✗ Label '${label}' missing${NC}"
            echo "  Run: ./create-labels.sh to create missing labels"
        fi
    done
else
    echo -e "${YELLOW}? Cannot check labels (authentication required)${NC}"
fi
echo ""

# Check for documentation files
echo -e "${BLUE}4. Documentation:${NC}"
docs=("PROJECT_AUTOMATION_SETUP.md" "setup-github-project-automation.sh" "create-labels.sh")

for doc in "${docs[@]}"; do
    if [[ -f "$doc" ]]; then
        echo -e "${GREEN}✓ ${doc} exists${NC}"
    else
        echo -e "${RED}✗ ${doc} missing${NC}"
    fi
done
echo ""

# Check repository secrets (this requires API access)
echo -e "${BLUE}5. Repository Configuration:${NC}"
echo -e "${YELLOW}! Manual verification required:${NC}"
echo "  1. PROJECT_TOKEN secret configured in repository settings"
echo "  2. GitHub project created and linked to repository"
echo "  3. Project has required status columns:"
echo "     • Backlog"
echo "     • To Do (or Todo)"
echo "     • In Progress"
echo "     • In Review"
echo "     • Done"
echo ""

# Show next steps
echo -e "${BLUE}6. Next Steps:${NC}"
echo ""

# Check if PROJECT_TOKEN can be verified indirectly
if gh auth status &> /dev/null; then
    # Try to check if we can access projects (indirect check for token scope)
    if gh api graphql -f query='query { viewer { login } }' &> /dev/null; then
        echo -e "${GREEN}✓ Basic API access working${NC}"
        echo "  Next: Verify PROJECT_TOKEN secret has 'project' scope"
    else
        echo -e "${RED}✗ API access issues detected${NC}"
        echo "  Check authentication and token permissions"
    fi
fi

echo ""
echo -e "${YELLOW}Required manual steps:${NC}"
echo "1. Go to: https://github.com/jeanfbrito/Biker-Log/settings/secrets/actions"
echo "2. Create PROJECT_TOKEN secret with a personal access token"
echo "3. Ensure the token has 'project' scope permissions"
echo "4. Update project URL in .github/workflows/project-automation.yml"
echo "5. Test automation by creating an issue or PR"
echo ""

echo -e "${BLUE}=== Status Check Complete ===${NC}"