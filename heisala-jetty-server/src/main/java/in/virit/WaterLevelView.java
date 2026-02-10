package in.virit;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.Route;

@Route(value = "water-level", layout = TopLayout.class)
@Menu(title = "Water Level", icon = "vaadin:drop", order = 2)
public class WaterLevelView extends VerticalLayout {

    public WaterLevelView() {
        add(new H1("Water Level Monitoring"));

        add(new Paragraph("""
            This view will display water level data from the Heisala jetty area.
            """));

        add(new Paragraph("""
            Planned features:
            """));

        add(new Paragraph("- Real-time water level display"));
        add(new Paragraph("- Historical water level chart"));
        add(new Paragraph("- Alerts for high/low water levels"));

        add(new Paragraph("TODO: Implement water level sensor integration and data visualization."));

        setSizeFull();
        setAlignItems(Alignment.CENTER);
    }
}
