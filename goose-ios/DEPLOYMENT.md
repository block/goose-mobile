# GooseChat iOS - TestFlight Deployment Guide

## Current Project Status
- **Bundle ID**: `com.goose.chat` (needs to be changed to your unique identifier)
- **Version**: 1.0
- **Build**: 1
- **Code Signing**: Automatic with Apple Development
- **App Icons**: Not yet added (required for submission)

## Prerequisites
- Active Apple Developer Program membership ($99/year)
- Xcode installed and up to date
- App Store Connect access
- A 1024x1024 PNG icon for your app

## Step-by-Step Deployment Process

### 1. Configure Your Apple Developer Account in Xcode
1. Open Xcode and go to **Xcode → Settings → Accounts**
2. Click **+** to add your Apple ID if not already added
3. Verify your account shows your Developer Program membership
4. If needed, download manual profiles via "Download Manual Profiles"

### 2. Update Bundle Identifier
1. Open the project in Xcode: `open GooseChat.xcodeproj`
2. Select the **GooseChat** project in the navigator
3. Select the **GooseChat** target
4. Go to **Signing & Capabilities** tab
5. Change Bundle Identifier to something unique:
   - Format: `com.yourcompany.goosechat`
   - Must be globally unique
   - Use reverse domain notation

### 3. Configure Code Signing
In the **Signing & Capabilities** tab:
1. Ensure **Automatically manage signing** is checked
2. Select your **Team** (your Apple Developer account)
3. Xcode will automatically create provisioning profiles
4. Verify no signing errors appear

### 4. Add Required App Icons
**This is mandatory for App Store submission!**

