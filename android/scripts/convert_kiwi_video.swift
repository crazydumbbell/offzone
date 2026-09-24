import AVFoundation
import ImageIO
import UniformTypeIdentifiers
try FileManager.default.createDirectory(atPath: "/tmp/offzone-kiwi-frames", withIntermediateDirectories: true)
let asset = AVURLAsset(url: URL(fileURLWithPath: CommandLine.arguments[1]))
let generator = AVAssetImageGenerator(asset: asset)
generator.appliesPreferredTrackTransform = true
generator.maximumSize = CGSize(width: 360, height: 282)
generator.requestedTimeToleranceBefore = .zero
generator.requestedTimeToleranceAfter = .zero
for i in 0..<191 {
    let image = try generator.copyCGImage(at: CMTime(value: Int64(i * 2), timescale: 24), actualTime: nil)
    let url = URL(fileURLWithPath: String(format:"/tmp/offzone-kiwi-frames/%03d.png", i))
    let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(destination, image, nil)
    guard CGImageDestinationFinalize(destination) else { fatalError("PNG write failed") }
}
