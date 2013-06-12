package com.fileshare.file;

import org.apache.logging.log4j.LogManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Kamil Sikora
 *         Data: 10.06.13
 */
public class DirectoryWatcher extends IDirectoryWatch {

    private static final org.apache.logging.log4j.Logger logger = LogManager.getLogger(DirectoryWatcher.class.getName());

    private Path path;
    private final Map<WatchKey, Path> keys;

    /**
     * @param watchedDir ścieżka do obserwowania.
     * @param interval   co ile sekund sprawdzany będzie system plików
     */
    protected DirectoryWatcher(String watchedDir, int interval) {
        super();
        this.interval = interval;
        this.keys = new HashMap<>();
        this.path = new File(watchedDir).toPath();

        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            WatchKey key = path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
            keys.put(key, path);
        } catch (IOException e) {
            logger.error("Error while registering the directory.");
            e.printStackTrace();
        }

    }

    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>) event;
    }

    @Override
    protected void watchChanges() {
        ArrayList<PathInfo> changedItems;
        // You spin me round, round baby... infinite loop
        while (true) {
            changedItems = new ArrayList<>();
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException x) {
                return;
            }

            Path dir = keys.get(key);
            if (dir == null)
                continue;

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind kind = event.kind();

                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();
                Path child = dir.resolve(name);
                changedItems.add(new PathInfo(name, ev.kind()));
            }

            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);
                logger.info("Key removed" + key);
                if (keys.isEmpty()) {
                    break;
                }
            }

            sendChanges(changedItems);

            try {
                Thread.sleep(interval * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void sendChanges(ArrayList<PathInfo> files) {
        hasChanged();
        notifyObservers(files);
        clearChanged();
    }

    @Override
    public void run() {
        watchChanges();
    }

    public static void main(String[] args) {
        DirectoryWatcher dw = new DirectoryWatcher("./", 5);
        new Thread(dw).start();
    }
}
