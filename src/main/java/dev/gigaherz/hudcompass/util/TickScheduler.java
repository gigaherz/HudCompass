package dev.gigaherz.hudcompass.util;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.Iterator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class TickScheduler
{
    public final class ScheduledTask implements Comparable<ScheduledTask>
    {
        private boolean removed = false;
        private int targetTick;
        private int interval;
        private boolean repeating;
        private final Consumer callback;
        private final Object parameter;

        private ScheduledTask(int targetTick, int interval, boolean repeating, Consumer callback, Object parameter)
        {
            this.targetTick = targetTick;
            this.interval = interval;
            this.repeating = repeating;
            this.callback = callback;
            this.parameter = parameter;
        }

        public boolean remove()
        {
            removed = true;
            return tasks.remove(this);
        }

        public void setInterval(int interval)
        {
            if (removed) throw new IllegalStateException("This task has been removed");
            tasks.remove(this);
            this.interval = interval;
            targetTick = currentTick.get() + interval;
            tasks.add(this);
        }

        public void reset()
        {
            if (removed) throw new IllegalStateException("This task has been removed");
            setInterval(interval);
        }

        @SuppressWarnings("unchecked")
        private void run()
        {
            callback.accept(parameter);
        }

        @Override
        public int compareTo(ScheduledTask o)
        {
            int cmp = o.targetTick - targetTick;
            if (cmp > 0) return 1;
            if (cmp < 0) return -1;
            return 0;
        }
    }

    private final PriorityBlockingQueue<ScheduledTask> tasks = new PriorityBlockingQueue<>();
    private final AtomicInteger currentTick = new AtomicInteger();

    public TickScheduler(boolean server)
    {
        if (server)
        {
            MinecraftForge.EVENT_BUS.addListener(this::serverTick);
        }
        else
        {
            MinecraftForge.EVENT_BUS.addListener(this::clientTick);
        }
    }

    private void serverTick(TickEvent.ServerTickEvent event)
    {
        update();
    }

    private void clientTick(TickEvent.ClientTickEvent event)
    {
        update();
    }

    private void update()
    {
        int current = currentTick.incrementAndGet();
        for (Iterator<ScheduledTask> it = tasks.iterator(); it.hasNext(); )
        {
            ScheduledTask next = it.next();
            if (next.targetTick <= current)
            {
                next.run();
                it.remove();
            }
            else
            {
                break;
            }
        }
    }

    public <T> ScheduledTask schedule(int interval, boolean repeating, Consumer<T> callback, T parameter)
    {
        ScheduledTask task = new ScheduledTask(currentTick.get() + interval, interval, repeating, callback, parameter);
        tasks.add(task);
        return task;
    }
}
