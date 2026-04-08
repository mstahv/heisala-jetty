package in.virit;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.Route;
import org.vaadin.firitin.layouts.HorizontalFloatLayout;
import org.vaadin.firitin.util.style.AuraProps;
import org.vaadin.firitin.util.style.VaadinCssProps;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static in.virit.SeaLevelReporter.CALIBRATION_LEVEL_CM;

@Route(value = "water-level", layout = TopLayout.class)
@Menu(title = "Water Level", icon = "vaadin:drop", order = 2)
public class WaterLevelView extends VerticalLayout {

    // Calibration: at these distances (mm), the official water level was -15cm.
    // Based on field measurement 2026-03-14.
    // Radar is primary (less noise), ultrasonic is backup.
    private static final int RADAR_CALIBRATION_DISTANCE_MM = 1175;
    private static final int ULTRASONIC_CALIBRATION_DISTANCE_MM = 1202;

    private final WaterDistanceService service;
    private final WaterLevelPanel waterLevelPanel;
    private final WaterLevelChart waterLevelChart = new WaterLevelChart();
    private final RadioButtonGroup<TimeRange> levelTimeRange = new RadioButtonGroup<>("Time Range");
    private final RawDataPanel rawDataPanel;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> updateTask;

    private enum TimeRange {
        M10("10 min", Duration.ofMinutes(10)),
        H1("1 hour", Duration.ofHours(1)),
        H6("6 hours", Duration.ofHours(6)),
        H24("24 hours", Duration.ofHours(24)),
        D7("7 days", Duration.ofDays(7));

        final String label;
        final Duration duration;

