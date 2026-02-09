package in.virit;

import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.Lumo;

@Route
@StyleSheet(Lumo.STYLESHEET)
public class MainView extends VerticalLayout {

    public MainView() {
        add(new H1("Jetty @ Heisala"));
        add("It works!? TODO:");

        add(new UnorderedList(){{
            add(new ListItem("Web cam"));
            add(new ListItem("Web cam rotations with servo motor"));
            add(new ListItem("Automatic timelapse videos"));
            add(new ListItem("Water level sensor using a radar module"));
            add(new ListItem("API/communication to layer to Catch-A-Fish application"));
        }});

    }
}
