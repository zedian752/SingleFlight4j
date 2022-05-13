package org.SingleFlight;

public interface Task<T> {
    T get() throws Exception;
}
