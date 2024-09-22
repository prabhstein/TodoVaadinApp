package app.todo.service;

import app.todo.model.Todo;
import app.todo.repo.TodoRepository;
import com.mongodb.client.gridfs.GridFSFindIterable;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TodoService {

    @Autowired
    private TodoRepository todoRepository;

    @Autowired
    private GridFsTemplate gridFsTemplate;

    public List<Todo> getAllTodos() {
        return todoRepository.findAll();
    }

    public List<Todo> getAllTodosByUserId(String userId) {
        return todoRepository.findByUserId(userId);
    }

    public Todo saveTodo(Todo todo) {
        return todoRepository.save(todo);
    }

    public void deleteTodo(Todo todo) {
        todoRepository.delete(todo);
    }

    public List<GridFSFile> getAllFiles() {
        GridFSFindIterable files = gridFsTemplate.find(new Query());
        List<GridFSFile> fileList = new ArrayList<>();
        files.forEach(fileList::add);
        return fileList;
    }

    public void deleteAllFiles() {
        todoRepository.deleteAll();
    }
}
