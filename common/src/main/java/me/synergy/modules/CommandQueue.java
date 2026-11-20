package me.synergy.modules;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;

/**
 * The website's way into the network. It inserts a row into synergy_queue (event, uuid, options as JSON, target server);
 * the hub claims pending rows, sends each as a Synergy event and writes the handler's reply back into the row, which
 * the website reads to show the result. One indexed query per poll, rows claimed with a conditional UPDATE, so several
 * hubs or a restart never run a command twice.
 *
 * status: 0 pending, 1 running, 2 done, 3 failed.
 * target: empty = this hub, "*" = every server, "@<uuid>" = the server that player is on, or a server name.
 * Any Synergy event works (command-queue.events, wildcards allowed); handlers answer with event.reply(),
 * and an "error" option in the reply marks the row failed.
 */
public final class CommandQueue implements Module {

    private record Job(long id, String event, String uuid, String options, String target) {}

    private static final int PENDING = 0, RUNNING = 1, DONE = 2, FAILED = 3;
    private static final AtomicBoolean polling = new AtomicBoolean();
    private static ScheduledFuture<?> poller, cleaner;

    @Override public Side side() { return Side.HUB; }
    @Override public String toggle() { return "command-queue.enabled"; }

    @Override
    public void defaults() {
        Config.addDefault("command-queue.enabled", true);
        Config.addDefault("command-queue.poll-millis", 1000);
        Config.addDefault("command-queue.reply-timeout-seconds", 10);
        Config.addDefault("command-queue.keep-days", 7);
        // Whoever can write to the database is already trusted with the whole network; narrow this to lock the bridge down
        Config.addDefault("command-queue.events", new String[] {"*"});
    }

    @Override
    public void start() {
        boolean sqlite = DataManager.sqlite();
        DataManager.update("CREATE TABLE IF NOT EXISTS synergy_queue ("
            + (sqlite ? "id INTEGER PRIMARY KEY AUTOINCREMENT" : "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY")
            + ", event VARCHAR(64) NOT NULL, uuid VARCHAR(36), options TEXT, target VARCHAR(64), status TINYINT NOT NULL DEFAULT 0"
            + ", result TEXT, created BIGINT NOT NULL, handled BIGINT)" + (sqlite ? "" : " DEFAULT CHARSET=utf8mb4"));
        DataManager.tolerate("CREATE INDEX " + (sqlite ? "IF NOT EXISTS " : "") + "synergy_queue_status ON synergy_queue (status, id)");
        // A hub that died mid-command leaves it running forever: give it back
        DataManager.update("UPDATE synergy_queue SET status = ?, result = ? WHERE status = ? AND handled < ?", FAILED, "{\"error\":\"interrupted\"}", RUNNING, System.currentTimeMillis() - 60_000);
        poller = Tasks.every(CommandQueue::poll, Duration.ofMillis(Math.max(200, Config.getInt("command-queue.poll-millis", 1000))));
        cleaner = Tasks.every(CommandQueue::clean, Duration.ofHours(1));
    }

    @Override
    public void stop() {
        if (poller != null) poller.cancel(false);
        if (cleaner != null) cleaner.cancel(false);
    }

    @Override
    public void refresh() {
        stop();
        poller = Tasks.every(CommandQueue::poll, Duration.ofMillis(Math.max(200, Config.getInt("command-queue.poll-millis", 1000))));
        cleaner = Tasks.every(CommandQueue::clean, Duration.ofHours(1));
    }

    private static void poll() {
        if (!polling.compareAndSet(false, true)) return;
        try {
            List<Job> jobs;
            do {
                jobs = DataManager.select("SELECT id, event, uuid, options, target FROM synergy_queue WHERE status = 0 ORDER BY id LIMIT 50",
                    r -> new Job(r.getLong(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5)));
                for (Job job : jobs) {
                    if (DataManager.update("UPDATE synergy_queue SET status = ?, handled = ? WHERE id = ? AND status = ?", RUNNING, System.currentTimeMillis(), job.id(), PENDING) == 1)
                        Tasks.async(() -> run(job));
                }
            } while (jobs.size() == 50);
        } finally {
            polling.set(false);
        }
    }

    private static void run(Job job) {
        if (Config.getStringList("command-queue.events").stream().noneMatch(p -> p.equals(job.event()) || p.endsWith("*") && job.event().startsWith(p.substring(0, p.length() - 1)))) {
            finish(job, FAILED, "{\"error\":\"event not allowed: " + job.event() + "\"}");
            return;
        }
        SynergyEvent event;
        try {
            event = new SynergyEvent(job.event(), job.uuid() == null || job.uuid().isBlank() ? null : UUID.fromString(job.uuid()), job.options());
        } catch (RuntimeException e) {
            finish(job, FAILED, "{\"error\":\"bad row: " + e.getMessage().replace("\"", "'") + "\"}");
            return;
        }
        String target = job.target() == null || job.target().isBlank() ? Messaging.PROXY : job.target();
        if (target.startsWith("@") && target.length() == 37) {
            event.toPlayer(UUID.fromString(target.substring(1)));
            if (!Synergy.platform().isOnline(event.getPlayerUniqueId())) {
                finish(job, FAILED, "{\"error\":\"player offline\"}");
                return;
            }
            event.request(Duration.ofSeconds(Config.getInt("command-queue.reply-timeout-seconds", 10))).whenComplete((reply, error) -> reply(job, reply));
            return;
        }
        if (target.equals("*")) {
            event.send();
            if (Synergy.isProxy() && Messaging.isEnabled()) event.fireEvent();
            finish(job, DONE, null);
            return;
        }
        event.to(target).request(Duration.ofSeconds(Config.getInt("command-queue.reply-timeout-seconds", 10))).whenComplete((reply, error) -> reply(job, reply));
    }

    /** No reply in time is not a failure: most events (broadcast, dispatch-command) never answer. */
    private static void reply(Job job, SynergyEvent reply) {
        if (reply == null) finish(job, DONE, null);
        else finish(job, reply.getOption("error").isSet() ? FAILED : DONE, reply.getOptionsAsJson());
    }

    private static void finish(Job job, int status, String result) {
        DataManager.update("UPDATE synergy_queue SET status = ?, result = ?, handled = ? WHERE id = ?", status, result, System.currentTimeMillis(), job.id());
    }

    private static void clean() {
        DataManager.update("DELETE FROM synergy_queue WHERE status >= ? AND created < ?", DONE, System.currentTimeMillis() - Duration.ofDays(Config.getInt("command-queue.keep-days", 7)).toMillis());
    }
}
