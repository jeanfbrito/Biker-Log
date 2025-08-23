#!/bin/bash

# Script to create labels needed for project automation
# Run this script to set up the required labels in your repository

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== Creating Project Automation Labels ===${NC}\n"

# Function to create a label if it doesn't exist
create_label() {
    local name="$1"
    local color="$2"
    local description="$3"
    
    echo -e "${BLUE}Creating label: ${name}${NC}"
    
    # Check if label already exists
    if gh label list --json name --jq '.[].name' | grep -q "^${name}$"; then
        echo -e "${YELLOW}Label '${name}' already exists${NC}"
    else
        gh label create "${name}" --color "${color}" --description "${description}"
        echo -e "${GREEN}✓ Created label: ${name}${NC}"
    fi
    echo ""
}

# Check if gh CLI is authenticated
if ! gh auth status &> /dev/null; then
    echo -e "${RED}Error: Not authenticated with GitHub CLI${NC}"
    echo "Please run: gh auth login"
    exit 1
fi

echo -e "${GREEN}✓ GitHub CLI authenticated${NC}\n"

# Create labels needed for automation
create_label "in-progress-ai" "0052CC" "Issues currently being worked on by AI/automation"
create_label "needs-qa" "D93F0B" "Issues that need quality assurance testing"

# Optional: Create additional useful labels for project management
create_label "enhancement" "84B6EB" "New feature or enhancement request"
create_label "bug" "D73A4A" "Something isn't working as expected"
create_label "documentation" "0075CA" "Improvements or additions to documentation"
create_label "good first issue" "7057FF" "Good for newcomers to the project"

echo -e "${GREEN}=== Label creation completed! ===${NC}"
echo -e "${BLUE}Labels created for project automation:${NC}"
echo "• in-progress-ai - Moves issues to 'In Progress' column"
echo "• needs-qa - Moves issues to 'In Review' column"
echo ""
echo -e "${BLUE}Additional labels created:${NC}"
echo "• enhancement, bug, documentation, good first issue"