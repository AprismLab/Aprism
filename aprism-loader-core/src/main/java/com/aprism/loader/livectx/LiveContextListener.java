package com.aprism.loader.livectx;

/**
 * A listener for live context transitions. Listeners run synchronously on
 * the reporting thread; implementations must be fast and must never throw
 * (a throwing listener is contained by the tracker and reported once).
 *
 * @author BlockConnect@StarsailsClover
 */
@FunctionalInterface
public interface LiveContextListener {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /**
     * Called for every accepted transition.
     *
     * @param transition the observed transition
     */
    void onTransition(LiveContextTransition transition);
}
