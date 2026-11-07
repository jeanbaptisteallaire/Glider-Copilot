import SwiftUI

/// Jetons de la charte (miroir de core/designsystem/Theme.kt). Ne jamais coder une couleur ou une police dans un écran.
enum WG {
    static let brandInk = Color(red: 0x1D / 255, green: 0x35 / 255, blue: 0x50 / 255)    // #1D3550
    static let brandPaper = Color(red: 0xF6 / 255, green: 0xF3 / 255, blue: 0xEE / 255)  // #F6F3EE
    static let sky = Color(red: 0x0A / 255, green: 0x84 / 255, blue: 0xFF / 255)         // #0A84FF sélection
    static let card = Color.white

    static let largeTitle = Font.custom("Inter", size: 34, relativeTo: .largeTitle).weight(.bold)
    static let headline = Font.custom("Inter", size: 17, relativeTo: .headline).weight(.semibold)
    static let body = Font.custom("Inter", size: 17, relativeTo: .body)
    static let footnote = Font.custom("Inter", size: 13, relativeTo: .footnote)
    static let caption = Font.custom("Inter", size: 12, relativeTo: .caption)
}
