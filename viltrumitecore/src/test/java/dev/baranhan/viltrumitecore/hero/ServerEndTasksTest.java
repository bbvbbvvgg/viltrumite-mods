package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ServerEndTasksTest {
   @Test void transferOnlyRunsAtExplicitServerEnd() {var q=new ServerEndTasks.Queue<String>();List<Integer> work=new ArrayList<>();q.enqueue("server",()->work.add(1));assertTrue(work.isEmpty());q.flush("server",e->fail(e));assertEquals(List.of(1),work);q.flush("server",e->fail(e));assertEquals(1,work.size());}
   @Test void tasksAddedWhileFlushingWaitForNextEnd() {var q=new ServerEndTasks.Queue<String>();List<Integer> work=new ArrayList<>();q.enqueue("server",()->{work.add(1);q.enqueue("server",()->work.add(2));});q.flush("server",e->fail(e));assertEquals(List.of(1),work);q.flush("server",e->fail(e));assertEquals(List.of(1,2),work);}
   @Test void serverStopDiscardsOnlyItsPendingTasks() {var q=new ServerEndTasks.Queue<String>();List<Integer> work=new ArrayList<>();q.enqueue("a",()->work.add(1));q.enqueue("b",()->work.add(2));q.clear("a");q.flush("a",e->fail(e));q.flush("b",e->fail(e));assertEquals(List.of(2),work);}
   @Test void failedTransferDoesNotDropOtherTransfers() {var q=new ServerEndTasks.Queue<String>();List<Throwable> errors=new ArrayList<>();List<Integer> work=new ArrayList<>();q.enqueue("a",()->{throw new IllegalStateException("test");});q.enqueue("a",()->work.add(2));q.flush("a",errors::add);assertEquals(1,errors.size());assertEquals(List.of(2),work);}
}
