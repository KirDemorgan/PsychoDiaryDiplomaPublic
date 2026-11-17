import SwiftUI
import WidgetKit

@main
struct DiaryWidgetBundle: WidgetBundle {
    var body: some Widget {
        QuickEntryWidget()
        QuickEntryControl()
    }
}
