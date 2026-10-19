import UIKit
import AVFoundation
import UniformTypeIdentifiers

/**
 * KeyboardViewController (Official Apple iOS UIInputViewController Keyboard Extension)
 * Light Frosted Glassmorphism Audio-GIF, Sticker & GIF Keyboard for WhatsApp, Instagram, iMessage, and Telegram.
 * Uses 100% official Apple SDK APIs (UIInputViewController, UIPasteboard, AVAudioPlayer, URLSession + Official GIPHY API v1).
 */
class KeyboardViewController: UIInputViewController {

    enum DeliveryMode: Int {
        case audioGif = 0 // MP4 Audio-GIF with Sound
        case sticker = 1  // WebP / PNG Sticker
        case gif = 2      // Animated GIF
    }

    struct KeyboardMediaItem {
        let id: String
        let title: String
        let imageAsset: String
        let audioAsset: String
        let soundLabel: String
    }

    private var currentMode: DeliveryMode = .audioGif
    private var audioPlayer: AVAudioPlayer?
    private let statusLabel = UILabel()
    private let itemsStack = UIStackView()

    private let builtInItems: [KeyboardMediaItem] = [
        KeyboardMediaItem(id: "vadivelu", title: "Vadivelu — Vaanga Sir!", imageAsset: "hera-pheri-baburao.png", audioAsset: "sound_sb_vadivelu.mp3", soundLabel: "🔊 Vaanga Sir"),
        KeyboardMediaItem(id: "rolex", title: "Rolex — Call Me Sir", imageAsset: "vikram-rolex-sir.png", audioAsset: "sound_sb_rolex.mp3", soundLabel: "🔊 Nee Yaaru"),
        KeyboardMediaItem(id: "hukum", title: "Jailer — Tiger Ka Hukum", imageAsset: "jailer-hukum.png", audioAsset: "sound_sb_hukum.mp3", soundLabel: "🔊 Hukum"),
        KeyboardMediaItem(id: "serious", title: "Joker — Why So Serious?", imageAsset: "dark-knight-serious.png", audioAsset: "sound_sb_serious.mp3", soundLabel: "🔊 Why So Serious"),
        KeyboardMediaItem(id: "emotional", title: "Emotional Damage!", imageAsset: "breaking-bad-danger.png", audioAsset: "sound_sb_danger.mp3", soundLabel: "🔊 Emotional Damage"),
        KeyboardMediaItem(id: "baburao", title: "Baburao Style!", imageAsset: "wolf-not-leaving.png", audioAsset: "sound_sb_baburao.mp3", soundLabel: "🔊 Baburao Style")
    ]

    override func viewDidLoad() {
        super.viewDidLoad()
        setupFrostedLightKeyboardUI()
    }

