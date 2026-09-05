package com.aprism.loader.livectx;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Content binding trigger (v26.9-Alpha.3): fires the supplied bind action
 * once per side on the first IN_WORLD transition - the moment a real world
 * exists to bind into. Re-arming re-fires on the next IN_WORLD after a
 * LEAVING, which is what world re-joins need.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class ContentBindTrigger implements BindTrigger {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    private static final Logger LOG =
            Logger.getLogger(ContentBindTrigger.class.getName());

    private final Runnable bindAction;
    private final boolean reArm;
    private final Set<LiveContext.Side> armed =
            ConcurrentHashMap.newKeySet();
    private final Map<LiveContext.Side, Integer> firings =
            new ConcurrentHashMap<>();

    /**
     * @param bindAction the bind action to run (must never throw upward;
     *        the trigger contains and logs throwables)
     * @param reArm whether to fire again on the next world join after a
     *        leave
     */
    public ContentBindTrigger(Runnable bindAction, boolean reArm) {
        this.bindAction = bindAction;
        this.reArm = reArm;
        this.armed.add(LiveContext.Side.CLIENT);
        this.armed.add(LiveContext.Side.SERVER);
    }

    @Override
    public void onTransition(LiveContextTransition transition) {
        LiveContext.Side side = transition.side();
        if (transition.to() == LiveContext.State.LEAVING && reArm) {
            armed.add(side);
            return;
        }
        if (transition.to() != LiveContext.State.IN_WORLD
                || transition.from() == LiveContext.State.IN_WORLD) {
            return;
        }
        if (!armed.remove(side)) {
            return;
        }
        try {
            bindAction.run();
            firings.merge(side, 1, Integer::sum);
            LOG.info("[livectx] content bind trigger fired on " + side
                    + " world join (re-arm=" + reArm + ")");
        } catch (Throwable contained) {
            LOG.warning("[livectx] bind trigger failed, contained: " + contained);
        }
    }

    /**
     * @return how many times the bind action fired for the side
     */
    public int firingCount(LiveContext.Side side) {
        return firings.getOrDefault(side, 0);
    }
}
