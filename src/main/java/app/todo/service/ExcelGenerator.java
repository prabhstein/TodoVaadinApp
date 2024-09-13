package app.todo.service;

import app.todo.model.Todo;
import com.vaadin.flow.server.StreamResource;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.time.format.DateTimeFormatter;
import java.util.Set;

@Service
public class ExcelGenerator implements Serializable {

    public StreamResource createExcelResource(Set<Todo> selectedTodos) {
        return new StreamResource("todos.xlsx", () -> {
            try {
                return createExcelFile(selectedTodos);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    public InputStream createExcelFile(Set<Todo> selectedTodos) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Todos");
            Row headerRow = sheet.createRow(0);

            // Create the header row
            String[] headers = {"Title", "Body", "Author", "Created At"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            // Create rows for selected todos
            int rowNum = 1;
            for (Todo todo : selectedTodos) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(todo.getTitle());
                row.createCell(1).setCellValue(todo.getBody());
                row.createCell(2).setCellValue(todo.getAuthor());
                row.createCell(3).setCellValue(todo.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            }

            // Write the Excel file to a byte array output stream
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }
}