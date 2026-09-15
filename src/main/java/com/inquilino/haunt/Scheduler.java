package com.inquilino.haunt;

import com.inquilino.InquilinoMod;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Agenda ações para alguns ticks no futuro (sequências de passos, batidas, finais...). */
public final class Scheduler {
	private static final List<Task> TASKS = new ArrayList<>();
	private static final List<Task> PENDING = new ArrayList<>();

	private Scheduler() {
	}

	public static void later(int ticks, Runnable action) {
		PENDING.add(new Task(Math.max(1, ticks), action));
	}

	public static void tick() {
		TASKS.addAll(PENDING);
		PENDING.clear();
		Iterator<Task> it = TASKS.iterator();
		while (it.hasNext()) {
			Task t = it.next();
			if (--t.remaining <= 0) {
				it.remove();
				try {
					t.action.run();
				} catch (Exception e) {
					InquilinoMod.LOGGER.error("Falha em tarefa agendada", e);
				}
			}
		}
	}

	public static void clear() {
		TASKS.clear();
		PENDING.clear();
	}

	private static final class Task {
		int remaining;
		final Runnable action;

		Task(int remaining, Runnable action) {
			this.remaining = remaining;
			this.action = action;
		}
	}
}
