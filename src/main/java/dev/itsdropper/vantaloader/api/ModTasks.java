package dev.itsdropper.vantaloader.api;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
/** Mod-owned asynchronous work. Tasks are cancelled before the mod class loader is closed. */
public interface ModTasks extends AutoCloseable {
 Future<?> execute(Runnable task);
 <T> Future<T> submit(Callable<T> task);
 boolean isClosed();
 @Override void close();
}
