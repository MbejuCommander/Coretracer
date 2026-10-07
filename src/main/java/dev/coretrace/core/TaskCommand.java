package dev.coretrace.core;

/** Delay is measured before this command, after export or the previous command. */
public final class TaskCommand {
    public String command = "";
    public int delayMs = 750;
    public TaskCommand() {}
    public TaskCommand(String command, int delayMs) { this.command = command; this.delayMs = delayMs; }
    public void validate() {
        if (command == null || command.isBlank() || command.length() > 256
                || command.chars().anyMatch(Character::isISOControl) || command.strip().equals("/"))
            throw new IllegalArgumentException("Invalid custom command");
        if (delayMs < 0 || delayMs > 3600000) throw new IllegalArgumentException("Invalid delay");
    }
}
