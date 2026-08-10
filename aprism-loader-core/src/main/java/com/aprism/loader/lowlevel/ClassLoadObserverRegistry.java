package com.aprism.loader.lowlevel;

import com.aprism.api.lowlevel.ClassLoadObserver;
import com.aprism.api.lowlevel.ClassShape;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registry of {@link ClassLoadObserver}s notified as classes pass through
 * the Aprism transformation pipeline (v26.4-Alpha.3, deep bytecode-hook
 * API). Notification is fail-safe: an observer that throws is logged and
 * skipped; it never aborts class loading or the game.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class ClassLoadObserverRegistry {

    private static final Logger LOG = Logger.getLogger("aprism.lowlevel.observers");

    private final CopyOnWriteArrayList<ClassLoadObserver> observers = new CopyOnWriteArrayList<>();

    /**
     * Registers an observer.
     *
     * @param observer the observer to register
     * @throws IllegalArgumentException if the observer is null or already
     *                                  registered
     */
    public void register(ClassLoadObserver observer) {
        if (observer == null) {
            throw new IllegalArgumentException("observer must be non-null");
        }
        if (!observers.addIfAbsent(observer)) {
            throw new IllegalArgumentException("observer already registered");
        }
    }

    /**
     * Unregisters a previously registered observer.
     *
     * @param observer the observer to remove
     * @return whether the observer was found and removed
     */
    public boolean unregister(ClassLoadObserver observer) {
        return observers.remove(observer);
    }

    /**
     * @return the registered observers in registration order
     */
    public List<ClassLoadObserver> registeredObservers() {
        return List.copyOf(observers);
    }

    /**
     * Notifies every observer of an observed class shape, fail-safely.
     *
     * @param shape the observed shape
     */
    public void notifyObservers(ClassShape shape) {
        for (ClassLoadObserver observer : observers) {
            try {
                observer.onClassObserved(shape);
            } catch (RuntimeException failure) {
                LOG.log(Level.WARNING, "class-load observer failed for "
                        + shape.className(), failure);
            }
        }
    }

    /**
     * Removes all observers. Called by the loader on shutdown.
     */
    public void clear() {
        observers.clear();
    }
}
