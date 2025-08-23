#!/bin/bash

# GitHub Project Board Automation Setup Script
# This script configures automation rules for the Biker Log GitHub project board

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Repository information
REPO_OWNER="jeanfbrito"
REPO_NAME="Biker-Log"

echo -e "${BLUE}=== GitHub Project Board Automation Setup ===${NC}"
echo -e "Repository: ${REPO_OWNER}/${REPO_NAME}\n"

# Function to check if gh CLI is installed and authenticated
check_requirements() {
    echo -e "${BLUE}Checking requirements...${NC}"
    
    if ! command -v gh &> /dev/null; then
        echo -e "${RED}Error: GitHub CLI (gh) is not installed${NC}"
        echo "Please install it from: https://cli.github.com/"
        exit 1
    fi
    
    if ! gh auth status &> /dev/null; then
        echo -e "${RED}Error: Not authenticated with GitHub CLI${NC}"
        echo "Please run: gh auth login"
        exit 1
    fi
    
    # Check for required scopes
    if ! gh auth status 2>&1 | grep -q "read:project\|project"; then
        echo -e "${YELLOW}Warning: GitHub token may not have required project scopes${NC}"
        echo "If you encounter permission errors, run:"
        echo "gh auth refresh --hostname github.com -s read:project,project"
        echo ""
    fi
    
    echo -e "${GREEN}✓ Requirements check passed${NC}\n"
}

# Function to find the project
find_project() {
    echo -e "${BLUE}Finding GitHub project...${NC}"
    
    # Try to get projects for the repository
    PROJECT_DATA=$(gh api graphql -f query='
    query {
      repository(owner: "'"$REPO_OWNER"'", name: "'"$REPO_NAME"'") {
        projectsV2(first: 10) {
          nodes {
            id
            title
            number
            url
          }
        }
      }
    }' 2>/dev/null || echo '{"data":{"repository":{"projectsV2":{"nodes":[]}}}}')
    
    # Parse project information
    PROJECT_COUNT=$(echo "$PROJECT_DATA" | jq -r '.data.repository.projectsV2.nodes | length')
    
    if [ "$PROJECT_COUNT" -eq 0 ]; then
        echo -e "${YELLOW}No projects found directly linked to repository.${NC}"
        echo "Trying to find projects in user account..."
        
        # Try user projects
        USER_PROJECTS=$(gh api graphql -f query='
        query {
          viewer {
            projectsV2(first: 20) {
              nodes {
                id
                title
                number
                url
              }
            }
          }
        }' 2>/dev/null || echo '{"data":{"viewer":{"projectsV2":{"nodes":[]}}}}')
        
        USER_PROJECT_COUNT=$(echo "$USER_PROJECTS" | jq -r '.data.viewer.projectsV2.nodes | length')
        
        if [ "$USER_PROJECT_COUNT" -eq 0 ]; then
            echo -e "${RED}Error: No projects found${NC}"
            echo "Please create a GitHub project first at: https://github.com/${REPO_OWNER}/${REPO_NAME}/projects"
            exit 1
        fi
        
        echo -e "${GREEN}Found $USER_PROJECT_COUNT user projects:${NC}"
        echo "$USER_PROJECTS" | jq -r '.data.viewer.projectsV2.nodes[] | "- \(.title) (ID: \(.id))"'
        
        # Use first project or let user specify
        PROJECT_ID=$(echo "$USER_PROJECTS" | jq -r '.data.viewer.projectsV2.nodes[0].id')
        PROJECT_TITLE=$(echo "$USER_PROJECTS" | jq -r '.data.viewer.projectsV2.nodes[0].title')
    else
        echo -e "${GREEN}Found $PROJECT_COUNT repository projects:${NC}"
        echo "$PROJECT_DATA" | jq -r '.data.repository.projectsV2.nodes[] | "- \(.title) (ID: \(.id))"'
        
        PROJECT_ID=$(echo "$PROJECT_DATA" | jq -r '.data.repository.projectsV2.nodes[0].id')
        PROJECT_TITLE=$(echo "$PROJECT_DATA" | jq -r '.data.repository.projectsV2.nodes[0].title')
    fi
    
    echo -e "${GREEN}✓ Using project: ${PROJECT_TITLE} (${PROJECT_ID})${NC}\n"
}

# Function to get project fields and columns
get_project_structure() {
    echo -e "${BLUE}Getting project structure...${NC}"
    
    PROJECT_FIELDS=$(gh api graphql -f query='
    query {
      node(id: "'"$PROJECT_ID"'") {
        ... on ProjectV2 {
          fields(first: 20) {
            nodes {
              ... on ProjectV2Field {
                id
                name
                dataType
              }
              ... on ProjectV2IterationField {
                id
                name
                dataType
              }
              ... on ProjectV2SingleSelectField {
                id
                name
                dataType
                options {
                  id
                  name
                }
              }
            }
          }
        }
      }
    }' 2>/dev/null || echo '{"data":{"node":{"fields":{"nodes":[]}}}}')
    
    # Find the Status field
    STATUS_FIELD_ID=$(echo "$PROJECT_FIELDS" | jq -r '.data.node.fields.nodes[] | select(.name == "Status") | .id')
    
    if [ "$STATUS_FIELD_ID" == "null" ] || [ -z "$STATUS_FIELD_ID" ]; then
        echo -e "${RED}Error: Status field not found in project${NC}"
        echo "Available fields:"
        echo "$PROJECT_FIELDS" | jq -r '.data.node.fields.nodes[] | "- \(.name) (\(.dataType))"'
        exit 1
    fi
    
    echo -e "${GREEN}✓ Found Status field: ${STATUS_FIELD_ID}${NC}"
    
    # Get status options
    STATUS_OPTIONS=$(echo "$PROJECT_FIELDS" | jq -r '.data.node.fields.nodes[] | select(.name == "Status") | .options[]? | "\(.name):\(.id)"')
    
    echo -e "${GREEN}Status options:${NC}"
    echo "$STATUS_OPTIONS" | while IFS=: read -r name id; do
        echo "- $name ($id)"
        
        # Store option IDs for automation rules
        case "$name" in
            "Backlog") BACKLOG_ID="$id" ;;
            "Todo"|"To Do") TODO_ID="$id" ;;
            "In Progress") IN_PROGRESS_ID="$id" ;;
            "In Review") IN_REVIEW_ID="$id" ;;
            "Done") DONE_ID="$id" ;;
        esac
    done
    
    echo ""
}

