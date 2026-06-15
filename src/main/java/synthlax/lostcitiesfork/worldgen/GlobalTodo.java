package synthlax.lostcitiesfork.worldgen;

import synthlax.lostcitiesfork.setup.Config;
import synthlax.lostcitiesfork.varia.TodoQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GlobalTodo {

    private final Map<ChunkPos, TodoQueue> todoQueues = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, GlobalTodo> instances = new ConcurrentHashMap<>();

    public static GlobalTodo get(Level world) {
        return instances.computeIfAbsent(world.dimension(), k -> new GlobalTodo());
    }

    public static void cleanUp() {
        instances.clear();
    }

    public void addTodo(TodoTask task) {
        BlockPos pos = task.pos();
        ChunkPos chunkPos = new ChunkPos(pos);
        TodoQueue queue = todoQueues.computeIfAbsent(chunkPos, k -> new TodoQueue());
        synchronized (queue) {
            queue.add(task);
        }
    }

    public void executeAndClearTodo(ServerLevel level) {
        int todoSize = Config.TODO_QUEUE_SIZE.get();

        Set<ChunkPos> todoToRemove = new HashSet<>();
        for (Map.Entry<ChunkPos, TodoQueue> entry : todoQueues.entrySet()) {
            TodoQueue queue = entry.getValue();
            ChunkPos cp = entry.getKey();
            synchronized (queue) {
                todoSize -= queue.forEach(todoSize, (pos, task) -> task.execute(level));
                if (queue.isEmpty()) {
                    todoToRemove.add(cp);
                }
            }
            if (todoSize <= 0) {
                break;
            }
        }

        // Remove all empty todo queues
        todoToRemove.forEach(todoQueues::remove);
    }
}
