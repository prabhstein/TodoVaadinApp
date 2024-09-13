package app.todo.service;

import app.todo.model.Todo;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.vaadin.flow.server.StreamResource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.Set;

public class PdfGenerator {

    public static StreamResource createPdfResource(Set<Todo> selectedTodos) {
        return new StreamResource("todos.pdf", () -> {
            try {
                return createPdfFile(selectedTodos);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    private static InputStream createPdfFile(Set<Todo> selectedTodos) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Initialize PDF document
        PdfWriter writer = new PdfWriter(out);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);

        // Add title
        document.add(new Paragraph("Todo List Export"));

        // Create a table with headers
        Table table = new Table(4); // 4 columns for Created At, Title, Body, Author

        table.addHeaderCell("Title");
        table.addHeaderCell("Body");
        table.addHeaderCell("Author");
        table.addHeaderCell("Created At");

        // Add rows for selected todos
        for (Todo todo : selectedTodos) {
            table.addCell(todo.getTitle());
            table.addCell(todo.getBody());
            table.addCell(todo.getAuthor());
            table.addCell(todo.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        }

        // Add table to the document
        document.add(table);

        // Close the document
        document.close();

        // Return the InputStream containing the PDF data
        return new ByteArrayInputStream(out.toByteArray());
    }
}