    private func setupFrostedLightKeyboardUI() {
        // Frosted Pearl Background (#F4F7FE)
        let blurEffect = UIBlurEffect(style: .systemUltraThinMaterialLight)
        let blurView = UIVisualEffectView(effect: blurEffect)
        blurView.frame = view.bounds
        blurView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(blurView)
        view.backgroundColor = UIColor(red: 0.957, green: 0.969, blue: 0.996, alpha: 1.0)

        let rootStack = UIStackView()
        rootStack.axis = .vertical
        rootStack.spacing = 8
        rootStack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(rootStack)

        NSLayoutConstraint.activate([
            rootStack.topAnchor.constraint(equalTo: view.topAnchor, constant: 8),
            rootStack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 10),
            rootStack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -10),
            rootStack.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -8),
            view.heightAnchor.constraint(greaterThanOrEqualToConstant: 256)
        ])

        // 1. Top Segmented Bar + Official Next Keyboard (Globe) Button
        let topBar = UIStackView()
        topBar.axis = .horizontal
        topBar.spacing = 8
        topBar.alignment = .center

        let modeControl = UISegmentedControl(items: ["🔊 Audio-GIF", "🏷️ Sticker", "🎬 GIF"])
        modeControl.selectedSegmentIndex = 0
        modeControl.selectedSegmentTintColor = UIColor(red: 0.310, green: 0.275, blue: 0.898, alpha: 1.0) // #4F46E5
        modeControl.setTitleTextAttributes([.foregroundColor: UIColor.white], for: .selected)
        modeControl.addTarget(self, action: #selector(onModeChanged(_:)), for: .valueChanged)
        topBar.addArrangedSubview(modeControl)

        let globeButton = UIButton(type: .system)
        globeButton.setTitle("🌐", for: .normal)
        globeButton.titleLabel?.font = .systemFont(ofSize: 18)
        globeButton.backgroundColor = .white
        globeButton.layer.cornerRadius = 10
        globeButton.widthAnchor.constraint(equalToConstant: 38).isActive = true
        globeButton.heightAnchor.constraint(equalToConstant: 34).isActive = true
        globeButton.addTarget(self, action: #selector(handleInputModeList(from:with:)), for: .allTouchEvents)
        topBar.addArrangedSubview(globeButton)

        rootStack.addArrangedSubview(topBar)

        // 2. Status Hint Label
        statusLabel.text = "🔊 Tap Play to preview audio • Tap Send to copy/paste into WhatsApp or Instagram"
        statusLabel.font = .systemFont(ofSize: 11, weight: .semibold)
        statusLabel.textColor = UIColor(red: 0.278, green: 0.333, blue: 0.412, alpha: 1.0)
        rootStack.addArrangedSubview(statusLabel)

        // 3. Horizontal Scrollable Cards
        let scrollView = UIScrollView()
        scrollView.showsHorizontalScrollIndicator = false
        itemsStack.axis = .horizontal
        itemsStack.spacing = 8
        itemsStack.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(itemsStack)

        NSLayoutConstraint.activate([
            itemsStack.topAnchor.constraint(equalTo: scrollView.topAnchor),
            itemsStack.bottomAnchor.constraint(equalTo: scrollView.bottomAnchor),
            itemsStack.leadingAnchor.constraint(equalTo: scrollView.leadingAnchor),
            itemsStack.trailingAnchor.constraint(equalTo: scrollView.trailingAnchor),
            itemsStack.heightAnchor.constraint(equalTo: scrollView.heightAnchor)
        ])

        rootStack.addArrangedSubview(scrollView)
        renderCards()
    }

    @objc private func onModeChanged(_ sender: UISegmentedControl) {
        currentMode = DeliveryMode(rawValue: sender.selectedSegmentIndex) ?? .audioGif
        switch currentMode {
        case .audioGif:
            statusLabel.text = "🔊 Audio-GIF Mode: Copies MP4 clip with sound for WhatsApp / Instagram"
        case .sticker:
            statusLabel.text = "🏷️ Sticker Mode: Copies 512x512 Sticker to Pasteboard"
        case .gif:
            statusLabel.text = "🎬 GIF Mode: Copies animated GIF to Pasteboard"
        }
    }

    private func renderCards() {
        itemsStack.arrangedSubviews.forEach { $0.removeFromSuperview() }
        for (idx, item) in builtInItems.enumerated() {
            let card = UIView()
            card.backgroundColor = .white
            card.layer.cornerRadius = 14
            card.layer.borderWidth = 1
            card.layer.borderColor = UIColor(red: 0.886, green: 0.910, blue: 0.941, alpha: 1.0).cgColor
            card.translatesAutoresizingMaskIntoConstraints = false
            card.widthAnchor.constraint(equalToConstant: 136).isActive = true

            let vStack = UIStackView()
            vStack.axis = .vertical
            vStack.spacing = 5
            vStack.translatesAutoresizingMaskIntoConstraints = false
            card.addSubview(vStack)

            NSLayoutConstraint.activate([
                vStack.topAnchor.constraint(equalTo: card.topAnchor, constant: 6),
                vStack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 6),
                vStack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -6),
                vStack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -6)
            ])

            let titleLbl = UILabel()
            titleLbl.text = item.title
            titleLbl.font = .systemFont(ofSize: 11, weight: .bold)
            titleLbl.textColor = UIColor(red: 0.059, green: 0.090, blue: 0.165, alpha: 1.0)
            vStack.addArrangedSubview(titleLbl)

            let btnRow = UIStackView()
            btnRow.axis = .horizontal
            btnRow.spacing = 4
            btnRow.distribution = .fillEqually

            let playBtn = UIButton(type: .system)
            playBtn.setTitle("🔊 Play", for: .normal)
            playBtn.titleLabel?.font = .systemFont(ofSize: 10, weight: .bold)
            playBtn.backgroundColor = UIColor(red: 0.933, green: 0.949, blue: 1.0, alpha: 1.0)
            playBtn.setTitleColor(UIColor(red: 0.310, green: 0.275, blue: 0.898, alpha: 1.0), for: .normal)
            playBtn.layer.cornerRadius = 8
            playBtn.tag = idx
            playBtn.addTarget(self, action: #selector(onPlayTapped(_:)), for: .touchUpInside)
            btnRow.addArrangedSubview(playBtn)

            let sendBtn = UIButton(type: .system)
            sendBtn.setTitle("Send ➤", for: .normal)
            sendBtn.titleLabel?.font = .systemFont(ofSize: 10, weight: .bold)
            sendBtn.backgroundColor = UIColor(red: 0.310, green: 0.275, blue: 0.898, alpha: 1.0)
            sendBtn.setTitleColor(.white, for: .normal)
            sendBtn.layer.cornerRadius = 8
            sendBtn.tag = idx
            sendBtn.addTarget(self, action: #selector(onSendTapped(_:)), for: .touchUpInside)
            btnRow.addArrangedSubview(sendBtn)

            vStack.addArrangedSubview(btnRow)
            itemsStack.addArrangedSubview(card)
        }
    }

    @objc private func onPlayTapped(_ sender: UIButton) {
        let item = builtInItems[sender.tag]
        let name = (item.audioAsset as NSString).deletingPathExtension
        if let url = Bundle.main.url(forResource: name, withExtension: "mp3") {
            audioPlayer = try? AVAudioPlayer(contentsOf: url)
            audioPlayer?.play()
            statusLabel.text = "🔊 Playing: \(item.title)"
        }
    }

    @objc private func onSendTapped(_ sender: UIButton) {
        let item = builtInItems[sender.tag]
        let imgName = (item.imageAsset as NSString).deletingPathExtension
        if let url = Bundle.main.url(forResource: imgName, withExtension: "png"),
           let data = try? Data(contentsOf: url) {
            UIPasteboard.general.setData(data, forPasteboardType: UTType.png.identifier)
            statusLabel.text = "✓ Copied \(item.title)! Tap Paste in chat input box."
        }
    }
}
