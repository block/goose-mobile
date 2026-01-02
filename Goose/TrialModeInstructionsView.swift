import SwiftUI

struct TrialModeInstructionsView: View {
    @Environment(\.dismiss) var dismiss
    @Environment(\.colorScheme) var colorScheme
    @State private var showingDownloadSheet = false

    // Download URL for Goose Desktop - always redirects to latest version
    private let gooseDesktopDownloadURL =
        "https://github.com/block/goose/releases/latest/download/Goose.zip"

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    // Header
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Connect to Your Goose Desktop")
                            .font(.system(size: 28, weight: .bold))
                            .foregroundColor(.primary)

                        Text(
                            "Follow these simple steps to link your iPhone to your desktop Goose agent"
                        )
                        .font(.system(size: 16))
                        .foregroundColor(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(.top, 8)

                    // Steps
                    VStack(alignment: .leading, spacing: 20) {
                        VStack(alignment: .leading, spacing: 12) {
                            InstructionStep(
                                number: 1,
                                title: "Install Goose Desktop",
                                description:
                                    "Download and install the Goose desktop application on your computer if you haven't already.",
                                icon: "desktopcomputer"
                            )

                            // Download button
                            Button(action: {
                                showingDownloadSheet = true
                            }) {
                                HStack(spacing: 8) {
                                    Image(systemName: "arrow.down.circle.fill")
                                        .font(.system(size: 16))
                                    Text("Send Download to Computer")
                                        .font(.system(size: 15, weight: .medium))
                                }
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .background(Color.blue)
                                .cornerRadius(12)
                            }
                            .padding(.leading, 48)  // Align with step content
                        }

                        InstructionStep(
                            number: 2,
                            title: "Enable Tunneling",
                            description:
                                "Open Goose desktop, go to Settings → App, and turn on the Tunneling option.",
                            icon: "network"
                        )

                        InstructionStep(
                            number: 3,
                            title: "Scan QR Code",
                            description:
                                "A QR code will appear in the desktop app. Use your iPhone's camera to scan it.",
                            icon: "qrcode.viewfinder"
                        )
                    }

                    // Important Note
                    VStack(alignment: .leading, spacing: 12) {
                        Label("Important", systemImage: "info.circle.fill")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(.blue)

                        Text(
                            "Make sure both devices are connected to the internet. The tunneling feature creates a secure connection between your iPhone and desktop, allowing you to access your personal Goose agent from anywhere."
                        )
                        .font(.system(size: 14))
                        .foregroundColor(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(16)
                    .background(
                        RoundedRectangle(cornerRadius: 12)
                            .fill(
                                colorScheme == .dark
                                    ? Color(red: 0.15, green: 0.15, blue: 0.18).opacity(0.5)
                                    : Color(red: 0.96, green: 0.96, blue: 0.98))
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .strokeBorder(
                                colorScheme == .dark
                                    ? Color(red: 0.25, green: 0.25, blue: 0.28)
                                    : Color(red: 0.88, green: 0.88, blue: 0.90),
                                lineWidth: 1
                            )
                    )

                    // Trial Mode Features
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Trial Mode Features")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(.primary)

                        Text("While in trial mode, you can:")
                            .font(.system(size: 14))
                            .foregroundColor(.secondary)

                        VStack(alignment: .leading, spacing: 8) {
                            FeatureRow(text: "Ask questions and get answers", isIncluded: true)
                            FeatureRow(text: "Explore the app interface", isIncluded: true)
                            FeatureRow(text: "Access your file system", isIncluded: false)
                            FeatureRow(text: "Run commands and scripts", isIncluded: false)
                            FeatureRow(text: "Save persistent sessions", isIncluded: false)
                        }
                    }
                    .padding(.top, 8)
                }
                .padding(20)
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                    .font(.system(size: 17, weight: .medium))
                }
            }
            .sheet(isPresented: $showingDownloadSheet) {
                DownloadLinkSheet(downloadURL: gooseDesktopDownloadURL)
            }
        }
    }
}

struct InstructionStep: View {
    let number: Int
    let title: String
    let description: String
    let icon: String
    @Environment(\.colorScheme) var colorScheme

    var body: some View {
        HStack(alignment: .top, spacing: 16) {
            // Step number circle
            ZStack {
                Circle()
                    .fill(
                        colorScheme == .dark
                            ? Color(red: 0.2, green: 0.2, blue: 0.25)
                            : Color(red: 0.95, green: 0.95, blue: 0.97)
                    )
                    .frame(width: 32, height: 32)

                Text("\(number)")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.primary)
            }

            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 8) {
                    Image(systemName: icon)
                        .font(.system(size: 16))
                        .foregroundColor(.blue)

                    Text(title)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.primary)
                }

                Text(description)
                    .font(.system(size: 14))
                    .foregroundColor(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer()
        }
    }
}

struct FeatureRow: View {
    let text: String
    let isIncluded: Bool

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: isIncluded ? "checkmark.circle.fill" : "xmark.circle.fill")
                .font(.system(size: 16))
                .foregroundColor(isIncluded ? .green : .gray)

            Text(text)
                .font(.system(size: 14))
                .foregroundColor(isIncluded ? .primary : .secondary)

            Spacer()
        }
    }
}

