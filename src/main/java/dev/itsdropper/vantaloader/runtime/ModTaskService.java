package dev.itsdropper.vantaloader.runtime;
import dev.itsdropper.vantaloader.api.ModTasks;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
/** A serial, daemon-backed executor whose lifetime is bounded by one mod. */
final class ModTaskService implements ModTasks {
 private final ExecutorService executor; private volatile boolean closed;
 ModTaskService(String modId,ClassLoader loader){
  AtomicInteger sequence=new AtomicInteger();
  executor=Executors.newSingleThreadExecutor(task->{Thread thread=new Thread(()->{
   Thread current=Thread.currentThread();ClassLoader previous=current.getContextClassLoader();current.setContextClassLoader(loader);
   try{task.run();}finally{current.setContextClassLoader(previous);}
  },"VantaMod-"+modId+"-"+sequence.incrementAndGet());thread.setDaemon(true);return thread;});
 }
 @Override public Future<?> execute(Runnable task){return submit(Executors.callable(Objects.requireNonNull(task,"task")));}
 @Override public <T>Future<T> submit(Callable<T> task){
  Objects.requireNonNull(task,"task");if(closed)throw new IllegalStateException("Mod task scope is closed");
  try{return executor.submit(task);}catch(RejectedExecutionException ex){throw new IllegalStateException("Mod task scope is closed",ex);}
 }
 @Override public boolean isClosed(){return closed;}
 @Override public void close(){
  if(closed)return;closed=true;executor.shutdownNow();
  try{if(!executor.awaitTermination(5,TimeUnit.SECONDS))throw new IllegalStateException("Mod tasks did not stop within 5 seconds");}
  catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException("Interrupted while stopping mod tasks",ex);}
 }
}
