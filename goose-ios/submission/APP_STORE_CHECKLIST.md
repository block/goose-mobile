# App Store Submission Checklist - Goose iOS

## Status: Ready for Unlisted App Store Submission ✅

This checklist covers everything needed to move from TestFlight to an unlisted App Store release.

---

## ✅ Already Complete

### 1. App Icons & Assets
- ✅ All app icon sizes present (20pt-1024pt)
- ✅ App Store 1024x1024 icon included
- ✅ Launch screen configured (SwiftUI app)

### 2. Privacy & Permissions
- ✅ Microphone usage description (voice input)
- ✅ Speech recognition usage description
- ✅ Export compliance set (`ITSAppUsesNonExemptEncryption = false`)
- ✅ App Transport Security configured for local networking

### 3. Project Configuration
- ✅ Bundle ID: `com.goose.chat`
- ✅ Version: 1.1.1
- ✅ Build number: 1
- ✅ Code signing configured
- ✅ TestFlight already working

### 4. Code Quality
- ✅ No third-party analytics/tracking
- ✅ Pure SwiftUI (no storyboards/xibs)
- ✅ Standard Apple frameworks only
- ✅ Trial mode/demo server functional

---

## 📋 Required Before Submission

### 1. App Store Connect Metadata (Required)

#### App Information
- [ ] **App Name** (30 chars max)
  - Current: "Goose" or "Goose Chat"
  - Recommendation: Keep it short and clear
  
- [ ] **Subtitle** (30 chars max)
  - Example: "AI Assistant Client"
  - This appears under app name in search

- [ ] **Description** (4000 chars max)
  - Explain what Goose does
  - Mention it connects to goosed server
  - Highlight key features (voice input, markdown, tool calls)
  - Note: Can be updated anytime without new build

- [ ] **Keywords** (100 chars max, comma-separated)
  - Example: "AI,assistant,chat,developer,tools,automation"
  - Critical for App Store search

- [ ] **Promotional Text** (170 chars, optional)
  - Can be updated without new build
  - Good for announcements/updates

- [ ] **Support URL** (Required)
  - GitHub repo or support page
  - Suggestion: `https://github.com/block/goose`

- [ ] **Marketing URL** (Optional)
  - Project website if you have one

#### Screenshots (Required - At Least 1 Set)

You need screenshots for at least one device size:

**6.7" Display (iPhone 15 Pro Max) - Most Important**
- Required: 3-10 screenshots
- Size: 1290 x 2796 pixels
- Shows: Main chat, settings, voice input, tool execution

**6.5" Display (iPhone 14 Plus)**
- Size: 1284 x 2778 pixels

**5.5" Display (iPhone 8 Plus)**
- Size: 1242 x 2208 pixels

**12.9" iPad Pro**
- Optional but recommended
- Size: 2048 x 2732 pixels

**Tips:**
- Use Xcode Simulator to capture clean screenshots
- Show actual app functionality
- Can add text overlays to explain features
- First 2-3 screenshots are most important (preview)

#### App Review Information
- [ ] **Contact Information** (Private, for reviewers)
  - First/Last Name
  - Phone Number
  - Email
  
- [ ] **Demo Account** (If applicable)
  - For unlisted app, reviewers need to test it
  - Options:
    1. Provide trial mode credentials (already works)
    2. Provide test goosed server URL + secret
    3. Explain app requires personal server setup
  
- [ ] **Notes for Reviewer**
  ```
  Example:
  "Goose is a client app that connects to a self-hosted goosed server.
  The app includes a trial mode that connects to our demo server at
  https://demo-goosed.fly.dev with limited functionality for testing purposes.
  
  To test:
  1. Launch app (defaults to trial mode)
  2. Start new chat
  3. Ask questions or give tasks
  4. Try voice input (microphone icon)
  
  The app is designed for developers who run their own goosed instance,
  typically accessed via Tailscale or Cloudflare tunnel."
  ```

### 2. Pricing & Availability
- [ ] **Price**: Free (or paid if you want)
- [ ] **Availability**: Set to "Unlisted on the App Store"
  - ⚠️ **IMPORTANT**: This is what you want for unlisted distribution
  - App won't appear in search or browse
  - Only accessible via direct link
  - Perfect for limited distribution

### 3. Age Rating Questionnaire
- [ ] Complete age rating questionnaire
  - Should be **4+** (no restricted content)
  - No violence, adult content, gambling, etc.
  - Just a productivity/developer tool

