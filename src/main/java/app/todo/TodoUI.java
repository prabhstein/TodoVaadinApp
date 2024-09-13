package app.todo;

import app.todo.model.Todo;
import app.todo.service.Broadcastor;
import app.todo.service.ExcelGenerator;
import app.todo.service.TodoService;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MultiFileMemoryBuffer;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamRegistration;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.shared.Registration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Set;

import static app.todo.service.PdfGenerator.createPdfResource;

@Route("t")
@PageTitle("Todo App")
public class TodoUI extends VerticalLayout implements HasUrlParameter<String> {

    @Autowired
    private TodoService todoService;

    @Autowired
    private ExcelGenerator excelGenerator;

    @Autowired
    private GridFsTemplate gridFsTemplate;

    private Grid<Todo> view;
    private VerticalLayout fileLayout;
    private Registration broadcastRegistration;
    private String author;

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);

        // Initialize UI components
        initializeHeader();
        initializeButtons();
        initializeGrid();
        initializeFileUpload();
        initializeFileLayout();

        // Register broadcast listener
        var ui = attachEvent.getUI();
        broadcastRegistration = Broadcastor.register((message) -> ui.access(() -> {
            refreshView();
            Notification.show(message);
        }));
    }

    private void initializeHeader() {
        var h2 = new H2("Todo Application by: " + author);
        add(h2);
    }

    private void initializeButtons() {
        var addButton = createAddButton();
        var removeSelectedItemsButton = createRemoveSelectedItemsButton();
        var exportButton = getExportButton();
        var exportSelectedToPdf = createExportSelectedToPdfButton();

        add(new HorizontalLayout(addButton, removeSelectedItemsButton, exportButton, exportSelectedToPdf));
    }

    private Button createAddButton() {
        var addButton = new Button("Add new Item");
        addButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_SMALL);
        addButton.addClickShortcut(Key.ENTER);
        addButton.addClickListener(event -> createDialog().open());
        return addButton;
    }

    private Button createRemoveSelectedItemsButton() {
        var removeSelectedItemsButton = new Button("remove selected items");
        removeSelectedItemsButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
        removeSelectedItemsButton.addClickListener(event -> {
            todoService.getAllTodos().removeAll(view.getSelectedItems());
            Broadcastor.broadcast("removed Todo Item by: " + author);
        });
        return removeSelectedItemsButton;
    }

    private Button createExportSelectedToPdfButton() {
        var exportSelectedToPdf = new Button("Export selected to PDF");
        exportSelectedToPdf.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        exportSelectedToPdf.addClickListener(event -> {
            Set<Todo> selectedTodos = view.getSelectedItems();
            if (selectedTodos.isEmpty()) {
                Notification.show("Please select at least one item to export.", 3000, Notification.Position.MIDDLE);
            } else {
                try {
                    StreamResource resource = createPdfResource(selectedTodos);
                    StreamRegistration registration = VaadinSession.getCurrent().getResourceRegistry().registerResource(resource);
                    com.vaadin.flow.component.UI.getCurrent().getPage().open(registration.getResourceUri().toString(), "_blank");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Notification.show("Error while exporting to PDF.", 3000, Notification.Position.MIDDLE);
                }
            }
        });
        return exportSelectedToPdf;
    }

    private void initializeGrid() {
        view = new Grid<>();
        view.setSelectionMode(Grid.SelectionMode.MULTI);
        view.addColumn(Todo::getTitle).setHeader("Title");
        view.addColumn(Todo::getBody).setHeader("Body");
        view.addColumn(Todo::getAuthor).setHeader("Author");
        view.addColumn(Todo::getCreatedAt).setHeader("CreatedAt");
        refreshView();
        add(view);

        var selectAllButton = createSelectAllButton();
        var deSelectAllButton = createDeselectAllButton();
        add(new HorizontalLayout(selectAllButton, deSelectAllButton));
    }

    private Button createSelectAllButton() {
        var selectAllButton = new Button("select all");
        selectAllButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        selectAllButton.setIcon(VaadinIcon.PLUS.create());
        selectAllButton.addClickListener(event -> view.asMultiSelect().select(todoService.getAllTodos()));
        return selectAllButton;
    }

    private Button createDeselectAllButton() {
        var deSelectAllButton = new Button("deselect all");
        deSelectAllButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        deSelectAllButton.setIcon(VaadinIcon.PLUS.create());
        deSelectAllButton.addClickListener(event -> view.deselectAll());
        return deSelectAllButton;
    }

    private void initializeFileUpload() {
        MultiFileMemoryBuffer buffer = new MultiFileMemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes("application/pdf", "image/jpeg", "image/png");
        upload.addSucceededListener(event -> {
            try {
                InputStream inputStream = new ByteArrayInputStream(buffer.getInputStream(event.getFileName()).readAllBytes());
                gridFsTemplate.store(inputStream, event.getFileName(), event.getMIMEType());
                Notification.show("File uploaded successfully", 3000, Notification.Position.MIDDLE);
                refreshFileLayout();
            } catch (Exception e) {
                Notification.show("Error uploading file: " + e.getMessage(), 3000, Notification.Position.MIDDLE);
            }
        });
        add(upload);
    }

    private void initializeFileLayout() {
        fileLayout = new VerticalLayout();
        refreshFileLayout();
        add(fileLayout);
    }

    private void refreshFileLayout() {
        fileLayout.removeAll();
        for (GridFSFile file : todoService.getAllFiles()) {
            HorizontalLayout layout = new HorizontalLayout();

            // File icon
            Icon fileIcon = VaadinIcon.FILE.create();

            // Filename
            Span filename = new Span(file.getFilename());

            // Download icon
            Icon downloadIcon = VaadinIcon.DOWNLOAD.create();
            downloadIcon.getStyle().set("cursor", "pointer");
            downloadIcon.getStyle().set("color", "black");
            downloadIcon.getStyle().set("font-size", "16px");
            downloadIcon.getStyle().set("margin-left", "5px");
            downloadIcon.addClickListener(event -> {
                StreamResource resource = new StreamResource(file.getFilename(), () -> {
                    try {
                        return gridFsTemplate.getResource(file).getInputStream();
                    } catch (IOException e) {
                        Notification.show("Error downloading file: " + e.getMessage(), 3000, Notification.Position.MIDDLE);
                        return null;
                    }
                });
                StreamRegistration registration = VaadinSession.getCurrent().getResourceRegistry().registerResource(resource);
                com.vaadin.flow.component.UI.getCurrent().getPage().open(registration.getResourceUri().toString(), "_blank");
            });

            layout.add(fileIcon, filename, downloadIcon);
            fileLayout.add(layout);
        }
    }

    private Button getExportButton() {
        var exportButton = new Button("export selected items to MS Excel");
        exportButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        exportButton.addClickListener(event -> {
            Set<Todo> selectedTodos = view.getSelectedItems();
            if (selectedTodos.isEmpty()) {
                Notification.show("Please select at least one item to export to file", 3000, Notification.Position.TOP_CENTER);
            } else {
                try {
                    StreamResource resource = excelGenerator.createExcelResource(selectedTodos);
                    StreamRegistration registration = VaadinSession.getCurrent().getResourceRegistry().registerResource(resource);
                    com.vaadin.flow.component.UI.getCurrent().getPage().open(registration.getResourceUri().toString(), "_blank");
                } catch (Exception e) {
                    e.printStackTrace();
                    Notification.show("Error while exporting to Excel", 3000, Notification.Position.MIDDLE);
                }
            }
        });
        return exportButton;
    }

    private void refreshView() {
        view.setItems(todoService.getAllTodos());
    }

    @Override
    public void setParameter(BeforeEvent event, String parameter) {
        author = event.getLocation().getSegments().get(1);
    }

    public Dialog createDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle("New Todo");

        var verticalLayout = new VerticalLayout();

        var titleField = new TextField("title");
        titleField.setRequiredIndicatorVisible(true);
        var bodyField = new TextField("body");
        bodyField.setRequired(true);

        verticalLayout.add(titleField);
        verticalLayout.add(bodyField);

        var saveBtn = createSaveButton(titleField, bodyField, dialog);
        bodyField.addKeyPressListener(Key.ENTER, event -> saveBtn.click());

        var cancelButton = new Button("Cancel");
        cancelButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        cancelButton.addClickListener(event -> dialog.close());

        var horizontalLayout = new HorizontalLayout(saveBtn, cancelButton);
        horizontalLayout.setWidthFull();
        horizontalLayout.setSpacing(true);

        dialog.getFooter().add(horizontalLayout);
        dialog.add(verticalLayout);

        return dialog;
    }

    private Button createSaveButton(TextField titleField, TextField bodyField, Dialog dialog) {
        var saveBtn = new Button("add");
        saveBtn.addClickListener(event -> {
            if (titleField.isEmpty()) {
                Notification.show("Title field cannot be empty", 3000, Notification.Position.MIDDLE);
                titleField.focus();
                titleField.setInvalid(true);
                titleField.setErrorMessage("Please enter a title");
            } else {
                Todo todo = Todo.builder()
                        .title(titleField.getValue())
                        .body(bodyField.getValue())
                        .author(author)
                        .createdAt(LocalDateTime.now())
                        .build();
                todoService.saveTodo(todo);
                Broadcastor.broadcast("Todo Item added by: " + author);
                dialog.close();
            }
        });
        return saveBtn;
    }
}