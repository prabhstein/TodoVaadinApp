package app.todo.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@Document(collection = "todos")
public class Todo {

    @Id
    private String id;
    private String title;
    private String body;
    private String author;
    private LocalDateTime createdAt;
}