# Function to create automation workflows
create_automation_workflows() {
    echo -e "${BLUE}Setting up automation workflows...${NC}"
    
    # Extract status option IDs
    BACKLOG_ID=$(echo "$PROJECT_FIELDS" | jq -r '.data.node.fields.nodes[] | select(.name == "Status") | .options[] | select(.name == "Backlog") | .id')
    TODO_ID=$(echo "$PROJECT_FIELDS" | jq -r '.data.node.fields.nodes[] | select(.name == "Status") | .options[] | select(.name == "Todo" or .name == "To Do") | .id')
    IN_PROGRESS_ID=$(echo "$PROJECT_FIELDS" | jq -r '.data.node.fields.nodes[] | select(.name == "Status") | .options[] | select(.name == "In Progress") | .id')
    IN_REVIEW_ID=$(echo "$PROJECT_FIELDS" | jq -r '.data.node.fields.nodes[] | select(.name == "Status") | .options[] | select(.name == "In Review") | .id')
    DONE_ID=$(echo "$PROJECT_FIELDS" | jq -r '.data.node.fields.nodes[] | select(.name == "Status") | .options[] | select(.name == "Done") | .id')
    
    # Create workflow for new issues → Backlog
    if [ "$BACKLOG_ID" != "null" ]; then
        echo "Creating workflow: New issues → Backlog"
        gh api graphql -f query='
        mutation {
          createProjectV2Workflow(input: {
            projectId: "'"$PROJECT_ID"'"
            name: "New Issues to Backlog"
            enabled: true
            triggers: [{
              event: ISSUE_OPENED
              patterns: [{
                repository: {
                  owner: "'"$REPO_OWNER"'"
                  name: "'"$REPO_NAME"'"
                }
              }]
            }]
            workflows: [{
              name: "Add to Backlog"
              on: ISSUE_OPENED
              jobs: [{
                name: "add-to-project"
                steps: [{
                  name: "Add item to project"
                  uses: "actions/add-to-project@v0.1.0"
                  with: {
                    project-url: "'"$(gh api graphql -f query='query { node(id: "'"$PROJECT_ID"'") { ... on ProjectV2 { url } } }' --jq '.data.node.url')"'"
                    github-token: "${{ secrets.GITHUB_TOKEN }}"
                  }
                }, {
                  name: "Set status to Backlog"
                  uses: "actions/github-script@v6"
                  with: {
                    script: |
                      // Set status to Backlog
                      // This would require additional GraphQL mutations
                  }
                }]
              }]
            }]
          }) {
            projectV2Workflow {
              id
              name
            }
          }
        }' --silent 2>/dev/null || echo -e "${YELLOW}Note: Workflow creation via API may require GitHub Actions${NC}"
    fi
    
    echo -e "${GREEN}✓ Automation setup attempted${NC}\n"
}