// MARK: - Download Link Sheet
struct DownloadLinkSheet: View {
    let downloadURL: String
    @Environment(\.dismiss) var dismiss
    @Environment(\.colorScheme) var colorScheme
    @State private var showingShareSheet = false
    @State private var linkCopied = false

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                Spacer()

                // Content
                VStack(spacing: 40) {
                    // Header
                    VStack(spacing: 16) {
                        Image(systemName: "desktopcomputer")
                            .font(.system(size: 70))
                            .foregroundColor(.blue)

                        Text("Get Goose Desktop")
                            .font(.system(size: 28, weight: .bold))
                            .foregroundColor(.primary)

                        Text("Send the installer to your Mac to get started")
                            .font(.system(size: 16))
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 40)
                    }

                    // Action Buttons
                    VStack(spacing: 16) {
                        // Share button (primary action - includes AirDrop)
                        Button(action: {
                            showingShareSheet = true
                        }) {
                            HStack(spacing: 12) {
                                Image(systemName: "square.and.arrow.up")
                                    .font(.system(size: 20, weight: .semibold))
                                Text("Share to Mac")
                                    .font(.system(size: 18, weight: .semibold))
                            }
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 18)
                            .background(
                                LinearGradient(
                                    colors: [Color.blue, Color.blue.opacity(0.8)],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .cornerRadius(16)
                            .shadow(color: Color.blue.opacity(0.4), radius: 12, x: 0, y: 6)
                        }

                        // Copy link button (secondary)
                        Button(action: {
                            copyToClipboard()
                        }) {
                            HStack(spacing: 10) {
                                Image(systemName: linkCopied ? "checkmark" : "doc.on.doc")
                                    .font(.system(size: 17))
                                Text(linkCopied ? "Link Copied!" : "Copy Link")
                                    .font(.system(size: 16, weight: .medium))
                            }
                            .foregroundColor(linkCopied ? .green : .blue)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 16)
                            .background(
                                RoundedRectangle(cornerRadius: 14)
                                    .strokeBorder(
                                        linkCopied ? Color.green : Color.blue, lineWidth: 2)
                            )
                        }
                    }
                    .padding(.horizontal, 32)

                    // Info text
                    VStack(spacing: 8) {
                        HStack(spacing: 6) {
                            Image(systemName: "info.circle")
                                .font(.system(size: 14))
                            Text("Look for your Mac's name in AirDrop")
                                .font(.system(size: 14))
                        }
                        .foregroundColor(.secondary)
                    }
                }

                Spacer()
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                    .font(.system(size: 17))
                }
            }
        }
        .sheet(isPresented: $showingShareSheet) {
            if let url = URL(string: downloadURL) {
                let activityItem = DownloadActivityItemSource(url: url)
                ShareSheet(items: [activityItem])
            }
        }
    }

    private func copyToClipboard() {
        UIPasteboard.general.string = downloadURL

        // Show confirmation with haptic feedback
        let generator = UINotificationFeedbackGenerator()
        generator.notificationOccurred(.success)

        withAnimation {
            linkCopied = true
        }

        // Reset after 2 seconds
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            withAnimation {
                linkCopied = false
            }
        }
    }
}

// MARK: - Custom Activity Item for better share sheet presentation
class DownloadActivityItemSource: NSObject, UIActivityItemSource {
    let url: URL

    init(url: URL) {
        self.url = url
        super.init()
    }

    func activityViewControllerPlaceholderItem(_ activityViewController: UIActivityViewController)
        -> Any
    {
        return url
    }

    func activityViewController(
        _ activityViewController: UIActivityViewController,
        itemForActivityType activityType: UIActivity.ActivityType?
    ) -> Any? {
        // Exclude Reminders and other task apps by checking the activity type string
        if let activityType = activityType {
            let typeString = activityType.rawValue.lowercased()
            // Filter out Reminders, Calendar, and other productivity apps
            if typeString.contains("reminder") || typeString.contains("calendar")
                || typeString.contains("todo") || typeString.contains("task")
            {
                return nil
            }
        }
        return url
    }

    func activityViewController(
        _ activityViewController: UIActivityViewController,
        subjectForActivityType activityType: UIActivity.ActivityType?
    ) -> String {
        return "Download Goose Desktop"
    }

    // Provide a descriptive message for sharing
    func activityViewController(
        _ activityViewController: UIActivityViewController,
        dataTypeIdentifierForActivityType activityType: UIActivity.ActivityType?
    ) -> String {
        return "public.url"
    }
}

// MARK: - ShareSheet wrapper for UIActivityViewController
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        let controller = UIActivityViewController(
            activityItems: items,
            applicationActivities: nil
        )

        // Exclude social media and other irrelevant sharing options to make AirDrop more prominent
        controller.excludedActivityTypes = [
            .postToFacebook,
            .postToTwitter,
            .postToWeibo,
            .postToVimeo,
            .postToFlickr,
            .postToTencentWeibo,
            .assignToContact,
            .saveToCameraRoll,
            .addToReadingList,
            .openInIBooks,
            .markupAsPDF,
            .print,
            .sharePlay,
        ]

        // Note: We keep Messages, Mail, Notes, and AirDrop as these are useful for sharing download links

        return controller
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

#Preview {
    TrialModeInstructionsView()
}
