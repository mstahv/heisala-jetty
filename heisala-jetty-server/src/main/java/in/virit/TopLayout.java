package in.virit;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.theme.aura.Aura;
import org.vaadin.firitin.appframework.MainLayout;

@StyleSheet(Aura.STYLESHEET)
public class TopLayout extends MainLayout {

    @Override
    protected Object getDrawerHeader() {
        UI ui = UI.getCurrent();
        ui.addClassName("aura-accent-purple");

        VerticalLayout header = new VerticalLayout();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setPadding(false);
        header.setSpacing(false);
        header.getStyle().setPaddingTop("var(--lumo-space-m)");

        Image logo = new Image("icons/jetty-logo.svg", "Jetty Logo");
        logo.setWidth("140px");
        logo.setHeight("120px");

        Span title = new Span("Pölsynmäki Jetty");
        title.getStyle()
            .setFontSize("var(--lumo-font-size-l)")
            .setFontWeight("600")
            .setMarginTop("var(--lumo-space-s)");

        Span subtitle = new Span("@ Heisala");
        subtitle.getStyle()
            .setFontSize("var(--lumo-font-size-s)")
            .setColor("var(--lumo-secondary-text-color)");

        header.add(logo, title, subtitle);
        return header;
    }
}
