import AppIntents
import Foundation
#if QUICK_ENTRY_APP
import UIKit
#endif

let quickEntryURL = URL(string: "diary://entry/new")!

@available(iOS 18.0, *)
struct OpenQuickEntryIntent: AppIntent {
    static let title: LocalizedStringResource = "Новая запись"
    static let description = IntentDescription("Открыть запись мысли в дневнике.")
    static let openAppWhenRun = true

    @MainActor
    func perform() async throws -> some IntentResult {
        #if QUICK_ENTRY_APP
        await UIApplication.shared.open(quickEntryURL)
        #endif
        return .result()
    }
}
