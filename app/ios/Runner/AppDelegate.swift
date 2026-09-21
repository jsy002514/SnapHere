import Flutter
import UIKit
import GoogleMaps

@main
@objc class AppDelegate: FlutterAppDelegate, FlutterImplicitEngineDelegate {
  private var mapsConfigured = false
  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }

  func didInitializeImplicitFlutterEngine(_ engineBridge: FlutterImplicitEngineBridge) {
    GeneratedPluginRegistrant.register(with: engineBridge.pluginRegistry)
    let mapsChannel = FlutterMethodChannel(
      name: "com.snaphere.snap_here/maps",
      binaryMessenger: engineBridge.applicationRegistrar.messenger()
    )
    mapsChannel.setMethodCallHandler { [weak self] call, result in
      guard call.method == "configure" else {
        result(FlutterMethodNotImplemented)
        return
      }
      guard let self else { result(false); return }
      if !self.mapsConfigured,
         let arguments = call.arguments as? [String: Any],
         let key = arguments["apiKey"] as? String,
         !key.isEmpty, !key.hasPrefix("your_") {
        self.mapsConfigured = GMSServices.provideAPIKey(key)
      }
      result(self.mapsConfigured)
    }
  }
}
