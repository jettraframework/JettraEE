package io.jettra.ee.security.db;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Motor de persistencia embebido local para seguridad en JettraEE.
 * Almacena entidades serializadas en db/securitydb/<coleccion>/<id>.jdb
 * con control de concurrencia mediante cerrojos de proceso y de máquina virtual.
 */
public class JettraSecurityDB {

    private static final String DEFAULT_DIR = System.getProperty("user.dir") + File.separator + "db" + File.separator + "securitydb";
    private final String baseDir;
    private static final ConcurrentHashMap<Class<?>, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();

    public JettraSecurityDB() {
        this(System.getProperty("jettra.securitydb.path", DEFAULT_DIR));
    }

    public JettraSecurityDB(String baseDir) {
        this.baseDir = baseDir;
        init();
    }

    private void init() {
        File dir = new File(baseDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private ReentrantReadWriteLock getLock(Class<?> clazz) {
        return locks.computeIfAbsent(clazz, k -> new ReentrantReadWriteLock(true));
    }

    private File getCollectionDir(Class<?> clazz) {
        File dir = new File(baseDir + File.separator + clazz.getSimpleName().toLowerCase());
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public <T extends Serializable> void save(String id, T entity) {
        Class<?> clazz = entity.getClass();
        ReentrantReadWriteLock.WriteLock writeLock = getLock(clazz).writeLock();
        writeLock.lock();
        FileOutputStream fos = null;
        java.nio.channels.FileLock fileLock = null;
        try {
            File dir = getCollectionDir(clazz);
            File file = new File(dir, id + ".jdb");

            fos = new FileOutputStream(file);
            java.nio.channels.FileChannel channel = fos.getChannel();
            fileLock = channel.lock();

            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(new CompactHeader());
            oos.writeObject(entity);
            oos.flush();
        } catch (IOException e) {
            throw new RuntimeException("Error saving object to JettraSecurityDB", e);
        } finally {
            if (fileLock != null) {
                try {
                    fileLock.release();
                } catch (IOException ignored) {}
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ignored) {}
            }
            writeLock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends Serializable> Optional<T> findById(Class<T> clazz, String id) {
        ReentrantReadWriteLock.ReadLock readLock = getLock(clazz).readLock();
        readLock.lock();
        FileInputStream fis = null;
        java.nio.channels.FileLock fileLock = null;
        try {
            File dir = getCollectionDir(clazz);
            File file = new File(dir, id + ".jdb");
            if (!file.exists()) {
                return Optional.empty();
            }

            fis = new FileInputStream(file);
            java.nio.channels.FileChannel channel = fis.getChannel();
            fileLock = channel.lock(0L, Long.MAX_VALUE, true);

            ObjectInputStream ois = new ObjectInputStream(fis);
            CompactHeader header = (CompactHeader) ois.readObject();
            T entity = (T) ois.readObject();
            return Optional.of(entity);
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Error reading object from JettraSecurityDB", e);
        } finally {
            if (fileLock != null) {
                try {
                    fileLock.release();
                } catch (IOException ignored) {}
            }
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ignored) {}
            }
            readLock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends Serializable> List<T> findAll(Class<T> clazz) {
        ReentrantReadWriteLock.ReadLock readLock = getLock(clazz).readLock();
        readLock.lock();
        try {
            File dir = getCollectionDir(clazz);
            List<T> results = new ArrayList<>();

            File[] files = dir.listFiles((d, name) -> name.endsWith(".jdb"));
            if (files != null) {
                for (File file : files) {
                    FileInputStream fis = null;
                    java.nio.channels.FileLock fileLock = null;
                    try {
                        fis = new FileInputStream(file);
                        java.nio.channels.FileChannel channel = fis.getChannel();
                        fileLock = channel.lock(0L, Long.MAX_VALUE, true);

                        ObjectInputStream ois = new ObjectInputStream(fis);
                        CompactHeader header = (CompactHeader) ois.readObject();
                        T entity = (T) ois.readObject();
                        results.add(entity);
                    } catch (FileNotFoundException ignored) {
                    } catch (IOException | ClassNotFoundException e) {
                        System.err.println("[JettraSecurityDB] Error reading file: " + file.getName());
                    } finally {
                        if (fileLock != null) {
                            try {
                                fileLock.release();
                            } catch (IOException ignored) {}
                        }
                        if (fis != null) {
                            try {
                                fis.close();
                            } catch (IOException ignored) {}
                        }
                    }
                }
            }
            return results;
        } finally {
            readLock.unlock();
        }
    }

    public <T extends Serializable> void delete(Class<T> clazz, String id) {
        ReentrantReadWriteLock.WriteLock writeLock = getLock(clazz).writeLock();
        writeLock.lock();
        try {
            File dir = getCollectionDir(clazz);
            File file = new File(dir, id + ".jdb");
            if (file.exists()) {
                file.delete();
            }
        } finally {
            writeLock.unlock();
        }
    }

    public <T extends Serializable> List<T> search(Class<T> clazz, Predicate<T> predicate) {
        return findAll(clazz).stream().filter(predicate).collect(Collectors.toList());
    }

    public <T extends Serializable> List<T> search(Class<T> clazz, String query) {
        return search(clazz, JettraQueryParser.parse(query, clazz));
    }
}
