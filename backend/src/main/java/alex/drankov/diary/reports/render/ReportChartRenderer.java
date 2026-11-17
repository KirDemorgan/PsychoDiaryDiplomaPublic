package alex.drankov.diary.reports.render;

import alex.drankov.diary.analytics.dto.DailyValueResponse;
import alex.drankov.diary.analytics.entity.Metric;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

@Component
public class ReportChartRenderer {
    public String chart(
            List<DailyValueResponse> days,
            Metric metric
    ) {
        var image = new BufferedImage(1200, 390, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 1200, 390);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
            int left = 60;
            int right = 1170;
            int top = 20;
            int bottom = 325;
            for (int value = 0; value <= 10; value += 2) {
                int y = bottom - value * (bottom - top) / 10;
                g.setColor(new Color(228, 234, 238));
                g.drawLine(left, y, right, y);
                g.setColor(new Color(85, 100, 117));
                g.drawString(String.valueOf(value), 22, y + 8);
            }
            Color color = switch (metric) {
                case ANXIETY -> new Color(182, 94, 72);
                case MOOD -> new Color(67, 105, 167);
                case ENERGY -> new Color(33, 127, 115);
            };
            g.setStroke(new BasicStroke(4));
            Integer previousX = null;
            Integer previousY = null;
            for (int i = 0; i < days.size(); i++) {
                var day = days.get(i);
                var mean = switch (metric) {
                    case ANXIETY -> day.anxietyMean();
                    case MOOD -> day.moodMean();
                    case ENERGY -> day.energyMean();
                };
                int x = days.size() == 1 ? (left + right) / 2 : left + i * (right - left) / (days.size() - 1);
                if (mean == null) {
                    previousX = null;
                    previousY = null;
                } else {
                    int y = bottom - (int) Math.round(mean.doubleValue() * (bottom - top) / 10);
                    g.setColor(color);
                    if (previousX != null) {
                        g.drawLine(previousX, previousY, x, y);
                    }
                    g.fillOval(x - 5, y - 5, 10, 10);
                    previousX = x;
                    previousY = y;
                }
                int interval = Math.max(1, (days.size() + 5) / 6);
                if (i % interval == 0 || i == days.size() - 1) {
                    String date = day.date().format(DateTimeFormatter.ofPattern("dd.MM"));
                    g.setColor(new Color(85, 100, 117));
                    g.drawString(date, Math.max(0, Math.min(x - 30, 1115)), 365);
                }
            }
        } finally {
            g.dispose();
        }
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Chart rendering failed", exception);
        }
    }
}
