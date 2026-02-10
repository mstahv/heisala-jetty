package in.virit;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.markdown.Markdown;

/**
 * A help button that displays markdown content in a dialog when clicked.
 */
public class HelpButton extends Button {

    public HelpButton(String markdownContent) {
        super(VaadinIcon.QUESTION_CIRCLE.create());
        addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        addClickListener(e -> new Dialog() {{
            setHeaderTitle("Help");
            setWidth("600px");
            setMaxHeight("80vh");
            add(new Markdown(markdownContent));
            getFooter().add(new Button("Close", ce -> close()) {{
                addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            }});
        }}.open());
    }
}
