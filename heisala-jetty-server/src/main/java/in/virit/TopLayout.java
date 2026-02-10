package in.virit;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.theme.aura.Aura;
import org.vaadin.firitin.appframework.MainLayout;

@StyleSheet(Aura.STYLESHEET)
public class TopLayout extends MainLayout {
    @Override
    protected Object getDrawerHeader() {
        UI ui = UI.getCurrent();
        ui.addClassName("aura-accent-purple");
        return "Pölsynmäki Jetty @ Heisala";
    }
}
