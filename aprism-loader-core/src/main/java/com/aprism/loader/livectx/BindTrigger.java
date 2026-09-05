package com.aprism.loader.livectx;

/**
 * A reusable binder trigger: reacts to live context transitions instead of
 * polling or guessing timings (v26.9 roadmap Alpha.3). Triggers are
 * registered as {@link LiveContextListener}s by the runtime.
 *
 * @author BlockConnect@StarsailsClover
 */
@FunctionalInterface
public interface BindTrigger extends LiveContextListener {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover
}
