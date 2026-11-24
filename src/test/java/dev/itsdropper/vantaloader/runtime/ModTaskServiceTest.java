package dev.itsdropper.vantaloader.runtime;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
class ModTaskServiceTest {
 @Test void executesSerialTasksWithOwningContextClassLoader()throws Exception{
  ClassLoader owner=new ClassLoader(){};var tasks=new ModTaskService("test_mod",owner);var seen=new AtomicReference<ClassLoader>();
  Future<?> first=tasks.execute(()->seen.set(Thread.currentThread().getContextClassLoader()));Future<Integer> second=tasks.submit(()->42);
  first.get(2,TimeUnit.SECONDS);assertSame(owner,seen.get());assertEquals(42,second.get(2,TimeUnit.SECONDS));tasks.close();assertTrue(tasks.isClosed());
 }
 @Test void rejectsWorkAfterClose(){var tasks=new ModTaskService("closed_mod",getClass().getClassLoader());tasks.close();assertThrows(IllegalStateException.class,()->tasks.execute(()->{}));}
 @Test void closeCancelsQueuedWork()throws Exception{
  var tasks=new ModTaskService("cancel_mod",getClass().getClassLoader());var entered=new CountDownLatch(1);var block=new CountDownLatch(1);
  Future<?> running=tasks.execute(()->{entered.countDown();try{block.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
  assertTrue(entered.await(2,TimeUnit.SECONDS));Future<?> queued=tasks.execute(()->fail("queued task must not run"));tasks.close();assertTrue(running.isDone());assertTrue(queued.isCancelled());
 }
}
