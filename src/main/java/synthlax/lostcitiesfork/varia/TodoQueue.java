package synthlax.lostcitiesfork.varia;

import net.minecraft.core.BlockPos;
import synthlax.lostcitiesfork.worldgen.TodoTask;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.BiConsumer;

public class TodoQueue {

    private final Queue<TodoTask> queue = new ArrayDeque<>();

    public void add(TodoTask task) {
        queue.add(task);
    }

    public TodoTask get() {
        return queue.poll();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int getSize() {
        return queue.size();
    }

    public void forEach(BiConsumer<BlockPos, TodoTask> consumer) {
        queue.forEach(task -> consumer.accept(task.pos(), task));
    }

    // Execute a BiConsumer on the N first elements in the queue
    public int forEach(int n, BiConsumer<BlockPos, TodoTask> consumer) {
        int cnt = 0;
        for (int i = 0; i < n; i++) {
            TodoTask task = queue.poll();
            if (task == null) {
                break;
            }
            consumer.accept(task.pos(), task);
            cnt++;
        }
        return cnt;
    }
}
