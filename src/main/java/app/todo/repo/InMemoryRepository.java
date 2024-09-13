package app.todo.repo;

import app.todo.model.Todo;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class InMemoryRepository {

    private final List<Todo> items = new ArrayList<>();

    @PostConstruct
    void setup() {
       addItems(Todo.builder().title("Running").body("Get Going").author("Admin").createdAt(LocalDateTime.now()).build());
       addItems(Todo.builder().title("Learning").body("Learn something new").author("Admin").createdAt(LocalDateTime.now()).build());
       addItems(Todo.builder().title("Walking").body("Get Going").author("Admin").createdAt(LocalDateTime.now()).build());
       addItems(Todo.builder().title("Eating").body("Learn something new").author("Admin").createdAt(LocalDateTime.now()).build());
       addItems(Todo.builder().title("Drinking").body("Get Going").author("Admin").createdAt(LocalDateTime.now()).build());
       addItems(Todo.builder().title("Diving").body("Learn something new").author("Admin").createdAt(LocalDateTime.now()).build());
    }

    public void addItems(Todo todo) {
        items.add(todo);
    }

    public List<Todo> getItems() {
        return items;
    }
}
