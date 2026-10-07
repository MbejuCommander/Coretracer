package dev.coretrace.core;

import java.util.ArrayList;
import java.util.List;

/** A saved task list; edits to the active queue never modify this snapshot. */
public final class TaskProfile {
    public String name = "";
    public List<TaskDefinition> tasks = new ArrayList<>();

    public TaskProfile() {}
    public TaskProfile(String name, List<TaskDefinition> tasks) {
        this.name = validName(name);
        this.tasks = copyTasks(tasks);
    }
    public static String validName(String name) {
        if (name == null || name.isBlank() || name.strip().length() > 64
                || name.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid profile name");
        return name.strip();
    }
    public static ArrayList<TaskDefinition> copyTasks(List<TaskDefinition> tasks) {
        var result = new ArrayList<TaskDefinition>();
        for (TaskDefinition task : tasks) result.add(task.copy());
        return result;
    }
    public void normalize() {
        name = validName(name);
        if (tasks == null) tasks = new ArrayList<>();
        tasks.removeIf(java.util.Objects::isNull);
        tasks.forEach(TaskDefinition::migrateCommands);
    }
}
