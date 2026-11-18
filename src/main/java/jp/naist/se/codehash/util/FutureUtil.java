package jp.naist.se.codehash.util;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class FutureUtil {

	/**
	 * Wait for the result of a task.
	 * An exception thrown by the task is rethrown as is if it is unchecked.
	 * @param future specifies a task.
	 * @return the result of the task.
	 */
	public static <T> T get(Future<T> future) {
		try {
			return future.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException) throw (RuntimeException)cause;
			if (cause instanceof Error) throw (Error)cause;
			throw new RuntimeException(cause);
		}
	}

}
