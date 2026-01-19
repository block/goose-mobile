# 📸 Screenshot Guide for App Store

## Quick Reference

### Required Size
**iPhone 15 Pro Max: 1290 x 2796 pixels**
- This is the ONLY required size
- Xcode simulator produces perfect size
- Need 3-10 screenshots

## How to Capture

```bash
# 1. Open Xcode
# 2. Run app on iPhone 15 Pro Max simulator
# 3. Navigate to screen
# 4. Press Cmd+S (saves to Desktop)
# 5. Repeat for each screen
```

## Suggested Screenshots (in order)

### 1. Main Chat Screen ⭐ MOST IMPORTANT
**What to show:**
- Active conversation with Goose
- Good example query/response
- Shows markdown rendering
- Clean, professional look

**Example query to use:**
```
"Help me understand how to deploy this iOS app to the App Store"
```

### 2. Tool Execution
**What to show:**
- Expanded tool request/response
- Shows Goose using developer tools
- Demonstrates automation capabilities

**How to get it:**
- Ask: "What files are in this directory?"
- Expand the tool call to show details

### 3. Settings/Configuration
**What to show:**
- Settings screen
- Server configuration options
- Shows customizability
- Model/provider selection

### 4. Voice Input (Optional but good)
**What to show:**
- Voice recording UI active
- Microphone icon in use
- Shows accessibility feature

### 5. Session History (Optional)
**What to show:**
- Multiple conversation sessions
- Token usage stats
- Shows organization

### 6. Welcome/Trial Mode (Optional)
**What to show:**
- First launch experience
- Trial mode explanation
- "Try it now" experience

## Screenshot Checklist

Before capturing:
- [ ] Set iPhone 15 Pro Max simulator
- [ ] App in light mode (or dark if you prefer)
- [ ] Status bar clean (full battery, good signal)
- [ ] Trial mode working
- [ ] Good example content prepared

During capture:
- [ ] Main chat with quality conversation
- [ ] Tool execution expanded
- [ ] Settings screen
- [ ] 2-3 more screens showing features

After capture:
- [ ] Verify size: 1290 x 2796 px
- [ ] Check they're clear and readable
- [ ] No sensitive/test data visible
- [ ] Show actual app functionality

## Pro Tips

### Content Quality
✅ Use realistic, impressive examples
✅ Show features that make reviewers say "cool!"
✅ Clean, polished conversations
✅ Demonstrate value immediately

❌ Don't show errors or bugs
❌ Don't use lorem ipsum or test data
❌ Don't show empty/minimal states
❌ Don't include personal information

### Visual Quality
- Use light mode (easier to see)
- Full screen (no keyboards unless relevant)
- Hide any debug UI
- Make sure text is readable
- First 2-3 screenshots are critical (they show in preview)

### Order Matters
1st screenshot = Most important (appears in search)
2nd-3rd = Also show in preview
4th+ = Viewed when user taps to see more

## Editing (Optional)

You can add:
- Text overlays explaining features
- Colored borders/highlights
- Callout arrows
- Feature labels

Tools:
- Keynote (Mac built-in)
- Sketch/Figma
- Photoshop
- Online tools like AppLaunchpad

But simple, clean screenshots work fine!

## Upload Process

1. Go to App Store Connect
2. Navigate to your app
3. Click "App Store" tab
4. Under "Screenshots and Previews"
5. Drag and drop 3-10 screenshots
6. Reorder if needed (drag to rearrange)
7. First screenshot shows in search/browse

## Validation

Before uploading, verify:
- [ ] Exactly 1290 x 2796 pixels
- [ ] PNG or JPEG format
- [ ] Under 5MB per image
- [ ] No transparency (PNG with opaque background)
- [ ] At least 3 screenshots
- [ ] Shows actual app functionality

## Time Estimate

- **Capturing**: 15 minutes
- **Selecting best**: 5 minutes  
- **Optional editing**: 10-30 minutes
- **Uploading**: 5 minutes

**Total: 25-60 minutes**

## Fallback Options

### Don't have time for quality screenshots?
- Minimum: 3 simple screen captures
- Chat, Settings, and one feature
- Better than nothing!
- Can always update later (no review needed for screenshot updates)

### Need inspiration?
Look at other dev tools in App Store:
- Working Copy (git client)
- Prompt (SSH client)
- Runestone (text editor)

They all use simple, functional screenshots.

## Common Mistakes

❌ Wrong size (must be 1290 x 2796 for 6.7" display)
❌ Too few screenshots (need at least 3)
❌ Showing UI that doesn't exist
❌ Including test/fake data
❌ Screenshots from wrong device

✅ Correct size from simulator
✅ 3-10 real screenshots
✅ Actual app functionality
✅ Real, quality content
✅ iPhone 15 Pro Max simulator

---

## Quick Command Reference

```bash
# Open simulator with specific device
open -a Simulator

# In Xcode: 
# Window → Devices and Simulators → iPhone 15 Pro Max

# Run app (Cmd+R)

# Take screenshot (Cmd+S)
# Saves to Desktop automatically

# Check screenshot size
sips -g pixelWidth -g pixelHeight ~/Desktop/Simulator*.png
# Should show: 1290 x 2796
```

## Done!

Once you have 3-10 good screenshots at 1290 x 2796 pixels, you're ready to upload to App Store Connect. Screenshots are the biggest blocker for most people - but with the simulator, it's actually quick and easy!
