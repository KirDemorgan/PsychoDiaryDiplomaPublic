import SwiftUI
import WidgetKit

struct QuickEntryEntry: TimelineEntry {
    let date: Date
}

struct QuickEntryProvider: TimelineProvider {
    func placeholder(in context: Context) -> QuickEntryEntry {
        QuickEntryEntry(date: .now)
    }

    func getSnapshot(in context: Context, completion: @escaping (QuickEntryEntry) -> Void) {
        completion(QuickEntryEntry(date: .now))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<QuickEntryEntry>) -> Void) {
        completion(Timeline(entries: [QuickEntryEntry(date: .now)], policy: .never))
    }
}

struct QuickEntryView: View {
    @Environment(\.widgetFamily) private var family

    var body: some View {
        content
            .widgetURL(quickEntryURL)
            .containerBackground(for: .widget) { background }
    }

    @ViewBuilder
    private var content: some View {
        switch family {
        case .accessoryCircular:
            Image(systemName: "square.and.pencil")
                .font(.title2.weight(.semibold))
                .accessibilityLabel("Новая запись")
        case .accessoryRectangular:
            HStack(spacing: 8) {
                Image(systemName: "square.and.pencil")
                    .font(.title3.weight(.semibold))
                VStack(alignment: .leading, spacing: 2) {
                    Text("Новая запись").font(.headline)
                    Text("Записать мысль").font(.caption)
                }
                Spacer(minLength: 0)
            }
        default:
            VStack(alignment: .leading, spacing: 6) {
                Image(systemName: "square.and.pencil")
                    .font(.title.weight(.semibold))
                    .foregroundStyle(.white)
                Spacer(minLength: 0)
                Text("Новая запись")
                    .font(.headline)
                    .foregroundStyle(.white)
                Text("Записать мысль")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.85))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    @ViewBuilder
    private var background: some View {
        switch family {
        case .accessoryCircular, .accessoryRectangular:
            AccessoryWidgetBackground()
        default:
            LinearGradient(
                colors: [Color("gradientStart"), Color("gradientEnd")],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }
    }
}

struct QuickEntryWidget: Widget {
    let kind = "QuickEntryWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: QuickEntryProvider()) { _ in
            QuickEntryView()
        }
        .configurationDisplayName("Новая запись")
        .description("Быстро открыть запись мысли в дневнике.")
        .supportedFamilies([.systemSmall, .accessoryCircular, .accessoryRectangular])
    }
}