### 4. Version Information
- [ ] **What's New in This Version**
  - For 1.1.1, describe key features
  - Example: "Initial public release with voice input, markdown rendering, and tool execution support"

---

## ⚠️ Things to Check/Fix (Optional but Recommended)

### Code Cleanup (Minor Priority)

1. **TODO Comments** (found 3)
   ```swift
   // In ToolViews.swift - Permission response buttons
   // These show UI but don't do anything yet
   // Consider: Disable these buttons or implement functionality
   ```

2. **Debug Print Statements** (121 instances)
   - Most are fine (logging is good)
   - Consider: Add `#if DEBUG` wrapper for verbose logs
   - Apple doesn't reject for print statements

3. **#if DEBUG Sections**
   - ✅ Already have debug-only features (good practice)
   - Settings has debug tools section (hidden in release)

### User Experience Polish

4. **First Launch Experience**
   - ✅ Defaults to trial mode (good)
   - Consider: Add onboarding screen explaining what Goose is
   - Consider: Help text for connecting to personal server

5. **Error Messages**
   - Review user-facing error messages
   - Make sure they're helpful (not technical)
   - Add recovery suggestions where possible

6. **Offline Behavior**
   - Test what happens with no connection
   - Make sure error messages are clear
   - Consider: Offline mode messaging

### App Store Guidelines Review

7. **Data Collection** (Prepare for App Privacy Questions)
   - [ ] Does app collect any data? **Likely NO**
   - [ ] Is data linked to user identity? **NO**
   - [ ] Is data used for tracking? **NO**
   
   Based on code review:
   - ✅ No analytics SDKs
   - ✅ No advertising
   - ✅ Data stays on user's server
   - ✅ Only connects to user-specified goosed instance

8. **Third-Party Content**
   - App generates AI responses via goosed
   - Apple may ask: How do you handle inappropriate content?
   - Answer: "Content moderation is handled by the underlying LLM provider and goosed server configuration"

---

## 🚀 Submission Process

### Step 1: Prepare Build
```bash
# Increment build number
# In Xcode: Target → General → Build = 2 (or next number)

# Clean and archive
xcodebuild -scheme Goose -destination 'generic/platform=iOS' clean archive
```

### Step 2: Upload to App Store Connect
1. Open Xcode → Window → Organizer
2. Select latest archive
3. **Validate App** (catches issues before upload)
4. **Distribute App** → App Store Connect → Upload

### Step 3: Complete App Store Connect
1. Go to App Store Connect → My Apps → Goose
2. Click **App Store** tab (not TestFlight)
3. Create new version or use existing
4. Fill in ALL required metadata above
5. Select your uploaded build
6. **Submit for Review**

### Step 4: Set to Unlisted
**CRITICAL STEP:**
1. In App Store Connect → Pricing and Availability
2. Under "App Availability" section
3. Select **"Unlisted on the App Store"**
4. This prevents app from appearing in search/browse
5. You'll get a direct URL to share

### Step 5: Wait for Review
- Usually 24-48 hours
- Watch for emails from App Review
- May ask clarifying questions
- Can respond via Resolution Center

---

## 📱 After Approval

### Distribution (Unlisted App)
- Apple provides a direct URL
- Format: `https://apps.apple.com/us/app/your-app/id[APP_ID]`
- Share this link with intended users
- Users can install like any App Store app
- Still gets updates via App Store

### Benefits of Unlisted
- ✅ Proper App Store distribution
- ✅ Automatic updates
- ✅ No enterprise certificate issues
- ✅ Full iOS features (no restrictions)
- ✅ More permanent than TestFlight (90 days)
- ✅ No tester limits
- ❌ Won't appear in searches
- ❌ Won't appear on charts

### Monitoring
- Check App Store Connect analytics
- Monitor crash reports
- Review user feedback (if ratings enabled)
- Track adoption via App Store Connect

---

## 📊 Screenshot Checklist

Suggested screenshots to capture (use iPhone 15 Pro Max simulator):

1. **Main Chat Screen** - Show conversation with AI
   - Good query/response example
   - Clean, polished look

2. **Tool Execution** - Show tool being used
   - Expanded tool request/response
   - Demonstrates power of Goose

3. **Voice Input** - Voice recording UI
   - Shows accessibility feature
   - Microphone permission

4. **Settings Screen** - Configuration options
   - Shows customization
   - Server connection setup

5. **Session History** - Multiple conversations
   - Shows organization
   - Token usage stats

6. **Welcome/Trial Mode** - First launch
   - Shows it works out of box
   - Trial mode explanation

