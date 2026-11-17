import AppIntents
import SwiftUI
import WidgetKit

struct QuickEntryControl: ControlWidget {
    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: "com.demorgan.diary.widget.quick-entry") {
            ControlWidgetButton(action: OpenQuickEntryIntent()) {
                Label("Новая запись", systemImage: "square.and.pencil")
            }
        }
        .displayName("Новая запись")
        .description("Открыть запись мысли в дневнике.")
    }
}