1. Create or obtain a 1024x1024 pixel PNG icon
2. Generate all required sizes using [App Icon Generator](https://www.appicon.co/)
3. In Xcode, navigate to `GooseChat/Assets.xcassets/AppIcon`
4. Drag and drop the generated icons into their respective slots
5. Ensure all required sizes are filled:
   - iPhone: 20pt, 29pt, 40pt, 60pt (2x and 3x)
   - iPad: 20pt, 29pt, 40pt, 76pt, 83.5pt
   - App Store: 1024x1024 (1x)

### 5. Create App in App Store Connect
1. Go to [App Store Connect](https://appstoreconnect.apple.com)
2. Click **My Apps** → **+** → **New App**
3. Fill in the required information:
   - **Platform**: iOS
   - **App Name**: GooseChat (or your chosen name)
   - **Primary Language**: English (or your preference)
   - **Bundle ID**: Select the one you configured
   - **SKU**: A unique identifier (e.g., "GOOSECHAT001")
4. Click **Create**

### 6. Build and Archive
In Xcode:
1. Select **Any iOS Device (arm64)** as the build destination (top toolbar)
2. Ensure scheme is set to **GooseChat**
3. Clean build folder: **Product → Clean Build Folder** (⌘⇧K)
4. Archive: **Product → Archive** (⌘⇧I)
5. Wait for the build to complete (may take several minutes)

### 7. Upload to App Store Connect
When the Organizer window appears:
1. Select your archive and click **Distribute App**
2. Choose **App Store Connect**
3. Choose **Upload**
4. Options (usually keep defaults):
   - **App Store Connect Distribution**: Upload
   - **Automatically manage signing**: Yes
   - **Upload your app's symbols**: Yes
5. Review and click **Upload**
6. Wait for upload to complete

### 8. Configure TestFlight
In App Store Connect:
1. Wait 10-30 minutes for processing (you'll get an email)
2. Go to your app → **TestFlight** tab
3. Your build will appear under **iOS Builds**
4. Click on the build number
5. Fill in required **Test Information**:
   - **What to Test**: Brief description of features to test
   - **Test Credentials**: If app requires login (optional)
6. Answer Export Compliance (for HTTPS-only apps, usually "No")
7. Click **Save**

### 9. Add Testers

#### Internal Testing (Instant, up to 100 testers)
1. Go to **Internal Group** in TestFlight
2. Click **+** next to Testers
3. Add team members by email
4. Select the build to test
5. Testers receive invitation immediately

#### External Testing (Requires review, up to 10,000 testers)
1. Click **+** next to External Groups
2. Create a new group (e.g., "Beta Testers")
3. Add the build to the group
4. Add external testers by email
5. Submit for **Beta App Review**
6. Wait 1-2 days for approval
7. Testers receive invitations after approval

## Version Management

### Version Numbers
- **Marketing Version** (CFBundleShortVersionString): User-facing version (1.0.0, 1.1.0, 2.0.0)
- **Build Number** (CFBundleVersion): Must increment with each upload (1, 2, 3...)

### Updating for New Builds
1. In Xcode, select project → target → General
2. Update Version and/or Build number
3. Archive and upload as before

## Command Line Deployment (Optional)

```bash
# Check current configuration
xcodebuild -showBuildSettings -project GooseChat.xcodeproj | grep -E "PRODUCT_BUNDLE_IDENTIFIER|MARKETING_VERSION|CURRENT_PROJECT_VERSION"

# Build archive
xcodebuild -project GooseChat.xcodeproj \
  -scheme GooseChat \
  -configuration Release \
  -archivePath ./build/GooseChat.xcarchive \
  archive

# Export for App Store
xcodebuild -exportArchive \
  -archivePath ./build/GooseChat.xcarchive \
  -exportPath ./build \
  -exportOptionsPlist ExportOptions.plist
```

### Sample ExportOptions.plist
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>method</key>
    <string>app-store</string>
    <key>teamID</key>
    <string>YOUR_TEAM_ID</string>
    <key>uploadBitcode</key>
    <true/>
    <key>uploadSymbols</key>
    <true/>
</dict>
</plist>
```

## Troubleshooting

### Common Issues and Solutions

| Issue | Solution |
|-------|----------|
| "No eligible devices" | Select "Any iOS Device (arm64)" as build destination |
| Provisioning profile errors | Enable "Automatically manage signing" in project settings |
| "Missing required icon" | Add 1024x1024 App Store icon in Assets.xcassets |
| Bundle ID already exists | Choose a unique identifier in your namespace |
| Build number already used | Increment build number in General tab |
| "Invalid Swift Support" | Clean build folder and rebuild |
| Export compliance missing | Answer export compliance questions in TestFlight |

### Certificate Issues
If you encounter certificate problems:
1. Xcode → Settings → Accounts
2. Select your account → Manage Certificates
3. Click **+** → Apple Development
4. Retry the archive process

### Build Validation Errors
Before uploading, you can validate:
1. In Organizer, select your archive
2. Click **Validate App**
3. Fix any issues before uploading

## TestFlight Best Practices

1. **Test Information**: Always provide clear testing instructions
2. **Build Notes**: Add release notes for each build
3. **Feedback**: Enable TestFlight feedback in your app
4. **Crash Reports**: Monitor TestFlight crashes in App Store Connect
5. **Beta Review**: For external testing, submit 1-2 days before needed
6. **Version Strategy**: Use build numbers liberally, save version numbers for releases

## Next Steps After TestFlight

1. **Gather Feedback**: Use TestFlight feedback and crash reports
2. **Iterate**: Fix issues and upload new builds
3. **Prepare for Release**: 
   - Add App Store screenshots
   - Write App Store description
   - Set pricing and availability
4. **Submit for Review**: When ready, submit for App Store review

## Useful Resources

- [App Store Connect](https://appstoreconnect.apple.com)
- [Apple Developer Portal](https://developer.apple.com)
- [TestFlight Documentation](https://developer.apple.com/testflight/)
- [App Store Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)
- [Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines/)

## Support

For issues with:
- **Technical problems**: Apple Developer Forums
- **App Review**: App Store Connect Contact Us
- **TestFlight issues**: TestFlight Feedback in App Store Connect
