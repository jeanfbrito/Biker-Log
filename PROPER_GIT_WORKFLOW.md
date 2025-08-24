# Proper Git Workflow for Protected Main Branch

## 🚨 Current Issue
The main branch is currently **NOT PROTECTED**, allowing direct commits. This is a security and quality risk!

## ✅ Correct Workflow (After Protection)

### 1. Never Commit Directly to Main
```bash
# ❌ WRONG - This should be blocked:
git commit -m "fix: something"
git push origin main

# ✅ CORRECT - Use feature branches:
git checkout -b fix/issue-25-imu-optimization
git commit -m "fix(#25): Optimize IMU sampling rate"
git push origin fix/issue-25-imu-optimization
```

### 2. Create Pull Requests
```bash
# After pushing your branch:
gh pr create \
  --title "fix(#25): Optimize IMU sampling rate" \
  --body "Closes #25" \
  --base main
```

### 3. Required PR Process
1. **Automated Checks** must pass:
   - ✅ Build successful
   - ✅ Tests passing
   - ✅ Ktlint (code style)
   - ✅ Detekt (static analysis)
   
2. **Code Review** required:
   - At least 1 approval
   - All conversations resolved
   
3. **Merge Methods**:
   - Squash and merge (recommended)
   - Create merge commit
   - Rebase and merge

## 🔧 Setting Up Protection

### Quick Setup (Run Once)
```bash
./scripts/setup-branch-protection.sh
```

### Manual Setup via GitHub UI
1. Go to Settings → Branches
2. Add rule for `main`
3. Enable:
   - ✅ Require a pull request before merging
   - ✅ Require approvals (1)
   - ✅ Dismiss stale reviews
   - ✅ Require status checks
   - ✅ Require conversation resolution
   - ✅ Include administrators (optional but recommended)

## 📝 Example: How Issue #25 Should Have Been Done

### Step 1: Create Feature Branch
```bash
git checkout -b fix/issue-25-imu-optimization
```

### Step 2: Make Changes
```bash
# Edit files...
git add .
git commit -m "fix(#25): Optimize IMU sampling rate

- Changed default from 100Hz to 50Hz
- Added configurable settings
- Reduced file size by 60%

Closes #25"
```

### Step 3: Push Branch
```bash
git push origin fix/issue-25-imu-optimization
```

### Step 4: Create PR
```bash
gh pr create \
  --title "fix(#25): Optimize IMU sampling rate" \
  --body "## Changes
  - Optimized default IMU rate to 50Hz
  - Added UI for configuration
  - Added tests
  
  ## Results
  - 60% file size reduction
  - 40% battery savings
  
  Closes #25" \
  --assignee @me \
  --label "bug,P0-critical"
```

### Step 5: Review & Merge
1. Wait for CI checks ✅
2. Request review from team
3. Address feedback
4. Merge when approved

## 🤖 AI Agent Workflow

When using Claude or other AI agents:

### Configure Agent for PR Workflow
```bash
# Tell the agent to use branches:
"Work on issue #25 using a feature branch"
"Create a PR for issue #25"
```

### Agent Should:
1. Create branch: `git checkout -b fix/issue-25`
2. Make changes and commit
3. Push to branch (not main!)
4. Create PR with `gh pr create`
5. NOT merge without review

## 🛡️ Benefits of Protection

### Quality Assurance
- No broken builds in main
- All code is tested
- Consistent code style
- No accidental commits

### Collaboration
- Code reviews improve quality
- Knowledge sharing
- Catch bugs early
- Documentation in PRs

### Audit Trail
- Clear history of changes
- Link issues to PRs
- Reviewer accountability
- Rollback capability

## ⚠️ Emergency Override

If you absolutely must bypass (not recommended):

### As Admin (if enforce_admins is false):
```bash
git push origin main --force  # Still works for admins
```

### Better Alternative:
```bash
# Create emergency PR with expedited review:
gh pr create --title "EMERGENCY: Critical fix" --label "emergency"
# Get quick approval
# Merge immediately
```

## 📊 Monitoring Protection

### Check Status
```bash
# Is branch protected?
gh api repos/jeanfbrito/Biker-Log/branches/main/protection

# View protection rules
gh api repos/jeanfbrito/Biker-Log/branches/main/protection | jq
```

### View Recent PRs
```bash
gh pr list --state merged --limit 10
```

## 🎯 Action Items

1. **Immediately**: Run `./scripts/setup-branch-protection.sh`
2. **Going Forward**: Always use feature branches
3. **For AI Agents**: Configure to use PR workflow
4. **Team**: Document and enforce process

---

Remember: **Protected main = Professional development** 🚀