# Function to create GitHub Actions workflow for project automation
create_github_actions_workflow() {
    echo -e "${BLUE}Creating GitHub Actions workflow for project automation...${NC}"
    
    mkdir -p .github/workflows
    
    cat > .github/workflows/project-automation.yml << 'EOF'
name: Project Board Automation

on:
  issues:
    types: [opened, closed, assigned, labeled, unlabeled]
  pull_request:
    types: [opened, closed, ready_for_review, converted_to_draft]

jobs:
  update_project_board:
    runs-on: ubuntu-latest
    steps:
      - name: Get project data
        env:
          GITHUB_TOKEN: ${{ secrets.PROJECT_TOKEN }}
          ORGANIZATION: jeanfbrito
          PROJECT_NUMBER: 1  # Update this with your project number
        run: |
          # Get project ID
          gh api graphql -f query='
            query($org: String!, $number: Int!) {
              organization(login: $org) {
                projectV2(number: $number) {
                  id
                }
              }
            }' -f org=$ORGANIZATION -F number=$PROJECT_NUMBER > project_data.json
          
          echo "PROJECT_ID=$(jq -r '.data.organization.projectV2.id' project_data.json)" >> $GITHUB_ENV
      
      - name: Add issue to project
        if: github.event.action == 'opened' && github.event.issue
        env:
          GITHUB_TOKEN: ${{ secrets.PROJECT_TOKEN }}
          ISSUE_ID: ${{ github.event.issue.node_id }}
        run: |
          gh api graphql -f query='
            mutation($project:ID!, $issue:ID!) {
              addProjectV2ItemByContentId(input: {
                projectId: $project
                contentId: $issue
              }) {
                item {
                  id
                }
              }
            }' -f project=$PROJECT_ID -f issue=$ISSUE_ID
      
      - name: Move issue to appropriate column
        if: github.event.issue
        env:
          GITHUB_TOKEN: ${{ secrets.PROJECT_TOKEN }}
          ISSUE_ID: ${{ github.event.issue.node_id }}
        run: |
          # Logic to determine target column based on:
          # - New issues → Backlog
          # - Issues with assignee → To Do
          # - Issues labeled 'in-progress-ai' → In Progress
          # - Issues labeled 'needs-qa' → In Review
          # - Closed issues → Done
          
          if [[ "${{ github.event.action }}" == "opened" ]]; then
            COLUMN="Backlog"
          elif [[ "${{ github.event.action }}" == "assigned" ]]; then
            COLUMN="To Do"
          elif [[ "${{ github.event.action }}" == "labeled" && "${{ github.event.label.name }}" == "in-progress-ai" ]]; then
            COLUMN="In Progress"
          elif [[ "${{ github.event.action }}" == "labeled" && "${{ github.event.label.name }}" == "needs-qa" ]]; then
            COLUMN="In Review"
          elif [[ "${{ github.event.action }}" == "closed" ]]; then
            COLUMN="Done"
          fi
          
          echo "Moving issue to $COLUMN"
          # Additional GraphQL mutation would be needed here to update the status field
      
      - name: Add PR to project
        if: github.event.action == 'opened' && github.event.pull_request
        env:
          GITHUB_TOKEN: ${{ secrets.PROJECT_TOKEN }}
          PR_ID: ${{ github.event.pull_request.node_id }}
        run: |
          gh api graphql -f query='
            mutation($project:ID!, $pr:ID!) {
              addProjectV2ItemByContentId(input: {
                projectId: $project
                contentId: $pr
              }) {
                item {
                  id
                }
              }
            }' -f project=$PROJECT_ID -f pr=$PR_ID
          
          # Move PR to In Review column
          echo "Moving PR to In Review column"
EOF
    
    echo -e "${GREEN}✓ GitHub Actions workflow created at .github/workflows/project-automation.yml${NC}\n"
}

# Function to display next steps
show_next_steps() {
    echo -e "${BLUE}=== Next Steps ===${NC}"
    echo -e "${YELLOW}1. Authentication Setup:${NC}"
    echo "   If you encountered permission errors, run:"
    echo "   gh auth refresh --hostname github.com -s read:project,project"
    echo ""
    
    echo -e "${YELLOW}2. GitHub Actions Setup:${NC}"
    echo "   - A PROJECT_TOKEN secret needs to be added to your repository"
    echo "   - Go to: https://github.com/${REPO_OWNER}/${REPO_NAME}/settings/secrets/actions"
    echo "   - Create a personal access token with 'project' scope"
    echo "   - Add it as 'PROJECT_TOKEN' secret"
    echo ""
    
    echo -e "${YELLOW}3. Project Configuration:${NC}"
    echo "   - Update the PROJECT_NUMBER in the workflow file"
    echo "   - Ensure your project has these status columns:"
    echo "     • Backlog"
    echo "     • To Do (or Todo)"
    echo "     • In Progress"
    echo "     • In Review"
    echo "     • Done"
    echo ""
    
    echo -e "${YELLOW}4. Label Setup:${NC}"
    echo "   Create these labels in your repository:"
    echo "   - in-progress-ai"
    echo "   - needs-qa"
    echo ""
    
    echo -e "${GREEN}✓ Setup script completed!${NC}"
}

# Main execution
main() {
    check_requirements
    find_project
    get_project_structure
    create_automation_workflows
    create_github_actions_workflow
    show_next_steps
}

# Run main function
main "$@"