### How to Capture:
```bash
# Run simulator
open -a Simulator

# Choose iPhone 15 Pro Max
# Run app via Xcode
# Navigate to each screen
# Cmd+S to save screenshot
# Screenshots save to Desktop

# They'll be perfect size for 6.7" display requirement
```

---

## ⚡ Quick Start (Minimal Viable Submission)

If you want to submit ASAP with minimum effort:

1. **Take 3 Screenshots** (30 min)
   - Main chat
   - Settings/configuration  
   - Tool execution or voice input

2. **Write Descriptions** (30 min)
   - App name: "Goose"
   - Subtitle: "AI Assistant Client"
   - Description: 2-3 paragraphs explaining it's a client for goosed
   - Keywords: "AI,assistant,developer,chat"

3. **Review Notes** (10 min)
   - Explain trial mode works for testing
   - Provide demo-goosed.fly.dev info

4. **Privacy** (5 min)
   - No data collection
   - No tracking
   - Data stays on user's server

5. **Submit** (10 min)
   - Upload build
   - Select "Unlisted on the App Store"
   - Submit for review

**Total Time: ~90 minutes for minimum viable submission**

---

## 🔍 Common Rejection Reasons (and how to avoid)

### 1. Missing Demo/Login
- **Issue**: Reviewers can't test app
- **Solution**: ✅ Trial mode already works!
- **Action**: Clearly explain in review notes

### 2. Insufficient Screenshots
- **Issue**: Need at least 1 screenshot per device size
- **Solution**: Capture from iPhone 15 Pro Max simulator
- **Action**: Take 3-10 screenshots

### 3. Confusing Description
- **Issue**: Reviewers don't understand purpose
- **Solution**: Clear, simple description
- **Action**: Explain it's a client app for goosed server

### 4. Privacy Policy (if collecting data)
- **Issue**: Required if you collect/transmit data
- **Solution**: ✅ You don't collect data (app is client only)
- **Action**: Select "No" for all data collection questions

### 5. Restricted Functionality
- **Issue**: App appears to do nothing
- **Solution**: Trial mode demonstrates features
- **Action**: Ensure trial mode is robust

---

## 💡 Pro Tips

1. **Version Strategy**
   - Keep version 1.1.1 for first release
   - Increment build number for each upload
   - Save version bumps for feature releases

2. **Unlisted vs TestFlight**
   - TestFlight: Testing, 90-day limit, 10k testers
   - Unlisted: Production, permanent, unlimited installs
   - You can keep both active

3. **Review Timeline**
   - Usually 24-48 hours
   - Can be faster (same day) or slower (1 week)
   - Don't submit before long weekends

4. **Updates**
   - Unlisted apps can be updated normally
   - Users get automatic updates via App Store
   - Review process same as initial submission

5. **Promotion**
   - Share direct link via QR code (like TestFlight)
   - Add to GitHub README
   - Tweet/blog the link
   - Won't appear in search regardless

---

## 📞 Support Resources

- **App Store Connect**: https://appstoreconnect.apple.com
- **Review Guidelines**: https://developer.apple.com/app-store/review/guidelines/
- **Resolution Center**: Handle rejections/questions
- **Contact App Review**: For clarifications during review

---

## ✅ Final Pre-Submission Checklist

Go through this right before submitting:

- [ ] Version and build numbers incremented
- [ ] All metadata fields completed
- [ ] 3+ screenshots uploaded
- [ ] Support URL added
- [ ] Review notes written
- [ ] Demo account info provided (or trial mode explained)
- [ ] Privacy questions answered (No data collection)
- [ ] Age rating completed (4+)
- [ ] Pricing set (Free)
- [ ] **"Unlisted on the App Store" selected**
- [ ] Build selected and attached
- [ ] Team reviewed everything
- [ ] Ready to hit Submit!

---

## 🎯 Success Criteria

Your app will be approved if:
- ✅ Reviewers can test it (trial mode works)
- ✅ App doesn't crash
- ✅ Descriptions are clear
- ✅ No policy violations
- ✅ Privacy questions answered honestly
- ✅ Screenshots show real functionality

Based on my code review, you should have **no issues** getting approved. The app is clean, functional, and follows Apple's guidelines.

---

## Next Steps

1. **Today**: Take screenshots, write descriptions
2. **Tomorrow**: Complete metadata in App Store Connect
3. **Day 3**: Submit for review
4. **Day 4-5**: Get approved
5. **Day 6**: Share unlisted App Store link!

Good luck! 🚀
