// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "mobile_platform",
    platforms: [.iOS("18.4")],
    products: [
        .library(name: "mobile-platform", targets: ["mobile_platform"])
    ],
    dependencies: [
        .package(name: "FlutterFramework", path: "../FlutterFramework")
    ],
    targets: [
        .target(
            name: "mobile_platform",
            dependencies: [.product(name: "FlutterFramework", package: "FlutterFramework")]
        )
    ]
)
