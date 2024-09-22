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
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.SucceededEvent;
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
import com.vaadin.flow.theme.lumo.LumoUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Set;

import static app.todo.service.PdfGenerator.createPdfResource;

@Route("t")
@PageTitle("Modern Todo App")
public class TodoUI extends VerticalLayout implements HasUrlParameter<String> {

    @Autowired
    private TodoService todoService;

    @Autowired
    private ExcelGenerator excelGenerator;

    @Autowired
    private GridFsTemplate gridFsTemplate;

    private Grid<Todo> todoGrid;
    private VerticalLayout fileLayout;
    private Registration broadcastRegistration;
    private String author;
    private static final Logger logger = LoggerFactory.getLogger(TodoUI.class);

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);

        setSizeFull();
        setPadding(false);
        setSpacing(false);

        initializeHeader();
        initializeContent();
        initializeBroadcastListener(attachEvent);
    }

    private void initializeHeader() {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.addClassNames(
                LumoUtility.Padding.Vertical.MEDIUM,
                LumoUtility.Padding.Horizontal.LARGE,
                LumoUtility.Background.BASE
        );
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H2 title = new H2("Todo Application");
        title.addClassNames(LumoUtility.FontSize.XLARGE, LumoUtility.Margin.NONE);

        Span authorSpan = new Span("by " + author);
        authorSpan.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);

        header.add(title, authorSpan);
        add(header);
    }

    private void initializeContent() {
        VerticalLayout content = new VerticalLayout();
        content.setSizeFull();
        content.setPadding(false);
        content.setSpacing(false);

        initializeActionButtons(content);
        initializeGrid(content);
        initializeFileUploadSection(content);

        add(content);
    }

    private void initializeActionButtons(VerticalLayout content) {
        HorizontalLayout buttonLayout = new HorizontalLayout();
        buttonLayout.addClassNames(LumoUtility.Padding.Horizontal.LARGE, LumoUtility.Padding.Vertical.SMALL);
        buttonLayout.setWidthFull();

        Button addButton = createActionButton("Add Todo", VaadinIcon.PLUS, ButtonVariant.LUMO_PRIMARY);
        addButton.addClickListener(event -> createDialog().open());

        Button removeButton = createActionButton("Remove Selected", VaadinIcon.TRASH, ButtonVariant.LUMO_ERROR);
        removeButton.addClickListener(event -> removeSelectedItems());

        MenuBar exportMenu = new MenuBar();
        exportMenu.addThemeVariants(MenuBarVariant.LUMO_SMALL);

        MenuItem exportMenuItem = exportMenu.addItem("Export");
        exportMenuItem.addComponentAsFirst(new Icon(VaadinIcon.DOWNLOAD));

        MenuItem excelItem = exportMenuItem.getSubMenu().addItem("Export to Excel", e -> exportToExcel());
        excelItem.addComponentAsFirst(new Icon(VaadinIcon.FILE_TABLE));

        MenuItem pdfItem = exportMenuItem.getSubMenu().addItem("Export to PDF", e -> exportToPdf());
        pdfItem.addComponentAsFirst(new Icon(VaadinIcon.FILE_TEXT));

        // Apply some styling to make it look more like a button
        exportMenu.getStyle()
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "var(--lumo-space-xs) var(--lumo-space-s)");

        buttonLayout.add(addButton, removeButton, exportMenu);
        content.add(buttonLayout);
    }

    private Button createActionButton(String text, VaadinIcon icon, ButtonVariant... variants) {
        Button button = new Button(text, new Icon(icon));
        button.addThemeVariants(variants);
        button.addThemeVariants(ButtonVariant.LUMO_SMALL);
        return button;
    }

    private void initializeGrid(VerticalLayout content) {
        todoGrid = new Grid<>();
        todoGrid.setSizeFull();
        todoGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);

        todoGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        todoGrid.addColumn(Todo::getTitle).setHeader("Title").setAutoWidth(true).setFlexGrow(1);
        todoGrid.addColumn(Todo::getBody).setHeader("Body").setAutoWidth(true).setFlexGrow(2);
        todoGrid.addColumn(Todo::getAuthor).setHeader("Author").setAutoWidth(true);
        todoGrid.addColumn(Todo::getCreatedAt).setHeader("Created At").setAutoWidth(true);

        todoGrid.setHeight("50vh");

        refreshGrid();
        content.add(todoGrid);
    }

    private void initializeFileUploadSection(VerticalLayout content) {
        VerticalLayout fileSection = new VerticalLayout();
        fileSection.addClassNames(
                LumoUtility.Padding.LARGE,
                LumoUtility.Background.CONTRAST_5
        );

        H3 fileUploadTitle = new H3("File Attachments");
        fileUploadTitle.addClassNames(LumoUtility.Margin.NONE, LumoUtility.Margin.Bottom.MEDIUM);

        var upload = getUpload();

        fileLayout = new VerticalLayout();
        fileLayout.setSpacing(false);
        refreshFileLayout();

        Button clearFilesButton = new Button("Clear All Files", VaadinIcon.CLOSE.create());
        clearFilesButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        clearFilesButton.addClickListener(e -> clearAllFiles());

        fileSection.add(fileUploadTitle, upload, fileLayout, clearFilesButton);
        content.add(fileSection);
    }

    private Upload getUpload() {
        MultiFileMemoryBuffer buffer = new MultiFileMemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes("application/pdf", "image/jpeg", "image/png");
        upload.setMaxFileSize(10 * 1024 * 1024); // Set max file size to 10MB
        upload.addSucceededListener(event -> handleFileUpload(buffer, event));
        upload.addFailedListener(event -> {
            String errorMessage = event.getReason().getMessage();
            showNotification("Upload failed: " + errorMessage, NotificationVariant.LUMO_ERROR);
            // Log the error
            logger.error("File upload failed: {}, Reason: {}", event.getFileName(), errorMessage);
        });
        return upload;
    }

    private void clearAllFiles() {
        todoService.deleteAllFiles();
        refreshFileLayout();
        showNotification("All files cleared", NotificationVariant.LUMO_SUCCESS);
    }

    private void initializeBroadcastListener(AttachEvent attachEvent) {
        var ui = attachEvent.getUI();
        broadcastRegistration = Broadcastor.register((message) -> {
            if (ui.isAttached()) {
                ui.access(() -> {
                    refreshGrid();
                    showNotification(message, NotificationVariant.LUMO_SUCCESS);
                });
            }
        });
    }

    private void refreshGrid() {
        todoGrid.setItems(todoService.getAllTodos());
    }

    private void refreshFileLayout() {
        fileLayout.removeAll();
        for (GridFSFile file : todoService.getAllFiles()) {
            fileLayout.add(createFileComponent(file));
        }
    }

    private HorizontalLayout createFileComponent(GridFSFile file) {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setAlignItems(FlexComponent.Alignment.CENTER);

        Icon fileIcon = VaadinIcon.FILE_O.create();
        fileIcon.setColor("var(--lumo-primary-color)");

        Span filename = new Span(file.getFilename());
        filename.addClassNames(LumoUtility.FontWeight.MEDIUM);

        Button downloadButton = new Button(new Icon(VaadinIcon.DOWNLOAD));
        downloadButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        downloadButton.addClickListener(event -> downloadFile(file));

        layout.add(fileIcon, filename, downloadButton);
        return layout;
    }

    private void handleFileUpload(MultiFileMemoryBuffer buffer, SucceededEvent event) {
        try {
            InputStream inputStream = new ByteArrayInputStream(buffer.getInputStream(event.getFileName()).readAllBytes());
            gridFsTemplate.store(inputStream, event.getFileName(), event.getMIMEType());
            showNotification("File uploaded successfully", NotificationVariant.LUMO_SUCCESS);
            refreshFileLayout();
        } catch (Exception e) {
            showNotification("Error uploading file: " + e.getMessage(), NotificationVariant.LUMO_ERROR);
        }
    }

    private void downloadFile(GridFSFile file) {
        StreamResource resource = new StreamResource(file.getFilename(), () -> {
            try {
                return gridFsTemplate.getResource(file).getInputStream();
            } catch (IOException e) {
                showNotification("Error downloading file: " + e.getMessage(), NotificationVariant.LUMO_ERROR);
                return null;
            }
        });
        StreamRegistration registration = VaadinSession.getCurrent().getResourceRegistry().registerResource(resource);
        getUI().ifPresent(ui -> ui.getPage().open(registration.getResourceUri().toString(), "_blank"));
    }

    private void removeSelectedItems() {
        Set<Todo> selectedTodos = todoGrid.getSelectedItems();
        if (selectedTodos.isEmpty()) {
            showNotification("Please select at least one item to remove", NotificationVariant.LUMO_CONTRAST);
        } else {
            for (Todo todo : selectedTodos) {
                todoService.deleteTodo(todo);
            }
            refreshGrid();
            //Broadcastor.broadcast("Removed " + selectedTodos.size() + " Todo item(s) by: " + author);
            showNotification(selectedTodos.size() + " item(s) removed successfully", NotificationVariant.LUMO_SUCCESS);
        }
    }

    private void exportToExcel() {
        Set<Todo> selectedTodos = todoGrid.getSelectedItems();
        if (selectedTodos.isEmpty()) {
            showNotification("Please select at least one item to export", NotificationVariant.LUMO_CONTRAST);
        } else {
            try {
                StreamResource resource = excelGenerator.createExcelResource(selectedTodos);
                StreamRegistration registration = VaadinSession.getCurrent().getResourceRegistry().registerResource(resource);
                getUI().ifPresent(ui -> ui.getPage().open(registration.getResourceUri().toString(), "_blank"));
            } catch (Exception e) {
                showNotification("Error exporting to Excel: " + e.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        }
    }

    private void exportToPdf() {
        Set<Todo> selectedTodos = todoGrid.getSelectedItems();
        if (selectedTodos.isEmpty()) {
            showNotification("Please select at least one item to export", NotificationVariant.LUMO_CONTRAST);
        } else {
            try {
                StreamResource resource = createPdfResource(selectedTodos);
                StreamRegistration registration = VaadinSession.getCurrent().getResourceRegistry().registerResource(resource);
                getUI().ifPresent(ui -> ui.getPage().open(registration.getResourceUri().toString(), "_blank"));
            } catch (Exception e) {
                showNotification("Error exporting to PDF: " + e.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        }
    }

    private Dialog createDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("New Todo");

        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.setPadding(false);
        dialogLayout.setSpacing(false);

        TextField titleField = new TextField("Title");
        titleField.setWidthFull();

        TextField bodyField = new TextField("Body");
        bodyField.setWidthFull();

        dialogLayout.add(titleField, bodyField);

        Button saveButton = new Button("Save", e -> saveTodo(titleField, bodyField, dialog));
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button("Cancel", e -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        bodyField.addKeyPressListener(Key.ENTER, event -> saveButton.click());

        dialog.getFooter().add(cancelButton, saveButton);
        dialog.add(dialogLayout);

        return dialog;
    }

    private void saveTodo(TextField titleField, TextField bodyField, Dialog dialog) {
        if (titleField.isEmpty()) {
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
           // Broadcastor.broadcast("Todo Item added by: " + author);
            refreshGrid();
            dialog.close();
            showNotification("Todo item added successfully", NotificationVariant.LUMO_SUCCESS);
        }
    }

    private void showNotification(String message, NotificationVariant variant) {
        Notification notification = Notification.show(message);
        notification.addThemeVariants(variant);
        notification.setPosition(Notification.Position.TOP_CENTER);
        notification.setDuration(3000);
    }

    @Override
    public void setParameter(BeforeEvent event, String parameter) {
        author = event.getLocation().getSegments().get(1);
    }
}