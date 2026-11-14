import Flutter
import UIKit

public final class MobilePlatformPlugin: NSObject, FlutterPlugin, FlutterSceneLifeCycleDelegate {
    private let channel: FlutterMethodChannel
    private var pendingLink: String?
    private var listenerReady = false
    private var lastLink: String?
    private var lastLinkTime: TimeInterval = 0
    private static let scheduleBundleID = "net.anissia.schedule"

    private init(channel: FlutterMethodChannel) {
        self.channel = channel
        super.init()
    }

    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(
            name: "net.anissia.mobile/platform", binaryMessenger: registrar.messenger()
        )
        let instance = MobilePlatformPlugin(channel: channel)
        registrar.addMethodCallDelegate(instance, channel: channel)
        registrar.addApplicationDelegate(instance)
        registrar.addSceneDelegate(instance)
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        let arguments = call.arguments as? [String: Any]
        let rawURL = arguments?["url"] as? String
        switch call.method {
        case "initialLink":
            let initial = pendingLink
            pendingLink = nil
            listenerReady = true
            result(initial)
        case "openBrowser":
            guard let url = webURL(rawURL) else {
                result(false)
                return
            }
            UIApplication.shared.open(browserURL(url), options: [:]) { result($0) }
        case "openSchedule":
            openSchedule(rawURL, result: result)
        case "openExternal":
            guard let url = externalURL(rawURL) else {
                result(false)
                return
            }
            UIApplication.shared.open(url, options: [:]) { result($0) }
        case "exitApp":
            result(nil)
        default:
            result(FlutterMethodNotImplemented)
        }
    }

    private func openSchedule(_ rawURL: String?, result: @escaping FlutterResult) {
        guard Bundle.main.bundleIdentifier != Self.scheduleBundleID,
              let url = scheduleURL(rawURL), !isBrowserFallback(url) else {
            result(false)
            return
        }
        UIApplication.shared.open(url, options: [.universalLinksOnly: true]) { opened in
            if opened {
                result(true)
                return
            }
            var components = URLComponents()
            components.scheme = "anissia-schedule"
            components.host = "open"
            components.queryItems = [URLQueryItem(name: "url", value: url.absoluteString)]
            guard let fallback = components.url else {
                result(false)
                return
            }
            UIApplication.shared.open(fallback, options: [:]) { result($0) }
        }
    }

    public func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [AnyHashable: Any] = [:]
    ) -> Bool {
        if let url = launchOptions[UIApplication.LaunchOptionsKey.url] as? URL {
            _ = receive(url)
        }
        if let activities = launchOptions[UIApplication.LaunchOptionsKey.userActivityDictionary]
            as? [AnyHashable: Any] {
            for case let activity as NSUserActivity in activities.values {
                _ = receive(activity)
            }
        }
        return true
    }

    public func application(
        _ app: UIApplication,
        open url: URL,
        options: [UIApplication.OpenURLOptionsKey: Any] = [:]
    ) -> Bool {
        receive(url)
    }

    public func application(
        _ application: UIApplication,
        continue userActivity: NSUserActivity,
        restorationHandler: @escaping ([Any]) -> Void
    ) -> Bool {
        receive(userActivity)
    }

    public func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions?
    ) -> Bool {
        guard let options = connectionOptions else { return false }
        var handled = false
        for context in options.urlContexts {
            handled = receive(context.url) || handled
        }
        for activity in options.userActivities {
            handled = receive(activity) || handled
        }
        return handled
    }

    public func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) -> Bool {
        var handled = false
        for context in URLContexts {
            handled = receive(context.url) || handled
        }
        return handled
    }

    public func scene(_ scene: UIScene, continue userActivity: NSUserActivity) -> Bool {
        receive(userActivity)
    }

    private func receive(_ activity: NSUserActivity) -> Bool {
        guard activity.activityType == NSUserActivityTypeBrowsingWeb,
              let url = activity.webpageURL else { return false }
        return receive(url)
    }

    private func receive(_ incoming: URL) -> Bool {
        guard Bundle.main.bundleIdentifier == Self.scheduleBundleID else { return false }
        var rawURL = incoming.absoluteString
        if incoming.scheme?.lowercased() == "anissia-schedule" {
            guard let components = URLComponents(url: incoming, resolvingAgainstBaseURL: false),
                  components.host == "open", components.path.isEmpty,
                  components.user == nil, components.password == nil,
                  components.port == nil, components.fragment == nil,
                  let items = components.queryItems, items.count == 1,
                  items[0].name == "url", let value = items[0].value else { return false }
            rawURL = value
        }
        guard let url = scheduleURL(rawURL), !isBrowserFallback(url) else { return false }
        let link = url.absoluteString
        let now = Date.timeIntervalSinceReferenceDate
        if lastLink == link && now - lastLinkTime < 0.5 { return true }
        lastLink = link
        lastLinkTime = now
        if listenerReady {
            channel.invokeMethod("link", arguments: ["url": link])
        } else {
            pendingLink = link
        }
        return true
    }

    private func webURL(_ raw: String?) -> URL? {
        guard let raw = raw, !raw.isEmpty, raw.utf8.count <= 16384,
              !raw.unicodeScalars.contains(where: { $0.value <= 32 || $0.value == 92 }),
              let components = URLComponents(string: raw),
              let scheme = components.scheme?.lowercased(), ["http", "https"].contains(scheme),
              let host = components.host, !host.isEmpty,
              components.user == nil, components.password == nil,
              components.port == nil || (1...65535).contains(components.port!),
              let url = components.url else { return nil }
        return url
    }

    private func externalURL(_ raw: String?) -> URL? {
        guard let raw = raw, !raw.isEmpty, raw.utf8.count <= 16384,
              !raw.unicodeScalars.contains(where: { $0.value <= 32 || $0.value == 92 }),
              let components = URLComponents(string: raw),
              let scheme = components.scheme?.lowercased(), ["mailto", "tel"].contains(scheme),
              components.host == nil, components.user == nil, components.password == nil,
              components.port == nil, components.fragment == nil,
              !components.path.isEmpty || (scheme == "mailto" && components.query != nil) else {
            return nil
        }
        return components.url
    }

    private func browserURL(_ url: URL) -> URL {
        guard scheduleURL(url.absoluteString) != nil,
              var components = URLComponents(url: url, resolvingAgainstBaseURL: false) else {
            return url
        }
        var items = (components.queryItems ?? []).filter { $0.name != "_anissia_browser" }
        items.append(URLQueryItem(name: "_anissia_browser", value: "1"))
        components.queryItems = items
        return components.url ?? url
    }

    private func isBrowserFallback(_ url: URL) -> Bool {
        URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.contains {
            $0.name == "_anissia_browser" && $0.value == "1"
        } == true
    }

    private func scheduleURL(_ raw: String?) -> URL? {
        guard let url = webURL(raw),
              var components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              components.scheme?.lowercased() == "https",
              components.host?.lowercased() == "anissia.net",
              components.port == nil || components.port == 443 else { return nil }
        let path = components.path
        guard path == "/schedule" || path.hasPrefix("/schedule/"),
              !path.contains("\\"),
              !path.split(separator: "/").contains(where: { $0 == "." || $0 == ".." }) else {
            return nil
        }
        components.scheme = "https"
        components.host = "anissia.net"
        components.port = nil
        return components.url
    }
}