        TimeRange(String label, Duration duration) {
            this.label = label;
            this.duration = duration;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    @Inject
    public WaterLevelView(WaterDistanceService service) {
        this.service = service;

        waterLevelPanel = new WaterLevelPanel();

        levelTimeRange.setItems(TimeRange.values());
        levelTimeRange.setValue(TimeRange.H6);
        levelTimeRange.addValueChangeListener(e -> updateLevelChart());
        add(new HorizontalFloatLayout(waterLevelPanel, levelTimeRange));

        waterLevelChart.setWidthFull();
        add(waterLevelChart);

        rawDataPanel = new RawDataPanel();
        var rawDetails = new Details("Raw sensor data", rawDataPanel);
        rawDetails.setWidthFull();
        add(rawDetails);

        setWidthFull();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        var ui = attachEvent.getUI();
        updateAll();
        updateTask = executor.scheduleAtFixedRate(() -> {
            try {
                ui.access(this::updateAll);
            } catch (Exception e) {
                // UI detached
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        if (updateTask != null) {
            updateTask.cancel(true);
        }
    }

    private void updateAll() {
        waterLevelPanel.update();
        updateLevelChart();
        rawDataPanel.update();
    }

    private void updateLevelChart() {
        TimeRange range = levelTimeRange.getValue();
        if (range == null) range = TimeRange.H6;
        Instant since = Instant.now().minus(range.duration);
        var data = service.getMeasurementsSince(since);
        var downsampled = WaterDistanceService.getDownsampled(data, 800);
        waterLevelChart.updateData(downsampled);
    }

    // --- Calibration ---

    static double radarToWaterLevelCm(int distanceMm) {
        return CALIBRATION_LEVEL_CM + (RADAR_CALIBRATION_DISTANCE_MM - distanceMm) / 10.0;
    }

    static double ultrasonicToWaterLevelCm(int distanceMm) {
        return CALIBRATION_LEVEL_CM + (ULTRASONIC_CALIBRATION_DISTANCE_MM - distanceMm) / 10.0;
    }

    static double bestWaterLevelCm(WaterDistanceService.Measurement m) {
        // Radar is primary — less noise. Ultrasonic only as fallback.
        if (m.hasRadar()) {
            return radarToWaterLevelCm(m.radarMm());
        } else if (m.hasUltrasonic()) {
            return ultrasonicToWaterLevelCm(m.ultrasonicMm());
        }
        return Double.NaN;
    }

    private static final long SMOOTHING_WINDOW_MS = 60_000; // 1 minute

    /**
     * Weighted moving average with 1-minute window. Recent measurements
     * get linearly higher weight.
     */
    static double[] smoothedWaterLevels(List<WaterDistanceService.Measurement> data) {
        double[] raw = new double[data.size()];
        for (int i = 0; i < data.size(); i++) {
            raw[i] = bestWaterLevelCm(data.get(i));
        }
        double[] smoothed = new double[data.size()];
        for (int i = 0; i < data.size(); i++) {
            long t = data.get(i).epochMillis();
            double weightSum = 0;
            double valueSum = 0;
            for (int j = i; j >= 0; j--) {
                long age = t - data.get(j).epochMillis();
                if (age > SMOOTHING_WINDOW_MS) break;
                if (Double.isNaN(raw[j])) continue;
                double weight = 1.0 - (double) age / SMOOTHING_WINDOW_MS;
                weightSum += weight;
                valueSum += weight * raw[j];
            }
            smoothed[i] = weightSum > 0 ? valueSum / weightSum : Double.NaN;
        }
        return smoothed;
    }

    // --- Water Level Panel (main display for users) ---

    private class WaterLevelPanel extends Div {
        private final Span levelValue = new Span("--");
        private final Span levelUnit = new Span(" cm");
        private final Span timestampLabel = new Span("No data yet");

        WaterLevelPanel() {
            var title = new Span("Relative to normal");
            title.getStyle()
                    .setFontSize(AuraProps.FONT_SIZE_S.var())
                    .setColor(VaadinCssProps.TEXT_COLOR_SECONDARY.var())
                    .setDisplay(Style.Display.BLOCK);

            levelValue.getStyle()
                    .setFontSize("3em")
                    .setFontWeight("bold")
                    .setDisplay(Style.Display.INLINE);

            levelUnit.getStyle()
                    .setFontSize(AuraProps.FONT_SIZE_L.var())
                    .setColor(VaadinCssProps.TEXT_COLOR_SECONDARY.var());

            timestampLabel.getStyle()
                    .setColor(VaadinCssProps.TEXT_COLOR_SECONDARY.var())
                    .setFontSize(AuraProps.FONT_SIZE_S.var())
                    .setDisplay(Style.Display.BLOCK);

            add(title, new Div(levelValue, levelUnit){{
                getStyle().setLineHeight("4em");
            }}, timestampLabel);
            getStyle()
                    .setPadding(VaadinCssProps.PADDING_L.var())
                    .setBorderRadius(VaadinCssProps.RADIUS_L.var())
                    .setBackground(VaadinCssProps.BACKGROUND_CONTAINER.var())
                    .setTextAlign(Style.TextAlign.CENTER)
                    .setMinWidth("200px");
        }

        void update() {
            // Try smoothed value from last minute; fall back to latest reading
            var recent = service.getMeasurementsSince(
                    Instant.now().minus(Duration.ofMillis(SMOOTHING_WINDOW_MS)));
            double level;
            WaterDistanceService.Measurement latest;
            if (!recent.isEmpty()) {
                double[] smoothed = smoothedWaterLevels(recent);
                level = smoothed[smoothed.length - 1];
                latest = recent.getLast();
            } else {
                latest = service.getLatest();
                if (latest == null) return;
                level = bestWaterLevelCm(latest);
            }
            if (Double.isNaN(level)) {
                levelValue.setText("--");
            } else {
                String sign = level >= 0 ? "+" : "";
                levelValue.setText(sign + String.format("%.1f", level));
                levelValue.getStyle().setColor(
                        level > 5 ? AuraProps.ACCENT_TEXT_COLOR.var()
                                : level < -30 ? AuraProps.RED_TEXT.var()
                                : VaadinCssProps.TEXT_COLOR.var());
            }
            LocalDateTime time = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(latest.epochMillis()), ZoneId.systemDefault());
            timestampLabel.setText(time.format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
    }

    // --- Burst summary for grid display ---

    record BurstSummary(long batchId, long firstEpochMillis, long lastEpochMillis,
                        int avgRadarMm, int avgUltrasonicMm, int wifiRssi, int count,
                        WaterDistanceService.Transport transport) {

        static List<BurstSummary> fromRawMeasurements(List<WaterDistanceService.RawMeasurement> raw) {
            var byBatch = new java.util.LinkedHashMap<Long, List<WaterDistanceService.RawMeasurement>>();
            for (var m : raw) {
                byBatch.computeIfAbsent(m.batchId(), k -> new java.util.ArrayList<>()).add(m);
            }
            var result = new java.util.ArrayList<BurstSummary>(byBatch.size());
            for (var entry : byBatch.entrySet()) {
                var readings = entry.getValue();
                long first = readings.getFirst().epochMillis();
                long last = readings.getLast().epochMillis();
                int radarSum = 0, radarCount = 0;
                int usSum = 0, usCount = 0;
                for (var r : readings) {
                    if (r.radarMm() > 0) { radarSum += r.radarMm(); radarCount++; }
                    if (r.ultrasonicMm() > 0) { usSum += r.ultrasonicMm(); usCount++; }
                }
                int avgRadar = radarCount > 0 ? radarSum / radarCount : -1;
                int avgUs = usCount > 0 ? usSum / usCount : -1;
                int rssi = readings.getFirst().wifiRssi();
                var transport = readings.getFirst().transport();
                result.add(new BurstSummary(entry.getKey(), first, last, avgRadar, avgUs, rssi, readings.size(), transport));
            }
            return result;
        }
    }

    // --- Raw Data Panel (hidden in Details for debugging) ---

    private class RawDataPanel extends VerticalLayout {
        private final ReadingCard radarCard = new ReadingCard("Radar");
        private final ReadingCard ultrasonicCard = new ReadingCard("Ultrasonic");
        private final ReadingCard wifiCard = new ReadingCard("WiFi Signal");
        private final ReadingCard missedCard = new ReadingCard("Missed");
        private final Span rawTimestamp = new Span("No data yet");
        private final RawDistanceChart rawChart = new RawDistanceChart();
        private final RadioButtonGroup<TimeRange> rawTimeRange = new RadioButtonGroup<>("Time Range");
        private final Grid<BurstSummary> burstGrid = new Grid<>();
        private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        RawDataPanel() {
            setAlignItems(Alignment.CENTER);
            setWidthFull();
            setPadding(false);

            var readingsRow = new HorizontalLayout(radarCard, ultrasonicCard, wifiCard, missedCard, rawTimestamp) {{
                setAlignItems(Alignment.CENTER);
                setJustifyContentMode(JustifyContentMode.CENTER);
                setSpacing(true);
            }};
            rawTimestamp.getStyle()
                    .setColor(VaadinCssProps.TEXT_COLOR_SECONDARY.var())
                    .setFontSize(AuraProps.FONT_SIZE_S.var());

            rawTimeRange.setItems(TimeRange.values());
            rawTimeRange.setValue(TimeRange.H6);
            rawTimeRange.addValueChangeListener(e -> updateRawData());

            var refreshButton = new Button(VaadinIcon.REFRESH.create(), e -> update());

            rawChart.setWidthFull();

            burstGrid.addColumn(b -> LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(b.firstEpochMillis()), ZoneId.systemDefault())
                    .format(TIME_FMT)).setHeader("Time");
            burstGrid.addColumn(BurstSummary::avgRadarMm).setHeader("Avg Radar (mm)");
            burstGrid.addColumn(BurstSummary::avgUltrasonicMm).setHeader("Avg Ultrasonic (mm)");
            burstGrid.addColumn(b -> b.transport().name()).setHeader("Transport");
            burstGrid.addColumn(b -> b.transport() == WaterDistanceService.Transport.BLE
                    ? "--" : b.wifiRssi() + " dBm").setHeader("WiFi RSSI");
            burstGrid.addColumn(BurstSummary::count).setHeader("Readings");
            burstGrid.addComponentColumn(b -> {
                if (b.batchId() == 0) {
                    return new Span("--");
                }
                var btn = new Button("Details", e -> openBurstDialog(b.batchId()));
                btn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
                return btn;
            }).setHeader("Burst");
            burstGrid.setWidthFull();
            burstGrid.setHeight("400px");

            var controlsRow = new HorizontalLayout(rawTimeRange, refreshButton) {{
                setAlignItems(Alignment.BASELINE);
            }};
            add(readingsRow, controlsRow, rawChart, burstGrid);
        }

        void update() {
            var m = service.getLatestRaw();
            if (m != null) {
                radarCard.setValue(m.radarMm() + " mm");
                ultrasonicCard.setValue(m.ultrasonicMm() + " mm");
                wifiCard.setValue(m.transport() == WaterDistanceService.Transport.BLE
                        ? "BLE" : m.wifiRssi() + " dBm");
                LocalDateTime time = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(m.epochMillis()), ZoneId.systemDefault());
                rawTimestamp.setText("Last: " + time.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            }
            updateRawData();
        }

        private void openBurstDialog(long batchId) {
            var burstData = service.getRawMeasurementsByBatch(batchId);
            var dialog = new Dialog();
            dialog.setHeaderTitle("Burst #" + batchId + " (" + burstData.size() + " readings)");
            dialog.setWidth("700px");

            var grid = new Grid<>(WaterDistanceService.RawMeasurement.class, false);
            grid.addColumn(m -> LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(m.epochMillis()), ZoneId.systemDefault())
                    .format(TIME_FMT)).setHeader("Time");
            grid.addColumn(WaterDistanceService.RawMeasurement::radarMm).setHeader("Radar (mm)");
            grid.addColumn(WaterDistanceService.RawMeasurement::ultrasonicMm).setHeader("Ultrasonic (mm)");
            grid.setItems(burstData);
            grid.setWidthFull();
            grid.setAllRowsVisible(true);

            dialog.add(grid);
            dialog.getFooter().add(new Button("Close", e -> dialog.close()));
            dialog.open();
        }

        private void updateRawData() {
            TimeRange range = rawTimeRange.getValue();
            if (range == null) range = TimeRange.H6;
            Instant since = Instant.now().minus(range.duration);

            missedCard.setLabel("Missed (" + range.label + ")");
            int missed = service.estimateMissedBursts(since);
            int received = service.countMeasurementsSince(since);
            int expected = received + missed;
            String pct = expected > 0 ? String.format(" (%.1f%%)", 100.0 * missed / expected) : "";
            missedCard.setValue(missed + pct);

            var rawData = service.getRawMeasurementsSince(since);
            rawChart.updateData(rawData);

            // Group by burst, most recent first
            var bursts = BurstSummary.fromRawMeasurements(rawData);
            Collections.reverse(bursts);
            burstGrid.setItems(bursts);
        }
    }

    private static class ReadingCard extends Div {
        private final Span titleSpan;
        private final Span valueLabel;

        ReadingCard(String title) {
            titleSpan = new Span(title);
            titleSpan.getStyle()
                    .setFontSize(AuraProps.FONT_SIZE_S.var())
                    .setColor(VaadinCssProps.TEXT_COLOR_SECONDARY.var())
                    .setDisplay(Style.Display.BLOCK);

            valueLabel = new Span("--");
            valueLabel.getStyle()
                    .setFontSize(AuraProps.FONT_SIZE_XL.var())
                    .setFontWeight("bold")
                    .setDisplay(Style.Display.BLOCK);

            add(titleSpan, valueLabel);
            getStyle()
                    .setPadding(VaadinCssProps.PADDING_M.var())
                    .setBorderRadius(VaadinCssProps.RADIUS_M.var())
                    .setBackground(VaadinCssProps.BACKGROUND_CONTAINER.var())
                    .setTextAlign(Style.TextAlign.CENTER)
                    .setMinWidth("140px");
        }

        void setLabel(String label) {
            titleSpan.setText(label);
        }

        void setValue(String value) {
            valueLabel.setText(value);
        }
    }

    // --- Water Level Chart (calibrated, for users) ---

    private static class WaterLevelChart extends Chart {
        WaterLevelChart() {
            super(ChartType.SPLINE);
            Configuration conf = getConfiguration();
            conf.setTitle((String) null);
            conf.getLegend().setEnabled(false);
            conf.getyAxis().setTitle("Water level (cm)");

            PlotLine zeroLine = new PlotLine();
            zeroLine.setValue(0);
            zeroLine.setDashStyle(DashStyle.DASH);
            zeroLine.setWidth(1);
            Label zeroLabel = new Label("normal");
            zeroLabel.setAlign(HorizontalAlign.RIGHT);
            zeroLine.setLabel(zeroLabel);
            conf.getyAxis().addPlotLine(zeroLine);

            conf.getxAxis().setType(AxisType.DATETIME);

            PlotOptionsLine plotOptions = new PlotOptionsLine();
            plotOptions.setMarker(new Marker(false));
            conf.setPlotOptions(plotOptions);

            Tooltip tooltip = new Tooltip();
            tooltip.setValueSuffix(" cm");
            conf.setTooltip(tooltip);

            conf.getChart().setStyledMode(false);
            conf.setExporting(false);
            conf.getTime().setUseUTC(false);
        }

        void updateData(List<WaterDistanceService.Measurement> data) {
            Configuration conf = getConfiguration();
            double[] levels = smoothedWaterLevels(data);
            DataSeries series = new DataSeries("Water level");
            for (int i = 0; i < data.size(); i++) {
                DataSeriesItem item = new DataSeriesItem();
                item.setX(data.get(i).epochMillis());
                item.setY(Double.isNaN(levels[i]) ? null : levels[i]);
                series.add(item);
            }
            conf.setSeries(series);
            drawChart();
        }
    }

    // --- Raw Distance Chart (for debugging) ---

    private static class RawDistanceChart extends Chart {
        RawDistanceChart() {
            super(ChartType.LINE);
            Configuration conf = getConfiguration();
            conf.setTitle((String) null);
            conf.getyAxis().setTitle("Distance (mm)");
            conf.getxAxis().setType(AxisType.DATETIME);

            Tooltip tooltip = new Tooltip();
            tooltip.setShared(true);
            tooltip.setValueSuffix(" mm");
            conf.setTooltip(tooltip);

            conf.getChart().setStyledMode(false);
            conf.setExporting(false);
            conf.getTime().setUseUTC(false);
        }

        void updateData(List<WaterDistanceService.RawMeasurement> data) {
            // Downsample for chart performance
            List<WaterDistanceService.RawMeasurement> chartData = data;
            if (data.size() > 800) {
                chartData = new java.util.ArrayList<>(800);
                double step = (double) (data.size() - 1) / 799;
                for (int i = 0; i < 800; i++) {
                    chartData.add(data.get((int) Math.round(i * step)));
                }
            }

            Configuration conf = getConfiguration();
            DataSeries radarSeries = new DataSeries("Radar");
            DataSeries ultrasonicSeries = new DataSeries("Ultrasonic");

            PlotOptionsLine ultrasonicOptions = new PlotOptionsLine();
            ultrasonicOptions.setDashStyle(DashStyle.SHORTDASH);
            ultrasonicOptions.setMarker(new Marker(false));
            ultrasonicSeries.setPlotOptions(ultrasonicOptions);

            PlotOptionsLine radarOptions = new PlotOptionsLine();
            radarOptions.setMarker(new Marker(false));
            radarSeries.setPlotOptions(radarOptions);

            for (var m : chartData) {
                DataSeriesItem radarItem = new DataSeriesItem();
                radarItem.setX(m.epochMillis());
                radarItem.setY(m.radarMm() > 0 ? (Number) m.radarMm() : null);
                radarSeries.add(radarItem);

                DataSeriesItem usItem = new DataSeriesItem();
                usItem.setX(m.epochMillis());
                usItem.setY(m.ultrasonicMm() > 0 ? (Number) m.ultrasonicMm() : null);
                ultrasonicSeries.add(usItem);
            }

            conf.setSeries(radarSeries, ultrasonicSeries);
            drawChart();
        }
    }
}
